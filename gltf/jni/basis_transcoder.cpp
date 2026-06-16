// Basis Universal transcoder JNI wrapper
// Handle-based architecture for KTX2 container support

#include <jni.h>
#include <android/log.h>
#include <algorithm> // Required for std::max

#define LOG_TAG "BasisJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define BASISD_SUPPORT_KTX2 1
#define BASISD_SUPPORT_KTX2_ZSTD 1
#include "basisu_transcoder.h"

// Wrapper structure to track KTX2 transcoder state in C++
struct KTX2Wrapper {
    basist::ktx2_transcoder transcoder;
    const uint8_t* data;
    uint32_t data_size;
};

extern "C" {

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_basis_BasisTranscoder_nativeIsAvailable(JNIEnv *env, jclass clazz) {
    LOGI("nativeIsAvailable: Initializing static basisu structures...");
    
    // CRITICAL GLOBAL FIX: This sets 'g_transcoder_initialized = true'
    // and unpacks the internal Huffman/ETC1S decoding tables.
    basist::basisu_transcoder_init();
    
    LOGI("nativeIsAvailable: returning 1");
    return 1;
}

JNIEXPORT jlong JNICALL Java_net_mgsx_gltf_basis_BasisTranscoder_openKTX2(JNIEnv *env, jclass clazz, jobject buffer, jint size) {
    LOGI("openKTX2: size=%d", size);
    
    // Secondary safety guard invocation
    basist::basisu_transcoder_init();
    
    const uint8_t* ktx2_data = (const uint8_t*)env->GetDirectBufferAddress(buffer);
    if (!ktx2_data) {
        LOGE("openKTX2: GetDirectBufferAddress failed");
        return 0;
    }
    LOGI("openKTX2: buffer address=%p", ktx2_data);
    
    KTX2Wrapper* wrapper = new KTX2Wrapper();
    wrapper->data = ktx2_data;
    wrapper->data_size = size;
    
    if (!wrapper->transcoder.init(ktx2_data, size)) {
        LOGE("openKTX2: ktx2_transcoder.init() failed");
        delete wrapper;
        return 0;
    }

    // CRITICAL: Initialize global states and start decompression pipelines.
    // Without this, transcode_slice triggers the 'g_transcoder_initialized' abort.
    if (!wrapper->transcoder.start_transcoding()) {
        LOGE("openKTX2: ktx2_transcoder.start_transcoding() failed");
        delete wrapper;
        return 0;
    }
    
    LOGI("openKTX2: init success, width=%u height=%u levels=%u",
         wrapper->transcoder.get_width(),
         wrapper->transcoder.get_height(),
         wrapper->transcoder.get_levels());
    LOGI("openKTX2: is_etc1s=%s is_uastc=%s has_alpha=%u",
         wrapper->transcoder.is_etc1s() ? "true" : "false",
         wrapper->transcoder.is_uastc() ? "true" : "false",
         wrapper->transcoder.get_has_alpha());
    
    return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT void JNICALL Java_net_mgsx_gltf_basis_BasisTranscoder_closeKTX2(JNIEnv *env, jclass clazz, jlong handle) {
    if (handle) {
        LOGI("closeKTX2: freeing handle=%lx", handle);
        delete reinterpret_cast<KTX2Wrapper*>(handle);
    }
}

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_basis_BasisTranscoder_getKTX2Width(JNIEnv *env, jclass clazz, jlong handle) {
    KTX2Wrapper* wrapper = reinterpret_cast<KTX2Wrapper*>(handle);
    return wrapper->transcoder.get_width();
}

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_basis_BasisTranscoder_getKTX2Height(JNIEnv *env, jclass clazz, jlong handle) {
    KTX2Wrapper* wrapper = reinterpret_cast<KTX2Wrapper*>(handle);
    return wrapper->transcoder.get_height();
}

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_basis_BasisTranscoder_getKTX2NumLevels(JNIEnv *env, jclass clazz, jlong handle) {
    KTX2Wrapper* wrapper = reinterpret_cast<KTX2Wrapper*>(handle);
    return wrapper->transcoder.get_levels();
}

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_basis_BasisTranscoder_getKTX2HasAlpha(JNIEnv *env, jclass clazz, jlong handle) {
    KTX2Wrapper* wrapper = reinterpret_cast<KTX2Wrapper*>(handle);
    return wrapper->transcoder.get_has_alpha();
}

JNIEXPORT jint JNICALL Java_net_mgsx_gltf_basis_BasisTranscoder_transcodeKTX2Level(JNIEnv *env, jclass clazz,
    jlong handle, jint level, jint layer, jint target_format, jobject output_buf) {
    
    LOGI("transcodeKTX2Level: handle=%lx level=%d layer=%d target_format=%d", handle, level, layer, target_format);
    
    KTX2Wrapper* wrapper = reinterpret_cast<KTX2Wrapper*>(handle);
    uint8_t* out_ptr = (uint8_t*)env->GetDirectBufferAddress(output_buf);
    if (!out_ptr) {
        LOGE("transcodeKTX2Level: GetDirectBufferAddress failed");
        return -1;
    }
    
    jsize out_capacity = env->GetDirectBufferCapacity(output_buf);
    LOGI("transcodeKTX2Level: output buffer capacity=%d bytes", out_capacity);
    
    // Map target format
    basist::transcoder_texture_format fmt;
    if (target_format == 1) {
        fmt = basist::transcoder_texture_format::cTFASTC_4x4_RGBA;
    } else {
        fmt = basist::transcoder_texture_format::cTFETC2_RGBA;
    }
    
    // Safeguard Mipmap Level dimensions using std::max to prevent 0-pixel crashes
    uint32_t width = wrapper->transcoder.get_width();
    uint32_t height = wrapper->transcoder.get_height();
    
    uint32_t levelWidth = std::max<uint32_t>(1u, width >> level);
    uint32_t levelHeight = std::max<uint32_t>(1u, height >> level);
    
    uint32_t blockWidth = (levelWidth + 3) / 4;
    uint32_t blockHeight = (levelHeight + 3) / 4;
    uint32_t outputBlocks = blockWidth * blockHeight;
    
    LOGI("transcodeKTX2Level: level=%d levelWidth=%u levelHeight=%u blockWidth=%u blockHeight=%u outputBlocks=%u",
         level, levelWidth, levelHeight, blockWidth, blockHeight, outputBlocks);
    
    // Transcode the level
    bool success = wrapper->transcoder.transcode_image_level(
        (uint32_t)level,
        (uint32_t)layer,
        0,  // face index
        out_ptr,
        outputBlocks,
        fmt
    );
    
    if (success) {
        LOGI("transcodeKTX2Level: SUCCESS");
        return 0;
    } else {
        LOGE("transcodeKTX2Level: FAILED");
        return -1;
    }
}

} // extern "C"