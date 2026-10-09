# InterviewGuide 复建顺序 1-8

本文根据 `project-functional-chains.md` 整理，目标是从零复建一个可运行、可演示、可用于实习面试的 InterviewGuide。

项目只保留以下主线：

```text
简历上传与 AI 分析
  -> 文字模拟面试与评估
  -> 知识库向量化与 RAG 问答
  -> 带历史记录的流式 RAG 聊天
```

明确不做：面试日程、语音面试、WebSocket 音频传输、ASR、TTS、语音模型配置以及所有对应页面、接口和数据表。

建议每完成一步就创建一个可运行的 Git 提交。不要同时铺开多个模块；上一阶段的验收项全部通过后再进入下一阶段。

## 1. 搭建基础后端

**难度：低。目标：得到一个能连接 PostgreSQL、返回统一 JSON、可通过 Swagger 调试的 Spring Boot 服务。**

### 1.1 创建工程

创建 `app` 后端工程，采用 Java 21、Spring Boot、Maven 或 Gradle，并加入这些基础依赖：

- Spring Web：REST API。
- Spring Validation：请求参数校验。
- Spring Data JPA：业务表读写。
- PostgreSQL Driver：连接数据库。
- Spring Boot Actuator：健康检查。
- springdoc-openapi：Swagger 接口文档。
- Lombok：可选，用于减少样板代码。
- Testcontainers：建议加入测试依赖，后续做 PostgreSQL/Redis 集成测试。

先建立最小目录：

```text
app/src/main/java/interview/guide/
  App.java
  common/
    config/
    exception/
    result/
  modules/
  infrastructure/

app/src/main/resources/
  application.yml
  application-local.yml
```

### 1.2 启动本地依赖

准备 `docker-compose.yml`，先启动 PostgreSQL；Redis 和 MinIO 也可以同时声明，但这一阶段只要求 PostgreSQL 可用。为了后续 RAG，PostgreSQL 镜像直接使用 `pgvector/pgvector:pg16`，初始化脚本包含：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

配置全部通过环境变量注入，不把密码和 API Key 提交到 Git：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
S3_ENDPOINT
S3_ACCESS_KEY
S3_SECRET_KEY
S3_BUCKET
APP_AI_CONFIG_ENCRYPTION_KEY
```

本地端口约定：后端 `8080`、PostgreSQL `5432`、Redis `6379`、MinIO API `9000`、MinIO 控制台 `9001`。

### 1.3 建立统一响应和异常处理

创建 `Result<T>`，普通 JSON API 统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

至少实现：

- `Result.success(data)` 和 `Result.failure(code, message)`。
- `BusinessException`：可预期业务错误，例如资源不存在、文件重复。
- `GlobalExceptionHandler`：处理参数校验、业务异常和未知异常。
- 未知异常只向前端返回通用提示，详细堆栈写日志，不能泄露密钥或 SQL。
- 错误码按模块划分，例如基础错误、简历错误、面试错误、知识库错误。

文件下载和 SSE 是例外：文件下载直接返回字节流，SSE 直接返回事件流，不包装 `Result`。

### 1.4 完成基础配置

依次实现：

1. CORS 或开发代理配置，只允许实际使用的前端来源。
2. JPA 时间字段和枚举映射约定。
3. `createdAt`、`updatedAt` 基础实体或审计监听器。
4. 请求日志和统一 trace id，便于排查异步任务。
5. 分环境配置：`local` 使用本地容器，生产配置只读环境变量。

新增 `GET /api/resumes/health`，返回应用和数据库状态。此时先不创建完整简历模块，只用它验证请求链路。

### 1.5 测试与验收

- 启动 PostgreSQL 后，后端能在 `8080` 正常启动。
- `GET /api/resumes/health` 返回统一 `Result`。
- Swagger 可打开并调用健康接口。
- 传入非法参数时返回明确的 4xx 和统一错误结构。
- 数据库不可用时应用日志能说明原因，响应不暴露连接密码。
- 至少有一个 Controller 测试和一个 Repository 集成测试。

**本阶段面试讲点：** 统一响应、全局异常、配置隔离、分层目录和容器化开发环境为什么能降低后续模块的重复工作。

## 2. 搭建基础前端

**难度：低。目标：得到具有完整路由、统一请求层和基础交互状态的 React 应用。**

### 2.1 创建工程和目录

使用 React 18、TypeScript、Vite，加入 React Router 和 Axios。目录先按职责拆分：

```text
frontend/src/
  main.tsx
  App.tsx
  api/
    request.ts
    stream.ts
  components/
  hooks/
  layouts/
  pages/
  types/
  utils/
