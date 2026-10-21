# M2 SSE 实现与验证记录

更新日期：2026-10-21。

## 请求与事件协议

`POST /agent/chat/stream` 使用 `Content-Type: application/json` 和 `Accept: text/event-stream`。请求体复用 `AgentChatRequest`：

```json
{
  "message": "本次2人，预算100元，清淡不辣。请记住，暂不推荐或下单。"
}
```

首次不传 `conversationId`，后续携带返回的编号。注册用户传 `X-User-Token`；游客保留 Session Cookie。确认订单仍须明确提交 `confirmedDraftId`，流式回复不会放宽已有确认校验。

正常响应的 Content-Type 为 `text/event-stream`，包含 `Cache-Control: no-store` 和 `X-Accel-Buffering: no`。各事件的 data 都是 JSON：

```text
event:meta
data:{"conversationId":"<实际会话编号>"}

event:delta
data:{"text":"好的，"}

event:delta
data:{"text":"已记住本次需求。"}

event:done
data:{"conversationId":"<实际会话编号>","reply":"好的，已记住本次需求。","proposalToolResult":null,"draftToolResult":null,"orderToolResult":null}

```

模型或流式初始化失败时，以 error 结束：

```text
event:error
data:{"code":504,"msg":"本次回复生成失败","data":null}

```

流已开始后 HTTP 状态仍为 200，由终止事件表示生成结果；参数、身份、会话、并发许可校验发生在流开始前，失败使用相应 HTTP 状态与 JSON，即使请求 Accept 为 SSE 也如此。

`delta` 用于增量展示；工具调用前也可能产生文字，因此最终以 `done.reply` 为准，可用它替换增量预览。`done.proposalToolResult` 来自本轮实际工具对象，以最后一次 `proposeUserPreference` 调用为准；失败或空结果会覆盖较早的成功候选。普通聊天为空。成功候选尚未保存，仍需用户核对后调用独立画像 PUT。

浏览器使用 `fetch` POST 读取流；原生 EventSource 不能直接满足本接口的 POST 请求体及自定义 token 请求头。读取端按 SSE 空行分帧并使用流式 UTF-8 解码，不能把一次网络读取当作一个完整事件。后续已接入真实前端及结构化草稿、订单字段，见[前端记录](m2-frontend-verification.md)。

## 执行与资源生命周期

- 同步和流式使用同一个 OrderingAgent、工具与会话记忆。全局共享 4 个并发许可，同会话仍只允许一条消息执行；输入最多 2000 字。
- 每个流式请求创建一个 `AgentChatStreamSession`，集中串行处理发送与终止状态。meta 发送后，TokenStream 的增量、工具、完成和异常回调分别驱动后续事件。
- 模型完成、失败或启动失败时，会话计数与许可只归还一次。工具结果由实际回调取得，不解析模型自由文本。
- HTTP 断连或 SSE 超时只停止输出；已启动的模型或工具可能仍在执行，真实终止回调到达后才归还计数及许可，避免同一记忆被提前再次使用或清理。
- SSE 连接超时为 60 秒；进入 Spring 超时回调后响应已不能继续发送事件，直接完成连接。客户端若 EOF 前未收到 done，应展示“回复未完成”。流式模型每次上游请求也配置 60 秒超时，但这不是整条多工具链的总时限。
- 尚未实现即时取消、心跳、断点续传、消息归档或缓存会话总数限制。不能自动重放中断请求；订单可能已经创建，应继续使用已有草稿确认与幂等校验核实。

## SSE 后端首轮持久回归测试：29/29 通过

执行 `./mvnw test`，失败、错误、跳过均为 0。其中新增 SSE 测试 27 项，既有请求校验和 Spring 上下文测试各 1 项。

| 测试类 | 数量 | 主要覆盖 |
| --- | --- | --- |
| AgentChatStreamSessionTest | 14 | meta/delta/done、特殊文本、真实工具结果类型、最后一次候选覆盖、模型和启动异常、空最终回复、发送失败、断连、超时、竞争终止与仅释放一次 |
| AgentChatStreamingControllerTest | 13 | 实际 Controller 与 MockMvc SSE 输出、可信上下文、参数及身份拒绝、会话 403/404/409、共享 4 个许可、同会话忙碌、异常释放、实际 Spring 异步超时及断连回调 |

新增测试使用受控 TokenStream、真实本地会话计数实现及模拟 Redis，不依赖模型随机输出或真实长时间等待。完整 Maven 中的既有上下文测试会启动应用上下文，不能据此宣称所有业务场景均已覆盖。

## 真实链路检查：9/9 通过

使用独立端口 18081 启动本次后端，连接真实 DeepSeek、MySQL 与 Redis，完成：

1. 首次请求收到 meta、多次 delta 及 done；本次观测 129 个文字事件，首段约 689 毫秒、结束约 1520 毫秒。这只是本机一次观测，不是性能基准。
2. 同一游客携带 conversationId 续聊，能复述 2 人、100 元、清淡不辣。
3. 随后使用同步 `/agent/chat`，仍可读取同一段聊天记忆。
4. 注册用户调用真实画像候选工具，done 包含成功候选及所请求的微辣、少油；原画像及创建、更新时间不变。
5. 真实菜单工具通过 SSE 返回在售菜名和价格，普通菜单聊天无画像候选。本次未启动 Qdrant，不代表推荐检索链路已回归。
6. 无效 token 在流开始前返回 HTTP/body 401 JSON。
7. 2001 字输入在流开始前返回 HTTP/body 400 JSON。
8. 注册用户访问游客会话被 403 拒绝；拒绝请求没有新增会话元数据。
9. 全程无新订单或订单草稿，原长期画像保持不变。

初次联调期间，本机 Redis Windows 进程崩溃，Windows 应用事件记录了 redis-server.exe 的文件访问异常。恢复 Redis 后，上述 9 项重新全部通过；未将该中断记录为成功，也未修改会话 Lua 来绕过故障。

该轮验证结束后，删除生成的 1 个测试 token 与 3 个会话元数据，停止独立验证后端，移除临时验证程序、编译产物和依赖清单。持久测试保留在 `src/test`。该轮没有通过真实 HTTP 注入模型故障、触发 60 秒超时或制造 TCP 断连；这些生命周期分支由受控回归测试覆盖。该阶段未测试真实订单写入。

## 同日前端接入后的补充

AgentChatResponse 新增 `draftToolResult`、`orderToolResult`，同步与流式均从实际工具对象提取。当前 Java 测试扩为 32 项（Session 16、Controller 14、既有测试 2），另有 18 项前端持久测试，均通过。真实浏览器完成续聊、草稿卡及取消确认，隔离浏览器覆盖明确提交、口味保存清除与错误恢复；详情及截图见[前端验证记录](m2-frontend-verification.md)。上面的 9 项真实 HTTP 检查保留为 SSE 后端首轮证据，没有与后续界面验证混为同一轮。

## 实现依据

- [LangChain4j AI Services](https://docs.langchain4j.dev/tutorials/ai-services/)：TokenStream 与流式回调；项目依赖的实际接口同时通过本地 jar 核对。
- [Spring MVC 异步请求](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)：SseEmitter、异步生命周期及连接异常处理。
