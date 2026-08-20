<template>
  <div class="admin-page">
    <AppHeader />

    <main class="admin-main">
      <section class="page-heading">
        <div>
          <p class="eyebrow">ADMIN · FRANCHISE CATALOG</p>
          <h1>프랜차이즈 관리</h1>
          <p>확정된 프랜차이즈 카탈로그와 연결 게임·기사·Topic·IGDB 동기화 상태를 관리합니다.</p>
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
              게임 {{ franchise.gameCount || 0 }} · 기사 {{ franchise.articleCount || 0 }} · Topic {{ franchise.topicCount || 0 }}
              <template v-if="franchise.igdbId"> · IGDB #{{ franchise.igdbId }}</template>
            </p>
            <p v-if="franchise.lastSyncedAt" class="meta">최근 IGDB 동기화 {{ formatDate(franchise.lastSyncedAt) }}</p>
            <p v-if="franchise.aliases?.length" class="aliases">{{ franchise.aliases.join(' · ') }}</p>
          </div>
          <div class="row-actions">
            <button type="button" class="text-button" @click="toggleReview(franchise)">상세 · 관리</button>
            <button type="button" class="text-button" @click="toggleEdit(franchise)">수정</button>
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

          <div v-if="reviewingId === franchise.id" class="inline-panel review-panel">
            <div class="panel-heading">
              <strong>프랜차이즈 상세 · 관리</strong>
              <button type="button" @click="closePanels">닫기</button>
            </div>
            <div v-if="detailLoadingId === franchise.id" class="empty">상세 정보를 불러오는 중...</div>
            <template v-else-if="detailFor(franchise.id)">
              <div class="review-overview">
                <div><small>메타데이터</small><strong>{{ sourceLabel(detailFor(franchise.id).metadataSource) }}</strong></div>
                <div><small>IGDB</small><strong>{{ detailFor(franchise.id).igdbId ? `#${detailFor(franchise.id).igdbId}` : '미연결' }}</strong></div>
                <div><small>소속 게임</small><strong>{{ detailFor(franchise.id).games?.length || 0 }}</strong></div>
                <div><small>관련 기사</small><strong>{{ detailFor(franchise.id).articles?.length || 0 }}</strong></div>
                <div><small>관련 Topic</small><strong>{{ detailFor(franchise.id).topics?.length || 0 }}</strong></div>
              </div>

              <div class="sync-row">
                <div>
                  <strong>IGDB 카탈로그</strong>
                  <p>IGDB Franchise의 games 목록을 기준으로 Game과 GameFranchise를 최신화합니다.</p>
                </div>
                <button
                  type="button"
                  class="primary-button"
                  :disabled="syncingId === franchise.id"
                  @click="syncIgdb(franchise)"
                >
                  {{ syncingId === franchise.id ? '동기화 중...' : (detailFor(franchise.id).igdbId ? 'IGDB 동기화' : 'IGDB 연결 · 동기화') }}
                </button>
              </div>

              <section class="review-section">
                <div class="review-title"><strong>소속 게임</strong><span>{{ detailFor(franchise.id).games?.length || 0 }}개</span></div>
                <div class="linked-games">
                  <div v-for="link in detailFor(franchise.id).games" :key="link.gameId" class="game-link-row">
                    <span>
                      <strong>{{ link.gameDisplayName || link.gameName }}</strong>
                      <small>
                        #{{ link.gameId }}
                        <template v-if="link.gameIgdbId"> · IGDB #{{ link.gameIgdbId }}</template>
                        <template v-if="link.igdbGameType"> · {{ link.igdbGameType }}</template>
                        · {{ relationSourceLabel(link.relationSource) }}
                      </small>
                    </span>
                    <div class="link-actions">
                      <button type="button" class="mini-button" :class="{ active: link.isPrimary }" @click="setPrimary(franchise, link, !link.isPrimary)">
                        {{ link.isPrimary ? '대표' : '대표 지정' }}
                      </button>
                      <button type="button" class="mini-button danger" @click="unlinkGame(franchise, link)">연결 해제</button>
                    </div>
                  </div>
                  <p v-if="!detailFor(franchise.id).games?.length" class="empty">연결된 게임이 없습니다.</p>
                </div>

                <label class="game-search">
                  <span>소속 게임 보정</span>
                  <input v-model.trim="gameSearch" type="search" placeholder="게임명 · 별칭 검색" />
                </label>
                <div v-if="gameSearch" class="game-search-results">
                  <button v-for="game in gameCandidates(franchise.id)" :key="game.id" type="button" class="candidate-button" @click="linkGame(franchise, game)">
                    <span><strong>{{ game.displayName || game.name }}</strong><small>{{ game.publisher || '퍼블리셔 정보 없음' }}</small></span>
                    <em>#{{ game.id }}</em>
                  </button>
                  <p v-if="!gameCandidates(franchise.id).length" class="empty">추가할 게임이 없습니다.</p>
                </div>
              </section>

              <section class="review-section">
                <div class="review-title"><strong>관련 기사</strong><span>{{ detailFor(franchise.id).articles?.length || 0 }}개</span></div>
                <div class="compact-list">
                  <div v-for="article in detailFor(franchise.id).articles" :key="article.articleId" class="compact-row">
                    <span><strong>{{ article.title }}</strong><small>{{ article.sourceName || '출처 없음' }} · confidence {{ article.confidenceScore ?? '-' }}</small></span>
                    <em v-if="article.isPrimary">PRIMARY</em>
                  </div>
                  <p v-if="!detailFor(franchise.id).articles?.length" class="empty">직접 연결된 기사가 없습니다.</p>
                </div>
              </section>

              <section class="review-section">
                <div class="review-title"><strong>관련 Topic</strong><span>{{ detailFor(franchise.id).topics?.length || 0 }}개</span></div>
                <div class="compact-list">
                  <RouterLink v-for="topic in detailFor(franchise.id).topics" :key="topic.topicId" :to="`/topics/${topic.topicId}`" class="compact-row link-row">
                    <span><strong>{{ topic.title }}</strong><small>중요도 {{ topic.importanceScore ?? '-' }} · relevance {{ topic.relevanceScore ?? '-' }}</small></span>
                    <em v-if="topic.isPrimary">PRIMARY</em>
                  </RouterLink>
                  <p v-if="!detailFor(franchise.id).topics?.length" class="empty">직접 연결된 Topic이 없습니다.</p>
                </div>
              </section>

              <section class="review-section">
                <div class="review-title"><strong>유사 프랜차이즈</strong><span>중복 검토</span></div>
                <div class="compact-list">
                  <div v-for="candidate in detailFor(franchise.id).similarFranchises" :key="candidate.id" class="compact-row">
                    <span>
                      <strong>{{ candidate.displayName || candidate.name }}</strong>
                      <small>#{{ candidate.id }} · 유사도 {{ candidate.similarityScore }}<template v-if="candidate.igdbId"> · IGDB #{{ candidate.igdbId }}</template></small>
                    </span>
                    <button type="button" class="mini-button danger" @click="mergeInto(franchise, candidate)">이쪽으로 병합</button>
                  </div>
                  <p v-if="!detailFor(franchise.id).similarFranchises?.length" class="empty">뚜렷한 중복 후보가 없습니다.</p>
                </div>
              </section>
            </template>
          </div>
        </article>

        <div v-if="!loading && !filteredFranchises.length" class="empty-state">
          <strong>프랜차이즈가 없습니다.</strong>
          <p>기사 분석 또는 검토 큐에서 확정된 프랜차이즈가 이곳에 표시됩니다.</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { gameApi } from '@/api/game.js'
