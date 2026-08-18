<template>
  <router-link :to="{ name: 'TopicDetail', params: { id: topic.id } }" class="topic-card-link">
    <article class="topic-card" :class="{ featured }">
      <div class="topic-meta">
        <div class="meta-left">
          <span class="category">{{ categoryLabel }}</span>
          <span v-if="dateLabel" class="meta-dot">·</span>
          <time v-if="dateLabel">{{ dateLabel }}</time>
        </div>
        <div class="meta-right">
          <span v-if="interested" class="interest-badge">관심 게임</span>
          <span class="importance">중요도 <strong>{{ topic.importanceScore ?? '-' }}</strong></span>
        </div>
      </div>

      <h2 class="topic-title">{{ topic.title }}</h2>

      <p v-if="topic.summary" class="topic-summary">
        {{ topic.summary }}
      </p>

      <div v-if="topic.whyImportant" class="why-important">
        <span class="why-label">왜 중요한가</span>
        <p>{{ topic.whyImportant }}</p>
      </div>
    </article>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  topic: {
    type: Object,
    required: true
  },
  featured: {
    type: Boolean,
    default: false
  },
  interested: {
    type: Boolean,
    default: false
  }
})


const CATEGORY_LABELS = {
  RELEASE: '출시',
  UPDATE: '업데이트',
  INDUSTRY: '산업',
  ESPORTS: 'e스포츠',
  EVENT: '이벤트',
  CONTROVERSY: '논란',
  OTHER: '기타'
}

const categoryLabel = computed(() => CATEGORY_LABELS[props.topic.category] || props.topic.category || '기타')

const dateLabel = computed(() => {
  const value = props.topic.lastUpdatedAt || props.topic.firstSeenAt || props.topic.createdAt
  if (!value) return ''

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  return new Intl.DateTimeFormat('ko-KR', {
    month: 'numeric',
    day: 'numeric'
  }).format(date)
})

</script>

<style scoped>
.topic-card-link {
  display: block;
}

.topic-card {
  padding: 22px 2px 24px;
  border-bottom: 1px solid var(--color-border);
  transition: background-color 0.16s ease;
}

.topic-card:hover {
  background: #fafafa;
}

.topic-card-link:hover .topic-title {
  color: var(--color-primary);
}

.topic-card.featured {
  padding: 26px 26px 24px;
  border: 1px solid #e7e9ed;
  border-radius: 4px;
  background: #fff;
}

.topic-card.featured:hover {
  background: #fcfcfc;
}

.topic-meta,
.meta-left,
.meta-right {
  display: flex;
  align-items: center;
}

.topic-meta {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.meta-left {
  gap: 7px;
  min-width: 0;
}

.meta-right {
  gap: 10px;
  flex-shrink: 0;
}

.category {
  color: var(--color-primary);
  font-weight: 700;
}

.meta-dot {
  color: #c3c7ce;
}

.interest-badge {
  padding: 3px 7px;
  border: 1px solid #d9dde3;
  border-radius: 999px;
  color: #555c66;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.importance {
  flex-shrink: 0;
  color: var(--color-text-secondary);
}

.importance strong {
  color: var(--color-text-primary);
  font-size: 13px;
}

.topic-title {
  margin: 0;
  color: #17191d;
  font-size: 20px;
  line-height: 1.42;
  letter-spacing: -0.025em;
  font-weight: 700;
  transition: color 0.16s ease;
}

.featured .topic-title {
  font-size: 26px;
  line-height: 1.36;
}

.topic-summary {
  margin-top: 10px;
  color: #555c66;
  font-size: 14px;
  line-height: 1.72;
}

.featured .topic-summary {
  max-width: 780px;
  font-size: 15px;
}

.why-important {
  margin-top: 15px;
  padding-left: 13px;
  border-left: 2px solid #d9dde3;
}

.why-label {
  display: block;
  margin-bottom: 3px;
  color: #363b43;
  font-size: 12px;
  font-weight: 700;
}

.why-important p {
  color: #6a717c;
  font-size: 13px;
  line-height: 1.65;
}


@media (max-width: 640px) {
  .topic-card.featured {
    padding: 20px 18px;
  }

  .featured .topic-title {
    font-size: 22px;
  }

  .topic-meta {
    align-items: flex-start;
  }

  .meta-right {
    align-items: flex-end;
    flex-direction: column;
    gap: 5px;
  }
}
</style>
