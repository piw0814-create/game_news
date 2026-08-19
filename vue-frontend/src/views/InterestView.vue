<template>
  <div class="interest-page">
    <AppHeader />

    <main class="interest-main">
      <router-link to="/feed" class="back-link">← 뉴스</router-link>

      <section class="page-heading">
        <p class="eyebrow">INTERESTS</p>
        <h1>관심 게임</h1>
        <p>관심 있는 게임을 등록하면 Feed의 관심순 정렬에 반영됩니다.</p>
      </section>

      <div v-if="interestStore.error" class="error-banner" role="alert">
        <span>{{ interestStore.error }}</span>
        <button type="button" @click="interestStore.clearError">닫기</button>
      </div>

      <section class="interest-section">
        <div class="section-heading">
          <h2>내 관심 게임</h2>
          <span>{{ interestStore.interests.length }}개</span>
        </div>

        <div v-if="interestStore.loading" class="loading-state">
          <div class="loading-line wide"></div>
          <div class="loading-line"></div>
        </div>

        <div v-else-if="interestStore.interests.length">
          <div class="game-list">
            <article
            v-for="interest in pagedInterests"
            :key="interest.gameId"
            class="game-row"
          >
            <div class="game-info">
              <h3>{{ interest.gameName }}</h3>
              <p class="publisher">{{ interest.publisher || '퍼블리셔 정보 없음' }}</p>
              <p class="meta">{{ gameMeta(interest) }}</p>
            </div>

            <button
              type="button"
              class="action-button remove"
              :disabled="interestStore.actionGameId === interest.gameId"
              @click="removeInterest(interest.gameId)"
            >
              {{ interestStore.actionGameId === interest.gameId ? '해제 중...' : '관심 해제' }}
            </button>
            </article>
          </div>

          <nav v-if="interestTotalPages > 1" class="pagination" aria-label="내 관심 게임 페이지">
            <button
              type="button"
              :disabled="interestPage === 1"
              @click="interestPage -= 1"
            >
              이전
            </button>
            <span>{{ interestPage }} / {{ interestTotalPages }}</span>
            <button
              type="button"
              :disabled="interestPage === interestTotalPages"
              @click="interestPage += 1"
            >
              다음
            </button>
          </nav>
        </div>

        <div v-else-if="!interestStore.loading" class="empty-state">
          <strong>아직 관심 게임이 없습니다.</strong>
          <p>아래 게임 목록에서 관심 게임을 추가해보세요.</p>
        </div>
      </section>

      <section class="interest-section game-finder">
        <div class="section-heading">
          <h2>게임 찾기</h2>
          <span v-if="!interestStore.loading">{{ filteredGames.length }}개</span>
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
            placeholder="게임 이름이나 회사 검색"
            autocomplete="off"
          />
        </label>

        <div v-if="interestStore.loading" class="loading-state finder-loading">
          <div class="loading-line wide"></div>
          <div class="loading-line"></div>
        </div>

        <div v-else-if="filteredGames.length">
          <div class="game-list finder-list">
            <article
            v-for="game in pagedGames"
            :key="game.id"
            class="game-row"
          >
            <div class="game-info">
              <h3>{{ game.name }}</h3>
              <p class="publisher">{{ game.publisher || '퍼블리셔 정보 없음' }}</p>
              <p class="meta">{{ gameMeta(game) }}</p>
            </div>

            <span v-if="interestStore.isInterested(game.id)" class="interest-status">관심 중</span>
            <button
              v-else
              type="button"
              class="action-button"
              :disabled="interestStore.actionGameId === game.id"
              @click="addInterest(game.id)"
            >
              {{ interestStore.actionGameId === game.id ? '등록 중...' : '관심 등록' }}
            </button>
            </article>
          </div>

          <nav v-if="gameTotalPages > 1" class="pagination" aria-label="게임 찾기 페이지">
            <button
              type="button"
              :disabled="gamePage === 1"
              @click="gamePage -= 1"
            >
              이전
            </button>
            <span>{{ gamePage }} / {{ gameTotalPages }}</span>
            <button
              type="button"
              :disabled="gamePage === gameTotalPages"
              @click="gamePage += 1"
            >
              다음
            </button>
          </nav>
        </div>

        <div v-else-if="interestStore.games.length" class="empty-state compact">
          <strong>검색 결과가 없습니다.</strong>
          <p>검색어를 변경해보세요.</p>
        </div>

        <div v-else-if="!interestStore.loading" class="empty-state compact">
          <strong>등록된 게임이 없습니다.</strong>
          <p>News Service에 게임이 등록되면 여기에서 선택할 수 있습니다.</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { useInterestStore } from '@/store/interest.js'

const interestStore = useInterestStore()
const searchKeyword = ref('')
const interestPage = ref(1)
const gamePage = ref(1)

const INTEREST_PAGE_SIZE = 8
const GAME_PAGE_SIZE = 10

const filteredGames = computed(() => {
  const keyword = searchKeyword.value.toLocaleLowerCase('ko-KR')
  if (!keyword) return interestStore.games

  return interestStore.games.filter((game) => {
    const searchable = [game.name, game.publisher, game.genre, game.platform]
      .filter(Boolean)
      .join(' ')
      .toLocaleLowerCase('ko-KR')

    return searchable.includes(keyword)
  })
})

