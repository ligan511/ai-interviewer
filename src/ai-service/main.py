from fastapi import FastAPI, HTTPException, Request, UploadFile, File
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, model_validator
from typing import Optional, List, Dict, Any
from starlette.middleware.base import BaseHTTPMiddleware
import json
import os
import hashlib
import numpy as np
from dotenv import load_dotenv
load_dotenv()

app = FastAPI(title="AI Interviewer Service", version="2.0.0")

# ── Configuration ──────────────────────────────────────────────────────────
# 优先使用 Agnes AI，未配置时降级到 SiliconFlow，均无则使用 mock
AGNES_API_KEY     = os.getenv("AGNES_API_KEY",       "")
SILICONFLOW_API_KEY = os.getenv("SILICONFLOW_API_KEY", "")
LLM_API_KEY       = AGNES_API_KEY or SILICONFLOW_API_KEY
LLM_API_URL       = os.getenv("LLM_API_URL",        "https://apihub.agnes-ai.com/v1/chat/completions")
MODEL_NAME        = os.getenv("MODEL_NAME",         "agnes-2.5-flash")
EMBEDDING_MODEL   = os.getenv("EMBEDDING_MODEL",    "BAAI/bge-m3")
EMBEDDING_API_URL = os.getenv("EMBEDDING_API_URL",  "https://api.siliconflow.cn/v1/embeddings")
DASHSCOPE_KEY     = os.getenv("DASHSCOPE_API_KEY",  "")

# ── 内部接口鉴权 Key ──────────────────────────────────────────────────────
# 后端调用 /internal/ai/* 接口需在请求头携带 X-AI-Internal-Key
AI_INTERNAL_KEY   = os.getenv("AI_INTERNAL_KEY",    "")
APP_ENV          = os.getenv("APP_ENV", "development")

# 生产环境必须配置 AI_INTERNAL_KEY，否则 /internal/ai/* 完全裸露
if APP_ENV == "production" and not AI_INTERNAL_KEY:
    raise RuntimeError(
        "生产环境（APP_ENV=production）必须配置 AI_INTERNAL_KEY，"
        "否则 /internal/ai/* 内部接口将无鉴权保护"
    )

# 启动日志：打印 key 配置状态，便于排查 401 问题
print(f"[auth] AI_INTERNAL_KEY configured: {bool(AI_INTERNAL_KEY)}")
if not AI_INTERNAL_KEY:
    print("[auth] WARNING: AI_INTERNAL_KEY 未配置，/internal/ai/* 处于无鉴权状态（仅开发模式安全）")


# ── 鉴权中间件：保护 /internal/ai/* 接口 ─────────────────────────────────
# 当 AI_INTERNAL_KEY 未配置（空字符串）时，中间件不做鉴权（仅适用于本地开发）
# 生产环境必须配置 AI_INTERNAL_KEY，否则 /internal/ai/* 接口无保护
@app.middleware("http")
async def verify_internal_key(request: Request, call_next):
    # 仅保护内部接口，其他路径放行（健康检查等）
    if request.url.path.startswith("/internal/ai/"):
        if AI_INTERNAL_KEY:
            provided_key = request.headers.get("X-AI-Internal-Key", "")
            if not provided_key or provided_key != AI_INTERNAL_KEY:
                return JSONResponse(
                    status_code=401,
                    content={"detail": "无效的内部接口凭据"}
                )
        # else: key 未配置（开发模式）跳过鉴权
    response = await call_next(request)
    return response

# ── In-memory storage ─────────────────────────────────────────────────────
question_history: Dict[int, List[Dict]] = {}

# ── 向量库持久化（SQLite + numpy）─────────────────────────────────────
# 将文档 embedding 持久化到本地 SQLite，避免重启即丢。
# 检索时一次性加载到内存（百条级别足够），新写入时同步落盘。
import sqlite3
import json as _json
import os as _os

_VECTOR_DB_PATH = _os.getenv("VECTOR_DB_PATH", "./data/knowledge.db")
_os.makedirs(_os.path.dirname(_VECTOR_DB_PATH) or ".", exist_ok=True)


