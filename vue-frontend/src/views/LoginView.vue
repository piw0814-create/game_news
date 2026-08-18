<template>
  <div class="login-page">
    <div class="login-layout">
      <section class="login-left">
        <div class="brand">
          <span class="brand-mark">G</span>
          <span class="brand-name">GAME INTELLIGENCE</span>
        </div>

        <div class="brand-content">
          <p class="eyebrow">GAME NEWS, GROUPED BY EVENT</p>
          <h2>게임 뉴스를<br>기사보다 사건으로</h2>
          <p>여러 출처의 기사를 하나의 Topic으로 묶고 핵심 내용과 중요도를 빠르게 확인합니다.</p>

          <ul class="feature-list">
            <li v-for="feature in features" :key="feature">
              <span class="dot"></span>{{ feature }}
            </li>
          </ul>
        </div>
      </section>

      <section class="login-right">
        <div class="login-box fade-in-up">
          <div v-if="!showRegister" class="section">
            <h3 class="section-title">로그인</h3>
            <p class="section-desc">계정에 로그인해 Topic Feed와 관심 게임을 확인합니다.</p>

            <form class="form" @submit.prevent="handleLogin">
              <div class="form-group">
                <label class="form-label">이메일</label>
                <input v-model="loginForm.email" type="email" class="form-input" placeholder="user@example.com" required />
              </div>
              <div class="form-group">
                <label class="form-label">비밀번호</label>
                <input v-model="loginForm.password" type="password" class="form-input" placeholder="비밀번호" required />
              </div>

              <div v-if="error" class="error-msg">{{ error }}</div>

              <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
                <span v-if="loading">로그인 중...</span>
                <span v-else>로그인</span>
              </button>
            </form>

            <div class="switch-link">
              계정이 없으신가요?
              <button class="text-btn" @click="openRegister">회원가입</button>
            </div>
          </div>

          <div v-else class="section">
            <h3 class="section-title">회원가입</h3>
            <form class="form" @submit.prevent="handleRegister">
              <div class="form-group">
                <label class="form-label">이름</label>
                <input v-model="registerForm.name" type="text" class="form-input" placeholder="홍길동" required />
              </div>
              <div class="form-group">
                <label class="form-label">이메일</label>
                <input v-model="registerForm.email" type="email" class="form-input" placeholder="user@example.com" required />
              </div>
              <div class="form-group">
                <label class="form-label">비밀번호</label>
                <input v-model="registerForm.password" type="password" class="form-input" placeholder="8자 이상" required />
              </div>

              <div v-if="error" class="error-msg">{{ error }}</div>
              <div v-if="success" class="success-msg">{{ success }}</div>

              <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
                <span v-if="loading">가입 중...</span>
                <span v-else>회원가입</span>
              </button>
            </form>

            <div class="switch-link">
              이미 계정이 있으신가요?
              <button class="text-btn" @click="openLogin">로그인</button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'
import { authApi } from '@/api/auth.js'

const router = useRouter()
const auth = useAuthStore()

const showRegister = ref(false)
const loading = ref(false)
const error = ref('')
const success = ref('')

const createLoginForm = () => ({ email: '', password: '' })
const createRegisterForm = () => ({ name: '', email: '', password: '' })
const loginForm = ref(createLoginForm())
const registerForm = ref(createRegisterForm())

const features = ['동일 사건 Topic 통합', 'AI 요약과 중요도', '관심 게임 기반 Feed']

function clearMessages() {
  error.value = ''
  success.value = ''
}

function openRegister() {
  clearMessages()
  showRegister.value = true
}

function openLogin() {
  clearMessages()
  showRegister.value = false
}

async function handleLogin() {
  clearMessages()
  loading.value = true

  try {
    await auth.login(loginForm.value)
    loginForm.value = createLoginForm()
    await router.replace('/feed')
  } catch (e) {
    error.value = e.response?.data?.message || '로그인에 실패했습니다.'
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  clearMessages()
  loading.value = true

  try {
    const email = registerForm.value.email
    await authApi.register(registerForm.value)
    success.value = '회원가입이 완료되었습니다. 로그인해 주세요.'
    registerForm.value = createRegisterForm()
    loginForm.value.email = email

    setTimeout(() => {
      showRegister.value = false
      success.value = ''
    }, 1500)
  } catch (e) {
    error.value = e.response?.data?.message || '회원가입에 실패했습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: stretch;
}

.login-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  min-height: 100vh;
}

.login-left {
  padding: 48px;
  display: flex;
  flex-direction: column;
  gap: 90px;
  background: #181b20;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.brand-name {
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.brand-content {
  width: min(100%, 520px);
}

.eyebrow {
  margin: 0 0 16px;
  color: rgba(255, 255, 255, 0.45);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.11em;
}

.brand-content h2 {
  margin: 0 0 18px;
  color: #fff;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.34;
  letter-spacing: -0.04em;
}

.brand-content > p:not(.eyebrow) {
  max-width: 470px;
  margin: 0 0 30px;
  color: rgba(255, 255, 255, 0.62);
  font-size: 14px;
  line-height: 1.8;
}

.feature-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 13px;
}

.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.65);
  flex-shrink: 0;
}

.login-right {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: #fff;
}

.login-box {
  width: 100%;
  max-width: 400px;
}


.section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-title {
  margin: 0 0 4px;
  color: #1d2025;
  font-size: 22px;
  font-weight: 700;
}

.section-desc {
  margin: 0 0 4px;
  color: #737a84;
  font-size: 13px;
  line-height: 1.7;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  color: #646b75;
  font-size: 12px;
  font-weight: 600;
}

.form-input {
  padding: 10px 2px;
  border: 0;
  border-bottom: 1px solid #ccd1d7;
  border-radius: 0;
  outline: none;
  background: #fff;
  color: #1d2025;
  font: inherit;
  font-size: 14px;
}

.form-input:focus {
  border-bottom-color: #1d2025;
}

.btn-full {
  width: 100%;
  margin-top: 6px;
  justify-content: center;
}

.switch-link {
  margin-top: 4px;
  text-align: center;
  color: #7a818b;
  font-size: 12px;
}

.text-btn {
  padding: 0 2px;
  background: none;
  color: #25292f;
  font-size: 12px;
  font-weight: 700;
  text-decoration: underline;
}

.error-msg,
.success-msg {
  padding: 10px 12px;
  border-left: 2px solid;
  font-size: 12px;
}

.error-msg {
  border-color: #b54e4e;
  background: #fff7f7;
  color: #a23e3e;
}

.success-msg {
  border-color: #4d826f;
  background: #f4faf7;
  color: #3b6c5c;
}

@media (max-width: 760px) {
  .login-layout {
    grid-template-columns: 1fr;
  }

  .login-left {
    min-height: 330px;
    gap: 48px;
    padding: 32px;
  }

  .brand-content h2 {
    font-size: 28px;
  }

  .login-right {
    padding: 42px 32px 64px;
  }
}
</style>
