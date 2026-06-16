package net.mgsx.gltf.data.texture;

import net.mgsx.gltf.data.GLTFEntity;

/**
 * EXT_texture_webp extension allows glTF to use WebP images.
 * https://github.com/KhronosGroup/glTF/blob/main/extensions/vulkan/EXT_texture_webp/
 */
public class EXTTextureWebp extends GLTFEntity {
    public static final String EXT = "EXT_texture_webp";
    
    /**
     * The index of a WebP image.
     */
    public Integer source;
}
