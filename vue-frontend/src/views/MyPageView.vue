<template>
  <div class="mypage-shell">
    <AppHeader />

    <main class="mypage-main">
      <router-link to="/feed" class="back-link">← 뉴스</router-link>

      <section class="page-heading">
        <p class="eyebrow">ACCOUNT</p>
        <h1>마이페이지</h1>
        <p>현재 로그인한 계정 정보와 관심 게임 설정을 확인합니다.</p>
      </section>

      <section class="profile-section">
        <div class="profile-head">
          <div class="profile-avatar">{{ initial }}</div>
          <div>
            <h2>{{ auth.user?.name || '사용자' }}</h2>
            <p>{{ auth.user?.email || '-' }}</p>
          </div>
        </div>

        <dl class="profile-list">
          <div class="profile-row">
            <dt>이름</dt>
            <dd>{{ auth.user?.name || '-' }}</dd>
          </div>
          <div class="profile-row">
            <dt>이메일</dt>
            <dd>{{ auth.user?.email || '-' }}</dd>
          </div>
        </dl>
      </section>

      <section class="interest-section">
        <div>
          <h2>관심 게임</h2>
          <p>관심 게임을 등록하면 Feed의 관심순 정렬에 반영됩니다.</p>
        </div>
        <router-link to="/interests" class="manage-link">관심 게임 관리 →</router-link>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { useAuthStore } from '@/store/auth.js'

const auth = useAuthStore()
const initial = computed(() => auth.user?.name?.trim()?.charAt(0)?.toUpperCase() || 'U')
</script>

<style scoped>
.mypage-shell {
  min-height: 100vh;
  background: #fff;
  color: #1d2025;
}

.mypage-main {
  width: min(920px, calc(100% - 48px));
  margin: 0 auto;
  padding: 52px 0 96px;
}

.back-link {
  display: inline-block;
  margin-bottom: 34px;
  color: #7a818b;
  font-size: 12px;
  font-weight: 600;
}

.back-link:hover {
  color: #1d2025;
}

.page-heading {
  padding-bottom: 30px;
  border-bottom: 1px solid #dfe2e6;
}

.eyebrow {
  margin: 0 0 9px;
  color: #8a919b;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.page-heading h1 {
  margin: 0 0 10px;
  font-size: 30px;
  line-height: 1.25;
  letter-spacing: -0.035em;
}

.page-heading > p:last-child {
  margin: 0;
  color: #727984;
  font-size: 13px;
}

.profile-section {
  padding: 34px 0 28px;
  border-bottom: 1px solid #dfe2e6;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 30px;
}

.profile-avatar {
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: #1d2025;
  color: #fff;
  font-size: 17px;
  font-weight: 800;
}

.profile-head h2 {
  margin: 0 0 4px;
  font-size: 17px;
  letter-spacing: -0.02em;
}

.profile-head p {
  margin: 0;
  color: #808791;
  font-size: 12px;
}

.profile-list {
  margin: 0;
  border-top: 1px solid #22262c;
}

.profile-row {
  display: grid;
  grid-template-columns: 150px 1fr;
  padding: 15px 2px;
  border-bottom: 1px solid #eceef1;
}

.profile-row dt {
  color: #7b828c;
  font-size: 12px;
}

.profile-row dd {
  margin: 0;
  color: #2b2f35;
  font-size: 12px;
  font-weight: 600;
  word-break: break-all;
}

.interest-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 32px 0 0;
}

.interest-section h2 {
  margin: 0 0 9px;
  font-size: 15px;
  letter-spacing: -0.02em;
}

.interest-section p {
  margin: 0;
  color: #838a94;
  font-size: 12px;
  line-height: 1.7;
}

.manage-link {
  flex: 0 0 auto;
  color: #2d3238;
  font-size: 12px;
  font-weight: 700;
}

.manage-link:hover {
  text-decoration: underline;
}

@media (max-width: 640px) {
  .mypage-main {
    width: min(100% - 32px, 920px);
    padding-top: 36px;
  }

  .profile-row {
    grid-template-columns: 90px 1fr;
  }

  .interest-section {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
