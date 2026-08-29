package com.moka.agent.config;

import com.moka.agent.EchoAgentFactory;
import com.moka.agent.common.PromptLoader;
import com.moka.agent.context.AgentSessionContext;
import com.moka.agent.context.AgentSessionContextHolder;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

/**
 * Moka Agent 框架默认 Bean 装配：把模型、会话上下文、Toolkit 统一接入。
 *
 * <p>当前提供一个示范 Agent {@code echoAgent}：
 * <ul>
 *   <li>使用 {@code strongModel} 作为推理模型</li>
 *   <li>使用 {@code AutoContextMemory}（自动压缩 / 卸载大消息）抑制多轮输入 token 膨胀</li>
 *   <li>通过 prototype scope 保证每次请求获取独立实例，并自动从 ThreadLocal 注入会话上下文</li>
 * </ul>
 *
 * <p>所有 Agent Bean 均为 {@link Lazy @Lazy}：仅在第一次调用时才会真正创建 Agent 实例、
 * 触发底层 Model 初始化。这样即便未配置 DashScope API Key，Spring 上下文也能正常启动，
 * 业务侧可在准备好 Key 之后通过真实请求触发 Agent 装配。</p>
 *
 * <p>新增业务 Agent 时参考本类即可：定义一个 {@code @Configuration}，暴露 prototype 作用域的
 * {@link ReActAgent} bean 工厂方法，并按需注册 {@code @Tool} 方法。</p>
 */
@Configuration
public class AgentConfig {

    /**
     * 主 Agent 工具执行超时（分钟）。
     */
    private static final int TOOL_TIMEOUT_MINUTES = 5;
    /**
     * 主 Agent 最大推理迭代次数。
     */
    private static final int MAX_ITERATIONS = 8;

    @Bean(name = "echoAgent")
    @Scope("prototype")
    @Lazy
    public ReActAgent echoAgent(@Qualifier("strongModel") Model strongModel,
                                @Qualifier("stableModel") Model stableModel,
                                EchoAgentFactory agentFactory,
                                ApplicationContext context) {
        // 从 ThreadLocal 读取当前会话上下文（由 Controller 在创建本 bean 之前投影到当前线程）
        AgentSessionContext sessionCtx = AgentSessionContextHolder.get();
        String userId = sessionCtx != null ? sessionCtx.getUserId() : null;
        String sessionId = sessionCtx != null ? sessionCtx.getSessionId() : null;

        ToolExecutionContext toolCtx = sessionCtx != null
                ? ToolExecutionContext.builder().register(sessionCtx).build()
                : ToolExecutionContext.empty();

        // Toolkit 暂时为空 —— 业务侧通过 agentFactory.tools() 扩展
        Toolkit toolkit = agentFactory.tools();

        // AutoContextMemory：自动压缩 / 卸载大消息，抑制多轮输入 token 膨胀
        AutoContextConfig memoryConfig = AutoContextConfig.builder()
                .largePayloadThreshold(4 * 1024L)
                .offloadSinglePreview(300)
                .maxToken(128 * 1024L)
                .tokenRatio(0.75)
                .msgThreshold(60)
                .lastKeep(20)
                .build();
        AutoContextMemory memory = new AutoContextMemory(memoryConfig, stableModel);

        return ReActAgent.builder()
                .name("EchoAgent")
                .description("Moka 框架的示范 Agent：演示如何接入 DashScope 模型、@Tool、AutoContextMemory")
                .model(strongModel)
                .toolkit(toolkit)
                .toolExecutionContext(toolCtx)
                .toolExecutionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofMinutes(TOOL_TIMEOUT_MINUTES))
                        .maxAttempts(3)
                        .build())
                .memory(memory)
                .sysPrompt(PromptLoader.load("echo-agent-system.md"))
                .maxIters(MAX_ITERATIONS)
                .generateOptions(GenerateOptions.builder()
                        .cacheControl(true)
                        .build())
                .build();
    }

    /**
     * 提供一个不依赖上下文的兜底 Agent Bean（用于框架冒烟测试 / 控制台启动验证）。
     */
    @Bean(name = "defaultEchoAgent")
    @Lazy
    public ReActAgent defaultEchoAgent(@Qualifier("strongModel") Model strongModel,
                                       EchoAgentFactory agentFactory) {
        Toolkit toolkit = agentFactory.tools();
        return ReActAgent.builder()
                .name("DefaultEchoAgent")
                .description("Moka 框架的兜底 Agent：不绑定会话上下文，仅用于冒烟测试")
                .model(strongModel)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .sysPrompt(PromptLoader.load("echo-agent-system.md"))
                .maxIters(MAX_ITERATIONS)
                .build();
    }
}
