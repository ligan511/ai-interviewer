<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { resumeApi } from '@/api/resume'

const router = useRouter()
const userStore = useUserStore()
const resumes = ref<any[]>([])
const uploading = ref(false)
const uploadFile = ref<File | null>(null)

onMounted(loadResumes)

async function loadResumes() {
  try {
    const res = await resumeApi.list()
    if (res.code === 0) resumes.value = res.data || []
  } catch (e) { /* ignore */ }
}

async function handleUpload() {
  if (!uploadFile.value) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  const formData = new FormData()
  formData.append('file', uploadFile.value)
  formData.append('fileName', uploadFile.value.name)
  try {
    const res = await resumeApi.upload(formData)
    if (res.code === 0) {
      ElMessage.success('简历上传成功')
      uploadFile.value = null
      await loadResumes()
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
    const res = await resumeApi.delete(id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      await loadResumes()
    }
  } catch (e) { ElMessage.error('删除失败') }
}

function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="profile-page">
    <h2 style="margin-bottom:20px">个人中心</h2>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card header="基本信息">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">{{ userStore.userInfo?.username || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userStore.userInfo?.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="用户ID">{{ userStore.userInfo?.userId || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div style="margin-top:24px;text-align:center">
            <el-button type="danger" size="large" @click="handleLogout">退出登录</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card header="我的简历">
          <el-upload action="" :auto-upload="false" :on-change="(file:any)=>uploadFile=file.raw" style="margin-bottom:16px">
            <el-button type="primary" :loading="uploading">上传简历（PDF/Word）</el-button>
          </el-upload>
          <el-empty v-if="resumes.length === 0" description="暂无简历" />
          <div v-for="r in resumes" :key="r.id" style="display:flex;align-items:center;justify-content:space-between;padding:8px 0;border-bottom:1px solid #ebeef5">
            <div>
              <div style="font-weight:bold">{{ r.fileName }}</div>
              <div style="font-size:12px;color:#909399">{{ r.createdAt?.substring(0,10) }}</div>
            </div>
            <el-button type="danger" link size="small" @click="handleDelete(r.id)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.profile-page { padding: 10px; }
</style>
