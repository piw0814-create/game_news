<template>
  <div class="feed-page">
    <AppHeader />

    <main class="feed-container">
      <section class="feed-intro">
        <h1>뉴스</h1>
        <p class="intro-copy">
          여러 기사를 하나의 사건으로 묶어 게임 업계의 핵심 이슈를 빠르게
          확인합니다.
        </p>
      </section>

      <section class="feed-controls" aria-label="뉴스 검색 및 필터">
        <div class="category-tabs" role="tablist" aria-label="카테고리">
          <button
            v-for="category in categories"
            :key="category.value"
            type="button"
            class="category-tab"
            :class="{ active: selectedCategory === category.value }"
            :aria-selected="selectedCategory === category.value"
            role="tab"
            @click="selectedCategory = category.value"
          >
            {{ category.label }}
          </button>
        </div>

        <div class="control-row">
          <label class="search-box">
            <span class="sr-only">뉴스 검색</span>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="6.5" />
              <path d="m16 16 4 4" />
            </svg>
            <input
              v-model.trim="searchKeyword"
              type="search"
              placeholder="게임, 회사, 이슈 검색"
              autocomplete="off"
            />
          </label>

          <label class="sort-box">
            <span class="sort-caption">정렬</span>
            <select v-model="sortType" aria-label="뉴스 정렬">
              <option value="latest">최신순</option>
              <option value="importance">중요도순</option>
              <option
                value="personalized"
                :disabled="!interestStore.hasInterests"
              >
                관심순
              </option>
            </select>
          </label>
        </div>
      </section>

      <section class="feed-section">
        <div class="section-heading">
          <h2>오늘 주요뉴스</h2>
          <span
            v-if="!topicStore.loading && !topicStore.error"
            class="topic-count"
          >
            Topic {{ filteredTopics.length }}개
          </span>
        </div>

        <div v-if="topicStore.loading" class="loading-state">
          <div class="loading-line wide"></div>
          <div class="loading-line"></div>
          <div class="loading-line short"></div>
        </div>

        <div v-else-if="topicStore.error" class="message-state error-state">
          <strong>Topic을 불러오지 못했습니다.</strong>
          <p>{{ topicStore.error }}</p>
          <button
            type="button"
            class="retry-button"
            @click="topicStore.fetchTopics"
          >
            다시 시도
          </button>
        </div>

        <TopicCard
          v-else-if="featuredTopic"
          :topic="featuredTopic"
          :interested="hasInterestMatch(featuredTopic)"
          featured
        />

        <div
          v-else-if="topicStore.topics.length"
          class="message-state empty-filter-state"
        >
          <strong>조건에 맞는 뉴스가 없습니다.</strong>
          <p>검색어나 카테고리를 변경해보세요.</p>
          <button type="button" class="reset-button" @click="resetFilters">
            필터 초기화
          </button>
        </div>

        <div v-else class="message-state">
          <strong>아직 등록된 Topic이 없습니다.</strong>
          <p>새로운 게임 이슈가 등록되면 이곳에 표시됩니다.</p>
        </div>
      </section>

      <section v-if="otherTopics.length" class="feed-section news-section">
        <div class="section-heading news-heading">
          <h2>뉴스</h2>
          <span class="sort-label">{{ sortLabel }}</span>
        </div>

        <div class="topic-list">
          <TopicCard
            v-for="topic in otherTopics"
            :key="topic.id"
            :topic="topic"
            :interested="hasInterestMatch(topic)"
          />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import AppHeader from "@/components/AppHeader.vue";
import TopicCard from "@/components/TopicCard.vue";
import { useTopicStore } from "@/store/topic.js";
import { useInterestStore } from "@/store/interest.js";

const topicStore = useTopicStore();
const interestStore = useInterestStore();

