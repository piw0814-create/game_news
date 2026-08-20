<template>
  <div class="admin-page">
    <AppHeader />

    <main class="admin-main">
      <section class="page-heading">
        <div>
          <p class="eyebrow">ADMIN · GAME REVIEW</p>
          <h1>게임 관리</h1>
          <p>게임 기준 데이터를 확인하고 등록 출처와 관계없이 수정·병합·검토할 수 있습니다.</p>
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
            placeholder="게임명 · 별칭 · 퍼블리셔 검색"
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
                <h3>{{ gameDisplayName(game) }}</h3>
                <span class="status-badge" :class="statusClass(game.reviewStatus)">
                  {{ statusLabel(game.reviewStatus) }}
                </span>
              </div>
              <p v-if="game.displayName && game.displayName !== game.name" class="canonical-name">{{ game.name }}</p>

              <p class="publisher">{{ game.publisher || '퍼블리셔 정보 없음' }}</p>
              <p v-if="game.developer" class="developer">개발 {{ game.developer }}</p>
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
              <button type="button" class="text-button" @click="toggleReview(game)">검토 정보</button>
              <button type="button" class="text-button" @click="toggleMerge(game)">병합</button>
            </div>

            <form v-if="editingGameId === game.id" class="inline-panel edit-panel" @submit.prevent="saveEdit(game)">
              <div class="panel-heading">
                <strong>게임 정보 수정</strong>
                <button type="button" @click="closePanels">닫기</button>
              </div>

              <div class="form-grid">
                <label class="field full">
                  <span>기준 게임명</span>
                  <input v-model.trim="editForm.name" required />
                </label>
                <label class="field full">
                  <span>표시 이름</span>
                  <input v-model.trim="editForm.displayName" placeholder="한국 공식명 등" />
                </label>
                <label class="field full">
                  <span>별칭</span>
                  <input v-model="editForm.aliasesText" placeholder="쉼표로 구분 (예: NTE, 异环)" />
                </label>
                <label class="field">
                  <span>퍼블리셔</span>
                  <input v-model.trim="editForm.publisher" />
                </label>
                <label class="field">
                  <span>개발사</span>
                  <input v-model.trim="editForm.developer" />
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
                  placeholder="게임명 · 별칭 검색"
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
                    <strong>{{ gameDisplayName(target) }}</strong>
                    <small>{{ target.publisher || '퍼블리셔 정보 없음' }}</small>
                  </span>
                  <em>#{{ target.id }}</em>
                </button>
              </div>

              <p v-else class="merge-empty">
                {{ mergeKeyword ? '일치하는 게임이 없습니다.' : '병합할 게임을 검색하세요.' }}
              </p>
            </div>

            <div v-if="reviewingGameId === game.id" class="inline-panel review-panel">
              <div class="panel-heading">
                <strong>검토 판단 정보</strong>
                <button type="button" @click="closePanels">닫기</button>
              </div>

              <div v-if="reviewLoadingGameId === game.id" class="review-loading">검토 정보를 불러오는 중...</div>

              <template v-else-if="contextFor(game.id)">
                <section v-if="canReclassifyAiRecognition(game)" class="review-decision">
                  <div class="review-decision-heading">
                    <strong>{{ game.reviewStatus === 'CONFIRMED' ? 'AI 자동확정 판정을 다시 분류할까요?' : '이 AI 인식을 어떻게 처리할까요?' }}</strong>
                    <span>{{ game.reviewStatus === 'CONFIRMED' ? '등록 근거가 특정 게임이 아니었다면 프랜차이즈 또는 관련 없음으로 바로잡을 수 있습니다.' : '기사 근거를 확인한 뒤 하나를 선택하세요.' }}</span>
                  </div>

                  <div class="decision-grid">
                    <div class="decision-card">
                      <strong>특정 게임</strong>
                      <p>{{ game.reviewStatus === 'CONFIRMED' ? '현재 특정 게임으로 확정되어 있습니다. 이름·메타데이터가 잘못됐다면 수정/IGDB 재적용/병합을 사용하세요.' : '현재 항목이 실제 특정 작품을 가리키면 그대로 확정합니다. 필요하면 아래 IGDB 보강이나 기존 Game 병합을 먼저 사용하세요.' }}</p>
                      <button
                        v-if="game.reviewStatus === 'REVIEW_REQUIRED'"
                        type="button"
                        class="primary-button"
                        :disabled="actionGameId === game.id"
                        @click="confirmGame(game)"
                      >
                        이 게임으로 확정
                      </button>
                      <span v-else class="decision-current">현재 특정 게임으로 확정됨</span>
                    </div>

                    <div class="decision-card">
                      <strong>프랜차이즈</strong>
                      <p>특정 작품이 아니라 IP/시리즈 전체를 가리키면 ArticleGame을 Franchise 관계로 전환합니다.</p>
                      <input
                        v-model.trim="reviewFranchiseKeyword"
                        class="decision-input"
                        type="search"
                        placeholder="프랜차이즈 검색"
                      />
                      <div v-if="franchiseCandidates().length" class="decision-candidates">
                        <button
                          v-for="franchise in franchiseCandidates()"
                          :key="franchise.id"
                          type="button"
                          :disabled="actionGameId === game.id"
                          @click="resolveAsFranchise(game, franchise)"
                        >
                          <span>{{ franchise.displayName || franchise.name }}</span>
                          <small v-if="franchise.displayName">{{ franchise.name }}</small>
                        </button>
                      </div>
                      <button
                        v-else
                        type="button"
                        class="secondary-button"
                        :disabled="actionGameId === game.id"
                        @click="createAndResolveFranchise(game)"
                      >
                        “{{ gameDisplayName(game) }}” 프랜차이즈 등록 후 전환
                      </button>
                    </div>

                    <div class="decision-card danger-card">
                      <strong>관련 없음</strong>
                      <p>게임/프랜차이즈 인식 자체가 잘못된 경우입니다. AI 등록 Game과 연결된 기사·토픽·관심 관계를 정리합니다.</p>
                      <button
                        type="button"
                        class="secondary-button danger-action"
                        :disabled="actionGameId === game.id"
                        @click="rejectGame(game)"
                      >
                        관련 없음 처리
                      </button>
                    </div>
                  </div>
                </section>

                <section class="review-block">
                  <div class="review-block-heading">
                    <strong>등록 근거</strong>
                  </div>
                  <a
                    v-if="contextFor(game.id).sourceArticle"
                    :href="contextFor(game.id).sourceArticle.url"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="article-link"
                  >
                    <span>
                      <strong>{{ contextFor(game.id).sourceArticle.title }}</strong>
                      <small>{{ contextFor(game.id).sourceArticle.sourceName }}</small>
                    </span>
                    <em>원문 ↗</em>
                  </a>
                  <p v-else class="review-empty">등록 원인 기사 정보가 없습니다.</p>
                </section>

                <section class="review-block">
                  <div class="review-block-heading">
                    <strong>연결 기사</strong>
                    <span>{{ contextFor(game.id).linkedArticles?.length || 0 }}건</span>
                  </div>
                  <div v-if="contextFor(game.id).linkedArticles?.length" class="article-list">
                    <a
                      v-for="article in contextFor(game.id).linkedArticles"
                      :key="article.id"
                      :href="article.url"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="article-link compact"
                    >
                      <span>
                        <strong>{{ article.title }}</strong>
                        <small>{{ article.sourceName }} · 신뢰도 {{ confidencePercent(article.confidenceScore) }}%<template v-if="article.primary"> · 대표</template></small>
                        <small v-if="article.relevanceReason" class="article-reason">근거 · {{ article.relevanceReason }}</small>
                      </span>
                      <em>↗</em>
                    </a>
                  </div>
                  <p v-else class="review-empty">연결된 기사가 없습니다.</p>
                </section>

                <section class="review-block">
                  <div class="review-block-heading">
                    <strong>기존 Game 유사 후보</strong>
                    <span>Top {{ contextFor(game.id).similarGames?.length || 0 }}</span>
                  </div>
                  <div v-if="contextFor(game.id).similarGames?.length" class="similar-list">
                    <div v-for="candidate in contextFor(game.id).similarGames" :key="candidate.id" class="similar-item">
                      <div>
                        <strong>{{ gameDisplayName(candidate) }}</strong>
                        <small v-if="candidate.name !== gameDisplayName(candidate)">{{ candidate.name }}</small>
                        <p>{{ (candidate.reasons || []).join(' · ') || '이름 기반 유사 후보' }}</p>
                      </div>
                      <div class="similar-actions">
                        <em>{{ confidencePercent(candidate.similarityScore) }}%</em>
                        <button
                          type="button"
                          class="secondary-button small"
                          :disabled="actionGameId === game.id"
                          @click="mergeGame(game, candidate)"
                        >
                          병합
                        </button>
                      </div>
                    </div>
                  </div>
                  <p v-else class="review-empty">유사도가 높은 기존 Game이 없습니다.</p>
                </section>

                <section class="review-block igdb-block">
                  <div class="review-block-heading">
                    <div>
                      <strong>IGDB 메타데이터</strong>
                      <small v-if="game.enrichmentStatus">{{ enrichmentLabel(game.enrichmentStatus) }}</small>
                    </div>
                    <button
                      type="button"
                      class="secondary-button small"
                      :disabled="enrichmentLoadingGameId === game.id"
                      @click="previewEnrichment(game)"
                    >
                      {{ enrichmentLoadingGameId === game.id ? '조회 중...' : '메타데이터 찾기' }}
                    </button>
                  </div>

                  <template v-if="previewFor(game.id)">
                    <p v-if="!previewFor(game.id).configured" class="review-empty warning">
                      IGDB_CLIENT_ID / IGDB_CLIENT_SECRET 설정이 필요합니다.
                    </p>
                    <div v-else-if="previewFor(game.id).candidates?.length" class="igdb-candidates">
                      <article v-for="candidate in previewFor(game.id).candidates" :key="candidate.igdbId" class="igdb-candidate">
                        <img v-if="candidate.imageUrl" :src="candidate.imageUrl" :alt="candidate.name" />
                        <div class="igdb-candidate-body">
                          <div class="candidate-title">
                            <strong>{{ candidate.name }}</strong>
                            <em>매칭 {{ confidencePercent(candidate.matchScore) }}%</em>
                          </div>
                          <p v-if="candidate.matchReasons?.length" class="candidate-reasons">{{ candidate.matchReasons.join(' · ') }}</p>
                          <dl class="metadata-grid">
                            <div><dt>Developer</dt><dd>{{ candidate.developer || '-' }}</dd></div>
                            <div><dt>Publisher</dt><dd>{{ candidate.publisher || '-' }}</dd></div>
                            <div><dt>Genre</dt><dd>{{ candidate.genres?.join(', ') || '-' }}</dd></div>
                            <div><dt>Platform</dt><dd>{{ candidate.platforms?.join(', ') || '-' }}</dd></div>
                          </dl>
                          <p v-if="candidate.aliases?.length" class="candidate-extra">별칭 · {{ candidate.aliases.slice(0, 6).join(', ') }}</p>
                          <p v-if="candidate.localizedNames?.length" class="candidate-extra">지역명 · {{ localizedPreview(candidate.localizedNames) }}</p>
                          <div class="candidate-actions">
                            <button
                              type="button"
                              class="primary-button"
                              :disabled="actionGameId === game.id"
                              @click="applyEnrichment(game, candidate)"
                            >
                              이 후보 적용
                            </button>
                          </div>
                        </div>
                      </article>
                    </div>
                    <p v-else class="review-empty">IGDB에서 후보를 찾지 못했습니다.</p>
                  </template>
                </section>
              </template>
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
import { franchiseApi } from '@/api/franchise.js'