def _vector_db_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(_VECTOR_DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS knowledge_chunk (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            doc_id INTEGER NOT NULL,
            chunk TEXT NOT NULL,
            embedding TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """
    )
    conn.commit()
    return conn


def _load_all_chunks() -> List[Dict[str, Any]]:
    """启动时/按需从 SQLite 加载所有 chunks 到内存"""
    conn = _vector_db_conn()
    try:
        rows = conn.execute(
            "SELECT doc_id, chunk, embedding FROM knowledge_chunk"
        ).fetchall()
    finally:
        conn.close()
    result = []
    for doc_id, chunk, emb_json in rows:
        try:
            result.append({"doc_id": doc_id, "chunk": chunk, "embedding": _json.loads(emb_json)})
        except Exception:
            continue
    return result


# 进程级内存缓存（启动时加载，写操作同步落盘）
knowledge_index: List[Dict[str, Any]] = _load_all_chunks()

# ── LLM helper ─────────────────────────────────────────────────────────────
def _parse_llm_json(text: str) -> dict | None:
    """从 LLM 返回文本中提取 JSON，兼容 ```json 包裹格式"""
    if not text:
        return None
    cleaned = text.strip()
    # 去掉开头的 ```json 或 ``` 包裹
    if cleaned.startswith("```"):
        cleaned = cleaned[3:]
        if cleaned.startswith("json"):
            cleaned = cleaned[4:]
    # 去掉结尾的 ``` 包裹（如有）
    if cleaned.endswith("```"):
        cleaned = cleaned[:-3]
    cleaned = cleaned.strip()
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        return None

def call_llm(messages: list, temperature: float = 0.7, timeout: int = 90) -> str:
    if not LLM_API_KEY:
        return ""
    try:
        import httpx
        resp = httpx.post(
            LLM_API_URL,
            headers={"Authorization": f"Bearer {LLM_API_KEY}", "Content-Type": "application/json"},
            json={"model": MODEL_NAME, "messages": messages, "temperature": temperature},
            timeout=timeout,
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"]
    except httpx.TimeoutException as e:
        print(f"[LLM timeout] {e}")
        return ""
    except Exception as e:
        print(f"[LLM error] {e}")
        return ""

def call_embedding(text: str) -> List[float]:
    if not LLM_API_KEY:
        return [0.0] * 8
    try:
        import httpx
        resp = httpx.post(
            EMBEDDING_API_URL,
            headers={"Authorization": f"Bearer {LLM_API_KEY}", "Content-Type": "application/json"},
            json={"model": EMBEDDING_MODEL, "input": text},
            timeout=30,
        )
        resp.raise_for_status()
        return resp.json()["data"][0]["embedding"]
    except Exception as e:
        print(f"[Embedding error] {e}")
        return [0.0] * 8

def cosine_similarity(a: List[float], b: List[float]) -> float:
    if not a or not b:
        return 0.0
    va, vb = np.array(a, dtype=float), np.array(b, dtype=float)
    na, nb = np.linalg.norm(va), np.linalg.norm(vb)
    return float(np.dot(va, vb) / (na * nb + 1e-9))

# ── Mock question pools (fallback when LLM unavailable) ────────────────────
MOCK_QUESTIONS: Dict[str, List[str]] = {
    "Java后端": [
        "请介绍一下 Redis 的缓存机制以及常见缓存问题。",
        "HashMap 在 JDK 8 中做了哪些优化？红黑树的作用是什么？",
        "请解释 Spring Bean 的生命周期。",
        "MySQL 索引失效的场景有哪些？",
        "请描述一次你解决的高并发问题。",
        "Spring 事务传播机制有哪些？",
        "JWT 和 Session 认证的优缺点各是什么？",
        "微服务架构中如何进行分布式事务处理？",
        "Java 中 HashMap 与 ConcurrentHashMap 的区别是什么？",
        "什么是 JVM 内存模型？请解释堆、栈、方法区的区别。",
        "请解释 Spring AOP 的实现原理及其应用场景。",
        "MySQL 如何实现分页查询？深度分页如何优化？",
        "Java 中的强引用、软引用、弱引用、虚引用有什么区别？",
        "请描述 Spring 缓存注解 @Cacheable 的工作原理。",
        "如何处理 MySQL 的慢查询？请列举常见的优化手段。",
        "请解释 Java 中的 ThreadLocal 及其使用场景。",
        "Netty 与传统 NIO 相比有什么优势？请描述 Netty 的线程模型。",
        "Java 中如何设计一个线程安全的单例模式？有几种写法？",
        "请解释 MySQL 的 MVCC 机制及其实现原理。",
        "Spring Boot 自动装配的原理是什么？如何自定义 starter？",
    ],
    "Python开发": [
        "请介绍 Python GIL 的原理及其影响。",
        "Django 和 FastAPI 的适用场景分别是什么？",
        "Python 装饰器和上下文管理器如何使用？",
        "如何处理大数据量的 Pandas 操作性能问题？",
        "请解释 Python 的 GIL 和多线程的关系。",
        "Python 的生成器和迭代器有什么区别？",
        "请解释 async/await 在 Python 中的工作原理。",
        "Python 中如何实现单例模式？有哪些方法？",
        "请介绍 Python 的内存管理机制，特别是垃圾回收。",
        "Django ORM 的 N+1 问题是什么？如何避免？",
        "Python 中 __new__ 和 __init__ 的区别是什么？",
        "请解释 Python 的多进程与多线程的使用场景。",
        "FastAPI 中如何使用依赖注入？请举例说明。",
        "Python 中如何做性能 profiling？有哪些工具？",
        "请介绍 Python 中的 metaclass 及其应用场景。",
    ],
    "前端开发": [
        "Vue 3 Composition API 与 Options API 的区别是什么？",
        "请解释 React Diff 算法的核心思想。",
        "前端性能优化有哪些常用手段？",
        "TypeScript 中 interface 和 type 有什么区别？",
        "请描述浏览器从输入 URL 到页面渲染的完整过程。",
        "前端路由如何实现？Hash 路由和 History 路由有什么区别？",
        "请解释 JavaScript 的事件循环（Event Loop）机制。",
        "Vue 3 的响应式原理是什么？Proxy 相比 Object.defineProperty 有哪些改进？",
        "请描述前端雪碧图、懒加载、代码分割等性能优化手段。",
        "React 的 hooks 规则是什么？为什么不能在条件语句中调用 hooks？",
        "请解释跨域问题的成因及常见解决方案。",
        "前端安全攻击有哪些？XSS 和 CSRF 有什么区别？如何防护？",
        "JavaScript 中闭包是什么？请举例说明其应用场景。",
        "Webpack 或 Vite 的打包原理是什么？Tree Shaking 是如何实现的？",
        "请描述前端组件通信的常见方式及适用场景。",
    ],
    "default": [
        "请介绍一下你自己，以及你的职业目标。",
        "你在团队合作中通常扮演什么角色？",
        "请描述一个你遇到的技术挑战及解决方案。",
        "你对这个岗位的理解是什么？",
        "你平时如何保持技术学习的习惯？",
        "请描述一次你在团队中产生分歧并最终解决的完整过程。",
        "你未来 3 年的职业规划是什么？",
        "你在工作中如何衡量一个功能的优先级？",
        "请分享一个你失败的项目经历，以及从中学到了什么。",
        "你更倾向于独立完成还是团队协作？为什么？",
    ],
}

SKILL_KEYWORDS: Dict[str, List[str]] = {
    "Redis":     ["Redis", "缓存", "缓存穿透", "缓存击穿", "缓存雪崩"],
    "Java集合":  ["HashMap", "红黑树", "ConcurrentHashMap", "数据结构", "线程安全"],
    "Spring":    ["Spring", "Bean", "IOC", "AOP", "事务", "自动装配"],
    "MySQL":     ["MySQL", "索引", "SQL", "事务隔离", "锁", "MVCC", "慢查询"],
    "多线程":    ["线程", "并发", "Lock", "线程池", "GIL", "ThreadLocal", "Netty"],
    "项目经验":  ["项目", "经历", "负责", "实现", "架构", "困难", "挑战"],
    "Python基础":["Python", "装饰器", "生成器", "GIL", "async", "Pandas"],
    "前端基础":  ["Vue", "React", "TypeScript", "事件循环", "闭包", "跨域", "前端路由"],
}


# ── Request / Response Models ──────────────────────────────────────────────
class QuestionGenerateRequest(BaseModel):
    sessionId: int
    userId: int
    jobId: int
    jobName: Optional[str] = None
    resumeId: Optional[int]
    resumeContext: Optional[str] = None
    interviewType: str
    difficulty: str
    currentCount: int
    lastQuestionId: Optional[int]
    recentScores: Optional[List[float]] = None

class EvaluateRequest(BaseModel):
    question: str
    answer: str
    jobId: int
    difficulty: str
    resumeContext: Optional[str] = None
    knowledgeContext: Optional[str] = None

class ResumeParseRequest(BaseModel):
    file_content: str          # base64 encoded or raw text
    file_type: str = "text"

class KnowledgeRetrieveRequest(BaseModel):
    question: str
    answer: str
    job_skills: List[str]

class KnowledgeEmbedRequest(BaseModel):
    content: str
    doc_id: int = 0
    chunk_size: int = 500

class VoiceTranscribeRequest(BaseModel):
    audio_url: str


# ── Evaluation Result 校验模型（数据质量 P0）────────────────────────────
# 用于校验 LLM 输出的评分结构，越界分数 / 空参考答案会被拒绝并走 fallback。
class EvaluationResult(BaseModel):
    totalScore: float = Field(..., ge=0, le=100)
    professionalScore: float = Field(..., ge=0, le=100)
    logicScore: float = Field(..., ge=0, le=100)
    completenessScore: float = Field(..., ge=0, le=100)
    analysisScore: float = Field(..., ge=0, le=100)
    expressionScore: float = Field(..., ge=0, le=100)
    jobMatchScore: float = Field(..., ge=0, le=100)
    strengths: List[str] = Field(default_factory=list)
    weaknesses: List[str] = Field(default_factory=list)
    suggestions: List[str] = Field(default_factory=list)
    referenceAnswer: str = Field(..., min_length=50)


def build_fallback_evaluation(req: EvaluateRequest, llm_failed: bool = False) -> dict:
    """构建启发式评分兜底结果（始终返回合法 EvaluationResult 结构）。"""
    answer_len = len(req.answer)
    has_keywords = any(kw in req.answer for kw in ["例如", "比如", "因为", "所以", "首先", "其次", "第一", "第二"])
    base_score = min(100, max(40, 50 + answer_len // 20))
    if has_keywords: base_score += 8
    if answer_len > 200: base_score += 5
    if answer_len > 500: base_score += 5

    ps = min(100, base_score + 3)
    ls = min(100, base_score + (2 if has_keywords else -5))
    cs = min(100, base_score + (5 if answer_len > 100 else -5))
    ans = min(100, base_score)
    es = min(100, base_score - 2)
    jm = min(100, base_score + 2)
    total = ps*0.30 + ls*0.20 + cs*0.15 + ans*0.15 + es*0.10 + jm*0.10

    strengths, weaknesses, suggestions = [], [], []
    if llm_failed:
        weaknesses.append("AI 评分服务暂不可用，以下为系统启发式兜底评分")
        suggestions.append("请在 AI 服务恢复后重新提交答案以获取模型评分")
    if answer_len > 200:
        strengths.append("回答内容较充实")
    else:
        weaknesses.append("回答内容偏简短")
        suggestions.append("建议增加更多具体细节和案例支撑")
    if has_keywords:
        strengths.append("逻辑结构较为清晰")
    else:
        weaknesses.append("回答逻辑性有待提升")
        suggestions.append("建议使用 STAR 法则组织回答结构")

    result = EvaluationResult(
        totalScore=round(total),
        professionalScore=round(ps),
        logicScore=round(ls),
        completenessScore=round(cs),
        analysisScore=round(ans),
        expressionScore=round(es),
        jobMatchScore=round(jm),
        strengths=strengths,
        weaknesses=weaknesses,
        suggestions=suggestions,
        referenceAnswer=(
            f"要点1：先界定问题中的核心概念与边界，说明适用场景。\n"
            f"要点2：给出解决思路或步骤（结合 {req.difficulty} 难度应有的深度）。\n"
            f"要点3：补充关键细节、常见坑点或最佳实践。\n"
            f"要点4：结合岗位技能说明落地方式与可量化的结果。\n"
            f"评分参考：回答是否结构清晰、是否结合实例、深度是否匹配难度。"
        ),
    )
    return result.model_dump()


# ── Endpoints ───────────────────────────────────────────────────────────────

@app.get("/health")
async def health():
    """健康检查接口，供 Docker / K8s healthcheck 使用，无需鉴权"""
    return {"status": "ok", "service": "ai-interviewer-ai-service"}


@app.post("/internal/ai/question/generate")
async def generate_question(req: QuestionGenerateRequest):
    """AI 自动生成面试题（支持简历上下文 + RAG + 难度动态调整）"""
    # 1. 难度动态调整
    final_difficulty = req.difficulty
    if req.recentScores and len(req.recentScores) >= 2:
        avg = sum(req.recentScores) / len(req.recentScores)
        last_two = req.recentScores[-2:]
        if all(s >= 85 for s in last_two):
            diff_map = {"junior": "medium", "medium": "senior", "senior": "senior"}
            final_difficulty = diff_map.get(final_difficulty, "senior")
        elif all(s <= 45 for s in last_two):
            diff_map = {"senior": "medium", "medium": "junior", "junior": "junior"}
            final_difficulty = diff_map.get(final_difficulty, "junior")

    # 2. 获取岗位知识库上下文（用 resumeContext 做检索关键词，无需 question 字段）
    kb_context = ""
    if req.jobId:
        embed = call_embedding(req.resumeContext or "技术面试")
        if knowledge_index:
            scored = [(cosine_similarity(embed, c["embedding"]), c) for c in knowledge_index]
            scored.sort(key=lambda x: x[0], reverse=True)
            top_chunks = [s[1]["content"] for s in scored[:2] if s[0] > 0.3]
            kb_context = "\n".join(top_chunks)

    # 3. 根据简历上下文识别技能方向
    job_skill_text = ""
    resume_text = req.resumeContext or ""
    for skill, keywords in SKILL_KEYWORDS.items():
        if any(k in resume_text for k in keywords):
            job_skill_text = skill
            break

    system_prompt = f"""你是一个专业的技术面试官。请根据以下信息生成一道面试题目。

岗位名称：{req.jobName or "未知"}
岗位相关知识库片段（RAG检索结果）：
{kb_context or "（暂无）"}

用户简历摘要：
{req.resumeContext or "（用户未上传简历）"}

面试类型：{req.interviewType}
当前难度：{final_difficulty}（已根据历史表现动态调整）
已答题数：{req.currentCount}

要求：
1. 题目要专业、有针对性，与岗位核心技能相关
2. 如果是中文岗位，用中文出题；英文岗位用英文出题
3. 题目要能考察候选人的深度理解，不是简单背诵
4. 如果用户有简历，可以结合简历内容出题（如"你简历中提到..."）
5. 输出严格为 JSON，格式：{{"content":"题目内容","type":"technical","difficulty":"{final_difficulty}","targetSkill":"技能名称"}}
"""
    user_msg = f"当前问题上下文：上次问题ID={req.lastQuestionId}，请生成下一道题目。"

    llm_result = call_llm([
        {"role": "system", "content": system_prompt},
        {"role": "user",   "content": user_msg},
    ])

    if llm_result:
        try:
            parsed = _parse_llm_json(llm_result)
            if parsed:
                return JSONResponse(content={
                    "content":    parsed.get("content", ""),
                    "type":       parsed.get("type", req.interviewType),
                    "difficulty": parsed.get("difficulty", final_difficulty),
                    "targetSkill": parsed.get("targetSkill", job_skill_text or "通用技术"),
                })
        except json.JSONDecodeError:
            pass

    # Fallback: mock pool
    pool_key = "default"
    clean_job_name = req.jobName.replace(" ", "") if req.jobName else ""
    for key in MOCK_QUESTIONS:
        if key in clean_job_name or clean_job_name in key:
            pool_key = key
            break
    pool = MOCK_QUESTIONS.get(pool_key, MOCK_QUESTIONS["default"])
    idx = req.currentCount % len(pool)
    return JSONResponse(content={
        "content": pool[idx],
        "type": req.interviewType,
        "difficulty": final_difficulty,
        "targetSkill": job_skill_text or "通用技术",
    })


@app.post("/internal/ai/question/followup")
async def followup_question(req: dict):
    """AI 动态追问"""
    last_q = req.get("lastQuestion", "")
    user_ans = req.get("answer", "")
    skill = req.get("targetSkill", "通用")
    context = f"""上一题：{last_q}
用户回答：{user_ans}
考察技能：{skill}

请根据用户回答质量生成一道追问：
- 回答优秀 → 深入追问原理或扩展场景
- 回答一般 → 换角度再问或要求举例
- 回答较差 → 降低难度，问基础概念

输出 JSON：{{"content":"追问内容","type":"followup","difficulty":"{req.get('difficulty','medium')}","targetSkill":"{skill}"}}
"""
    llm_result = call_llm([
        {"role": "system", "content": "你是面试官，请根据候选人回答进行追问。"},
        {"role": "user",   "content": context},
    ])
    if llm_result:
        try:
            parsed = _parse_llm_json(llm_result)
            if parsed:
                return JSONResponse(content=parsed)
        except json.JSONDecodeError:
            pass
    return JSONResponse(content={
        "content": "请结合实际项目经验，详细说明你在上述场景中遇到的具体问题和解决方案。",
        "type": "followup",
        "difficulty": req.get("difficulty", "medium"),
        "targetSkill": req.get("targetSkill", "通用"),
    })


@app.post("/internal/ai/answer/evaluate")
async def evaluate_answer(req: EvaluateRequest):
    """AI 多维度评分（支持简历+知识库上下文）"""
    context = f"""请对以下面试回答进行多维度评分（0-100分）。

问题：{req.question}
回答：{req.answer}
岗位技能：（JobId={req.jobId}）
回答难度：{req.difficulty}
"""
    if req.resumeContext:
        context += f"\n用户简历：{req.resumeContext}"
    if req.knowledgeContext:
        context += f"\n参考知识点：{req.knowledgeContext}"

    prompt = f"""{context}

请从以下6个维度评分并给出评价：
1. 专业能力（30%）：技术知识掌握程度
2. 逻辑能力（20%）：思路是否清晰有条理
3. 回答完整度（15%）：是否覆盖了问题的关键点
4. 问题分析能力（15%）：分析问题的深度
5. 表达能力（10%）：语言组织是否流畅
6. 岗位匹配度（10%）：回答是否符合岗位要求

【重要】referenceAnswer 是给候选人学习用的"标准答案"，必须满足：
- 针对该问题给出详实、可直接对照学习的标准回答（不是复述用户答案，也不是空话套话）
- 采用分点结构：以"要点N："开头分 3-5 个要点，每个要点后附一句解释
- 贴合岗位技能与难度水平，体现该岗位期望的答案深度
- 总长度约 200-400 字
- 结尾用"评分参考："给出 1-2 句提示，说明什么样的回答能拿高分

输出严格 JSON，格式：
{{
  "totalScore": 总分(整数0-100),
  "professionalScore": 专业能力分,
  "logicScore": 逻辑能力分,
  "completenessScore": 回答完整度分,
  "analysisScore": 问题分析能力分,
  "expressionScore": 表达能力分,
  "jobMatchScore": 岗位匹配度分,
  "strengths": ["优点1", "优点2"],
  "weaknesses": ["不足1", "不足2"],
  "suggestions": ["建议1", "建议2"],
  "referenceAnswer": "要点1：...\n要点2：...\n要点3：...\n评分参考：..."
}}
"""
    llm_result = call_llm([
        {"role": "system", "content": "你是专业面试官，请公正评分。referenceAnswer 是供候选人学习的标准答案，必须详实、结构化、可直接对照，禁止输出占位符。"},
        {"role": "user",   "content": prompt},
    ])
    print(f"[evaluate] LLM call returned: {len(llm_result) if llm_result else 0} chars; "
          f"LLM_API_KEY set={bool(LLM_API_KEY)}, model={MODEL_NAME}")
    if llm_result:
        try:
            parsed = _parse_llm_json(llm_result)
            print(f"[evaluate] parsed totalScore={parsed.get('totalScore') if parsed else 'N/A'}")
            if parsed:
                # P0 数据质量：用 Pydantic 校验 LLM 输出，越界分数/空参考答案被拒
                try:
                    validated = EvaluationResult.model_validate(parsed)
                    # 防御：LLM 可能返回 totalScore=0 但维度分有值（或反之），
                    # 若 totalScore 为 0 但任一维度分 >0，按维度分加权和重算 totalScore，避免评分失效
                    dim_scores = [validated.professionalScore, validated.logicScore,
                                  validated.completenessScore, validated.analysisScore,
                                  validated.expressionScore, validated.jobMatchScore]
                    if validated.totalScore == 0 and any(s > 0 for s in dim_scores):
                        weighted = (validated.professionalScore*0.30 + validated.logicScore*0.20
                                    + validated.completenessScore*0.15 + validated.analysisScore*0.15
                                    + validated.expressionScore*0.10 + validated.jobMatchScore*0.10)
                        print(f"[evaluate] totalScore=0 but dims have values, recalculating to {round(weighted)}")
                        validated.totalScore = round(weighted)
                    # 防御：若 LLM 返回 0 分但 referenceAnswer 非空（即正常评分），保留维度分
                    print(f"[evaluate] VALID LLM success: totalScore={validated.totalScore}")
                    return JSONResponse(content=validated.model_dump())
                except Exception as e:
                    print(f"[evaluate] LLM output validation failed, using fallback: {e}")
            else:
                print(f"[evaluate] _parse_llm_json returned None. LLM raw head: {llm_result[:200]!r}")
        except json.JSONDecodeError:
            pass

    # Fallback scoring（始终返回合法 EvaluationResult 结构）
    print(f"[evaluate] FALLBACK triggered (llm_failed={not bool(llm_result)})")
    return JSONResponse(content=build_fallback_evaluation(req, llm_failed=True))


@app.post("/internal/ai/report/generate")
async def generate_report(req: dict):
    """生成综合面试报告"""
    questions = req.get("questions", [])
    evaluations = req.get("evaluations", [])
    total = sum(e.get("totalScore", 0) for e in evaluations) / max(len(evaluations), 1)
    level = "优秀" if total >= 85 else "良好" if total >= 70 else "及格" if total >= 60 else "待提高"
    summary = f"本次面试共回答 {len(questions)} 道题，综合得分 {round(total)} 分，整体表现{level}。"
    strengths = ["基础知识回答准确", "能够结合项目经验"]
    weaknesses = ["高并发场景细节不足", "表达可更结构化"]
    suggestions = ["建议重点复习高频考点", "使用 STAR 方法优化项目描述"]
    return JSONResponse(content={
        "summary": summary, "strengths": strengths,
        "weaknesses": weaknesses, "suggestions": suggestions,
    })


@app.post("/internal/ai/resume/parse")
async def parse_resume(req: ResumeParseRequest):
    """简历解析（P1）→ 返回结构化 JSON"""
    prompt = f"""请解析以下简历内容，提取关键信息，输出严格 JSON：
{{
  "education": [{{"degree":"学位","school":"学校","major":"专业","year":"年份"}}],
  "experience": [{{"company":"公司","position":"职位","duration":"时长","description":"简述"}}],
  "skills": ["技能1","技能2","技能3"],
  "projects": [{{"name":"项目名","role":"角色","tech":"技术栈"}}],
  "summary": "一句话个人简介"
}}

简历内容：
{req.file_content[:2000]}
"""
    llm_result = call_llm([
        {"role": "system", "content": "你是简历解析专家，请准确提取信息。"},
        {"role": "user",   "content": prompt},
    ])
    if llm_result:
        try:
            parsed = _parse_llm_json(llm_result)
            if parsed:
                return JSONResponse(content=parsed)
        except json.JSONDecodeError:
            pass

    return JSONResponse(content={
        "education": [{"degree": "本科", "school": "示例大学", "major": "计算机科学"}],
        "experience": [{"company": "示例公司", "position": "开发工程师", "duration": "2年"}],
        "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
        "projects": [{"name": "示例项目", "role": "核心开发"}],
        "summary": "示例简历解析结果",
    })


@app.post("/internal/ai/knowledge/retrieve")
async def retrieve_knowledge(req: KnowledgeRetrieveRequest):
    """RAG 知识检索（P1）"""
    if not knowledge_index:
        return JSONResponse(content={"chunks": [], "score": 0.0})
    embed = call_embedding(f"{req.question} {req.answer}")
    scored = [(cosine_similarity(embed, c["embedding"]), c) for c in knowledge_index]
    scored.sort(key=lambda x: x[0], reverse=True)
    top = [s[1] for s in scored[:3] if s[0] > 0.3]
    return JSONResponse(content={"chunks": top, "score": scored[0][0] if scored else 0.0})


@app.post("/internal/ai/knowledge/embed")
async def embed_knowledge(req: KnowledgeEmbedRequest):
    """文档 Embedding：切片、生成向量、落盘到 SQLite + 内存索引"""
    if not req.content or not req.content.strip():
        return JSONResponse(content={"status": "error", "message": "content is empty"}, status_code=400)

    # 按字符切片（简单稳健，对中文友好）
    text = req.content.strip()
    chunks = [text[i:i + req.chunk_size] for i in range(0, len(text), req.chunk_size)]
    if not chunks:
        chunks = [text]

    conn = _vector_db_conn()
    inserted = 0
    try:
        for chunk in chunks:
            embedding = call_embedding(chunk)
            if not embedding:
                continue
            conn.execute(
                "INSERT INTO knowledge_chunk (doc_id, chunk, embedding) VALUES (?, ?, ?)",
                (req.doc_id, chunk, _json.dumps(embedding)),
            )
            knowledge_index.append({"doc_id": req.doc_id, "chunk": chunk, "embedding": embedding})
            inserted += 1
        conn.commit()
    finally:
        conn.close()
    return JSONResponse(content={
        "status": "success",
        "chunks_inserted": inserted,
        "total_in_index": len(knowledge_index),
    })


@app.post("/internal/ai/voice/transcribe")
async def transcribe_voice(req: VoiceTranscribeRequest):
    """语音转文字（P1，阿里云 DashScope ASR）"""
    if not DASHSCOPE_KEY:
        return JSONResponse(content={
            "text": "（语音识别功能暂未配置 API Key，请使用文字回答）",
            "status": "not_configured",
        })
    try:
        import httpx
        resp = httpx.post(
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/audio-generation/asr",
            headers={"Authorization": f"Bearer {DASHSCOPE_KEY}", "Content-Type": "application/json"},
            json={"model": "paraformer-realtime-v2", "audio": {"url": req.audio_url}},
            timeout=60,
        )
        resp.raise_for_status()
        result = resp.json()
        text = result.get("output", {}).get("text", "")
        return JSONResponse(content={"text": text, "status": "success"})
    except Exception as e:
        print(f"[ASR error] {e}")
        return JSONResponse(content={
            "text": f"（语音识别出错: {str(e)[:80]}，请使用文字回答）",
            "status": "error",
        })


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
