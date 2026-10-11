#include <jni.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <exception>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include "whisper.h"

namespace {
struct Engine {
    explicit Engine(whisper_context *value) : ctx(value) {}
    whisper_context *ctx;
    std::atomic<bool> cancelled{false};
    std::mutex inference_mutex;
};

std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<Engine>> engines;
std::atomic<jlong> next_handle{1};

std::shared_ptr<Engine> lookup(jlong handle) {
    std::lock_guard<std::mutex> guard(registry_mutex);
    const auto it = engines.find(handle);
    return it == engines.end() ? nullptr : it->second;
}

bool abort_inference(void *data) {
    return static_cast<Engine *>(data)->cancelled.load(std::memory_order_acquire);
}

void throw_state(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass klass = env->FindClass("java/lang/IllegalStateException");
    if (klass) env->ThrowNew(klass, message);
}

void throw_exception(JNIEnv *env, const std::exception &error) {
    throw_state(env, error.what());
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_dessalines_thumbkey_dictation_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    try {
        return env->NewStringUTF(whisper_print_system_info());
    } catch (const std::exception &error) {
        throw_exception(env, error);
    } catch (...) {
        throw_state(env, "Whisper native failure");
    }
    return nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_dessalines_thumbkey_dictation_WhisperNative_create(JNIEnv *env, jobject, jstring path) {
    const char *utf = nullptr;
    whisper_context *created_ctx = nullptr;
    try {
        utf = env->GetStringUTFChars(path, nullptr);
        if (!utf) return 0;
        auto params = whisper_context_default_params();
        params.use_gpu = false;
        created_ctx = whisper_init_from_file_with_params(utf, params);
        env->ReleaseStringUTFChars(path, utf);
        utf = nullptr;
        if (!created_ctx) {
            throw_state(env, "Unable to load Whisper model");
            return 0;
        }
        auto engine = std::make_shared<Engine>(created_ctx);
        created_ctx = nullptr;
        const jlong handle = next_handle.fetch_add(1, std::memory_order_relaxed);
        std::lock_guard<std::mutex> guard(registry_mutex);
        engines.emplace(handle, std::move(engine));
        return handle;
    } catch (const std::bad_alloc &) {
        if (utf) env->ReleaseStringUTFChars(path, utf);
        if (created_ctx) whisper_free(created_ctx);
        throw_state(env, "Not enough memory to load Whisper model");
    } catch (const std::exception &error) {
        if (utf) env->ReleaseStringUTFChars(path, utf);
        if (created_ctx) whisper_free(created_ctx);
        throw_exception(env, error);
    } catch (...) {
        if (utf) env->ReleaseStringUTFChars(path, utf);
        if (created_ctx) whisper_free(created_ctx);
        throw_state(env, "Whisper native failure");
    }
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dessalines_thumbkey_dictation_WhisperNative_transcribe(
    JNIEnv *env, jobject, jlong handle, jshortArray pcm, jstring language) {
    const char *lang = nullptr;
    try {
        auto engine = lookup(handle);
        if (!engine) {
            throw_state(env, "Whisper engine is closed");
            return nullptr;
        }
        std::lock_guard<std::mutex> inference_guard(engine->inference_mutex);
        if (!engine->ctx || engine->cancelled.load(std::memory_order_acquire)) return nullptr;

        const jsize count = env->GetArrayLength(pcm);
        std::vector<float> samples(static_cast<size_t>(count));
        std::array<jshort, 4096> chunk{};
        for (jsize offset = 0; offset < count; offset += static_cast<jsize>(chunk.size())) {
            const jsize length = std::min<jsize>(static_cast<jsize>(chunk.size()), count - offset);
            env->GetShortArrayRegion(pcm, offset, length, chunk.data());
            if (env->ExceptionCheck()) return nullptr;
            for (jsize index = 0; index < length; ++index) {
                samples[static_cast<size_t>(offset + index)] = chunk[static_cast<size_t>(index)] / 32768.0f;
            }
        }
        lang = env->GetStringUTFChars(language, nullptr);
        if (!lang) return nullptr;
        const std::string language_value(lang);
        env->ReleaseStringUTFChars(language, lang);
        lang = nullptr;

        auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = static_cast<int>(std::max(1u, std::min(4u, std::thread::hardware_concurrency())));
        params.translate = false;
        params.no_context = true;
        params.no_timestamps = true;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.language = language_value == "auto" ? nullptr : language_value.c_str();
        params.detect_language = language_value == "auto";
        params.abort_callback = abort_inference;
        params.abort_callback_user_data = engine.get();
        const int result = whisper_full(engine->ctx, params, samples.data(), static_cast<int>(samples.size()));
        if (result != 0) {
            if (!engine->cancelled.load(std::memory_order_acquire)) throw_state(env, "Whisper transcription failed");
            return nullptr;
        }
        if (engine->cancelled.load(std::memory_order_acquire)) return nullptr;
        std::string text;
        for (int i = 0; i < whisper_full_n_segments(engine->ctx); ++i) text += whisper_full_get_segment_text(engine->ctx, i);
        return env->NewStringUTF(text.c_str());
    } catch (const std::bad_alloc &) {
        if (lang) env->ReleaseStringUTFChars(language, lang);
        throw_state(env, "Not enough memory to transcribe audio");
    } catch (const std::exception &error) {
        if (lang) env->ReleaseStringUTFChars(language, lang);
        throw_exception(env, error);
    } catch (...) {
        if (lang) env->ReleaseStringUTFChars(language, lang);
        throw_state(env, "Whisper native failure");
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dessalines_thumbkey_dictation_WhisperNative_cancel(JNIEnv *env, jobject, jlong handle) {
    try {
        auto engine = lookup(handle);
        if (engine) engine->cancelled.store(true, std::memory_order_release);
    } catch (const std::exception &error) {
        throw_exception(env, error);
    } catch (...) {
        throw_state(env, "Whisper native failure");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_dessalines_thumbkey_dictation_WhisperNative_destroy(JNIEnv *env, jobject, jlong handle) {
    try {
        std::shared_ptr<Engine> engine;
        {
            std::lock_guard<std::mutex> guard(registry_mutex);
            const auto it = engines.find(handle);
            if (it == engines.end()) return;
            engine = it->second;
            engines.erase(it);
        }
        engine->cancelled.store(true, std::memory_order_release);
        std::lock_guard<std::mutex> inference_guard(engine->inference_mutex);
        if (engine->ctx) {
            whisper_free(engine->ctx);
            engine->ctx = nullptr;
        }
    } catch (const std::exception &error) {
        throw_exception(env, error);
    } catch (...) {
        throw_state(env, "Whisper native failure");
    }
}
