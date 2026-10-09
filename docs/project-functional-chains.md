# InterviewGuide 功能链路复建文档

这份文档面向计算机基础较弱、希望照着项目重新搭建的人。它不只列功能，还把每个功能从“前端页面点击”拆到“后端接口、Service、数据库、Redis、对象存储、外部 AI 调用”。

建议学习和复建时按本文顺序来，不要一开始就同时做所有功能。

## 1. 项目是什么

InterviewGuide 是一个智能 AI 面试辅助平台，核心能力包括：

- 简历上传、解析、AI 分析、PDF 导出。
- 文字模拟面试：根据 Skill、简历、JD 生成问题，保存答案，异步生成评估报告。
- 语音模拟面试：WebSocket 实时传音频，ASR 识别，LLM 追问，TTS 回传语音，结束后异步评估。
- 知识库管理：上传文档，解析、切块、向量化，支持 RAG 问答。
- RAG 聊天：一个会话绑定多个知识库，支持流式回答和历史上下文。
- 面试日程：从邀请文本中解析公司、岗位、时间、会议链接，保存日程。
- 多模型配置：管理 OpenAI 兼容 Provider、默认聊天模型、默认 Embedding 模型、ASR/TTS 配置。

## 2. 技术栈和运行组件

| 层级 | 技术 | 作用 |
| --- | --- | --- |
| 前端 | React 18 + TypeScript + Vite | 页面、路由、表单、音频采集、SSE/WebSocket 客户端 |
| 后端 | Java 21 + Spring Boot 4.1 | REST API、WebSocket、业务逻辑、异步消费者 |
| ORM | Spring Data JPA | 业务表自动建表和读写 |
| 数据库 | PostgreSQL + pgvector | 业务数据 + 向量数据 |
| 缓存/队列 | Redis + Redisson | 文字面试会话缓存、限流、Redis Stream 异步任务 |
| 对象存储 | MinIO/RustFS，S3 协议 | 保存上传的简历和知识库原文件 |
| AI | Spring AI + OpenAI 兼容 Provider | 聊天、结构化输出、Embedding |
| 语音 | DashScope Qwen ASR/TTS | 实时语音识别和语音合成 |

本地开发默认端口：

| 服务 | 地址 |
| --- | --- |
| 前端 Vite | `http://localhost:5173` |
| 后端 API | `http://localhost:8080` |
| Swagger | `http://localhost:8080/swagger-ui.html` |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| MinIO/RustFS API | `localhost:9000` |
| MinIO/RustFS 控制台 | `localhost:9001` |

## 3. 推荐复建顺序

如果你要从零搭建，不建议直接复制所有模块。推荐按依赖从小到大：

1. 搭基础后端：Spring Boot、统一返回 `Result<T>`、全局异常、CORS、JPA、PostgreSQL。
2. 搭基础前端：Vite、React Router、Axios 封装、布局、页面路由。
3. 接入对象存储和文件解析：S3 SDK、Apache Tika、文件校验、文件哈希。
4. 做简历模块：上传、去重、入库、异步分析、列表、详情、删除、PDF 导出。
5. 做 LLM Provider：读取配置、创建 ChatClient/EmbeddingModel、设置默认模型。
6. 做文字面试：Skill 加载、出题、会话缓存、保存答案、异步评估。
7. 做知识库：上传、异步向量化、向量检索、普通 RAG 问答。
8. 做 RAG 聊天：会话表、消息表、SSE 流式输出、历史上下文。
9. 做面试日程：规则解析 + AI 兜底解析、日历 CRUD。
10. 做语音面试：REST 建会话、WebSocket 协议、ASR/LLM/TTS 管线、异步评估。
11. 最后补限流、PDF 样式、监控、Docker 一键部署。

## 4. 总体请求链路

### 前端到后端

```text
React 页面
  -> frontend/src/api/*.ts
  -> request.ts 统一 Axios 封装
  -> /api/... REST 接口 或 /ws/... WebSocket
  -> Controller
  -> Service
  -> Repository / Redis / S3 / AI
  -> Result<T> 返回
```

前端 `request.ts` 约定后端大多数 JSON 接口返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

前端拦截器会把成功响应自动变成 `data`，失败响应抛出 `message`。

例外：

- 文件下载接口直接返回 `ResponseEntity<byte[]>`。
- SSE 接口用 `fetch` 读取流，不走 Axios 拦截器。
- WebSocket 接口走浏览器原生 `WebSocket`。

### 后端分层

```text
modules/
  resume/             简历
  interview/          文字模拟面试
  voiceinterview/     语音模拟面试
  knowledgebase/      知识库 + RAG 聊天
  interviewschedule/  面试日程
  llmprovider/        多模型配置

common/
  ai/                 LLM Provider、结构化输出、Prompt 安全
  async/              Redis Stream 生产者/消费者模板
  evaluation/         文字和语音共用评估引擎
  aspect/             限流切面
  exception/          业务异常和全局异常
  result/             统一返回结构

infrastructure/
  file/               文件校验、哈希、解析、S3 存储
  redis/              Redis 服务、文字面试会话缓存
  mapper/             MapStruct 映射
  export/             PDF 导出
```

## 5. 前端页面和功能入口

路由定义在 `frontend/src/App.tsx`。

