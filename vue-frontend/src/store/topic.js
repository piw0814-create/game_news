import { defineStore } from 'pinia'
import { ref } from 'vue'
import { topicApi } from '@/api/topic.js'

export const useTopicStore = defineStore('topic', () => {
  const topics = ref([])
  const selectedTopic = ref(null)
  const loading = ref(false)
  const detailLoading = ref(false)
  const error = ref(null)

  function unwrap(response) {
    return response?.data?.data ?? response?.data
  }

  async function fetchTopics() {
    loading.value = true
    error.value = null

    try {
      const response = await topicApi.getAll()
      const data = unwrap(response)
      topics.value = Array.isArray(data) ? data : []
    } catch (err) {
      console.error('[TopicStore] Topic 목록 조회 실패:', err)
      error.value = err.response?.data?.message || 'Topic 목록을 불러오지 못했습니다.'
      topics.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchTopic(id) {
    detailLoading.value = true
    error.value = null

    try {
      const response = await topicApi.getById(id)
      selectedTopic.value = unwrap(response) ?? null
      return selectedTopic.value
    } catch (err) {
      console.error('[TopicStore] Topic 상세 조회 실패:', err)
      error.value = err.response?.data?.message || 'Topic 상세 정보를 불러오지 못했습니다.'
      selectedTopic.value = null
      throw err
    } finally {
      detailLoading.value = false
    }
  }

  function clearSelectedTopic() {
    selectedTopic.value = null
  }

  return {
    topics,
    selectedTopic,
    loading,
    detailLoading,
    error,
    fetchTopics,
    fetchTopic,
    clearSelectedTopic
  }
})
