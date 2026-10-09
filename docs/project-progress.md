# InterviewGuide 续接记录

## 快速定位

- 最后评估：2026-09-13
- 当前阶段：**第 1 步「基础后端」代码已实现，尚未完成一次当前环境的全量验收；第 2 步前端尚未开始。**
- 下一项工作：先关闭第 1 步验证缺口，然后按 `project-rebuild-order-1-8.md` 开始第 2 步 React 前端基础壳。
- 继续阅读顺序：本文件 -> `agent.md` -> `docs/project-rebuild-order-1-8.md` 第 2 步。

## 范围裁决

本仓库只重建以下 1-8 主线：

```text
简历上传和 AI 分析
  -> 文字模拟面试和评估
  -> 知识库向量化和 RAG 问答
  -> 带历史记录的流式 RAG 聊天
```

`docs/project-functional-chains.md` 是完整历史功能图，含语音面试与面试日程；它只用于理解依赖和通用模式。以 `agent.md` 和 `docs/project-rebuild-order-1-8.md` 为准，**不得实现语音、WebSocket 音频、ASR、TTS、面试日程及相关 Provider 配置**。

## 已读范围

| 范围 | 结论 |
| --- | --- |
| `docs/project-functional-chains.md` | 已完整阅读；定义了全功能链路、数据模型、接口与 Redis Stream 模式。 |
| `docs/project-rebuild-order-1-8.md` | 已完整阅读；是当前精简项目的唯一实施顺序和验收标准。 |
| `agent.md` | 已完整阅读；明确第 1 步已实现、模块边界和后续顺序。 |
| `app/src/main/java/**` | 已完整阅读；只有基础设施与健康检查，没有业务功能域实现。 |
| `app/src/test/java/**` | 已完整阅读；有健康接口 MVC 测试和数据库探针 Testcontainers 测试。 |
| `app/src/main/resources/**`、`pom.xml`、`README.md`、`run-local.ps1` | 已完整阅读；基础后端配置和本地运行说明齐备。 |
| `docker-compose.yml`、`docker/postgresql/init/01-enable-vector.sql`、`.env.example` | 已完整阅读；声明 PostgreSQL + pgvector、Redis、MinIO 和环境变量模板。 |

## 阶段状态

| 重建步骤 | 状态 | 代码证据 / 说明 |
| --- | --- | --- |
| 1. 基础后端 | 实现完成，待当前环境验收 | Spring Boot、PostgreSQL、Swagger、`Result<T>`、异常处理、CORS、审计字段、Trace ID、`GET /api/resumes/health` 已存在。 |
| 2. 基础前端 | 未开始 | 根目录不存在 `frontend/`；没有 React、Vite、路由或请求层。 |
| 3. 文件解析和对象存储 | 未开始 | Compose 已声明 MinIO，但没有 S3 SDK、Tika、文件校验/哈希/解析/存储适配器。 |
| 4. 简历模块 | 未开始 | 仅 `ResumeHealthController`；没有实体、Repository、上传、Redis Stream、列表/详情/导出。 |
| 5. LLM Provider | 未开始 | 没有 Spring AI、Provider 表、密钥加密、Prompt 或结构化输出代码。 |
| 6. 文字模拟面试 | 未开始 | 没有 Skill、会话、答案、缓存或评估模块。 |
| 7. 知识库与普通 RAG | 未开始 | 没有知识库实体、向量存储、Embedding 或检索代码。 |
| 8. 流式 RAG 聊天 | 未开始 | 没有聊天会话/消息、SSE 接口或前端流读取器。 |

## 第 1 步实现清单

| 验收项 | 状态 | 位置 |
| --- | --- | --- |
| Java 21 Spring Boot 后端 | 已实现 | `app/pom.xml`、`app/src/main/java/interview/guide/App.java` |
| Web、Validation、JPA、PostgreSQL、Actuator、Swagger、Testcontainers 依赖 | 已实现 | `app/pom.xml` |
| pgvector 初始化 | 已实现 | `docker/postgresql/init/01-enable-vector.sql` |
| PostgreSQL、Redis、MinIO Compose 定义 | 已实现 | `docker-compose.yml` |
| 环境变量隔离 | 已实现 | `.env.example`、`application-local.yml`、`application-production.yml` |
| 统一 JSON 返回 | 已实现 | `common/result/Result.java` |
| 业务、校验、数据库和未知异常处理 | 已实现 | `common/exception/*` |
| CORS、UTC/JPA 审计、Trace ID | 已实现 | `common/config/*`、`common/model/AuditableEntity.java` |
| 数据库探针与健康接口 | 已实现 | `infrastructure/persistence/*`、`modules/resume/ResumeHealthController.java` |
| Controller 测试与 Repository 集成测试 | 已编写 | `app/src/test/java/**` |

