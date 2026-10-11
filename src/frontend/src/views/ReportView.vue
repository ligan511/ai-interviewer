<template>
  <div class="report-page">
    <el-page-header @back="router.push('/dashboard')" title="返回首页" style="margin-bottom: 20px" />

    <el-card v-if="report" class="report-card">
      <template #header>
        <div class="report-header">
          <div class="report-title-group">
            <h2 class="report-title">面试报告</h2>
            <span class="report-level" :class="levelClass">{{ levelLabel }}</span>
          </div>
          <div class="score-dash">
            <el-progress
              type="dashboard"
              :percentage="Number(report.overallScore) || 0"
              :width="100"
              :stroke-width="10"
              :color="scoreColor"
            >
              <template #default>
                <span class="dash-num">{{ report.overallScore }}</span>
                <span class="dash-cap">综合分</span>
              </template>
            </el-progress>
          </div>
        </div>
      </template>

      <div ref="chartRef" style="width: 100%; height: 320px; margin-bottom: 12px"></div>

      <h3 class="section-title">综合评价</h3>
      <p class="summary-text">{{ report.summary || '暂无评价内容' }}</p>

      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="12">
          <el-card shadow="never" class="strength-card">
            <template #header>
              <div class="card-title success">
                <el-icon><Trophy /></el-icon><span>优势</span>
              </div>
            </template>
            <ul v-if="report.strengths && report.strengths.length > 0" class="strength-list">
              <li v-for="(s, i) in report.strengths" :key="i">{{ s }}</li>
            </ul>
            <el-empty v-else description="暂无优势记录" :image-size="60" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="weakness-card">
            <template #header>
              <div class="card-title danger">
                <el-icon><WarningFilled /></el-icon><span>待改进</span>
              </div>
            </template>
            <ul v-if="report.weaknesses && report.weaknesses.length > 0" class="weakness-list">
              <li v-for="(w, i) in report.weaknesses" :key="i">{{ w }}</li>
            </ul>
            <el-empty v-else description="暂无待改进项" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>

      <el-card style="margin-top: 20px" class="suggestion-card">
        <template #header>
          <div class="card-title primary">
            <el-icon><Promotion /></el-icon><span>改进建议</span>
          </div>
        </template>
        <ul v-if="report.suggestions && report.suggestions.length > 0" class="suggestion-list">
          <li v-for="(s, i) in report.suggestions" :key="i">{{ s }}</li>
        </ul>
        <el-empty v-else description="暂无改进建议" :image-size="60" />
      </el-card>

      <el-card v-if="refAnswers.length > 0" style="margin-top: 20px" class="ref-summary-card">
        <template #header>
          <div class="card-title primary">
            <el-icon><Document /></el-icon><span>参考答案汇总（{{ refAnswers.length }} 题）</span>
            <el-tag v-if="totalAnswered > validCount" size="small" type="warning" effect="plain"
                    style="margin-left: 8px">仅 {{ validCount }} 道有效评分题（{{ totalAnswered - validCount }} 题兜底未展示）</el-tag>
            <el-tag v-else size="small" type="info" style="margin-left: 8px">{{ refAnswers.length }} 题</el-tag>
          </div>
        </template>
        <el-collapse v-model="refExpanded">
          <el-collapse-item
            v-for="(item, i) in refAnswers"
            :key="i"
            :name="String(i)"
          >
            <template #title>
              <div class="ref-item-title">
                <el-tag size="small" type="primary" round class="ref-badge">{{ i + 1 }}</el-tag>
                <span class="ref-q-text">{{ (item.question || '').substring(0, 50) }}{{ (item.question || '').length > 50 ? '…' : '' }}</span>
              </div>
            </template>
            <div class="reference-answer">
              <div v-for="(line, li) in splitRef(item.referenceAnswer)" :key="li" class="ref-line" :class="{ 'ref-tip': line.startsWith('评分参考') }">{{ line }}</div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-card>
    </el-card>

    <el-empty v-else description="暂无面试报告" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { interviewApi } from '@/api/interview'

const router = useRouter()
const route = useRoute()
const report = ref<any>(null)
const refAnswers = ref<{ question: string; referenceAnswer: string }[]>([])
const validCount = ref(0)
const totalAnswered = ref(0)
const refExpanded = ref<string[]>([])
const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

