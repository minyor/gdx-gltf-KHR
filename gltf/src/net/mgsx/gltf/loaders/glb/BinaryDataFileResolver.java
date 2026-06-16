package net.mgsx.gltf.loaders.glb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.LittleEndianInputStream;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.StreamUtils;

import net.mgsx.gltf.data.GLTF;
import net.mgsx.gltf.data.data.GLTFBufferView;
import net.mgsx.gltf.data.texture.GLTFImage;
import net.mgsx.gltf.loaders.exceptions.GLTFIllegalException;
import net.mgsx.gltf.loaders.exceptions.GLTFRuntimeException;
import net.mgsx.gltf.loaders.gltf.SeparatedDataFileResolver;
import net.mgsx.gltf.loaders.shared.GLTFLoaderBase;
import net.mgsx.gltf.loaders.shared.data.DataFileResolver;
import net.mgsx.gltf.loaders.shared.texture.PixmapBinaryLoaderHack;

public class BinaryDataFileResolver implements DataFileResolver
{
	private ObjectMap<Integer, ByteBuffer> bufferMap = new ObjectMap<Integer, ByteBuffer>();
	private GLTF glModel;
	private FileHandle path;
	
	@Override
	public void load(FileHandle file) {
		path = file.parent();
		load(file.read());
	}
	
	public void load(byte[] bytes){
		load(new ByteArrayInputStream(bytes));
	}

	public void load(InputStream stream) {
		load(new LittleEndianInputStream(stream));
	}
	
	public void load(LittleEndianInputStream stream) {
		try {
			loadInternal(stream);
			
		} catch (IOException e) {
			throw new GLTFRuntimeException(e);
		} finally {
			StreamUtils.closeQuietly(stream);
		}
	}
	
	private void loadInternal(LittleEndianInputStream stream) throws IOException {
		long magic = stream.readInt(); // & 0xFFFFFFFFL;
		if(magic != 0x46546C67) throw new GLTFIllegalException("bad magic");
		int version = stream.readInt();
		if(version != 2) throw new GLTFIllegalException("bad version");
		long length = stream.readInt();// & 0xFFFFFFFFL;
		
		byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
		String jsonData = null;
		for(int i=12 ; i<length ; ){
			int chunkLen = stream.readInt();
			int chunkType = stream.readInt();
			i += 8;			// chunkLen % 4;
			if(chunkType == 0x4E4F534A){
				byte[] data = new byte[(int)chunkLen];
				stream.readFully(data, 0, chunkLen);
				jsonData = new String(data);
			}else if(chunkType == 0x004E4942){
				ByteBuffer bufferData = ByteBuffer.allocate(chunkLen);
				bufferData.order(ByteOrder.LITTLE_ENDIAN);
				int bytesToRead = chunkLen;
				int bytesRead;
				while (bytesToRead > 0 && (bytesRead = stream.read(buffer, 0, Math.min(buffer.length, bytesToRead))) != -1) {
					bufferData.put(buffer, 0, bytesRead);
					bytesToRead -= bytesRead;
				}
				if(bytesToRead > 0) throw new GLTFIllegalException("premature end of file");
				bufferData.flip();
				bufferMap.put(bufferMap.size, bufferData);
			}else{
				Gdx.app.log(GLTFLoaderBase.TAG, "skip buffer type " + chunkType);
				if(chunkLen > 0){
					stream.skip(chunkLen);
				}
			}
			i += chunkLen;
		}
		Json json = new Json();
		json.setIgnoreUnknownFields(true);
		glModel = json.fromJson(GLTF.class, jsonData);
	}
	
	@Override
	public GLTF getRoot() {
		return glModel;
	}

	@Override
	public ByteBuffer getBuffer(int buffer) {
		return bufferMap.get(buffer);
	}
	
