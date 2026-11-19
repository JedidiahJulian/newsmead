// MNN CPU precision JNI shim for NewsMead. Contract pinned to MNN d407447ed56c4121a11ccbd266dc184ca1ead0c2.
#include <jni.h>
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <array>
#include <cmath>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <new>
#include <stdexcept>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

namespace {
struct PendingJavaException {};
struct Failure : std::runtime_error {
    const char* javaClass;
    Failure(const char* type, const std::string& message) : std::runtime_error(message), javaClass(type) {}
};
[[noreturn]] void invalid(const std::string& message) {
    throw Failure("java/lang/IllegalArgumentException", message);
}
[[noreturn]] void failed(const std::string& message) {
    throw Failure("java/lang/IllegalStateException", message);
}
void checkJava(JNIEnv* env) { if (env->ExceptionCheck()) throw PendingJavaException{}; }
void wipe(void* pointer, size_t bytes) noexcept {
    if (pointer == nullptr) return;
    auto* p = static_cast<volatile unsigned char*>(pointer);
    while (bytes-- > 0) *p++ = 0;
}
template <class T> struct WipedVector {
    std::vector<T> values;
    explicit WipedVector(size_t count) : values(count) {}
    ~WipedVector() { wipe(values.data(), values.size() * sizeof(T)); }
};

constexpr jint PRECISION_NORMAL = 0;
constexpr jint PRECISION_LOW = 2;
constexpr size_t INPUT_COUNT = 4;
constexpr size_t OUTPUT_ELEMENTS = 258;
const std::array<std::vector<int>, INPUT_COUNT> expectedShapes{{
    {1, 3, 224, 224}, {1, 3, 112, 112}, {1, 3, 112, 112}, {1, 12}
}};
constexpr std::array<jsize, INPUT_COUNT> expectedLengths{{
    3 * 224 * 224, 3 * 112 * 112, 3 * 112 * 112, 12
}};

struct SessionBox {
    WipedVector<jbyte> bytes;
    MNN::Interpreter* net = nullptr;
    MNN::Session* session = nullptr;
    MNN::BackendConfig backend;
    MNN::ScheduleConfig config;
    std::array<MNN::Tensor*, INPUT_COUNT> inputs{{nullptr, nullptr, nullptr, nullptr}};
    std::array<MNN::Tensor*, INPUT_COUNT> hostInputs{{nullptr, nullptr, nullptr, nullptr}};
    MNN::Tensor* output = nullptr;
    MNN::Tensor* hostOutput = nullptr;
    int actualBackend = -1;
    const std::thread::id owner = std::this_thread::get_id();
    bool closed = false;

    explicit SessionBox(size_t count) : bytes(count) {}

    void releaseResources() noexcept {
        closed = true;
        for (size_t i = 0; i < INPUT_COUNT; ++i) {
            if (hostInputs[i] != nullptr) {
                wipe(hostInputs[i]->host<void>(), static_cast<size_t>(expectedLengths[i]) * sizeof(float));
                MNN::Tensor::destroy(hostInputs[i]);
                hostInputs[i] = nullptr;
            }
        }
        if (hostOutput != nullptr) {
            wipe(hostOutput->host<void>(), OUTPUT_ELEMENTS * sizeof(float));
            MNN::Tensor::destroy(hostOutput);
            hostOutput = nullptr;
        }
        if (net != nullptr) {
            if (session != nullptr) {
                net->releaseSession(session);
                session = nullptr;
            }
            MNN::Interpreter::destroy(net);
            net = nullptr;
        }
    }

    ~SessionBox() noexcept { releaseResources(); }
};

std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<SessionBox>> registry;
jlong nextHandle = 1;

std::shared_ptr<SessionBox> lookupOwned(jlong handle) {
    std::shared_ptr<SessionBox> box;
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        const auto it = registry.find(handle);
        if (handle <= 0 || it == registry.end()) failed("Invalid or closed MNN CPU session handle");
        box = it->second;
    }
    if (box->owner != std::this_thread::get_id()) failed("MNN CPU precision calls require the creating thread");
    if (box->closed) failed("MNN CPU session is closed");
    return box;
}

