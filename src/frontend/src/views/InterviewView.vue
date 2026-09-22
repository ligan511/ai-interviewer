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

const currentQuestion = computed(() => questions.value[currentQuestionIndex.value])

const answeredCount = computed(() => answers.value.size)

const isAllDone = computed(() => answeredCount.value >= totalQuestions.value)

onMounted(async () => {
  try {
    const sessionRes = await interviewApi.get(sessionId)
    if (sessionRes.code === 0) sessionInfo.value = sessionRes.data
    totalQuestions.value = sessionInfo.value?.questionLimit || 10
  } catch (e) { /* use default */ }
  await loadNextQuestion()
  answerStartTime.value = Date.now()
  timerRef.value = setInterval(() => {}, 60000)
})

onUnmounted(() => {
  if (timerRef.value) clearInterval(timerRef.value)
  if (recordTimer.value) clearInterval(recordTimer.value)
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
        } else {
          // 生成下一题失败：不自动结束，等待用户重试
          questionLoadError.value = true
          questionErrorType.value = 'llm'
        }
      } else {
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
              <el-tag>{{ diffLabel(currentQuestion?.difficulty) }}</el-tag>
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
                  :type="isRecording ? 'danger' : 'default'"
                  :icon="isRecording ? 'VideoPause' : 'VideoCamera'"
                  @click="isRecording ? stopRecording() : startRecording()"
                >
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
            <div class="total-score">
              <span class="score-number">{{ currentEvaluation.totalScore }}</span>
              <span class="score-label">综合得分</span>
            </div>
          </div>
          <el-divider />
          <h4>优点</h4>
          <ul><li v-for="s in currentEvaluation.strengths" :key="s">{{ s }}</li></ul>
          <h4>不足</h4>
          <ul><li v-for="w in currentEvaluation.weaknesses" :key="w">{{ w }}</li></ul>
          <h4>参考答案</h4>
          <p class="reference-answer">{{ currentEvaluation.referenceAnswer }}</p>
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
.question-text { font-size: 18px; font-weight: bold; color: #303133; line-height: 1.6; margin-bottom: 20px; }
.answer-area { margin-top: 16px; }
.score-summary { text-align: center; padding: 20px 0; }
.score-number { display: block; font-size: 48px; font-weight: bold; color: #409eff; }
.score-label { font-size: 14px; color: #909399; }
.reference-answer { background: #f5f7fa; padding: 12px; border-radius: 4px; font-size: 14px; color: #606266; line-height: 1.6; }
ul { padding-left: 20px; margin: 8px 0; }
li { margin: 4px 0; font-size: 14px; color: #606266; }
h4 { margin: 12px 0 8px; font-size: 14px; color: #303133; }
</style>
