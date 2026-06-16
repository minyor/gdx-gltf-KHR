package net.mgsx.gltf.loaders.shared.data;

import java.nio.ByteBuffer;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

import net.mgsx.gltf.data.GLTF;
import net.mgsx.gltf.data.texture.GLTFImage;

public interface DataFileResolver {
	public void load(FileHandle file);
	public GLTF getRoot();
	public ByteBuffer getBuffer(int buffer);
	public Pixmap load(GLTFImage glImage);
	
	/**
	 * Get raw bytes for an image (used for Basis textures).
	 * @param glImage The image
	 * @return Raw bytes, or null if not available
	 */
	public default byte[] getRawBytes(GLTFImage glImage) {
		return null;
	}
}