| 页面路径 | 页面组件 | 主要功能 | 调用 API |
| --- | --- | --- | --- |
| `/history` | `HistoryPage` | 简历库列表、状态轮询、删除、进入详情 | `historyApi` |
| `/upload` | `UploadPage` | 上传简历 | `resumeApi.uploadAndAnalyze` |
| `/history/:resumeId` | `ResumeDetailPage` | 简历详情、分析结果、导出、开始面试 | `historyApi` |
| `/interview-hub` | `InterviewHubPage` | 文字/语音面试入口 | `skillApi`、`interviewApi`、`voiceInterviewApi` |
| `/interview` 和 `/interview/:resumeId` | `InterviewPage` | 文字模拟面试答题 | `interviewApi` |
| `/interviews` | `InterviewHistoryPage` | 文字面试历史 | `interviewApi.listSessions`、`historyApi` |
| `/interviews/:sessionId` | `InterviewDetailPanel` | 文字面试报告详情 | `historyApi.getInterviewDetail` |
| `/voice-interview` | `VoiceInterviewPage` | 语音面试 | `voiceInterviewApi`、`VoiceInterviewWebSocket` |
| `/voice-interview/:sessionId/evaluation` | `VoiceInterviewEvaluationPage` | 语音面试评估报告 | `voiceInterviewApi.getEvaluation` |
| `/knowledgebase` | `KnowledgeBaseManagePage` | 知识库管理 | `knowledgeBaseApi` |
| `/knowledgebase/upload` | `KnowledgeBaseUploadPage` | 上传知识库文档 | `knowledgeBaseApi.uploadKnowledgeBase` |
| `/knowledgebase/chat` | `KnowledgeBaseQueryPage` | RAG 聊天 | `ragChatApi` |
| `/interview-schedule` | `InterviewSchedulePage` | 面试日程日历/列表 | `interviewScheduleApi` |
| `/settings` | `SettingsPage` | Provider、默认模型、语音配置 | `llmProviderApi` |

注意：`frontend/src/api/history.ts` 中有 `getStatistics()` 调用 `/api/resumes/statistics`，但当前后端 `ResumeController` 没有该接口。复建时要么补接口，要么删除前端未使用调用。

## 6. 数据库模型

JPA 默认配置 `spring.jpa.hibernate.ddl-auto=update`，开发环境会自动建表。PostgreSQL 首次启动会执行 `docker/postgres/init.sql`：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 简历相关

| 表 | 实体 | 作用 |
| --- | --- | --- |
| `resumes` | `ResumeEntity` | 简历原文件元信息、文本、哈希、对象存储地址、分析状态 |
| `resume_analyses` | `ResumeAnalysisEntity` | 简历 AI 分析结果，多次分析可保留多条记录 |

关键字段：

- `resumes.fileHash`：SHA-256，用于重复上传检测。
- `resumes.storageKey/storageUrl`：对象存储中的文件位置。
- `resumes.resumeText`：Apache Tika 解析后的纯文本。
- `resumes.analyzeStatus/analyzeError`：异步分析状态。
- `resume_analyses.overallScore/*Score/summary/strengthsJson/suggestionsJson`：分析报告。

### 文字面试相关

| 表 | 实体 | 作用 |
| --- | --- | --- |
| `interview_sessions` | `InterviewSessionEntity` | 一次文字面试会话、问题 JSON、报告总分和评语 |
| `interview_answers` | `InterviewAnswerEntity` | 每道题的用户答案、评分、反馈、参考答案 |

关键字段：

- `interview_sessions.sessionId`：业务会话 ID，前端使用。
- `questionsJson`：生成的问题列表 JSON。
- `currentQuestionIndex`：当前答到第几题。
- `evaluateStatus/evaluateError`：异步评估状态。
- `interview_answers` 对 `(session_id, question_index)` 有唯一约束，避免同一题重复插入。

### 知识库和 RAG 聊天

| 表 | 实体 | 作用 |
| --- | --- | --- |
| `knowledge_bases` | `KnowledgeBaseEntity` | 知识库文件元信息、分类、向量化状态 |
| `vector_store` | Spring AI pgvector | 文档 chunk 的向量数据和 metadata |
| `rag_chat_sessions` | `RagChatSessionEntity` | RAG 聊天会话 |
| `rag_chat_messages` | `RagChatMessageEntity` | RAG 聊天消息 |
| `rag_session_knowledge_bases` | JPA ManyToMany 中间表 | 聊天会话绑定多个知识库 |

关键字段：

- `knowledge_bases.fileHash`：重复上传检测。
- `knowledge_bases.vectorStatus/vectorError/chunkCount`：向量化状态。
- `vector_store.metadata.kb_id`：用于按知识库过滤向量结果。
- `rag_chat_messages.completed`：SSE 流式回答未完成时为 `false`，结束后更新为 `true`。

### 语音面试

| 表 | 实体 | 作用 |
| --- | --- | --- |
| `voice_interview_sessions` | `VoiceInterviewSessionEntity` | 语音面试会话、阶段、状态、时长、评估状态 |
| `voice_interview_messages` | `VoiceInterviewMessageEntity` | ASR 识别文本和 AI 追问文本 |
| `voice_interview_evaluations` | `VoiceInterviewEvaluationEntity` | 语音面试评估报告 |

关键字段：

- `voice_interview_sessions.status`：`IN_PROGRESS`、`PAUSED`、`COMPLETED`、`FAILED`。
- `currentPhase`：`INTRO`、`TECH`、`PROJECT`、`HR`、`COMPLETED`。
- `voice_interview_messages.userRecognizedText/aiGeneratedText`：一次问答的两侧文本。

### 面试日程

| 表 | 实体 | 作用 |
| --- | --- | --- |
| `interview_schedule` | `InterviewScheduleEntity` | 公司、岗位、面试时间、会议链接、轮次、状态 |