```

配置 Vite 将 `/api` 代理到 `http://localhost:8080`。本项目不做语音面试，因此不需要 `/ws` 代理。

### 2.2 建立页面骨架

只注册本项目需要的路由：

| 路由 | 页面 | 当前阶段内容 |
| --- | --- | --- |
| `/upload` | `UploadPage` | 简历上传占位页 |
| `/history` | `HistoryPage` | 简历列表占位页 |
| `/history/:resumeId` | `ResumeDetailPage` | 简历详情占位页 |
| `/interview-hub` | `InterviewHubPage` | 仅文字面试入口 |
| `/interview`、`/interview/:resumeId` | `InterviewPage` | 答题占位页 |
| `/interviews` | `InterviewHistoryPage` | 面试历史占位页 |
| `/interviews/:sessionId` | `InterviewDetailPage` | 面试报告占位页 |
| `/knowledgebase` | `KnowledgeBaseManagePage` | 知识库管理占位页 |
| `/knowledgebase/upload` | `KnowledgeBaseUploadPage` | 知识库上传占位页 |
| `/knowledgebase/chat` | `KnowledgeBaseQueryPage` | RAG 聊天占位页 |
| `/settings` | `SettingsPage` | 只配置聊天和 Embedding 模型 |

导航中不要出现面试日程和语音面试入口，也不要创建对应 API 文件和组件。

### 2.3 封装请求层

在 `api/request.ts` 中创建唯一 Axios 实例：

- 设置 `baseURL=/api` 和合理超时。
- 请求拦截器添加 trace id；后续如有登录再添加 token。
- 响应拦截器在成功时返回 `Result.data`。
- 失败时优先抛出后端 `message`，网络错误显示统一提示。
- TypeScript 方法返回业务数据类型，不让页面感知 AxiosResponse。

在 `api/stream.ts` 中先定义 SSE 读取工具的接口，实际流解析到第 8 步完成。SSE 使用 `fetch + ReadableStream`，不能走普通 Axios 响应拦截器。

### 2.4 完成基础体验

建立可复用的加载、空数据、错误、确认删除和 Toast 组件。每个异步页面都必须覆盖四种状态：首次加载、成功、空数据、失败。表单提交期间禁用重复提交。

调用第 1 步的健康接口，在页面上显示后端是否连通。补一个 404 页面，并确保刷新动态路由时开发服务器能回退到 `index.html`。

### 2.5 测试与验收

- 前端在 `5173` 启动，能通过代理调用后端健康接口。
- 所有保留路由都能访问，导航高亮正确，动态路由刷新不白屏。
- 后端业务错误能显示 `message`，网络错误不会使页面崩溃。
- 页面不存在日程和语音功能的入口、路由或空壳菜单。
- 至少为 `request.ts` 的成功解包和错误处理各写一个测试。

**本阶段面试讲点：** 前端请求层如何统一后端协议，为什么把页面状态、API 类型和网络实现分开。

## 3. 接入对象存储和文件解析

**难度：中低。目标：建立一套简历和知识库都能复用的安全文件处理基础设施。**

### 3.1 接入依赖

后端加入 AWS S3 SDK 和 Apache Tika。本地使用 MinIO，后端只依赖 S3 协议，使本地和云端对象存储可以替换。

在 `infrastructure/file` 中建立：

```text
FileValidationService
FileHashService
DocumentParseService
FileStorageService
S3FileStorageService
StorageProperties
```

简历和知识库业务层只调用这些接口，不直接使用 S3Client 或 Tika。

### 3.2 实现文件校验

上传处理顺序必须固定：

