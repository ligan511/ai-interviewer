<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({ username: '', email: '', password: '', confirmPassword: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 2, max: 50, message: '长度 2-50 位', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }, { type: 'email', message: '格式不正确', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '不少于 6 位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_rule: any, value: string, callback: any) => {
      if (value !== form.password) callback(new Error('两次输入的密码不一致'))
      else callback()
    }, trigger: 'blur' }
  ]
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await authApi.register({ username: form.username, email: form.email, password: form.password })
    if (res.code === 0) { ElMessage.success('注册成功'); router.push('/login') }
    else ElMessage.error(res.message || '注册失败')
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '注册失败') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="register-container">
    <div class="register-panel">
      <div class="brand-side">
        <div class="brand-content">
          <h1 class="brand-title">加入 AI 面试官</h1>
          <p class="brand-sub">创建账号，开启求职能力训练</p>
          <ul class="brand-features">
            <li><el-icon><User /></el-icon> 一键创建，邮箱即账号</li>
            <li><el-icon><Aim /></el-icon> 选择岗位，AI 为你定制面试</li>
            <li><el-icon><DataLine /></el-icon> 六维评分 + 能力趋势追踪</li>
            <li><el-icon><Document /></el-icon> 完整报告，含参考答案与改进建议</li>
          </ul>
        </div>
      </div>
      <div class="form-side">
        <el-card class="register-card" shadow="never">
          <h2 class="title">创建账号</h2>
          <p class="subtitle">加入 AI 智能面试官系统</p>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" size="large" />
            </el-form-item>
            <el-form-item prop="email">
              <el-input v-model="form.email" placeholder="邮箱" prefix-icon="Message" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码（不少于6位）" prefix-icon="Lock" size="large" show-password />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" prefix-icon="Lock" size="large" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" class="submit-btn" style="width: 100%" :loading="loading" @click="handleRegister">
                注 册
              </el-button>
            </el-form-item>
            <el-form-item>
              <div style="text-align: center">
                <router-link to="/login" class="link">已有账号？立即登录</router-link>
              </div>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--gradient-hero);
}
.register-panel {
  display: flex;
  width: 860px;
  max-width: 92vw;
  min-height: 520px;
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
.brand-title { font-size: 28px; font-weight: 700; margin-bottom: 6px; color: #fff; }
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
.register-card {
  width: 100%;
  border: none;
  box-shadow: none;
  padding: 8px 4px;
}
.register-card:hover { box-shadow: none; }
.title { text-align: center; font-size: 24px; color: #303133; margin-bottom: 4px; font-weight: 700; }
.subtitle { text-align: center; color: #909399; font-size: 14px; margin-bottom: 24px; }
.submit-btn { border-radius: var(--radius-md); font-weight: 600; }
.link { color: var(--el-color-primary); font-size: 14px; }
.link:hover { text-decoration: underline; }
:deep(.el-input__wrapper) { transition: box-shadow var(--transition-fast); }
:deep(.el-input__wrapper:focus-within) { box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15); }
@media (max-width: 768px) {
  .brand-side { display: none; }
  .register-panel { width: 420px; min-height: auto; }
}
</style>