状态：`PENDING`、`COMPLETED`、`CANCELLED`、`RESCHEDULED`。

### Provider 配置

| 表 | 实体 | 作用 |
| --- | --- | --- |
| `llm_provider_config` | `LlmProviderEntity` | OpenAI 兼容 Provider 配置，API Key 加密存储 |
| `llm_global_setting` | `LlmGlobalSettingEntity` | 默认聊天 Provider、默认 Embedding Provider |

## 7. Redis 使用

### 文字面试会话缓存

`InterviewSessionCache` 保存文字面试的临时会话状态：

- `sessionId`
- `resumeText`
- `resumeId`
- `questions`
- `currentIndex`
- `status`

读取会话时先读 Redis，缓存不存在再从数据库恢复。

### Redis Stream 异步任务

统一模板：

- `AbstractStreamProducer<T>`：把任务写入 Stream。
- `AbstractStreamConsumer<T>`：启动后台线程，创建 consumer group，循环消费、重试、ack。
- 最大重试次数：`3`。
- 每批消费：`10`。
- Stream 最大长度：`1000`。

| 任务 | Stream Key | Consumer Group | Producer | Consumer |
| --- | --- | --- | --- | --- |
| 简历分析 | `resume:analyze:stream` | `analyze-group` | `AnalyzeStreamProducer` | `AnalyzeStreamConsumer` |
| 知识库向量化 | `knowledgebase:vectorize:stream` | `vectorize-group` | `VectorizeStreamProducer` | `VectorizeStreamConsumer` |
| 文字面试评估 | `interview:evaluate:stream` | `evaluate-group` | `EvaluateStreamProducer` | `EvaluateStreamConsumer` |
| 语音面试评估 | `voice:evaluate:stream` | `voice-evaluate-group` | `VoiceEvaluateStreamProducer` | `VoiceEvaluateStreamConsumer` |

## 8. LLM Provider 链路

### 配置来源

默认 Provider 在 `application.yml` 的 `app.ai.providers` 下配置。运行时也会同步到数据库表：

- `llm_provider_config`
- `llm_global_setting`

API Key 通过 `ApiKeyEncryptionService` 加密后保存。

### 创建客户端

`LlmProviderRegistry` 负责根据 Provider 创建和缓存：

- `ChatClient`：普通聊天，默认可带 Skill Tool 和 Advisor。
- `PlainChatClient`：无工具调用，适合结构化 JSON 输出。
- `VoiceChatClient`：语音面试用，支持流式和工具调用。
- `EmbeddingModel`：知识库向量化和检索使用。

核心链路：

```text
业务 Service
  -> LlmProviderRegistry.getChatClientOrDefault(providerId)
  -> 读取 llm_provider_config / application.yml
  -> ApiPathResolver 构造 OpenAIClient
  -> OpenAiChatModel
  -> ChatClient
```

### Provider 设置页面链路

```text
SettingsPage
  -> llmProviderApi
  -> LlmProviderController /api/llm-provider/...
  -> LlmProviderConfigService
  -> LlmProviderRepository / LlmGlobalSettingRepository
  -> LlmProviderRegistry.reload()
```

## 9. 简历模块功能链路

### 9.1 上传简历并异步分析

前端入口：

```text
UploadPage
  -> resumeApi.uploadAndAnalyze(file)
  -> POST /api/resumes/upload
```

后端链路：

```text
ResumeController.uploadAndAnalyze
  -> ResumeUploadService.uploadAndAnalyze
    1. FileValidationService.validateFile，限制 10MB
    2. ResumeParseService.detectContentType
    3. ResumePersistenceService.findExistingResume，按 fileHash 去重
    4. ResumeParseService.parseResume，用 Tika 抽取文本
    5. FileStorageService.uploadResume，上传到 S3/RustFS
    6. ResumePersistenceService.saveResume，写 resumes，状态 PENDING
    7. AnalyzeStreamProducer.sendAnalyzeTask，写 Redis Stream
  -> 返回 resumeId、storage、duplicate、analyzeStatus=PENDING
```

异步消费者：

```text
AnalyzeStreamConsumer
  -> markProcessing：resumes.analyzeStatus = PROCESSING
  -> ResumeGradingService.analyzeResume
    -> 加载 prompts/resume-analysis-system.st
    -> 加载 prompts/resume-analysis-user.st
    -> LlmProviderRegistry.getDefaultChatClient()
    -> StructuredOutputInvoker 调用 AI 并解析 JSON
  -> ResumePersistenceService.saveAnalysis，写 resume_analyses
  -> markCompleted：resumes.analyzeStatus = COMPLETED
```

失败时：

- 任务最多重试 3 次。
- 最终失败写 `resumes.analyzeStatus=FAILED` 和 `analyzeError`。
- 前端列表通过查询详情/列表看到失败状态，可调用重试接口。

### 9.2 简历列表和详情

```text
HistoryPage
  -> GET /api/resumes
  -> ResumeHistoryService.getAllResumes
  -> resumes + latest resume_analyses + interview count
```

```text
ResumeDetailPage
  -> GET /api/resumes/{id}/detail
  -> ResumeHistoryService.getResumeDetail
  -> resumes + analyses + interviews
```

### 9.3 重新分析

```text
ResumeDetailPage / HistoryPage
  -> POST /api/resumes/{id}/reanalyze
  -> ResumeUploadService.reanalyze
    1. 读取 resumes
    2. 如果 resumeText 不存在，从 S3 下载原文件后重新解析
    3. 设置 analyzeStatus=PENDING
    4. 重新写入 resume:analyze:stream
```

