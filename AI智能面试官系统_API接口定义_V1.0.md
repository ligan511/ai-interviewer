# AI 智能面试官系统 API 接口定义 V1.0

## 1. 统一约定
- Base URL: `/api/v1`
- Content-Type: `application/json`
- 认证：Bearer Token
- 时间：ISO-8601 / 服务端统一时区
- 成功响应：`200/201`
- 参数错误：`400`
- 未认证：`401`
- 无权限：`403`
- 资源不存在：`404`
- 业务冲突：`409`
- 服务异常：`500`

统一成功结构：
```json
{"code":0,"message":"ok","data":{}}
```

统一错误结构：
```json
{"code":10001,"message":"invalid request","data":null}
```

## 2. API 清单
| 模块 | 方法 | Path | 优先级 |
|---|---|---|---|
| Auth | POST | `/auth/register` | P0 |
| Auth | POST | `/auth/login` | P0 |
| Jobs | GET | `/jobs` | P0 |
| Jobs | GET | `/jobs/{id}` | P0 |
| Interviews | POST | `/interviews` | P0 |
| Interviews | GET | `/interviews/{id}` | P0 |
| Questions | POST | `/interviews/{id}/questions/next` | P0 |
| Answers | POST | `/interviews/{id}/answers` | P0 |
| Evaluation | GET | `/answers/{id}/evaluation` | P0 |
| Interviews | POST | `/interviews/{id}/complete` | P0 |
| Reports | GET | `/interviews/{id}/report` | P0 |
| History | GET | `/interviews/history` | P0 |
| Resume | POST | `/resumes` | P1 |
| Resume | GET | `/resumes/{id}` | P1 |
| Voice | POST | `/interviews/{id}/answers/audio` | P1 |
| Knowledge | POST | `/knowledge/documents` | P1 |
| Recommendations | GET | `/recommendations` | P2 |

## 3. 关键接口

### 3.1 创建面试
`POST /interviews`

Request:
```json
{
  "jobId": 1,
  "resumeId": 12,
  "type": "technical",
  "difficulty": "medium",
  "questionLimit": 10,
  "durationLimitSeconds": 1800
}
```

Response:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sessionId": 10001,
    "status": "CREATED"
  }
}
```

### 3.2 生成下一题
`POST /interviews/{sessionId}/questions/next`

Response:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "questionId": 20001,
    "parentQuestionId": null,
    "content": "请介绍一下 Redis 的缓存机制。",
    "type": "technical",
    "difficulty": "medium",
    "targetSkill": "Redis",
    "reason": "考察缓存基础与项目应用能力"
  }
}
```

### 3.3 提交文字回答
`POST /interviews/{sessionId}/answers`

Request:
```json
{
  "questionId": 20001,
  "answerText": "Redis 是一种内存型键值数据库……",
  "clientDurationSeconds": 95
}
```

Response:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "answerId": 30001,
    "evaluationStatus": "PROCESSING",
    "nextAction": "FOLLOW_UP"
  }
}
```

### 3.4 单题评价
`GET /answers/{answerId}/evaluation`

Response:
```json
{
  "code": 0,
  "data": {
    "totalScore": 82,
    "professionalScore": 88,
    "logicScore": 80,
    "completenessScore": 78,
    "analysisScore": 82,
    "expressionScore": 76,
    "jobMatchScore": 86,
    "strengths": ["基础知识准确"],
    "weaknesses": ["缺少高并发场景细节"],
    "suggestions": ["补充缓存击穿、穿透、雪崩处理方案"],
    "referenceAnswer": "……"
  }
}
```

### 3.5 结束面试
`POST /interviews/{sessionId}/complete`

Response:
```json
{
  "code": 0,
  "data": {
    "sessionId": 10001,
    "reportId": 50001,
    "status": "COMPLETED",
    "overallScore": 82
  }
}
```

## 4. AI 内部服务 API

建议 AI 服务使用 Python/FastAPI，通过内部网络调用，不直接暴露给浏览器。

- `POST /internal/ai/question/generate`
- `POST /internal/ai/question/followup`
- `POST /internal/ai/answer/evaluate`
- `POST /internal/ai/report/generate`
- `POST /internal/ai/resume/parse`

AI 输出必须为可解析 JSON，服务端先做 schema 校验，再写入数据库。

## 5. 幂等与安全
- `POST /answers` 建议使用 `Idempotency-Key` 防止重复提交。
- 完成面试接口必须保证幂等，多次调用返回同一个 reportId。
- 用户 ID 从 Token 获取，不接受客户端直接传入作为权限依据。
- 文件上传先进入对象存储，再异步解析；不要把原始文件二进制写入 MySQL。
- AI Prompt、模型名称、token 使用量建议记录到独立审计表（后续 P2）。
