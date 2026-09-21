<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { knowledgeApi } from '@/api/knowledge'
import { jobApi } from '@/api/job'

const jobs = ref<any[]>([])
const documents = ref<any[]>([])
const loading = ref(false)
const uploadFile = ref<File | null>(null)
const uploadTitle = ref('')
const uploadJobId = ref<number | undefined>()
const uploading = ref(false)

onMounted(async () => {
  try {
    const jr = await jobApi.list()
    if (jr.code === 0) jobs.value = jr.data || []
  } catch (e) { /* ignore */ }
  loadDocuments()
})

async function loadDocuments() {
  loading.value = true
  try {
    const res = await knowledgeApi.list()
    if (res.code === 0) documents.value = res.data || []
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

async function handleUpload() {
  if (!uploadFile.value || !uploadJobId.value) {
    ElMessage.warning('请选择岗位和文件')
    return
  }
  uploading.value = true
  const formData = new FormData()
  formData.append('jobId', String(uploadJobId.value))
  formData.append('title', uploadTitle.value || uploadFile.value.name)
  formData.append('file', uploadFile.value)
  try {
    const res = await knowledgeApi.upload(formData)
    if (res.code === 0) {
      ElMessage.success('上传成功')
      uploadFile.value = null
      uploadTitle.value = ''
      uploadJobId.value = undefined
      await loadDocuments()
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleDelete(id: number) {
  try {
    const res = await knowledgeApi.delete(id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      await loadDocuments()
    }
  } catch (e) { ElMessage.error('删除失败') }
}
</script>

<template>
  <div class="knowledge-page">
    <h2 style="margin-bottom:20px">岗位知识库</h2>

    <el-card style="margin-bottom:24px" header="上传知识文档">
      <el-form :model="{ jobId: uploadJobId, title: uploadTitle, file: uploadFile }" label-width="100px">
        <el-form-item label="目标岗位">
          <el-select v-model="uploadJobId" placeholder="选择岗位" style="width:200px">
            <el-option v-for="j in jobs" :key="j.id" :label="j.name" :value="j.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档标题">
          <el-input v-model="uploadTitle" placeholder="文档标题（可选）" style="width:300px" />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload action="" :auto-upload="false" :on-change="(file:any)=>uploadFile=file.raw">
            <el-button>选择文件（PDF/TXT）</el-button>
          </el-upload>
          <span v-if="uploadFile" style="margin-left:12px;color:#606266">{{ uploadFile.name }}</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card header="已上传文档">
      <el-table :data="documents" style="width:100%">
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="jobId" label="岗位ID" width="100" />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status==='PROCESSED'?'success':row.status==='PROCESSING'?'warning':'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && documents.length === 0" description="暂无知识库文档" />
    </el-card>
  </div>
</template>

<style scoped>
.knowledge-page { padding: 10px; }
</style>
