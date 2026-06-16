package net.mgsx.gltf.meshopt;

import java.nio.ByteBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * Java wrapper for meshoptimizer decoder via JNI.
 * Provides decompression for EXT_meshopt_compression glTF extension.
 */
public class MeshoptDecoder {
	private static boolean available = false;
	
	static {
		try {
			System.loadLibrary("gdx-gltf-native");
			available = true;
		} catch (Throwable e) {
			// Native library not available - will use fallback
			Gdx.app.log("GLTF", "MeshoptDecoder native library not available: " + e.getMessage());
			available = false;
		}
	}
	
	/**
	 * @return true if native meshopt decoder is available
	 */
	public static boolean isAvailable() {
		return available;
	}
	
	// Native decoder methods
	private static native int nativeDecodeVertexBuffer(ByteBuffer dest, int vertexCount, int vertexSize, ByteBuffer source);
	private static native int nativeDecodeIndexBuffer(ByteBuffer dest, int indexCount, int indexSize, ByteBuffer source);
	private static native int nativeDecodeIndexSequence(ByteBuffer dest, int indexCount, int indexSize, ByteBuffer source);
	private static native void nativeDecodeFilterOct(ByteBuffer buffer, int count, int stride);
	private static native void nativeDecodeFilterQuat(ByteBuffer buffer, int count, int stride);
	private static native void nativeDecodeFilterExp(ByteBuffer buffer, int count, int stride);
	private static native void nativeDecodeFilterColor(ByteBuffer buffer, int count, int stride);
	
	/**
	 * Decompress a vertex buffer encoded with meshopt_encodeVertexBuffer.
	 * @param dest Destination buffer (must have space for vertexCount * vertexSize bytes)
	 * @param vertexCount Number of vertices
	 * @param vertexSize Size of each vertex in bytes (must be multiple of 4)
	 * @param source Compressed source buffer
	 * @return 0 on success, negative error code on failure
	 */
	public static int decodeVertexBuffer(ByteBuffer dest, int vertexCount, int vertexSize, ByteBuffer source) {
		return nativeDecodeVertexBuffer(dest, vertexCount, vertexSize, source);
	}
	
	/**
	 * Decompress an index buffer encoded with meshopt_encodeIndexBuffer (triangle list).
	 * @param dest Destination buffer (must have space for indexCount * indexSize bytes)
	 * @param indexCount Number of indices
	 * @param indexSize Size of each index in bytes (2 or 4)
	 * @param source Compressed source buffer
	 * @return 0 on success, negative error code on failure
	 */
	public static int decodeIndexBuffer(ByteBuffer dest, int indexCount, int indexSize, ByteBuffer source) {
		return nativeDecodeIndexBuffer(dest, indexCount, indexSize, source);
	}
	
	/**
	 * Decompress an index sequence encoded with meshopt_encodeIndexSequence.
	 * @param dest Destination buffer (must have space for indexCount * indexSize bytes)
	 * @param indexCount Number of indices
	 * @param indexSize Size of each index in bytes (2 or 4)
	 * @param source Compressed source buffer
	 * @return 0 on success, negative error code on failure
	 */
	public static int decodeIndexSequence(ByteBuffer dest, int indexCount, int indexSize, ByteBuffer source) {
		return nativeDecodeIndexSequence(dest, indexCount, indexSize, source);
	}
	
	/**
	 * Apply octahedral filter decoding (for normals/tangents).
	 * @param buffer Buffer to decode in-place
	 * @param count Number of vectors
	 * @param stride Stride in bytes (4 or 8)
	 */
	public static void decodeFilterOct(ByteBuffer buffer, int count, int stride) {
		nativeDecodeFilterOct(buffer, count, stride);
	}
	
	/**
	 * Apply quaternion filter decoding.
	 * @param buffer Buffer to decode in-place
	 * @param count Number of quaternions
	 * @param stride Stride in bytes (must be 8)
	 */
	public static void decodeFilterQuat(ByteBuffer buffer, int count, int stride) {
		nativeDecodeFilterQuat(buffer, count, stride);
	}
	
	/**
	 * Apply exponential filter decoding.
	 * @param buffer Buffer to decode in-place
	 * @param count Number of vectors
	 * @param stride Stride in bytes (must be multiple of 4)
	 */
	public static void decodeFilterExp(ByteBuffer buffer, int count, int stride) {
		nativeDecodeFilterExp(buffer, count, stride);
	}
	
	/**
	 * Apply color filter decoding (YCoCg).
	 * @param buffer Buffer to decode in-place
	 * @param count Number of colors
	 * @param stride Stride in bytes (4 or 8)
	 */
	public static void decodeFilterColor(ByteBuffer buffer, int count, int stride) {
		nativeDecodeFilterColor(buffer, count, stride);
	}
}