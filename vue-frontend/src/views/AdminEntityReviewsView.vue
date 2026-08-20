<template>
  <div class="admin-page">
    <AppHeader />

    <main class="admin-main">
      <section class="page-heading">
        <div>
          <p class="eyebrow">ADMIN · ENTITY REVIEW</p>
          <h1>엔티티 검토 큐</h1>
          <p>AI와 IGDB가 자동 확정하지 못한 게임·프랜차이즈만 확인합니다.</p>
        </div>
        <button type="button" class="refresh-button" :disabled="loading" @click="loadReviews">
          {{ loading ? '불러오는 중' : '새로고침' }}
        </button>
      </section>

      <div v-if="error" class="message-banner error">
        <span>{{ error }}</span><button type="button" @click="error = ''">닫기</button>
      </div>
      <div v-if="notice" class="message-banner notice">
        <span>{{ notice }}</span><button type="button" @click="notice = ''">닫기</button>
      </div>

      <section class="toolbar">
        <div class="status-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            type="button"
            class="status-tab"
            :class="{ active: status === tab.value }"
            @click="changeStatus(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>
        <label class="search-box">
          <span class="sr-only">검토 검색</span>
          <input v-model.trim="search" type="search" placeholder="감지명 · 기사 제목 검색" />
        </label>
      </section>

      <section class="review-list">
        <div v-if="loading" class="empty-state">검토 항목을 불러오는 중입니다.</div>
        <div v-else-if="!filteredReviews.length" class="empty-state">현재 조건의 검토 항목이 없습니다.</div>

        <article v-for="review in filteredReviews" :key="review.id" class="review-row">
          <div class="review-summary">
            <div class="title-line">
              <span class="kind-badge">{{ kindLabel(review.entityKind) }}</span>
              <h2>{{ review.detectedName }}</h2>
              <span class="confidence">AI {{ confidencePercent(review.confidenceScore) }}%</span>
            </div>
            <p class="article-title">{{ review.articleTitle }}</p>
            <p class="meta">{{ review.articleSourceName }} · 기사 #{{ review.articleId }} · {{ review.aiEntityType || '-' }}</p>
            <p v-if="review.reason" class="reason">{{ review.reason }}</p>
          </div>

          <div class="row-actions">
            <a v-if="review.articleUrl" :href="review.articleUrl" target="_blank" rel="noopener" class="text-button">원문</a>
            <button
              v-if="review.status === 'PENDING'"
              type="button"
              class="text-button danger"
              :disabled="resolvingId === review.id"
              @click="resolveUnrelated(review)"
            >관련 없음</button>
          </div>

          <div v-if="review.status === 'PENDING'" class="candidate-panel">
            <div class="panel-heading">
              <strong>판정 후보</strong>
              <span>IGDB 우선 · 애매하면 관리자 결정</span>
            </div>

            <div v-if="review.candidates?.length" class="candidate-grid">
              <div
                v-for="(candidate, index) in review.candidates"
                :key="`${candidate.source}-${candidate.entityKind}-${candidate.localId || candidate.igdbId || index}`"
                class="candidate-card"
              >
                <div class="candidate-title">
                  <strong>{{ candidate.displayName || candidate.name }}</strong>
                  <span>{{ candidate.entityKind === 'GAME' ? 'GAME' : 'FRANCHISE' }}</span>
                </div>
                <p v-if="candidate.displayName && candidate.displayName !== candidate.name" class="canonical">{{ candidate.name }}</p>
                <p class="candidate-meta">
                  {{ candidate.source }}
                  <template v-if="candidate.localId"> · Local #{{ candidate.localId }}</template>
                  <template v-if="candidate.igdbId"> · IGDB #{{ candidate.igdbId }}</template>
                </p>
                <p v-if="candidate.publisher || candidate.developer" class="candidate-meta">
                  {{ [candidate.developer, candidate.publisher].filter(Boolean).join(' · ') }}
                </p>
                <p v-if="candidate.gameType || candidate.versionParentIgdbId" class="candidate-meta">
                  {{ candidate.gameType || 'type 미확인' }}
                  <template v-if="candidate.versionParentIgdbId"> · parent #{{ candidate.versionParentIgdbId }}</template>
                </p>
                <button
                  type="button"
                  class="resolve-button"
                  :disabled="resolvingId === review.id"
                  @click="resolveCandidate(review, candidate)"
                >
                  {{ candidate.entityKind === 'GAME' ? '이 게임으로 확정' : '이 프랜차이즈로 확정' }}
                </button>
              </div>
            </div>
            <p v-else class="no-candidate">IGDB/로컬 후보가 없습니다. 게임·프랜차이즈 관리 화면에서 기준 데이터를 먼저 등록한 뒤 다시 검토하세요.</p>
          </div>

          <div v-else class="resolved-line">
            <span>{{ statusLabel(review.status) }}</span>
            <span v-if="review.resolvedGameId">Game #{{ review.resolvedGameId }}</span>
            <span v-if="review.resolvedFranchiseId">Franchise #{{ review.resolvedFranchiseId }}</span>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { entityReviewApi } from '@/api/entityReview.js'

