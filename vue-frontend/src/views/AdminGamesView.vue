<template>
  <div class="admin-page">
    <AppHeader />

    <main class="admin-main">
      <section class="page-heading">
        <div>
          <p class="eyebrow">ADMIN · GAME REVIEW</p>
          <h1>게임 관리</h1>
          <p>AI가 등록한 게임을 확인하고 수정·확정·병합·거절할 수 있습니다.</p>
        </div>
        <button type="button" class="refresh-button" :disabled="loading" @click="loadGames">
          {{ loading ? '불러오는 중' : '새로고침' }}
        </button>
      </section>

      <div v-if="error" class="message-banner error" role="alert">
        <span>{{ error }}</span>
        <button type="button" @click="error = ''">닫기</button>
      </div>

      <div v-if="notice" class="message-banner notice" role="status">
        <span>{{ notice }}</span>
        <button type="button" @click="notice = ''">닫기</button>
      </div>

      <section class="toolbar">
        <div class="status-tabs" role="tablist" aria-label="게임 검수 상태">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            type="button"
            class="status-tab"
            :class="{ active: activeStatus === tab.value }"
            @click="activeStatus = tab.value"
          >
            {{ tab.label }}
            <span>{{ countByStatus(tab.value) }}</span>
          </button>
        </div>

        <label class="search-box">
          <span class="sr-only">게임 검색</span>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" />
            <path d="m16 16 4 4" />
          </svg>
          <input
            v-model.trim="searchKeyword"
            type="search"
            placeholder="게임명 · 퍼블리셔 검색"
            autocomplete="off"
          />
        </label>
      </section>

      <section class="game-section">
        <div class="section-heading">
          <h2>{{ currentTabLabel }}</h2>
          <span>{{ filteredGames.length }}개</span>
        </div>

        <div v-if="loading" class="loading-state">
          <div v-for="index in 4" :key="index" class="loading-row">
            <span class="loading-line wide"></span>
            <span class="loading-line"></span>
          </div>
        </div>

        <div v-else-if="filteredGames.length">
          <div class="game-list">
            <article v-for="game in pagedGames" :key="game.id" class="game-row">
            <div class="game-summary">
              <div class="title-line">
                <h3>{{ game.name }}</h3>
                <span class="status-badge" :class="statusClass(game.reviewStatus)">
                  {{ statusLabel(game.reviewStatus) }}
                </span>
              </div>

              <p class="publisher">{{ game.publisher || '퍼블리셔 정보 없음' }}</p>
              <p class="meta">{{ gameMeta(game) }}</p>

              <div class="audit-line">
                <span>{{ sourceLabel(game.registrationSource) }}</span>
                <span v-if="game.registrationConfidence != null">
                  신뢰도 {{ confidencePercent(game.registrationConfidence) }}%
                </span>
                <span v-if="game.sourceArticleId">기사 #{{ game.sourceArticleId }}</span>
              </div>
            </div>

            <div class="row-actions">
              <button type="button" class="text-button" @click="toggleEdit(game)">수정</button>
              <button
                v-if="game.reviewStatus !== 'CONFIRMED'"
                type="button"
                class="text-button strong"
                :disabled="actionGameId === game.id"
                @click="confirmGame(game)"
              >
                확정
              </button>
              <button type="button" class="text-button" @click="toggleMerge(game)">병합</button>
              <button
                v-if="game.reviewStatus !== 'CONFIRMED'"
                type="button"
                class="text-button danger"
                :disabled="actionGameId === game.id"
                @click="rejectGame(game)"
              >
                거절
              </button>
            </div>

            <form v-if="editingGameId === game.id" class="inline-panel edit-panel" @submit.prevent="saveEdit(game)">
              <div class="panel-heading">
                <strong>게임 정보 수정</strong>
                <button type="button" @click="closePanels">닫기</button>
              </div>

              <div class="form-grid">
                <label class="field full">
                  <span>게임명</span>
                  <input v-model.trim="editForm.name" required />
                </label>
                <label class="field">
                  <span>퍼블리셔</span>
                  <input v-model.trim="editForm.publisher" />
                </label>
                <label class="field">
                  <span>장르</span>
                  <input v-model.trim="editForm.genre" />
                </label>
                <label class="field">
                  <span>플랫폼</span>
                  <input v-model.trim="editForm.platform" />
                </label>
                <label class="field">
                  <span>이미지 URL</span>
                  <input v-model.trim="editForm.imageUrl" />
                </label>
              </div>

              <div class="panel-actions">
                <button type="button" class="secondary-button" @click="closePanels">취소</button>
                <button type="submit" class="primary-button" :disabled="actionGameId === game.id">
                  {{ actionGameId === game.id ? '저장 중...' : '저장' }}
                </button>
              </div>
            </form>

            <div v-if="mergingGameId === game.id" class="inline-panel merge-panel">
              <div class="panel-heading">
                <strong>기존 게임과 병합</strong>
                <button type="button" @click="closePanels">닫기</button>
              </div>

              <label class="merge-search">
                <span>병합 대상</span>
                <input
                  v-model.trim="mergeKeyword"
                  type="search"
                  placeholder="게임명 검색"
                  autocomplete="off"
                />
              </label>

              <div v-if="mergeTargets(game.id).length" class="merge-results">
                <button
                  v-for="target in mergeTargets(game.id)"
                  :key="target.id"
                  type="button"
                  class="merge-target"
                  :disabled="actionGameId === game.id"
                  @click="mergeGame(game, target)"
                >
                  <span>
                    <strong>{{ target.name }}</strong>
                    <small>{{ target.publisher || '퍼블리셔 정보 없음' }}</small>
                  </span>
                  <em>#{{ target.id }}</em>
                </button>
              </div>

              <p v-else class="merge-empty">
                {{ mergeKeyword ? '일치하는 게임이 없습니다.' : '병합할 게임을 검색하세요.' }}
              </p>
            </div>
            </article>
          </div>

          <nav v-if="totalPages > 1" class="pagination" aria-label="게임 관리 페이지">
            <button
              type="button"
              :disabled="currentPage === 1"
              @click="changePage(currentPage - 1)"
            >
              이전
            </button>
            <span>{{ currentPage }} / {{ totalPages }}</span>
            <button
              type="button"
              :disabled="currentPage === totalPages"
              @click="changePage(currentPage + 1)"
            >
              다음
            </button>
          </nav>
        </div>

        <div v-else class="empty-state">
          <strong>표시할 게임이 없습니다.</strong>
          <p>필터나 검색어를 변경해보세요.</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { gameApi } from '@/api/game.js'