void requireFloat32(const MNN::Tensor* tensor) {
    if (tensor == nullptr) failed("Missing model tensor");
    const auto type = tensor->getType();
    if (type.code != halide_type_float || type.bits != 32 || type.lanes != 1) {
        failed("MNN CPU session requires logical float32 tensors");
    }
}

std::vector<int> validateTensors(SessionBox& box) {
    for (size_t i = 0; i < INPUT_COUNT; ++i) {
        requireFloat32(box.inputs[i]);
        if (box.inputs[i]->shape() != expectedShapes[i]) failed("Unexpected model input rank or shape");
    }
    requireFloat32(box.output);
    const auto shape = box.output->shape();
    if (shape.empty()) failed("Missing output dimensions");
    size_t product = 1;
    for (int dimension : shape) {
        if (dimension <= 0 || product > OUTPUT_ELEMENTS / static_cast<size_t>(dimension)) {
            failed("Unexpected output shape");
        }
        product *= static_cast<size_t>(dimension);
    }
    if (product != OUTPUT_ELEMENTS) failed("Output must contain exactly 258 logical features");
    return shape;
}

void throwJava(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass klass = env->FindClass(type);
    if (klass != nullptr) { env->ThrowNew(klass, message); env->DeleteLocalRef(klass); }
}

