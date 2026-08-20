<template>
  <div class="admin-page">
    <AppHeader />

    <main class="admin-main">
      <section class="page-heading">
        <div>
          <p class="eyebrow">ADMIN · FRANCHISE DATA</p>
          <h1>프랜차이즈 관리</h1>
          <p>IP 기준 정보를 등록하고 게임 소속 관계를 관리합니다.</p>
        </div>
        <button type="button" class="refresh-button" :disabled="loading" @click="loadAll">
          {{ loading ? '불러오는 중' : '새로고침' }}
        </button>
      </section>

      <div v-if="error" class="message-banner error" role="alert">
        <span>{{ error }}</span><button type="button" @click="error = ''">닫기</button>
      </div>
      <div v-if="notice" class="message-banner notice" role="status">
        <span>{{ notice }}</span><button type="button" @click="notice = ''">닫기</button>
      </div>

      <section class="create-panel">
        <div class="section-heading compact">
          <h2>신규 등록</h2>
          <span>AI는 프랜차이즈를 자동 생성하지 않습니다.</span>
        </div>
        <form class="create-form" @submit.prevent="createFranchise">
          <label class="field">
            <span>기준 이름*</span>
            <input v-model.trim="createForm.name" required placeholder="예: God of War" />
          </label>
          <label class="field">
            <span>표시 이름</span>
            <input v-model.trim="createForm.displayName" placeholder="예: 갓 오브 워" />
          </label>
          <label class="field full">
            <span>별칭</span>
            <input v-model="createForm.aliasesText" placeholder="쉼표로 구분 (예: GOW, God of War Series)" />
          </label>
          <div class="create-actions">
            <button type="submit" class="primary-button" :disabled="saving">
              {{ saving ? '등록 중...' : '프랜차이즈 등록' }}
            </button>
          </div>
        </form>
      </section>

      <section class="toolbar">
        <div class="section-title">
          <strong>Franchise</strong>
          <span>{{ filteredFranchises.length }}개</span>
        </div>
        <label class="search-box">
          <span class="sr-only">프랜차이즈 검색</span>
          <input v-model.trim="searchKeyword" type="search" placeholder="이름 · 표시명 · 별칭 · IGDB ID 검색" />
        </label>
      </section>

      <section class="franchise-list">
        <article v-for="franchise in filteredFranchises" :key="franchise.id" class="franchise-row">
          <div class="summary">
            <div class="title-line">
              <h3>{{ franchise.displayName || franchise.name }}</h3>
              <span class="source-badge">{{ sourceLabel(franchise.metadataSource) }}</span>
            </div>
            <p v-if="franchise.displayName" class="canonical">{{ franchise.name }}</p>
            <p class="meta">
              소속 게임 {{ franchise.gameCount || 0 }}개
              <template v-if="franchise.igdbId"> · IGDB #{{ franchise.igdbId }}</template>
            </p>
            <p v-if="franchise.aliases?.length" class="aliases">{{ franchise.aliases.join(' · ') }}</p>
          </div>
          <div class="row-actions">
            <button type="button" class="text-button" @click="toggleEdit(franchise)">수정</button>
            <button type="button" class="text-button" @click="toggleGames(franchise)">게임 연결</button>
          </div>

          <form v-if="editingId === franchise.id" class="inline-panel" @submit.prevent="saveEdit(franchise)">
            <div class="panel-heading"><strong>프랜차이즈 정보 수정</strong><button type="button" @click="closePanels">닫기</button></div>
            <div class="form-grid">
              <label class="field"><span>기준 이름*</span><input v-model.trim="editForm.name" required /></label>
              <label class="field"><span>표시 이름</span><input v-model.trim="editForm.displayName" /></label>
              <label class="field full"><span>별칭</span><input v-model="editForm.aliasesText" /></label>
            </div>
            <div class="panel-actions">
              <button type="button" class="secondary-button" @click="closePanels">취소</button>
              <button type="submit" class="primary-button" :disabled="saving">저장</button>
            </div>
          </form>

          <div v-if="managingId === franchise.id" class="inline-panel">
            <div class="panel-heading"><strong>Game ↔ Franchise</strong><button type="button" @click="closePanels">닫기</button></div>
            <div v-if="detailLoadingId === franchise.id" class="empty">연결 정보를 불러오는 중...</div>
            <template v-else-if="detailFor(franchise.id)">
              <div class="linked-games">
                <div v-for="link in detailFor(franchise.id).games" :key="link.gameId" class="game-link-row">
                  <span>
                    <strong>{{ link.gameDisplayName || link.gameName }}</strong>
                    <small v-if="link.gameDisplayName">{{ link.gameName }}</small>
                  </span>
                  <div class="link-actions">
                    <button
                      type="button"
                      class="mini-button"
                      :class="{ active: link.isPrimary }"
                      @click="setPrimary(franchise, link, !link.isPrimary)"
                    >
                      {{ link.isPrimary ? '대표' : '대표 지정' }}
                    </button>
                    <button type="button" class="mini-button danger" @click="unlinkGame(franchise, link)">연결 해제</button>
                  </div>
                </div>
                <p v-if="!detailFor(franchise.id).games?.length" class="empty">연결된 게임이 없습니다.</p>
              </div>

              <label class="game-search">
                <span>게임 추가</span>
                <input v-model.trim="gameSearch" type="search" placeholder="게임명 · 별칭 검색" />
              </label>
              <div v-if="gameSearch" class="game-search-results">
                <button
                  v-for="game in gameCandidates(franchise.id)"
                  :key="game.id"
                  type="button"
                  class="candidate-button"
                  @click="linkGame(franchise, game)"
                >
                  <span><strong>{{ game.displayName || game.name }}</strong><small>{{ game.publisher || '퍼블리셔 정보 없음' }}</small></span>
                  <em>#{{ game.id }}</em>
                </button>
                <p v-if="!gameCandidates(franchise.id).length" class="empty">추가할 게임이 없습니다.</p>
              </div>
            </template>
          </div>
        </article>

        <div v-if="!loading && !filteredFranchises.length" class="empty-state">
          <strong>프랜차이즈가 없습니다.</strong>
          <p>위에서 직접 등록하거나 IGDB 메타데이터 적용으로 생성할 수 있습니다.</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { gameApi } from '@/api/game.js'