## 当前验证事实与缺口

| 检查 | 结果 | 处理建议 |
| --- | --- | --- |
| Java | 可用：21.0.12 | 满足项目要求。 |
| Docker CLI | 可用：29.6.2 | 可用于 Compose 和 Testcontainers。 |
| Maven | 不可用：`mvn` 不在 PATH，项目也没有 Maven Wrapper | 安装 Maven 3.9+ 并加入 PATH，或添加 Maven Wrapper 后再运行测试。 |
| 后端运行状态 | `localhost:8080` 当前不可连接 | 启动依赖和后端后访问健康接口与 Swagger。 |
| 历史启动记录 | `app/backend.log` 显示 2026-08-18 成功连接 PostgreSQL 并启动到 8080 | 仅历史证据，不能替代当前验收。 |
| 历史 MVC 测试 | 2026-08-18：2 tests, 0 failures, 0 errors | 重新运行确认。 |
| 历史 Testcontainers 测试 | 2026-08-18：1 test, 1 error，原因为当时找不到 Docker 环境 | Docker 现已可用；Maven 修复后应优先复跑。 |
| Git 状态 | 无法读取：根目录 `.git` 为空，缺少 `.git/HEAD`，`git status` 不是仓库 | 在有有效 Git 元数据的副本中初始化/恢复版本控制；本次未改动或还原任何现有文件。 |

## 继续操作

### 先完成第 1 步验收

```powershell
# 仓库根目录：准备本地环境变量（不要提交 .env）
Copy-Item .env.example .env

# 填入 .env 中的本地密码和密钥后启动基础设施
docker compose up -d

# 安装 Maven 或使用已配置的 Maven 后，在 app 目录执行
mvn test
./run-local.ps1
```

随后检查：

```text
GET http://localhost:8080/api/resumes/health
http://localhost:8080/swagger-ui.html
```

期望健康接口返回 HTTP 200 和：

```json
{"code":200,"message":"success","data":{"application":"UP","database":"UP"}}
```

在 Controller 无效参数场景中，确认 HTTP 400 且响应为统一 `Result` 结构。Maven 测试必须包含 Controller 测试和 Testcontainers 数据库探针测试均通过，才能把第 1 步标记为“验收完成”。

### 然后开始第 2 步前端

创建 `frontend/`，技术固定为 React 18 + TypeScript + Vite + React Router + Axios。先完成路由、布局、唯一的 `api/request.ts`、SSE 工具接口、加载/空/错误/Toast 状态，以及用 `/api/resumes/health` 验证 Vite 代理。只创建 1-8 范围内的页面和导航入口。

## 约束速查

- Controller 仅校验和转发；事务与业务决策放 Service；S3、Redis、AI SDK 放 infrastructure。
- 常规 JSON 一律返回 `Result<T>`；文件下载与 SSE 不包装 `Result`。
- 新持久化实体继承 `AuditableEntity`，时间使用 `Instant`，枚举用 `EnumType.STRING`。
- 上传顺序：校验 -> hash 去重 -> 抽取文本 -> 存储对象 -> 保存元数据 -> 投递异步任务；持久化失败要删除已上传对象。
- Redis Stream 消息只传 ID、任务 ID、重试次数、Trace ID；Consumer 必须幂等，持久化成功后再 ack，最多重试 3 次。
- 所有密钥仅来自环境变量；不写入日志、响应、数据库明文字段或 Git。

## 文档差异提醒

- `project-rebuild-order-1-8.md` 提到 Spring Boot 4.1；现有 `app/pom.xml` 使用 Spring Boot 3.3.5。此差异未阻塞第 1 步，但后续升级需单独评估 Spring AI 与依赖兼容性，不能在业务阶段中顺手升级。
- `project-functional-chains.md` 中的端点和目录含已排除的日程、语音内容。新增文件前始终先以本记录的“范围裁决”为准。
