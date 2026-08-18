<template>
  <div class="detail-page">
    <AppHeader />

    <main class="detail-container">
      <router-link to="/feed" class="back-link">← 뉴스</router-link>

      <div v-if="topicStore.detailLoading" class="detail-state">
        <div class="loading-line title-line"></div>
        <div class="loading-line"></div>
        <div class="loading-line short"></div>
      </div>

      <div v-else-if="topicStore.error && !topic" class="detail-state error-state">
        <strong>Topic을 불러오지 못했습니다.</strong>
        <p>{{ topicStore.error }}</p>
        <button type="button" class="retry-button" @click="loadTopic">다시 시도</button>
      </div>

      <article v-else-if="topic" class="topic-detail">
        <header class="detail-header">
          <div class="detail-meta">
            <div class="meta-left">
              <span class="category">{{ categoryLabel }}</span>
              <span class="meta-dot">·</span>
              <span class="importance">중요도 <strong>{{ topic.importanceScore ?? '-' }}</strong></span>
            </div>
            <time v-if="updatedLabel" class="updated-at">{{ updatedLabel }}</time>
          </div>

          <h1>{{ topic.title }}</h1>
          <p v-if="topic.summary" class="summary">{{ topic.summary }}</p>
        </header>

        <section v-if="topic.whyImportant" class="detail-section why-section">
          <h2>왜 중요한가</h2>
          <p>{{ topic.whyImportant }}</p>
        </section>

        <section class="detail-section">
          <div class="section-heading">
            <h2>관련 게임</h2>
            <span>{{ games.length }}개</span>
          </div>

          <div v-if="games.length" class="game-list">
            <div v-for="game in games" :key="game.id" class="game-row">
              <div>
                <strong>{{ game.name }}</strong>
                <span v-if="game.isPrimary" class="primary-label">주요 게임</span>
              </div>
              <span v-if="game.relevanceScore != null" class="score-label">
                관련도 {{ formatScore(game.relevanceScore) }}
              </span>
            </div>
          </div>
          <p v-else class="empty-copy">연결된 게임이 없습니다.</p>
        </section>

        <section class="detail-section">
          <div class="section-heading">
            <h2>관련 뉴스</h2>
            <span>{{ articles.length }}개</span>
          </div>

          <div v-if="articles.length" class="article-list">
            <a
              v-for="article in articles"
              :key="article.id"
              :href="article.url"
              class="article-row"
              target="_blank"
              rel="noopener noreferrer"
            >
              <div class="article-copy">
                <div class="article-source">
                  <span>{{ article.sourceName || '출처 미상' }}</span>
                  <span v-if="article.sourceType" class="meta-dot">·</span>
                  <span v-if="article.sourceType">{{ sourceTypeLabel(article.sourceType) }}</span>
                </div>
                <strong>{{ article.title }}</strong>
              </div>
              <span class="original-link">원문 보기 →</span>
            </a>
          </div>
          <p v-else class="empty-copy">연결된 뉴스가 없습니다.</p>
        </section>
      </article>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { useTopicStore } from '@/store/topic.js'

const route = useRoute()
const topicStore = useTopicStore()

const CATEGORY_LABELS = {
  RELEASE: '출시',
  UPDATE: '업데이트',
  INDUSTRY: '산업',
  ESPORTS: 'e스포츠',
  EVENT: '이벤트',
  CONTROVERSY: '논란',
  OTHER: '기타'
}

const SOURCE_TYPE_LABELS = {
  OFFICIAL: '공식',
  MEDIA: '언론',
  COMMUNITY: '커뮤니티'
}

const topic = computed(() => topicStore.selectedTopic)
const games = computed(() => topic.value?.games ?? [])
const articles = computed(() => topic.value?.articles ?? [])
const categoryLabel = computed(() => {
  const category = topic.value?.category
  return CATEGORY_LABELS[category] || category || '기타'
})

const updatedLabel = computed(() => {
  const value = topic.value?.lastUpdatedAt || topic.value?.firstSeenAt || topic.value?.createdAt
  if (!value) return ''

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
})

function sourceTypeLabel(value) {
  return SOURCE_TYPE_LABELS[value] || value
}

function formatScore(value) {
  const score = Number(value)
  return Number.isNaN(score) ? value : score.toFixed(2)
}

async function loadTopic() {
  try {
    await topicStore.fetchTopic(route.params.id)
  } catch {
    // 오류 상태는 store에서 화면에 표시한다.
  }
}

watch(
  () => route.params.id,
  () => loadTopic(),
  { immediate: true }
)

onBeforeUnmount(() => {
  topicStore.clearSelectedTopic()
})
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: #fff;
}

