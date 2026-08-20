<template>
  <header class="app-header">
    <div class="header-inner">
      <router-link :to="auth.isAuthenticated ? '/feed' : '/login'" class="brand">
        <span class="brand-mark">G</span>
        <span class="brand-text">GAME INTELLIGENCE</span>
      </router-link>

      <nav v-if="auth.isAuthenticated" class="nav-links">
        <router-link to="/feed" class="nav-link" :class="{ active: isNewsRoute }">뉴스</router-link>
        <router-link to="/interests" class="nav-link" :class="{ active: route.name === 'Interests' }">관심 게임</router-link>
        <router-link
          v-if="auth.isAdmin"
          to="/admin/games"
          class="nav-link"
          :class="{ active: route.name === 'AdminGames' }"
        >
          게임 관리
        </router-link>
        <router-link
          v-if="auth.isAdmin"
          to="/admin/franchises"
          class="nav-link"
          :class="{ active: route.name === 'AdminFranchises' }"
        >
          프랜차이즈
        </router-link>
        <router-link
          v-if="auth.isAdmin"
          to="/admin/reviews"
          class="nav-link"
          :class="{ active: route.name === 'AdminEntityReviews' }"
        >
          검토 큐
        </router-link>
      </nav>

      <div class="header-actions">
        <template v-if="auth.isAuthenticated">
          <router-link to="/mypage" class="mypage-link">내 정보</router-link>
          <button type="button" class="logout-button" @click="handleLogout">로그아웃</button>
        </template>
        <template v-else>
          <router-link to="/login" class="login-link">로그인</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const isNewsRoute = computed(() => route.name === 'Feed' || route.name === 'TopicDetail')

function handleLogout() {
  auth.logout(false)
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.97);
  border-bottom: 1px solid #eceef1;
}

.header-inner {
  width: min(1120px, calc(100% - 48px));
  height: 58px;
  margin: 0 auto;
  display: flex;
  align-items: center;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  flex-shrink: 0;
}

.brand-mark {
  width: 25px;
  height: 25px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  background: #1d2025;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.brand-text {
  color: #1b1e23;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.045em;
}

.nav-links {
  display: flex;
  align-items: stretch;
  gap: 22px;
  height: 100%;
  margin-left: 40px;
}

.nav-link {
  position: relative;
  display: flex;
  align-items: center;
  padding: 0 3px;
  color: #777e88;
  font-size: 13px;
  font-weight: 600;
}

.nav-link.active {
  color: #202329;
}

.nav-link.active::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: #202329;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.logout-button,
.login-link,
.mypage-link {
  background: none;
  color: #6d747e;
  font-size: 12px;
  font-weight: 600;
}

.logout-button:hover,
.login-link:hover,
.mypage-link:hover {
  color: #1f2329;
}

@media (max-width: 640px) {
  .header-inner {
    width: min(100% - 32px, 1120px);
  }

  .brand-text {
    display: none;
  }

  .nav-links {
    gap: 14px;
    margin-left: 18px;
  }

  .mypage-link {
    display: none;
  }
}
</style>
