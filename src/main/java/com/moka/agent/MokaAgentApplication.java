package com.moka.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Moka Agent 启动入口。
 *
 * <p>基于 gogo-agent 开发框架构建，集成 AgentScope ReActAgent、DashScope LLM，
 * 提供多 Agent 协作、会话上下文、SSE 流式输出等基础能力。</p>
 *
 * <p>启动方式：
 * <pre>
 *   mvn spring-boot:run
 *   # 或
 *   mvn package && java -jar target/moka-agent-1.0.0-SNAPSHOT.jar
 * </pre>
 * </p>
 */
@SpringBootApplication
@EnableAsync
public class MokaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MokaAgentApplication.class, args);
    }
}
