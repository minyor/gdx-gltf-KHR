package net.mgsx.gltf.loaders.shared.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.badlogic.gdx.utils.ObjectMap;

import net.mgsx.gltf.data.GLTF;
import net.mgsx.gltf.data.data.GLTFAccessor;
import net.mgsx.gltf.data.data.GLTFBufferView;
import net.mgsx.gltf.data.data.MeshoptBufferViewExtension;
import net.mgsx.gltf.loaders.exceptions.GLTFUnsupportedException;
import net.mgsx.gltf.loaders.shared.GLTFTypes;
import net.mgsx.gltf.meshopt.MeshoptDecoder;

public class DataResolver {
	
	private GLTF glModel;
	private DataFileResolver dataFileResolver;
	
	public DataResolver(GLTF glModel, DataFileResolver dataFileResolver) {
		super();
		this.glModel = glModel;
		this.dataFileResolver = dataFileResolver;
	}
	
	public GLTFAccessor getAccessor(int accessorID) {
		return glModel.accessors.get(accessorID);
	}

	public float[] readBufferFloat(int accessorID) {
		GLTFAccessor accessor = glModel.accessors.get(accessorID);
		AccessorBuffer accessorBuffer = getAccessorBuffer(accessor);
		ByteBuffer bytes = accessorBuffer.prepareForReading();
		
		// MANDATORY FOR GLTF: Force byte buffer to match glTF specification specification (Little Endian)
		// This guarantees that 1.0f (3F 80 00 00) reads as 1.0f on both Android and PlayStation!
		bytes.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		
		int nbFloatsPerVertex = GLTFTypes.accessorTypeSize(accessor);
		int totalElements = accessor.count * nbFloatsPerVertex;
		float[] data = new float[totalElements];

		// Check what primitive data type gltfpack actually wrote for this accessor channel
		if (accessor.componentType == 5126) { // GL_FLOAT (Standard, 4 Bytes)
			int nbBytesToSkip = accessorBuffer.getByteStride() - nbFloatsPerVertex * 4;
			if(nbBytesToSkip == 0){
				int currentPos = bytes.position();
				ByteBuffer safeSlice = bytes.duplicate();
				// Ensure the duplicated slice inherits the forced Little-Endian byte orientation
				safeSlice.order(java.nio.ByteOrder.LITTLE_ENDIAN);
				safeSlice.position(currentPos);
				safeSlice.limit(currentPos + (totalElements * 4));
				safeSlice.asFloatBuffer().get(data);
				bytes.position(currentPos + (totalElements * 4));
			}else{
				for(int i=0 ; i<accessor.count ; i++){
					for(int j=0 ; j<nbFloatsPerVertex ; j++){
						data[i*nbFloatsPerVertex+j] = bytes.getFloat();
					}
					bytes.position(bytes.position() + nbBytesToSkip);
				}
			}
		} 
		else if (accessor.componentType == 5122) { // GL_SHORT (Compressed Signed Short, 2 Bytes)
			int nbBytesToSkip = accessorBuffer.getByteStride() - nbFloatsPerVertex * 2;
			boolean isQuaternion = (nbFloatsPerVertex == 4);
			
			// Dynamically read glTF bounds to scale translations/scales without hardcoding factors!
			float[] minBounds = (accessor.min != null && accessor.min.length >= nbFloatsPerVertex) ? accessor.min : null;
			float[] maxBounds = (accessor.max != null && accessor.max.length >= nbFloatsPerVertex) ? accessor.max : null;

			for(int i=0 ; i<accessor.count ; i++){
				for(int j=0 ; j<nbFloatsPerVertex ; j++){
					short rawShort = bytes.getShort();
					if (isQuaternion) {
						// Rotations decode strictly between -1.0 and 1.0 per glTF spec
						data[i*nbFloatsPerVertex+j] = (float) rawShort / 32767.0f;
					} else if (minBounds != null && maxBounds != null) {
						// Standard glTF dequantization fallback using accessor limits
						float normalizedFraction = ((float) rawShort + 32128.0f) / 65535.0f;
						data[i*nbFloatsPerVertex+j] = minBounds[j] + (normalizedFraction * (maxBounds[j] - minBounds[j]));
					} else {
						// Fallback if bounds are missing
						data[i*nbFloatsPerVertex+j] = (float) rawShort;
					}
				}
				bytes.position(bytes.position() + nbBytesToSkip);
			}
		} 
		else if (accessor.componentType == 5123) { // GL_UNSIGNED_SHORT (Compressed Unsigned Short, 2 Bytes)
			int nbBytesToSkip = accessorBuffer.getByteStride() - nbFloatsPerVertex * 2;
			for(int i=0 ; i<accessor.count ; i++){
				for(int j=0 ; j<nbFloatsPerVertex ; j++){
					data[i*nbFloatsPerVertex+j] = (float) (bytes.getShort() & 0xFFFF);
				}
				bytes.position(bytes.position() + nbBytesToSkip);
			}
		} 
		else if (accessor.componentType == 5121) { // GL_UNSIGNED_BYTE (Compressed Unsigned Byte, 1 Byte)
			int nbBytesToSkip = accessorBuffer.getByteStride() - nbFloatsPerVertex;
			for(int i=0 ; i<accessor.count ; i++){
				for(int j=0 ; j<nbFloatsPerVertex ; j++){
					data[i*nbFloatsPerVertex+j] = (float) (bytes.get() & 0xFF);
				}
				bytes.position(bytes.position() + nbBytesToSkip);
			}
		} 
		else {
			throw new com.badlogic.gdx.utils.GdxRuntimeException("Unsupported component type inside readBufferFloat: " + accessor.componentType);
		}
		
		return data;
	}
	
