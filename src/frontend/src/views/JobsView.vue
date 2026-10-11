<template>
  <div class="jobs-page">
    <div class="jobs-header">
      <h2 class="page-title">岗位浏览</h2>
      <p class="page-sub">选择一个岗位，开始 AI 模拟面试</p>
    </div>

    <el-empty v-if="jobs.length === 0" description="暂无岗位" />

    <el-row v-else :gutter="20" class="jobs-grid">
      <el-col v-for="job in jobs" :key="job.id" :span="8" :xs="24" :sm="12" :md="8">
        <el-card class="job-card" shadow="hover" @click="router.push(`/jobs/${job.id}`)">
          <div class="job-card-body">
            <div class="job-top">
              <h3 class="job-name">{{ job.name }}</h3>
              <el-tag :type="levelTag(job.level)" effect="dark" size="small" round>
                {{ levelLabel[job.level] || job.level }}
              </el-tag>
            </div>
            <p class="job-desc" :title="job.description">{{ job.description || '暂无描述' }}</p>
            <el-button type="primary" plain size="small" class="job-cta" @click.stop="router.push(`/jobs/${job.id}`)">
              查看详情 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="jobs.length > 0" class="jobs-footer">
      <el-button :disabled="page <= 1" @click="page--; loadJobs()">上一页</el-button>
      <span class="page-info">第 {{ page }} 页</span>
      <el-button :disabled="jobs.length < 10" @click="page++; loadJobs()">下一页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { jobApi } from '@/api/job'

const router = useRouter()
const jobs = ref<any[]>([])
const page = ref(1)

const levelLabel: Record<string, string> = {
  junior: '初级', medium: '中级', senior: '高级'
}

function levelTag(level?: string) {
  if (level === 'junior') return 'success'
  if (level === 'senior') return 'warning'
  return 'primary'
}

async function loadJobs() {
  try {
    const res = await jobApi.list({ page: page.value, pageSize: 10 })
    if (res.code === 0) jobs.value = res.data || []
  } catch (e) { /* ignore */ }
}

onMounted(loadJobs)
</script>

<style scoped>
.jobs-page { padding: 10px; }
.jobs-header { margin-bottom: 20px; }
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
}
.page-sub { color: #909399; font-size: 14px; padding-left: 13px; }

.jobs-grid { row-gap: 0; }
.job-card {
  cursor: pointer;
  border-radius: var(--radius-lg);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
  height: 100%;
}
.job-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
}
.job-card-body { padding: 4px; }
.job-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
}
.job-name {
  font-size: 17px;
  font-weight: 700;
  color: #303133;
  margin: 0;
  flex: 1;
}
.job-desc {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 14px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 60px;
}
.job-cta { width: 100%; }

.jobs-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 24px;
}
.page-info { color: #909399; font-size: 13px; }
</style>
