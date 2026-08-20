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
  },
  {
    path: '/admin/games',
    name: 'AdminGames',
    component: () => import('@/views/AdminGamesView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/franchises',
    name: 'AdminFranchises',
    component: () => import('@/views/AdminFranchisesView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/reviews',
    name: 'AdminEntityReviews',
    component: () => import('@/views/AdminEntityReviewsView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
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

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'Feed' }
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: 'Feed' }
  }
})

export default router
