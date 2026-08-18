import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

const routes = [
  {
    path: '/',
    name: 'Home',
    redirect: () => {
      const auth = useAuthStore()
      return auth.isAuthenticated ? { name: 'Feed' } : { name: 'Login' }
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/feed',
    name: 'Feed',
    component: () => import('@/views/FeedView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/topics/:id(\\d+)',
    name: 'TopicDetail',
    component: () => import('@/views/TopicDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/interests',
    name: 'Interests',
    component: () => import('@/views/InterestView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/mypage',
    name: 'MyPage',
    component: () => import('@/views/MyPageView.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'Login' }
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: 'Feed' }
  }
})

export default router