const games = ref([])
const franchises = ref([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
const searchKeyword = ref('')
const activeStatus = ref('REVIEW_REQUIRED')
const editingGameId = ref(null)
const mergingGameId = ref(null)
const reviewingGameId = ref(null)
const reviewLoadingGameId = ref(null)
const enrichmentLoadingGameId = ref(null)
const reviewContexts = reactive({})
const enrichmentPreviews = reactive({})
const actionGameId = ref(null)
const mergeKeyword = ref('')
const reviewFranchiseKeyword = ref('')
const currentPage = ref(1)

const PAGE_SIZE = 10

const editForm = reactive({
  name: '',
  displayName: '',
  aliasesText: '',
  publisher: '',
  developer: '',
  genre: '',
  platform: '',
  imageUrl: ''
})

const tabs = [
  { value: 'REVIEW_REQUIRED', label: '검토 필요' },
  { value: 'CONFIRMED', label: '확정' },
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

    const searchable = [game.name, game.displayName, ...(game.aliases || []), game.publisher, game.developer, game.genre, game.platform]
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
    REVIEW_REQUIRED: '검토 필요',
    CONFIRMED: '확정'
  }[status] || status
}

function statusClass(status) {
  return String(status || '').toLowerCase().replaceAll('_', '-')
}

function sourceLabel(source) {
  if (source === 'AI') return 'AI 등록'
  if (source === 'IGDB') return 'IGDB 등록'
  return '수동 등록'
}

function canReclassifyAiRecognition(game) {
  if (!game) return false
  if (game.reviewStatus === 'REVIEW_REQUIRED') return true
  return game.reviewStatus === 'CONFIRMED' && game.registrationSource === 'AI'
}

function confidencePercent(value) {
  return Math.round(Number(value) * 100)
}

function gameMeta(game) {
  const values = [game.genre, game.platform].filter(Boolean)
  return values.length ? values.join(' · ') : '추가 정보 없음'
}

function gameDisplayName(game) {
  return game?.displayName || game?.name || ''
}

function parseAliases(value) {
  const seen = new Set()
  return String(value || '')
    .split(',')
    .map((alias) => alias.trim())
    .filter((alias) => {
      if (!alias) return false
      const key = alias.toLocaleLowerCase('ko-KR')
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
}

function clearMessages() {
  error.value = ''
  notice.value = ''
}

function closePanels() {
  editingGameId.value = null
  mergingGameId.value = null
  reviewingGameId.value = null
  mergeKeyword.value = ''
  reviewFranchiseKeyword.value = ''
}

function toggleEdit(game) {
  clearMessages()

  if (editingGameId.value === game.id) {
    closePanels()
    return
  }

  editingGameId.value = game.id
  mergingGameId.value = null
  reviewingGameId.value = null
  mergeKeyword.value = ''
  editForm.name = game.name || ''
  editForm.displayName = game.displayName || ''
  editForm.aliasesText = (game.aliases || []).join(', ')
  editForm.publisher = game.publisher || ''
  editForm.developer = game.developer || ''
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
  reviewingGameId.value = null
  mergeKeyword.value = ''
}


function contextFor(gameId) {
  return reviewContexts[gameId] || null
}

function previewFor(gameId) {
  return enrichmentPreviews[gameId] || null
}

function enrichmentLabel(status) {
  return {
    NOT_ENRICHED: '미보강',
    ENRICHED: '보강 완료',
    PARTIAL: '일부 보강',
    FAILED: '보강 실패'
  }[status] || status
}

function localizedPreview(values = []) {
  return values
    .slice(0, 5)
    .map((item) => `${item.name}${item.regionName ? ` (${item.regionName})` : ''}`)
    .join(', ')
}

async function toggleReview(game) {
  clearMessages()
  if (reviewingGameId.value === game.id) {
    closePanels()
    return
  }

  editingGameId.value = null
  mergingGameId.value = null
  reviewingGameId.value = game.id
  reviewLoadingGameId.value = game.id
  reviewFranchiseKeyword.value = game.name || ''

  try {
    const [response] = await Promise.all([
      gameApi.getAdminReviewContext(game.id),
      loadFranchises()
    ])
    reviewContexts[game.id] = extractData(response)
  } catch (err) {
    error.value = errorMessage(err, '검토 정보를 불러오지 못했습니다.')
  } finally {
    reviewLoadingGameId.value = null
  }
}

async function loadFranchises() {
  if (franchises.value.length) return
  const response = await franchiseApi.getAdminAll()
  const data = extractData(response)
  franchises.value = Array.isArray(data) ? data : []
}

function franchiseCandidates() {
  const keyword = reviewFranchiseKeyword.value.toLocaleLowerCase('ko-KR')
  if (!keyword) return franchises.value.slice(0, 6)
  return franchises.value.filter((franchise) => {
    const searchable = [franchise.name, franchise.displayName, ...(franchise.aliases || [])]
      .filter(Boolean)
      .join(' ')
      .toLocaleLowerCase('ko-KR')
    return searchable.includes(keyword)
  }).slice(0, 6)
}

async function resolveAsFranchise(game, franchise) {
  const linkedCount = contextFor(game.id)?.linkedArticles?.length || 0
  const confirmed = window.confirm(
    `“${gameDisplayName(game)}”을(를) 특정 게임이 아닌 “${franchise.displayName || franchise.name}” 프랜차이즈 언급으로 전환하시겠습니까?\n연결 기사 ${linkedCount}건을 ArticleFranchise로 옮기고 이 AI 등록 Game과 토픽·관심 게임 관계를 정리합니다.`
  )
  if (!confirmed) return

  clearMessages()
  actionGameId.value = game.id
  try {
    const response = await gameApi.resolveAdminAsFranchise(game.id, franchise.id)
    const result = extractData(response)
    games.value = games.value.filter((item) => item.id !== game.id)
    notice.value = `${gameDisplayName(game)}을(를) ${result.franchiseName} 프랜차이즈로 전환했습니다. 연결 기사 ${result.convertedArticleCount}건을 이동했습니다.`
    closePanels()
  } catch (err) {
    error.value = errorMessage(err, '프랜차이즈로 전환하지 못했습니다.')
  } finally {
    actionGameId.value = null
  }
}

async function createAndResolveFranchise(game) {
  const confirmed = window.confirm(
    `기존 프랜차이즈를 찾지 못했습니다. “${gameDisplayName(game)}” 이름으로 새 프랜차이즈를 등록하고 이 AI Game 인식을 전환하시겠습니까?\n연결 기사와 토픽·관심 게임 관계도 함께 정리됩니다.`
  )
  if (!confirmed) return

  clearMessages()
  actionGameId.value = game.id
  try {
    const createdResponse = await franchiseApi.createAdmin({
      name: game.name,
      displayName: game.displayName || '',
      aliases: game.aliases || []
    })
    const created = extractData(createdResponse)
    franchises.value.push({ ...created, gameCount: created.games?.length || 0 })
    const response = await gameApi.resolveAdminAsFranchise(game.id, created.id)
    const result = extractData(response)
    games.value = games.value.filter((item) => item.id !== game.id)
    notice.value = `${result.franchiseName} 프랜차이즈를 등록하고 연결 기사 ${result.convertedArticleCount}건을 전환했습니다.`
    closePanels()
  } catch (err) {
    error.value = errorMessage(err, '프랜차이즈 등록/전환을 완료하지 못했습니다.')
  } finally {
    actionGameId.value = null
  }
}

async function previewEnrichment(game) {
  clearMessages()
  enrichmentLoadingGameId.value = game.id
  try {
    const response = await gameApi.previewAdminEnrichment(game.id)
    enrichmentPreviews[game.id] = extractData(response)
  } catch (err) {
    error.value = errorMessage(err, 'IGDB 메타데이터 후보를 불러오지 못했습니다.')
  } finally {
    enrichmentLoadingGameId.value = null
  }
}

async function applyEnrichment(game, candidate) {
  const confirmed = window.confirm(`“${candidate.name}” IGDB 메타데이터를 적용하시겠습니까?\n기존 값은 유지하고 빈 메타데이터와 새 별칭만 보강합니다.`)
  if (!confirmed) return

  clearMessages()
  actionGameId.value = game.id
  try {
    const response = await gameApi.applyAdminEnrichment(game.id, candidate.igdbId)
    const updated = extractData(response)
    replaceGame(updated)
    notice.value = `${gameDisplayName(updated)} 메타데이터를 보강했습니다.`
    const contextResponse = await gameApi.getAdminReviewContext(game.id)
    reviewContexts[game.id] = extractData(contextResponse)
  } catch (err) {
    error.value = errorMessage(err, 'IGDB 메타데이터를 적용하지 못했습니다.')
  } finally {
    actionGameId.value = null
  }
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
      displayName: editForm.displayName,
      aliases: parseAliases(editForm.aliasesText),
      publisher: editForm.publisher || null,
      developer: editForm.developer || null,
      genre: editForm.genre || null,
      platform: editForm.platform || null,
      imageUrl: editForm.imageUrl || null
    }
    const response = await gameApi.updateAdmin(game.id, payload)
    const updated = extractData(response)
    replaceGame(updated)
    notice.value = `${gameDisplayName(updated)} 정보를 수정했습니다.`
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
    notice.value = `${gameDisplayName(updated)} 검수를 확정했습니다.`
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
      const searchable = [game.name, game.displayName, ...(game.aliases || []), game.publisher, game.developer]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('ko-KR')
      return searchable.includes(keyword)
    })
    .slice(0, 8)
}

