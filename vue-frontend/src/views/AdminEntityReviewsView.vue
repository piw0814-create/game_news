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
        <div class="filter-groups">
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
          <div class="kind-tabs" aria-label="엔티티 종류">
            <button
              v-for="tab in kindTabs"
              :key="tab.value"
              type="button"
              class="kind-tab"
              :class="{ active: kindFilter === tab.value }"
              @click="kindFilter = tab.value"
            >
              {{ tab.label }}
            </button>
          </div>
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
              class="text-button"
              :disabled="resolvingId === review.id"
              @click="recheckReview(review)"
            >최신 후보 재조회</button>
            <button
              v-if="review.status === 'PENDING'"
              type="button"
              class="text-button danger"
              :disabled="resolvingId === review.id"
              @click="resolveUnrelated(review)"
            >관련 없음</button>
            <button
              v-else
              type="button"
              class="text-button"
              :disabled="resolvingId === review.id"
              @click="reopenReview(review)"
            >재검토</button>
          </div>

          <div v-if="review.status === 'PENDING'" class="candidate-panel">
            <div class="panel-heading">
              <strong>판정 후보</strong>
              <span>IGDB 우선 · 후보 순서는 참고용, 애매하면 관리자 결정</span>
            </div>

            <div v-if="review.candidates?.length" class="candidate-groups">
              <section
                v-for="group in candidateGroups(review)"
                :key="`${review.id}-${group.kind}`"
                class="candidate-group"
              >
                <div class="candidate-group-heading">
                  <strong>{{ group.label }}</strong>
                  <span>{{ group.items.length }}개</span>
                </div>
                <div class="candidate-grid">
                  <div
                    v-for="(candidate, index) in group.items"
                    :key="`${candidate.source}-${candidate.entityKind}-${candidate.localId || candidate.igdbId || candidate.igdbCollectionId || index}`"
                    class="candidate-card"
                    :class="{
                      recommended: candidate.entityKind === review.entityKind,
                      priority: isPriorityCandidate(review, group, index)
                    }"
                  >
                    <div class="candidate-title">
                      <strong>{{ candidate.displayName || candidate.name }}</strong>
                      <div class="candidate-badges">
                        <span v-if="isPriorityCandidate(review, group, index)" class="priority-badge">우선 확인</span>
                        <span>{{ candidate.entityKind === 'GAME' ? 'GAME' : 'FRANCHISE' }}</span>
                      </div>
                    </div>
                    <p v-if="candidate.displayName && candidate.displayName !== candidate.name" class="canonical">{{ candidate.name }}</p>
                    <p class="candidate-meta">
                      {{ candidateSourceLabel(candidate.source) }}
                      <template v-if="candidate.localId"> · Local #{{ candidate.localId }}</template>
                      <template v-if="candidate.igdbId"> · #{{ candidate.igdbId }}</template>
                      <template v-if="candidate.igdbCollectionId"> · #{{ candidate.igdbCollectionId }}</template>
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
              </section>
            </div>
            <p v-else class="no-candidate">현재 IGDB/로컬 후보를 찾지 못했습니다. IGDB나 로컬 기준 데이터가 추가된 뒤 ‘최신 후보 재조회’를 누르면 다시 검색합니다.</p>
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
const kindFilter = ref('ALL')
const search = ref('')

const tabs = [
  { value: 'PENDING', label: '검토 필요' },
  { value: 'RESOLVED', label: '확정' },
  { value: 'REJECTED', label: '관련 없음' },
  { value: '', label: '전체' }
]

const kindTabs = [
  { value: 'ALL', label: '전체 유형' },
  { value: 'GAME', label: '게임' },
  { value: 'FRANCHISE', label: '프랜차이즈' }
]

const filteredReviews = computed(() => {
  const keyword = search.value.toLocaleLowerCase('ko-KR')
  return reviews.value.filter((item) => {
    if (kindFilter.value !== 'ALL' && item.entityKind !== kindFilter.value) return false
    if (!keyword) return true
    return [item.detectedName, item.articleTitle, item.articleSourceName, item.reason]
      .filter(Boolean).join(' ').toLocaleLowerCase('ko-KR').includes(keyword)
  })
})

function extractData(response) { return response?.data?.data ?? response?.data }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function kindLabel(kind) { return kind === 'GAME' ? '게임 후보' : '프랜차이즈 후보' }
function candidateSourceLabel(source) {
  if (source === 'LOCAL_IGDB') return 'LOCAL · IGDB'
  if (source === 'IGDB_FRANCHISE') return 'IGDB · Franchise'
  if (source === 'IGDB_COLLECTION') return 'IGDB · Series'
  return source || '-'
}
function statusLabel(value) { return value === 'RESOLVED' ? '확정 완료' : value === 'REJECTED' ? '관련 없음' : '검토 필요' }
function confidencePercent(value) { return value == null ? '-' : Math.round(Number(value) * 100) }

