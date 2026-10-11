<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { interviewApi } from '@/api/interview'

const router = useRouter()
const route = useRoute()
const sessionId = Number(route.params.sessionId)

const questions = ref<any[]>([])
const answers = ref<Map<number, any>>(new Map())
const currentQuestionIndex = ref(0)
const answerText = ref('')
const currentEvaluation = ref<any>(null)
const refExpanded = ref<string[]>(['ref'])
const submitting = ref(false)
const questionLoadError = ref(false)
const questionLoading = ref(false)
const questionErrorType = ref('')
const timerRef = ref<ReturnType<typeof setInterval> | null>(null)
const answerStartTime = ref(0)
const totalQuestions = ref(10)
const sessionInfo = ref<any>(null)
const finished = ref(false)

// 难度中英文映射
const difficultyLabel: Record<string, string> = {
  junior: '初级', medium: '中级', senior: '高级'
}
function diffLabel(d?: string) {
  return (d && difficultyLabel[d]) || d || ''
}

// ── 每题倒计时（统一 300s，到时自动记空答并跳下一题）──────────────────
const questionTimer = ref(300)
const TIMER_TOTAL = 300
let questionTimerInterval: ReturnType<typeof setInterval> | null = null

function resetQuestionTimer() {
  if (questionTimerInterval) clearInterval(questionTimerInterval)
  questionTimer.value = TIMER_TOTAL
  questionTimerInterval = setInterval(async () => {
    if (submitting.value) return
    questionTimer.value--
    if (questionTimer.value <= 0) {
      if (questionTimerInterval) clearInterval(questionTimerInterval)
      questionTimerInterval = null
      await onQuestionTimeout()
    }
  }, 1000)
}

// 超时：无论是否有文字都记一条空答（空答占位），再跳下一题
async function onQuestionTimeout() {
  if (isAllDone.value) return
  const qId = currentQuestion.value?.questionId
  if (!qId) {
    const loaded = await loadNextQuestion()
    if (loaded) {
      currentQuestionIndex.value++
      resetQuestionTimer()
    } else {
      questionLoadError.value = true
      questionErrorType.value = 'llm'
    }
    return
  }
  // 提交空答占位（带标记，便于后端/报告识别）
  try {
    await interviewApi.submitAnswer(sessionId, {
      questionId: qId,
      answerText: '（本题超时未作答）',
      clientDurationSeconds: TIMER_TOTAL,
    })
    answers.value.set(qId, { answerId: 0, status: 'TIMEOUT' })
  } catch (e) { /* 忽略超时提交失败 */ }
  // 进入下一题
  if (answeredCount.value < totalQuestions.value) {
    questionLoadError.value = false
    questionErrorType.value = ''
    questionLoading.value = true
    const loaded = await loadNextQuestion()
    questionLoading.value = false
    if (loaded) {
      currentQuestionIndex.value++
      resetQuestionTimer()
    } else {
      questionLoadError.value = true
      questionErrorType.value = 'llm'
    }
  } else {
    await autoFinish()
  }
  answerText.value = ''
  useVoice.value = false
  audioChunks.value = []
  stopSpeechRecognition()
  answerStartTime.value = Date.now()
  ElMessage.warning('本题已超时，自动记录为空答')
}

const timerDisplay = computed(() => {
  const m = Math.floor(questionTimer.value / 60)
  const s = questionTimer.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

// Voice recording
const isRecording = ref(false)
const mediaRecorder = ref<MediaRecorder | null>(null)
const audioChunks = ref<Blob[]>([])
const recordingTime = ref(0)
const recordTimer = ref<ReturnType<typeof setInterval> | null>(null)
const useVoice = ref(false)

// ── 本地语音识别（Web Speech API）──────────────────────────────────
// Chrome/Edge 原生支持，录完即出文字；不支持的浏览器回退到仅存音频
const SpeechRecognitionCtor: any =
  (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
const speechSupported = SpeechRecognitionCtor !== undefined
const recognition = ref<any>(null)
const isRecognizing = ref(false)

function startSpeechRecognition() {
  if (!speechSupported) return
  try {
    const rec = new SpeechRecognitionCtor()
    rec.lang = 'zh-CN'
    rec.continuous = true
    rec.interimResults = true
    rec.maxAlternatives = 1
    // 实时把识别结果（含中间结果）填入 answerText
    let finalText = ''
    rec.onresult = (e: any) => {
      let interim = ''
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i]
        if (r.isFinal) finalText += r[0].transcript
        else interim += r[0].transcript
      }
      answerText.value = (finalText + interim).trim()
    }
    rec.onerror = (e: any) => {
      // 麦克风权限被拒等场景，停止识别
      if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
        ElMessage.warning('无法获取语音识别权限，已回退为仅保存音频')
        isRecognizing.value = false
      }
    }
    rec.onend = () => {
      isRecognizing.value = false
    }
    recognition.value = rec
    rec.start()
    isRecognizing.value = true
  } catch (e) {
    isRecognizing.value = false
  }
}