const categories = [
  { label: "전체", value: "ALL" },
  { label: "출시", value: "RELEASE" },
  { label: "업데이트", value: "UPDATE" },
  { label: "산업", value: "INDUSTRY" },
  { label: "e스포츠", value: "ESPORTS" },
  { label: "이벤트", value: "EVENT" },
  { label: "논란", value: "CONTROVERSY" },
  { label: "기타", value: "OTHER" },
];

const searchKeyword = ref("");
const selectedCategory = ref("ALL");
const sortType = ref("latest");

function topicTime(topic) {
  const value = topic.lastUpdatedAt || topic.firstSeenAt || topic.createdAt;
  if (!value) return 0;

  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
}

function compareLatest(a, b) {
  return topicTime(b) - topicTime(a);
}

function compareImportance(a, b) {
  const importanceDiff = (b.importanceScore ?? 0) - (a.importanceScore ?? 0);
  return importanceDiff !== 0 ? importanceDiff : compareLatest(a, b);
}

function hasInterestMatch(topic) {
  const gameIds = Array.isArray(topic.gameIds) ? topic.gameIds : [];
  return gameIds.some((gameId) => interestStore.isInterested(gameId));
}

function personalizedScore(topic) {
  const importanceScore = topic.importanceScore ?? 0;
  const interestBonus = hasInterestMatch(topic) ? 20 : 0;
  const recencyBonus = topic.recencyBonus ?? 0;
  return importanceScore + interestBonus + recencyBonus;
}
function comparePersonalized(a, b) {
  const interestDiff =
    Number(hasInterestMatch(b)) - Number(hasInterestMatch(a));

  if (interestDiff !== 0) return interestDiff;

  const personalizedDiff = personalizedScore(b) - personalizedScore(a);

  if (personalizedDiff !== 0) return personalizedDiff;

  return compareImportance(a, b);
}

const filteredTopics = computed(() => {
  const keyword = searchKeyword.value.toLocaleLowerCase("ko-KR");

  return topicStore.topics.filter((topic) => {
    const categoryMatches =
      selectedCategory.value === "ALL" ||
      topic.category === selectedCategory.value;
    if (!categoryMatches) return false;

    if (!keyword) return true;

    const searchableText = [topic.title, topic.summary, topic.whyImportant]
      .filter(Boolean)
      .join(" ")
      .toLocaleLowerCase("ko-KR");

    return searchableText.includes(keyword);
  });
});

const featuredTopic = computed(() => {
  if (!filteredTopics.value.length) return null;
  return [...filteredTopics.value].sort(compareImportance)[0];
});

const otherTopics = computed(() => {
  if (!featuredTopic.value) return [];

  const topics = filteredTopics.value.filter(
    (topic) => topic.id !== featuredTopic.value.id,
  );

  if (sortType.value === "importance") {
    return topics.sort(compareImportance);
  }

  if (sortType.value === "personalized") {
    return topics.sort(comparePersonalized);
  }

  return topics.sort(compareLatest);
});

const sortLabel = computed(() => {
  if (sortType.value === "importance") return "중요도순";
  if (sortType.value === "personalized") return "관심순";
  return "최신순";
});

function resetFilters() {
  searchKeyword.value = "";
  selectedCategory.value = "ALL";
}

watch(
  () => interestStore.hasInterests,
  (hasInterests) => {
    if (!hasInterests && sortType.value === "personalized") {
      sortType.value = "latest";
    }
  },
);

onMounted(() => {
  Promise.all([topicStore.fetchTopics(), interestStore.loadGameIds()]);
});
</script>

<style scoped>
.feed-page {
  min-height: 100vh;
  background: #fff;
}

.feed-container {
  width: min(920px, calc(100% - 48px));
  margin: 0 auto;
  padding: 58px 0 88px;
}

.feed-intro {
  padding-bottom: 32px;
}

.feed-intro h1 {
  margin: 0;
  color: #14161a;
  font-size: 28px;
  line-height: 1.3;
  letter-spacing: -0.035em;
}

.intro-copy {
  margin-top: 8px;
  color: #777e88;
  font-size: 13px;
  line-height: 1.7;
}

