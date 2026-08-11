package XuanJi.llm.provider;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 进程级共享 HttpClient —— 复用连接池，避免每个 LLM 请求都 {@code newBuilder().build()} 新建连接池与 SSL 上下文。
 *
 * <p>各供应商的 {@code jdkFactory()} 此前在每次 {@code chat/chatStream/chatWithTools} 调用里都新建一个
 * {@link HttpClient}（连带全新的连接池与 SSL 上下文），导致连接无法跨请求复用、句柄与 CPU 浪费。
 * 本类把底层 {@link HttpClient} 收敛为单例，连接池在所有供应商、所有请求之间共享；
 * 各供应商仍保留自己的 {@link org.springframework.http.client.JdkClientHttpRequestFactory} 与读超时，
 * 仅连接层被复用。
 *
 * <p>非 Spring 托管（静态持有），以便被 {@code static jdkFactory()} 直接复用，减少改造面。
 * connectTimeout 取各供应商原值上界（10s），比原 5s 更宽松且不破坏既有调用
 * （仅让 5~10s 的慢连接从「超时失败」变为「成功」，属正向增强）。读超时仍由各工厂自行设置。
 */
public final class LlmHttpClient {

    private static final HttpClient SHARED = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private LlmHttpClient() {
    }

    /** 返回进程级共享 HttpClient（连接池在所有供应商间复用）。 */
    public static HttpClient shared() {
        return SHARED;
    }
}