async function mergeGame(source, target) {
  const confirmed = window.confirm(
    `“${gameDisplayName(source)}”을(를) “${gameDisplayName(target)}”에 병합하시겠습니까?\n연결된 기사·토픽·관심 게임과 기존 이름·별칭도 대상 게임으로 이동합니다.`
  )
  if (!confirmed) return

  clearMessages()
  actionGameId.value = source.id

  try {
    await gameApi.mergeAdmin(source.id, target.id)
    games.value = games.value.filter((game) => game.id !== source.id)
    notice.value = `${gameDisplayName(source)}을(를) ${gameDisplayName(target)}에 병합했습니다.`
    closePanels()
  } catch (err) {
    error.value = errorMessage(err, '게임을 병합하지 못했습니다.')
  } finally {
    actionGameId.value = null
  }
}

async function rejectGame(game) {
  const linkedCount = contextFor(game.id)?.linkedArticles?.length || 0
  const confirmed = window.confirm(
    `“${gameDisplayName(game)}” AI 인식을 관련 없음으로 처리하시겠습니까?\n연결 기사 ${linkedCount}건과 토픽·관심 게임 관계를 정리하고 이 AI 등록 Game을 삭제합니다.`
  )
  if (!confirmed) return

  clearMessages()
  actionGameId.value = game.id

  try {
    await gameApi.rejectAdmin(game.id)
    games.value = games.value.filter((item) => item.id !== game.id)
    notice.value = `${gameDisplayName(game)}을(를) 관련 없음으로 처리했습니다.`
    closePanels()
  } catch (err) {
    error.value = errorMessage(err, '관련 없음 처리를 완료하지 못했습니다.')
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


.status-badge.confirmed {
  border-color: #bdcdbf;
  color: #58705c;
}

.canonical-name {
  margin: 4px 0 0;
  color: #7a818b;
  font-size: 12px;
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


.developer {
  margin: 3px 0 0;
  color: #7b828b;
  font-size: 11px;
}

.review-panel {
  display: grid;
  gap: 18px;
}

.review-loading,
.review-empty {
  margin: 0;
  color: #8a919a;
  font-size: 11px;
}

.review-empty.warning {
  color: #9a6d2f;
}

.review-block {
  padding-top: 14px;
  border-top: 1px solid #eceef1;
}

.review-block:first-of-type {
  padding-top: 0;
  border-top: 0;
}

.review-block-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 10px;
}

.review-block-heading > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.review-block-heading strong {
  font-size: 11px;
}

.review-block-heading span,
.review-block-heading small {
  color: #8d939c;
  font-size: 10px;
}

.article-list,
.similar-list,
.igdb-candidates {
  display: grid;
  gap: 8px;
}

.article-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 11px 12px;
  border: 1px solid #e1e4e8;
  color: inherit;
  text-decoration: none;
}

.article-link.compact {
  padding: 9px 10px;
}

.article-link:hover {
  border-color: #aeb4bc;
}

.article-link span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.article-link strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}

