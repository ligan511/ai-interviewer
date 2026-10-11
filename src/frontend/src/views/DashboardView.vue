<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { interviewApi } from '@/api/interview'
import { useUserStore } from '@/stores/user'
import * as echarts from 'echarts'

const router = useRouter()
const userStore = useUserStore()
const recentInterviews = ref<any[]>([])
const historyCount = ref(0)
const trends = ref<any[]>([])
let trendChart: echarts.ECharts | null = null
let chartEl: HTMLElement | null = null

const statusLabel: Record<string, string> = {
  CREATED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成'
}
const statusType: Record<string, string> = {
  CREATED: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success'
}

// ── 顶部统计指标（4 卡片）────────────────────────────────────────
const completedCount = computed(() =>
  recentInterviews.value.filter((i) => i.status === 'COMPLETED').length
)
const scores = computed(() =>
  recentInterviews.value
    .map((i: any) => Number(i.overallScore))
    .filter((s) => !isNaN(s))
)
const avgScore = computed(() => {
  if (scores.value.length === 0) return 0
  return Math.round(scores.value.reduce((a, b) => a + b, 0) / scores.value.length)
})
const highestScore = computed(() => (scores.value.length ? Math.max(...scores.value) : 0))

const statCards = [
  { key: 'total', value: historyCount, label: '历史面试次数', icon: 'History', bg: '#409eff', to: '/interviews' },
  { key: 'completed', value: completedCount, label: '已完成面试', icon: 'CircleCheck', bg: '#67c23a', to: '/interviews' },
  { key: 'avg', value: avgScore, label: '平均综合分', icon: 'DataAnalysis', bg: '#e6a23c', to: '/interviews' },
  { key: 'highest', value: highestScore, label: '最高综合分', icon: 'Trophy', bg: '#f56c6c', to: '/interviews' }
]

onMounted(async () => {
  try {
    const res = await interviewApi.history({ page: 1, pageSize: 50 })
    if (res.code === 0) {
      recentInterviews.value = res.data.list || []
      historyCount.value = res.data.total || 0
    }
  } catch (e) { /* ignore */ }

  try {
    const res = await fetch('/api/v1/dashboard/trends', {
      headers: { 'Authorization': `Bearer ${userStore.token}` }
    })
    const json = await res.json()
    if (json.code === 0 && json.data?.trends?.length > 0) {
      trends.value = json.data.trends
      await nextTick()
      initTrendChart()
    }
  } catch (e) { /* ignore */ }
})

onBeforeUnmount(() => {
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
  window.removeEventListener('resize', handleResize)
})

function handleResize() {
  trendChart?.resize()
}

function initTrendChart() {
  chartEl = document.getElementById('trend-chart')
  if (!chartEl) return
  if (trendChart) trendChart.dispose()
  trendChart = echarts.init(chartEl)

  const dates = trends.value.map((t: any) => t.date)
  trendChart.setOption({
    tooltip: { trigger: 'axis', confine: true },
    legend: { data: ['总分', '专业能力', '逻辑能力', '表达能力'], bottom: 0 },
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', min: 0, max: 100 },
    color: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c'],
    series: [
      {
        name: '总分', type: 'line', data: trends.value.map((t: any) => t.totalScore),
        smooth: true, symbol: 'circle', symbolSize: 8, lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff88' },
            { offset: 1, color: '#409eff11' }
          ])
        }
      },
      { name: '专业能力', type: 'line', data: trends.value.map((t: any) => t.professionalScore), smooth: true, symbol: 'circle', symbolSize: 6 },
      { name: '逻辑能力', type: 'line', data: trends.value.map((t: any) => t.logicScore), smooth: true, symbol: 'circle', symbolSize: 6 },
      { name: '表达能力', type: 'line', data: trends.value.map((t: any) => t.expressionScore), smooth: true, symbol: 'circle', symbolSize: 6 }
    ],
    animationDuration: 800
  })
}
</script>

<template>
  <div class="dashboard">
    <div class="welcome">
      <h2>欢迎回来，{{ userStore.userInfo?.username || '求职者' }}</h2>
      <p>开始您的 AI 模拟面试之旅</p>
    </div>

    <el-row :gutter="20" class="stat-row">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card" @click="router.push(card.to)">
          <div class="stat-body">
            <div class="stat-icon" :style="{ background: card.bg }">
              <el-icon size="28"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-text">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:24px" v-if="trends.length > 0" class="trend-card"
             :header="`能力趋势（近 ${trends.length} 次面试）`">
      <div ref="chartEl" id="trend-chart" style="width:100%;height:320px"></div>
    </el-card>

    <el-card style="margin-top:24px" header="最近面试" class="recent-card">
      <el-empty v-if="recentInterviews.length === 0" description="暂无面试记录，开始第一次面试吧！" />
      <el-table v-else :data="recentInterviews.slice(0, 5)" style="width:100%" :row-class-name="() => 'recent-row'">
        <el-table-column prop="createdAt" label="面试时间" width="180" />
        <el-table-column prop="jobId" label="岗位ID" width="100" />
        <el-table-column prop="overallScore" label="综合分" width="90">
          <template #default="{ row }">
            <span v-if="row.overallScore != null" class="score-pill">{{ row.overallScore }}</span>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="{ row }"><el-tag :type="statusType[row.status] || 'info'">{{ statusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button v-if="scope.row.status === 'COMPLETED'" type="primary" link @click="router.push(`/reports/${scope.row.id}`)">查看报告</el-button>
            <el-button v-else type="primary" link @click="router.push(`/interviews`)">继续</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard { padding: 10px; }
.welcome h2 { margin-bottom: 4px; font-size: 24px; font-weight: 700; color: #303133; }
.welcome p { color: #909399; margin-bottom: 24px; }

.stat-row { row-gap: 0; }
.stat-card {
  cursor: pointer;
  border-radius: var(--radius-lg);
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-card-hover); }
.stat-body { display: flex; align-items: center; gap: 14px; padding: 4px; }
.stat-icon {
  width: 56px; height: 56px;
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  color: #fff; flex-shrink: 0;
}
.stat-value { font-size: 28px; font-weight: 700; color: #303133; line-height: 1.1; }
.stat-label { font-size: 13px; color: #909399; margin-top: 2px; }

.trend-card, .recent-card { border-radius: var(--radius-lg); }
.score-pill {
  display: inline-block;
  min-width: 32px;
  text-align: center;
  padding: 2px 8px;
  border-radius: 12px;
  background: #ecf5ff;
  color: #409eff;
  font-weight: 700;
  font-size: 14px;
}
:deep(.recent-row) { transition: background 0.15s; }
:deep(.recent-row:hover > td) { background: #f5f7fa; }
</style>
