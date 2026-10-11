# AI 智能面试官与求职能力评估系统 —— 项目介绍

> 版本：V1.0　|　文档用途：作为输入交给 ChatGPT 等 LLM 给出完善建议
> 生成日期：2026-10

---

## 一、项目概述

本系统是一个基于大语言模型（LLM）的 AI 智能面试官与求职能力评估平台。用户注册后可浏览岗位、配置面试参数（面试类型/难度/题数），系统通过 AI 自动生成面试题并支持文字/语音回答，AI 从 6 个维度（专业能力、逻辑能力、回答完整度、问题分析、表达能力、岗位匹配度）对每道题进行评分，并给出优劣势与改进建议，最终生成带雷达图的综合面试报告。系统还内置知识库 RAG 检索、简历解析、动态追问等能力。

**核心价值：**

- 把传统人工面试的主观、低效问题，转化为可量化、可复现、可追溯的 AI 评估流程
- 面向求职者的自我训练工具：随练随评，能力趋势可视化，持续改进
- 面向企业（预留）：可批量生成面试题、自动评分、输出结构化报告

---

## 二、技术架构总览

系统采用**前后端分离 + 独立 AI 服务**的三层架构，共 4 个可部署单元：

| 单元 | 技术栈 | 端口 |
|---|---|---|
| 前端 | Vue 3 + TypeScript + Vite 5 + Element Plus + Pinia + ECharts | 5173 |
| 后端 | Java 17 + Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + JWT | 8081 |
| AI 服务 | Python 3 + FastAPI + OpenAI SDK + numpy + SQLite 向量库 | 8001 |
| 数据库 | MySQL 8（主业务）+ SQLite（AI RAG 向量库）+ Redis（预留） | 3306 |

**部署与工程化：**

- Docker Compose 一键编排（mysql + backend + ai-service + frontend/Nginx），含健康检查与依赖顺序
- GitHub Actions CI 三条流水线（mvn test + pytest + vite build）
- 本地 Windows 快速启动脚本（start-all.ps1 / start-*.bat）

**调用链路：**

```
Vue SPA → Spring Boot REST API (8081, JWT) → FastAPI AI 服务 (8001, 内部 Key)
        → 外部 LLM (Agnes / SiliconFlow) + MySQL + SQLite 向量库
```

**三个关键设计主题：**

- **优雅降级**：每个 AI 调用（出题、评分、简历解析、embedding、RAG）都有编码级兜底（mock 题库、启发式评分、示例 JSON、零向量），确保无 API key 时 P0 主流程仍可跑通
- **异步处理**：答案评分、简历解析、知识库 embedding 均跑在 bounded 线程池，不阻塞请求响应
- **幂等性**：答案提交按 question 唯一键防重、出题在重试时复用已有题目、报告按需重新生成并替换旧记录

---

## 三、前端详细设计（Vue 3 SPA）

**技术栈：** Vue ^3.4.21 + TypeScript ^5.3.3 + Vite ^5.2.0 + Element Plus ^2.6.3 + Pinia ^2.1.7 + Vue Router ^4.3.0 + axios ^1.20.0 + ECharts ^5.5.0。开发端口 5173，Vite 代理 `/api` 与 `/uploads` 到 `http://localhost:8081`。

### 3.1 页面（views/ 共 11 个组件）

| 页面 | 功能 |
|---|---|
| `LoginView.vue` | 邮箱+密码登录，表单校验，成功把 JWT 存入 Pinia 并跳 `/dashboard` |
| `RegisterView.vue` | 注册（用户名/邮箱/密码/确认密码），校验后跳登录 |
| `DashboardView.vue` | 首页：最近 5 次面试、总数统计、ECharts 折线图展示能力趋势（总分/专业/逻辑/表达 4 条线，取自 `/dashboard/trends` 最近 10 次） |
| `JobsView.vue` | 岗位卡片网格，level 标签，点击进详情 |
| `JobDetailView.vue` | 岗位名称/描述/级别 + 核心技能标签 + "立即开始面试"按钮 |
| `InterviewCreateView.vue` | 面试配置：选岗位、可选简历、面试类型（技术/综合/项目）、难度（初级/中级/高级）、题数（5-30 步进 5），创建后进入面试间 |
| `InterviewView.vue` | **核心面试间**（约 520 行）：顺序加载题目、每题 300s 倒计时（超时自动提交"（本题超时未作答）"占位）、文字 textarea + Web Speech API 语音识别/录音、提交后轮询评分、右侧实时显示当前评分 + 参考答案（折叠分点渲染）、LLM 失败可重试 |
| `ReportView.vue` | 面试报告：综合得分徽章、ECharts 雷达图（6 维度）、综合评价、优势/待改进/建议列表、"参考答案汇总"折叠区（每题问题+参考答案） |
| `HistoryView.vue` | 历史面试分页表格（时间/岗位/类型/难度/状态标签），已完成可"查看报告" |
| `ProfileView.vue` | 个人中心：简历上传(multipart)、列表、删除、退出登录 |
| `KnowledgeView.vue` | 知识库：上传文档（岗位+文件+标题）、列表带状态、删除 |

