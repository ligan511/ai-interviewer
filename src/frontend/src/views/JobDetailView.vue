<template>
  <div class="job-detail-page">
    <el-page-header @back="router.back()" :title="'返回'" style="margin-bottom: 20px" />

    <el-card v-if="job">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-size: 22px; font-weight: bold">{{ job.job.name }}</span>
          <el-tag size="large">{{ job.job.level }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="岗位名称">{{ job.job.name }}</el-descriptions-item>
        <el-descriptions-item label="难度等级">{{ job.job.level }}</el-descriptions-item>
        <el-descriptions-item label="岗位描述" :span="2">{{ job.job.description || '暂无描述' }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="job.skills?.length">
        <h3 style="margin-top: 20px">核心技能要求</h3>
        <el-tag v-for="skill in job.skills" :key="skill.id" style="margin: 4px">
          {{ skill.skillName }}
        </el-tag>
      </template>
    </el-card>

    <el-card style="margin-top: 20px" header="开始面试">
      <p style="color: #666">系统将基于该岗位要求生成 AI 面试题，支持文字回答与自动评分。</p>
      <el-button type="primary" size="large" @click="startInterview">
        立即开始面试
      </el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { jobApi } from '@/api/job'

const router = useRouter()
const route = useRoute()
const job = ref<any>(null)

onMounted(async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) return
  try {
    const res = await jobApi.detail(id)
    if (res.code === 0) {
      job.value = res.data
    }
  } catch (e) {
    // ignore
  }
})

function startInterview() {
  router.push({ path: '/interview/create', query: { jobId: route.params.id } })
}
</script>

<style scoped>
.job-detail-page {
  padding: 10px;
}
</style>
