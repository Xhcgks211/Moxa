package com.moka.agent.context;

import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 封装每次 Agent 调用的会话上下文（userId + sessionId）。
 *
 * <p>在 prototype bean 构建或工具调用时通过 {@link AgentSessionContextHolder} 写入当前线程，
 * AgentScope 会通过 {@code ToolExecutionContext} 自动注入到 {@code @Tool} 方法参数中
 * （参数无需标注 {@code @ToolParam}，对 LLM 完全透明）。</p>
 *
 * <p>预留 {@code attributes} 用于业务侧在同一次请求生命周期内传递轻量上下文数据，
 * 供 Hook、工具复用，避免重复查库或重新计算。</p>
 */
public class AgentSessionContext {

    private final String userId;
    private final String sessionId;

    /**
     * 业务自定义属性（如当前差旅单 ID、租户 ID、当前激活的子 Agent 等），
     * 使用 {@link ConcurrentHashMap} 以兼容并行 Toolkit 跨线程访问。
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public AgentSessionContext(String userId, String sessionId) {
        Assert.notNull(userId, "userId must not be null");
        Assert.notNull(sessionId, "sessionId must not be null");
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    public String toString() {
        return "AgentSessionContext{userId='" + userId + "', sessionId='" + sessionId + "'}";
    }
}