const games = ref([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
const searchKeyword = ref('')
const activeStatus = ref('AI_CREATED')
const editingGameId = ref(null)
const mergingGameId = ref(null)
const actionGameId = ref(null)
const mergeKeyword = ref('')
const currentPage = ref(1)

const PAGE_SIZE = 10

const editForm = reactive({
  name: '',
  publisher: '',
  genre: '',
  platform: '',
  imageUrl: ''
})

const tabs = [
  { value: 'REVIEW_REQUIRED', label: '검토 필요' },
  { value: 'AI_CREATED', label: 'AI 자동등록' },
  { value: 'ALL', label: '전체' }
]

const currentTabLabel = computed(() => tabs.find((tab) => tab.value === activeStatus.value)?.label || '게임')

const filteredGames = computed(() => {
  const keyword = searchKeyword.value.toLocaleLowerCase('ko-KR')

  return games.value.filter((game) => {
    if (activeStatus.value !== 'ALL' && game.reviewStatus !== activeStatus.value) {
      return false
    }

    if (!keyword) return true

    const searchable = [game.name, game.publisher, game.genre, game.platform]
      .filter(Boolean)
      .join(' ')
      .toLocaleLowerCase('ko-KR')

    return searchable.includes(keyword)
  })
})

const totalPages = computed(() =>
  Math.max(1, Math.ceil(filteredGames.value.length / PAGE_SIZE))
)

const pagedGames = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filteredGames.value.slice(start, start + PAGE_SIZE)
})

watch([activeStatus, searchKeyword], () => {
  currentPage.value = 1
  closePanels()
})

watch(totalPages, (pageCount) => {
  if (currentPage.value > pageCount) currentPage.value = pageCount
})

function extractData(response) {
  return response?.data?.data ?? response?.data
}

function errorMessage(err, fallback) {
  return err?.response?.data?.message || err?.message || fallback
}