### 9.4 导出 PDF

```text
ResumeDetailPage
  -> GET /api/resumes/{id}/export
  -> ResumeHistoryService.exportAnalysisPdf
  -> PdfExportService
  -> ResponseEntity<byte[]> application/pdf
```

## 10. 文字模拟面试链路

### 10.1 Skill 列表

Skill 定义在 `app/src/main/resources/skills/*/SKILL.md` 和 `skill.meta.yml`。

```text
UnifiedInterviewModal / InterviewHubPage
  -> skillApi.listSkills()
  -> GET /api/interview/skills
  -> InterviewSkillController
  -> InterviewSkillService
  -> 加载 classpath:skills
```

接口：

- `GET /api/interview/skills`：全部 Skill。
- `GET /api/interview/skills/{id}`：单个 Skill。
- `POST /api/interview/skills/parse-jd`：从 JD 中提取自定义考察分类。

### 10.2 创建文字面试会话

前端入口：

```text
InterviewHubPage / ResumeDetailPage
  -> interviewApi.createSession
  -> POST /api/interview/sessions
```

后端链路：

```text
InterviewController.createSession
  -> InterviewSessionService.createSession
    1. 如果有 resumeId 且 forceCreate != true，先找未完成会话
    2. 生成 sessionId
    3. 查询历史问题，用于避免重复出题
    4. InterviewQuestionService.generateQuestionsBySkill
    5. InterviewSessionCache.saveSession，写 Redis 缓存
    6. InterviewPersistenceService.saveSession，写 interview_sessions
```

出题逻辑：

```text
InterviewQuestionService.generateQuestionsBySkill
  -> resolveSkill
  -> LlmProviderRegistry.getPlainChatClient(provider)
  -> 如果没有简历：只按 Skill 方向出题
  -> 如果有简历：并行生成
       简历题约 60%
       方向题约 40%
  -> 使用 StructuredOutputInvoker 解析结构化问题列表
  -> 失败时降级到 fallback questions
```

### 10.3 答题

获取当前题：

```text
InterviewPage
  -> GET /api/interview/sessions/{sessionId}/question
  -> InterviewSessionService.getCurrentQuestionResponse
  -> 从 Redis 取当前题，必要时从 DB 恢复
```

暂存答案：

```text
InterviewPage 自动保存
  -> PUT /api/interview/sessions/{sessionId}/answers
  -> InterviewSessionService.saveAnswer
  -> 更新 Redis questions
  -> upsert interview_answers
```

提交答案：

```text
InterviewPage 点击下一题
  -> POST /api/interview/sessions/{sessionId}/answers
  -> InterviewSessionService.submitAnswer
    1. 校验 questionIndex
    2. 把答案写回问题列表
    3. 保存 interview_answers
    4. 更新 interview_sessions.currentQuestionIndex
    5. 更新 Redis currentIndex/questions
    6. 如果最后一题完成，设置 COMPLETED 并触发异步评估
```

### 10.4 完成和评估

最后一题提交或提前交卷都会触发：

```text
InterviewSessionService.enqueueEvaluationTask
  -> interview_sessions.evaluateStatus = PENDING
  -> EvaluateStreamProducer.sendEvaluateTask
```

消费者：

```text
EvaluateStreamConsumer
  -> markProcessing：evaluateStatus=PROCESSING
  -> 读取 interview_sessions.questionsJson
  -> 读取 interview_answers，把用户答案合并到 questions
  -> LlmProviderRegistry.getChatClientOrDefault(session.llmProvider)
  -> AnswerEvaluationService.evaluateInterview
  -> UnifiedEvaluationService.evaluate
       分批评估
       合并每题分数
       二次总结 overallFeedback/strengths/improvements
  -> InterviewPersistenceService.saveReport
  -> markCompleted：evaluateStatus=COMPLETED
```

### 10.5 报告查看和导出

```text
InterviewHistoryPage
  -> GET /api/interview/sessions
```

```text
InterviewDetailPanel
  -> GET /api/interview/sessions/{sessionId}/details
  -> InterviewHistoryService.getInterviewDetail
```

```text
导出 PDF
  -> GET /api/interview/sessions/{sessionId}/export
  -> InterviewHistoryService.exportInterviewPdf
  -> PdfExportService
```

## 11. 知识库模块链路

### 11.1 上传知识库并异步向量化

前端入口：

```text
KnowledgeBaseUploadPage
  -> knowledgeBaseApi.uploadKnowledgeBase(file, name, category)
  -> POST /api/knowledgebase/upload
```

后端链路：

```text
KnowledgeBaseController.uploadKnowledgeBase
  -> KnowledgeBaseUploadService.uploadKnowledgeBase
    1. FileValidationService.validateFile，限制 50MB
    2. KnowledgeBaseParseService.detectContentType
    3. FileHashService.calculateHash，按 hash 去重
    4. KnowledgeBaseParseService.parseContent，用 Tika 抽文本
    5. FileStorageService.uploadKnowledgeBase，上传到 S3/RustFS
    6. KnowledgeBasePersistenceService.saveKnowledgeBase，写 knowledge_bases，状态 PENDING
    7. VectorizeStreamProducer.sendVectorizeTask，写 Redis Stream
```

异步消费者：

```text
VectorizeStreamConsumer
  -> markProcessing：vectorStatus=PROCESSING
  -> KnowledgeBaseVectorService.vectorizeAndStore
    1. TokenTextSplitter 切块
    2. 给 chunk metadata 写 pending kb_id、target id、job id
    3. 每批最多 10 个 chunk 调用 Embedding 并写 vector_store
    4. 成功后删除旧 kb_id 向量，把 pending job 提升为正式 kb_id
  -> markCompleted：vectorStatus=COMPLETED
```