import { franchiseApi } from '@/api/franchise.js'

const route = useRoute()
const franchises = ref([])
const games = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const searchKeyword = ref('')
const editingId = ref(null)
const managingId = ref(null)
const detailLoadingId = ref(null)
const details = reactive({})
const gameSearch = ref('')

const createForm = reactive({ name: '', displayName: '', aliasesText: '' })
const editForm = reactive({ name: '', displayName: '', aliasesText: '' })

const filteredFranchises = computed(() => {
  const keyword = searchKeyword.value.toLocaleLowerCase('ko-KR')
  if (!keyword) return franchises.value
  return franchises.value.filter((item) => [item.name, item.displayName, ...(item.aliases || []), item.igdbId]
    .filter(Boolean).join(' ').toLocaleLowerCase('ko-KR').includes(keyword))
})

function extractData(response) { return response?.data?.data ?? response?.data }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function sourceLabel(source) { return source === 'IGDB' ? 'IGDB' : '수동 등록' }
function parseAliases(value) {
  const seen = new Set()
  return String(value || '').split(',').map((item) => item.trim()).filter((item) => {
    if (!item) return false
    const key = item.toLocaleLowerCase('ko-KR')
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}
function detailFor(id) { return details[id] || null }
function closePanels() { editingId.value = null; managingId.value = null; gameSearch.value = '' }

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [franchiseResponse, gameResponse] = await Promise.all([
      franchiseApi.getAdminAll(),
      gameApi.getAdminAll()
    ])
    franchises.value = Array.isArray(extractData(franchiseResponse)) ? extractData(franchiseResponse) : []
    games.value = Array.isArray(extractData(gameResponse)) ? extractData(gameResponse) : []
  } catch (err) {
    error.value = errorMessage(err, '프랜차이즈 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function createFranchise() {
  saving.value = true
  error.value = ''
  try {
    const response = await franchiseApi.createAdmin({
      name: createForm.name,
      displayName: createForm.displayName,
      aliases: parseAliases(createForm.aliasesText)
    })
    const created = extractData(response)
    notice.value = `${created.displayName || created.name} 프랜차이즈를 등록했습니다.`
    createForm.name = ''; createForm.displayName = ''; createForm.aliasesText = ''
    await loadAll()
  } catch (err) {
    error.value = errorMessage(err, '프랜차이즈를 등록하지 못했습니다.')
  } finally {
    saving.value = false
  }
}

function toggleEdit(franchise) {
  if (editingId.value === franchise.id) return closePanels()
  editingId.value = franchise.id; managingId.value = null
  editForm.name = franchise.name || ''
  editForm.displayName = franchise.displayName || ''
  editForm.aliasesText = (franchise.aliases || []).join(', ')
}

async function saveEdit(franchise) {
  saving.value = true; error.value = ''
  try {
    await franchiseApi.updateAdmin(franchise.id, {
      name: editForm.name,
      displayName: editForm.displayName,
      aliases: parseAliases(editForm.aliasesText)
    })
    notice.value = '프랜차이즈 정보를 수정했습니다.'
    closePanels(); await loadAll()
  } catch (err) {
    error.value = errorMessage(err, '프랜차이즈 정보를 수정하지 못했습니다.')
  } finally { saving.value = false }
}

async function toggleGames(franchise) {
  if (managingId.value === franchise.id) return closePanels()
  managingId.value = franchise.id; editingId.value = null; gameSearch.value = ''
  await refreshDetail(franchise.id)
}

async function refreshDetail(id) {
  detailLoadingId.value = id
  try { details[id] = extractData(await franchiseApi.getAdminById(id)) }
  catch (err) { error.value = errorMessage(err, '게임 연결 정보를 불러오지 못했습니다.') }
  finally { detailLoadingId.value = null }
}

function gameCandidates(franchiseId) {
  const keyword = gameSearch.value.toLocaleLowerCase('ko-KR')
  const linked = new Set((detailFor(franchiseId)?.games || []).map((item) => item.gameId))
  return games.value.filter((game) => !linked.has(game.id)).filter((game) => {
    const text = [game.name, game.displayName, ...(game.aliases || []), game.publisher, game.developer]
      .filter(Boolean).join(' ').toLocaleLowerCase('ko-KR')
    return text.includes(keyword)
  }).slice(0, 8)
}

async function linkGame(franchise, game) {
  try {
    details[franchise.id] = extractData(await franchiseApi.linkGame(franchise.id, game.id, false))
    notice.value = `${game.displayName || game.name}을(를) ${franchise.displayName || franchise.name}에 연결했습니다.`
    await loadAll(); managingId.value = franchise.id
  } catch (err) { error.value = errorMessage(err, '게임을 연결하지 못했습니다.') }
}

async function setPrimary(franchise, link, isPrimary) {
  try {
    details[franchise.id] = extractData(await franchiseApi.updateGameLink(franchise.id, link.gameId, isPrimary))
    notice.value = isPrimary ? '대표 프랜차이즈로 지정했습니다.' : '대표 지정을 해제했습니다.'
  } catch (err) { error.value = errorMessage(err, '대표 여부를 변경하지 못했습니다.') }
}

async function unlinkGame(franchise, link) {
  if (!window.confirm(`${link.gameDisplayName || link.gameName} 연결을 해제하시겠습니까?`)) return
  try {
    details[franchise.id] = extractData(await franchiseApi.unlinkGame(franchise.id, link.gameId))
    notice.value = '게임 연결을 해제했습니다.'
    await loadAll(); managingId.value = franchise.id
  } catch (err) { error.value = errorMessage(err, '게임 연결을 해제하지 못했습니다.') }
}

onMounted(async () => {
  if (typeof route.query.name === 'string') createForm.name = route.query.name
  await loadAll()
})
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
.create-panel { padding: 28px 0; border-bottom: 1px solid #dfe2e6; }
.section-heading.compact { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 16px; }
.section-heading h2 { margin: 0; font-size: 15px; }
.section-heading span { color: #8b929b; font-size: 10px; }
.create-form, .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 18px; }
.field { display: grid; gap: 6px; }
.field.full { grid-column: 1 / -1; }
.field span, .game-search span { color: #7b828c; font-size: 10px; font-weight: 700; }
.field input, .game-search input, .search-box input { width: 100%; height: 36px; padding: 0 2px; border: 0; border-bottom: 1px solid #cfd3d8; outline: 0; background: transparent; font: inherit; font-size: 12px; }
.create-actions { grid-column: 1 / -1; display: flex; justify-content: flex-end; }
.toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 28px 0 10px; }
.section-title { display: flex; gap: 8px; align-items: baseline; }
.section-title strong { font-size: 16px; }
.section-title span { color: #8b929b; font-size: 11px; }
.search-box { width: min(360px, 100%); }
.franchise-list { border-top: 2px solid #17191d; border-bottom: 1px solid #dfe2e6; }
.franchise-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 18px 28px; padding: 22px 2px; border-bottom: 1px solid #eceef1; }
.title-line { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.title-line h3 { margin: 0; font-size: 15px; }
.source-badge { padding: 2px 6px; border: 1px solid #d8dce1; color: #69717b; font-size: 9px; font-weight: 800; }
.canonical, .meta, .aliases { margin: 4px 0 0; color: #858c95; font-size: 11px; }
.row-actions { display: flex; gap: 12px; }
.text-button { padding: 3px 0; background: none; color: #727984; font-size: 11px; font-weight: 700; }
.inline-panel { grid-column: 1 / -1; padding: 18px 0 2px; border-top: 1px solid #dfe2e6; }
.panel-heading { display: flex; justify-content: space-between; margin-bottom: 16px; }
.panel-heading strong { font-size: 12px; }
.panel-heading button { background: none; color: #8a919b; font-size: 10px; }
.panel-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
.primary-button, .secondary-button, .mini-button { padding: 8px 12px; font-size: 11px; font-weight: 700; }
.primary-button { border: 1px solid #25292f; background: #25292f; color: #fff; }
.secondary-button, .mini-button { border: 1px solid #d3d7dc; background: #fff; color: #69717b; }
.linked-games, .game-search-results { width: min(720px, 100%); border-top: 1px solid #eceef1; }
.game-link-row, .candidate-button { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 10px 2px; border-bottom: 1px solid #eceef1; background: #fff; text-align: left; }
.game-link-row span, .candidate-button span { display: grid; gap: 2px; }
.game-link-row strong, .candidate-button strong { font-size: 11px; }
.game-link-row small, .candidate-button small, .candidate-button em { color: #8b929b; font-size: 10px; font-style: normal; }
.link-actions { display: flex; gap: 8px; }
.mini-button { padding: 5px 8px; font-size: 9px; }
.mini-button.active { border-color: #a9bbaa; color: #56705a; }
.mini-button.danger { color: #9a5656; }
.game-search { display: grid; gap: 6px; width: min(520px, 100%); margin-top: 18px; }
.empty, .empty-state { color: #949aa3; font-size: 11px; }
.empty-state { padding: 54px 0; text-align: center; }
.empty-state p { margin: 4px 0 0; }
@media (max-width: 760px) {
  .admin-main { width: min(100% - 32px, 1040px); padding-top: 36px; }
  .create-form, .form-grid, .franchise-row { grid-template-columns: 1fr; }
  .field.full, .create-actions, .inline-panel { grid-column: auto; }
  .toolbar { align-items: stretch; flex-direction: column; }
  .search-box { width: 100%; }
  .row-actions { justify-content: flex-start; }
}
</style>