### 3.2 路由结构（router/index.ts）

- **公开路由**：`/login`、`/register`
- **鉴权路由树**（MainLayout 下）：`dashboard`(默认 redirect)、`jobs`、`jobs/:id`、`interview/create`、`interview/:sessionId`、`reports/:reportId`、`interviews`(历史)、`profile`、`knowledge`
- 全局 `beforeEach` 守卫：无 token 跳 `/login`

### 3.3 API 封装（api/ 6 个模块）

- `request.ts` — axios 实例：`baseURL=/api/v1`，30s 超时，自动附加 Bearer token，401 登出跳转、403/404 提示
- `auth.ts` — `register` / `login`
- `job.ts` — `list`(分页) / `detail`(含技能)
- `interview.ts` — `create`/`get`/`complete`/`history`/`nextQuestion`(150s 超时时，含 lastQuestionId 幂等)/`submitAnswer`/`getEvaluation`/`getReport`/`submitAudioAnswer`(multipart webm)
- `resume.ts` — `upload`/`list`/`get`/`delete`
- `knowledge.ts` — `upload`/`list`/`delete`

### 3.4 状态管理（stores/user.ts）

单一 Pinia store `user`：持有 `token` + `userInfo`，持久化到 `localStorage`；`setAuth`/`logout`。

### 3.5 布局（layouts/MainLayout.vue）

顶部：Logo + 水平 el-menu（首页/岗位浏览/知识库/历史记录/个人中心）+ 用户名 + 退出；底部：版本信息。---

## 四、后端详细设计（Spring Boot 3.2.5 / Java 17）

**框架：** Spring Boot 3.2.5（父 POM），Java 17，Maven 构建。ORM 用 MyBatis-Plus 3.5.5，JWT 用 jjwt 0.12.5，HTTP 客户端用 RestTemplate（WebFlux 依赖）。

### 4.1 控制器（10 个，controller/）

| 控制器 | 路径 | 端点 |
|---|---|---|
| `AuthController` | `/api/v1/auth` | `GET /health` 存活探针；`POST /register` 创建用户（BCrypt 哈希+邮箱唯一）；`POST /login` 签发 JWT+用户信息，失败 401 |
| `InterviewController` | `/api/v1/interviews` | `POST /` 创建会话（默认 technical/medium/10 题/1800s）；`GET /{id}` 校验归属；`POST /{id}/complete` 状态→COMPLETED；`GET /history` 分页含 total |
| `QuestionController` | `/api/v1/interviews/{id}/questions` | `POST /next` 调 AI 生成下一题（可选 lastQuestionId 幂等重试，达上限 409）；`GET /` 列出本会话题目 |
| `AnswerController` | `/api/v1/interviews/{id}/answers` | `POST /` 提交文字答案（幂等复用/更新既有答案，返回 evaluationStatus + nextAction）；`POST /audio` 上传 webm 音频（MIME/大小/白名单校验，存 `/uploads/audios/`） |
| `ReportController` | `/api/v1` | `GET /interviews/{id}/report` 按需重新生成报告+参考答案汇总（404 if none）；`GET /answers/{id}/evaluation` 取 6 维评分+优劣势建议+参考答案 |
| `JobController` | `/api/v1/jobs` | `GET /` 分页列活跃岗位；`GET /{id}` 详情（岗位+技能 VO） |
| `ResumeController` | `/api/v1/resumes` | `POST` 上传（校验+存+异步 AI 解析）；`GET /` 我的列表；`GET /{id}`；`DELETE /{id}`（均校验归属） |
| `KnowledgeController` | `/api/v1/knowledge` | `POST` 上传文档；`GET` 按可选 jobId 列表；`DELETE /{id}` |
| `DashboardController` | `/api/v1/dashboard` | `GET /trends` 最近 10 次完成会话的 6 维度平均分序列（供前端折线图） |
| `DebugController` | `/api/v1/debug` | 仅 `@Profile("dev")`，`GET/POST /test` 验证鉴权流程 |