// 综合分颜色（按区间着色）
const scoreColor = computed(() => {
  const s = Number(report.value?.overallScore) || 0
  if (s >= 85) return '#67c23a'
  if (s >= 70) return '#409eff'
  if (s >= 60) return '#e6a23c'
  return '#f56c6c'
})
const levelLabel = computed(() => {
  const s = Number(report.value?.overallScore) || 0
  if (s >= 85) return '优秀'
  if (s >= 70) return '良好'
  if (s >= 60) return '及格'
  return '待提高'
})
const levelClass = computed(() => {
  const s = Number(report.value?.overallScore) || 0
  if (s >= 85) return 'lv-success'
  if (s >= 70) return 'lv-primary'
  if (s >= 60) return 'lv-warning'
  return 'lv-danger'
})

onMounted(async () => {
  const sessionId = Number(route.params.reportId)
  if (isNaN(sessionId)) return

  try {
    const res = await interviewApi.getReport(sessionId)
    if (res.code === 0 && res.data) {
      report.value = res.data.report
      refAnswers.value = res.data.referenceAnswers || []
      validCount.value = res.data.validCount ?? refAnswers.value.length
      totalAnswered.value = res.data.totalAnswered ?? refAnswers.value.length
      refExpanded.value = refAnswers.value.map((_, i) => String(i))
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

function splitRef(text: string | undefined | null): string[] {
  if (!text) return []
  return String(text).split('\n').map((l) => l.trim()).filter(Boolean)
}

function initChart() {
  if (!chartRef.value || !report.value) return
  if (chartInstance) chartInstance.dispose()

  chartInstance = echarts.init(chartRef.value)
  const dims = report.value.dimensionScores || {}
  const color = scoreColor.value

  chartInstance.setOption({
    radar: {
      indicator: [
        { name: '专业能力', max: 100 },
        { name: '逻辑能力', max: 100 },
        { name: '完整度', max: 100 },
        { name: '分析能力', max: 100 },
        { name: '表达能力', max: 100 },
        { name: '岗位匹配', max: 100 }
      ],
      radius: '68%',
      splitArea: {
        areaStyle: {
          color: ['#f5f7fa', '#eef1f6', '#e6ebf2', '#dde3ec'],
          opacity: 0.5
        }
      },
      axisName: {
        color: '#606266',
        fontSize: 12
      }
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
        name: '能力评分',
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color, width: 2 },
        itemStyle: { color },
        areaStyle: {
          color: {
            type: 'radial',
            x: 0.5, y: 0.5, r: 0.5,
            colorStops: [
              { offset: 0, color: color + 'cc' },
              { offset: 1, color: color + '22' }
            ]
          }
        }
      }]
    }],
    animationDuration: 800
  })
}
</script>

<style scoped>
.report-page {
  padding: 10px;
}
.report-card {
  border-radius: var(--radius-lg);
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.report-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}
.report-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin: 0;
}
.report-level {
  font-size: 13px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 12px;
}
.lv-success { background: #f0f9eb; color: #67c23a; }
.lv-primary { background: #ecf5ff; color: #409eff; }
.lv-warning { background: #fdf6ec; color: #e6a23c; }
.lv-danger { background: #fef0f0; color: #f56c6c; }

.score-dash {
  display: flex;
  align-items: center;
}
.dash-num {
  font-size: 30px;
  font-weight: 700;
  color: #303133;
  display: block;
  line-height: 1.1;
}
.dash-cap {
  font-size: 12px;
  color: #909399;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
}
.summary-text {
  line-height: 1.9;
  color: #606266;
  padding: 14px;
  background: #f5f7fa;
  border-radius: var(--radius-md);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 15px;
}
.card-title.success { color: #67c23a; }
.card-title.danger { color: #f56c6c; }
.card-title.primary { color: #409eff; }

.strength-list li {
  color: #67c23a;
  margin: 8px 0;
  padding-left: 6px;
}
.weakness-list li {
  color: #f56c6c;
  margin: 8px 0;
  padding-left: 6px;
}
.suggestion-list li {
  color: #606266;
  margin: 8px 0;
}
ul {
  padding-left: 20px;
  margin: 0;
}

/* 参考答案汇总 */
.ref-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding-right: 12px;
}
.ref-badge {
  min-width: 20px;
  text-align: center;
}
.ref-q-text {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}
.reference-answer {
  background: #f5f7fa;
  padding: 14px;
  border-radius: var(--radius-md);
  border-left: 3px solid var(--el-color-primary);
}
.ref-line {
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
  margin-bottom: 4px;
  padding-left: 4px;
}
.ref-line::before {
  content: "•";
  color: var(--el-color-primary);
  margin-right: 6px;
  font-weight: 700;
}
.ref-tip {
  background: #fff8e6;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  border-left: 3px solid #e6a23c;
  margin-top: 8px;
  font-weight: 500;
}
.ref-tip::before {
  content: "★";
  color: #e6a23c;
}
</style>