const reviews = ref([])
const loading = ref(false)
const resolvingId = ref(null)
const error = ref('')
const notice = ref('')
const status = ref('PENDING')
const search = ref('')

const tabs = [
  { value: 'PENDING', label: '검토 필요' },
  { value: 'RESOLVED', label: '확정' },
  { value: 'REJECTED', label: '관련 없음' },
  { value: '', label: '전체' }
]

const filteredReviews = computed(() => {
  const keyword = search.value.toLocaleLowerCase('ko-KR')
  if (!keyword) return reviews.value
  return reviews.value.filter((item) => [item.detectedName, item.articleTitle, item.articleSourceName, item.reason]
    .filter(Boolean).join(' ').toLocaleLowerCase('ko-KR').includes(keyword))
})

function extractData(response) { return response?.data?.data ?? response?.data }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function kindLabel(kind) { return kind === 'GAME' ? '게임 후보' : '프랜차이즈 후보' }
function statusLabel(value) { return value === 'RESOLVED' ? '확정 완료' : value === 'REJECTED' ? '관련 없음' : '검토 필요' }
function confidencePercent(value) { return value == null ? '-' : Math.round(Number(value) * 100) }

async function loadReviews() {
  loading.value = true
  error.value = ''
  try {
    const data = extractData(await entityReviewApi.getAll(status.value))
    reviews.value = Array.isArray(data) ? data : []
  } catch (err) {
    error.value = errorMessage(err, '검토 큐를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function changeStatus(value) {
  status.value = value
  await loadReviews()
}

async function resolveCandidate(review, candidate) {
  resolvingId.value = review.id
  error.value = ''
  try {
    const payload = {
      resolutionType: candidate.entityKind,
      localEntityId: candidate.localId || null,
      igdbId: candidate.localId ? null : (candidate.igdbId || null)
    }
    await entityReviewApi.resolve(review.id, payload)
    notice.value = `${candidate.displayName || candidate.name}으로 확정했습니다. 기사/Topic 관계를 다시 동기화합니다.`
    await loadReviews()
  } catch (err) {
    error.value = errorMessage(err, '검토 결정을 반영하지 못했습니다.')
  } finally {
    resolvingId.value = null
  }
}

async function resolveUnrelated(review) {
  if (!window.confirm(`'${review.detectedName}'을(를) 기사와 관련 없는 엔티티로 처리하시겠습니까?`)) return
  resolvingId.value = review.id
  error.value = ''
  try {
    await entityReviewApi.resolve(review.id, { resolutionType: 'UNRELATED' })
    notice.value = '관련 없음으로 처리했습니다.'
    await loadReviews()
  } catch (err) {
    error.value = errorMessage(err, '검토 결정을 반영하지 못했습니다.')
  } finally {
    resolvingId.value = null
  }
}

onMounted(loadReviews)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #fff; color: #1d2025; }
.admin-main { width: min(1040px, calc(100% - 48px)); margin: 0 auto; padding: 52px 0 96px; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding-bottom: 30px; border-bottom: 1px solid #dfe2e6; }
.eyebrow { margin: 0 0 9px; color: #8a919b; font-size: 10px; font-weight: 700; letter-spacing: .08em; }
.page-heading h1 { margin: 0 0 10px; font-size: 30px; letter-spacing: -.035em; }
.page-heading p:last-child { margin: 0; color: #727984; font-size: 13px; }
.refresh-button { padding: 8px 11px; border: 1px solid #d2d6db; background: #fff; color: #5e6670; font-size: 11px; font-weight: 700; }
.message-banner { display: flex; justify-content: space-between; gap: 18px; margin-top: 22px; padding: 10px 0; border-bottom: 1px solid #d8dce1; font-size: 12px; }
.message-banner.error { color: #a33a3a; border-bottom-color: #e4b6b6; }
.message-banner.notice { color: #4e5c51; }
.message-banner button { background: none; color: #777e88; font-size: 11px; }
.toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 28px 0 12px; }
.status-tabs { display: flex; gap: 4px; }
.status-tab { padding: 7px 10px; border: 1px solid transparent; background: #fff; color: #838a94; font-size: 11px; font-weight: 700; }
.status-tab.active { border-color: #22262c; color: #22262c; }
.search-box { width: min(340px, 100%); }
.search-box input { width: 100%; height: 34px; border: 0; border-bottom: 1px solid #cfd3d8; outline: 0; font: inherit; font-size: 12px; }
.review-list { border-top: 2px solid #17191d; }
.review-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 18px 26px; padding: 22px 2px; border-bottom: 1px solid #e8eaed; }
.title-line { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.title-line h2 { margin: 0; font-size: 16px; }
.kind-badge { padding: 2px 6px; border: 1px solid #d4d8dd; color: #68707a; font-size: 9px; font-weight: 800; }
.confidence { color: #818892; font-size: 10px; }
.article-title { margin: 8px 0 0; font-size: 12px; font-weight: 700; }
.meta, .reason { margin: 5px 0 0; color: #7f8791; font-size: 11px; line-height: 1.5; }
.reason { color: #5f6670; }
.row-actions { display: flex; gap: 12px; align-items: flex-start; }
.text-button { padding: 3px 0; background: none; color: #727984; font-size: 11px; font-weight: 700; }
.text-button.danger { color: #9a5555; }
.candidate-panel { grid-column: 1 / -1; padding-top: 18px; border-top: 1px solid #e1e4e8; }
.panel-heading { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 12px; }
.panel-heading strong { font-size: 12px; }
.panel-heading span { color: #9299a2; font-size: 10px; }
.candidate-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.candidate-card { padding: 14px; border: 1px solid #dfe2e6; }
.candidate-title { display: flex; justify-content: space-between; gap: 12px; }
.candidate-title strong { font-size: 12px; }
.candidate-title span { color: #9299a2; font-size: 9px; font-weight: 800; }
.canonical, .candidate-meta { margin: 5px 0 0; color: #7f8791; font-size: 10px; }
.resolve-button { margin-top: 12px; padding: 7px 10px; border: 1px solid #25292f; background: #25292f; color: #fff; font-size: 10px; font-weight: 700; }
.resolve-button:disabled { opacity: .5; }
.no-candidate, .empty-state, .resolved-line { padding: 24px 0; color: #858c95; font-size: 12px; }
.resolved-line { grid-column: 1 / -1; display: flex; gap: 16px; padding: 10px 0 0; border-top: 1px solid #eceef1; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 760px) {
  .admin-main { width: min(100% - 32px, 1040px); }
  .page-heading, .toolbar { align-items: stretch; flex-direction: column; }
  .search-box { width: 100%; }
  .review-row { grid-template-columns: 1fr; }
  .candidate-grid { grid-template-columns: 1fr; }
}
</style>
