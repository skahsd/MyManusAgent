# My-Manus

基于 Spring AI + ReAct 模式的多智能体任务编排系统，受 [Manus](https://manus.im/) 启发，通过 LLM 驱动 Planning Agent 拆解复杂任务，调度多个领域 Agent（浏览器、图表、表格等）协同完成。

## 架构概览

```
用户(WebSocket) ──▶ WebSocketController ──▶ WebSocketServiceImpl
                                                   │
                                          WSSession（消息缓冲队列）
                                                   │
                                          AgentFactory ──▶ ReActPlanningAgent
                                                   │
                                          ┌──── ReAct 循环 ────┐
                                          │  1. 获取当前状态     │
                                          │  2. LLM 推理决策     │
                                          │  3. 执行子任务 Agent  │
                                          │  4. 结果反馈，继续循环 │
                                          └────────────────────┘
                                                   │
                              ┌────────────────────┼────────────────────┐
                              ▼                    ▼                    ▼
                        BrowserAgent         ChartAgent           TableAgent
                      (Playwright 自动化)    (数据可视化)          (表格生成)
```

## 核心特性

- **ReAct 推理模式**：思考（Thinking）→ 行动（Action）→ 观察（Observation）循环，LLM 根据每一步的执行结果动态调整后续规划
- **Planning Agent**：将复杂任务拆解为子任务链，每次规划一个子任务，根据上一步结果决定下一步
- **多 Agent 协作**：BrowserAgent（网页操作）、ChartAgent（图表）、TableAgent（表格）、HtmlDocAgent（网页内容生成）、AMAPAgent（地理信息）
- **LLM 驱动的浏览器自动化**：注入 JS 标注 DOM 可交互元素 → 生成文本地图 → LLM 语义理解后决策操作目标，不依赖硬编码选择器
- **网页内容降级提取**：HTML → Markdown → 纯文本 → 可见元素摘要，Token 预算内自动选择最优格式
- **WebSocket 实时通信**：全程向客户端推送思考过程、执行状态和最终结果
- **工具自动注册**：通过 `@Tool` 注解声明工具，运行时自动合并到 LLM 的 function calling Schema 中

## 技术栈

| 技术 | 说明 |
|------|------|
| Java 17 + Spring Boot 3.4 | 基础框架 |
| Spring AI 1.0 | LLM 调用抽象层 |
| Spring WebSocket + STOMP | 实时通信 |
| Playwright 1.51 | 浏览器自动化 |
| Jsoup + Flexmark | HTML 解析与 Markdown 转换 |
| Hutool 5.8 | 通用工具库 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- Playwright 浏览器（首次运行自动下载 Chromium）

### 配置

在项目根目录创建 `.env` 文件：

```env
VOLCES_API_KEY=你的API密钥
```

编辑 `src/main/resources/application.yml` 配置模型：

```yaml
agent-model:
  base-url: https://ark.cn-beijing.volces.com/api/
  api-key: ${VOLCES_API_KEY}
  model-name: deepseek-v3-250324
  completions-path: /v3/chat/completions
  stream: false

plan-model:
  base-url: https://ark.cn-beijing.volces.com/api/
  api-key: ${VOLCES_API_KEY}
  model-name: deepseek-r1-250120
  completions-path: /v3/chat/completions
```

### 启动

```bash
mvn spring-boot:run
```

服务启动在 `http://localhost:18081`，通过 WebSocket 端点 `/ws` 连接，发送消息到 `/enhanced-dialog`。

### 前端

项目包含 Vue 前端（`my-manus-vue/` 目录），支持对话式交互：

```bash
cd my-manus-vue
npm install
npm run dev
```

## 项目结构

```
src/main/java/cn/mymanus/manus/
├── agent/
│   ├── Agent.java                  # Agent 接口
│   ├── BaseAgent.java              # 基础抽象类
│   ├── ReActBaseAgent.java         # ReAct 循环核心实现
│   ├── AgentFactory.java           # Agent 工厂注册
│   ├── planning/
│   │   └── ReActPlanningAgent.java # 任务规划 Agent
│   ├── prompt/
│   │   └── PromptManagement.java   # Prompt 模板管理
│   └── bean/
│       └── ReActInput.java         # ReAct 输入/输出结构
├── config/
│   ├── ModelConfig.java            # 大模型配置
│   ├── ReActConfig.java            # ReAct 参数配置
│   └── WebSocketConfig.java        # WebSocket 配置
├── controller/
│   └── WebSocketController.java    # WebSocket 入口
├── service/
│   ├── WebSocketService.java       # 消息处理接口
│   └── impl/
│       ├── WebSocketServiceImpl.java    # 消息处理实现
│       └── PageContentExtractServiceImpl.java  # 网页内容提取
├── message/
│   ├── MessageSession.java         # 会话接口
│   ├── WSSession.java              # WebSocket 会话实现
│   └── WSSessionManagement.java    # 会话管理器
├── dto/
│   └── DialogMessageDTO.java       # 消息传输对象
├── enums/
│   └── AgentTypeEnum.java          # Agent 类型枚举
└── constants/
    └── Constant.java               # 常量定义

src/main/resources/
├── prompt/           # Prompt 模板（.txt）
├── schema/           # LLM function calling Schema（.json）
└── application.yml   # 应用配置
```

## 工作流程

1. 用户通过 WebSocket 发送任务消息
2. `WSSession` 将消息放入阻塞队列缓冲
3. `AgentFactory` 创建 `ReActPlanningAgent`，在线程池中异步执行
4. **ReAct 循环**：
   - LLM 输出 `current_state`（上一步评估/记忆/思考）和 `action`（工具调用）
   - `generateNext`：规划下一个子任务，指派给特定 Agent
   - `getCurrentStatus`：执行子任务链中最后一个未被执行的子任务
   - 执行结果反馈给 LLM，进入下一轮决策
5. 子任务链全部执行完毕，LLM 调用 `done` 结束
6. 结果通过 WebSocket 推回客户端


