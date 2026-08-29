package com.moka.agent.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * <p>模型选型与 gogo-agent 保持一致，便于后续业务无缝迁移。</p>
 */
@Configuration
public class ModelConfig {

    @Value("${agentscope.dashscope.api-key:${DASHSCOPE_API_KEY:}}")
    private String apiKey;

    @Bean("fastModel")
    public Model fastModel() {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-flash")
                .stream(true)
                .enableThinking(false)
                .build();
    }

    @Bean("strongModel")
    @Primary
    public Model strongModel() {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .stream(true)
                .enableThinking(false)
                .build();
    }

    @Bean("strongModelWithThinking")
    public Model strongModelWithThinking() {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .stream(true)
                .enableThinking(true)
                .build();
    }

    @Bean("stableModel")
    public Model stableModel() {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-turbo")
                .stream(true)
                .enableThinking(false)
                .build();
    }
}
