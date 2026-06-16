package net.mgsx.gltf.basis;

import java.nio.ByteBuffer;

import com.badlogic.gdx.Gdx;

/**
 * Java wrapper for Basis Universal transcoder via JNI.
 * Supports both raw .basis files and KTX2 containers.
 * Uses handle-based architecture for proper state management.
 */
public class BasisTranscoder {
    private static boolean available = false;

    static {
        try {
            System.loadLibrary("gdx-gltf-native");
            available = nativeIsAvailable() == 1;
            Gdx.app.log("BasisTranscoder", "Native library loaded, available=" + available);
        } catch (UnsatisfiedLinkError e) {
            available = false;
            Gdx.app.error("BasisTranscoder", "Failed to load native library: " + e.getMessage());
        } catch (Throwable e) {
            available = false;
            Gdx.app.error("BasisTranscoder", "Error loading native library: " + e.getMessage());
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    // --- KTX2 SPECIFIC JNI API (Handle-based) ---

    /**
     * Validates the KTX2 header and returns a pointer/handle to a native C++ ktx2_transcoder wrapper.
     * @param ktx2Data Direct ByteBuffer containing KTX2 data
     * @param dataSize Size of data
     * @return Native handle pointer (long), or 0 if initialization/validation fails.
     */
    public static native long openKTX2(ByteBuffer ktx2Data, int dataSize);

    /**
     * Closes and frees the native transcoder instance.
     */
    public static native void closeKTX2(long handle);

    public static native int getKTX2Width(long handle);
    public static native int getKTX2Height(long handle);
    public static native int getKTX2NumLevels(long handle);
    public static native int getKTX2HasAlpha(long handle);

    /**
     * Transcodes a specific layer/mipmap level using the native KTX2 transcoder.
     *
     * @param handle The pointer returned by openKTX2
     * @param level Mipmap level index
     * @param layer Image layer index (usually 0 for 2D textures)
     * @param targetFormat Transcoder format ID (0=ETC2_RGBA, 1=ASTC_4x4_RGBA)
     * @param outputBuffer Direct ByteBuffer to write native GPU texture data into
     * @return 0 on success, negative error code on failure
     */
    public static native int transcodeKTX2Level(long handle, int level, int layer, int targetFormat, ByteBuffer outputBuffer);

    // --- Legacy methods (for raw .basis files - deprecated) ---
    private static native int nativeIsAvailable();
}