.feed-controls {
  margin-bottom: 44px;
}

.category-tabs {
  display: flex;
  gap: 0;
  overflow-x: auto;
  border-top: 1px solid #e5e7ea;
  border-bottom: 1px solid #d9dce1;
  scrollbar-width: none;
}

.category-tabs::-webkit-scrollbar {
  display: none;
}

.category-tab {
  position: relative;
  flex: 0 0 auto;
  padding: 13px 14px 12px;
  border: 0;
  background: transparent;
  color: #737a84;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.category-tab:first-child {
  padding-left: 2px;
  padding-right: 18px;
}

.category-tab:hover {
  color: #2e333a;
}

.category-tab.active {
  color: #17191d;
  font-weight: 700;
}

.category-tab.active::after {
  content: "";
  position: absolute;
  right: 12px;
  bottom: -1px;
  left: 12px;
  height: 2px;
  background: #17191d;
}

.category-tab:first-child.active::after {
  left: 2px;
  right: 18px;
}

.control-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 14px;
}

.search-box {
  display: flex;
  align-items: center;
  flex: 1 1 420px;
  max-width: 540px;
  height: 38px;
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

.sort-box {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.sort-caption {
  color: #979ca4;
  font-size: 11px;
}

.sort-box select {
  min-width: 90px;
  padding: 7px 25px 7px 8px;
  border: 0;
  border-bottom: 1px solid #cfd3d8;
  border-radius: 0;
  outline: 0;
  background-color: #fff;
  color: #41464e;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.feed-section + .feed-section {
  margin-top: 54px;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #17191d;
}

.section-heading h2 {
  margin: 0;
  color: #17191d;
  font-size: 18px;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.topic-count,
.sort-label {
  color: #8a9099;
  font-size: 12px;
}

.news-heading {
  margin-bottom: 0;
  border-bottom-width: 1px;
}

.topic-list {
  border-bottom: 1px solid var(--color-border);
}

.loading-state,
.message-state {
  padding: 32px 26px;
  border: 1px solid var(--color-border);
  background: #fff;
}

.loading-line {
  width: 68%;
  height: 12px;
  margin-top: 12px;
  border-radius: 2px;
  background: #eceff2;
  animation: pulse 1.2s ease-in-out infinite alternate;
}

.loading-line:first-child {
  margin-top: 0;
}

.loading-line.wide {
  width: 86%;
  height: 21px;
}

.loading-line.short {
  width: 45%;
}

.message-state strong {
  color: #353a42;
  font-size: 14px;
}

.message-state p {
  margin-top: 6px;
  color: #7a818b;
  font-size: 13px;
}

.error-state {
  border-color: #eadada;
  background: #fffafa;
}

.retry-button,
.reset-button {
  margin-top: 15px;
  padding: 7px 12px;
  border: 1px solid #d5d9df;
  border-radius: 3px;
  background: #fff;
  color: #404650;
  font-size: 12px;
  cursor: pointer;
}

.empty-filter-state {
  border-style: dashed;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@keyframes pulse {
  from {
    opacity: 0.55;
  }
  to {
    opacity: 1;
  }
}

@media (max-width: 640px) {
  .feed-container {
    width: min(100% - 32px, 920px);
    padding-top: 38px;
  }

  .feed-intro {
    padding-bottom: 26px;
  }

  .feed-controls {
    margin-bottom: 36px;
  }

  .category-tab {
    padding-right: 11px;
    padding-left: 11px;
  }

  .category-tab:first-child {
    padding-left: 1px;
    padding-right: 14px;
  }

  .category-tab.active::after {
    right: 9px;
    left: 9px;
  }

  .category-tab:first-child.active::after {
    left: 1px;
    right: 14px;
  }

  .control-row {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .search-box {
    flex-basis: auto;
    max-width: none;
  }

  .sort-box {
    justify-content: flex-end;
  }
}
</style>
