<template>
  <div class="job-detail-page">
    <el-page-header @back="router.push('/jobs')" title="返回岗位列表" style="margin-bottom: 20px" />

    <el-card v-if="detail" class="job-detail-card">
      <template #header>
        <div class="job-detail-header">
          <h2 class="job-detail-name">{{ detail.job.name }}</h2>
          <el-tag :type="levelTag(detail.job.level)" effect="dark" size="large" round>
            {{ levelLabel[detail.job.level] || detail.job.level }}
          </el-tag>
        </div>
      </template>

      <div class="job-detail-body">
        <section class="job-section">
          <h3 class="section-title">岗位描述</h3>
          <p class="job-desc-text">{{ detail.job.description || '暂无描述' }}</p>
        </section>

        <section class="job-section">
          <h3 class="section-title">核心技能要求</h3>
          <div v-if="detail.skills && detail.skills.length > 0" class="skill-tags">
            <el-tag
              v-for="skill in detail.skills"
              :key="skill.id"
              :type="skillTagType(skill.weight)"
              effect="plain"
              class="skill-tag"
            >
              <span class="skill-name">{{ skill.skillName }}</span>
              <span v-if="skill.weight > 0" class="skill-weight">{{ skill.weight.toFixed(2) }}</span>
            </el-tag>
          </div>
          <el-empty v-else description="暂无技能标签" :image-size="60" />
        </section>

        <div class="job-cta-area">
          <el-button
            type="primary"
            size="large"
            class="start-btn"
            @click="router.push(`/interview/create?jobId=${detail.job.id}`)"
          >
            <el-icon><Aim /></el-icon>
            立即开始面试
          </el-button>
        </div>
      </div>
    </el-card>

    <el-empty v-else description="岗位不存在或加载中" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { jobApi } from '@/api/job'
import type { JobDetail } from '@/api/job'

const router = useRouter()
const route = useRoute()
const detail = ref<JobDetail | null>(null)

const levelLabel: Record<string, string> = {
  junior: '初级', medium: '中级', senior: '高级'
}

function levelTag(level?: string) {
  if (level === 'junior') return 'success'
  if (level === 'senior') return 'warning'
  return 'primary'
}
function skillTagType(weight: number) {
  if (weight >= 1.5) return 'danger'
  if (weight >= 1.0) return 'primary'
  return 'info'
}

onMounted(async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) return
  try {
    const res = await jobApi.detail(id)
    if (res.code === 0) detail.value = res.data
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.job-detail-page { padding: 10px; }
.job-detail-card {
  border-radius: var(--radius-lg);
  max-width: 860px;
}
.job-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.job-detail-name {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 0;
}
.job-detail-body { padding: 4px 0; }

.job-section { margin-bottom: 28px; }
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
}
.job-desc-text {
  color: #606266;
  line-height: 1.9;
  font-size: 14px;
  padding: 14px;
  background: #f5f7fa;
  border-radius: var(--radius-md);
  margin: 0;
}

.skill-tags { display: flex; flex-wrap: wrap; gap: 10px; }
.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  padding: 4px 12px;
  border-radius: 16px;
}
.skill-name { font-weight: 500; }
.skill-weight {
  font-size: 11px;
  opacity: 0.7;
  font-family: monospace;
}

.job-cta-area {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
  text-align: center;
}
.start-btn {
  font-size: 16px;
  padding: 12px 36px;
  border-radius: var(--radius-md);
  font-weight: 600;
}
</style>
