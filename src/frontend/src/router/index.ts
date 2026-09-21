import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/dashboard'
        },
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '首页' }
        },
        {
          path: 'jobs',
          name: 'Jobs',
          component: () => import('@/views/JobsView.vue'),
          meta: { title: '岗位列表' }
        },
        {
          path: 'jobs/:id',
          name: 'JobDetail',
          component: () => import('@/views/JobDetailView.vue'),
          meta: { title: '岗位详情' }
        },
        {
          path: 'interview/create',
          name: 'InterviewCreate',
          component: () => import('@/views/InterviewCreateView.vue'),
          meta: { title: '创建面试' }
        },
        {
          path: 'interview/:sessionId',
          name: 'Interview',
          component: () => import('@/views/InterviewView.vue'),
          meta: { title: '面试中' }
        },
        {
          path: 'reports/:reportId',
          name: 'Report',
          component: () => import('@/views/ReportView.vue'),
          meta: { title: '面试报告' }
        },
        {
          path: 'interviews',
          name: 'History',
          component: () => import('@/views/HistoryView.vue'),
          meta: { title: '历史记录' }
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: '个人中心' }
        },
        {
          path: 'knowledge',
          name: 'Knowledge',
          component: () => import('@/views/KnowledgeView.vue'),
          meta: { title: '知识库' }
        }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const store = useUserStore()
  if (to.meta.requiresAuth !== false && !store.token) {
    next('/login')
  } else {
    next()
  }
})

export default router