function stopSpeechRecognition() {
  if (recognition.value && isRecognizing.value) {
    try { recognition.value.stop() } catch { /* ignore */ }
    isRecognizing.value = false
  }
}

const progressPercent = computed(() =>
  Math.round((answeredCount.value / Math.max(totalQuestions.value, 1)) * 100)
)

// 倒计时环形进度条状态（视觉美化）
const timerDanger = computed(() => questionTimer.value <= 60)
const timerPulse = computed(() => questionTimer.value <= 30)

// 评分仪表盘颜色（按分数区间着色）
const scoreColor = computed(() => {
  const s = Number(currentEvaluation.value?.totalScore) || 0
  if (s >= 85) return '#67c23a'
  if (s >= 70) return '#409eff'
  if (s >= 60) return '#e6a23c'
  return '#f56c6c'
})

// 语音按钮视觉状态（录音中闪烁红点）
const voiceButtonClass = computed(() => ({
  'voice-btn-recording': isRecording.value,
  'voice-btn-idle': !isRecording.value
}))

const currentQuestion = computed(() => questions.value[currentQuestionIndex.value])

const answeredCount = computed(() => answers.value.size)

const isAllDone = computed(() => answeredCount.value >= totalQuestions.value)

const refLines = computed(() => {
  const text = currentEvaluation.value?.referenceAnswer
  if (!text) return []
  return String(text).split('\n').map((l: string) => l.trim()).filter(Boolean)
})

onMounted(async () => {
  try {
    const sessionRes = await interviewApi.get(sessionId)
    if (sessionRes.code === 0) sessionInfo.value = sessionRes.data
    totalQuestions.value = sessionInfo.value?.questionLimit || 10
  } catch (e) { /* use default */ }
  const firstLoaded = await loadNextQuestion()
  answerStartTime.value = Date.now()
  if (firstLoaded) resetQuestionTimer()
  timerRef.value = setInterval(() => {}, 60000)
})

onUnmounted(() => {
  if (timerRef.value) clearInterval(timerRef.value)
  if (recordTimer.value) clearInterval(recordTimer.value)
  if (questionTimerInterval) clearInterval(questionTimerInterval)
  stopRecording()
  stopSpeechRecognition()
})

async function loadNextQuestion() {
  try {
    const lastQ = questions.value[currentQuestionIndex.value]
    const res: any = await interviewApi.nextQuestion(sessionId, lastQ?.questionId)
    if (res.code === 0) {
      questions.value.push(res.data)
      totalQuestions.value = Math.max(totalQuestions.value, questions.value.length)
      return true
    }
    // 后端返回 409/其他错误：题目已到最后或业务拒绝
    return false
  } catch (e: any) {
    // 网络/LLM 超时或服务不可用
    return false
  }
}

async function retryNextQuestion() {
  questionLoadError.value = false
  questionErrorType.value = ''
  questionLoading.value = true
  // 重试前回退索引到"当前显示题"，保证 lastQuestionId 指向当前题而非上一题
  const loaded = await loadNextQuestion()
  questionLoading.value = false
  if (loaded) {
    currentQuestionIndex.value++
    resetQuestionTimer()
  } else {
    questionLoadError.value = true
    questionErrorType.value = 'llm'
  }
}

async function autoFinish() {
  finished.value = true
  submitting.value = true
  try {
    await interviewApi.complete(sessionId)
    ElMessage.success('面试已结束，正在生成报告...')
    router.push('/interviews')
  } catch (e: any) {
    ElMessage.error('结束面试失败，请刷新重试')
  } finally {
    submitting.value = false
  }
}

