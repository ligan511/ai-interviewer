<template>
  <div class="interview-create">
    <el-page-header @back="router.back()" title="返回" style="margin-bottom: 20px" />
    <h2>面试配置</h2>

    <el-card style="max-width: 600px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="目标岗位">
          <el-select v-model="form.jobId" placeholder="请选择岗位" style="width: 100%">
            <el-option v-for="job in jobs" :key="job.id" :label="job.name" :value="job.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="关联简历">
          <el-select v-model="form.resumeId" placeholder="选择简历（可选）" style="width: 100%">
            <el-option label="不使用简历（通用面试）" :value="null" />
            <el-option v-for="r in resumes" :key="r.id" :label="r.fileName" :value="r.id" />
          </el-select>
          <div style="margin-top:4px"><router-link to="/profile" style="font-size:12px;color:#409eff">去上传简历 →</router-link></div>
        </el-form-item>

        <el-form-item label="面试类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="技术面试" value="technical" />
            <el-option label="综合面试" value="comprehensive" />
            <el-option label="项目经验面试" value="project" />
          </el-select>
        </el-form-item>

        <el-form-item label="面试难度">
          <el-radio-group v-model="form.difficulty">
            <el-radio label="junior">初级</el-radio>
            <el-radio label="medium">中级</el-radio>
            <el-radio label="senior">高级</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="题目数量">
          <el-input-number v-model="form.questionLimit" :min="5" :max="30" :step="5" />
        </el-form-item>

        <el-form-item label="预计时长">
          <el-input-number v-model="form.durationLimitSeconds" :min="300" :max="3600" :step="300" style="width: 150px" />
          <span style="margin-left: 8px; color: #909399">秒</span>
        </el-form-item>
      </el-form>

      <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">
        开始面试
      </el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { interviewApi } from '@/api/interview'
import { jobApi } from '@/api/job'
import { resumeApi } from '@/api/resume'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const jobs = ref<any[]>([])
const resumes = ref<any[]>([])

const form = reactive({
  jobId: Number(route.query.jobId) || undefined,
  resumeId: null as number | null,
  type: 'technical',
  difficulty: 'medium',
  questionLimit: 10,
  durationLimitSeconds: 1800
})

onMounted(async () => {
  try {
    const res = await jobApi.list()
    if (res.code === 0) jobs.value = res.data || []
  } catch (e) { /* ignore */ }
  try {
    const res = await resumeApi.list()
    if (res.code === 0) resumes.value = res.data || []
  } catch (e) { /* ignore */ }
})

async function handleSubmit() {
  if (!form.jobId) {
    ElMessage.warning('请选择目标岗位')
    return
  }
  loading.value = true
  try {
    const res = await interviewApi.create({
      jobId: form.jobId,
      resumeId: form.resumeId || undefined,
      type: form.type,
      difficulty: form.difficulty,
      questionLimit: form.questionLimit,
      durationLimitSeconds: form.durationLimitSeconds
    })
    if (res.code === 0) {
      ElMessage.success('面试创建成功')
      router.push(`/interview/${res.data.sessionId}`)
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.interview-create {
  padding: 10px;
}
.interview-create h2 {
  margin-bottom: 20px;
}
</style>