	public int[] readBufferUByte(int accessorID) {
		GLTFAccessor accessor = glModel.accessors.get(accessorID);
		AccessorBuffer accessorBuffer = getAccessorBuffer(accessor);
		ByteBuffer bytes = accessorBuffer.prepareForReading();
		
		// (Single bytes are endian-independent, but keeping it uniform protects sub-slices)
		bytes.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		
		int nbBytesPerVertex = GLTFTypes.accessorTypeSize(accessor);
		int[] data = new int[accessor.count * nbBytesPerVertex];
		
		int nbBytesToSkip = accessorBuffer.getByteStride() - nbBytesPerVertex;
		if(nbBytesToSkip == 0){
			int currentPos = bytes.position();
			ByteBuffer safeSlice = bytes.duplicate();
			safeSlice.order(java.nio.ByteOrder.LITTLE_ENDIAN);
			safeSlice.position(currentPos);
			safeSlice.limit(currentPos + data.length);
			
			for(int i=0 ; i<data.length ; i++){
				data[i] = safeSlice.get() & 0xFF;
			}
			bytes.position(currentPos + data.length);
		}else{
			for(int i=0 ; i<accessor.count ; i++){
				for(int j=0 ; j<nbBytesPerVertex ; j++){
					data[i*nbBytesPerVertex+j] = bytes.get() & 0xFF;
				}
				bytes.position(bytes.position() + nbBytesToSkip);
			}
		}
		return data;
	}
	
	public int[] readBufferUShort(int accessorID) {
		GLTFAccessor accessor = glModel.accessors.get(accessorID);
		AccessorBuffer accessorBuffer = getAccessorBuffer(accessor);
		ByteBuffer bytes = accessorBuffer.prepareForReading(); 
		
		// MANDATORY FOR GLTF: Force 16-bit short integers to evaluate using Little-Endian order!
		bytes.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		
		int nbShortsPerVertex = GLTFTypes.accessorTypeSize(accessor);
		int[] data = new int[accessor.count * nbShortsPerVertex];
		
		int nbBytesToSkip = accessorBuffer.getByteStride() - nbShortsPerVertex * 2;
		if(nbBytesToSkip == 0){
			int currentPos = bytes.position();
			ByteBuffer safeSlice = bytes.duplicate();
			safeSlice.order(java.nio.ByteOrder.LITTLE_ENDIAN);
			safeSlice.position(currentPos);
			safeSlice.limit(currentPos + (data.length * 2)); 
			
			java.nio.ShortBuffer shorts = safeSlice.asShortBuffer();
			for(int i=0 ; i<data.length ; i++){
				data[i] = shorts.get() & 0xFFFF;
			}
			bytes.position(currentPos + (data.length * 2));
		}else{
			for(int i=0 ; i<accessor.count ; i++){
				for(int j=0 ; j<nbShortsPerVertex ; j++){
					data[i*nbShortsPerVertex+j] = bytes.getShort() & 0xFFFF;
				}
				bytes.position(bytes.position() + nbBytesToSkip);
			}
		}
		return data;
	}
	
