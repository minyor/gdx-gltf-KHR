package net.mgsx.gltf.loaders.shared.geometry;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectMap.Entry;
import com.badlogic.gdx.utils.ObjectSet;

import net.mgsx.gltf.data.data.GLTFAccessor;
import net.mgsx.gltf.data.geometry.GLTFMesh;
import net.mgsx.gltf.data.geometry.GLTFPrimitive;
import net.mgsx.gltf.loaders.blender.BlenderShapeKeys;
import net.mgsx.gltf.loaders.exceptions.GLTFIllegalException;
import net.mgsx.gltf.loaders.exceptions.GLTFUnsupportedException;
import net.mgsx.gltf.loaders.shared.GLTFTypes;
import net.mgsx.gltf.loaders.shared.data.AccessorBuffer;
import net.mgsx.gltf.loaders.shared.data.DataResolver;
import net.mgsx.gltf.loaders.shared.material.MaterialLoader;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRVertexAttributes;
import net.mgsx.gltf.scene3d.model.NodePartPlus;
import net.mgsx.gltf.scene3d.model.NodePlus;
import net.mgsx.gltf.scene3d.model.WeightVector;

public class MeshLoader {
	
	private ObjectMap<GLTFMesh, Array<NodePart>> meshMap = new ObjectMap<GLTFMesh, Array<NodePart>>();
	private final Array<Mesh> meshes = new Array<Mesh>();
	