const interestTotalPages = computed(() =>
  Math.max(1, Math.ceil(interestStore.interests.length / INTEREST_PAGE_SIZE))
)

const pagedInterests = computed(() => {
  const start = (interestPage.value - 1) * INTEREST_PAGE_SIZE
  return interestStore.interests.slice(start, start + INTEREST_PAGE_SIZE)
})

const gameTotalPages = computed(() =>
  Math.max(1, Math.ceil(filteredGames.value.length / GAME_PAGE_SIZE))
)

const pagedGames = computed(() => {
  const start = (gamePage.value - 1) * GAME_PAGE_SIZE
  return filteredGames.value.slice(start, start + GAME_PAGE_SIZE)
})

watch(searchKeyword, () => {
  gamePage.value = 1
})

watch(interestTotalPages, (totalPages) => {
  if (interestPage.value > totalPages) interestPage.value = totalPages
})

watch(gameTotalPages, (totalPages) => {
  if (gamePage.value > totalPages) gamePage.value = totalPages
})

function gameMeta(game) {
  const values = [game.genre, game.platform].filter(Boolean)
  return values.length ? values.join(' · ') : '추가 정보 없음'
}

async function addInterest(gameId) {
  try {
    await interestStore.addInterest(gameId)
  } catch {
    // Store의 error 상태를 화면에 표시한다.
  }
}

async function removeInterest(gameId) {
  try {
    await interestStore.removeInterest(gameId)
  } catch {
    // Store의 error 상태를 화면에 표시한다.
  }
}

onMounted(() => {
  interestStore.load()
})
</script>

<style scoped>
.interest-page {
  min-height: 100vh;
  background: #fff;
  color: #1d2025;
}

.interest-main {
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

.error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 24px;
  padding: 11px 0;
  border-bottom: 1px solid #e4b6b6;
  color: #a33a3a;
  font-size: 12px;
}

.error-banner button {
  flex: 0 0 auto;
  background: none;
  color: #7c7373;
  font-size: 11px;
}

.interest-section {
  padding: 34px 0 0;
}

.game-finder {
  margin-top: 26px;
  padding-top: 34px;
  border-top: 1px solid #dfe2e6;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 2px;
  border-bottom: 1px solid #eceef1;
}

.game-row:last-child {
  border-bottom: 0;
}

.game-info {
  min-width: 0;
}

.game-info h3 {
  margin: 0 0 5px;
  color: #202329;
  font-size: 15px;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.publisher {
  margin: 0;
  color: #656c76;
  font-size: 12px;
  font-weight: 600;
}

.meta {
  margin: 4px 0 0;
  color: #9197a0;
  font-size: 11px;
  line-height: 1.55;
}

.action-button {
  flex: 0 0 auto;
  min-width: 82px;
  padding: 8px 11px;
  border: 1px solid #25292f;
  background: #25292f;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.action-button.remove {
  border-color: #d4d7db;
  background: #fff;
  color: #606770;
}

.action-button:hover:not(:disabled) {
  opacity: 0.82;
}

.action-button:disabled {
  cursor: default;
  opacity: 0.48;
}

.interest-status {
  flex: 0 0 auto;
  padding: 5px 9px;
  border: 1px solid #25292f;
  border-radius: 999px;
  background: #25292f;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
}

.search-box {
  display: flex;
  align-items: center;
  width: min(100%, 520px);
  height: 42px;
  margin: 18px 0 6px;
  border-bottom: 1px solid #cfd3d8;
}

.search-box:focus-within {
  border-bottom-color: #4d535c;
}

.search-box svg {
  width: 16px;
  height: 16px;
  margin-right: 9px;
  fill: none;
  stroke: #858b94;
  stroke-linecap: round;
  stroke-width: 1.6;
}

.search-box input {
  width: 100%;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #24272c;
  font: inherit;
  font-size: 13px;
}

.search-box input::placeholder {
  color: #a0a5ad;
}

.finder-list {
  margin-top: 8px;
}

.empty-state {
  padding: 28px 2px;
  border-bottom: 1px solid #dfe2e6;
}

.empty-state.compact {
  margin-top: 8px;
}

.empty-state strong {
  display: block;
  color: #343940;
  font-size: 13px;
}

.empty-state p {
  margin: 5px 0 0;
  color: #8a9099;
  font-size: 12px;
}

.loading-state {
  padding: 24px 2px;
  border-bottom: 1px solid #dfe2e6;
}

.finder-loading {
  margin-top: 8px;
}

.loading-line {
  width: 46%;
  height: 10px;
  margin-top: 10px;
  background: #eef0f2;
}

.loading-line:first-child {
  margin-top: 0;
}

.loading-line.wide {
  width: 68%;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 44px;
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

@media (max-width: 640px) {
  .interest-main {
    width: min(100% - 32px, 920px);
    padding-top: 36px;
  }

  .game-row {
    align-items: flex-start;
    gap: 14px;
  }

  .action-button {
    min-width: 74px;
  }
}
</style>
