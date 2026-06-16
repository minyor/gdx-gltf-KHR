package net.mgsx.gltf.loaders.shared.texture;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectMap.Entry;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.mgsx.gltf.data.GLTFExtensions;
import net.mgsx.gltf.data.texture.GLTFImage;
import net.mgsx.gltf.data.texture.GLTFSampler;
import net.mgsx.gltf.data.texture.GLTFTexture;
import net.mgsx.gltf.data.texture.GLTFTextureInfo;
import net.mgsx.gltf.data.texture.KHRTextureBasisu;
import net.mgsx.gltf.data.texture.EXTTextureWebp;
import net.mgsx.gltf.loaders.exceptions.GLTFRuntimeException;
import net.mgsx.gltf.loaders.shared.GLTFTypes;
import net.mgsx.gltf.basis.BasisTranscoder;

public class TextureResolver implements Disposable
{
    // Target format constants (matches JNI)
    private static final int TF_ETC2_RGBA = 0;
    private static final int TF_ASTC_4x4_RGBA = 1;
    
    // OpenGL compressed texture format for ETC2_RGBA
    private static final int GL_COMPRESSED_RGBA_ETC2 = 0x9278;
    private static final int GL_COMPRESSED_RGBA_ASTC_4x4 = 0x93B0;
    
    protected final ObjectMap<Integer, Texture> texturesSimple = new ObjectMap<Integer, Texture>();
    protected final ObjectMap<Integer, Texture> texturesMipmap = new ObjectMap<Integer, Texture>();
    protected Array<GLTFTexture> glTextures;
    protected Array<GLTFSampler> glSamplers;
    
    public void loadTextures(Array<GLTFTexture> glTextures, Array<GLTFSampler> glSamplers, ImageResolver imageResolver) {
        this.glTextures = glTextures;
        this.glSamplers = glSamplers;
        if(glTextures != null){
            for(int i=0 ; i<glTextures.size ; i++){
                GLTFTexture glTexture = glTextures.get(i);

                // check if mipmap needed for this texture configuration
                boolean useMipMaps = false;
                if(glTexture.sampler != null){
                    GLTFSampler sampler = glSamplers.get(glTexture.sampler);
                    if(GLTFTypes.isMipMapFilter(sampler)){
                        useMipMaps = true;
                    }
                }

                ObjectMap<Integer, Texture> textureMap = useMipMaps ? texturesMipmap : texturesSimple;

                // Check for KHR_texture_basisu extension
                if (glTexture.extensions != null) {
                    KHRTextureBasisu basisu = glTexture.extensions.get(KHRTextureBasisu.class, KHRTextureBasisu.EXTENSION_NAME);
                    if (basisu != null && basisu.source != null) {
                        Gdx.app.log("GLTF", "KHR_texture_basisu detected on texture " + i + ", Basis source=" + basisu.source);
        
                        // Load Basis data and transcode to GPU compressed format
                        Texture texture = loadBasisTexture(basisu.source, useMipMaps, imageResolver);
                        if (texture != null) {
                            textureMap.put(basisu.source, texture);
                        } else {
                            Gdx.app.error("GLTF", "Failed to load Basis texture " + basisu.source + ", skipping");
                        }
                        continue; // Always continue for Basis textures to avoid trying to load as regular image
                    }
                    
                    // Check for EXT_texture_webp extension
                    EXTTextureWebp webp = glTexture.extensions.get(EXTTextureWebp.class, EXTTextureWebp.EXT);
                    if (webp != null && webp.source != null) {
                        Gdx.app.log("GLTF", "EXT_texture_webp detected on texture " + i + ", WebP source=" + webp.source);
                        
                        // Load WebP data and decode to Texture
                        Texture texture = loadWebPTexture(webp.source, useMipMaps, imageResolver);
                        if (texture != null) {
                            textureMap.put(webp.source, texture);
                        } else {
                            Gdx.app.error("GLTF", "Failed to load WebP texture " + webp.source + ", skipping");
                        }
                        continue; // Always continue for WebP textures to avoid trying to load as regular image
                    }
                }
        
                if(!textureMap.containsKey(glTexture.source)){
                    Pixmap pixmap = imageResolver.get(glTexture.source);
                    Gdx.app.log("GLTF", "TextureResolver: Getting pixmap for texture " + i + ", source=" + glTexture.source + ", pixmap=" + pixmap);
                    if (pixmap != null) {
                        Gdx.app.log("GLTF", "TextureResolver: Creating Texture from pixmap for " + i);
                        Texture texture = new Texture(pixmap, useMipMaps);
                        textureMap.put(glTexture.source, texture);
                        Gdx.app.log("GLTF", "TextureResolver: Texture created successfully for " + i);
                    } else {
                        Gdx.app.error("GLTF", "TextureResolver: Pixmap is null for texture " + i + ", source=" + glTexture.source);
                    }
                }
            }
        }
    }

