# InterviewGuide

面向求职准备场景的 AI 面试辅助平台。本仓库正在按可验证的阶段逐步实现以下主链路：

```text
简历上传与 AI 分析
  -> 文字模拟面试与评估
  -> 知识库向量化与 RAG 问答
  -> 带历史记录的流式 RAG 聊天
```

当前仓库处于基础设施建设阶段。已完成 Spring Boot 基础后端、统一响应与异常处理、数据库健康检查，以及兼容 S3 协议的文件存储封装。简历业务接口、前端、AI 分析、模拟面试和 RAG 尚在后续计划中。

> 本项目不包含面试日程、语音面试、WebSocket 音频、ASR 或 TTS 功能。README 中的能力状态以当前代码为准。

## 当前进度

| 阶段 | 状态 | 当前内容 |
| --- | --- | --- |
| 1. 基础后端 | 已完成 | Spring Boot、PostgreSQL、统一响应、异常处理、Swagger、Trace ID、JPA 审计、健康检查 |
| 2. 前端基础壳 | 未开始 | 计划采用 React 18、TypeScript、Vite |
| 3. 文件基础设施 | 进行中 | 已完成 S3/RustFS/MinIO 客户端和统一存储服务；文件校验、哈希与文本解析待实现 |
| 4. 简历模块 | 未开始 | 上传、去重、异步分析、详情与导出 |
| 5. LLM Provider | 未开始 | Provider 配置、密钥加密、结构化输出 |
| 6. 文字模拟面试 | 未开始 | 出题、答题、缓存、异步评估 |
| 7. 知识库 RAG | 未开始 | 文档切块、Embedding、pgvector 检索 |
| 8. 流式 RAG 聊天 | 未开始 | 会话历史、SSE 流式回答、来源引用 |

详细实施顺序见 [docs/project-rebuild-order-1-8.md](docs/project-rebuild-order-1-8.md)。

## 已实现能力

- Java 21 + Spring Boot 3.3.5 后端工程。
- PostgreSQL 16 + pgvector、Redis、MinIO 的 Docker Compose 开发环境。
- 统一 `Result<T>` JSON 响应、业务异常和全局异常处理。
- 请求 `X-Trace-Id` 透传与日志 MDC 上下文。
- JPA UTC 时间配置和审计基础实体。
- Swagger UI 与数据库健康检查接口。
- `StorageConfigProperties` 类型安全配置绑定。
- `FileStorageService` 统一封装简历和知识库文件的上传、下载与删除。
- 服务端生成对象键：`{type}/{yyyy}/{MM}/{uuid}.{ext}`，不使用用户文件名作为存储键。
- S3 path-style 访问，支持 RustFS、MinIO 等 S3 兼容对象存储。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.3.5、Spring Web、Spring Validation |
| 持久化 | Spring Data JPA、PostgreSQL 16、pgvector |
| 对象存储 | AWS SDK for Java v2、S3 协议、RustFS/MinIO |
| 基础设施 | Redis 7、Docker Compose |
| API 文档 | springdoc-openapi / Swagger UI |
| 测试 | JUnit 5、Mockito、Spring Boot Test、Testcontainers |

## 项目结构

