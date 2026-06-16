package net.mgsx.gltf.data.texture;

import net.mgsx.gltf.data.GLTFEntity;

/**
 * KHR_texture_basisu extension data model.
 * 
 * This extension allows textures to be stored in Basis Universal format,
 * which is a compact multi-format texture compression that can be transcoded
 * to various GPU-native compressed formats (ASTC, ETC2, BC7, etc.).
 * 
 * @see <a href="https://github.com/KhronosGroup/glTF/blob/main/extensions/vulkan/KHR_texture_basisu/">KHR_texture_basisu</a>
 */
public class KHRTextureBasisu extends GLTFEntity {
	public static final String EXTENSION_NAME = "KHR_texture_basisu";
	
	/** Index of the Basis Universal compressed image */
	public Integer source;
}