### 4.2 业务服务（7 个接口 + 实现，service/impl/）

| 服务 | 职责 |
|---|---|
| `InterviewSessionServiceImpl` | 会话 CRUD + 状态机 CREATED→IN_PROGRESS→COMPLETED，归属校验，分页历史 |
| `InterviewQuestionServiceImpl` | `generateNextQuestion`：拼上下文（简历解析摘要+近 5 次总分做自适应难度），调 AI `/internal/ai/question/generate`；**幂等**（重试复用已有后续题）；**兜底** 12 条硬编码中文通用题 |
| `InterviewAnswerServiceImpl` | 文字/语音答案提交，唯一键幂等；在 `aiTaskExecutor` 线程池**异步评分**：取 RAG 上下文→调 `/internal/ai/answer/evaluate`→落 6 维分+参考答案；异常时落 0 分 EVALUATION_FAILED 记录；本会话全评完自动触发报告；AI 不可达时用 Java 端启发式评分（基于长度 50+chars/10 等） |
| `InterviewReportServiceImpl` | `generateReport` 聚合本会话全部评分→综合分+6 维平均+去重优劣势建议+等级（优秀/良好/及格/待提高）+全兜底时加注；幂等 delete-then-insert；`getReportWithReferences` 额外返回每题参考答案列表 |
| `KnowledgeServiceImpl` | 文档存 `./uploads/knowledge/`，插入记录（UPLOADED），异步调 `/internal/ai/knowledge/embed`（文本 UTF-8、二进制 base64），状态→INDEXED 或 EMBED_FAILED |
| `ResumeServiceImpl` | 存 `./uploads/resumes/`，异步调 `/internal/ai/resume/parse`，回写 parsedJson+summary（失败写失败提示） |
| `UserService` | 注册（BCrypt+邮箱唯一）、登录（JWT 签发）、`loadUserByEmail` 供 JWT 过滤器用 |

### 4.3 实体与数据模型（12 个实体，entity/）

`User`、`Job`、`JobSkill`、`InterviewSession`、`InterviewQuestion`、`InterviewAnswer`、`AnswerEvaluation`（多字段 JSON 用 `JacksonTypeHandler`+`autoResultMap`）、`InterviewReport`（JSON dimensionScores）、`UserResume`（JSON parsedJson）、`KnowledgeDocument`、`KnowledgeChunk`（JSON metadata）、`TrainingRecommendation`。每个实体对应一个 MyBatis-Plus Mapper。

### 4.4 配置（config/）

- `AsyncConfig` — `aiTaskExecutor` 线程池（core 4 / max 16 / queue 100 / CallerRuns）承载异步 AI 工作
- `AutoFillHandler` — MyBatis-Plus `createdAt`/`updatedAt` 自动填充
- `MybatisPlusConfig` — MySQL 分页插件
- `RestClientConfig` — RestTemplate（连接 10s/读 120s），拦截器给所有 `/internal/ai/*` 请求加 `X-AI-Internal-Key` 头
- `WebMvcConfig` — 把 `/uploads/**` 映射到文件系统上传目录（绝对路径）

### 4.5 安全（security/）

- `SecurityConfig` — 无状态、关 CSRF、CORS（localhost:5173/3000/80/8081）；`permitAll`：`/api/v1/auth/**`、`/api/v1/internal/**`、`/uploads/**`、OPTIONS；BCrypt 编码器
- `JwtService` — jjwt HMAC-SHA，密钥来自 `ai.jwt.secret`（≥32 字符），subject=用户邮箱，24h 过期
- `JwtAuthFilter` — Bearer token→email claim→载入用户→填充 SecurityContext
- `InternalKeyFilter` — 守护 `/api/v1/internal/**`，校验 `X-AI-Internal-Key` 与 `ai.internal.key` 一致

