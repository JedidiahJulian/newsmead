package com.newsmead.mgazenetbenchmark

/** Explicit synthetic benchmark candidate. The production app never imports this module. */
object GazeNetNextCandidate {
    const val ID = "gazefollower2_gazenet_next_fp32"
    const val ASSET_DIRECTORY = "gazefollower2"
    const val ONNX_SHA = "5f635713b50134163545d1eb76868610253c4fadc467dd5c4f69bd271610b69e"
    const val MNN_SHA = "4547cc4685a7176aad75c50a25dc9a3368d1dd1fafe0d8ea5c32421af93a49b0"
    const val REFERENCE_SHA = "e5a2ecbe665a8024fc601d875d8f58f63ff26c5ea14e69e36e0a1f5a7b9132c5"
    val spec get() = MnnEstimator.ModelSpec("$ASSET_DIRECTORY/GazeNet_Next.mnn", MNN_SHA,
        "outpu_0", requireBatchOneOutput = true)
}
