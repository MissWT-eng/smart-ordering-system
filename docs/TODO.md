# 项目待办

## 待做：学习参考项目，补齐 V2 会话记忆与流式聊天

- 状态：待做
- 添加日期：2026-10-10
- 目标：在现有 LangChain4j 点餐 Agent 上补齐会话记忆、会话归属校验和流式聊天体验。

### 学习资料

1. [LangChain4j 官方 AI Service 示例](https://github.com/langchain4j/langchain4j-examples/blob/main/tutorials/src/main/java/_08_AIServiceExamples.java)：重点阅读按用户分开记忆的示例，理解 `@MemoryId`、`chatMemoryProvider` 和记忆窗口。
2. [微软 LangChain4j 课程 04-tools](https://github.com/microsoft/LangChain4j-for-Beginners/blob/main/04-tools/README.md)：理解模型选工具、执行工具、回传结果和同一轮串联多个工具的过程。
3. [鱼皮 ai-code-helper](https://github.com/liyupi/ai-code-helper)：挑选会话记忆、流式响应、SSE 和前后端对接模块学习，并核对与当前项目依赖版本的差异。

### 实施与验收

- [ ] 记录上述三个参考项目中可复用的设计及对应源码位置。
- [ ] 为 `OrderingAgent` 增加会话记忆，能在后续轮次保留人数、预算、口味等信息。
- [ ] 后端校验会话归属；注册用户与游客使用受控的会话标识，不能仅凭客户端传入的编号读取会话。
- [ ] 验证两个不同用户或游客会话互不串话，越权访问会话被拒绝。
- [ ] 限制记忆大小并明确过期、清理策略，避免上下文和存储无限增长。
- [ ] 增加流式聊天与前端增量展示，处理断线、超时、完成和错误状态，避免重试造成重复写订单。
- [ ] 用一次多轮点餐演示和简短实现说明记录成果，说明工具调用链路及会话隔离方式。

### 依赖与边界

- 复用现有业务 Service，先完成最小可用链路，不为学习参考项目而整体迁移框架。
- 会话记忆不能替代订单确认与后端鉴权；正式下单仍需明确确认和业务校验。