async function submitAnswerFn() {
  const qId = currentQuestion.value?.questionId
  if (!qId) return
  submitting.value = true
  const durationSeconds = Math.floor((Date.now() - answerStartTime.value) / 1000)
  try {
    let res: any
    // 优先用识别出的文字（本地语音识别成功时），其次音频 blob，最后手打文字
    const transcribed = answerText.value.trim()
    if (useVoice.value && transcribed) {
      // 本地识别成功：走文字提交分支（AI 评分可用），音频仅作附件
      res = await interviewApi.submitAnswer(sessionId, {
        questionId: qId,
        answerText: transcribed,
        clientDurationSeconds: durationSeconds,
      })
      if (audioChunks.value.length > 0) {
        // 可选：同时存一份音频（非阻塞，失败不影响主流程）
        try {
          const audioBlob = new Blob(audioChunks.value, { type: 'audio/webm' })
          await interviewApi.submitAudioAnswer(sessionId, audioBlob, qId)
        } catch { /* ignore */ }
      }
    } else if (useVoice.value && audioChunks.value.length > 0) {
      // 无文字识别结果（浏览器不支持 Web Speech）：仅存音频
      const audioBlob = new Blob(audioChunks.value, { type: 'audio/webm' })
      res = await interviewApi.submitAudioAnswer(sessionId, audioBlob, qId)
    } else if (transcribed) {
      res = await interviewApi.submitAnswer(sessionId, {
        questionId: qId,
        answerText: transcribed,
        clientDurationSeconds: durationSeconds,
      })
    } else {
      ElMessage.warning('请先输入文字或录制语音回答')
      submitting.value = false
      return
    }
    if (res.code === 0) {
      answers.value.set(qId, { answerId: res.data.answerId, status: res.data.evaluationStatus })
      await new Promise(r => setTimeout(r, 1500))
      await loadEvaluation(res.data.answerId)
      answerText.value = ''
      useVoice.value = false
      audioChunks.value = []
      stopSpeechRecognition()
      answerStartTime.value = Date.now()

      // 先预加载下一题，成功后推进索引；失败时索引保持不变，等待用户重试
      if (answeredCount.value < totalQuestions.value) {
        questionLoadError.value = false
        questionErrorType.value = ''
        questionLoading.value = true
        const loaded = await loadNextQuestion()
        questionLoading.value = false
        if (loaded) {
          currentQuestionIndex.value++
          resetQuestionTimer()
        } else {
          // 生成下一题失败：不自动结束，等待用户重试
          questionLoadError.value = true
          questionErrorType.value = 'llm'
        }
      } else {
        if (questionTimerInterval) clearInterval(questionTimerInterval)
        await autoFinish()
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function loadEvaluation(answerId: number) {
  try {
    const res = await interviewApi.getEvaluation(answerId)
    if (res.code === 0) currentEvaluation.value = res.data
  } catch (e) { /* not ready */ }
}

async function finishInterview() {
  if (isAllDone.value) return
  await interviewApi.complete(sessionId)
  ElMessage.success('面试已结束，正在生成报告...')
  router.push('/interviews')
}

// ── Voice recording ──────────────────────────────────────────────────────
async function startRecording() {
  // 方案一：单一音频源，避免 MediaRecorder 与 SpeechRecognition 争抢麦克风
  // - 浏览器支持 Web Speech（Chrome/Edge）：仅启动实时语音转文字，不占 getUserMedia
  // - 不支持（Safari/Firefox/部分 Chromium 内核）：回退为 MediaRecorder 仅存音频
  if (speechSupported) {
    startSpeechRecognition()
    useVoice.value = true
    isRecording.value = true
    recordingTime.value = 0
    recordTimer.value = setInterval(() => { recordingTime.value++ }, 1000)
  } else {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = new MediaRecorder(stream)
      audioChunks.value = []
      recorder.ondataavailable = (e) => { if (e.data.size > 0) audioChunks.value.push(e.data) }
      recorder.start(250)
      mediaRecorder.value = recorder
      isRecording.value = true
      useVoice.value = true
      recordingTime.value = 0
      recordTimer.value = setInterval(() => { recordingTime.value++ }, 1000)
    } catch (e) {
      ElMessage.error('无法访问麦克风，请检查浏览器权限')
      useVoice.value = false
    }
  }
}

function stopRecording() {
  if (speechSupported) {
    // Web Speech 分支：只停识别，无音频流需要释放
    stopSpeechRecognition()
  } else {
    // 录音分支：停 MediaRecorder 并释放音频轨道
    if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
      const recorder = mediaRecorder.value
      recorder.onstop = () => {
        if (audioChunks.value.length === 0) useVoice.value = false
      }
      recorder.stop()
      recorder.stream.getTracks().forEach(t => t.stop())
    }
  }
  isRecording.value = false
  if (recordTimer.value) clearInterval(recordTimer.value)
}

function playRecording() {
  if (audioChunks.value.length === 0) return
  const blob = new Blob(audioChunks.value, { type: 'audio/webm' })
  const url = URL.createObjectURL(blob)
  new Audio(url).play()
}
</script>

<template>
  <div class="interview-page">
    <el-page-header @back="router.push('/dashboard')" title="返回首页" style="margin-bottom:16px" />
    <el-row :gutter="20">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>
                问题 {{ answeredCount + 1 }} / {{ totalQuestions }}
                <el-tag v-if="isAllDone" type="success" size="small" style="margin-left:8px">已完成</el-tag>
              </span>
              <span style="display:flex;align-items:center;gap:12px">
                <div v-if="!isAllDone && !questionLoadError" class="ring-timer" :class="{ danger: timerDanger, pulse: timerPulse }">
                  <el-progress
                    type="circle"
                    :percentage="Math.round((questionTimer / TIMER_TOTAL) * 100)"
                    :width="54"
                    :stroke-width="5"
                    :color="timerDanger ? '#f56c6c' : '#409eff'"
                  >
                    <template #default="{ percentage }">
                      <span class="ring-timer-text">{{ timerDisplay }}</span>
                    </template>
                  </el-progress>
                </div>
                <el-tag type="danger" v-if="!isAllDone && !questionLoadError && timerPulse" effect="dark" class="pulse-tag">即将超时</el-tag>
                <el-tag>{{ diffLabel(currentQuestion?.difficulty) }}</el-tag>
              </span>
            </div>
          </template>

          <div v-if="questionLoadError" class="question-area">
            <el-result icon="warning" title="生成下一题失败" :sub-title="questionErrorType === 'llm' ? 'AI 出题服务响应较慢，请稍后点击重试' : '出题服务暂不可用，请重试'">
              <template #extra>
                <el-button type="primary" @click="retryNextQuestion" :loading="questionLoading">重试</el-button>
              </template>
            </el-result>
          </div>
          <div v-else-if="!isAllDone && !currentQuestion" class="question-area">
            <el-empty description="正在生成问题..." />
          </div>
          <div v-else-if="!isAllDone" class="question-area">
            <p class="question-text">{{ currentQuestion.content }}</p>
            <div class="answer-area">
              <div style="margin-bottom:12px;display:flex;gap:12px;align-items:center">
                <el-button
                  class="voice-btn"
                  :class="voiceButtonClass"
                  :type="isRecording ? 'danger' : 'default'"
                  :icon="isRecording ? 'VideoPause' : 'VideoCamera'"
                  @click="isRecording ? stopRecording() : startRecording()"
                >
                  <span v-if="isRecording" class="recording-dot"></span>
                  {{ isRecording ? `录制中 ${recordingTime}s` : '🎤 语音回答' }}
                </el-button>
                <el-button v-if="!isRecording && audioChunks.length > 0" type="success" plain @click="playRecording">播放录音</el-button>
                <el-button v-if="!isRecording && audioChunks.length > 0" @click="audioChunks=[];useVoice=false;answerText=''">重新录制</el-button>
                <span v-if="speechSupported && isRecognizing" style="color:#409eff;font-size:13px">识别中…</span>
                <span v-else-if="useVoice && answerText.trim()" style="color:#67c23a;font-size:13px">语音已转文字</span>
                <span v-else-if="useVoice && audioChunks.length > 0" style="color:#e6a23c;font-size:13px">已录制语音（浏览器不支持实时识别，仅保存音频）</span>
              </div>
              <el-input
                v-model="answerText"
                type="textarea"
                :rows="6"
                placeholder="在此输入您的回答..."
                :disabled="submitting"
                @input="useVoice=false"
              />
              <div style="margin-top:12px;text-align:right">
                <el-button :loading="submitting" type="primary" @click="submitAnswerFn">提交回答</el-button>
              </div>
            </div>
          </div>

          <div v-else class="finish-area">
            <el-result icon="success" title="面试完成" :sub-title="`共回答 ${totalQuestions} 道题`">
              <template #extra>
                <el-button type="primary" @click="finishInterview" :loading="submitting">查看报告</el-button>
              </template>
            </el-result>
          </div>
        </el-card>
        <el-progress
          v-if="!isAllDone"
          :percentage="progressPercent"
          style="margin-top:16px"
          :stroke-width="12"
        />
      </el-col>

      <el-col :span="8">
        <el-card v-if="currentEvaluation && !isAllDone" header="当前评分">
          <div class="score-summary">
            <el-progress
              type="dashboard"
              :percentage="Number(currentEvaluation.totalScore) || 0"
              :width="120"
              :stroke-width="10"
              :color="scoreColor"
            >
              <template #default>
                <span class="dash-score">{{ currentEvaluation.totalScore }}</span>
                <span class="dash-label">综合得分</span>
              </template>
            </el-progress>
          </div>
          <el-divider />
          <h4><el-icon style="vertical-align:-2px;color:#67c23a"><Trophy /></el-icon> 优点</h4>
          <ul class="strength-list"><li v-for="s in currentEvaluation.strengths" :key="s">{{ s }}</li></ul>
          <h4><el-icon style="vertical-align:-2px;color:#f56c6c"><WarningFilled /></el-icon> 不足</h4>
          <ul class="weakness-list"><li v-for="w in currentEvaluation.weaknesses" :key="w">{{ w }}</li></ul>
          <h4><el-icon style="vertical-align:-2px;color:#409eff"><Document /></el-icon> 参考答案</h4>
          <el-collapse v-model="refExpanded">
            <el-collapse-item title="查看参考答案（分点详解）" name="ref">
              <div class="reference-answer">
                <div v-for="(line, li) in refLines" :key="li" class="ref-line" :class="{ 'ref-tip': line.startsWith('评分参考') }">{{ line }}</div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
        <el-card v-if="!isAllDone" header="面试进度">
          <el-timeline>
            <el-timeline-item
              v-for="(q, idx) in questions"
              :key="q.questionId"
              :timestamp="`第 ${idx+1} 题`"
              :type="idx === currentQuestionIndex ? 'primary' : answers.has(q.questionId) ? 'success' : 'info'"
            >
              <span style="font-size:12px">{{ (q.content || '').substring(0, 30) }}...</span>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.interview-page { padding: 10px; }
.question-area { padding: 10px 0; }
.finish-area { padding: 40px 0; text-align: center; }
.question-text {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  line-height: 1.7;
  margin-bottom: 20px;
  padding: 16px;
  background: #f5f7fa;
  border-left: 4px solid var(--el-color-primary);
  border-radius: var(--radius-md);
}
.answer-area { margin-top: 16px; }

/* 环形倒计时 */
.ring-timer { display: inline-block; }
.ring-timer .ring-timer-text { font-size: 12px; font-weight: 600; color: #303133; }
.ring-timer.danger .ring-timer-text { color: #f56c6c; }
.ring-timer.pulse { animation: ringPulse 1s infinite; }
.pulse-tag { animation: tagPulse 1s infinite; }
@keyframes ringPulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.06); } }
@keyframes tagPulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

/* 评分仪表盘 */
.score-summary { text-align: center; padding: 8px 0; display: flex; justify-content: center; }
.score-summary .dash-score { font-size: 32px; font-weight: 700; color: #303133; display: block; line-height: 1.2; }
.score-summary .dash-label { font-size: 13px; color: #909399; }

/* 优劣势列表 */
.strength-list li { color: #67c23a; }
.weakness-list li { color: #f56c6c; }

/* 参考答案 */
.reference-answer {
  background: #f5f7fa;
  padding: 14px;
  border-radius: var(--radius-md);
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
  border-left: 3px solid var(--el-color-primary);
}
.ref-line { line-height: 1.8; margin-bottom: 6px; padding-left: 4px; }
.ref-line::before { content: "•"; color: var(--el-color-primary); margin-right: 6px; font-weight: 700; }
.ref-tip {
  background: #fff8e6;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  border-left: 3px solid #e6a23c;
  margin-top: 8px;
  font-weight: 500;
}
.ref-tip::before { content: "★"; color: #e6a23c; }

/* 语音按钮 */
.voice-btn { position: relative; }
.voice-btn-recording .recording-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  margin-right: 6px;
  animation: dotBlink 1s infinite;
}
.recording-dot { vertical-align: middle; }
@keyframes dotBlink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }

ul { padding-left: 20px; margin: 8px 0; }
li { margin: 4px 0; font-size: 14px; color: #606266; }
h4 { margin: 14px 0 8px; font-size: 14px; color: #303133; font-weight: 600; display: flex; align-items: center; gap: 4px; }
</style>
