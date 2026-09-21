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
const timerRef = ref<ReturnType<typeof setInterval> | null>(null)
const answerStartTime = ref(0)
const totalQuestions = ref(10)
const sessionInfo = ref<any>(null)
const finished = ref(false)

// Voice recording
const isRecording = ref(false)
const mediaRecorder = ref<MediaRecorder | null>(null)
const audioChunks = ref<Blob[]>([])
const recordingTime = ref(0)
const recordTimer = ref<ReturnType<typeof setInterval> | null>(null)
const useVoice = ref(false)

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
    return false
  } catch (e) {
    return false
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
    if (useVoice.value && audioChunks.value.length > 0) {
      const audioBlob = new Blob(audioChunks.value, { type: 'audio/webm' })
      res = await interviewApi.submitAudioAnswer(sessionId, audioBlob, qId)
    } else if (answerText.value.trim()) {
      res = await interviewApi.submitAnswer(sessionId, {
        questionId: qId,
        answerText: answerText.value,
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
      answerStartTime.value = Date.now()

      // 检查是否还有下一题
      if (answeredCount.value < totalQuestions.value) {
        const loaded = await loadNextQuestion()
        if (loaded) {
          currentQuestionIndex.value++
        } else {
          // 后端返回"没有更多题目"，直接结束
          await autoFinish()
        }
      } else {
        // 最后一题答完，自动结束
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
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const recorder = new MediaRecorder(stream)
    audioChunks.value = []
    recorder.ondataavailable = (e) => { if (e.data.size > 0) audioChunks.value.push(e.data) }
    recorder.start()
    mediaRecorder.value = recorder
    isRecording.value = true
    recordingTime.value = 0
    recordTimer.value = setInterval(() => { recordingTime.value++ }, 1000)
  } catch (e) {
    ElMessage.error('无法访问麦克风，请检查浏览器权限')
  }
}

function stopRecording() {
  if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
    mediaRecorder.value.stop()
    mediaRecorder.value.stream.getTracks().forEach(t => t.stop())
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
              <el-tag>{{ currentQuestion?.difficulty }}</el-tag>
            </div>
          </template>

          <div v-if="!isAllDone && !currentQuestion" class="question-area">
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
                <el-button v-if="!isRecording && audioChunks.length > 0" @click="audioChunks=[];useVoice=false">重新录制</el-button>
                <span v-if="useVoice && audioChunks.length > 0" style="color:#67c23a;font-size:13px">已录制语音回答</span>
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
