# Moka Agent 启动脚手架

基于 **gogo-agent** 开发框架的最小可运行脚手架，用于在 `Moxa` 工程内快速启动一个
多 Agent 协作项目。

> gogo-agent = Spring Boot 3 + AgentScope 1.0.12 + DashScope LLM 的多智能体差旅系统参考实现。
> Moka = gogo-agent 的精简启动版，剥离差旅业务，保留 Agent 框架核心装配能力。

---

## 一、技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 21 | 语言运行时 |
| Spring Boot | 3.4.0 | Web 框架 |
| AgentScope | 1.0.12 | AI Agent 框架（ReAct 模式） |
| DashScope SDK | 由 AgentScope 管理 | 通义大模型接入 |
| Reactor | 由 Spring Boot 管理 | SSE / 响应式流 |

> 脚手架刻意保持精简：暂不引入 MyBatis-Plus、Redis、Sa-Token、RAG、MCP、Bailian 长期记忆等组件。
> 业务侧按需从 gogo-agent 平移即可（包名 `com.gogo.travel.*` → `com.moka.agent.*`）。

---

## 二、目录结构

```
Moxa/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/moka/agent/
    │   │   ├── MokaAgentApplication.java          # 启动入口
    │   │   ├── EchoAgent.java                     # 示范 Agent（含 2 个 @Tool）
    │   │   ├── common/PromptLoader.java           # prompt 加载与片段展开
    │   │   ├── config/
    │   │   │   ├── ModelConfig.java               # DashScope 模型 Bean
    │   │   │   └── AgentConfig.java               # EchoAgent prototype Bean
    │   │   ├── context/
    │   │   │   ├── AgentSessionContext.java       # 会话上下文（userId + sessionId）
    │   │   │   └── AgentSessionContextHolder.java # ThreadLocal 持有者
    │   │   ├── controller/HelloController.java    # /api/ping /api/echo SSE
    │   │   └── web/GlobalExceptionHandler.java    # 全局异常
    │   └── resources/
    │       ├── application.yml
    │       ├── logback-spring.xml
    │       └── prompts/echo-agent-system.md       # 示范 Agent 的 system prompt
    └── test/java/com/moka/agent/
        └── MokaAgentApplicationTests.java         # 上下文冒烟测试
```

---

## 三、快速启动

### 1. 申请 DashScope API Key

- 阿里云百炼控制台：<https://dashscope.console.aliyun.com/apiKey>
- 新账号有免费额度，可用于开发联调

### 2. 设置环境变量

PowerShell：

```powershell
$env:DASHSCOPE_API_KEY = "sk-xxxxxxxxxxxxxxxxxxxx"
```

或者直接修改 `src/main/resources/application.yml`：

```yaml
agentscope:
  dashscope:
    api-key: sk-xxxxxxxxxxxxxxxxxxxx
```

### 3. 启动

```powershell
# 在 Moxa 目录下
mvn spring-boot:run
```

或者：

```powershell
mvn clean package -DskipTests
java -jar target/moka-agent-1.0.0-SNAPSHOT.jar
```

启动成功后日志会显示：

```
Tomcat started on port 8080
Started MokaAgentApplication in 3.x seconds
```

### 4. 验证

```powershell
# 健康检查
curl http://localhost:8080/api/ping

# 同步调用示范 Agent
curl -X POST "http://localhost:8080/api/echo/demo-session-001?userId=demo-user" `
     -H "Content-Type: application/json" `
     -d '{"message":"你好，请用一句话介绍你自己"}'

# SSE 流式调用
curl -N -X POST "http://localhost:8080/api/echo/demo-session-001/stream?userId=demo-user" `
     -H "Content-Type: application/json" `
     -d '{"message":"现在几点了？"}'
```

---

## 四、框架装配要点

| 关注点 | 装配位置 | 说明 |
|--------|----------|------|
| LLM 模型 Bean | `config/ModelConfig.java` | 4 个档位（fast/strong/strongThinking/stable） |
| Agent Bean 工厂 | `config/AgentConfig.java` | prototype 作用域，从 ThreadLocal 注入 sessionCtx |
| `@Tool` 工具方法 | `EchoAgent.java` | 由 `Toolkit.registerTool(this)` 扫描注册 |
| prompt 模板 | `resources/prompts/*.md` | 支持 `{{include:...}}` 片段 + 动态变量 |
| 会话上下文 | `context/AgentSessionContext*.java` | ThreadLocal 持有，跨线程需用 `Holder.bind(...)` |
| 全局异常 | `web/GlobalExceptionHandler.java` | 统一 JSON 响应，不暴露堆栈 |

新增业务 Agent 的标准动作：

1. 在 `resources/prompts/` 下新增 `xxx-agent-system.md`
2. 新建 `XxxAgent.java` `@Component`，写若干 `@Tool` 方法
3. 在 `config/AgentConfig.java` 追加 `@Bean @Scope("prototype")` 暴露 ReActAgent
4. （可选）在 `controller/` 新增 controller，遵循 set→call→clear 模板

---

## 五、与 gogo-agent 的差异

| 模块 | gogo-agent | Moka |
|------|------------|------|
| MyBatis-Plus + Druid | ✅ | ❌（按需添加） |
| Redis + Sa-Token | ✅ | ❌（按需添加） |
| RAG 知识库（GENERIC + AGENTIC） | ✅ | ❌ |
| MCP（Weather / Orizn Visa） | ✅ | ❌ |
| 百炼长期记忆 | ✅ | ❌ |
| Tool 维度熔断 | ✅ | ❌ |
| 9 个 ReActAgent + 业务子 Agent | ✅ | ❌（仅 1 个 EchoAgent 示范） |
| 启动框架（Spring Boot + AgentScope + DashScope） | ✅ | ✅ |

**包名映射**：`com.gogo.travel.*` → `com.moka.agent.*`
**ArtifactId**：`gogo-agent` → `moka-agent`

---

## 六、下一步

参考 gogo-agent 的源码，按需平移以下模块到 Moxa：

1. 持久化：MyBatis-Plus + Druid + MySQL
2. 鉴权：Sa-Token + Redis
3. RAG：AgentScope 原生 `Knowledge` + Word/Excel 解析
4. MCP：Streamable HTTP / stdio 两种方式
5. 长期记忆：Bailian `LongTermMemory` + `AGENT_CONTROL` 模式
6. Hook 体系：执行日志、进度推送、敏感信息脱敏
7. 多 Agent 编排：MasterAgent + SubAgent + `SubAgentConfig`