template <class T, class F> T guarded(JNIEnv* env, T fallback, F action) {
    try { return action(); }
    catch (const PendingJavaException&) { /* Existing VM exception is preserved. */ }
    catch (const Failure& error) { throwJava(env, error.javaClass, error.what()); }
    catch (const std::bad_alloc&) { throwJava(env, "java/lang/OutOfMemoryError", "MNN CPU allocation failed"); }
    catch (const std::exception& error) { throwJava(env, "java/lang/IllegalStateException", error.what()); }
    catch (...) { throwJava(env, "java/lang/IllegalStateException", "Unknown MNN CPU native failure"); }
    return fallback;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_nativeCreate(
    JNIEnv* env, jclass, jbyteArray modelBytes, jint threads, jint precisionMode) {
    return guarded<jlong>(env, 0, [&]() -> jlong {
        if (threads < 1 || threads > 8) invalid("MNN CPU threads must be in 1..8");
        if (precisionMode != PRECISION_NORMAL && precisionMode != PRECISION_LOW) {
            invalid("Unsupported MNN CPU precision mode");
        }
        if (modelBytes == nullptr) invalid("Missing model bytes");
        const jsize length = env->GetArrayLength(modelBytes);
        checkJava(env);
        if (length <= 0) invalid("Empty model bytes");

        auto box = std::make_shared<SessionBox>(static_cast<size_t>(length));
        env->GetByteArrayRegion(modelBytes, 0, length, box->bytes.values.data());
        checkJava(env);

        box->net = MNN::Interpreter::createFromBuffer(box->bytes.values.data(), box->bytes.values.size());
        if (box->net == nullptr) failed("MNN model load failed");
        box->net->setSessionMode(MNN::Interpreter::Session_Release);

        box->backend.memory = MNN::BackendConfig::Memory_Normal;
        box->backend.power = MNN::BackendConfig::Power_Normal;
        box->backend.precision = (precisionMode == PRECISION_LOW)
            ? MNN::BackendConfig::Precision_Low
            : MNN::BackendConfig::Precision_Normal;
        box->config.type = MNN_FORWARD_CPU;
        box->config.numThread = threads;
        box->config.backendConfig = &box->backend;

        box->session = box->net->createSession(box->config);
        if (box->session == nullptr) failed("MNN CPU session creation failed");

        int backends[2] = {-777, -888};
        if (!box->net->getSessionInfo(box->session, MNN::Interpreter::BACKENDS, backends)) {
            failed("MNN CPU BACKENDS query unavailable");
        }
        box->actualBackend = backends[0];
        if (box->actualBackend != MNN_FORWARD_CPU && box->actualBackend != MNN_FORWARD_CPU_EXTENSION) {
            failed("Unexpected MNN CPU main backend: " + std::to_string(box->actualBackend));
        }

        constexpr std::array<const char*, INPUT_COUNT> names{{"face", "left", "right", "rect"}};
        for (size_t i = 0; i < INPUT_COUNT; ++i) {
            box->inputs[i] = box->net->getSessionInput(box->session, names[i]);
        }
        box->output = box->net->getSessionOutput(box->session, "output_0");
        const auto outputShape = validateTensors(*box);

        for (size_t i = 0; i < INPUT_COUNT; ++i) {
            box->hostInputs[i] = MNN::Tensor::create<float>(expectedShapes[i], nullptr, MNN::Tensor::CAFFE);
            if (box->hostInputs[i] == nullptr || box->hostInputs[i]->host<float>() == nullptr ||
                box->hostInputs[i]->elementSize() != expectedLengths[i]) {
                failed("Failed to allocate MNN host input tensor");
            }
        }
        box->hostOutput = MNN::Tensor::create<float>(outputShape, nullptr, MNN::Tensor::CAFFE);
        if (box->hostOutput == nullptr || box->hostOutput->host<float>() == nullptr ||
            box->hostOutput->elementSize() != static_cast<int>(OUTPUT_ELEMENTS)) {
            failed("Failed to allocate MNN host output tensor");
        }

        std::lock_guard<std::mutex> lock(registryMutex);
        if (nextHandle == std::numeric_limits<jlong>::max()) failed("MNN CPU handle space exhausted");
        const jlong handle = nextHandle++;
        registry.emplace(handle, std::move(box));
        return handle;
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_nativeRun(
    JNIEnv* env, jclass, jlong handle,
    jfloatArray face, jfloatArray left, jfloatArray right, jfloatArray rect, jfloatArray outValues) {
    return guarded<jint>(env, -1, [&]() -> jint {
        const auto box = lookupOwned(handle);
        const std::array<jfloatArray, INPUT_COUNT> arrays{{face, left, right, rect}};
        for (size_t i = 0; i < INPUT_COUNT; ++i) {
            if (arrays[i] == nullptr || env->GetArrayLength(arrays[i]) != expectedLengths[i]) {
                invalid("MNN CPU input length mismatch");
            }
            checkJava(env);
        }
        if (outValues == nullptr || env->GetArrayLength(outValues) != static_cast<jsize>(OUTPUT_ELEMENTS)) {
            invalid("MNN CPU output array length mismatch");
        }
        checkJava(env);

        for (size_t i = 0; i < INPUT_COUNT; ++i) {
            auto* dst = box->hostInputs[i]->host<float>();
            env->GetFloatArrayRegion(arrays[i], 0, expectedLengths[i], dst);
            checkJava(env);
            for (jsize j = 0; j < expectedLengths[i]; ++j) {
                if (!std::isfinite(dst[j])) invalid("Non-finite MNN CPU input");
            }
        }
        for (size_t i = 0; i < INPUT_COUNT; ++i) {
            if (!box->inputs[i]->copyFromHostTensor(box->hostInputs[i])) {
                failed("MNN CPU input copyFromHostTensor failed");
            }
        }
        const auto status = box->net->runSession(box->session);
        if (status != MNN::NO_ERROR) {
            failed("MNN CPU runSession failed with code " + std::to_string(status));
        }
        if (!box->output->copyToHostTensor(box->hostOutput)) {
            failed("MNN CPU output copyToHostTensor failed");
        }
        const auto* values = box->hostOutput->host<float>();
        for (size_t i = 0; i < OUTPUT_ELEMENTS; ++i) {
            if (!std::isfinite(values[i])) failed("Non-finite MNN CPU output");
        }
        env->SetFloatArrayRegion(outValues, 0, static_cast<jsize>(OUTPUT_ELEMENTS), values);
        checkJava(env);
        return 0;
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_nativeMainBackend(JNIEnv* env, jclass, jlong handle) {
    return guarded<jint>(env, -1, [&]() -> jint {
        return lookupOwned(handle)->actualBackend;
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_nativeClose(JNIEnv* env, jclass, jlong handle) {
    guarded<int>(env, 0, [&]() -> int {
        std::shared_ptr<SessionBox> box;
        {
            std::lock_guard<std::mutex> lock(registryMutex);
            const auto it = registry.find(handle);
            if (it == registry.end()) return 0; // Idempotent close.
            if (it->second->owner != std::this_thread::get_id()) {
                failed("MNN CPU precision calls require the creating thread");
            }
            box = std::move(it->second);
            registry.erase(it);
        }
        box->releaseResources();
        return 0;
    });
}
