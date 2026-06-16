package net.mgsx.gltf.loaders.shared.texture;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Constructor;
import com.badlogic.gdx.utils.reflect.ReflectionException;

import net.mgsx.gltf.loaders.exceptions.GLTFRuntimeException;
import net.mgsx.gltf.loaders.exceptions.GLTFUnsupportedException;

/**
 * Hack {@link Pixmap} loading from binary data via reflection in order to avoid GWT compilation issues. 
 */
public class PixmapBinaryLoaderHack {

	public static Pixmap load(byte [] encodedData, int offset, int len){
		Gdx.app.log("GLTF", "PixmapBinaryLoaderHack.load: START, offset=" + offset + ", len=" + len);
		if(Gdx.app.getType() == ApplicationType.WebGL){
			throw new GLTFUnsupportedException("load pixmap from bytes not supported for WebGL");
		}else{
			// Log first few bytes to identify image format
			String header = "";
			for (int i = 0; i < Math.min(8, len); i++) {
				header += String.format("0x%02X ", encodedData[offset + i]);
			}
			Gdx.app.log("GLTF", "PixmapBinaryLoaderHack.load: Header bytes: " + header);
			
			// call new Pixmap(encodedData, offset, len); via reflection to
			// avoid compilation error with GWT.
			try {
				Gdx.app.log("GLTF", "PixmapBinaryLoaderHack.load: Calling ClassReflection.getConstructor");
				Constructor constructor = ClassReflection.getConstructor(Pixmap.class, byte[].class, int.class, int.class);
				Gdx.app.log("GLTF", "PixmapBinaryLoaderHack.load: Constructor found, calling newInstance");
				Pixmap pixmap = (Pixmap)constructor.newInstance(encodedData, offset, len);
				Gdx.app.log("GLTF", "PixmapBinaryLoaderHack.load: Pixmap created successfully: " + pixmap);
				return pixmap;
			} catch (ReflectionException e) {
				Gdx.app.error("GLTF", "PixmapBinaryLoaderHack.load: ReflectionException: " + e.getMessage());
				Gdx.app.error("GLTF", "PixmapBinaryLoaderHack.load: Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "null"));
				throw new GLTFRuntimeException(e);
			}
		}
	}
}