function changePage(page) {
  const nextPage = Math.min(Math.max(1, page), totalPages.value)
  if (nextPage === currentPage.value) return

  currentPage.value = nextPage
  closePanels()
}

async function loadGames() {
  loading.value = true
  error.value = ''

  try {
    const response = await gameApi.getAdminAll()
    const data = extractData(response)
    games.value = Array.isArray(data) ? data : []
  } catch (err) {
    error.value = errorMessage(err, '게임 목록을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function countByStatus(status) {
  if (status === 'ALL') return games.value.length
  return games.value.filter((game) => game.reviewStatus === status).length
}

function statusLabel(status) {
  return {
    AI_CREATED: 'AI 자동등록',
    REVIEW_REQUIRED: '검토 필요',
    CONFIRMED: '확정'
  }[status] || status
}

function statusClass(status) {
  return String(status || '').toLowerCase().replaceAll('_', '-')
}

function sourceLabel(source) {
  return source === 'AI' ? 'AI 등록' : '수동 등록'
}

function confidencePercent(value) {
  return Math.round(Number(value) * 100)
}

function gameMeta(game) {
  const values = [game.genre, game.platform].filter(Boolean)
  return values.length ? values.join(' · ') : '추가 정보 없음'
}

function clearMessages() {
  error.value = ''
  notice.value = ''
}

function closePanels() {
  editingGameId.value = null
  mergingGameId.value = null
  mergeKeyword.value = ''
}

function toggleEdit(game) {
  clearMessages()

  if (editingGameId.value === game.id) {
    closePanels()
    return
  }

  editingGameId.value = game.id
  mergingGameId.value = null
  mergeKeyword.value = ''
  editForm.name = game.name || ''
  editForm.publisher = game.publisher || ''
  editForm.genre = game.genre || ''
  editForm.platform = game.platform || ''
  editForm.imageUrl = game.imageUrl || ''
}

function toggleMerge(game) {
  clearMessages()

  if (mergingGameId.value === game.id) {
    closePanels()
    return
  }

  mergingGameId.value = game.id
  editingGameId.value = null
  mergeKeyword.value = ''
}

function replaceGame(updated) {
  const index = games.value.findIndex((game) => game.id === updated.id)
  if (index >= 0) {
    games.value[index] = updated
  }
}

async function saveEdit(game) {
  clearMessages()
  actionGameId.value = game.id

  try {
    const payload = {
      name: editForm.name,
      publisher: editForm.publisher || null,
      genre: editForm.genre || null,
      platform: editForm.platform || null,
      imageUrl: editForm.imageUrl || null
    }
    const response = await gameApi.updateAdmin(game.id, payload)
    const updated = extractData(response)
    replaceGame(updated)
    notice.value = `${updated.name} 정보를 수정했습니다.`
    closePanels()
  } catch (err) {
    error.value = errorMessage(err, '게임 정보를 수정하지 못했습니다.')
  } finally {
    actionGameId.value = null
  }
}

async function confirmGame(game) {
  clearMessages()
  actionGameId.value = game.id

  try {
    const response = await gameApi.confirmAdmin(game.id)
    const updated = extractData(response)
    replaceGame(updated)
    notice.value = `${updated.name} 검수를 확정했습니다.`
  } catch (err) {
    error.value = errorMessage(err, '게임을 확정하지 못했습니다.')
  } finally {
    actionGameId.value = null
  }
}

function mergeTargets(sourceGameId) {
  const keyword = mergeKeyword.value.toLocaleLowerCase('ko-KR')
  if (!keyword) return []

  return games.value
    .filter((game) => game.id !== sourceGameId)
    .filter((game) => {
      const searchable = [game.name, game.publisher]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('ko-KR')
      return searchable.includes(keyword)
    })
    .slice(0, 8)
}

async function mergeGame(source, target) {
  const confirmed = window.confirm(
    `“${source.name}”을(를) “${target.name}”에 병합하시겠습니까?\n연결된 기사·토픽·관심 게임은 대상 게임으로 이동합니다.`
  )
  if (!confirmed) return

  clearMessages()
  actionGameId.value = source.id

  try {
    await gameApi.mergeAdmin(source.id, target.id)
    games.value = games.value.filter((game) => game.id !== source.id)
    notice.value = `${source.name}을(를) ${target.name}에 병합했습니다.`
    closePanels()
  } catch (err) {
    error.value = errorMessage(err, '게임을 병합하지 못했습니다.')
  } finally {
    actionGameId.value = null
  }
}

async function rejectGame(game) {
  const confirmed = window.confirm(
    `“${game.name}”을(를) 거절하고 삭제하시겠습니까?\n연결된 기사·토픽·관심 게임 관계도 함께 정리됩니다.`
  )
  if (!confirmed) return

  clearMessages()
  actionGameId.value = game.id

  try {
    await gameApi.rejectAdmin(game.id)
    games.value = games.value.filter((item) => item.id !== game.id)
    notice.value = `${game.name}을(를) 거절했습니다.`
    closePanels()
  } catch (err) {
    error.value = errorMessage(err, '게임을 거절하지 못했습니다.')
  } finally {
    actionGameId.value = null
  }
}

onMounted(loadGames)
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  background: #fff;
  color: #1d2025;
}

.admin-main {
  width: min(1040px, calc(100% - 48px));
  margin: 0 auto;
  padding: 52px 0 96px;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
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

.page-heading p:last-child {
  margin: 0;
  color: #727984;
  font-size: 13px;
}

.refresh-button {
  flex: 0 0 auto;
  padding: 8px 11px;
  border: 1px solid #d2d6db;
  background: #fff;
  color: #5e6670;
  font-size: 11px;
  font-weight: 700;
}

.refresh-button:hover:not(:disabled) {
  border-color: #8f969f;
  color: #1f2329;
}

.refresh-button:disabled {
  cursor: default;
  opacity: 0.48;
}

.message-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 22px;
  padding: 10px 0;
  border-bottom: 1px solid #d8dce1;
  font-size: 12px;
}

.message-banner.error {
  border-bottom-color: #e4b6b6;
  color: #a33a3a;
}

.message-banner.notice {
  color: #4e5c51;
}

.message-banner button {
  flex: 0 0 auto;
  background: none;
  color: #777e88;
  font-size: 11px;
}

.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding: 28px 0 6px;
}

