// Camera-free debug probe. API contract pinned to MNN d407447ed56c4121a11ccbd266dc184ca1ead0c2.
#include <jni.h>
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <new>
#include <stdexcept>
#include <string>
#include <thread>
#include <unordered_map>
#include <utility>
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
    auto* p = static_cast<volatile unsigned char*>(pointer);
    while (bytes-- > 0) *p++ = 0;
}
template <class T> struct WipedVector {
    std::vector<T> values;
    explicit WipedVector(size_t count) : values(count) {}
    ~WipedVector() { wipe(values.data(), values.size() * sizeof(T)); }
};
struct Probe {
    WipedVector<jbyte> bytes;
    MNN::Interpreter* net = nullptr;
    MNN::Session* session = nullptr;
    MNN::BackendConfig backend;
    MNN::ScheduleConfig config;
    std::array<MNN::Tensor*, 4> inputs{{nullptr, nullptr, nullptr, nullptr}};
    MNN::Tensor* output = nullptr;
    const std::thread::id owner = std::this_thread::get_id();
    explicit Probe(size_t count) : bytes(count) {}
    ~Probe() noexcept {
        // MNN objects die before the owned backing model buffer is wiped.
        if (net != nullptr) {
            if (session != nullptr) net->releaseSession(session);
            delete net;
        }
    }
};
std::mutex registryMutex;
std::unordered_map<jlong, std::unique_ptr<Probe>> registry;
jlong nextHandle = 1;
Probe& owned(jlong handle) {
    const auto it = registry.find(handle);
    if (handle <= 0 || it == registry.end()) failed("Invalid or closed GPU probe handle");
    if (it->second->owner != std::this_thread::get_id()) failed("GPU probe calls require the creating thread");
    return *it->second;
}
void float32(MNN::Tensor* tensor) {
    if (tensor == nullptr) failed("Missing model tensor");
    const auto type = tensor->getType();
    if (type.code != halide_type_float || type.bits != 32 || type.lanes != 1) {
        failed("Probe requires logical float32 tensors");
    }
}
const std::array<std::vector<int>, 4> expectedShapes{{
    {1, 3, 224, 224}, {1, 3, 112, 112}, {1, 3, 112, 112}, {1, 12}
}};
constexpr std::array<jsize, 4> expectedLengths{{3 * 224 * 224, 3 * 112 * 112, 3 * 112 * 112, 12}};
void validateTensors(Probe& probe) {
    for (size_t i = 0; i < probe.inputs.size(); ++i) {
        float32(probe.inputs[i]);
        if (probe.inputs[i]->shape() != expectedShapes[i]) failed("Unexpected model input rank or shape");
    }
    float32(probe.output);
    const auto shape = probe.output->shape();
    if (shape.empty()) failed("Missing output dimensions");
    size_t product = 1;
    for (int dimension : shape) {
        if (dimension <= 0 || product > 258u / static_cast<size_t>(dimension)) failed("Unexpected output shape");
        product *= static_cast<size_t>(dimension);
    }
    if (product != 258u) failed("Output must contain exactly 258 logical features");
}
int mainBackend(Probe& probe) {
    // One config: documented buffer size >= 1 + config count. BACKENDS is main-pipeline only.
    int backends[2] = {-777, -888};
    if (!probe.net->getSessionInfo(probe.session, MNN::Interpreter::BACKENDS, backends)) {
        failed("UnsupportedOpenCL: BACKENDS query unavailable");
    }
    return backends[0];
}
struct Mapping {
    MNN::Tensor* tensor;
    MNN::Tensor::MapType type;
    void* pointer;
    Mapping(MNN::Tensor* value, MNN::Tensor::MapType access)
        : tensor(value), type(access), pointer(value->map(access, MNN::Tensor::CAFFE)) {
        if (pointer == nullptr) failed("Tensor CAFFE map failed");
    }
    ~Mapping() noexcept { tensor->unmap(type, MNN::Tensor::CAFFE, pointer); }
};
void throwJava(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass klass = env->FindClass(type);
    if (klass != nullptr) { env->ThrowNew(klass, message); env->DeleteLocalRef(klass); }
}
template <class T, class F> T guarded(JNIEnv* env, T fallback, F action) {
    try { return action(); }
    catch (const PendingJavaException&) { /* Existing VM exception is preserved. */ }
    catch (const Failure& error) { throwJava(env, error.javaClass, error.what()); }
    catch (const std::bad_alloc&) { throwJava(env, "java/lang/OutOfMemoryError", "GPU probe allocation failed"); }
    catch (const std::exception& error) { throwJava(env, "java/lang/IllegalStateException", error.what()); }
    catch (...) { throwJava(env, "java/lang/IllegalStateException", "Unknown GPU probe native failure"); }
    return fallback;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_newsmead_gaze_mgazenet_MnnGpuProbeNative_nativeCreate(JNIEnv* env, jclass, jbyteArray model) {
    return guarded<jlong>(env, 0, [&]() -> jlong {
        if (model == nullptr) invalid("Missing model bytes");
        const jsize length = env->GetArrayLength(model);
        checkJava(env);
        if (length <= 0) invalid("Empty model bytes");
        auto probe = std::make_unique<Probe>(static_cast<size_t>(length));
        env->GetByteArrayRegion(model, 0, length, probe->bytes.values.data());
        checkJava(env);
        probe->net = MNN::Interpreter::createFromBuffer(probe->bytes.values.data(), probe->bytes.values.size());
        if (probe->net == nullptr) failed("Model creation failed");
        probe->backend.precision = MNN::BackendConfig::Precision_Normal;
        probe->config.type = MNN_FORWARD_OPENCL;
        probe->config.mode = MNN_GPU_TUNING_FAST;
        probe->config.backendConfig = &probe->backend;
        // backupType remains the upstream CPU default. Per-operator fallback is not excluded.
        probe->session = probe->net->createSession(probe->config);
        if (probe->session == nullptr) failed("UnsupportedOpenCL: session creation failed");
        if (mainBackend(*probe) != MNN_FORWARD_OPENCL) {
            failed("UnsupportedOpenCL: main pipeline fell back from OpenCL");
        }
        constexpr std::array<const char*, 4> names{{"face", "left", "right", "rect"}};
        for (size_t i = 0; i < names.size(); ++i) probe->inputs[i] = probe->net->getSessionInput(probe->session, names[i]);
        probe->output = probe->net->getSessionOutput(probe->session, "output_0");
        validateTensors(*probe);
        std::lock_guard<std::mutex> lock(registryMutex);
        if (nextHandle == std::numeric_limits<jlong>::max()) failed("GPU probe handle space exhausted");
        const jlong handle = nextHandle++;
        registry.emplace(handle, std::move(probe));
        return handle;
    });
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_newsmead_gaze_mgazenet_MnnGpuProbeNative_nativeRun(
    JNIEnv* env, jclass, jlong handle, jfloatArray face, jfloatArray left, jfloatArray right, jfloatArray rect) {
    return guarded<jfloatArray>(env, nullptr, [&]() -> jfloatArray {
        std::lock_guard<std::mutex> lock(registryMutex);
        Probe& probe = owned(handle);
        const std::array<jfloatArray, 4> arrays{{face, left, right, rect}};
        // Validate every length before any map, upload or inference.
        for (size_t i = 0; i < arrays.size(); ++i) {
            if (arrays[i] == nullptr || env->GetArrayLength(arrays[i]) != expectedLengths[i]) {
                invalid("GPU probe input length mismatch");
            }
            checkJava(env);
        }
        validateTensors(probe);
        std::array<std::unique_ptr<WipedVector<jfloat>>, 4> copies;
        for (size_t i = 0; i < arrays.size(); ++i) {
            copies[i] = std::make_unique<WipedVector<jfloat>>(static_cast<size_t>(expectedLengths[i]));
            env->GetFloatArrayRegion(arrays[i], 0, expectedLengths[i], copies[i]->values.data());
            checkJava(env);
            for (float value : copies[i]->values) if (!std::isfinite(value)) invalid("Non-finite GPU probe input");
        }
        // All lengths, shapes, ranks and finite values have now passed; CAFFE maps use NCHW.
        for (size_t i = 0; i < arrays.size(); ++i) {
            Mapping mapped(probe.inputs[i], MNN::Tensor::MAP_TENSOR_WRITE);
            std::memcpy(mapped.pointer, copies[i]->values.data(), copies[i]->values.size() * sizeof(jfloat));
        }
        const auto status = probe.net->runSession(probe.session);
        if (status != MNN::NO_ERROR) failed("OpenCL runSession failed with code " + std::to_string(status));
        if (probe.output->wait(MNN::Tensor::MAP_TENSOR_READ, true) != MNN::NO_ERROR) failed("GPU output wait failed");
        Mapping mapped(probe.output, MNN::Tensor::MAP_TENSOR_READ);
        const auto* values = static_cast<const float*>(mapped.pointer);
        for (size_t i = 0; i < 258; ++i) if (!std::isfinite(values[i])) failed("Non-finite GPU probe output");
        jfloatArray result = env->NewFloatArray(258);
        checkJava(env);
        if (result == nullptr) throw std::bad_alloc();
        env->SetFloatArrayRegion(result, 0, 258, values);
        if (env->ExceptionCheck()) { env->DeleteLocalRef(result); throw PendingJavaException{}; }
        return result;
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_newsmead_gaze_mgazenet_MnnGpuProbeNative_nativeMainBackend(JNIEnv* env, jclass, jlong handle) {
    return guarded<jint>(env, -1, [&]() -> jint {
        std::lock_guard<std::mutex> lock(registryMutex);
        return mainBackend(owned(handle));
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_newsmead_gaze_mgazenet_MnnGpuProbeNative_nativeClose(JNIEnv* env, jclass, jlong handle) {
    guarded<int>(env, 0, [&]() -> int {
        std::lock_guard<std::mutex> lock(registryMutex);
        const auto it = registry.find(handle);
        if (it == registry.end()) return 0; // Idempotent close; no untrusted pointer dereference.
        owned(handle); // Enforce thread ownership before destruction.
        registry.erase(it);
        return 0;
    });
}
