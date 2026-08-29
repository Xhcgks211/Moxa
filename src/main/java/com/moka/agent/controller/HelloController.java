package com.moka.agent.controller;

import com.moka.agent.context.AgentSessionContext;
import com.moka.agent.context.AgentSessionContextHolder;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Moka 框架 HTTP 接入入口。
 *
 * <p>提供 3 个接口：
 * <ul>
 *   <li>{@code GET /api/ping}  —— 框架健康检查（不调用 LLM）</li>
 *   <li>{@code POST /api/echo/{sessionId}} —— 同步调用 EchoAgent，返回完整回复</li>
 *   <li>{@code POST /api/echo/{sessionId}/stream} —— SSE 流式调用，逐 token 推送</li>
 * </ul>
 *
 * <p>所有接口均按 gogo-agent 的标准模式处理：先构造 {@link AgentSessionContext} 写入
 * {@link AgentSessionContextHolder}，再调用 prototype 作用域的 Agent Bean；调用结束
 * （无论成功 / 异常）必须 {@code AgentSessionContextHolder.clear()}，避免 ThreadLocal 泄漏。</p>
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    private static final Logger logger = LoggerFactory.getLogger(HelloController.class);

    /** 简单的 SSE 回调线程池，避免占用 Tomcat worker。 */
    private static final Executor SSE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "moka-sse");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    @Qualifier("echoAgent")
    private org.springframework.beans.factory.ObjectProvider<ReActAgent> echoAgentProvider;

    @Autowired
    @Qualifier("defaultEchoAgent")
    private ReActAgent defaultEchoAgent;

    // ===================== 健康检查 =====================

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("framework", "moka-agent");
        body.put("agentscope", "1.0.12");
        body.put("time", java.time.LocalDateTime.now().toString());
        return body;
    }

    // ===================== 同步调用 =====================

    @PostMapping(value = "/echo/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> echo(
            @PathVariable String sessionId,
            @RequestParam(name = "userId", defaultValue = "demo-user") String userId,
            @RequestBody Map<String, String> body) throws Exception {

        String message = body.getOrDefault("message", "");
        logger.info("[CONTROLLER] sync echo 收到请求 sessionId={}, userId={}, messageLength={}",
                sessionId, userId, message.length());

        AgentSessionContext ctx = new AgentSessionContext(userId, sessionId);
        AgentSessionContextHolder.set(ctx);
        try {
            // 从 ObjectProvider 获取 prototype 作用域的 agent 实例（会复用当前线程的 AgentSessionContext）
            ReActAgent agent = echoAgentProvider.getObject();

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .name("user")
                    .content(TextBlock.builder().text(message).build())
                    .build();

            Msg reply = agent.call(userMsg).block();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sessionId", sessionId);
            resp.put("userId", userId);
            resp.put("reply", extractText(reply));
            resp.put("raw", reply);
            return resp;
        } finally {
            AgentSessionContextHolder.clear();
        }
    }

    // ===================== SSE 流式调用 =====================

    @PostMapping(value = "/echo/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String sessionId,
            @RequestParam(name = "userId", defaultValue = "demo-user") String userId,
            @RequestBody Map<String, String> body) {

        String message = body.getOrDefault("message", "");
        SseEmitter emitter = new SseEmitter(60_000L);

        SSE_EXECUTOR.execute(() -> {
            AgentSessionContext ctx = new AgentSessionContext(userId, sessionId);
            AgentSessionContextHolder.set(ctx);
            try {
                ReActAgent agent = echoAgentProvider.getObject();
                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .name("user")
                        .content(TextBlock.builder().text(message).build())
                        .build();

                // 流式订阅：每个增量 token 推一次
                agent.call(userMsg)
                        .filter(msg -> msg != null)
                        .doOnNext(msg -> {
                            try {
                                String chunk = extractText(msg);
                                emitter.send(SseEmitter.event().name("chunk").data(chunk));
                            } catch (Exception ex) {
                                emitter.completeWithError(ex);
                            }
                        })
                        .doOnError(emitter::completeWithError)
                        .doOnSuccess(v -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            } catch (Exception ignored) {
                            } finally {
                                emitter.complete();
                            }
                        })
                        .block();
            } catch (Exception e) {
                logger.error("[CONTROLLER] stream 调用失败", e);
                emitter.completeWithError(e);
            } finally {
                AgentSessionContextHolder.clear();
            }
        });

        return emitter;
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }
}

