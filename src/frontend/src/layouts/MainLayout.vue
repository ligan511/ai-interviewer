<template>
  <div class="main-layout">
    <el-header class="header">
      <div class="logo">AI 智能面试官</div>
      <el-menu mode="horizontal" :ellipsis="false" router :default-active="activeRoute">
        <el-menu-item index="/dashboard">首页</el-menu-item>
        <el-menu-item index="/jobs">岗位浏览</el-menu-item>
        <el-menu-item index="/knowledge">知识库</el-menu-item>
        <el-menu-item index="/interviews">历史记录</el-menu-item>
        <el-menu-item index="/profile">个人中心</el-menu-item>
      </el-menu>
      <div class="user-info">
        <span>{{ userStore.userInfo?.username }}</span>
        <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
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
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
  height: 60px;
}
.logo {
  font-size: 20px;
  font-weight: bold;
  color: #409eff;
  margin-right: 40px;
  white-space: nowrap;
}
.header .el-menu {
  flex: 1;
  border-bottom: none;
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
  border-top: 1px solid #e4e7ed;
}
</style>