.article-link small {
  color: #868d96;
  font-size: 10px;
}

.article-link .article-reason {
  color: #6f7680;
  white-space: normal;
  overflow-wrap: anywhere;
}

.article-link em {
  flex: 0 0 auto;
  color: #747b84;
  font-size: 10px;
  font-style: normal;
}

.similar-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 10px 12px;
  border: 1px solid #e1e4e8;
}

.similar-item > div:first-child {
  min-width: 0;
}

.similar-item strong {
  display: block;
  font-size: 11px;
}

.similar-item small {
  display: block;
  margin-top: 2px;
  color: #858c95;
  font-size: 10px;
}

.similar-item p {
  margin: 4px 0 0;
  color: #959ba3;
  font-size: 10px;
}

.similar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.similar-actions em {
  color: #424951;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.secondary-button.small {
  padding: 6px 9px;
  font-size: 10px;
}

.igdb-candidate {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 14px;
  padding: 12px;
  border: 1px solid #dde1e6;
}

.igdb-candidate > img {
  width: 88px;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  background: #f2f3f5;
}

.igdb-candidate-body {
  min-width: 0;
}

.candidate-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.candidate-title strong {
  font-size: 12px;
}

.candidate-title em {
  flex: 0 0 auto;
  color: #4e5965;
  font-size: 10px;
  font-style: normal;
  font-weight: 800;
}