.status-tabs {
  display: flex;
  gap: 20px;
  min-width: 0;
}

.status-tab {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 8px 0;
  background: none;
  color: #858c96;
  font-size: 12px;
  font-weight: 700;
  border-bottom: 2px solid transparent;
}

.status-tab span {
  font-size: 10px;
  font-weight: 600;
}

.status-tab.active {
  border-bottom-color: #202329;
  color: #202329;
}

.search-box {
  display: flex;
  align-items: center;
  width: min(360px, 100%);
  height: 40px;
  border-bottom: 1px solid #cfd3d8;
}

.search-box:focus-within {
  border-bottom-color: #4d535c;
}

.search-box svg {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  margin-right: 10px;
  fill: none;
  stroke: #8d949d;
  stroke-width: 1.5;
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #25292f;
  font: inherit;
  font-size: 12px;
}

.game-section {
  padding-top: 24px;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #17191d;
}

.section-heading h2 {
  margin: 0;
  font-size: 17px;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.section-heading span {
  color: #8a9099;
  font-size: 12px;
}

.game-list {
  border-bottom: 1px solid #dfe2e6;
}

.game-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  column-gap: 28px;
  padding: 22px 2px;
  border-bottom: 1px solid #eceef1;
}

.game-row:last-child {
  border-bottom: 0;
}

.game-summary {
  min-width: 0;
}

