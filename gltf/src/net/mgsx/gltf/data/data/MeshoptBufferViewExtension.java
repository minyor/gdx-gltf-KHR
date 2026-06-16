package net.mgsx.gltf.data.data;

public class MeshoptBufferViewExtension {
	public static final String EXTENSION_NAME = "EXT_meshopt_compression";
	
	public int buffer;
	public int byteOffset;
	public int byteLength;
	public int byteStride;
	public String mode;
	public String filter;
	public int count;
	
	public enum Mode {
		ATTRIBUTES, TRIANGLES, INDICES
	}
	
	public enum Filter {
		NONE, OCTAHEDRAL, QUATERNION, EXPONENTIAL, COLOR
	}
	
	public Mode getMode() {
		if (mode == null) return Mode.ATTRIBUTES;
		try {
			return Mode.valueOf(mode);
		} catch (IllegalArgumentException e) {
			return Mode.ATTRIBUTES;
		}
	}
	
	public Filter getFilter() {
		if (filter == null) return Filter.NONE;
		try {
			return Filter.valueOf(filter);
		} catch (IllegalArgumentException e) {
			return Filter.NONE;
		}
	}
}