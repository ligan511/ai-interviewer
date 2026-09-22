<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
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

const statusLabel: Record<string, string> = {
  CREATED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成'
}
const statusType: Record<string, string> = {
  CREATED: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success'
}

onMounted(async () => {
  try {
    const res = await interviewApi.history({ page: 1, pageSize: 5 })
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

function initTrendChart() {
  if (!trendChart) {
    const el = document.getElementById('trend-chart')
    if (!el) return
    trendChart = echarts.init(el)
  }
  const dates = trends.value.map(t => t.date)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['总分', '专业能力', '逻辑能力', '表达能力'], bottom: 0 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [
      { name: '总分', type: 'line', data: trends.value.map(t => t.totalScore), smooth: true, symbol: 'circle', itemStyle: { color: '#409eff' } },
      { name: '专业能力', type: 'line', data: trends.value.map(t => t.professionalScore), smooth: true, symbol: 'circle', itemStyle: { color: '#67c23a' } },
      { name: '逻辑能力', type: 'line', data: trends.value.map(t => t.logicScore), smooth: true, symbol: 'circle', itemStyle: { color: '#e6a23c' } },
      { name: '表达能力', type: 'line', data: trends.value.map(t => t.expressionScore), smooth: true, symbol: 'circle', itemStyle: { color: '#f56c6c' } },
    ]
  })
}
</script>

<template>
  <div class="dashboard">
    <h2>欢迎回来，{{ userStore.userInfo?.username }}</h2>
    <p style="color: #666; margin-bottom: 30px">开始您的 AI 模拟面试之旅</p>
    <el-row :gutter="20">
      <el-col :span="8"><el-card shadow="hover" class="stat-card" @click="router.push('/jobs')"><div class="stat-icon" style="background:#409eff"><el-icon size="32"><Briefcase /></el-icon></div><div class="stat-info"><div class="stat-value">开始面试</div><div class="stat-label">选择岗位，开启 AI 模拟面试</div></div></el-card></el-col>
      <el-col :span="8"><el-card shadow="hover" class="stat-card" @click="router.push('/interviews')"><div class="stat-icon" style="background:#67c23a"><el-icon size="32"><History /></el-icon></div><div class="stat-info"><div class="stat-value">{{ historyCount }}</div><div class="stat-label">历史面试次数</div></div></el-card></el-col>
      <el-col :span="8"><el-card shadow="hover" class="stat-card" @click="router.push('/profile')"><div class="stat-icon" style="background:#e6a23c"><el-icon size="32"><User /></el-icon></div><div class="stat-info"><div class="stat-value">完善档案</div><div class="stat-label">上传简历，获得个性化面试</div></div></el-card></el-col>
    </el-row>

    <el-card style="margin-top:24px" :header="`能力趋势（近 ${trends.length} 次面试）`" v-if="trends.length > 0">
      <div id="trend-chart" style="width:100%;height:300px"></div>
    </el-card>
    <el-card style="margin-top:24px" header="最近面试" v-else>
      <el-empty description="暂无面试记录，开始第一次面试吧！" />
    </el-card>

    <el-card style="margin-top:24px" header="最近面试">
      <el-empty v-if="recentInterviews.length === 0" description="暂无面试记录" />
      <el-table v-else :data="recentInterviews" style="width:100%">
        <el-table-column prop="createdAt" label="面试时间" width="180" />
        <el-table-column prop="jobId" label="岗位ID" width="100" />
        <el-table-column prop="status" label="状态"><template #default="{ row }"><el-tag :type="statusType[row.status] || 'info'">{{ statusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作"><template #default><el-button type="primary" link @click="router.push('/interviews')">查看详情</el-button></template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard { padding: 10px; }
.dashboard h2 { margin-bottom: 4px; }
.stat-card { cursor: pointer; transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-4px); }
.stat-icon { width: 64px; height: 64px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; float: left; margin-right: 16px; }
.stat-info { overflow: hidden; }
.stat-value { font-size: 24px; font-weight: bold; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