function candidateGroups(review) {
  const candidates = Array.isArray(review.candidates) ? review.candidates : []
  const preferredKind = review.entityKind
  const alternateKind = preferredKind === 'GAME' ? 'FRANCHISE' : 'GAME'
  const groups = [
    {
      kind: preferredKind,
      label: preferredKind === 'GAME' ? '추천 게임 후보' : '추천 프랜차이즈 후보',
      items: candidates
        .filter((item) => item.entityKind === preferredKind)
        .slice()
        .sort((a, b) => candidateRank(review, a) - candidateRank(review, b))
    },
    {
      kind: alternateKind,
      label: alternateKind === 'GAME' ? '다른 해석 · 게임' : '다른 해석 · 프랜차이즈',
      items: candidates
        .filter((item) => item.entityKind === alternateKind)
        .slice()
        .sort((a, b) => candidateRank(review, a) - candidateRank(review, b))
    }
  ]
  return groups.filter((group) => group.items.length)
}

function candidateRank(review, candidate) {
  const detected = String(review.detectedName || '').trim().toLocaleLowerCase('ko-KR')
  const name = String(candidate.name || '').trim().toLocaleLowerCase('ko-KR')
  let score = 100

  if (name && name === detected) score -= 40
  if (candidate.source === 'LOCAL_IGDB') score -= 20
  else if (candidate.source === 'LOCAL') score -= 8

  if (candidate.entityKind === 'GAME') {
    const type = String(candidate.gameType || '').toLocaleLowerCase('en-US')
    if (type === 'main game') score -= 18
    else if (type.includes('remaster') || type.includes('remake')) score -= 4
    else if (type.includes('port')) score += 2
    else if (type.includes('expansion') || type.includes('dlc') || type.includes('pack') || type.includes('addon')) score += 8
    if (candidate.versionParentIgdbId) score += 6
  }

  return score
}

function isPriorityCandidate(review, group, index) {
  return group.kind === review.entityKind && index === 0 && group.items.length > 1
}

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
      igdbId: candidate.localId ? null : (candidate.igdbId || null),
      igdbCollectionId: candidate.localId ? null : (candidate.igdbCollectionId || null)
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

async function recheckReview(review) {
  resolvingId.value = review.id
  error.value = ''
  try {
    const refreshed = extractData(await entityReviewApi.recheck(review.id))
    notice.value = refreshed?.status === 'RESOLVED'
      ? '최신 로컬/IGDB 기준으로 안전하게 자동 확정했습니다. 기사/Topic 관계도 다시 동기화합니다.'
      : '최신 로컬/IGDB 기준으로 후보를 다시 조회했습니다.'
    await loadReviews()
  } catch (err) {
    error.value = errorMessage(err, '최신 후보를 다시 조회하지 못했습니다.')
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

async function reopenReview(review) {
  if (!window.confirm(`'${review.detectedName}' 검토 결정을 되돌리고 다시 검토하시겠습니까? 기존 기사/Topic 관계도 함께 되돌립니다.`)) return
  resolvingId.value = review.id
  error.value = ''
  try {
    await entityReviewApi.reopen(review.id)
    notice.value = '검토 항목을 다시 PENDING으로 돌렸습니다. 후보도 최신 IGDB 기준으로 다시 조회했습니다.'
    status.value = 'PENDING'
    await loadReviews()
  } catch (err) {
    error.value = errorMessage(err, '재검토 상태로 되돌리지 못했습니다.')
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
.filter-groups { display: flex; flex-direction: column; gap: 8px; }
.status-tabs, .kind-tabs { display: flex; gap: 4px; flex-wrap: wrap; }
.status-tab { padding: 7px 10px; border: 1px solid transparent; background: #fff; color: #838a94; font-size: 11px; font-weight: 700; }
.status-tab.active { border-color: #22262c; color: #22262c; }
.kind-tab { padding: 5px 8px; border: 0; background: #f5f6f7; color: #8a919a; font-size: 10px; font-weight: 700; }
.kind-tab.active { background: #2a2e34; color: #fff; }
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
.candidate-groups { display: flex; flex-direction: column; gap: 18px; }
.candidate-group { padding-top: 2px; }
.candidate-group + .candidate-group { padding-top: 16px; border-top: 1px dashed #dfe2e6; }
.candidate-group-heading { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 9px; }
.candidate-group-heading strong { font-size: 11px; }
.candidate-group-heading span { color: #9aa0a8; font-size: 9px; }
.candidate-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.candidate-card { padding: 14px; border: 1px solid #dfe2e6; }
.candidate-card.recommended { border-color: #aeb4bc; }
.candidate-card.priority { border-color: #676f79; }
.candidate-title { display: flex; justify-content: space-between; gap: 12px; }
.candidate-title strong { font-size: 12px; }
.candidate-title span { color: #9299a2; font-size: 9px; font-weight: 800; }
.candidate-badges { display: flex; align-items: center; gap: 6px; }
.candidate-title .priority-badge { padding: 2px 5px; background: #eef0f2; color: #4f5761; }
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