.detail-container {
  width: min(920px, calc(100% - 48px));
  margin: 0 auto;
  padding: 42px 0 88px;
}

.back-link {
  display: inline-block;
  margin-bottom: 34px;
  color: #7b828c;
  font-size: 12px;
  font-weight: 600;
}

.back-link:hover {
  color: #1f2329;
}

.detail-header {
  padding-bottom: 40px;
  border-bottom: 1px solid #dfe3e8;
}

.detail-meta,
.meta-left,
.section-heading,
.game-row,
.article-row,
.article-source {
  display: flex;
  align-items: center;
}

.detail-meta {
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;
  color: #8a9099;
  font-size: 12px;
}

.meta-left,
.article-source {
  gap: 7px;
}

.category {
  color: var(--color-primary);
  font-weight: 700;
}

.meta-dot {
  color: #c4c8ce;
}

.importance strong {
  color: #252930;
  font-size: 13px;
}

.updated-at {
  color: #9aa0a8;
}

.detail-header h1 {
  margin: 0;
  color: #15171b;
  font-size: 34px;
  line-height: 1.35;
  letter-spacing: -0.035em;
}

.summary {
  max-width: 820px;
  margin-top: 18px;
  color: #565d67;
  font-size: 16px;
  line-height: 1.8;
}

.detail-section {
  padding: 34px 0;
  border-bottom: 1px solid #e5e8ec;
}

.detail-section h2 {
  margin: 0;
  color: #202329;
  font-size: 16px;
  line-height: 1.4;
  letter-spacing: -0.02em;
}

.why-section p {
  max-width: 820px;
  margin-top: 12px;
  color: #555c66;
  font-size: 14px;
  line-height: 1.8;
}

.section-heading {
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 13px;
  border-bottom: 1px solid #25282d;
}

.section-heading span {
  color: #9298a1;
  font-size: 12px;
}

.game-list,
.article-list {
  border-bottom: 1px solid #eceef1;
}

.game-row {
  justify-content: space-between;
  gap: 20px;
  min-height: 62px;
  padding: 14px 2px;
  border-top: 1px solid #eceef1;
}

.game-row:first-child,
.article-row:first-child {
  border-top: 0;
}

.game-row > div {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.game-row strong {
  color: #262a30;
  font-size: 14px;
}

.primary-label {
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 700;
}

.score-label {
  flex-shrink: 0;
  color: #858c96;
  font-size: 12px;
}

.article-row {
  justify-content: space-between;
  gap: 24px;
  padding: 18px 2px;
  border-top: 1px solid #eceef1;
}

.article-row:hover .article-copy strong,
.article-row:hover .original-link {
  color: var(--color-primary);
}

.article-copy {
  min-width: 0;
}

.article-source {
  margin-bottom: 5px;
  color: #8b929b;
  font-size: 11px;
}

.article-copy strong {
  display: block;
  color: #292d33;
  font-size: 14px;
  line-height: 1.5;
  transition: color 0.16s ease;
}

.original-link {
  flex-shrink: 0;
  color: #7d848e;
  font-size: 12px;
  font-weight: 600;
  transition: color 0.16s ease;
}

.empty-copy {
  padding: 22px 2px;
  color: #9298a1;
  font-size: 13px;
}

.detail-state {
  padding: 30px 0;
}

.detail-state strong {
  color: #343940;
  font-size: 14px;
}

.detail-state p {
  margin-top: 6px;
  color: #7b828c;
  font-size: 13px;
}

.loading-line {
  width: 72%;
  height: 12px;
  margin-top: 12px;
  background: #eceff2;
  animation: pulse 1.2s ease-in-out infinite alternate;
}

.loading-line:first-child {
  margin-top: 0;
}

.loading-line.title-line {
  width: 82%;
  height: 30px;
}

.loading-line.short {
  width: 48%;
}

.retry-button {
  margin-top: 15px;
  padding: 7px 12px;
  border: 1px solid #d5d9df;
  border-radius: 3px;
  background: #fff;
  color: #404650;
  font-size: 12px;
}

@keyframes pulse {
  from { opacity: 0.55; }
  to { opacity: 1; }
}

@media (max-width: 640px) {
  .detail-container {
    width: min(100% - 32px, 920px);
    padding-top: 30px;
  }

  .back-link {
    margin-bottom: 26px;
  }

  .detail-header h1 {
    font-size: 27px;
  }

  .detail-meta,
  .game-row,
  .article-row {
    align-items: flex-start;
  }

  .updated-at,
  .score-label,
  .original-link {
    margin-top: 2px;
  }

  .article-row {
    gap: 14px;
  }
}
</style>
