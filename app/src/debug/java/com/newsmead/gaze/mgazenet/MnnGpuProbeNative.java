package com.newsmead.gaze.mgazenet;

/** Optional debug GPU bridge; loaded only by explicit probes or isolated execution trials. */
public final class MnnGpuProbeNative {
    private static boolean loaded;
    private MnnGpuProbeNative() {}

    public static synchronized void load() {
        if (loaded) return;
        System.loadLibrary("c++_shared");
        System.loadLibrary("MNN");
        System.loadLibrary("MNN_CL");
        System.loadLibrary("newsmead_mnn_gpu_probe");
        loaded = true;
    }

    public static native long nativeCreate(byte[] model);
    public static native float[] nativeRun(long handle, float[] face, float[] left, float[] right, float[] rect);
    public static native int nativeMainBackend(long handle);
    public static native void nativeClose(long handle);
}
