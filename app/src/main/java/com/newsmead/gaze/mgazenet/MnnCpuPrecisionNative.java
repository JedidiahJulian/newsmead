package com.newsmead.gaze.mgazenet;

/** Minimal JNI bridge exposing MNN BackendConfig CPU precision (FP32 normal vs FP16/ARM82 low). */
public final class MnnCpuPrecisionNative {
    public static final int PRECISION_NORMAL = 0;
    public static final int PRECISION_LOW = 2;

    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("MNN");
        System.loadLibrary("newsmead_mnn_cpu");
    }

    public static native long nativeCreate(byte[] modelBytes, int threads, int precisionMode);

    public static native int nativeRun(
        long handle,
        float[] face,
        float[] left,
        float[] right,
        float[] rect,
        float[] outValues
    );

    public static native int nativeMainBackend(long handle);

    public static native void nativeClose(long handle);

    private MnnCpuPrecisionNative() {}
}
