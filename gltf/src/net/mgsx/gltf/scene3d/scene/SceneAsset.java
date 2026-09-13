package net.mgsx.gltf.scene3d.scene;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import net.mgsx.gltf.data.GLTF;

/**
 * gdx view of an asset file : Model, Camera (as template), lights (as template), textures
 */
public class SceneAsset implements Disposable 
{
	/** underlying GLTF data structure, null if loaded without "withData" option. */
	public GLTF data;
	
	public Array<SceneModel> scenes;
	public SceneModel scene;

	public Array<Animation> animations;
	public int maxBones;
	
	/** Keep track of loaded texture in order to dispose them. Textures handled by AssetManager are excluded. */
	public Array<Texture> textures;
	
	/** Keep track of loaded pixmaps in order to dispose them. Pixmaps handled by AssetManager are excluded. */
	public Array<Pixmap> pixmaps;
	
	/** Keep track of loaded meshes in order to dispose them. */
	public Array<Mesh> meshes;
	
	@Override
	public void dispose() {
		// Null-guard every tracked element: a partially-decoded asset (e.g. a compressed/basis texture
		// whose transcode failed) can leave null entries in these arrays, which previously crashed dispose()
		// with a NullPointerException the first time a screen was torn down. Skipping nulls makes dispose()
		// safe on any asset regardless of decode success. Generic - no per-asset or format special-casing.
		if(scenes != null){
			for(SceneModel scene : scenes){
				if(scene != null) scene.dispose();
			}
		}
		if(textures != null){
			for(Texture texture : textures){
				if(texture != null) texture.dispose();
			}
		}
		if(pixmaps != null){
			for(Pixmap pixmap : pixmaps){
				if(pixmap != null) pixmap.dispose();
			}
		}
		if(meshes != null){
			for(Mesh mesh : meshes){
				if(mesh != null) mesh.dispose();
			}
		}
	}
}