.candidate-reasons,
.candidate-extra {
  margin: 5px 0 0;
  color: #858c95;
  font-size: 10px;
}

.metadata-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px 14px;
  margin: 12px 0 0;
}

.metadata-grid div {
  min-width: 0;
}

.metadata-grid dt {
  color: #9a9fa7;
  font-size: 9px;
  font-weight: 700;
  text-transform: uppercase;
}

.metadata-grid dd {
  margin: 2px 0 0;
  color: #4e555e;
  font-size: 10px;
  overflow-wrap: anywhere;
}

.candidate-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.review-decision {
  padding: 16px;
  border: 1px solid #d9dde2;
  background: #fbfbfc;
}

.review-decision-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.review-decision-heading strong { font-size: 12px; }
.review-decision-heading span { color: #8a919a; font-size: 10px; }

.decision-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.decision-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 9px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #e2e5e9;
  background: #fff;
}

.decision-card > strong { font-size: 11px; }
.decision-card > p { margin: 0; color: #858c95; font-size: 10px; line-height: 1.55; }
.decision-current {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  margin-top: auto;
  padding: 0 10px;
  border: 1px solid #dfe2e6;
  color: #68717c;
  background: #f7f8f9;
  font-size: 10px;
  font-weight: 700;
}
.decision-card .primary-button, .decision-card .secondary-button { margin-top: auto; }
.decision-input { width: 100%; height: 32px; border: 0; border-bottom: 1px solid #cfd3d8; outline: 0; font-size: 11px; }
.decision-candidates { display: grid; width: 100%; border-top: 1px solid #eceef1; }
.decision-candidates button { display: grid; gap: 1px; padding: 7px 2px; border-bottom: 1px solid #eceef1; background: #fff; text-align: left; }
.decision-candidates button span { color: #363b42; font-size: 10px; font-weight: 700; }
.decision-candidates button small { color: #9399a1; font-size: 9px; }
.danger-card { border-color: #eadede; }
.danger-action { color: #945656; border-color: #dfcaca; }

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

  .decision-grid {
    grid-template-columns: 1fr;
  }

  .field.full {
    grid-column: auto;
  }
}
</style>