这个“pending -> promote”设计用于避免重向量化失败时把旧可用向量删掉。

### 11.2 知识库管理

```text
KnowledgeBaseManagePage
  -> GET /api/knowledgebase/list?sortBy=&vectorStatus=
  -> KnowledgeBaseListService.listKnowledgeBases
```

其他接口：

- `GET /api/knowledgebase/{id}`：详情。
- `DELETE /api/knowledgebase/{id}`：删除元数据、原文件、向量。
- `GET /api/knowledgebase/{id}/download`：下载原文件。
- `GET /api/knowledgebase/categories`：分类列表。
- `GET /api/knowledgebase/category/{category}`：按分类查。
- `GET /api/knowledgebase/uncategorized`：未分类。
- `PUT /api/knowledgebase/{id}/category`：修改分类。
- `GET /api/knowledgebase/search?keyword=`：搜索。
- `GET /api/knowledgebase/stats`：统计。
- `POST /api/knowledgebase/{id}/revectorize`：重新向量化。

### 11.3 普通 RAG 问答

```text
KnowledgeBaseQueryPage
  -> POST /api/knowledgebase/query
  -> KnowledgeBaseQueryService.queryKnowledgeBase
```

业务步骤：

```text
KnowledgeBaseQueryService.answerQuestion
  1. 校验 knowledgeBaseIds 和 question
  2. KnowledgeBaseCountService.updateQuestionCounts
  3. rewriteQuestion，可选调用 LLM 改写查询
  4. resolveSearchParams，根据问题长度决定 topK 和 minScore
  5. KnowledgeBaseVectorService.similaritySearch
  6. 拼接命中的 Document text 为 context
  7. 使用 knowledgebase-query-system/user prompt 调用 ChatClient
  8. 返回 QueryResponse
```

### 11.4 流式 RAG 问答

```text
KnowledgeBaseQueryPage
  -> knowledgeBaseApi.queryKnowledgeBaseStream
  -> POST /api/knowledgebase/query/stream
  -> produces text/event-stream
```

后端返回 `Flux<String>`，前端 `stream.ts` 逐块读取 `data:` 内容并追加到页面。

## 12. RAG 聊天链路

RAG 聊天是在普通 RAG 问答之上增加“会话、消息、置顶、多知识库绑定、历史上下文”。

### 12.1 创建会话

```text
KnowledgeBaseQueryPage
  -> ragChatApi.createSession(knowledgeBaseIds, title)
  -> POST /api/rag-chat/sessions
  -> RagChatSessionService.createSession
    1. 校验知识库 ID 都存在
    2. 创建 rag_chat_sessions
    3. 写入 rag_session_knowledge_bases 中间表
```

### 12.2 发送消息并流式回答

```text
ragChatApi.sendMessageStream
  -> POST /api/rag-chat/sessions/{sessionId}/messages/stream
  -> RagChatController.sendMessageStream
```

后端链路：

```text
RagChatSessionService.prepareStreamMessage
  -> 保存 USER 消息
  -> 创建 ASSISTANT 空消息 completed=false
  -> 更新会话 messageCount

RagChatSessionService.getStreamAnswer
  -> 读取会话绑定的 knowledgeBaseIds
  -> 根据配置读取最近历史消息
  -> KnowledgeBaseQueryService.answerQuestionStream
  -> SSE 持续返回 chunk

流完成后：
  -> RagChatSessionService.completeStreamMessage
  -> 更新 ASSISTANT 消息 content 和 completed=true
```

失败时也会把已生成内容或错误文本保存到 `rag_chat_messages`。

## 13. 面试日程链路

### 13.1 解析邀请文本

```text
InterviewSchedulePage
  -> interviewScheduleApi.parse(rawText, source)
  -> POST /api/interview-schedule/parse
  -> InterviewParseService.parse
```

解析策略：

```text
InterviewParseService.parse
  1. 输入为空直接失败
  2. 按 source 选择规则解析：feishu / tencent / zoom
  3. 如果 source 不明确，自动按文本特征尝试所有规则
  4. 如果规则无法得到 companyName + position + interviewTime
     -> 调用 AI 解析
  5. AI 返回 JSON 后转成 CreateInterviewRequest
```

注意：AI 解析相对日期时基于运行时当前日期。复建时要明确传当前日期到 prompt。

### 13.2 日程 CRUD

```text
InterviewSchedulePage
  -> POST /api/interview-schedule
  -> InterviewScheduleService.create
  -> interview_schedule.status = PENDING
```

其他接口：

- `GET /api/interview-schedule/{id}`：详情。
- `GET /api/interview-schedule?status=&start=&end=`：列表或时间范围查询。
- `PUT /api/interview-schedule/{id}`：更新内容。
- `PATCH|PUT /api/interview-schedule/{id}/status?status=`：更新状态。
- `DELETE /api/interview-schedule/{id}`：删除。

## 14. 语音面试链路

语音面试由 REST 管会话生命周期，由 WebSocket 处理实时音频。

### 14.1 创建语音会话

```text
VoiceInterviewPage
  -> voiceInterviewApi.createSession
  -> POST /api/voice-interview/sessions
  -> VoiceInterviewController.createSession
  -> VoiceInterviewService.createSession
  -> 写 voice_interview_sessions
  -> 返回 webSocketUrl
```