1. 检查文件是否为空。
2. 清理原始文件名，拒绝路径穿越字符。
3. 检查大小：简历最大 10 MB，知识库最大 50 MB。
4. 同时检查扩展名、浏览器声明的 MIME 和 Tika 探测类型。
5. 只开放确实支持的格式，第一版建议 TXT、PDF、DOCX。
6. 计算 SHA-256，供业务模块去重。
7. 用 Tika 抽取纯文本，限制最大文本长度，拒绝完全没有文本的文件。

不要只相信文件扩展名，也不要把用户原始文件名直接作为对象存储 key。

### 3.3 实现对象存储

启动时检查 bucket，不存在则创建。对象 key 使用服务端生成的安全路径：

```text
resumes/{yyyy}/{MM}/{uuid}.{ext}
knowledge-bases/{yyyy}/{MM}/{uuid}.{ext}
```

`FileStorageService` 至少提供：上传、下载、删除、判断存在。数据库后续只保存 `storageKey`；`storageUrl` 若会过期，不应作为唯一定位信息。

错误处理要区分校验失败、解析失败和存储失败。如果数据库尚未提交而存储已成功，需要补偿删除；如果删除对象失败，需要记录日志并允许后续清理。

### 3.4 先做基础设施测试

使用小型 TXT、PDF、DOCX，以及空文件、伪造扩展名、超限文件作为测试样本：

- 单元测试覆盖文件名清理、大小限制、SHA-256 稳定性。
- 集成测试覆盖上传、下载内容一致、删除。
- 解析测试验证三种支持格式能得到非空文本。
- 两个内容相同但文件名不同的文件应得到相同 hash。

### 3.5 验收

- MinIO 中能看到按安全 key 保存的文件。
- TXT/PDF/DOCX 能抽取文本，非法类型和空文本有清晰错误。
- 上传后下载的字节与原文件一致，删除后对象不存在。
- S3 和 Tika 的实现没有泄漏到简历或知识库 Controller。

**本阶段面试讲点：** 文件校验不能只看后缀；用接口隔离 S3 实现；hash 去重、对象 key 和失败补偿如何保证文件链路可靠。

## 4. 完成简历模块

**难度：中。目标：打通第一个完整业务闭环，并先建立可复用的异步任务模式。**

### 4.1 建立数据模型

创建两张表：

`resumes` 保存：

- `id`、原始文件名、content type、文件大小。
- `fileHash`，建立唯一索引或业务唯一校验。
- `storageKey`、解析后的 `resumeText`。
- `analyzeStatus`：`PENDING`、`PROCESSING`、`COMPLETED`、`FAILED`。
- `analyzeError`、创建时间、更新时间。

`resume_analyses` 保存：

- `id`、`resumeId`、分析版本和创建时间。
- `overallScore` 以及各维度分数。
- `summary`、`strengthsJson`、`suggestionsJson`。
- 模型和 Provider 信息，方便解释不同分析结果。

一次重新分析新建一条分析记录，不覆盖旧报告。列表和详情默认取最新一条。

### 4.2 实现同步上传主链路

按以下顺序实现 `POST /api/resumes/upload`：

```text
UploadPage
  -> resumeApi.uploadAndAnalyze(file)
  -> ResumeController
  -> ResumeUploadService
     1. 校验文件
     2. 计算 hash 并检查重复
     3. Tika 解析文本
     4. 上传 MinIO
     5. 保存 resumes，状态 PENDING
     6. 发布分析任务
  -> 返回 resumeId、duplicate、analyzeStatus
```

重复上传默认返回已有 `resumeId`，不要重复保存文件和分析任务。数据库保存失败时删除刚上传的对象。

### 4.3 建立 Redis Stream 异步骨架

引入 Redis 和 Redisson，先围绕简历分析实现最小版本：

- Stream：`resume:analyze:stream`。
- Consumer group：`analyze-group`。
- 消息只传 `resumeId`、task id、重试次数，不传整份简历文本。
- Producer 在简历记录成功保存后入队。
- Consumer 将状态从 `PENDING` 改为 `PROCESSING`，完成后改为 `COMPLETED`。
- 最多重试 3 次；最终失败写 `FAILED` 和经过清理的 `analyzeError`。
- Consumer 重复收到同一个 task 时应幂等，已完成任务不重复写报告。
- 正确 `ack`，并处理长时间未完成的 pending 消息。

