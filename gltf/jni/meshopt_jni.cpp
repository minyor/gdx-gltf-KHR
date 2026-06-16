#include <jni.h>
#include "meshoptimizer.h"

extern "C" {

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_meshopt_MeshoptDecoder_nativeDecodeVertexBuffer(
    JNIEnv* env, jclass clazz, jobject dest, jint vertexCount, jint vertexSize, jobject source) {
    unsigned char* destBuf = (unsigned char*)env->GetDirectBufferAddress(dest);
    const unsigned char* srcBuf = (const unsigned char*)env->GetDirectBufferAddress(source);
    jsize srcLen = env->GetDirectBufferCapacity(source);
    return meshopt_decodeVertexBuffer(destBuf, vertexCount, vertexSize, srcBuf, srcLen);
}

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_meshopt_MeshoptDecoder_nativeDecodeIndexBuffer(
    JNIEnv* env, jclass clazz, jobject dest, jint indexCount, jint indexSize, jobject source) {
    void* destBuf = env->GetDirectBufferAddress(dest);
    const unsigned char* srcBuf = (const unsigned char*)env->GetDirectBufferAddress(source);
    jsize srcLen = env->GetDirectBufferCapacity(source);
    return meshopt_decodeIndexBuffer(destBuf, indexCount, indexSize, srcBuf, srcLen);
}

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_meshopt_MeshoptDecoder_nativeDecodeIndexSequence(
    JNIEnv* env, jclass clazz, jobject dest, jint indexCount, jint indexSize, jobject source) {
    void* destBuf = env->GetDirectBufferAddress(dest);
    const unsigned char* srcBuf = (const unsigned char*)env->GetDirectBufferAddress(source);
    jsize srcLen = env->GetDirectBufferCapacity(source);
    return meshopt_decodeIndexSequence(destBuf, indexCount, indexSize, srcBuf, srcLen);
}

JNIEXPORT void JNICALL Java_net_mgsx_gltf_meshopt_MeshoptDecoder_nativeDecodeFilterOct(
    JNIEnv* env, jclass clazz, jobject buffer, jint count, jint stride) {
    void* buf = env->GetDirectBufferAddress(buffer);
    meshopt_decodeFilterOct(buf, count, stride);
}

JNIEXPORT void JNICALL Java_net_mgsx_gltf_meshopt_MeshoptDecoder_nativeDecodeFilterQuat(
    JNIEnv* env, jclass clazz, jobject buffer, jint count, jint stride) {
    void* buf = env->GetDirectBufferAddress(buffer);
    meshopt_decodeFilterQuat(buf, count, stride);
}

JNIEXPORT void JNICALL Java_net_mgsx_gltf_meshopt_MeshoptDecoder_nativeDecodeFilterExp(
    JNIEnv* env, jclass clazz, jobject buffer, jint count, jint stride) {
    void* buf = env->GetDirectBufferAddress(buffer);
    meshopt_decodeFilterExp(buf, count, stride);
}

JNIEXPORT void JNICALL Java_net_mgsx_gltf_meshopt_MeshoptDecoder_nativeDecodeFilterColor(
    JNIEnv* env, jclass clazz, jobject buffer, jint count, jint stride) {
    void* buf = env->GetDirectBufferAddress(buffer);
    meshopt_decodeFilterColor(buf, count, stride);
}

} // extern "C"