`CreateSessionRequest` 包含：

- `roleType`
- `skillId`
- `difficulty`
- `customJdText`
- `resumeId`
- `introEnabled/techEnabled/projectEnabled/hrEnabled`
- `plannedDuration`
- `llmProvider`

### 14.2 WebSocket 连接

WebSocket 路径：

```text
/ws/voice-interview/{sessionId}
```

注册位置：

```text
WebSocketConfig.registerWebSocketHandlers
  -> VoiceInterviewWebSocketHandler
```

连接建立：

```text
VoiceInterviewWebSocketHandler.afterConnectionEstablished
  1. 从 URL 提取 sessionId
  2. 保存 WebSocketSession 和 SessionState
  3. startDashScopeStt，启动 Qwen ASR 实时识别
  4. 发送 welcome control 消息
  5. 如果没有历史消息，发送开场问题
     -> 保存 AI 消息
     -> 下发 text
     -> TTS 合成并下发 audio/audio_chunk
```

### 14.3 前端发给后端的 WebSocket 消息

音频消息：

```json
{
  "type": "audio",
  "data": "base64-audio",
  "timestamp": 1710000000000
}
```

控制消息：

```json
{
  "type": "control",
  "action": "manual_commit",
  "data": {},
  "timestamp": 1710000000000
}
```

具体控制动作由 `VoiceInterviewWebSocketHandler.handleControl` 处理，常见用途是手动提交、暂停/恢复、音频播放完成通知等。

### 14.4 后端发给前端的 WebSocket 消息

| type | 作用 |
| --- | --- |
| `control` | welcome、ASR ready、播放完成、暂停警告、超时暂停等控制事件 |
| `subtitle` | ASR 实时字幕，包含中间结果和最终结果 |
| `text` | AI 文字回复 |
| `audio` | AI 完整语音 |
| `audio_chunk` | AI 分段语音，按 index 顺序播放 |
| `error` | 错误提示 |

### 14.5 实时语音处理管线

核心链路：

```text
浏览器麦克风
  -> AudioRecorder / AudioWorklet
  -> base64 音频通过 WebSocket 发送
  -> VoiceInterviewWebSocketHandler.handleTextMessage(type=audio)
  -> QwenAsrService.appendAudio
  -> ASR partial/final 回调
  -> handleSttResult
  -> 合并 final 片段，等待静音 debounce
  -> LLM 生成下一问
  -> VoiceInterviewService.saveMessage
  -> QwenTtsService.synthesize
  -> PCM 转 WAV
  -> WebSocket 返回 audio_chunk/audio
```

关键设计：

- 服务端 VAD 判断用户一句话是否结束。
- `user-utterance-debounce-ms` 控制最终提交前等待多久。
- `min-commit-chars` 防止太短内容被立即提交。
- `aiSpeaking` 和冷却时间防止 AI 播放声音被麦克风重新录入。
- TTS 支持按句子分段并发合成，再按顺序下发。
- 5 分钟无活动自动暂停。

### 14.6 暂停、恢复、结束

REST 接口：

- `PUT /api/voice-interview/sessions/{sessionId}/pause`
- `PUT /api/voice-interview/sessions/{sessionId}/resume`
- `POST /api/voice-interview/sessions/{sessionId}/end`

结束会话后：

```text
VoiceInterviewService.endSession
  -> voice_interview_sessions.status = COMPLETED
  -> triggerEvaluation
  -> VoiceEvaluateStreamProducer.sendEvaluateTask
```

### 14.7 语音面试评估

```text
VoiceEvaluateStreamConsumer
  -> markProcessing：evaluateStatus=PROCESSING
  -> VoiceInterviewEvaluationService.generateEvaluation
    1. 读取 voice_interview_messages
    2. 转成通用 QaRecord
    3. 调用 UnifiedEvaluationService
    4. 写 voice_interview_evaluations
  -> markCompleted：evaluateStatus=COMPLETED
```

前端报告页轮询：

```text
VoiceInterviewEvaluationPage
  -> GET /api/voice-interview/sessions/{sessionId}/evaluation
  -> 如果 COMPLETED，返回 evaluation
  -> 否则继续轮询状态
```

## 15. 接口清单

### 简历

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/resumes/upload` | 上传简历并入队异步分析 |
| `GET` | `/api/resumes` | 简历列表 |
| `GET` | `/api/resumes/{id}/detail` | 简历详情 |
| `GET` | `/api/resumes/{id}/export` | 导出简历分析 PDF |
| `DELETE` | `/api/resumes/{id}` | 删除简历 |
| `POST` | `/api/resumes/{id}/reanalyze` | 重新分析 |
| `GET` | `/api/resumes/health` | 健康检查 |

### 文字面试

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/interview/sessions` | 会话列表 |
| `POST` | `/api/interview/sessions` | 创建会话 |
| `GET` | `/api/interview/sessions/{sessionId}` | 会话详情 |
| `GET` | `/api/interview/sessions/{sessionId}/question` | 当前题 |
| `POST` | `/api/interview/sessions/{sessionId}/answers` | 提交答案并进入下一题 |
| `PUT` | `/api/interview/sessions/{sessionId}/answers` | 暂存答案 |
| `POST` | `/api/interview/sessions/{sessionId}/complete` | 提前交卷 |
| `GET` | `/api/interview/sessions/{sessionId}/report` | 同步生成报告，当前主要由异步评估替代 |
| `GET` | `/api/interview/sessions/{sessionId}/details` | 报告详情 |
| `GET` | `/api/interview/sessions/{sessionId}/export` | 导出报告 PDF |
| `GET` | `/api/interview/sessions/unfinished/{resumeId}` | 查找未完成会话 |
| `DELETE` | `/api/interview/sessions/{sessionId}` | 删除会话 |