.title-line {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.title-line h3 {
  margin: 0;
  color: #202329;
  font-size: 15px;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 2px 6px;
  border: 1px solid #d8dce1;
  color: #69717b;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.01em;
}

.status-badge.review-required {
  border-color: #d8c7a7;
  color: #8a682b;
}

.status-badge.ai-created {
  border-color: #b9c8d8;
  color: #4f6b86;
}

.status-badge.confirmed {
  border-color: #bdcdbf;
  color: #58705c;
}

.publisher {
  margin: 6px 0 0;
  color: #656c76;
  font-size: 12px;
  font-weight: 600;
}

.meta {
  margin: 3px 0 0;
  color: #9197a0;
  font-size: 11px;
}

.audit-line {
  display: flex;
  flex-wrap: wrap;
  gap: 11px;
  margin-top: 8px;
  color: #a0a5ad;
  font-size: 10px;
}

.row-actions {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.text-button {
  padding: 3px 0;
  background: none;
  color: #727984;
  font-size: 11px;
  font-weight: 700;
}

.text-button:hover:not(:disabled) {
  color: #1f2329;
}

.text-button.strong {
  color: #303840;
}

.text-button.danger {
  color: #9a5656;
}

.text-button:disabled {
  cursor: default;
  opacity: 0.45;
}

.inline-panel {
  grid-column: 1 / -1;
  margin-top: 20px;
  padding: 18px 0 2px;
  border-top: 1px solid #dfe2e6;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.panel-heading strong {
  font-size: 12px;
}

.panel-heading button {
  background: none;
  color: #8a919b;
  font-size: 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
}

.field {
  display: grid;
  gap: 6px;
}

.field.full {
  grid-column: 1 / -1;
}

.field span,
.merge-search span {
  color: #7b828c;
  font-size: 10px;
  font-weight: 700;
}

.field input,
.merge-search input {
  width: 100%;
  height: 36px;
  padding: 0 2px;
  border: 0;
  border-bottom: 1px solid #cfd3d8;
  outline: 0;
  background: transparent;
  color: #22262c;
  font: inherit;
  font-size: 12px;
}

.field input:focus,
.merge-search input:focus {
  border-bottom-color: #4d535c;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.secondary-button,
.primary-button {
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 700;
}

.secondary-button {
  border: 1px solid #d3d7dc;
  background: #fff;
  color: #69717b;
}

.primary-button {
  border: 1px solid #25292f;
  background: #25292f;
  color: #fff;
}

.primary-button:disabled {
  cursor: default;
  opacity: 0.45;
}

.merge-search {
  display: grid;
  gap: 5px;
  width: min(520px, 100%);
}

.merge-results {
  width: min(620px, 100%);
  margin-top: 12px;
  border-top: 1px solid #eceef1;
}

.merge-target {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 12px 2px;
  border-bottom: 1px solid #eceef1;
  background: #fff;
  text-align: left;
}

.merge-target:hover:not(:disabled) {
  background: #fafbfc;
}

.merge-target span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.merge-target strong {
  color: #2a2e34;
  font-size: 12px;
}

.merge-target small {
  color: #9197a0;
  font-size: 10px;
}

.merge-target em {
  flex: 0 0 auto;
  color: #9ba1aa;
  font-size: 10px;
  font-style: normal;
}

.merge-empty {
  margin: 14px 0 0;
  color: #969ca5;
  font-size: 11px;
}

.loading-state {
  border-bottom: 1px solid #dfe2e6;
}

.loading-row {
  display: grid;
  gap: 8px;
  padding: 22px 2px;
  border-bottom: 1px solid #eceef1;
}

.loading-line {
  display: block;
  width: 180px;
  height: 9px;
  background: #f0f1f3;
}

.loading-line.wide {
  width: min(340px, 72%);
  height: 13px;
}

.empty-state {
  padding: 54px 0;
  border-bottom: 1px solid #dfe2e6;
  text-align: center;
}

.empty-state strong {
  color: #4e555f;
  font-size: 13px;
}

.empty-state p {
  margin: 4px 0 0;
  color: #9aa0a8;
  font-size: 11px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 46px;
  border-bottom: 1px solid #dfe2e6;
}

.pagination button {
  padding: 5px 2px;
  background: none;
  color: #626a74;
  font-size: 11px;
  font-weight: 700;
}

.pagination button:hover:not(:disabled) {
  color: #202329;
}

.pagination button:disabled {
  cursor: default;
  color: #c1c5ca;
}

.pagination span {
  min-width: 44px;
  color: #8a9099;
  font-size: 10px;
  text-align: center;
}

@media (max-width: 760px) {
  .admin-main {
    width: min(100% - 32px, 1040px);
    padding-top: 36px;
  }

  .page-heading {
    align-items: flex-start;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .status-tabs {
    gap: 14px;
    overflow-x: auto;
  }

  .search-box {
    width: 100%;
  }

  .game-row {
    grid-template-columns: 1fr;
    row-gap: 14px;
  }

  .row-actions {
    flex-wrap: wrap;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .field.full {
    grid-column: auto;
  }
}
</style>
