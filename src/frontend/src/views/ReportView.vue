<template>
  <div class="report-page">
    <el-page-header @back="router.push('/dashboard')" title="返回首页" style="margin-bottom: 20px" />

    <el-card v-if="report">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-size: 20px; font-weight: bold">面试报告</span>
          <el-tag size="large">{{ report.overallScore }} / 100</el-tag>
        </div>
      </template>

      <div ref="chartRef" style="width: 100%; height: 300px; margin-bottom: 20px"></div>

      <h3>综合评价</h3>
      <p style="line-height: 1.8; color: #606266">{{ report.summary || '暂无评价内容' }}</p>

      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="12">
          <el-card shadow="never" header="优势">
            <ul v-if="report.strengths && report.strengths.length > 0">
              <li v-for="(s, i) in report.strengths" :key="i" style="margin: 6px 0; color: #67c23a">{{ s }}</li>
            </ul>
            <el-empty v-else description="暂无优势记录" :image-size="60" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" header="待改进">
            <ul v-if="report.weaknesses && report.weaknesses.length > 0">
              <li v-for="(w, i) in report.weaknesses" :key="i" style="margin: 6px 0; color: #f56c6c">{{ w }}</li>
            </ul>
            <el-empty v-else description="暂无待改进项" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>

      <el-card style="margin-top: 20px" header="改进建议">
        <ul v-if="report.suggestions && report.suggestions.length > 0">
          <li v-for="(s, i) in report.suggestions" :key="i" style="margin: 6px 0; color: #606266">{{ s }}</li>
        </ul>
        <el-empty v-else description="暂无改进建议" :image-size="60" />
      </el-card>
    </el-card>

    <el-empty v-else description="暂无面试报告" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { interviewApi } from '@/api/interview'

const router = useRouter()
const route = useRoute()
const report = ref<any>(null)
const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

onMounted(async () => {
  const sessionId = Number(route.params.reportId)
  if (isNaN(sessionId)) return

  try {
    const res = await interviewApi.getReport(sessionId)
    if (res.code === 0) {
      report.value = res.data
      await nextTick()
      initChart()
    }
  } catch (e) {
    // ignore
  }
})

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.dispose()
  }
})

function initChart() {
  if (!chartRef.value || !report.value) return
  if (chartInstance) chartInstance.dispose()

  chartInstance = echarts.init(chartRef.value)
  const dims = report.value.dimensionScores || {}

  chartInstance.setOption({
    radar: {
      indicator: [
        { name: '专业能力', max: 100 },
        { name: '逻辑能力', max: 100 },
        { name: '完整度', max: 100 },
        { name: '分析能力', max: 100 },
        { name: '表达能力', max: 100 },
        { name: '岗位匹配', max: 100 }
      ]
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          dims.professionalScore || 0,
          dims.logicScore || 0,
          dims.completenessScore || 0,
          dims.analysisScore || 0,
          dims.expressionScore || 0,
          dims.jobMatchScore || 0
        ],
        name: '能力评分'
      }]
    }]
  })
}
</script>

<style scoped>
.report-page {
  padding: 10px;
}
ul {
  padding-left: 20px;
  margin: 0;
}
</style>