	public float[] readBufferUShortAsFloat(int accessorID) {
		int[] intBuffer = readBufferUShort(accessorID);
		float[] floatBuffer = new float[intBuffer.length];
		for (int i = 0; i < intBuffer.length; i++) {
			floatBuffer[i] = intBuffer[i] / 65535f;
		}
		return floatBuffer;
	}

	public float[] readBufferUByteAsFloat(int accessorID) {
		int[] intBuffer = readBufferUByte(accessorID);
		float[] floatBuffer = new float[intBuffer.length];
		for (int i = 0; i < intBuffer.length; i++) {
			floatBuffer[i] = intBuffer[i] / 255f;
		}
		return floatBuffer;
	}

	public FloatBuffer getBufferFloat(int accessorID) {
		return getBufferFloat(glModel.accessors.get(accessorID));
	}

	public GLTFBufferView getBufferView(int bufferViewID) {
		return glModel.bufferViews.get(bufferViewID);
	}

	public FloatBuffer getBufferFloat(GLTFAccessor glAccessor) {
		return getBufferByte(glAccessor).asFloatBuffer();
	}

	public IntBuffer getBufferInt(GLTFAccessor glAccessor) {
		return getBufferByte(glAccessor).asIntBuffer();
	}

	public ShortBuffer getBufferShort(GLTFAccessor glAccessor) {
		return getBufferByte(glAccessor).asShortBuffer();
	}

	public ByteBuffer getBufferByte(GLTFAccessor glAccessor) {
		AccessorBuffer buffer = getAccessorBuffer(glAccessor);
		return buffer.prepareForReading();
	}

	public AccessorBuffer getAccessorBuffer(GLTFAccessor glAccessor) {
		AccessorBuffer buffer;
		if (glAccessor.bufferView != null) {
			GLTFBufferView bufferView = glModel.bufferViews.get(glAccessor.bufferView);
			
			// Check for meshopt compression extension
			MeshoptBufferViewExtension meshopt = getMeshoptExtension(bufferView);
			if (meshopt != null) {
				// Decompress using meshopt decoder
				buffer = decompressMeshoptBuffer(glAccessor, bufferView, meshopt);
			} else {
				buffer = AccessorBuffer.fromBufferView(glAccessor, bufferView, dataFileResolver);
			}
		} else {
			buffer = AccessorBuffer.fromZeros(glAccessor);
		}
		if (glAccessor.sparse != null) {
			buffer.prepareForWriting();
			patchSparseValues(glAccessor, buffer);
		}
		buffer.prepareForReading();
		return buffer;
	}
	
	/**
	 * Cache for decompressed buffer views to avoid redundant decompression.
	 */
	private ObjectMap<GLTFBufferView, ByteBuffer> meshoptDecompressionCache = new ObjectMap<GLTFBufferView, ByteBuffer>();
	
	/**
	 * Get meshopt extension from buffer view if present.
	 */
	private MeshoptBufferViewExtension getMeshoptExtension(GLTFBufferView bufferView) {
		if (bufferView.extensions != null) {
			return bufferView.extensions.get(MeshoptBufferViewExtension.class, MeshoptBufferViewExtension.EXTENSION_NAME);
		}
		return null;
	}
	