import { franchiseApi } from '@/api/franchise.js'

const franchises = ref([])
const games = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const searchKeyword = ref('')
const editingId = ref(null)
const reviewingId = ref(null)
const detailLoadingId = ref(null)
const syncingId = ref(null)
const details = reactive({})
const gameSearch = ref('')
const editForm = reactive({ name: '', displayName: '', aliasesText: '' })

const filteredFranchises = computed(() => {
  const keyword = searchKeyword.value.toLocaleLowerCase('ko-KR')
  if (!keyword) return franchises.value
  return franchises.value.filter((item) => [item.name, item.displayName, ...(item.aliases || []), item.igdbId]
    .filter(Boolean).join(' ').toLocaleLowerCase('ko-KR').includes(keyword))
})

function extractData(response) { return response?.data?.data ?? response?.data }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function sourceLabel(source) { return source === 'IGDB' ? 'IGDB' : '로컬' }
function relationSourceLabel(source) { return source === 'IGDB' ? 'IGDB 관계' : source === 'MANUAL' ? '관리자 보정' : '기존 관계' }
function formatDate(value) { return value ? new Date(value).toLocaleString('ko-KR') : '-' }
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
function closePanels() { editingId.value = null; reviewingId.value = null; gameSearch.value = '' }

async function loadAll() {
  loading.value = true; error.value = ''
  try {
    const [franchiseResponse, gameResponse] = await Promise.all([franchiseApi.getAdminAll(), gameApi.getAdminAll()])
    franchises.value = Array.isArray(extractData(franchiseResponse)) ? extractData(franchiseResponse) : []
    games.value = Array.isArray(extractData(gameResponse)) ? extractData(gameResponse) : []
  } catch (err) { error.value = errorMessage(err, '프랜차이즈 정보를 불러오지 못했습니다.') }
  finally { loading.value = false }
}