### Skill

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/interview/skills` | Skill 列表 |
| `GET` | `/api/interview/skills/{id}` | Skill 详情 |
| `POST` | `/api/interview/skills/parse-jd` | JD 解析为考察分类 |

### 知识库

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/knowledgebase/upload` | 上传并入队向量化 |
| `GET` | `/api/knowledgebase/list` | 知识库列表 |
| `GET` | `/api/knowledgebase/{id}` | 知识库详情 |
| `DELETE` | `/api/knowledgebase/{id}` | 删除知识库 |
| `GET` | `/api/knowledgebase/{id}/download` | 下载原文件 |
| `POST` | `/api/knowledgebase/query` | 普通 RAG 问答 |
| `POST` | `/api/knowledgebase/query/stream` | SSE RAG 问答 |
| `GET` | `/api/knowledgebase/categories` | 分类列表 |
| `GET` | `/api/knowledgebase/category/{category}` | 分类下的知识库 |
| `GET` | `/api/knowledgebase/uncategorized` | 未分类知识库 |
| `PUT` | `/api/knowledgebase/{id}/category` | 修改分类 |
| `GET` | `/api/knowledgebase/search` | 搜索 |
| `GET` | `/api/knowledgebase/stats` | 统计 |
| `POST` | `/api/knowledgebase/{id}/revectorize` | 重新向量化 |

### RAG 聊天

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/rag-chat/sessions` | 创建聊天会话 |
| `GET` | `/api/rag-chat/sessions` | 会话列表 |
| `GET` | `/api/rag-chat/sessions/{sessionId}` | 会话详情 |
| `PUT` | `/api/rag-chat/sessions/{sessionId}/title` | 修改标题 |
| `PUT` | `/api/rag-chat/sessions/{sessionId}/pin` | 置顶/取消置顶 |
| `PUT` | `/api/rag-chat/sessions/{sessionId}/knowledge-bases` | 修改绑定知识库 |
| `DELETE` | `/api/rag-chat/sessions/{sessionId}` | 删除会话 |
| `POST` | `/api/rag-chat/sessions/{sessionId}/messages/stream` | 发送消息并 SSE 流式回答 |

### 面试日程

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/interview-schedule/parse` | 解析邀请文本 |
| `POST` | `/api/interview-schedule` | 创建日程 |
| `GET` | `/api/interview-schedule/{id}` | 日程详情 |
| `GET` | `/api/interview-schedule` | 日程列表 |
| `PUT` | `/api/interview-schedule/{id}` | 更新日程 |
| `PATCH/PUT` | `/api/interview-schedule/{id}/status` | 更新状态 |
| `DELETE` | `/api/interview-schedule/{id}` | 删除日程 |

### 语音面试

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/voice-interview/sessions` | 创建语音会话 |
| `GET` | `/api/voice-interview/sessions/{sessionId}` | 会话详情 |
| `GET` | `/api/voice-interview/sessions` | 会话列表 |
| `POST` | `/api/voice-interview/sessions/{sessionId}/end` | 结束会话并触发评估 |
| `PUT` | `/api/voice-interview/sessions/{sessionId}/pause` | 暂停 |
| `PUT` | `/api/voice-interview/sessions/{sessionId}/resume` | 恢复 |
| `DELETE` | `/api/voice-interview/sessions/{sessionId}` | 删除 |
| `GET` | `/api/voice-interview/sessions/{sessionId}/messages` | 消息历史 |
| `GET` | `/api/voice-interview/sessions/{sessionId}/evaluation` | 查询评估状态/结果 |
| `POST` | `/api/voice-interview/sessions/{sessionId}/evaluation` | 手动触发评估 |
| `WS` | `/ws/voice-interview/{sessionId}` | 实时语音通信 |

### 多模型配置

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/llm-provider/list` | Provider 列表 |
| `GET` | `/api/llm-provider/{id}` | Provider 详情 |
| `POST` | `/api/llm-provider` | 新建 Provider |
| `PUT` | `/api/llm-provider/{id}` | 更新 Provider |
| `DELETE` | `/api/llm-provider/{id}` | 删除 Provider |
| `POST` | `/api/llm-provider/{id}/test` | 测试 Provider |
| `POST` | `/api/llm-provider/reload` | 清空并重载客户端缓存 |
| `GET` | `/api/llm-provider/default-provider` | 默认 Provider |
| `PUT` | `/api/llm-provider/default-provider` | 修改默认聊天 Provider |
| `PUT` | `/api/llm-provider/default-embedding-provider` | 修改默认 Embedding Provider |
| `GET` | `/api/llm-provider/voice/asr` | ASR 配置 |
| `PUT` | `/api/llm-provider/voice/asr` | 修改 ASR 配置 |
| `GET` | `/api/llm-provider/voice/tts` | TTS 配置 |
| `PUT` | `/api/llm-provider/voice/tts` | 修改 TTS 配置 |
| `POST` | `/api/llm-provider/voice/asr/test` | 测试 ASR |

## 16. 外部依赖如何接入

### PostgreSQL + pgvector

复建要点：

1. 使用 `pgvector/pgvector:pg16` 镜像。
2. 初始化执行 `CREATE EXTENSION IF NOT EXISTS vector;`。
3. `application.yml` 配置 `spring.ai.vectorstore.pgvector.initialize-schema=true`，开发环境自动创建 `vector_store`。
4. Embedding 维度要和模型一致。当前默认 `text-embedding-v3`，维度 `1024`。

