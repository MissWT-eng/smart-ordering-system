# M1 故障场景与 V1 回归验证记录

验证日期：2026-11-11。环境：本地 Spring Boot 8080、MySQL `smart_ordering`、Redis 6379、Qdrant 6334，真实 DeepSeek 与 BGE-M3 服务。

本轮覆盖 M1 完成标准第 5 条（故障降级）与第 7 条（主要回归场景可重复验证）。构造方式以命令行参数或停依赖服务为主，未修改业务代码。

## 1. 故障场景

### 1.1 Redis 不可用

- 构造：`redis-cli shutdown nosave`，确认 `ping` 拒绝连接。
- 结果：`POST /agent/chat` 返回 `HTTP 500`，响应体 `{"code":500,"msg":"系统内部错误"}`；服务端日志保留 `RedisConnectionException: Unable to connect to localhost:6379` 完整堆栈；`GET /dishes` 仍为 `200`。
- 结论：通过。未泄露堆栈，未越权，不依赖 Redis 的菜单查询未被阻断。提示为通用文案，未区分具体故障类型，列为已知限制（见第 4 节）。
- 恢复：重启 Redis，`/agent/chat` 恢复正常会话。

### 1.2 模型超时

- 构造：启动参数 `--langchain4j.open-ai.streaming-chat-model.timeout=1s`。
- 结果：`POST /agent/chat/stream` 先发出 `meta` 事件，随后发出 `error` 事件 `{"code":504,"msg":"本次回复生成失败"}`，连接正常结束，未静默挂起。
- 结果补充：同一实例随后发起同步 `/agent/chat` 请求成功返回，说明超时后会话计数与并发许可已归还。
- 结论：通过。

### 1.3 凭证无效

- 构造：`X-User-Token: fake-token-123`。
- 结果：`POST /agent/chat` 与 `GET /orders/1` 均返回 `401`，响应体 `{"code":401,"msg":"未授权用户"}`。
- 结论：通过。

### 1.4 订单已生成但最终回复失败

- 构造：先通过 `/agent/chat` 生成草稿（清炒藕片 1 份、清炒莴笋 1 份，预算 100，草稿 `87d4e61b-…`）；随后发送带 `confirmedDraftId` 的确认请求，`curl --max-time 2` 在 2 秒后主动断开（exit 28），模拟客户端未收到回复。
- 结果：等待 12 秒后，`GET /order-drafts/{draftId}/order` 返回 `status=found`，订单号 19、金额 42.00、状态 `pending`，明细与草稿一致。
- 结果补充：再次发送同一确认请求，模型回复“这份草稿已经下单成功了，订单号 19……无需重复提交”，数据库按 `draft_id` 计数仍为 1 笔。
- 结论：通过。断流不等于订单失败，按草稿可恢复查询，重复确认不新建订单。

### 1.5 天气服务不可用

- 构造：启动参数 `--weather.api-key=invalid-key`，并删除 Redis 缓存 `weather:current:101060101`（首次尝试因命中缓存未生效，清缓存后复测）。
- 结果：询问天气时回复“抱歉，天气信息暂时查不到”；紧接着要求推荐，正常返回 3 道清淡候选。
- 结论：通过。天气失败不阻断推荐。
- 说明：`GET /weather` 受登录拦截，返回 `401`，属现有设计，不是天气故障的下游表现。

## 2. V1 既有接口回归

| 接口 | 请求 | 结果 |
| --- | --- | --- |
| `GET /dishes?pageNum=1&pageSize=10` | 公开 | 200 |
| `GET /dishes/46` | 公开 | 200 |
| `POST /recommendations` | 游客 `{"preference":"清淡"}` | 200 |
| `POST /orders` | 游客 `{"orderItems":[{"dishNumber":46,"quantity":1}]}` | 200，订单号 20、22.00 |
| `POST /users/identify` | `18993371267` | 200，userId=3、签发 token |
| `GET /orders/3` | 携带该 token | 200，total=5，分页与明细正常 |
| `GET /orders/1` | 携带 userId=3 的 token | 403 |
| `POST /admin/dishes` | 无 token / 错误 token | 401 |
| `POST /admin/dishes` | 正确 `X-Admin-Token` | 200，新建菜品 82 |
| `PUT /admin/dishes/82/status` | `{"status":"unavailable"}` | 200，查询确认状态已变更 |

结论：V1 菜单、推荐、手动下单、历史查询、越权拒绝与商户鉴权均正常，未受 V2 改动影响。

## 3. 并发确认

- 构造：对同一 `conversationId` 与同一 `confirmedDraftId` 同时发起两个确认请求。
- 结果：一个返回 `200`（已有订单），另一个返回 `409`“当前对话正在回复中”；数据库按 `draft_id` 计数为 1。
- 约束核对：`orders.draft_id` 列存在唯一索引，`Non_unique=0`；实际索引名为 `draft_id`，建表文档已按实际情况更正。
- 结论：通过（同一草稿最多一笔订单）。限制：HTTP 层并发被同会话互斥先行拦截，数据库重复键捕获分支仍未确定性触发，该限制沿用此前记录。

## 4. 已知限制

1. Redis 不可用时返回通用 `500 系统内部错误`，未区分“服务暂不可用”与业务错误；不影响正确性与越权边界，属体验问题。
2. 并发确认在同会话互斥保护下运行，数据库唯一键的实际触发路径仍未验证。
3. 商户接口只提供新增与修改，无删除入口，验证产生的临时菜品需直接清理数据库。
4. 天气测试依赖真实上游，`api-key` 无效与网络故障表现相同，未单独区分。

## 5. 测试数据清理

验证结束后已删除：订单 19（草稿确认）与订单 20（手动下单）及其明细、临时菜品 82。清理后核对三项计数均为 0。临时会话与草稿依赖 Redis 过期自动清理，未额外操作。