	public void load(Node node, GLTFMesh glMesh, DataResolver dataResolver, MaterialLoader materialLoader, net.mgsx.gltf.data.GLTF glModel)
	{
		Gdx.app.log("GLTF", "DEBUG MeshLoader.load: START node=" + node.id + " glMesh=" + (glMesh != null ? "non-null" : "NULL") + " glMesh.name=" + (glMesh != null ? glMesh.name : "N/A"));
		Gdx.app.log("GLTF", "DEBUG MeshLoader.load: glMesh.primitives=" + (glMesh != null ? glMesh.primitives.size : "N/A"));
		((NodePlus)node).morphTargetNames = BlenderShapeKeys.parse(glMesh);
		
		Array<NodePart> parts = meshMap.get(glMesh, null);
		Gdx.app.log("GLTF", "DEBUG MeshLoader.load: meshMap.get(glMesh)=" + (parts != null ? "found" : "null"));
		if(parts == null){
			parts = new Array<NodePart>();
			
			for(GLTFPrimitive primitive : glMesh.primitives){
				Gdx.app.log("GLTF", "DEBUG MeshLoader.load: processing primitive, mode=" + primitive.mode);
				
				final int glPrimitiveType = GLTFTypes.mapPrimitiveMode(primitive.mode);
				
				// material
				Material material;
				if(primitive.material != null){
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: primitive.material=" + primitive.material);
					material = materialLoader.get(primitive.material);
				}else{
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: primitive.material=null, using default");
					material = materialLoader.getDefaultMaterial();
				}
				
				// vertices
				Array<VertexAttribute> vertexAttributes = new Array<VertexAttribute>();
				Array<GLTFAccessor> glAccessors = new Array<GLTFAccessor>();
				ObjectSet<VertexAttribute> rgbOddAttributes = new ObjectSet<VertexAttribute>();
				
				Array<int[]> bonesIndices = new Array<int[]>();
				Array<float[]> bonesWeights = new Array<float[]>();
				
				boolean hasNormals = false;
				boolean hasTangent = false;
				
				Gdx.app.log("GLTF", "DEBUG MeshLoader.load: primitive.attributes.size=" + primitive.attributes.size);
				for(Entry<String, Integer> attribute : primitive.attributes){
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: attribute=" + attribute.key + " accessorId=" + attribute.value);
					String attributeName = attribute.key;
					int accessorId = attribute.value;
					GLTFAccessor accessor = dataResolver.getAccessor(accessorId);
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: accessor=" + (accessor != null ? "found" : "NULL") + " type=" + (accessor != null ? accessor.type : "N/A") + " componentType=" + (accessor != null ? accessor.componentType : "N/A"));
					boolean rawAttribute = true;
					
					if(attributeName.equals("POSITION")){
							Gdx.app.log("GLTF", "DEBUG MeshLoader: POSITION componentType=" + accessor.componentType + " type=" + accessor.type + " count=" + accessor.count + " min=" + java.util.Arrays.toString(accessor.min) + " max=" + java.util.Arrays.toString(accessor.max));
							// KHR_mesh_quantization: positions can be BYTE/SHORT/USHORT (quantized)
							if(!GLTFTypes.TYPE_VEC3.equals(accessor.type)) throw new GLTFIllegalException("illegal position attribute type");
							if(accessor.componentType != GLTFTypes.C_FLOAT && accessor.componentType != GLTFTypes.C_BYTE && accessor.componentType != GLTFTypes.C_SHORT && accessor.componentType != GLTFTypes.C_USHORT){
								throw new GLTFIllegalException("illegal position component type: " + accessor.componentType);
							}
							vertexAttributes.add(VertexAttribute.Position());
						}else if(attributeName.equals("NORMAL")){
							// KHR_mesh_quantization: normals can be BYTE or SHORT (quantized)
							if(!GLTFTypes.TYPE_VEC3.equals(accessor.type)) throw new GLTFIllegalException("illegal normal attribute type");
							if(accessor.componentType != GLTFTypes.C_FLOAT && accessor.componentType != GLTFTypes.C_BYTE && accessor.componentType != GLTFTypes.C_SHORT && accessor.componentType != GLTFTypes.C_USHORT){
								throw new GLTFIllegalException("illegal normal component type: " + accessor.componentType);
							}
							Gdx.app.log("GLTF", "DEBUG MeshLoader: NORMAL componentType=" + accessor.componentType + " count=" + accessor.count + " min=" + java.util.Arrays.toString(accessor.min) + " max=" + java.util.Arrays.toString(accessor.max));
							vertexAttributes.add(VertexAttribute.Normal());
							hasNormals = true;
						}else if(attributeName.equals("TANGENT")){
							// KHR_mesh_quantization: tangents can be BYTE or SHORT (quantized)
							if(!GLTFTypes.TYPE_VEC4.equals(accessor.type)) throw new GLTFIllegalException("illegal tangent attribute type");
							if(accessor.componentType != GLTFTypes.C_FLOAT && accessor.componentType != GLTFTypes.C_BYTE && accessor.componentType != GLTFTypes.C_SHORT && accessor.componentType != GLTFTypes.C_USHORT){
								throw new GLTFIllegalException("illegal tangent component type: " + accessor.componentType);
							}
							Gdx.app.log("GLTF", "DEBUG MeshLoader: TANGENT componentType=" + accessor.componentType + " count=" + accessor.count + " min=" + java.util.Arrays.toString(accessor.min) + " max=" + java.util.Arrays.toString(accessor.max));
							vertexAttributes.add(new VertexAttribute(Usage.Tangent, 4, ShaderProgram.TANGENT_ATTRIBUTE));
							hasTangent = true;
					}else if(attributeName.startsWith("TEXCOORD_")){
						if(!GLTFTypes.TYPE_VEC2.equals(accessor.type)) throw new GLTFIllegalException("illegal texture coordinate attribute type : " + accessor.type);
						// KHR_mesh_quantization: texcoords can be UBYTE or USHORT (quantized)
						if(accessor.componentType != GLTFTypes.C_FLOAT && accessor.componentType != GLTFTypes.C_UBYTE && accessor.componentType != GLTFTypes.C_USHORT){
							throw new GLTFIllegalException("illegal texture coordinate component type : " + accessor.componentType);
						}
						Gdx.app.log("GLTF", "DEBUG MeshLoader: TEXCOORD_ componentType=" + accessor.componentType + " count=" + accessor.count + " min=" + java.util.Arrays.toString(accessor.min) + " max=" + java.util.Arrays.toString(accessor.max));
						int unit = parseAttributeUnit(attributeName);
						vertexAttributes.add(VertexAttribute.TexCoords(unit));
					}else if(attributeName.startsWith("COLOR_")){
						int unit = parseAttributeUnit(attributeName);
						String alias = unit > 0 ? ShaderProgram.COLOR_ATTRIBUTE + unit : ShaderProgram.COLOR_ATTRIBUTE;
						if(GLTFTypes.TYPE_VEC4.equals(accessor.type)){
							if(GLTFTypes.C_FLOAT == accessor.componentType){
								vertexAttributes.add(new VertexAttribute(Usage.ColorUnpacked, 4, GL20.GL_FLOAT, false, alias));
							}
							else if(GLTFTypes.C_USHORT == accessor.componentType){
								vertexAttributes.add(new VertexAttribute(Usage.ColorUnpacked, 4, GL20.GL_UNSIGNED_SHORT, true, alias));
							}
							else if(GLTFTypes.C_UBYTE == accessor.componentType){
								vertexAttributes.add(new VertexAttribute(Usage.ColorUnpacked, 4, GL20.GL_UNSIGNED_BYTE, true, alias));
							}else{
								throw new GLTFIllegalException("illegal color attribute component type: " + accessor.type);
							}
						}
						else if(GLTFTypes.TYPE_VEC3.equals(accessor.type)){
							if(GLTFTypes.C_FLOAT == accessor.componentType){
								vertexAttributes.add(new VertexAttribute(Usage.ColorUnpacked, 3, GL20.GL_FLOAT, false, alias));
							}
							// LibGDX requires attribute to be multiple of 4 so RGB short (6 bytes) and RGB bytes (3 bytes) data needs to be converted to RGBA. 
							else if(GLTFTypes.C_USHORT == accessor.componentType){
								VertexAttribute a = new VertexAttribute(Usage.ColorUnpacked, 4, GL20.GL_UNSIGNED_SHORT, true, alias);
								rgbOddAttributes.add(a);
								vertexAttributes.add(a);
							}
							else if(GLTFTypes.C_UBYTE == accessor.componentType){
								VertexAttribute a = new VertexAttribute(Usage.ColorUnpacked, 4, GL20.GL_UNSIGNED_BYTE, true, alias);
								rgbOddAttributes.add(a);
								vertexAttributes.add(a);
							}else{
								throw new GLTFIllegalException("illegal color attribute component type: " + accessor.type);
							}
						}
						else{
							throw new GLTFIllegalException("illegal color attribute type: " + accessor.type);
						}
							
					}else if(attributeName.startsWith("WEIGHTS_")){
						rawAttribute = false;
						
						if(!GLTFTypes.TYPE_VEC4.equals(accessor.type)){
							throw new GLTFIllegalException("illegal weight attribute type: " + accessor.type);
						}
						
						int unit = parseAttributeUnit(attributeName);
						if(unit >= bonesWeights.size) bonesWeights.setSize(unit+1);

						if(accessor.componentType == GLTFTypes.C_FLOAT){
							bonesWeights.set(unit, dataResolver.readBufferFloat(accessorId));
						}else if(accessor.componentType == GLTFTypes.C_USHORT){ 
							bonesWeights.set(unit, dataResolver.readBufferUShortAsFloat(accessorId));
						}else if(accessor.componentType == GLTFTypes.C_UBYTE){ 
							bonesWeights.set(unit, dataResolver.readBufferUByteAsFloat(accessorId));
						}else{
							throw new GLTFIllegalException("illegal weight attribute type: " + accessor.componentType);
						}
					}else if(attributeName.startsWith("JOINTS_")){
						rawAttribute = false;
						
						if(!GLTFTypes.TYPE_VEC4.equals(accessor.type)){
							throw new GLTFIllegalException("illegal joints attribute type: " + accessor.type);
						}
						
						int unit = parseAttributeUnit(attributeName);
						if(unit >= bonesIndices.size) bonesIndices.setSize(unit+1);
						
						if(accessor.componentType == GLTFTypes.C_UBYTE){ // unsigned byte
							bonesIndices.set(unit, dataResolver.readBufferUByte(accessorId));
						}else if(accessor.componentType == GLTFTypes.C_USHORT){ // unsigned short
							bonesIndices.set(unit, dataResolver.readBufferUShort(accessorId));
						}else{
							throw new GLTFIllegalException("illegal type for joints: " + accessor.componentType);
						}
					}
					else if(attributeName.startsWith("_")){
						Gdx.app.error("GLTF", "skip unsupported custom attribute: " + attributeName);
						rawAttribute = false;
					}else{
						throw new GLTFIllegalException("illegal attribute type " + attributeName);
					}
					
					if(rawAttribute){
						glAccessors.add(accessor);
					}
				}
				
				// morph targets
				if(primitive.targets != null){
					int morphTargetCount = primitive.targets.size;
					((NodePlus)node).weights = new WeightVector(morphTargetCount);
					
					for(int t=0 ; t<primitive.targets.size ; t++){
						int unit = t;
						for(Entry<String, Integer> attribute : primitive.targets.get(t)){
							String attributeName = attribute.key;
							int accessorId = attribute.value.intValue();
							GLTFAccessor accessor = dataResolver.getAccessor(accessorId);
							glAccessors.add(accessor);
							
							if(attributeName.equals("POSITION")){
								if(!(GLTFTypes.TYPE_VEC3.equals(accessor.type) && accessor.componentType == GLTFTypes.C_FLOAT)) throw new GLTFIllegalException("illegal morph target position attribute format");
								vertexAttributes.add(new VertexAttribute(PBRVertexAttributes.Usage.PositionTarget, 3, ShaderProgram.POSITION_ATTRIBUTE+unit, unit));
							}else if(attributeName.equals("NORMAL")){
								if(!(GLTFTypes.TYPE_VEC3.equals(accessor.type) && accessor.componentType == GLTFTypes.C_FLOAT)) throw new GLTFIllegalException("illegal morph target normal attribute format");
								vertexAttributes.add(new VertexAttribute(PBRVertexAttributes.Usage.NormalTarget, 3, ShaderProgram.NORMAL_ATTRIBUTE + unit, unit));
							}else if(attributeName.equals("TANGENT")){
								if(!(GLTFTypes.TYPE_VEC3.equals(accessor.type) && accessor.componentType == GLTFTypes.C_FLOAT)) throw new GLTFIllegalException("illegal morph target tangent attribute format");
								vertexAttributes.add(new VertexAttribute(PBRVertexAttributes.Usage.TangentTarget, 3, ShaderProgram.TANGENT_ATTRIBUTE + unit, unit));
							}else{
								throw new GLTFIllegalException("illegal morph target attribute type " + attributeName);
							}
						}
					}
					
				}
				
				int bSize = bonesIndices.size * 4;

				Array<VertexAttribute> bonesAttributes = new Array<VertexAttribute>();
				for(int b=0 ; b<bSize ; b++){
					VertexAttribute boneAttribute = VertexAttribute.BoneWeight(b);
					vertexAttributes.add(boneAttribute);
					bonesAttributes.add(boneAttribute);
				}
				
				// add missing vertex attributes (normals and tangent)
				boolean computeNormals = false;
				boolean computeTangents = false;
				VertexAttribute normalMapUVs = null;
				if(glPrimitiveType == GL20.GL_TRIANGLES){
					if(!hasNormals){
						vertexAttributes.add(VertexAttribute.Normal());
						glAccessors.add(null);
						computeNormals = true;
					}
					if(!hasTangent){
						// tangent is only needed when normal map is used
						PBRTextureAttribute normalMap = material.get(PBRTextureAttribute.class, PBRTextureAttribute.NormalTexture);
						if(normalMap != null){
							vertexAttributes.add(new VertexAttribute(Usage.Tangent, 4, ShaderProgram.TANGENT_ATTRIBUTE));
							glAccessors.add(null);
							computeTangents = true;
							for(VertexAttribute attribute : vertexAttributes){
								if(attribute.usage == Usage.TextureCoordinates && attribute.unit == normalMap.uvIndex){
									normalMapUVs = attribute;
								}
							}
							if(normalMapUVs == null) throw new GLTFIllegalException("UVs not found for normal map");
						}
					}
				}
				
				VertexAttributes attributesGroup = new VertexAttributes((VertexAttribute[])vertexAttributes.toArray(VertexAttribute.class));
				
				int vertexFloats = attributesGroup.vertexSize/4;
				
				int maxVertices = glAccessors.first().count;

				float [] vertices = new float [maxVertices * vertexFloats];
				
				for(int b=0 ; b<bSize ; b++){
					VertexAttribute boneAttribute = bonesAttributes.get(b);
					for(int i=0 ; i<maxVertices ; i++){
						vertices[i * vertexFloats + boneAttribute.offset/4] = bonesIndices.get(b/4)[i * 4 + b%4];
						vertices[i * vertexFloats + boneAttribute.offset/4+1] = bonesWeights.get(b/4)[i * 4 + b%4];
					}
				}
				
				Gdx.app.log("GLTF", "DEBUG MeshLoader.load: glAccessors.size=" + glAccessors.size + " vertexAttributes.size=" + vertexAttributes.size);
				for(int i=0 ; i<glAccessors.size ; i++){
					GLTFAccessor glAccessor = glAccessors.get(i);
					VertexAttribute attribute = vertexAttributes.get(i);
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: processing accessor i=" + i + " glAccessor=" + (glAccessor != null ? "non-null" : "NULL") + " attribute=" + (attribute != null ? attribute.toString() : "NULL"));
					
					
					if(glAccessor == null) {
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: glAccessor is null, skipping");
						continue;
					}
					
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: calling dataResolver.getAccessorBuffer for accessor.count=" + glAccessor.count + " componentType=" + glAccessor.componentType);
					AccessorBuffer accBuffer = dataResolver.getAccessorBuffer(glAccessor);
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: got AccessorBuffer, byteStride=" + accBuffer.getByteStride());
					ByteBuffer data = accBuffer.prepareForReading();
					data.order(java.nio.ByteOrder.LITTLE_ENDIAN);

					// buffer can be interleaved, so vertex stride may be different than vertex size
					int byteStride = accBuffer.getByteStride();
					int attributeFloats = attribute.getSizeInBytes() / 4;
					
					// KHR_mesh_quantization: de-quantize BYTE/SHORT/USHORT to float
					// Standard glTF dequantization formulas:
					// USHORT: f = c / 65535.0
					// SHORT:  f = max(c / 32767.0, -1.0)
					// UBYTE:  f = c / 255.0
					// BYTE:   f = max(c / 127.0, -1.0)
					// If min/max bounds are provided (common in Meshopt), we use them for dequantization.
					
					float[] minBounds = glAccessor.min;
					float[] maxBounds = glAccessor.max;
					
					// If a Meshopt filter was applied, the data is already in a normalized range (usually -1 to 1)
					// and we should NOT apply min/max dequantization which would corrupt it.
					boolean hasMeshoptFilter = false;
					if (glAccessor.bufferView != null) {
						net.mgsx.gltf.data.data.GLTFBufferView bv = dataResolver.getBufferView(glAccessor.bufferView);
						if (bv.extensions != null) {
							net.mgsx.gltf.data.data.MeshoptBufferViewExtension m = bv.extensions.get(net.mgsx.gltf.data.data.MeshoptBufferViewExtension.class, net.mgsx.gltf.data.data.MeshoptBufferViewExtension.EXTENSION_NAME);
							if (m != null && m.filter != null && !m.filter.equals("NONE")) {
								hasMeshoptFilter = true;
							}
						}
					}
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: hasMeshoptFilter=" + hasMeshoptFilter + " minBounds=" + java.util.Arrays.toString(minBounds) + " maxBounds=" + java.util.Arrays.toString(maxBounds));

					// KHR_mesh_quantization: de-quantize integer types to float
					// IMPORTANT: After dequantization we MUST continue to skip float copy blocks
					// because the raw buffer contains integers, not floats.
					if(glAccessor.componentType == GLTFTypes.C_BYTE){
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: >>> DEQUANT BYTE <<<");
						int numComponents = GLTFTypes.accessorTypeSize(glAccessor);
						int strideBytes = byteStride;
						int baseOffset = accBuffer.getByteOffset();
						// For normals with OCTAHEDRAL filter: data is already decoded to Cartesian, just normalize from int to float
						// meshopt_quantizeSnorm formula: quantized = round(v * 127) for 8-bit signed
						// dequantize: v = quantized / 127.0
						for(int j = 0; j < glAccessor.count; j++){
							int vIndex = j * vertexFloats + attribute.offset/4;
							int dIndex = baseOffset + j * strideBytes;
							for(int k = 0; k < numComponents; k++){
								byte quantized = data.get(dIndex + k);
								float dequantized = quantized / 127f;
								vertices[vIndex + k] = dequantized;
								if (j == 0) Gdx.app.log("GLTF", "DEBUG BYTE dequant k=" + k + ": quantized=" + quantized + " dequantized=" + dequantized);
							}
						}
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: BYTE dequant done, first vertex[0]=" + vertices[attribute.offset/4]);
						continue;
					}
					else if(glAccessor.componentType == GLTFTypes.C_SHORT){
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: >>> DEQUANT SHORT <<<");
						int numComponents = GLTFTypes.accessorTypeSize(glAccessor);
						int strideBytes = byteStride;
						int baseOffset = accBuffer.getByteOffset();
						// For normals: meshopt_quantizeSnorm formula: quantized = round(v * 32767) for 16-bit signed
						// dequantize: v = quantized / 32767.0
						for(int j = 0; j < glAccessor.count; j++){
							int vIndex = j * vertexFloats + attribute.offset/4;
							int dIndex = baseOffset + j * strideBytes;
							for(int k = 0; k < numComponents; k++){
								short quantized = data.getShort(dIndex + k * 2);
								float dequantized = quantized / 32767f;
								vertices[vIndex + k] = dequantized;
								if (j == 0) Gdx.app.log("GLTF", "DEBUG SHORT dequant k=" + k + ": quantized=" + quantized + " dequantized=" + dequantized);
							}
						}
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: SHORT dequant done, first vertex[0]=" + vertices[attribute.offset/4]);
						continue;
					}
					else if(glAccessor.componentType == GLTFTypes.C_USHORT){
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: >>> DEQUANT USHORT <<<");
						int numComponents = GLTFTypes.accessorTypeSize(glAccessor);
						int strideBytes = byteStride;
						int baseOffset = accBuffer.getByteOffset();
						
						boolean isPosition = (attribute.usage == Usage.Position);
						
						if (isPosition) {
							// POSITION: gltfpack node-quantization mapping
							com.badlogic.gdx.math.Vector3 nodeTranslation = node.translation;
							com.badlogic.gdx.math.Vector3 nodeScale = node.scale;
							
							for(int j = 0; j < glAccessor.count; j++){
								int vIndex = j * vertexFloats + attribute.offset / 4;
								int dIndex = baseOffset + j * strideBytes;
								for(int k = 0; k < numComponents; k++){
									int quantized = data.getShort(dIndex + k * 2) & 0xFFFF;
									float scale = (k == 0) ? nodeScale.x : (k == 1) ? nodeScale.y : nodeScale.z;
									float offset = (k == 0) ? nodeTranslation.x : (k == 1) ? nodeTranslation.y : nodeTranslation.z;
									vertices[vIndex + k] = (quantized * scale) + offset;
								}
							}
							
						} else {
							float currentDenominator = 65535.0f;
							float scaleU = 1.0f;
							float scaleV = 1.0f;
							float offsetU = 0.0f;
							float offsetV = 0.0f;

							// Try to read dynamic values, but apply safe fallback handling if inverse bounds mismatch
							if (primitive.material != null && glModel != null && glModel.materials != null && primitive.material < glModel.materials.size) {
								net.mgsx.gltf.data.material.GLTFMaterial glMaterial = glModel.materials.get(primitive.material);
								if (glMaterial != null && glMaterial.pbrMetallicRoughness != null && glMaterial.pbrMetallicRoughness.baseColorTexture != null) {
									net.mgsx.gltf.data.texture.GLTFTextureInfo texInfo = glMaterial.pbrMetallicRoughness.baseColorTexture;
									if (texInfo.extensions != null) {
										net.mgsx.gltf.data.extensions.KHRTextureTransform transform = texInfo.extensions.get(net.mgsx.gltf.data.extensions.KHRTextureTransform.class, net.mgsx.gltf.data.extensions.KHRTextureTransform.EXT);
										if (transform != null) {
											// Extract the raw gltfpack scale multiplier (e.g. 16.0 for -vt 12, 64.0 for -vt 10)
											float rawGltfpackScale = transform.scale[0];
											currentDenominator = 65535.0f / rawGltfpackScale;

											// Check if the scale values are already inverted by gltfpack (e.g. 4.0 instead of 0.25)
											if (transform.scale != null && transform.scale.length >= 2) {
												scaleU = transform.scale[0] > 1.0f ? (1.0f / transform.scale[0]) : transform.scale[0];
												scaleV = transform.scale[1] > 1.0f ? (1.0f / transform.scale[1]) : transform.scale[1];
											}
											if (transform.offset != null && transform.offset.length >= 2) {
												offsetU = transform.offset[0];
												offsetV = transform.offset[1];
											}
										}
									}
								}
							}

							final float normFactor = 1.0f / currentDenominator;
							Gdx.app.log("GLTF", "DEBUG MeshLoader.load: currentDenominator=" + currentDenominator);

							for(int j = 0; j < glAccessor.count; j++){
								int vIndex = j * vertexFloats + attribute.offset / 4;
								int dIndex = baseOffset + j * strideBytes;
								
								// Normalize raw bit short streams cleanly to [0.0, 1.0] range
								float normU = (data.getShort(dIndex) & 0xFFFF) * normFactor;
								float normV = (data.getShort(dIndex + 2) & 0xFFFF) * normFactor;
								
								// Map directly onto the material sheet atlas boundaries
								vertices[vIndex]     = (normU * scaleU) + offsetU;
								vertices[vIndex + 1] = (normV * scaleV) + offsetV; // Keeps layout directions linear
							}
							Gdx.app.log("GLTF", "DEBUG MeshLoader.load: TEXCOORD USHORT dequant done!");
						}
						
						continue;
					}
					else if(glAccessor.componentType == GLTFTypes.C_UBYTE){
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: >>> DEQUANT UBYTE <<<");
						int numComponents = GLTFTypes.accessorTypeSize(glAccessor);
						int strideBytes = byteStride;
						int baseOffset = accBuffer.getByteOffset();
						// For texcoords: gltfpack stores min/max as FLOAT bounds in accessor
						// quantized = round((value - min) / (max - min) * 255)
						// dequantize: value = min + quantized * (max - min) / 255
						float[] tmin = glAccessor.min;
						float[] tmax = glAccessor.max;
						for(int j = 0; j < glAccessor.count; j++){
							int vIndex = j * vertexFloats + attribute.offset/4;
							int dIndex = baseOffset + j * strideBytes;
							for(int k = 0; k < numComponents; k++){
								int quantized = data.get(dIndex + k) & 0xFF;
								float dequantized;
								if (tmin != null && tmax != null && k < tmin.length) {
									dequantized = tmin[k] + quantized * (tmax[k] - tmin[k]) / 255f;
								} else {
									dequantized = quantized / 255f;
								}
								vertices[vIndex + k] = dequantized;
								if (j == 0) Gdx.app.log("GLTF", "DEBUG UBYTE dequant k=" + k + ": quantized=" + quantized + " dequantized=" + dequantized);
							}
						}
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: UBYTE dequant done, first vertex[0]=" + vertices[attribute.offset/4]);
						continue;
					}
					
					// libGDX requires attribute size to be multiple of 4 bytes.
					// RGB short and RGB bytes need to be converted to RGBA.
					if(rgbOddAttributes.contains(attribute)) {
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: >>> RGB ODD ATTRIBUTE <<<");
						if(attribute.type == GL20.GL_UNSIGNED_SHORT) {
							ShortBuffer shortBuffer = data.asShortBuffer();
							for(int j=0 ; j<glAccessor.count ; j++){
								shortBuffer.position(j * 3);
								int vIndex = j * vertexFloats + attribute.offset/4;
								int r = shortBuffer.get() & 0xFFFF;
								int g = shortBuffer.get() & 0xFFFF;
								int b = shortBuffer.get() & 0xFFFF;
								int a = 0xFFFF;
								vertices[vIndex] = Float.intBitsToFloat((r << 16) | g);
								vertices[vIndex+1] = Float.intBitsToFloat((b << 16) | a);
							}
						}
						else if(attribute.type == GL20.GL_UNSIGNED_BYTE) {
							for(int j=0 ; j<glAccessor.count ; j++){
								data.position(j * 3);
								int vIndex = j * vertexFloats + attribute.offset/4;
								int r = data.get() & 0xFF;
								int g = data.get() & 0xFF;
								int b = data.get() & 0xFF;
								int a = 0xFF;
								vertices[vIndex] = Float.intBitsToFloat((r << 24) | (g << 16) | (b << 8) | a );
							}
						}
					}
					// if vertex stride is not multiple of 4 bytes, we have to read float from byte buffer
					if(byteStride % 4 != 0){
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: >>> BYTE STRIDE NOT MULTIPLE OF 4 <<<");
						for(int j=0 ; j<glAccessor.count ; j++){
							int vIndex = j * vertexFloats + attribute.offset/4;
							int dIndex = j * byteStride;
							for(int k=0 ; k<attributeFloats ; k++) {
								vertices[vIndex+k] = data.getFloat(dIndex + k * 4);
							}
						}
					}
					// optimized copy when vertex stride is multiple of 4 bytes
					else {
						Gdx.app.log("GLTF", "DEBUG MeshLoader.load: >>> OPTIMIZED FLOAT COPY <<<");
						int floatStride = byteStride / 4;
						FloatBuffer floatBuffer = data.asFloatBuffer();
						for(int j=0 ; j<glAccessor.count ; j++){
							floatBuffer.position(j * floatStride);
							int vIndex = j * vertexFloats + attribute.offset/4;
							floatBuffer.get(vertices, vIndex, attributeFloats);
						}
					}
				}
				
				// indices
				Gdx.app.log("GLTF", "DEBUG MeshLoader.load: primitive.indices=" + (primitive.indices != null ? "non-null" : "NULL"));
				if(primitive.indices != null){
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: primitive.indices accessorId=" + primitive.indices);
					GLTFAccessor indicesAccessor = dataResolver.getAccessor(primitive.indices);
					Gdx.app.log("GLTF", "DEBUG MeshLoader.load: indicesAccessor=" + (indicesAccessor != null ? "found" : "NULL") + " count=" + (indicesAccessor != null ? indicesAccessor.count : "N/A"));
					
					if(!indicesAccessor.type.equals(GLTFTypes.TYPE_SCALAR)){
						throw new GLTFIllegalException("indices accessor must be SCALAR but was " + indicesAccessor.type);
					}
						
					int maxIndices = indicesAccessor.count;
					
					switch(indicesAccessor.componentType){
					case GLTFTypes.C_UINT:
						{
							Gdx.app.error("GLTF", "integer indices partially supported, mesh will be split");
							Gdx.app.error("GLTF", "splitting mesh: '" + String.valueOf(glMesh.name) + "', " + maxVertices + " vertices, " + maxIndices + " indices.");

							int verticesPerPrimitive;
							if(glPrimitiveType == GL20.GL_TRIANGLES){
								verticesPerPrimitive = 3;
							}else if(glPrimitiveType == GL20.GL_LINES){
								verticesPerPrimitive = 2;
							}else{
								throw new GLTFUnsupportedException("integer indices only supported for triangles or lines");
							}
							
							int [] indices = new int[maxIndices];
							dataResolver.getBufferInt(indicesAccessor).get(indices);
							
							Array<float[]> splitVertices = new Array<float[]>();
							Array<short[]> splitIndices = new Array<short[]>();
							
							MeshSpliter.split(splitVertices, splitIndices, vertices, attributesGroup, indices, verticesPerPrimitive);
							
							int stride = attributesGroup.vertexSize / 4;
							int groups = splitIndices.size;
							int totalVertices = 0;
							int totalIndices = 0;
							for(int i=0 ; i<groups ; i++){
								float[] groupVertices = splitVertices.get(i);
								short[] groupIndices = splitIndices.get(i);
								int groupVertexCount = groupVertices.length / stride;
								
								totalVertices += groupVertexCount;
								totalIndices += groupIndices.length;
								
								Gdx.app.error("GLTF", "generate mesh: " + groupVertexCount + " vertices, " + groupIndices.length + " indices.");
								
								generateParts(node, parts, material, glMesh.name, groupVertices, groupVertexCount, groupIndices, attributesGroup, glPrimitiveType, computeNormals, computeTangents, normalMapUVs);
							}
							Gdx.app.error("GLTF", "mesh split: " + parts.size + " meshes generated: " + totalVertices + " vertices, " + totalIndices + " indices.");
						}
						break;
					case GLTFTypes.C_USHORT:
					case GLTFTypes.C_SHORT:
					{
						short [] indices = new short[maxIndices];
						ByteBuffer indexBuffer = dataResolver.getBufferByte(indicesAccessor);
						indexBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
						Gdx.app.log("GLTF", "DEBUG indices: maxIndices=" + maxIndices + " remaining=" + indexBuffer.remaining());
						for(int di = 0; di < maxIndices; di++){
							indices[di] = indexBuffer.getShort();
							if(di < 10) Gdx.app.log("GLTF", "DEBUG indices[" + di + "]=" + indices[di]);
						}
						generateParts(node, parts, material, glMesh.name, vertices, maxVertices, indices, attributesGroup, glPrimitiveType, computeNormals, computeTangents, normalMapUVs);
						break;
					}
					case GLTFTypes.C_UBYTE:
					{
						short [] indices = new short[maxIndices];
						ByteBuffer byteBuffer = dataResolver.getBufferByte(indicesAccessor);
						for(int i=0 ; i<maxIndices ; i++){
							indices[i] = (short)(byteBuffer.get() & 0xFF);
						}
						generateParts(node, parts, material, glMesh.name, vertices, maxVertices, indices, attributesGroup, glPrimitiveType, computeNormals, computeTangents, normalMapUVs);
						break;
					}
					default:
						throw new GLTFIllegalException("illegal componentType " + indicesAccessor.componentType);
					}
				}else{
					// non indexed mesh
					generateParts(node, parts, material, glMesh.name, vertices, maxVertices, null, attributesGroup, glPrimitiveType, computeNormals, computeTangents, normalMapUVs);
				}
			}
			
			// Reset node transform AFTER all primitives are processed to prevent double transformations on the GPU
			// (Must be after the loop so all primitives share the same quantization transform)
			node.translation.set(0, 0, 0);
			node.scale.set(1, 1, 1);
			node.rotation.idt();
			
			Gdx.app.log("GLTF", "DEBUG MeshLoader.load: calling meshMap.put(glMesh=" + (glMesh != null ? "non-null" : "NULL") + ", parts)");
			meshMap.put(glMesh, parts);
			Gdx.app.log("GLTF", "DEBUG MeshLoader.load: meshMap.put done");
		}
		Gdx.app.log("GLTF", "DEBUG MeshLoader.load: calling node.parts.addAll(parts), parts.size=" + parts.size);
		node.parts.addAll(parts);
		Gdx.app.log("GLTF", "DEBUG MeshLoader.load: SUCCESS");
	}