第 5 步接入真实 AI 前，可让分析服务返回固定的开发测试报告，先验证状态机、重试和入库链路。链路稳定后再抽象 `AbstractStreamProducer<T>` 和 `AbstractStreamConsumer<T>`，供文字面试评估和知识库向量化复用。

### 4.4 完成简历 API

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/resumes/upload` | 上传、解析、入库、发布分析任务 |
| `GET` | `/api/resumes` | 简历列表和最新分析状态 |
| `GET` | `/api/resumes/{id}/detail` | 简历、历次分析和关联面试 |
| `POST` | `/api/resumes/{id}/reanalyze` | 重新入队分析 |
| `DELETE` | `/api/resumes/{id}` | 删除记录和原文件 |
| `GET` | `/api/resumes/{id}/export` | 导出最新分析 PDF |

如果 `resumeText` 丢失，重新分析时从 MinIO 下载原文件并重新解析。删除操作要明确顺序并记录对象删除失败，不能留下用户看得见的半删除状态。

`/api/resumes/statistics` 只有在前端确实展示统计信息时才实现；否则从前端 API 中删除，避免保留无后端实现的调用。

### 4.5 完成前端页面

`UploadPage`：拖放或选择文件、格式和大小提示、上传进度、成功后跳转详情。

`HistoryPage`：展示文件名、上传时间和分析状态；对 `PENDING/PROCESSING` 进行有限轮询；支持查看、重新分析和确认删除。

`ResumeDetailPage`：展示解析信息、最新报告、历史分析、错误和重试操作；下载 PDF 时按 Blob 处理，不经过 JSON 解包。

轮询必须在完成、失败、离开页面或达到超时时停止，避免永久请求。

### 4.6 测试与验收

- 首次上传产生一条简历、一份对象文件和一个 Stream 任务。
- 重复上传返回已有记录，不产生重复对象或重复任务。
- Consumer 成功、失败、重试和重复消费时状态都正确。
- 列表、详情、重新分析、删除、PDF 下载形成完整闭环。
- PDF 至少包含文件名、总分、总结、优势和改进建议，中文字体显示正常。
- 至少有上传 Service 集成测试和 Consumer 幂等测试。

**本阶段面试讲点：** 为什么耗时分析异步化；Redis Stream 的确认、重试和幂等；文件、数据库和队列之间如何处理一致性。

## 5. 接入 LLM Provider 和真实简历分析

**难度：中高。目标：统一管理聊天与 Embedding 模型，并把第 4 步的测试报告替换成真实结构化 AI 输出。**

### 5.1 只保留需要的模型类型

本项目只需要：

- `PlainChatClient`：结构化 JSON 输出，用于简历分析、出题和评估。
- `ChatClient`：普通或流式对话，用于 RAG。
- `EmbeddingModel`：文档向量化和相似度检索。

不要实现 `VoiceChatClient`、ASR、TTS 或任何语音配置项。

### 5.2 建立 Provider 配置

创建：

- `llm_provider_config`：名称、base URL、chat model、embedding model、加密后的 API Key、启用状态。
- `llm_global_setting`：默认聊天 Provider 和默认 Embedding Provider。
- `LlmProviderConfigService`：配置 CRUD、默认值和合法性校验。
- `ApiKeyEncryptionService`：使用 `APP_AI_CONFIG_ENCRYPTION_KEY` 加解密，接口响应永远不返回明文 key。
- `LlmProviderRegistry`：按 Provider 创建并缓存客户端，配置变化后 reload。

启动配置可以来自 `application.yml`，运行时同步到数据库。统一使用 OpenAI 兼容协议，但 base URL、模型名和 embedding 维度必须可配置。

### 5.3 提供必要接口和设置页

实现 Provider 列表、新建、修改、删除、连接测试、重载、设置默认聊天模型和默认 Embedding 模型。设置页只显示这两类模型配置。

连接测试设置短超时和明确结果；日志不得打印 Authorization 头、完整 API Key 或原始简历。删除 Provider 前检查它是否仍是默认项或正被业务记录引用。

### 5.4 实现结构化输出工具

创建 `StructuredOutputInvoker`：

1. 加载 system/user prompt 模板。
2. 使用 `BeanOutputConverter` 生成格式要求并解析结果。
3. 校验 DTO 字段、分数范围和列表长度。
4. 对暂时性网络错误和可修复格式错误做有限重试。
5. 重试仍失败时抛出可记录的业务异常。

所有 Prompt 放在 `app/src/main/resources/prompts/*.st`，通过变量传入文本，不写死在 Java 代码中。结构化输出必须使用无工具调用的 `PlainChatClient`，避免工具消息破坏 JSON。

### 5.5 激活真实简历分析

让 `AnalyzeStreamConsumer` 调用 `ResumeGradingService`：

```text
读取 resumeText
  -> 加载 resume-analysis-system.st
  -> 加载 resume-analysis-user.st
  -> LlmProviderRegistry.getPlainChatClient(...)
  -> StructuredOutputInvoker
  -> 校验并保存 resume_analyses
  -> 更新 analyzeStatus
```

为长简历设置输入长度上限或裁剪策略。保存模型名、Provider 和 prompt 版本，使报告可追溯。AI 不可用时记录失败并允许用户重新分析，不能生成一份看似成功的空报告。

### 5.6 测试与验收

- 设置页能新增 Provider、测试连接并设置默认聊天/Embedding 模型。
- 数据库中的 API Key 不是明文，接口和日志也不泄露 key。
- 同一份固定简历能被解析为符合 DTO 的分析结果，分数范围合法。
- AI 返回 Markdown 包裹 JSON、缺字段或超时时有可预期处理。
- 重载 Provider 后新请求使用新配置，正在执行的任务不会被破坏。
- 无真实 API Key 时可使用 mock server 完成自动化测试。

**本阶段面试讲点：** 多 Provider 抽象、密钥保护、结构化输出校验、prompt 外置，以及 AI 输出不稳定时如何重试和降级。

## 6. 完成文字模拟面试

**难度：高。目标：实现从选方向、AI 出题、逐题作答到异步评估报告的完整文字面试。**

### 6.1 建立 Skill 资源

在 `resources/skills/{skillId}/` 中保存 `SKILL.md` 和 `skill.meta.yml`。第一版准备 3 个清晰方向即可，例如 Java 后端、前端、通用计算机基础。

实现：

- `GET /api/interview/skills`：返回 Skill 摘要。
- `GET /api/interview/skills/{id}`：返回详情。
- `POST /api/interview/skills/parse-jd`：可选，根据 JD 提取考察方向。

Skill 加载失败要跳过单个坏配置并记录原因，不能导致整个应用启动失败。JD 解析可以后补，不应阻塞基础面试闭环。

### 6.2 建立会话和答案模型

`interview_sessions` 保存：业务 `sessionId`、可选 `resumeId`、skill、难度、问题 JSON、当前题号、会话状态、评估状态、总分和总结。

`interview_answers` 保存：session、question index、题目、用户答案、单题分数、反馈、参考答案。对 `(session_id, question_index)` 建唯一约束，自动保存和正式提交都使用 upsert。

状态至少包括：

```text
会话：IN_PROGRESS -> COMPLETED
评估：NOT_STARTED -> PENDING -> PROCESSING -> COMPLETED / FAILED
```

### 6.3 生成题目

创建会话的顺序：

```text
POST /api/interview/sessions
  -> 检查同一简历是否有未完成会话
  -> 生成 sessionId
  -> 查询历史问题，减少重复
  -> 解析 Skill、难度、JD 和简历文本
  -> PlainChatClient 生成结构化问题
  -> 保存 DB
  -> 写 Redis 会话缓存
```

有简历时可按约 60% 简历项目题、40% Skill 基础题组合；无简历时只按 Skill 出题。每道题至少包含 `questionIndex`、题目、类别、难度。AI 失败时使用 Skill 内置 fallback questions，保证用户仍能进入面试。

### 6.4 实现 Redis 会话缓存

缓存 `sessionId`、`resumeId`、问题列表、当前题号和状态。读取时先查 Redis，未命中时从 `interview_sessions + interview_answers` 恢复并重新写缓存。

数据库是最终事实来源，不能只把答案放在 Redis。更新题号、答案和状态时要保证数据库与缓存顺序明确；数据库成功后缓存失败，可以删除缓存并在下次请求时重建。

### 6.5 完成答题接口和页面

主要接口：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/interview/sessions` | 创建文字面试 |
| `GET` | `/api/interview/sessions/{sessionId}` | 会话详情 |
| `GET` | `/api/interview/sessions/{sessionId}/question` | 获取当前题 |
| `PUT` | `/api/interview/sessions/{sessionId}/answers` | 自动暂存答案 |
| `POST` | `/api/interview/sessions/{sessionId}/answers` | 提交并进入下一题 |
| `POST` | `/api/interview/sessions/{sessionId}/complete` | 提前交卷 |
| `GET` | `/api/interview/sessions` | 面试历史 |
| `GET` | `/api/interview/sessions/{sessionId}/details` | 报告详情 |
| `DELETE` | `/api/interview/sessions/{sessionId}` | 删除会话 |
| `GET` | `/api/interview/sessions/{sessionId}/export` | 导出 PDF |

`InterviewHubPage` 只展示文字面试，支持选择 Skill、难度、简历和粘贴 JD。`InterviewPage` 展示稳定的题号、进度、文本答案和上一题/下一题；输入停止一小段时间后自动保存，并显示保存状态。

后端必须校验 question index，不能相信前端提交的题目内容。重复点击下一题不得产生两条答案或跨过两题。

### 6.6 异步评估和报告

最后一题提交或提前交卷后：

```text
会话改为 COMPLETED
  -> evaluateStatus=PENDING
  -> 写 interview:evaluate:stream
  -> EvaluateStreamConsumer 合并题目和答案
  -> 分批评估每题
  -> 汇总 overallFeedback、strengths、improvements
  -> 写回 session 和 answers
  -> evaluateStatus=COMPLETED
```

复用第 4 步的异步消费模板。评估任务必须幂等；无答案的题目标记未作答，不让 LLM 虚构答案。长面试分批评估，最后再做总评，避免超过上下文限制。

`InterviewHistoryPage` 展示会话和评估状态，`InterviewDetailPage` 展示总分、逐题反馈、优势和改进建议。评估中采用有限轮询，失败时提供重新评估。PDF 导出直接返回字节流。

### 6.7 测试与验收

- 无简历和有简历两种方式都能创建会话。
- AI 出题失败时 fallback questions 可用。
- 暂存、刷新恢复、继续答题、重复提交和提前交卷都正确。
- Redis 清空后仍能从数据库恢复当前进度。
- 完成后只触发一次评估，报告包含总评和逐题反馈。
- 简历详情能跳转到文字面试，报告能关联回简历。
- 至少覆盖会话状态机、答案唯一约束和评估幂等测试。

**本阶段面试讲点：** Redis 缓存与数据库事实源的关系、状态机、自动保存、AI 出题降级，以及异步评估的幂等设计。

## 7. 完成知识库和普通 RAG 问答

**难度：高。目标：实现文档上传、切块、Embedding、向量检索和一次性 RAG 回答。**

### 7.1 建立数据和向量存储

创建 `knowledge_bases`，保存名称、分类、原文件信息、hash、storage key、解析文本、chunk 数量、`vectorStatus` 和 `vectorError`。

`vector_store` 由 Spring AI PgVectorStore 管理，不要为它创建普通 JPA Entity。配置：

- `spring.ai.vectorstore.pgvector.initialize-schema=true`。
- Embedding 维度与实际模型完全一致，例如 `1024`。
- 每个 Document metadata 至少包含 `kb_id`、chunk index、source name。

### 7.2 实现上传和异步向量化

`POST /api/knowledgebase/upload` 的同步部分复用第 3 步：校验、hash 去重、Tika 解析、上传 MinIO、保存 `PENDING` 记录，然后写 `knowledgebase:vectorize:stream`。

Consumer 执行：

1. 将 `vectorStatus` 改为 `PROCESSING`。
2. 使用 `TokenTextSplitter` 按 token 切块，保留适量重叠。
3. 过滤空块和极短块，记录 chunk 顺序。
4. 每批最多约 10 个 chunk 调用 Embedding，避免单次请求过大。
5. 将向量和 metadata 写入 `vector_store`。
6. 更新 `chunkCount` 并将状态改为 `COMPLETED`。
7. 失败时重试，最终写 `FAILED` 和错误摘要。

重新向量化采用 `pending -> promote`：新向量先使用临时 job id，全部成功后再删除旧向量并把新 metadata 提升为正式 `kb_id`。不能在新任务成功前删除旧向量。

### 7.3 完成知识库管理

前端实现上传、列表、详情、分类、搜索、状态轮询、下载、删除和重新向量化。后端至少提供：

```text
POST   /api/knowledgebase/upload
GET    /api/knowledgebase/list
GET    /api/knowledgebase/{id}
DELETE /api/knowledgebase/{id}
GET    /api/knowledgebase/{id}/download
GET    /api/knowledgebase/categories
PUT    /api/knowledgebase/{id}/category
GET    /api/knowledgebase/search?keyword=
GET    /api/knowledgebase/stats
POST   /api/knowledgebase/{id}/revectorize
```

删除时同时处理元数据、对象文件和该知识库向量。只有 `COMPLETED` 的知识库可以参与问答。

### 7.4 实现普通 RAG 问答

先做非流式 `POST /api/knowledgebase/query`：

```text
校验 question 和 knowledgeBaseIds
  -> 可选地改写问题
  -> 根据问题长度确定 topK 和 minScore
  -> similaritySearch，并按 metadata.kb_id 过滤
  -> 丢弃低分结果和重复 chunk
  -> 拼接带来源标记的 context
  -> ChatClient 调用 knowledgebase-query prompt
  -> 返回答案、命中来源和相似度
```

Prompt 必须要求模型只依据 context 回答；没有足够证据时明确说“不知道”，同时防止把文档中的指令当成系统指令。限制 context 总 token 数，避免将所有命中文本无边界地塞给模型。

先用固定 Embedding 或 mock 验证过滤逻辑，再接真实模型。准备一组已知问题，记录 topK 命中率、回答是否引用正确来源，避免只凭肉眼判断 RAG 效果。

### 7.5 测试与验收

- 上传文档后状态按 `PENDING -> PROCESSING -> COMPLETED/FAILED` 变化。
- `vector_store` 中 chunk 数与 `knowledge_bases.chunkCount` 一致，metadata 可按 `kb_id` 过滤。
- 同一个问题选择不同知识库时，只使用被选择知识库的内容。
- 无命中时不编造答案，并向用户显示未找到依据。
- 重新向量化失败后旧向量仍可查询，成功后才完成替换。
- 删除知识库后原文件、业务记录和向量都不可再查询。
- 至少覆盖 metadata 过滤、无命中、重向量化和删除清理测试。

**本阶段面试讲点：** 文档切块与重叠、Embedding 维度、metadata 过滤、检索参数、RAG 幻觉控制，以及 `pending -> promote` 如何保护线上可用数据。

## 8. 完成流式 RAG 聊天并封板项目

**难度：最高。目标：在第 7 步之上增加会话、历史上下文、SSE 流式输出，并整理成可面试展示的完整项目。**

### 8.1 建立聊天数据模型

创建：

- `rag_chat_sessions`：业务 session id、标题、置顶状态、消息数量、创建和更新时间。
- `rag_chat_messages`：session、role、content、顺序、`completed`、错误信息、创建时间。
- `rag_session_knowledge_bases`：会话与多个知识库的关联表。

对 session 和 message sequence 建必要索引。会话创建时校验所有知识库都存在且已完成向量化。

### 8.2 完成会话管理

实现：

```text
POST   /api/rag-chat/sessions
GET    /api/rag-chat/sessions
GET    /api/rag-chat/sessions/{sessionId}
PUT    /api/rag-chat/sessions/{sessionId}/title
PUT    /api/rag-chat/sessions/{sessionId}/pin
PUT    /api/rag-chat/sessions/{sessionId}/knowledge-bases
DELETE /api/rag-chat/sessions/{sessionId}
```

列表按置顶和最近更新时间排序。详情一次返回会话、绑定知识库和消息。修改知识库只影响后续提问，不修改已经保存的历史回答。

### 8.3 实现 SSE 流式回答

接口：

```text
POST /api/rag-chat/sessions/{sessionId}/messages/stream
Content-Type: text/event-stream
```

后端顺序：

```text
事务 1：保存 USER 消息
       创建 completed=false 的 ASSISTANT 空消息
       更新 session.messageCount

读取会话绑定的 knowledgeBaseIds
读取最近若干轮已完成历史消息
调用第 7 步的检索链路
ChatClient 流式生成
逐个 SSE event 返回 chunk

流完成：保存完整文本，completed=true
流失败：保存已生成文本和错误摘要，completed=true
```

SSE 事件建议明确类型：

```text
event: metadata   会话、消息和引用来源
event: delta      新增文本
event: done       完成
event: error      可展示错误
```

客户端断开时取消上游订阅，仍保存已经生成的内容。不要在整个模型流期间保持数据库事务。历史上下文只取最近若干轮，并按 token 预算截断。

### 8.4 完成聊天页面

`KnowledgeBaseQueryPage` 包含会话列表、知识库多选、消息区和输入区。实现：

- 新建、切换、重命名、置顶和删除会话。
- 发送期间增量追加 assistant 文本并禁止重复发送。
- 显示引用来源，支持回到对应知识库。
- 页面刷新后从数据库恢复历史消息。
- 用户中止、网络断开和后端错误都有明确状态。
- `stream.ts` 使用 `fetch` 读取 `ReadableStream`，正确处理跨 chunk 的 SSE 行和 UTF-8 字符。

不要用 Axios 的普通 JSON 拦截器解析 SSE，也不要让加载文字改变消息区宽高导致明显跳动。

### 8.5 做端到端验证

至少完成四条自动化或可重复演示链路：

1. 上传简历 -> 异步分析 -> 查看详情 -> 导出 PDF。
2. 选择简历和 Skill -> 完成文字面试 -> 异步评估 -> 查看报告。
3. 上传知识库 -> 异步向量化 -> 提问 -> 查看来源。
4. 创建 RAG 会话 -> 流式回答 -> 刷新页面 -> 历史仍存在。

额外检查：重复提交、AI 超时、Redis 短暂不可用、空检索结果、浏览器中止 SSE、API Key 不泄露。为核心 Service 写单元测试，为 PostgreSQL/Redis/MinIO 链路写集成测试，为上面四条主流程写最少量端到端测试。

### 8.6 整理成实习面试作品

在这一步一起完成项目封板：

- 提供 Docker Compose，一条命令启动 PostgreSQL、Redis、MinIO、后端和前端。
- README 写清项目价值、架构图、启动方式、环境变量、核心功能和取舍。
- 提供 `.env.example`，不提交任何真实密钥、真实简历或个人信息。
- 提供一份脱敏示例简历和一份小型知识库，面试时可以稳定演示。
- 准备数据库关系图和主链路图：前端 -> REST/SSE -> Service -> PostgreSQL/Redis/S3/AI。
- 记录关键指标：文件大小限制、Stream 重试次数、切块参数、topK、SSE 首字延迟。
- 保留清晰提交历史，每个提交对应一个可解释的功能里程碑。

项目演示顺序控制在 5-8 分钟：先展示简历分析，再完成两道文字题并查看已有报告，最后展示知识库流式问答及引用来源。演示重点不是页面数量，而是完整链路、失败处理和技术取舍。

### 8.7 最终验收

- 全新环境按 README 可以启动，Swagger 和前端均可访问。
- 项目中不存在日程或语音页面、接口、表、模块、菜单和配置项。
- 四条主流程可以连续演示，错误状态不会让数据永久卡在处理中。
- 重复请求和重复消费不会制造重复业务数据。
- 所有密钥由环境变量提供，日志、数据库查询接口和 Git 历史不包含明文密钥。
- README 能回答“为什么异步”“为什么需要 Redis”“如何减少 RAG 幻觉”“缓存丢失怎么办”“如何保证任务幂等”这五个问题。

**本阶段面试讲点：** SSE 的完整生命周期、历史上下文和 token 控制、流式消息持久化、端到端故障处理，以及如何把工程取舍讲成一个可信的项目故事。