	/**
	 * Decompress a meshopt-compressed buffer view.
	 * Falls back to uncompressed data from the original buffer view if native decoder is unavailable.
	 */
	private AccessorBuffer decompressMeshoptBuffer(GLTFAccessor glAccessor, GLTFBufferView bufferView, MeshoptBufferViewExtension meshopt) {
		com.badlogic.gdx.Gdx.app.log("GLTF", "DEBUG decompressMeshoptBuffer: START bufferView=" + (bufferView != null ? "non-null" : "NULL"));
		
		if (bufferView == null) {
			com.badlogic.gdx.Gdx.app.error("GLTF", "ERROR decompressMeshoptBuffer: bufferView is null!");
			return AccessorBuffer.fromZeros(glAccessor);
		}
		
		if (!MeshoptDecoder.isAvailable()) {
			com.badlogic.gdx.Gdx.app.log("GLTF", "DEBUG decompressMeshoptBuffer: MeshoptDecoder NOT available, falling back to uncompressed");
			return AccessorBuffer.fromBufferView(glAccessor, bufferView, dataFileResolver);
		}
		
		// 1. Check cache using the bufferView as the unique key
		if (meshoptDecompressionCache.containsKey(bufferView)) {
			com.badlogic.gdx.Gdx.app.log("GLTF", "DEBUG decompressMeshoptBuffer: CACHE HIT");
			ByteBuffer cachedBuffer = meshoptDecompressionCache.get(bufferView);
			// FIX: glAccessor.byteOffset is already mapped to the decompressed stream layout
			return AccessorBuffer.fromByteBufferAt(glAccessor, cachedBuffer.duplicate(), meshopt.byteStride, glAccessor.byteOffset);
		}
		
		com.badlogic.gdx.Gdx.app.log("GLTF", "DEBUG decompressMeshoptBuffer: CACHE MISS");

		// 2. Fetch the raw compressed storage container buffer
		// CRITICAL FIX: Use meshopt.buffer index, NOT bufferView.buffer index!
		int rawBufferIndex = meshopt.buffer;
		ByteBuffer globalBuffer = dataFileResolver.getBuffer(rawBufferIndex);
		
		// 3. CRITICAL FIX: Read boundary values from the meshopt extension instance, NOT the base bufferView!
		int compressedOffset = meshopt.byteOffset;
		int compressedLength = meshopt.byteLength;
		int stride = meshopt.byteStride;
		int count = meshopt.count;
		int decompressedSize = count * stride;

		com.badlogic.gdx.Gdx.app.log("GLTF", "DEBUG decompressMeshoptBuffer: slicing compressed bounds offset="
			+ compressedOffset + " length=" + compressedLength + " global capacity=" + globalBuffer.capacity());

		// 4. Safely isolate the compressed byte array block
		globalBuffer.position(compressedOffset);
		ByteBuffer compressedSlice = globalBuffer.slice();
		compressedSlice.limit(compressedLength);
		compressedSlice.order(ByteOrder.nativeOrder());

		// 5. Copy compressed data to a direct buffer (JNI requires direct buffers)
		ByteBuffer directCompressed = ByteBuffer.allocateDirect(compressedLength);
		directCompressed.order(ByteOrder.LITTLE_ENDIAN);
		directCompressed.put(compressedSlice);
		directCompressed.flip();

		// 6. Allocate a clean, direct target output buffer for the native decoder
		ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(decompressedSize);
		decompressedBuffer.order(ByteOrder.LITTLE_ENDIAN);

		// 7. Invoke native meshopt JNI decode based on mode
		int result;
		switch (meshopt.getMode()) {
			case ATTRIBUTES:
				result = MeshoptDecoder.decodeVertexBuffer(decompressedBuffer, count, stride, directCompressed);
				break;
			case TRIANGLES:
				result = MeshoptDecoder.decodeIndexBuffer(decompressedBuffer, count, stride, directCompressed);
				break;
			case INDICES:
				result = MeshoptDecoder.decodeIndexSequence(decompressedBuffer, count, stride, directCompressed);
				break;
			default:
				result = MeshoptDecoder.decodeVertexBuffer(decompressedBuffer, count, stride, directCompressed);
				break;
		}
		
		if (result != 0) {
			com.badlogic.gdx.Gdx.app.error("GLTF", "ERROR: Meshopt native vertex decoding failed with code: " + result);
			return AccessorBuffer.fromZeros(glAccessor);
		}

		// Apply meshopt filter to restore octahedral/quaternion/exponential encodings
		if (meshopt.filter != null && !meshopt.filter.equals("NONE")) {
			decompressedBuffer.clear();
			switch (meshopt.getFilter()) {
				case OCTAHEDRAL:
					com.badlogic.gdx.Gdx.app.log("GLTF", "Applying native Meshopt OCTAHEDRAL decode filter...");
					MeshoptDecoder.decodeFilterOct(decompressedBuffer, count, stride);
					break;
				case QUATERNION:
					com.badlogic.gdx.Gdx.app.log("GLTF", "Applying native Meshopt QUATERNION decode filter...");
					MeshoptDecoder.decodeFilterQuat(decompressedBuffer, count, stride);
					break;
				case EXPONENTIAL:
					com.badlogic.gdx.Gdx.app.log("GLTF", "Applying native Meshopt EXPONENTIAL decode filter...");
					MeshoptDecoder.decodeFilterExp(decompressedBuffer, count, stride);
					break;
				case COLOR:
					com.badlogic.gdx.Gdx.app.log("GLTF", "Applying native Meshopt COLOR decode filter...");
					MeshoptDecoder.decodeFilterColor(decompressedBuffer, count, stride);
					break;
				default:
					break;
			}
		}

		// 8. Store the result in the cache to avoid re-decoding this view for other accessors
		decompressedBuffer.clear(); // Reset positions back to 0 before saving
		meshoptDecompressionCache.put(bufferView, decompressedBuffer);

		com.badlogic.gdx.Gdx.app.log("GLTF", "DEBUG decompressMeshoptBuffer: SUCCESS");
		return AccessorBuffer.fromByteBufferAt(glAccessor, decompressedBuffer.duplicate(), stride, glAccessor.byteOffset);
	}
	
