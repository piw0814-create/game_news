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
              placeholder="게임, 프랜차이즈, 회사, 이슈 검색"
              autocomplete="off"
            />
          </label>

          <details ref="filterMenu" class="filter-menu">
            <summary class="filter-trigger" aria-label="기간 및 정렬 설정">
              <span class="filter-caption">필터</span>
              <span>{{ periodLabel }}</span>
              <span class="filter-divider">·</span>
              <span>{{ sortLabel }}</span>
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="m6 8 4 4 4-4" />
              </svg>
            </summary>

            <div class="filter-popover">
              <div class="filter-group">
                <strong>기간</strong>
                <button
                  v-for="period in periods"
                  :key="period.value"
                  type="button"
                  class="filter-option"
                  :class="{ active: periodType === period.value }"
                  @click="selectPeriod(period.value)"
                >
                  <span>{{ period.label }}</span>
                  <span v-if="periodType === period.value" class="check-mark">✓</span>
                </button>
              </div>

              <div class="filter-group sort-group">
                <strong>정렬</strong>
                <button
                  v-for="sort in sortOptions"
                  :key="sort.value"
                  type="button"
                  class="filter-option"
                  :class="{ active: sortType === sort.value }"
                  :disabled="sort.value === 'personalized' && !interestStore.hasInterests"
                  @click="selectSort(sort.value)"
                >
                  <span>{{ sort.label }}</span>
                  <span v-if="sortType === sort.value" class="check-mark">✓</span>
                </button>
              </div>
            </div>
          </details>
        </div>
      </section>

      <section class="feed-section">
        <div class="section-heading">
          <h2>오늘 주요뉴스</h2>
          <span
            v-if="!topicStore.loading && !topicStore.error"
            class="topic-count"
          >
            오늘 {{ todayTopics.length }}개
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
          :interest-type="interestMatchType(featuredTopic)"
          featured
        />

        <div
          v-else-if="filteredTopics.length"
          class="message-state empty-filter-state"
        >
          <strong>오늘 등록된 주요 뉴스가 없습니다.</strong>
          <p>아래 뉴스 목록에서 이전 Topic을 확인할 수 있습니다.</p>
        </div>

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
            v-for="topic in visibleTopics"
            :key="topic.id"
            :topic="topic"
            :interested="hasInterestMatch(topic)"
            :interest-type="interestMatchType(topic)"
          />
        </div>

        <div v-if="hasMoreTopics" class="load-more-wrap">
          <button type="button" class="load-more-button" @click="loadMoreTopics">
            더 보기
            <span>{{ nextLoadCount }}개 더 보기</span>
          </button>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
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

const periods = [
  { label: "전체", value: "ALL", milliseconds: null },
  { label: "최근 4시간", value: "4H", milliseconds: 4 * 60 * 60 * 1000 },
  { label: "최근 24시간", value: "24H", milliseconds: 24 * 60 * 60 * 1000 },
  { label: "최근 7일", value: "7D", milliseconds: 7 * 24 * 60 * 60 * 1000 },
  { label: "최근 1개월", value: "30D", milliseconds: 30 * 24 * 60 * 60 * 1000 },
];

const sortOptions = [
  { label: "최신순", value: "latest" },
  { label: "중요도순", value: "importance" },
  { label: "관심순", value: "personalized" },
];

const PAGE_SIZE = 20;

const searchKeyword = ref("");
const selectedCategory = ref("ALL");
const periodType = ref("ALL");
const sortType = ref("latest");
const visibleCount = ref(PAGE_SIZE);
const filterMenu = ref(null);
const currentTime = ref(Date.now());
let clockTimer = null;

function topicTime(topic) {
  const value = topic.lastUpdatedAt || topic.firstSeenAt || topic.createdAt;
  if (!value) return 0;

  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
}

function compareLatest(a, b) {
  return topicTime(b) - topicTime(a);
}

function isTodayTopic(topic) {
  const value = topic.lastUpdatedAt || topic.firstSeenAt || topic.createdAt;
  if (!value) return false;

  const topicDate = new Date(value);
  if (Number.isNaN(topicDate.getTime())) return false;

  const today = new Date(currentTime.value);
  return (
    topicDate.getFullYear() === today.getFullYear() &&
    topicDate.getMonth() === today.getMonth() &&
    topicDate.getDate() === today.getDate()
  );
}

function matchesPeriod(topic) {
  const selectedPeriod = periods.find(
    (period) => period.value === periodType.value,
  );

  if (!selectedPeriod?.milliseconds) return true;

  const time = topicTime(topic);
  if (!time) return false;

  return time >= currentTime.value - selectedPeriod.milliseconds;
}

function compareImportance(a, b) {
  const importanceDiff = (b.importanceScore ?? 0) - (a.importanceScore ?? 0);
  return importanceDiff !== 0 ? importanceDiff : compareLatest(a, b);
}

const DIRECT_GAME_INTEREST_BONUS = 30;
const FRANCHISE_INTEREST_BONUS = 10;

function interestMatchType(topic) {
  const gameIds = Array.isArray(topic.gameIds) ? topic.gameIds : [];
  if (gameIds.some((gameId) => interestStore.isInterested(gameId))) {
    return "game";
  }

  const franchiseIds = Array.isArray(topic.franchiseIds) ? topic.franchiseIds : [];
  if (franchiseIds.some((franchiseId) => interestStore.isFranchiseInterested(franchiseId))) {
    return "franchise";
  }

  return null;
}

function hasInterestMatch(topic) {
  return interestMatchType(topic) != null;
}

