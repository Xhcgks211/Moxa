package com.moka.agent;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moka 框架示范 Agent 工厂 & 工具承载。
 *
 * <p>本类同时承担两个角色（避免与 {@code @Bean(name="echoAgent")} 命名冲突）：
 * <ol>
 *   <li>作为 {@code @Component}（默认名 {@code echoAgentFactory}），由 Spring 注入到
 *       {@link com.moka.agent.config.AgentConfig} 用于构造 {@code ReActAgent}</li>
 *   <li>承载若干 {@code @Tool} 方法（{@link #echo} / {@link #currentTime}），供 Agent 在推理时调用</li>
 * </ol>
 *
 * <p>业务侧新增 Agent 时推荐按此模式：{@code @Component} 暴露 {@code tools()}，并把 {@code @Tool} 集中放在本类或独立的
 * Tools 类里。{@link Toolkit#registerTool(Object)} 会扫描 {@code @Tool} 方法自动注册。</p>
 */
@Component("echoAgentFactory")
public class EchoAgentFactory {

    private static final Logger logger = LoggerFactory.getLogger(EchoAgentFactory.class);

    /**
     * 构造 Agent 时使用的 Toolkit 工厂方法。
     */
    public Toolkit tools() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(this);
        return toolkit;
    }

    /**
     * 简单回声工具：把用户传入的文本原样返回，便于框架冒烟测试。
     *
     * <p>{@code @Tool} 注解让 AgentScope 自动暴露给 LLM，{@code @ToolParam(name = "text", description = "...")}
     * 描述参数语义。LLM 看到工具签名后即可在 ReAct 循环中调用。</p>
     */
    @Tool(description = "把用户输入的文本原样返回，常用于框架联调测试。")
    public Map<String, Object> echo(
            @ToolParam(name = "text", description = "需要回显的文本") String text) {
        logger.info("[ECHO] agent 工具被调用，text={}", text);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("length", text == null ? 0 : text.length());
        return result;
    }

    /**
     * 当前时间工具：返回服务器本地时间，便于演示工具调用日志与时间注入。
     */
    @Tool(description = "返回服务器当前的日期与时间（yyyy-MM-dd HH:mm:ss）。")
    public Map<String, Object> currentTime() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logger.info("[ECHO] currentTime 工具被调用，now={}", now);
        return Map.of("now", now);
    }
}