	/**
	 * Apply meshopt filter to decompressed data.
	 */
	private void applyFilter(ByteBuffer buffer, MeshoptBufferViewExtension meshopt) {
		MeshoptBufferViewExtension.Filter filter = meshopt.getFilter();
		if (filter == MeshoptBufferViewExtension.Filter.NONE) {
			return;
		}
		
		// Reset position to apply filter
		buffer.rewind();
		
		switch (filter) {
			case OCTAHEDRAL:
				MeshoptDecoder.decodeFilterOct(buffer, meshopt.count, meshopt.byteStride);
				break;
			case QUATERNION:
				MeshoptDecoder.decodeFilterQuat(buffer, meshopt.count, meshopt.byteStride);
				break;
			case EXPONENTIAL:
				MeshoptDecoder.decodeFilterExp(buffer, meshopt.count, meshopt.byteStride);
				break;
			case COLOR:
				MeshoptDecoder.decodeFilterColor(buffer, meshopt.count, meshopt.byteStride);
				break;
			default:
				break;
		}
		
		// Reset position after filter
		buffer.rewind();
	}

	private void patchSparseValues(GLTFAccessor glAccessor, AccessorBuffer outputBuffer) {
		GLTFBufferView indicesBufferView = getBufferView(glAccessor.sparse.indices.bufferView);
		ByteBuffer indicesBuffer = dataFileResolver.getBuffer(indicesBufferView.buffer).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
		indicesBuffer.position(glAccessor.sparse.indices.byteOffset + indicesBufferView.byteOffset);
		GLTFBufferView replacementValueBufferView = getBufferView(glAccessor.sparse.values.bufferView);
		ByteBuffer replacementValuesBuffer = dataFileResolver.getBuffer(replacementValueBufferView.buffer).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
		replacementValuesBuffer.position(glAccessor.sparse.values.byteOffset + replacementValueBufferView.byteOffset);
		int bytesPerValue = GLTFTypes.accessorStrideSize(glAccessor);
		byte[] replacementValueBytes = new byte[bytesPerValue];
		for (int i = 0; i < glAccessor.sparse.count; i++) {
			int indexToReplace;
			switch (glAccessor.sparse.indices.componentType) {
				case GLTFTypes.C_UBYTE:
					indexToReplace = ((int) indicesBuffer.get()) & 0xff;
					break;
				case GLTFTypes.C_USHORT:
					indexToReplace = ((int) indicesBuffer.getShort()) & 0xffff;
					break;
				case GLTFTypes.C_UINT: {
					// java does not have uint, so read as signed long
					long asLong = ((long) indicesBuffer.getInt()) & 0xffffffffL;
					if (asLong > Integer.MAX_VALUE) {
						throw new GLTFUnsupportedException("very large indices can not be parsed");
					}
					indexToReplace = (int) asLong;
					break;
				}
				default:
					throw new GLTFUnsupportedException("unsupported indices type");
			}
			replacementValuesBuffer.get(replacementValueBytes);
			ByteBuffer data = outputBuffer.getData();
			int elementOffset = indexToReplace * outputBuffer.getByteStride();
			data.position(outputBuffer.getByteOffset() + elementOffset);
			data.put(replacementValueBytes);
		}
	}

	public ByteBuffer getBufferByte(GLTFBufferView bufferView) {
		ByteBuffer bytes = dataFileResolver.getBuffer(bufferView.buffer);
		bytes.position(bufferView.byteOffset);
		return bytes;
	}
}
