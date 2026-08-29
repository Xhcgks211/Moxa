package com.moka.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 上下文加载冒烟测试：仅验证 Spring 容器能正常启动、Agent Bean 装配正确，
 * 不发起真实的 LLM 调用，避免 CI 阶段消耗 token。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "agentscope.dashscope.api-key=test-dummy-key"
})
class MokaAgentApplicationTests {

    @Test
    void contextLoads() {
    }
}
