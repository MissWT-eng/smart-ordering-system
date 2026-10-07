# smart-ordering-system

一个给选择困难症的智能点餐系统。用自然语言说出你的口味、人数和预算，系统结合天气与在售菜单生成推荐，再由后端校验后落单。

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 框架 | Spring Boot 4 + MyBatis |
| 数据库 | MySQL 8 |
| 缓存 / 令牌 | Redis |
| 大模型 | LangChain4j + DeepSeek（对话）+ BGE-M3（向量化） |
| 向量库 | Qdrant |
| 外部能力 | 和风天气 API |
| 前端 | 原生 HTML/CSS/JS（用户端 `static/user-frontend`、商户端 `merchant_ui.html` 第二版完善） |

## 功能概览

- **用户识别**：手机号识别注册用户，未注册按游客处理，游客同样可以看菜单、拿推荐、下订单。
- **菜单管理**：商户接口维护菜品（增删改、上下架），只有上架菜品可被推荐和下单。
- **智能推荐**：结合人数、预算、口味偏好、当日天气与在售菜单生成推荐，结果经后端校验（存在性、在售、价格、预算）后才进入订单。
- **订单闭环**：创建订单、状态流转、历史订单查询，推荐过程写入推荐日志。
- **反馈收集**：记录用户对菜品的反馈，用于后续推荐优化。
- **鉴权**：用户令牌存 Redis（默认 2 小时过期），商户管理接口使用静态令牌，越权访问返回 401/403。

## 目录结构

```
smart-ordering-system
├── docs/                      需求文档、业务建模、数据库设计与建表语句
├── src/main/java/com/luwei/ordering
│   ├── bootstrap/             数据初始化
│   ├── common/                枚举、异常、统一响应、类型处理器
│   ├── config/                AI 配置、CORS、用户与商户拦截器
│   ├── controller/            用户端与商户端接口
│   ├── converter/             实体与 DTO 转换
│   ├── dto/                   请求、响应与 AI 数据结构
│   ├── entity/                数据库实体
│   ├── mapper/                MyBatis Mapper
│   └── service/               业务与 AI 推荐逻辑
├── src/main/resources
│   ├── mapper/                Mapper XML
│   └── static/user-frontend/  用户端页面
└── merchant_ui.html           商户端页面
```

## 主要接口

| 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- |
| GET | `/dishes` | 菜单列表 | 否 |
| GET | `/dishes/{dishNumber}` | 菜品详情 | 否 |
| POST | `/recommendations` | 智能推荐（支持游客） | 否 |
| POST | `/orders` | 创建订单（支持游客） | 否 |
| GET | `/orders/{userId}` | 历史订单查询 | 用户令牌 |
| POST | `/feedback` | 提交菜品反馈 | 否 |
| POST | `/users/identify` | 手机号识别并签发令牌 | 否 |
| GET | `/users/{userId}` | 用户信息 | 用户令牌 |
| POST / PUT | `/admin/dishes` | 商户维护菜品、上下架、向量重建 | 商户令牌 |
| PATCH | `/admin/orders` | 商户更新订单状态 | 商户令牌 |

用户令牌通过请求头 `X-User-Token` 传递，商户令牌通过 `X-Admin-Token` 传递。

## 本地启动

1. 准备依赖服务：MySQL（库名 `smart_ordering`）、Redis、Qdrant。建表语句见 `docs/智能点餐系统数据库建表语句.md`。
2. 配置环境变量（密钥不写入仓库）：

```bash
export DEEPSEEK_API_KEY=...
export BGE_M3_API_KEY=...
export QWEATHER_API_KEY=...
export ADMIN_TOKEN=...
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=...
```

3. 启动应用：

```bash
./mvnw spring-boot:run
```

4. 访问用户端 `http://localhost:8080/user-frontend/index.html`，商户端 `http://localhost:8080/merchant_ui.html` 第二版完善。

## 文档

- `docs/requirements.md`：第一版需求与验收标准
- `docs/智能点餐系统业务建模.md`：业务建模说明
- `docs/智能点餐系统数据库设计思路.md`、`docs/智能点餐系统数据库建表语句.md`：数据库设计
