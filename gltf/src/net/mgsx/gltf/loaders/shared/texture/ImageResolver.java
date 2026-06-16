package net.mgsx.gltf.loaders.shared.texture;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import net.mgsx.gltf.data.texture.GLTFImage;
import net.mgsx.gltf.loaders.shared.data.DataFileResolver;

public class ImageResolver implements Disposable {
	
	private Array<Pixmap> pixmaps = new Array<Pixmap>();
	private Array<byte[]> rawBytes = new Array<byte[]>(); // For Basis textures
	
	private DataFileResolver dataFileResolver;
	
	public ImageResolver(DataFileResolver dataFileResolver) {
		super();
		this.dataFileResolver = dataFileResolver;
	}

	public void load(Array<GLTFImage> glImages) {
		if(glImages != null){
			for(int i=0 ; i<glImages.size ; i++){
				GLTFImage glImage = glImages.get(i);
				Gdx.app.log("GLTF", "ImageResolver: Loading image " + i + ", bufferView=" + glImage.bufferView + ", uri=" + glImage.uri);
				Pixmap pixmap = dataFileResolver.load(glImage);
				Gdx.app.log("GLTF", "ImageResolver: Image " + i + " loaded, pixmap=" + pixmap);
				if (pixmap != null) {
					pixmaps.add(pixmap);
				} else {
					// For Basis/KTX2 textures, store a placeholder null
					pixmaps.add(null);
				}
				// Store raw bytes for Basis textures
				byte[] raw = dataFileResolver.getRawBytes(glImage);
				Gdx.app.log("GLTF", "ImageResolver: Image " + i + " rawBytes=" + (raw != null ? raw.length : 0));
				rawBytes.add(raw);
			}
		}
	}
	
	public Pixmap get(int index) {
		return pixmaps.get(index);
	}
	
	/**
	 * Get raw bytes for an image (used for Basis textures).
	 * @param index The image index
	 * @return Raw bytes, or null if not available
	 */
	public byte[] getRawBytes(int index) {
		return rawBytes.get(index);
	}
	
	@Override
	public void dispose() {
		for(Pixmap pixmap : pixmaps){
			if (pixmap != null) {
				pixmap.dispose();
			}
		}
		pixmaps.clear();
		rawBytes.clear();
	}

	public void clear() {
		pixmaps.clear();
		rawBytes.clear();
	}

	public Array<Pixmap> getPixmaps(Array<Pixmap> array) {
		array.addAll(pixmaps);
		return array;
	}
}