	private void generateParts(Node node, Array<NodePart> parts, Material material, String id, float[] vertices, int vertexCount, short[] indices, VertexAttributes attributesGroup, int glPrimitiveType, boolean computeNormals, boolean computeTangents, VertexAttribute normalMapUVs) {

		// skip empty meshes
		if(vertices.length == 0 || (indices != null && indices.length == 0)){
			return;
		}
		
		// Find position, normal, and texcoord attribute offsets
		int posOffset = -1;
		int normalOffset = -1;
		int texcoordOffset = -1;
		for(int i = 0; i < attributesGroup.size(); i++){
			VertexAttribute attr = attributesGroup.get(i);
			if(attr.usage == Usage.Position){
				posOffset = attr.offset / 4;
			} else if(attr.usage == Usage.Normal){
				normalOffset = attr.offset / 4;
			} else if(attr.usage == Usage.TextureCoordinates){
				texcoordOffset = attr.offset / 4;
			}
		}
		
		int stride = attributesGroup.vertexSize / 4;
		
		// Print all vertex data
		Gdx.app.log("GLTF", "=== MESH '" + id + "' vertexCount=" + vertexCount + " indices=" + (indices != null ? indices.length : 0) + " stride=" + stride + " posOffset=" + posOffset + " normalOffset=" + normalOffset + " texcoordOffset=" + texcoordOffset + " ===");
		if(posOffset >= 0){
			for(int v = 0; v < vertexCount && v < 3; v++){
				float x = vertices[v * stride + posOffset];
				float y = vertices[v * stride + posOffset + 1];
				float z = vertices[v * stride + posOffset + 2];
				String line = "  vertex[" + v + "] pos = (" + x + ", " + y + ", " + z + ")";
				
				if(normalOffset >= 0){
					float nx = vertices[v * stride + normalOffset];
					float ny = vertices[v * stride + normalOffset + 1];
					float nz = vertices[v * stride + normalOffset + 2];
					line += " normal = (" + nx + ", " + ny + ", " + nz + ")";
				}
				
				if(texcoordOffset >= 0){
					float u = vertices[v * stride + texcoordOffset];
					float vtc = vertices[v * stride + texcoordOffset + 1];
					line += " uv = (" + u + ", " + vtc + ")";
				}
				
				Gdx.app.log("GLTF", line);
			}
			if(vertexCount > 3) Gdx.app.log("GLTF", "  ... (" + (vertexCount - 3) + " more vertices)");
		}
		
		// Print first 3 triangles
		if(indices != null && indices.length > 0){
			for(int t = 0; t < indices.length / 3 && t < 3; t++){
				short i0 = indices[t * 3];
				short i1 = indices[t * 3 + 1];
				short i2 = indices[t * 3 + 2];
				String winding = "";
				if(posOffset >= 0){
					float x0 = vertices[i0 * stride + posOffset];
					float y0 = vertices[i0 * stride + posOffset + 1];
					float z0 = vertices[i0 * stride + posOffset + 2];
					float x1 = vertices[i1 * stride + posOffset];
					float y1 = vertices[i1 * stride + posOffset + 1];
					float z1 = vertices[i1 * stride + posOffset + 2];
					float x2 = vertices[i2 * stride + posOffset];
					float y2 = vertices[i2 * stride + posOffset + 1];
					float z2 = vertices[i2 * stride + posOffset + 2];
					winding = "  v0=(" + x0 + "," + y0 + "," + z0 + ") v1=(" + x1 + "," + y1 + "," + z1 + ") v2=(" + x2 + "," + y2 + "," + z2 + ")";
				}
				Gdx.app.log("GLTF", "  triangle[" + t + "] = (" + i0 + ", " + i1 + ", " + i2 + ")" + winding);
			}
			if(indices.length / 3 > 3) Gdx.app.log("GLTF", "  ... (" + (indices.length / 3 - 3) + " more triangles)");
		}
		Gdx.app.log("GLTF", "=== END MESH '" + id + "' ===");
		
		if(computeNormals || computeTangents){
			if(computeNormals && computeTangents) Gdx.app.log("GLTF", "compute normals and tangents for primitive " + id);
			else if(computeTangents) Gdx.app.log("GLTF", "compute tangents for primitive " + id);
			else Gdx.app.log("GLTF", "compute normals for primitive " + id);
			MeshTangentSpaceGenerator.computeTangentSpace(vertices, indices, attributesGroup, computeNormals, computeTangents, normalMapUVs);
		}
		
		Mesh mesh = new Mesh(true, vertexCount, indices == null ? 0 : indices.length, attributesGroup);
		meshes.add(mesh);
		mesh.setVertices(vertices);
		
		if(indices != null){
			mesh.setIndices(indices);
		}
		
		int len = indices == null ? vertexCount : indices.length;
		
		MeshPart meshPart = new MeshPart(id, mesh, 0, len, glPrimitiveType);
		
		
		NodePartPlus nodePart = new NodePartPlus();
		nodePart.morphTargets = ((NodePlus)node).weights;
		nodePart.meshPart = meshPart;
		nodePart.material = material;
		parts.add(nodePart);
		
	}

	private int parseAttributeUnit(String attributeName) {
		int lastUnderscoreIndex = attributeName.lastIndexOf('_');
		try{
			return Integer.parseInt(attributeName.substring(lastUnderscoreIndex+1));
		}catch(NumberFormatException e){
			throw new GLTFIllegalException("illegal attribute name " + attributeName);
		}
	}

	public Array<? extends Mesh> getMeshes() {
		return meshes;
	}

}