function toggleEdit(franchise) {
  if (editingId.value === franchise.id) return closePanels()
  editingId.value = franchise.id; reviewingId.value = null
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
  } catch (err) { error.value = errorMessage(err, '프랜차이즈 정보를 수정하지 못했습니다.') }
  finally { saving.value = false }
}

async function toggleReview(franchise) {
  if (reviewingId.value === franchise.id) return closePanels()
  reviewingId.value = franchise.id; editingId.value = null; gameSearch.value = ''
  await refreshDetail(franchise.id)
}

async function refreshDetail(id) {
  detailLoadingId.value = id
  try { details[id] = extractData(await franchiseApi.getAdminById(id)) }
  catch (err) { error.value = errorMessage(err, '프랜차이즈 검토 정보를 불러오지 못했습니다.') }
  finally { detailLoadingId.value = null }
}

async function syncIgdb(franchise) {
  syncingId.value = franchise.id; error.value = ''
  try {
    const result = extractData(await franchiseApi.syncIgdb(franchise.id))
    notice.value = `IGDB 동기화 완료 · 전체 ${result.igdbGameCount} · 신규 ${result.createdGameCount} · 갱신 ${result.updatedGameCount} · 제외 ${result.skippedGameCount}`
    await loadAll(); reviewingId.value = franchise.id; await refreshDetail(franchise.id)
  } catch (err) { error.value = errorMessage(err, 'IGDB 프랜차이즈 동기화에 실패했습니다.') }
  finally { syncingId.value = null }
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
    notice.value = `${game.displayName || game.name}을(를) 연결했습니다.`
    await loadAll(); reviewingId.value = franchise.id
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
    await loadAll(); reviewingId.value = franchise.id
  } catch (err) { error.value = errorMessage(err, '게임 연결을 해제하지 못했습니다.') }
}

async function mergeInto(source, target) {
  const targetName = target.displayName || target.name
  if (!window.confirm(`${source.displayName || source.name}을(를) ${targetName}(으)로 병합하시겠습니까?`)) return
  try {
    await franchiseApi.mergeAdmin(source.id, target.id)
    notice.value = `${targetName}(으)로 병합했습니다.`
    closePanels(); await loadAll()
  } catch (err) { error.value = errorMessage(err, '프랜차이즈를 병합하지 못했습니다.') }
}

onMounted(loadAll)
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
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 18px; }
.field { display: grid; gap: 6px; }
.field.full { grid-column: 1 / -1; }
.field span, .game-search span { color: #7b828c; font-size: 10px; font-weight: 700; }
.field input, .game-search input, .search-box input { width: 100%; height: 36px; padding: 0 2px; border: 0; border-bottom: 1px solid #cfd3d8; outline: 0; background: transparent; font: inherit; font-size: 12px; }
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
.primary-button:disabled { opacity: .45; }
.secondary-button, .mini-button { border: 1px solid #d3d7dc; background: #fff; color: #69717b; }
.review-overview { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 1px; border: 1px solid #e2e5e9; background: #e2e5e9; }
.review-overview div { display: grid; gap: 5px; padding: 12px; background: #fff; }
.review-overview small { color: #8b929b; font-size: 9px; }
.review-overview strong { font-size: 12px; }
.sync-row { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 18px 0; border-bottom: 1px solid #e8eaed; }
.sync-row strong { font-size: 12px; }
.sync-row p { margin: 4px 0 0; color: #858c95; font-size: 10px; }
.review-section { padding: 20px 0 4px; border-bottom: 1px solid #eceef1; }
.review-title { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.review-title strong { font-size: 12px; }
.review-title span { color: #8b929b; font-size: 9px; }
.linked-games, .game-search-results, .compact-list { width: 100%; border-top: 1px solid #eceef1; }
.game-link-row, .candidate-button, .compact-row { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 10px 2px; border-bottom: 1px solid #eceef1; background: #fff; text-align: left; color: inherit; text-decoration: none; }
.game-link-row span, .candidate-button span, .compact-row span { display: grid; gap: 2px; }
.game-link-row strong, .candidate-button strong, .compact-row strong { font-size: 11px; }
.game-link-row small, .candidate-button small, .candidate-button em, .compact-row small, .compact-row em { color: #8b929b; font-size: 10px; font-style: normal; }
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
  .form-grid, .franchise-row { grid-template-columns: 1fr; }
  .field.full, .inline-panel { grid-column: auto; }
  .toolbar, .sync-row { align-items: stretch; flex-direction: column; }
  .search-box { width: 100%; }
  .row-actions { justify-content: flex-start; }
  .review-overview { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