function personalizedScore(topic) {
  const importanceScore = topic.importanceScore ?? 0;
  const matchType = interestMatchType(topic);
  const interestBonus = matchType === "game"
    ? DIRECT_GAME_INTEREST_BONUS
    : matchType === "franchise"
      ? FRANCHISE_INTEREST_BONUS
      : 0;
  const recencyBonus = topic.recencyBonus ?? 0;
  return importanceScore + interestBonus + recencyBonus;
}
function comparePersonalized(a, b) {
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
    if (!categoryMatches || !matchesPeriod(topic)) return false;

    if (!keyword) return true;

    const gameSearchText = (Array.isArray(topic.games) ? topic.games : [])
      .flatMap((game) => [
        game?.name,
        game?.displayName,
        game?.publisher,
        ...(Array.isArray(game?.aliases) ? game.aliases : []),
      ])
      .filter(Boolean);

    const franchiseSearchText = (Array.isArray(topic.franchises) ? topic.franchises : [])
      .flatMap((franchise) => [
        franchise?.name,
        franchise?.displayName,
        ...(Array.isArray(franchise?.aliases) ? franchise.aliases : []),
      ])
      .filter(Boolean);

    const searchableText = [
      topic.title,
      topic.summary,
      topic.whyImportant,
      ...gameSearchText,
      ...franchiseSearchText,
    ]
      .filter(Boolean)
      .join(" ")
      .toLocaleLowerCase("ko-KR");

    return searchableText.includes(keyword);
  });
});

const todayTopics = computed(() =>
  filteredTopics.value.filter((topic) => isTodayTopic(topic)),
);

const featuredTopic = computed(() => {
  if (!todayTopics.value.length) return null;
  return [...todayTopics.value].sort(compareImportance)[0];
});

const otherTopics = computed(() => {
  const topics = featuredTopic.value
    ? filteredTopics.value.filter(
        (topic) => topic.id !== featuredTopic.value.id,
      )
    : [...filteredTopics.value];

  if (sortType.value === "importance") {
    return topics.sort(compareImportance);
  }

  if (sortType.value === "personalized") {
    return topics.sort(comparePersonalized);
  }

  return topics.sort(compareLatest);
});

const visibleTopics = computed(() =>
  otherTopics.value.slice(0, visibleCount.value),
);

const hasMoreTopics = computed(
  () => visibleCount.value < otherTopics.value.length,
);

const nextLoadCount = computed(() =>
  Math.min(PAGE_SIZE, otherTopics.value.length - visibleCount.value),
);

const periodLabel = computed(
  () => periods.find((period) => period.value === periodType.value)?.label || "전체",
);

const sortLabel = computed(() => {
  if (sortType.value === "importance") return "중요도순";
  if (sortType.value === "personalized") return "관심순";
  return "최신순";
});

function selectPeriod(value) {
  periodType.value = value;
  filterMenu.value?.removeAttribute("open");
}

function selectSort(value) {
  if (value === "personalized" && !interestStore.hasInterests) return;
  sortType.value = value;
  filterMenu.value?.removeAttribute("open");
}

function loadMoreTopics() {
  visibleCount.value += PAGE_SIZE;
}

function resetVisibleTopics() {
  visibleCount.value = PAGE_SIZE;
}

function resetFilters() {
  searchKeyword.value = "";
  selectedCategory.value = "ALL";
  periodType.value = "ALL";
}

watch(
  [searchKeyword, selectedCategory, periodType, sortType],
  resetVisibleTopics,
);

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
  clockTimer = window.setInterval(() => {
    currentTime.value = Date.now();
  }, 60 * 1000);
});

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer);
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

.filter-menu {
  position: relative;
  flex: 0 0 auto;
}

.filter-menu summary {
  list-style: none;
}

.filter-menu summary::-webkit-details-marker {
  display: none;
}

.filter-trigger {
  display: flex;
  align-items: center;
  min-width: 172px;
  padding: 8px 5px 8px 8px;
  border-bottom: 1px solid #cfd3d8;
  color: #41464e;
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.filter-caption {
  margin-right: 9px;
  color: #979ca4;
  font-size: 11px;
}

.filter-divider {
  margin: 0 5px;
  color: #b0b5bc;
}

.filter-trigger svg {
  width: 16px;
  height: 16px;
  margin-left: auto;
  fill: none;
  stroke: #616771;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
  transition: transform 0.15s ease;
}

.filter-menu[open] .filter-trigger svg {
  transform: rotate(180deg);
}

.filter-popover {
  position: absolute;
  z-index: 20;
  top: calc(100% + 7px);
  right: 0;
  width: 196px;
  padding: 7px;
  border: 1px solid #dfe2e6;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(20 24 32 / 10%);
}

.filter-group strong {
  display: block;
  padding: 5px 8px 6px;
  color: #636a74;
  font-size: 11px;
}

.filter-group.sort-group {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid #e5e7ea;
}

.filter-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: #444a53;
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.filter-option:hover {
  background: #f6f7f9;
}

.filter-option.active {
  background: #f3f2ff;
  color: #4e55d9;
}

.filter-option:disabled {
  color: #b4b8bf;
  cursor: not-allowed;
}

.filter-option:disabled:hover {
  background: transparent;
}

.check-mark {
  color: #5860ff;
  font-size: 14px;
  line-height: 1;
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

.load-more-wrap {
  display: flex;
  justify-content: center;
  padding-top: 28px;
}

.load-more-button {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  padding: 9px 16px;
  border: 1px solid #d5d9df;
  border-radius: 3px;
  background: #fff;
  color: #343941;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.load-more-button:hover {
  border-color: #aeb4bc;
  background: #fafafa;
}

.load-more-button span {
  color: #8a9099;
  font-size: 11px;
  font-weight: 400;
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

  .filter-menu {
    align-self: flex-end;
  }

  .filter-trigger {
    min-width: 190px;
  }
}
</style>
