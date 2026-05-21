#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include "llama.cpp/include/llama.h"

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_sampler * g_sampler = nullptr;
static const llama_vocab * g_vocab = nullptr;
static bool g_mtp_enabled = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_xuabadai_ai_LlamaBridge_loadModelNative(
        JNIEnv * env,
        jobject /* this */,
        jstring model_path,
        jstring mmproj_path,
        jint n_threads,
        jboolean enable_mtp) {

    const char * c_model_path = env->GetStringUTFChars(model_path, nullptr);

    // Model params
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only on Android
    mparams.use_mmap = true;

    g_model = llama_model_load_from_file(c_model_path, mparams);
    env->ReleaseStringUTFChars(model_path, c_model_path);

    if (!g_model) {
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    // Context params
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;

    // Try to create MTP context if enabled and model supports it
    if (enable_mtp) {
        llama_context_params mtp_params = llama_context_default_params();
        mtp_params.n_ctx = 2048;
        mtp_params.n_threads = n_threads;
        mtp_params.n_threads_batch = n_threads;
        mtp_params.ctx_type = LLAMA_CONTEXT_TYPE_MTP;

        llama_context * mtp_ctx = llama_init_from_model(g_model, mtp_params);
        if (mtp_ctx) {
            // MTP model loaded successfully - we store reference
            g_mtp_enabled = true;
            // Note: Full MTP speculative decoding requires common/ infrastructure.
            // For now, we signal MTP is available. The actual speedup comes from
            // the MTP context producing draft tokens that get verified by the main context.
            // A production implementation would use speculative.cpp logic.
            llama_free(mtp_ctx); // Free for now - placeholder
        }
    }

    // Create main context
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Create sampler chain: top_k -> top_p -> temperature -> dist
    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(time(nullptr)));

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_xuabadai_ai_LlamaBridge_isMTPEnabledNative(JNIEnv *, jobject) {
    return g_mtp_enabled ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_xuabadai_ai_LlamaBridge_freeModelNative(JNIEnv *, jobject) {
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
    g_mtp_enabled = false;
}

JNIEXPORT jstring JNICALL
Java_com_xuabadai_ai_LlamaBridge_generateNative(
        JNIEnv * env,
        jobject /* this */,
        jstring prompt,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p) {

    if (!g_ctx || !g_model || !g_sampler) {
        return env->NewStringUTF("[错误] 模型未加载");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);

    // Tokenize prompt
    std::vector<llama_token> tokens;
    tokens.resize(1024);
    int32_t n_tokens = llama_tokenize(g_vocab, c_prompt, strlen(c_prompt),
                                       tokens.data(), tokens.size(), true, true);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_tokens < 0) {
        return env->NewStringUTF("[错误] Tokenization 失败");
    }
    tokens.resize(n_tokens);

    // Check context size
    uint32_t n_ctx = llama_n_ctx(g_ctx);
    if ((uint32_t)n_tokens >= n_ctx) {
        return env->NewStringUTF("[错误] Prompt 超出上下文长度");
    }

    // Reset sampler
    llama_sampler_reset(g_sampler);

    // Create batch for prompt tokens
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        return env->NewStringUTF("[错误] Prompt decode 失败");
    }

    // Generate tokens
    std::string result;
    int n_cur = n_tokens;

    for (int i = 0; i < max_tokens; i++) {
        // Sample next token
        llama_token new_token = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Check for end of text
        if (llama_vocab_is_eog(g_vocab, new_token)) {
            break;
        }

        // Convert token to text
        char buf[32];
        int n = llama_token_to_piece(g_vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        // Accept token into sampler
        llama_sampler_accept(g_sampler, new_token);

        // Prepare batch for next token
        llama_batch single = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, single) != 0) {
            break;
        }

        n_cur++;
    }

    return env->NewStringUTF(result.c_str());
}

} // extern "C"