    /**
     * Load a Basis Universal compressed texture and transcode to GPU compressed format.
     * Uses handle-based architecture for proper KTX2 support.
     */
    private Texture loadBasisTexture(int basisSource, boolean useMipMaps, ImageResolver imageResolver) {
        // Get raw Basis/KTX2 bytes
        byte[] basisData = imageResolver.getRawBytes(basisSource);
        if (basisData == null || basisData.length == 0) {
            Gdx.app.error("GLTF", "No Basis data available for source " + basisSource);
            return null;
        }

        // Check if transcoder is available
        if (!BasisTranscoder.isAvailable()) {
            Gdx.app.error("GLTF", "Basis transcoder not available");
            return null;
        }

        // Create direct ByteBuffer for JNI (required for KTX2)
        ByteBuffer ktx2Buffer = ByteBuffer.allocateDirect(basisData.length);
        ktx2Buffer.order(ByteOrder.nativeOrder());
        ktx2Buffer.put(basisData);
        ktx2Buffer.flip();

        // Open KTX2 transcoder handle
        long handle = BasisTranscoder.openKTX2(ktx2Buffer, basisData.length);
        if (handle == 0) {
            Gdx.app.error("GLTF", "Failed to open KTX2 transcoder for source " + basisSource);
            return null;
        }

        try {
            // Get texture dimensions from handle
            int width = BasisTranscoder.getKTX2Width(handle);
            int height = BasisTranscoder.getKTX2Height(handle);
            int numLevels = BasisTranscoder.getKTX2NumLevels(handle);
            int hasAlpha = BasisTranscoder.getKTX2HasAlpha(handle);

            if (width <= 0 || height <= 0) {
                Gdx.app.error("GLTF", "Invalid Basis texture dimensions: " + width + "x" + height);
                return null;
            }

            Gdx.app.log("GLTF", "Basis texture: " + width + "x" + height + ", " + numLevels + " levels, hasAlpha=" + hasAlpha);

            // Determine target format and GL format (use ETC2 for now)
            int targetFormat = TF_ETC2_RGBA;
            int glFormat = GL_COMPRESSED_RGBA_ETC2;

            // Create texture
            Texture texture = new Texture(width, height, Pixmap.Format.RGBA8888);
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);

            GL20 gl = Gdx.graphics.getGL20();
            gl.glBindTexture(GL20.GL_TEXTURE_2D, texture.getTextureObjectHandle());

            // Transcode each mipmap level
            int maxLevels = useMipMaps ? numLevels : 1;
            for (int level = 0; level < maxLevels; level++) {
                int levelWidth = Math.max(1, width >> level);
                int levelHeight = Math.max(1, height >> level);

                // Calculate transcoded size for this level (in blocks)
                int blockWidth = (levelWidth + 3) / 4;
                int blockHeight = (levelHeight + 3) / 4;
                int blockSize = (targetFormat == TF_ETC2_RGBA) ? 16 : 16; // 16 bytes per block for both ETC2 and ASTC 4x4
                int levelSize = blockWidth * blockHeight * blockSize;

                Gdx.app.log("GLTF", "Transcoding level " + level + ": " + levelWidth + "x" + levelHeight + ", " + blockWidth + "x" + blockHeight + " blocks, " + levelSize + " bytes");

                // Allocate direct buffer for transcoded data
                ByteBuffer outputBuffer = ByteBuffer.allocateDirect(levelSize);
                outputBuffer.order(ByteOrder.nativeOrder());

                // Transcode using handle-based API
                int result = BasisTranscoder.transcodeKTX2Level(handle, level, 0, targetFormat, outputBuffer);
                if (result != 0) {
                    Gdx.app.error("GLTF", "Failed to transcode Basis level " + level + ": " + result);
                    break;
                }

                // Upload directly to GPU using glCompressedTexImage2D
                outputBuffer.position(0);
                gl.glBindTexture(GL20.GL_TEXTURE_2D, texture.getTextureObjectHandle());
                gl.glCompressedTexImage2D(GL20.GL_TEXTURE_2D, level, glFormat, levelWidth, levelHeight, 0, levelSize, outputBuffer);
            }

            return texture;
        } finally {
            // Always close the handle
            BasisTranscoder.closeKTX2(handle);
        }
    }
    
    /**
     * Load a WebP compressed texture and decode to regular Texture.
     * Uses an anonymous virtual FileHandle to avoid temporary files.
     */
    private Texture loadWebPTexture(int webpSource, boolean useMipMaps, ImageResolver imageResolver) {
        final byte[] webpData = imageResolver.getRawBytes(webpSource);
        if (webpData == null || webpData.length == 0) {
            Gdx.app.error("GLTF", "No WebP data available for source " + webpSource);
            return null;
        }
        
        Gdx.app.log("GLTF", "Decoding WebP texture directly from memory...");
        
        try {
            // 1. Safe Cross-Platform Check: Use reflection to locate Android's BitmapFactory
            Class<?> bitmapFactoryClass = Class.forName("android.graphics.BitmapFactory");
            java.lang.reflect.Method decodeMethod = bitmapFactoryClass.getMethod("decodeByteArray", byte[].class, int.class, int.class);
            
            // 2. Invoke the native decoder: This passes the byte stream straight to the Android OS decoders
            Object bitmapObj = decodeMethod.invoke(null, webpData, 0, webpData.length);
            if (bitmapObj == null) {
                Gdx.app.error("GLTF", "Android BitmapFactory failed to decode the WebP byte stream!");
                return null;
            }

            // Extract width and height from the decoded bitmap using reflection methods
            int width = (Integer) bitmapObj.getClass().getMethod("getWidth").invoke(bitmapObj);
            int height = (Integer) bitmapObj.getClass().getMethod("getHeight").invoke(bitmapObj);
            Gdx.app.log("GLTF", "WebP decoded successfully via Android OS: " + width + "x" + height);

            // 3. Create an uninitialized libGDX Texture context
            Texture texture = new Texture(width, height, Pixmap.Format.RGBA8888);
            texture.bind();

            // 4. Locate and invoke GLUtils.texImage2D to upload the decoded bitmap straight to the GPU
            Class<?> glUtilsClass = Class.forName("android.opengl.GLUtils");
            java.lang.reflect.Method texImageMethod = glUtilsClass.getMethod("texImage2D", int.class, int.class, Class.forName("android.graphics.Bitmap"), int.class);
            
            // GL_TEXTURE_2D target constant is 3553
            texImageMethod.invoke(null, 3553, 0, bitmapObj, 0);

            // 5. Clean up the native Android bitmap allocation block immediately
            bitmapObj.getClass().getMethod("recycle").invoke(bitmapObj);

            // 6. Finalize texture state filters and wrapping behaviors
            texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            texture.setFilter(TextureFilter.Linear, useMipMaps ? TextureFilter.MipMapLinearLinear : TextureFilter.Linear);
            
            if (useMipMaps) {
                Gdx.gl.glGenerateMipmap(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D);
            }

            return texture;
        } catch (ClassNotFoundException e) {
            // Desktop Fallback: If not on Android, throw an exception or delegate to a desktop decoder
            Gdx.app.error("GLTF", "Not running on Android or BitmapFactory missing. Cannot decode WebP natively.", e);
            return null;
        } catch (Exception e) {
            Gdx.app.error("GLTF", "Error dynamically decoding WebP texture via reflection: " + e.getMessage(), e);
            return null;
        }
    }
    
    public TextureDescriptor<Texture> getTexture(GLTFTextureInfo glMap) {
        Gdx.app.log("GLTF", "DEBUG getTexture: glMap.index=" + glMap.index);
        GLTFTexture glTexture = glTextures.get(glMap.index);
        Gdx.app.log("GLTF", "DEBUG getTexture: glTexture.source=" + glTexture.source);
        
        // Check for Basis extension
        Integer basisSource = null;
        if (glTexture.extensions != null) {
            KHRTextureBasisu basisu = glTexture.extensions.get(KHRTextureBasisu.class, KHRTextureBasisu.EXTENSION_NAME);
            if (basisu != null) {
                basisSource = basisu.source;
            }
        }
        Gdx.app.log("GLTF", "DEBUG getTexture: basisu.source=" + basisSource);
        
        // Check for WebP extension
        Integer webpSource = null;
        if (glTexture.extensions != null) {
            EXTTextureWebp webp = glTexture.extensions.get(EXTTextureWebp.class, EXTTextureWebp.EXT);
            if (webp != null) {
                webpSource = webp.source;
            }
        }
        Gdx.app.log("GLTF", "DEBUG getTexture: webp.source=" + webpSource);
        
        // Determine the actual source index for texture lookup
        // For Basis/WebP textures, the source is stored in the extension, not glTexture.source
        Integer sourceIndex = glTexture.source;
        if (sourceIndex == null && basisSource != null) {
            sourceIndex = basisSource;
        }
        if (sourceIndex == null && webpSource != null) {
            sourceIndex = webpSource;
        }
        Gdx.app.log("GLTF", "DEBUG getTexture: final sourceIndex=" + sourceIndex);
        
        TextureDescriptor<Texture> textureDescriptor = new TextureDescriptor<Texture>();

        boolean useMipMaps;
        if(glTexture.sampler != null){
            GLTFSampler glSampler = glSamplers.get(glTexture.sampler);
            GLTFTypes.mapTextureSampler(textureDescriptor, glSampler);
            useMipMaps = GLTFTypes.isMipMapFilter(glSampler);
        }else{
            // default sampler options.
            // https://github.com/KhronosGroup/glTF/blob/master/specification/2.0/README.md#texture
            textureDescriptor.minFilter = TextureFilter.Linear;
            textureDescriptor.magFilter = TextureFilter.Linear;
            textureDescriptor.uWrap = TextureWrap.Repeat;
            textureDescriptor.vWrap = TextureWrap.Repeat;
            useMipMaps = false;
        }
        
        Gdx.app.log("GLTF", "DEBUG getTexture: useMipMaps=" + useMipMaps + " mapSize=" + (useMipMaps ? texturesMipmap.size : texturesSimple.size));
        ObjectMap<Integer, Texture> textureMap = useMipMaps ? texturesMipmap : texturesSimple;
        
        Gdx.app.log("GLTF", "DEBUG getTexture: calling textureMap.get(sourceIndex=" + sourceIndex + ")");
        Texture texture = textureMap.get(sourceIndex);
        Gdx.app.log("GLTF", "DEBUG getTexture: texture=" + (texture != null ? "found" : "NULL"));
        if(texture == null){
            throw new GLTFRuntimeException("texture not loaded for source=" + sourceIndex);
        }
        textureDescriptor.texture = texture;
        return textureDescriptor;
    }

    @Override
    public void dispose() {
        for(Entry<Integer, Texture> e : texturesSimple){
            e.value.dispose();
        }
        texturesSimple.clear();
        for(Entry<Integer, Texture> e : texturesMipmap){
            e.value.dispose();
        }
        texturesMipmap.clear();
    }

    public Array<Texture> getTextures(Array<Texture> textures) {
        for(Entry<Integer, Texture> e : texturesSimple){
            textures.add(e.value);
        }
        for(Entry<Integer, Texture> e : texturesMipmap){
            textures.add(e.value);
        }
        return textures;
    }
}