```text
.
|-- app/                              Spring Boot 后端
|   |-- src/main/java/interview/guide/
|   |   |-- common/                   通用配置、响应、异常和审计
|   |   |-- infrastructure/           数据库与对象存储适配器
|   |   `-- modules/                  业务模块
|   `-- src/test/                     单元测试与集成测试
|-- docker/postgresql/init/           PostgreSQL 初始化脚本
|-- docs/                             功能链、实施顺序和设计记录
|-- docker-compose.yml                PostgreSQL、Redis、MinIO
`-- .env.example                      本地环境变量模板
```

## 本地运行

### 环境要求

- JDK 21
- Maven 3.9+
- Docker Desktop 或兼容的 Docker Engine

### 1. 准备环境变量

在仓库根目录复制模板：

```powershell
Copy-Item .env.example .env
```

编辑 `.env` 并替换所有占位值。`.env` 已被 Git 忽略，不要提交真实密码或密钥。

| 变量 | 用途 | 示例 |
| --- | --- | --- |
| `POSTGRES_DB` | Compose 创建的数据库 | `interview_guide` |
| `POSTGRES_USER` | Compose 数据库用户 | `interview_guide` |
| `POSTGRES_PASSWORD` | Compose 数据库密码 | 本地强密码 |
| `DB_URL` | 后端 JDBC 地址 | `jdbc:postgresql://localhost:5432/interview_guide` |
| `DB_USERNAME` | 后端数据库用户 | `interview_guide` |
| `DB_PASSWORD` | 后端数据库密码 | 与本地数据库一致 |
| `REDIS_HOST` | Redis 主机 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `S3_ENDPOINT` | RustFS/MinIO S3 API 地址 | `http://localhost:9000` |
| `S3_ACCESS_KEY` | S3 访问密钥 | 仅通过环境变量注入 |
| `S3_SECRET_KEY` | S3 私密密钥 | 仅通过环境变量注入 |
| `S3_BUCKET` | 对象存储桶 | `interview-guide` |
| `S3_REGION` | S3 区域，可选 | `us-east-1` |
| `APP_AI_CONFIG_ENCRYPTION_KEY` | 后续 AI Provider 密钥加密 | 32 字节 Base64 密钥 |
| `APP_CORS_ALLOWED_ORIGINS` | 允许的前端来源 | `http://localhost:5173` |

`docker-compose.yml` 默认启动 MinIO。使用已有 RustFS 时，将 `S3_ENDPOINT`、`S3_ACCESS_KEY`、`S3_SECRET_KEY` 和 `S3_BUCKET` 指向对应实例即可，业务代码无需修改。

### 2. 启动基础设施

```powershell
docker compose up -d
docker compose ps
```

默认端口：

- PostgreSQL：`5432`
- Redis：`6379`
- MinIO S3 API：`9000`
- MinIO Console：`9001`

### 3. 启动后端

```powershell
Set-Location app
mvn spring-boot:run
```

启动后可访问：

- 后端：<http://localhost:8080>
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- 健康检查：`GET http://localhost:8080/api/resumes/health`

健康响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "application": "UP",
    "database": "UP"
  }
}
```

## 对象存储封装

上层业务通过 `FileStorageService` 访问文件，不直接依赖 AWS SDK、endpoint 或 bucket：

```java
String resumeKey = fileStorageService.uploadResume(file);
byte[] content = fileStorageService.downloadResume(resumeKey);
fileStorageService.deleteResume(resumeKey);

String knowledgeBaseKey = fileStorageService.uploadKnowledgeBase(file);
fileStorageService.deleteKnowledgeBase(knowledgeBaseKey);
```

当前存储封装负责生成安全对象键和执行 S3 操作。文件大小、MIME/Tika 检测、SHA-256 去重和文本抽取将在文件基础设施阶段继续补齐，上层业务不能把当前封装视为完整上传安全校验。

## 测试

在 `app` 目录执行：

```powershell
mvn test
```

数据库集成测试使用 Testcontainers，因此需要 Docker 正常运行。只运行不依赖 Docker 的现有单元测试：

```powershell
mvn -q '-Dtest=ResumeHealthControllerTest,FileStorageServiceTest' test
```

## 配置与安全约定

- 所有凭据通过环境变量注入，禁止写入源码、README、日志或 Git 历史。
- 数据库只保存对象存储 key，不保存临时签名 URL 作为唯一定位信息。
- Controller 只处理传输校验和服务调用；事务与业务决策位于 Service。
- 第三方 SDK 仅位于 `infrastructure`，业务模块不直接构造 S3、Redis 或 AI 客户端。
- 文件下载和 SSE 直接返回数据流，不包装为普通 `Result<T>`。
- 后续异步任务只传递 ID、重试次数和 Trace ID，不传递简历全文或密钥。

## 规划范围

项目只实现简历分析、文字面试、知识库 RAG 和流式 RAG 聊天四条关联链路。完整设计资料位于 `docs/`，但实际开发范围和验收顺序以 [docs/project-rebuild-order-1-8.md](docs/project-rebuild-order-1-8.md) 为准。

