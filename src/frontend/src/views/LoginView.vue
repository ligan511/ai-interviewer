<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = ref({
  email: '',
  password: ''
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await authApi.login(form.value)
    if (res.code === 0) {
      userStore.setAuth(res.data.token, res.data)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } else {
      ElMessage.error(res.message || '登录失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-panel">
      <!-- 左侧品牌区 -->
      <div class="brand-side">
        <div class="brand-content">
          <h1 class="brand-title">AI 智能面试官</h1>
          <p class="brand-sub">求职能力评估系统</p>
          <ul class="brand-features">
            <li><el-icon><Aim /></el-icon> AI 动态出题，贴合岗位与难度</li>
            <li><el-icon><Trophy /></el-icon> 六维评分：专业 / 逻辑 / 完整 / 分析 / 表达 / 匹配</li>
            <li><el-icon><DataLine /></el-icon> 能力趋势雷达图，持续改进</li>
            <li><el-icon><Cpu /></el-icon> 参考答案 + 改进建议，随练随评</li>
          </ul>
        </div>
      </div>
      <!-- 右侧表单区 -->
      <div class="form-side">
        <el-card class="login-card" shadow="never">
          <h2 class="title">欢迎登录</h2>
          <p class="subtitle">登录后开始您的 AI 模拟面试</p>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
            <el-form-item prop="email">
              <el-input v-model="form.email" placeholder="邮箱" prefix-icon="Message" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" class="login-btn" style="width: 100%" :loading="loading" @click="handleLogin">
                登 录
              </el-button>
            </el-form-item>
            <el-form-item>
              <div style="text-align: center">
                <router-link to="/register" class="link">还没有账号？立即注册</router-link>
              </div>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--gradient-hero);
}
.login-panel {
  display: flex;
  width: 820px;
  max-width: 92vw;
  min-height: 460px;
  background: #fff;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
}
.brand-side {
  flex: 1;
  background: var(--gradient-hero);
  color: #fff;
  padding: 40px 36px;
  display: flex;
  align-items: center;
}
.brand-content { width: 100%; }
.brand-title { font-size: 30px; font-weight: 700; margin-bottom: 6px; color: #fff; }
.brand-sub { font-size: 15px; color: rgba(255, 255, 255, 0.85); margin-bottom: 28px; }
.brand-features { list-style: none; padding: 0; margin: 0; }
.brand-features li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
  margin-bottom: 16px;
  line-height: 1.5;
}
.brand-features .el-icon { flex-shrink: 0; font-size: 18px; }
.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}
.login-card {
  width: 100%;
  border: none;
  box-shadow: none;
  padding: 8px 4px;
}
.login-card:hover { box-shadow: none; }
.title { text-align: center; font-size: 24px; color: #303133; margin-bottom: 4px; font-weight: 700; }
.subtitle { text-align: center; color: #909399; font-size: 14px; margin-bottom: 28px; }
.login-btn { border-radius: var(--radius-md); font-weight: 600; }
.link { color: var(--el-color-primary); font-size: 14px; }
.link:hover { text-decoration: underline; }
/* 输入框聚焦动画 */
:deep(.el-input__wrapper) {
  transition: box-shadow var(--transition-fast);
}
:deep(.el-input__wrapper:focus-within) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
}
/* 响应式：窄屏隐藏品牌区 */
@media (max-width: 768px) {
  .brand-side { display: none; }
  .login-panel { width: 420px; min-height: auto; }
}
</style>
