<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { jobApi } from '@/api/job'

const router = useRouter()
const jobs = ref<any[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await jobApi.list()
    if (res.code === 0) jobs.value = res.data || []
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
})

function goToDetail(id: number): void {
  router.push(`/jobs/${id}`)
}
</script>

<template>
  <div class="jobs-page">
    <h2 style="margin-bottom: 20px">岗位列表</h2>
    <el-row :gutter="20" v-if="!loading">
      <el-col v-for="job in jobs" :key="job.id" :span="8">
        <el-card shadow="hover" class="job-card" @click="goToDetail(job.id)">
          <div class="job-header">
            <span class="job-name">{{ job.name }}</span>
            <el-tag :type="job.level === 'junior' ? 'success' : job.level === 'medium' ? 'warning' : 'danger'" size="small">
              {{ job.level }}
            </el-tag>
          </div>
          <p class="job-desc">{{ job.description || '暂无描述' }}</p>
          <div class="job-footer">
            <span style="color: #909399; font-size: 13px">查看详情 →</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-else description="加载中..." />
  </div>
</template>

<style scoped>
.jobs-page { padding: 10px; }
.job-card { cursor: pointer; transition: transform 0.2s; margin-bottom: 20px; }
.job-card:hover { transform: translateY(-4px); }
.job-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.job-name { font-size: 18px; font-weight: bold; color: #303133; }
.job-desc { color: #606266; font-size: 14px; line-height: 1.6; min-height: 44px; }
.job-footer { margin-top: 12px; border-top: 1px solid #ebeef5; padding-top: 8px; }
</style>