### Redis

复建要点：

1. Redisson 配置连接 Redis。
2. 实现基础 `RedisService`，支持 Stream add、ack、consume、claim pending。
3. 先做一个异步任务，再抽象 `AbstractStreamProducer/Consumer`。
4. 注意 Consumer 在 `@PostConstruct` 后台启动。

### S3 对象存储

复建要点：

1. 本地用 MinIO/RustFS。
2. 后端用 AWS S3 SDK，配置 endpoint、accessKey、secretKey、bucket。
3. 启动时检查并创建 bucket。
4. 上传文件后，数据库只保存 `storageKey/storageUrl`。

### AI 调用

复建要点：

1. 统一走 OpenAI 兼容协议。
2. 普通聊天、结构化输出、Embedding 分开建客户端。
3. 结构化输出使用 `BeanOutputConverter`，失败时业务层重试。
4. Prompt 放到 `resources/prompts/*.st`，不要写死在 Java 代码里。

## 17. 最小可运行版本拆分

如果你从零写，可以按下面里程碑验收：

### 里程碑 1：基础壳

- 后端启动成功。
- `GET /api/resumes/health` 返回 `Result`。
- 前端能通过 Vite 代理请求后端。

### 里程碑 2：文件和数据库

- 能上传文件到 S3。
- 能用 Tika 解析 TXT/PDF。
- 能保存 `resumes` 或 `knowledge_bases`。

### 里程碑 3：第一个异步任务

- 上传简历后状态为 `PENDING`。
- Redis Stream 中出现任务。
- Consumer 消费后状态变为 `COMPLETED` 或 `FAILED`。

### 里程碑 4：AI 结构化输出

- 能调用 Provider。
- 能把 AI JSON 转成 Java DTO。
- 简历分析能写入 `resume_analyses`。

### 里程碑 5：文字面试

- 能加载 Skill。
- 能生成题目。
- 能保存答案。
- 完成后异步生成报告。

### 里程碑 6：知识库 RAG

- 知识库上传后能切块、向量化。
- 查询时能从 `vector_store` 命中文档。
- 能将 context 给 LLM 生成回答。

### 里程碑 7：SSE 和聊天

- 普通 RAG 问答改成流式输出。
- 能保存 USER 和 ASSISTANT 消息。
- 刷新页面后历史消息存在。

### 里程碑 8：语音面试

- REST 创建会话。
- WebSocket 连接成功。
- 能接收前端音频并返回字幕。
- 能把用户文本交给 LLM 生成追问。
- 能 TTS 合成并返回音频。
- 结束后能异步生成评估。

## 18. 复建时最容易漏的点

- 前端 JSON 接口默认只拿 `Result.data`，后端如果没包 `Result`，前端会拿到不一致的数据。
- 文件下载接口不要包 `Result`，直接返回 PDF/文件字节。
- SSE 不能用普通 Axios 拦截器，要用 `fetch` 读 `ReadableStream`。
- WebSocket URL 不是 `/api`，开发代理如果只代理 `/api`，需要额外处理 `/ws`。
- pgvector 的 `vector_store` 不是普通 JPA 实体表，主要由 Spring AI VectorStore 管。
- 重新向量化要避免先删旧向量，当前做法是先写 pending metadata，成功后再替换。
- 文字面试 Redis 缓存丢失后要能从数据库恢复。
- AI 结构化输出不要用带工具调用的 ChatClient，项目用 `getPlainChatClient` 避免工具消息破坏 JSON。
- Provider API Key 加密需要 `APP_AI_CONFIG_ENCRYPTION_KEY`，Docker 部署必须配置。
- 语音面试会出现回声录入，当前用 `aiSpeaking` 和冷却时间过滤。
- 语音评估和文字评估共用 `UnifiedEvaluationService`，不要写两套评分逻辑。

## 19. 你可以照着写的目录骨架

后端：

```text
app/src/main/java/interview/guide/
  App.java
  common/
    result/Result.java
    exception/
    config/
    ai/
    async/
    evaluation/
  infrastructure/
    file/
    redis/
    export/
    mapper/
  modules/
    resume/
      ResumeController.java
      model/
      repository/
      service/
      listener/
    interview/
      InterviewController.java
      model/
      repository/
      service/
      listener/
      skill/
    knowledgebase/
      KnowledgeBaseController.java
      RagChatController.java
      model/
      repository/
      service/
      listener/
    interviewschedule/
    llmprovider/
    voiceinterview/
```

前端：

```text
frontend/src/
  main.tsx
  App.tsx
  api/
    request.ts
    stream.ts
    resume.ts
    history.ts
    interview.ts
    knowledgebase.ts
    ragChat.ts
    interviewSchedule.ts
    llmProvider.ts
    voiceInterview.ts
  pages/
  components/
  hooks/
  types/
  utils/
```

资源：

```text
app/src/main/resources/
  application.yml
  prompts/
  skills/
  scripts/
  voice-interview-opening.yml
  fonts/
```

## 20. 本项目的一句话主线

这个项目的主线是：

```text
前端收集用户输入
  -> 后端保存原始数据
  -> 需要耗时 AI/Embedding 的任务放入 Redis Stream
  -> 后台 Consumer 调用 AI 并保存结果
  -> 前端轮询或流式读取结果
```

理解这条主线后，再分别看简历、文字面试、知识库、语音面试，会发现它们只是输入形态和结果表不同，底层模式基本一致。