### 4.6 必需环境变量（application.yml，缺失则启动失败）

- `MYSQL_PASSWORD`（`:?` 强制注入）
- `AI_INTERNAL_KEY`（后端调 AI 内部接口用，须与 AI 服务 `.env` 一致）
- `JWT_SECRET`（≥32 字符，HMAC-SHA 要求 256 位以上）

可选：`MYSQL_URL`（默认 localhost:3306/ai_interviewer）、`MYSQL_USERNAME`（默认 root）、`REDIS_*`、`AI_UPLOAD_DIR`（默认 ./uploads）、`AI_SERVICE_URL`（默认 http://localhost:8001）、`LOG_PATH`、`SPRING_PROFILES_ACTIVE`（默认 dev）。Redis 已声明但 V1.0 未实际使用。multipart 上限 20MB。---

## 五、AI 服务详细设计（Python FastAPI）

**框架：** Python 3 + FastAPI + uvicorn（端口 8001）。LLM 走 OpenAI 兼容协议（httpx 客户端），向量检索用 numpy + SQLite（`./data/knowledge.db`）+ 进程内存索引。`main.py` 共约 650 行，`if __name__ == "__main__"` 起 uvicorn。

### 5.1 LLM 集成

- 首选 **Agnes AI**（`AGNES_API_KEY` + `LLM_API_URL=https://apihub.agnes-ai.com/v1/chat/completions`，默认模型 agnes-2.5-flash，`.env` 现配 agnes-3.0-flash）
- 兜底 **SiliconFlow**（`SILICONFLOW_API_KEY`），同一 key 兼做 embedding（`BAAI/bge-m3`）
- `call_llm(messages, temperature=0.7, timeout=90)`：httpx POST，失败/超时返回 `""`
- `call_embedding(text)`：无 key 时返回 8 维零向量（优雅降级，不报错）
- `_parse_llm_json`：先剥 ```json 代码块围栏再 `json.loads`

**提示词设计（中文 system prompt）：**

- 出题 prompt 内嵌岗位名 + RAG 检索知识片段 + 简历摘要 + 面试类型 + 动态难度 + 已出题数，要求严格 JSON `{"content","type","difficulty","targetSkill"}`
- 评分 prompt 含 6 维加权（专业 30% / 逻辑 20% / 完整度 15% / 分析 15% / 表达 10% / 岗位匹配 10%），要求 `referenceAnswer` 为 200-400 字分点"要点 N：…"结构化标准答案 + 结尾"评分参考："高分提示
- 简历解析 prompt 抽取 `education/experience/skills/projects/summary` 的 JSON

### 5.2 端点清单（/internal/ai/*，内部鉴权）

| 端点 | 行为 / 兜底 |
|---|---|
| `GET /health` | 无鉴权，供 Docker/K8s 健康检查 |
| `POST /question/generate` | 自适应难度（近 2 次≥85 提难度、≤45 降难度）+ RAG 上下文 + 技能关键词识别（Redis/Java 集合/Spring/MySQL/多线程/项目经验/Python 基础/前端基础）；LLM 出题；**兜底** MOCK_QUESTIONS 题库（Java 后端 20 题/Python 15/前端 15/default 10）按 currentCount 取模 |
| `POST /question/followup` | 动态追问：回答好→深入原理/场景；一般→换角度/举例；差→降难度问基础；兜底静态通用追问句 |
| `POST /answer/evaluate` | LLM 6 维 JSON 评分 + 结构化参考答案；**兜底启发式**（base 50+长度/20，含逻辑连接词+8，长度>200/>500 各+5，加权求和；模板化优劣势 + 通用分点参考答案） |
| `POST /report/generate` | 对评分 totalScore 求平均→等级（优秀/良好/及格/待提高）+模板优劣势建议（后端实际在 Java 侧本地算报告，此端点为独立变体） |
| `POST /resume/parse` | LLM JSON 抽取（取前 2000 字符）；兜底示例结构 JSON |
| `POST /knowledge/retrieve` | 查询 embedding→余弦相似度取 top-3（阈值 0.3）；空索引返回 `{"chunks":[], "score":0}` |
| `POST /knowledge/embed` | 空内容 400；按 chunk_size（默认 500）切块→bge-m3 embedding→写 SQLite+内存索引，返回插入块数 |
| `POST /voice/transcribe` | DashScope paraformer-realtime-v2 ASR；无 DASHSCOPE_API_KEY 返回 not_configured，失败返回错误文本 |

### 5.3 RAG / 向量库

SQLite（WAL 模式）表 `knowledge_chunk(id, doc_id, chunk, embedding JSON)`，启动时全量载入进程内存列表；embedding 用 `BAAI/bge-m3`；检索用 numpy 余弦相似度。

### 5.4 鉴权与配置

所有 `/internal/ai/*` 经 `X-AI-Internal-Key` 中间件校验（与 `AI_INTERNAL_KEY` 环境变量匹配）；该变量为空时跳过鉴权（仅 dev 模式）。

`.env` 配置项：`AGNES_API_KEY`、`LLM_API_URL`、`MODEL_NAME`、`SILICONFLOW_API_KEY`、`EMBEDDING_API_URL`、`EMBEDDING_MODEL`、`DASHSCOPE_API_KEY`、`AI_SERVICE_PORT`、`AI_INTERNAL_KEY`。

---

## 六、数据库设计（MySQL 8，库 ai_interviewer，11 张表）

建库脚本：`AI智能面试官系统_MySQL_V1.0.sql`。字符集 utf8mb4。表清单与关系：

| 表 | 说明 |
|---|---|
| `user` | 用户：username、email（唯一）、password_hash、status、审计时间 |
| `user_resume` | 简历：user_id FK→user，file_name/file_url、parsed_json（JSON）、summary |
| `job` | 岗位：name、description、level（默认 junior）、status；索引 status |
| `job_skill` | 岗位技能：job_id FK→job、skill_name、weight DECIMAL(5,2) |
| `interview_session` | 面试会话：user_id/job_id FK、resume_id FK（可空）、interview_type/difficulty/question_limit/duration_limit_seconds、status（CREATED/IN_PROGRESS/COMPLETED）、started_at/ended_at |
| `interview_question` | 题目：session_id FK、parent_question_id 自 FK（追问链）、content、question_type、difficulty、target_skill、sequence_no；UNIQUE(session_id, sequence_no) |
| `interview_answer` | 答案：question_id FK（唯一，每题一答）、answer_text LONGTEXT、audio_url、transcript_text、duration_seconds、status（SUBMITTED/EVALUATED/EVALUATION_FAILED）、submitted_at |
| `answer_evaluation` | 评分：answer_id FK（唯一）、total_score+6 维 DECIMAL(5,2)、strengths/weaknesses/suggestions JSON、reference_answer LONGTEXT、model_name |
| `interview_report` | 报告：session_id FK（唯一）、overall_score、dimension_scores JSON、summary、优劣势/建议 JSON |
| `knowledge_document` | 知识文档：job_id FK（可空）、title、file_url、version、status（PROCESSING/UPLOADED/INDEXED/EMBED_FAILED） |
| `knowledge_chunk` | 知识块：document_id FK、chunk_no（与 doc 联合唯一）、content LONGTEXT、embedding_ref、metadata_json JSON |
| `training_recommendation` | 训练建议（P2 预留）：user_id+job_id FK、skill_name、recommendation_type、content、priority、status |

**关键机制：**

- 答案唯一键（`uk_answer_question`）保证幂等
- 报告唯一键（`uk_report_session`）+ delete-then-insert 保证幂等
- 面试状态机 CREATED→IN_PROGRESS→COMPLETED
- question 的 `parent_question_id` 自外键支持动态追问的父子链---

## 七、部署与工程化

### 7.1 Docker Compose（docker-compose.yml，4 服务，ai-net 桥接网络，4 命名卷）

| 服务 | 镜像/构建 | 说明 |
|---|---|---|
| `mysql` | `mysql:8.0` | 建库 ai_interviewer，SQL 初始化脚本挂载到 docker-entrypoint-initdb.d，healthcheck `mysqladmin ping`，端口 3306 |
| `backend` | 构建 `src/backend/Dockerfile` | `SPRING_PROFILES_ACTIVE=prod`，依赖 mysql+ai-service 健康后启动，env `MYSQL_PASSWORD`/`AI_INTERNAL_KEY`/`JWT_SECRET`，healthcheck curl `/api/v1/auth/health`，端口 8081，卷 uploads+logs |
| `ai-service` | 构建 `src/ai-service/Dockerfile` | env `AI_INTERNAL_KEY`/LLM key/embedding 配置/`VECTOR_DB_PATH=/app/data/knowledge.db`，healthcheck python urllib `/health`，端口 8001，卷 vector_data |
| `frontend` | 构建 `src/frontend/Dockerfile`（Nginx:alpine） | 依赖 backend+ai-service，端口 80 |

**启动前置环境变量：** `MYSQL_PASSWORD`、`AI_INTERNAL_KEY`、`JWT_SECRET`。

```bash
export MYSQL_PASSWORD="..." AI_INTERNAL_KEY="..." JWT_SECRET="..."
docker compose up -d --build
```

### 7.2 GitHub Actions CI（.github/workflows/ci.yml，push/PR 到 main/master 触发 3 个 job）

| Job | 运行内容 |
|---|---|
| `backend` | JDK 17 temurin + Maven 缓存 → `mvn -B clean compile` + `mvn -B test` |
| `ai-service` | Python 3.11 + pip 缓存 → `pytest tests/ -v`（向量/RAG + 数学正确性测试） |
| `frontend` | Node 20 + npm 缓存 → `npm ci` + `npm run build`（vue-tsc + vite build） |

### 7.3 Windows 本地启动脚本

| 脚本 | 作用 |
|---|---|
| `start-all.ps1` | 一键启动 AI（python main.py:8001）+ 后端（找 target/*.jar 起 java:8081）+ 前端（npm run dev:5173），sleep 20s 后逐端口检查 LISTENING |
| `start-backend.bat` | `cd src/backend; mvn spring-boot:run` |
| `start-frontend.bat` / `start-fe.bat` | 起前端 dev server |
| `start-ai-service.bat` | `cd src/ai-service; python main.py` |

> 使用 `start-all.ps1` 前需先设 3 个后端环境变量（MYSQL_PASSWORD / AI_INTERNAL_KEY / JWT_SECRET）并准备好 `src/ai-service/.env`。

### 7.4 关键约定与坑（来自 启动指南.md）

1. `JWT_SECRET` 必须 ≥ 32 字符，否则所有 JWT 校验失败（jjwt 要求 256 位以上）
2. `AI_INTERNAL_KEY` 后端与 AI 服务必须一致，否则内部接口 401
3. `application.yml` 强制 `:?` 注入 3 个环境变量，不设后端无法启动
4. 文件上传用 `MultipartFile.transferTo(Path)`，路径必须**绝对路径**（`WebMvcConfig` 映射 `/uploads/**`）
5. MyBatis-Plus 含 JSON 字段的实体必须加 `@TableName(autoResultMap=true)`，否则 List/Map 字段查询为 null
6. 每题限时 300s（前端控制），超时自动记空答并跳下一题
7. 语音识别仅 Chrome/Edge 支持 Web Speech API，其他浏览器回退为"仅存音频"

---

## 八、核心业务流程（端到端）

1. **注册/登录**：用户注册（邮箱唯一 + BCrypt），登录签 JWT（24h），前端存 localStorage，路由守卫拦截未登录
2. **浏览岗位**：分页列活跃岗位 → 详情看技能标签
3. **创建面试**：选岗位 + 难度 + 题数（可选挂简历）→ 后端建 `interview_session`（CREATED）
4. **AI 出题**：每答完一题前端调 `/questions/next` → 后端拼上下文（简历摘要 + 近 5 次总分）→ 调 AI `/question/generate`（含 RAG + 自适应难度）→ 存 `interview_question`。**幂等**：重试复用已有后续题
5. **作答**：文字 textarea 或 Web Speech API 语音（Chrome/Edge 转文字，其他录 webm 音频）；300s 倒计时超时自动提交占位空答
6. **评分**：提交答案 → 后端异步（线程池）取 RAG 上下文 → 调 AI `/answer/evaluate` → 落 `answer_evaluation`（6 维分 + 优劣势 + 结构化参考答案）；全题评完自动触发报告生成。AI 不可达时 Java 端启发式兜底评分
7. **报告**：完成面试跳报告页，按需重新生成 `interview_report`（综合分 + 6 维平均 + 等级摘要 + 去重优劣势建议）+ "参考答案汇总"（每题问题 + 参考答案折叠展示）
8. **趋势/历史**：Dashboard 折线图（最近 10 次能力趋势）；History 分页列表，已完成可查报告
9. **知识库/简历**：上传文档 → 异步 embedding 入库（RAG 检索用）；上传简历 → 异步 AI 解析出结构化 JSON
10. **训练建议（P2 预留）**：`training_recommendation` 表已建，待实现

---

## 九、V1.0 功能完成度清单

| # | 功能 | 状态 |
|---|---|---|
| 1 | 用户注册/登录（JWT） | ✅ |
| 2 | 岗位浏览 | ✅ |
| 3 | 面试配置（类型/难度/题数） | ✅ |
| 4 | AI 出题（LLM + 12 条 mock 兜底 + 自适应难度 + RAG） | ✅ |
| 5 | 文字/语音回答提交（webm 录音 + Web Speech 识别） | ✅ |
| 6 | AI 六维评分（专业/逻辑/完整度/分析/表达/岗位匹配 + 启发式兜底） | ✅ |
| 7 | AI 动态追问（parent_question_id 自 FK 链） | ✅ |
| 8 | 面试状态机（CREATED→IN_PROGRESS→COMPLETED） | ✅ |
| 9 | 面试报告（雷达图 + 优劣势 + 建议 + 参考答案汇总） | ✅ |
| 10 | 历史面试 + 能力趋势（折线图） | ✅ |
| 11 | 知识库 RAG 检索 | ✅（P1） |
| 12 | 语音转文字 Web Speech API | ✅（P1，仅 Chrome/Edge） |
| 13 | 训练推荐 | ⬜（P2，表已建未实现） |
| 14 | Redis 会话锁/缓存 | ⬜（预留未用） |
| 15 | DashScope ASR 语音转写 | ⬜（未接 key） |

---

## 十、给 LLM（ChatGPT）的完善建议输入锚点

请基于以上文档各部分（已标注对应代码位置/文件）给出**可落地**的完善建议，重点关注以下方面：

1. **测试覆盖**：前端无 Vitest/e2e，建议补面试流程 e2e；后端仅单元测试，建议补 controller 层 `@SpringBootTest` 集成测试
2. **可观测性**：当前仅 logback 文件日志，建议补结构化日志（ELK/Loki）、指标监控（Prometheus+Grafana，关键指标：AI 调用延迟/评分耗时/报告生成时长）、告警
3. **RAG 升级**：当前 SQLite+numpy 向量库适合单机 demo，生产建议 pgvector / Milvus / Qdrant，补 chunk 增量更新与失效策略
4. **ASR 接入**：当前前端 Web Speech API 仅浏览器端识别，建议接 DashScope/whisper 后端转写提升精度与离线能力
5. **Redis 落地**：会话锁、答案评分去重、热点报告缓存、限流
6. **并发与限流**：LLM 调用无速率限制/配额，建议加令牌桶限流、队列削峰、降级策略细化
7. **多租户/企业版**：当前单用户视角，建议补招聘方（HR）侧、批量面试、团队协作、岗位模板管理
8. **训练推荐（P2）**：基于 answer_evaluation 的 weaknesses + 知识库 自动生成个性化学习路径
9. **国际化**：文案/提示词当前中文硬编码，建议 i18n 化
10. **安全加固**：上传文件类型/大小校验已有，建议补 CSRF 强化、敏感数据脱敏、审计日志、密钥轮换
11. **成本治理**：LLM 调用量监控、缓存相似问题、降级路由（不同难度走不同价格模型）
12. **数据质量**：referenceAnswer 一致性校验、评分结果离群检测、模型版本回归测试集

> **给 ChatGPT 的使用提示：** 本文档各章节对应代码位置如下，请结合具体文件路径给出建议——
> - 前端：`src/frontend/src/views/*.vue`、`router/index.ts`、`api/*.ts`、`stores/user.ts`
> - 后端：`src/backend/src/main/java/com/aiinterviewer/{controller,service/impl,entity,config,security}`、`resources/application.yml`
> - AI 服务：`src/ai-service/main.py`、`.env.example`
> - 数据库：`AI智能面试官系统_MySQL_V1.0.sql`
> - 部署：`docker-compose.yml`、`.github/workflows/ci.yml`、`start-all.ps1`