	@Override
	public Pixmap load(GLTFImage glImage) {
		Gdx.app.log("GLTF", "BinaryDataFileResolver.load: START, bufferView=" + glImage.bufferView + ", uri=" + glImage.uri);
		if(glImage.bufferView != null){
			GLTFBufferView bufferView = glModel.bufferViews.get(glImage.bufferView);
			Gdx.app.log("GLTF", "BinaryDataFileResolver.load: bufferView=" + bufferView + ", bufferIndex=" + bufferView.buffer);
			ByteBuffer buffer = bufferMap.get(bufferView.buffer);
			Gdx.app.log("GLTF", "BinaryDataFileResolver.load: buffer=" + buffer + ", capacity=" + (buffer != null ? buffer.capacity() : "NULL"));
			buffer.position(bufferView.byteOffset);
			byte [] data = new byte[bufferView.byteLength];
			buffer.get(data);
			Gdx.app.log("GLTF", "BinaryDataFileResolver.load: data length=" + data.length);
			
			// Log first few bytes to identify image format
			String header = String.format("0x%02X 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X",
				data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]);
			Gdx.app.log("GLTF", "BinaryDataFileResolver.load: Image header bytes: " + header);
			
			// Check if this is a Basis Universal compressed texture
			// Basis files start with magic bytes "BASIS" or "basis_"
			// KTX2 files start with magic bytes 0xAB 0x4B 0x54 0x58 0x20 0x32 (KTX 2)
			// WebP files start with "RIFF....WEBP"
			boolean isBasis = data.length >= 6 &&
			    ((data[0] == 'B' && data[1] == 'A' && data[2] == 'S' && data[3] == 'I' && data[4] == 'S') ||
			     (data[0] == 'b' && data[1] == 'a' && data[2] == 's' && data[3] == 'i' && data[4] == 's'));
			boolean isKTX2 = data.length >= 6 &&
			    (data[0] == (byte)0xAB && data[1] == (byte)0x4B && data[2] == (byte)0x54 && data[3] == (byte)0x58 &&
			     data[4] == (byte)0x20 && data[5] == (byte)0x32);
			boolean isWebP = data.length >= 12 &&
			    (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' &&
			     data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P');
			
			if (isBasis || isKTX2) {
				Gdx.app.log("GLTF", "BinaryDataFileResolver.load: Detected " + (isBasis ? "Basis" : "KTX2") + " compressed texture, returning null");
				// Return null Pixmap - the Basis transcoder will handle this in TextureResolver
				return null;
			}
			
			if (isWebP) {
				Gdx.app.log("GLTF", "BinaryDataFileResolver.load: Detected WebP compressed texture, returning null");
				// Return null Pixmap - WebP will be handled via raw bytes in TextureResolver
				return null;
			}
			
			Gdx.app.log("GLTF", "BinaryDataFileResolver.load: Calling PixmapBinaryLoaderHack.load");
			Pixmap pixmap = PixmapBinaryLoaderHack.load(data, 0, data.length);
			Gdx.app.log("GLTF", "BinaryDataFileResolver.load: PixmapBinaryLoaderHack returned " + pixmap);
			return pixmap;
		} else if(glImage.uri != null) {
			Gdx.app.log("GLTF", "BinaryDataFileResolver.load: Loading from URI=" + glImage.uri);
			Pixmap pixmap = new Pixmap(path.child(SeparatedDataFileResolver.decodePath(glImage.uri)));
			Gdx.app.log("GLTF", "BinaryDataFileResolver.load: Pixmap from URI = " + pixmap);
			return pixmap;
		} else {
			Gdx.app.error("GLTF", "BinaryDataFileResolver.load: No bufferView or URI");
			throw new GLTFIllegalException("GLB image should have bufferView or uri");
		}
	}
	
	@Override
	public byte[] getRawBytes(GLTFImage glImage) {
		if(glImage.bufferView != null){
			GLTFBufferView bufferView = glModel.bufferViews.get(glImage.bufferView);
			ByteBuffer buffer = bufferMap.get(bufferView.buffer);
			buffer.position(bufferView.byteOffset);
			byte [] data = new byte[bufferView.byteLength];
			buffer.get(data);
			return data;
		}
		return null;
	}
}