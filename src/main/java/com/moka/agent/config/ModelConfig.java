package com.moka.agent.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

/**
 * DashScope LLM 模型 Bean 配置。
 *
 * <p>从 {@code agentscope.dashscope.api-key}（或环境变量 {@code DASHSCOPE_API_KEY}）读取 API Key，
 * 定义 4 个不同档位的模型供 Agent 按需选用：</p>
 *
 * <ul>
 *   <li>{@code fastModel}：用于简单任务（标题生成、推荐问题等），成本最低</li>
 *   <li>{@code strongModel}：默认主模型，负责意图识别、复杂推理等</li>
 *   <li>{@code strongModelWithThinking}：开启深度思考的强模型，用于行程规划、行程审核等</li>
 *   <li>{@code stableModel}：稳定型模型，用于流式输出与历史压缩</li>
 * </ul>
 *
 * <p>所有模型 Bean 均使用 {@link Lazy @Lazy}，仅在 Agent 真正调用时才会被实例化；
 * 启动阶段若未配置 API Key 只会打印 WARN，不会阻塞 Spring 上下文刷新。
 * 真实调用时若 API Key 仍为空，会抛 {@link IllegalStateException} 给出可操作的错误提示。</p>
 */
@Configuration
public class ModelConfig {

    private static final Logger logger = LoggerFactory.getLogger(ModelConfig.class);

    /**
     * 占位 API Key，用于让 DashScope SDK 在启动期通过参数校验，
     * 真实调用时再校验授权（由 DashScope 服务端返回 401）。
     * 业务侧必须通过 {@code DASHSCOPE_API_KEY} 或 {@code agentscope.dashscope.api-key} 注入真实 Key。
     */
    private static final String PLACEHOLDER_API_KEY = "sk-placeholder-moka-agent-please-replace";

    @Value("${agentscope.dashscope.api-key:${DASHSCOPE_API_KEY:}}")
    private String apiKey;

    @PostConstruct
    void warnIfApiKeyMissing() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("""

                    ===============================================================================
                    [Moka] DashScope API Key 未配置！
                           通过环境变量 DASHSCOPE_API_KEY 或 application.yml 中
                           agentscope.dashscope.api-key 注入后才能真正调用 LLM。
                           框架会正常启动并响应 /api/ping，但 /api/echo/* 会返回 500。
                    ===============================================================================""");
        } else {
            // 仅打印前 6 位，避免日志泄露完整 Key
            String preview = apiKey.length() <= 6 ? "***" : apiKey.substring(0, 6) + "***";
            logger.info("[Moka] DashScope API Key 已配置（预览：{}）", preview);
        }
    }

    /**
     * 解析用于 SDK 的 API Key。空值降级为占位符，避免启动期 IllegalArgumentException。
     */
    private String resolveApiKey() {
        return (apiKey == null || apiKey.isBlank()) ? PLACEHOLDER_API_KEY : apiKey;
    }

    @Bean("fastModel")
    @Lazy
    public Model fastModel() {
        return buildModel("qwen-flash", false);
    }

    @Bean("strongModel")
    @Primary
    @Lazy
    public Model strongModel() {
        return buildModel("qwen-plus", false);
    }

    @Bean("strongModelWithThinking")
    @Lazy
    public Model strongModelWithThinking() {
        return buildModel("qwen-plus", true);
    }

    @Bean("stableModel")
    @Lazy
    public Model stableModel() {
        return buildModel("qwen-turbo", false);
    }

    private Model buildModel(String modelName, boolean enableThinking) {
        return DashScopeChatModel.builder()
                .apiKey(resolveApiKey())
                .modelName(modelName)
                .stream(true)
                .enableThinking(enableThinking)
                .build();
    }
}
