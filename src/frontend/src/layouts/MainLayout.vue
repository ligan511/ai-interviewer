<template>
  <div class="main-layout">
    <el-header class="header">
      <div class="logo">
        <el-icon :size="24"><Cpu /></el-icon>
        <span class="logo-text">AI 智能面试官</span>
      </div>
      <el-menu mode="horizontal" :ellipsis="false" router :default-active="activeRoute" class="top-menu">
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon><span>首页</span>
        </el-menu-item>
        <el-menu-item index="/jobs">
          <el-icon><Briefcase /></el-icon><span>岗位浏览</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Collection /></el-icon><span>知识库</span>
        </el-menu-item>
        <el-menu-item index="/interviews">
          <el-icon><History /></el-icon><span>历史记录</span>
        </el-menu-item>
        <el-menu-item index="/profile">
          <el-icon><User /></el-icon><span>个人中心</span>
        </el-menu-item>
      </el-menu>
      <div class="user-info">
        <el-tag type="info" effect="plain" round>{{ userStore.userInfo?.username }}</el-tag>
        <el-button type="danger" size="small" plain @click="handleLogout">退出</el-button>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
    <el-footer class="footer">
      AI 智能面试官与求职能力评估系统 V1.0
    </el-footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeRoute = computed(() => route.path)

function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.main-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 24px;
  height: 60px;
  box-shadow: var(--shadow-card);
  position: relative;
  z-index: 10;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 36px;
  white-space: nowrap;
  color: var(--el-color-primary);
}
.logo .el-icon { font-size: 24px; }
.logo-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.top-menu {
  flex: 1;
  border-bottom: none;
  --el-menu-item-height: 60px;
}
/* 菜单项当前路由高亮（下划线指示器） */
:deep(.top-menu .el-menu-item.is-active) {
  color: var(--el-color-primary);
  font-weight: 600;
  border-bottom: 2px solid var(--el-color-primary);
  margin-bottom: -1px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 20px;
}
.main {
  flex: 1;
  padding: 20px;
  background: #f5f7fa;
}
.footer {
  text-align: center;
  color: #909399;
  font-size: 12px;
  padding: 10px;
  background: #fff;
  border-top: 1px solid #ebeef5;
}
</style>
