<template>
  <div class="history-page">
    <h2>历史面试记录</h2>

    <el-table :data="sessions" style="width: 100%">
      <el-table-column prop="createdAt" label="面试时间" width="180" />
      <el-table-column prop="jobId" label="岗位ID" width="100" />
      <el-table-column prop="interviewType" label="面试类型" width="120" />
      <el-table-column prop="difficulty" label="难度" width="100">
        <template #default="{ row }">
          {{ difficultyLabel[row.difficulty] || row.difficulty }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态">
        <template #default="{ row }">
          <el-tag :type="statusType[row.status] || 'info'">
            {{ statusLabel[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'COMPLETED'"
            type="primary"
            link
            @click="viewReport(row.id)"
          >
            查看报告
          </el-button>
          <span v-else style="color: #909399; font-size: 12px">未生成报告</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 20px; justify-content: center"
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next"
      @current-change="loadHistory"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { interviewApi } from '@/api/interview'

const router = useRouter()
const sessions = ref<any[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const difficultyLabel: Record<string, string> = {
  junior: '初级', medium: '中级', senior: '高级'
}
const statusLabel: Record<string, string> = {
  CREATED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成'
}
const statusType: Record<string, string> = {
  CREATED: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success'
}

onMounted(() => {
  loadHistory()
})

async function loadHistory() {
  try {
    const res = await interviewApi.history({ page: page.value, pageSize: pageSize.value })
    if (res.code === 0) {
      sessions.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    // ignore
  }
}

function viewReport(sessionId: number) {
  router.push(`/reports/${sessionId}`)
}
</script>

<style scoped>
.history-page {
  padding: 10px;
}
.history-page h2 {
  margin-bottom: 20px;
}
</style>
