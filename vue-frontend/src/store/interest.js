import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { gameApi } from '@/api/game.js'
import { interestApi } from '@/api/interest.js'

export const useInterestStore = defineStore('interest', () => {
  const games = ref([])
  const interests = ref([])
  const interestGameIds = ref([])
  const loading = ref(false)
  const idsLoading = ref(false)
  const idsError = ref(null)
  const error = ref(null)
  const actionGameId = ref(null)

  function unwrap(response) {
    return response?.data?.data ?? response?.data
  }

  const interestedGameIds = computed(() => new Set(interestGameIds.value))
  const hasInterests = computed(() => interestGameIds.value.length > 0)

  function isInterested(gameId) {
    return interestedGameIds.value.has(gameId)
  }

  async function loadGameIds() {
    idsLoading.value = true
    idsError.value = null

    try {
      const response = await interestApi.getMyGameIds()
      const data = unwrap(response)
      interestGameIds.value = Array.isArray(data) ? data : []
      return interestGameIds.value
    } catch (err) {
      console.error('[InterestStore] 관심 게임 ID 조회 실패:', err)
      idsError.value = err.response?.data?.message || '관심 게임 정보를 불러오지 못했습니다.'
      interestGameIds.value = []
      return []
    } finally {
      idsLoading.value = false
    }
  }

  async function load() {
    loading.value = true
    error.value = null

    try {
      const [gamesResponse, interestsResponse] = await Promise.all([
        gameApi.getAll(),
        interestApi.getMyGames()
      ])

      const gameData = unwrap(gamesResponse)
      const interestData = unwrap(interestsResponse)

      games.value = Array.isArray(gameData) ? gameData : []
      interests.value = Array.isArray(interestData) ? interestData : []
      interestGameIds.value = interests.value.map((item) => item.gameId)
    } catch (err) {
      console.error('[InterestStore] 관심 게임 초기 조회 실패:', err)
      error.value = err.response?.data?.message || '관심 게임 정보를 불러오지 못했습니다.'
      games.value = []
      interests.value = []
      interestGameIds.value = []
    } finally {
      loading.value = false
    }
  }

  async function addInterest(gameId) {
    actionGameId.value = gameId
    error.value = null

    try {
      const response = await interestApi.addGame(gameId)
      const saved = unwrap(response)

      if (saved && !isInterested(gameId)) {
        interests.value = [...interests.value, saved]
        interestGameIds.value = [...interestGameIds.value, gameId]
      }

      return saved
    } catch (err) {
      console.error('[InterestStore] 관심 게임 등록 실패:', err)
      error.value = err.response?.data?.message || '관심 게임 등록에 실패했습니다.'
      throw err
    } finally {
      actionGameId.value = null
    }
  }

  async function removeInterest(gameId) {
    actionGameId.value = gameId
    error.value = null

    try {
      await interestApi.removeGame(gameId)
      interests.value = interests.value.filter((item) => item.gameId !== gameId)
      interestGameIds.value = interestGameIds.value.filter((id) => id !== gameId)
    } catch (err) {
      console.error('[InterestStore] 관심 게임 해제 실패:', err)
      error.value = err.response?.data?.message || '관심 게임 해제에 실패했습니다.'
      throw err
    } finally {
      actionGameId.value = null
    }
  }

  function clearError() {
    error.value = null
  }

  return {
    games,
    interests,
    interestGameIds,
    interestedGameIds,
    hasInterests,
    loading,
    idsLoading,
    idsError,
    error,
    actionGameId,
    isInterested,
    load,
    loadGameIds,
    addInterest,
    removeInterest,
    clearError
  }
})
