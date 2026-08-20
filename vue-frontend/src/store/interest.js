import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { gameApi } from '@/api/game.js'
import { interestApi } from '@/api/interest.js'

export const useInterestStore = defineStore('interest', () => {
  const games = ref([])
  const interests = ref([])
  const interestGameIds = ref([])
  const interestFranchiseIds = ref([])
  const loading = ref(false)
  const idsLoading = ref(false)
  const idsError = ref(null)
  const error = ref(null)
  const actionGameId = ref(null)

  function unwrap(response) {
    return response?.data?.data ?? response?.data
  }

  const interestedGameIds = computed(() => new Set(interestGameIds.value))
  const interestedFranchiseIds = computed(() => new Set(interestFranchiseIds.value))
  const hasInterests = computed(() => interestGameIds.value.length > 0)

  function isInterested(gameId) {
    return interestedGameIds.value.has(gameId)
  }

  function isFranchiseInterested(franchiseId) {
    return interestedFranchiseIds.value.has(franchiseId)
  }

  async function loadFranchiseIds(gameIds = interestGameIds.value) {
    const uniqueGameIds = [...new Set(gameIds)].filter((gameId) => gameId != null)
    if (!uniqueGameIds.length) {
      interestFranchiseIds.value = []
      return []
    }

    const results = await Promise.allSettled(
      uniqueGameIds.map((gameId) => gameApi.getFranchises(gameId))
    )

    const franchiseIds = new Set()
    for (const result of results) {
      if (result.status !== 'fulfilled') continue
      const relations = unwrap(result.value)
      if (!Array.isArray(relations)) continue
      relations.forEach((relation) => {
        if (relation?.franchiseId != null) franchiseIds.add(relation.franchiseId)
      })
    }

    interestFranchiseIds.value = [...franchiseIds]
    return interestFranchiseIds.value
  }

  async function loadGameIds() {
    idsLoading.value = true
    idsError.value = null

    try {
      const response = await interestApi.getMyGameIds()
      const data = unwrap(response)
      interestGameIds.value = Array.isArray(data) ? data : []
      await loadFranchiseIds(interestGameIds.value)
      return interestGameIds.value
    } catch (err) {
      console.error('[InterestStore] 관심 게임 ID 조회 실패:', err)
      idsError.value = err.response?.data?.message || '관심 게임 정보를 불러오지 못했습니다.'
      interestGameIds.value = []
      interestFranchiseIds.value = []
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
      await loadFranchiseIds(interestGameIds.value)
    } catch (err) {
      console.error('[InterestStore] 관심 게임 초기 조회 실패:', err)
      error.value = err.response?.data?.message || '관심 게임 정보를 불러오지 못했습니다.'
      games.value = []
      interests.value = []
      interestGameIds.value = []
      interestFranchiseIds.value = []
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
        await loadFranchiseIds(interestGameIds.value)
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
      await loadFranchiseIds(interestGameIds.value)
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
    interestFranchiseIds,
    interestedGameIds,
    interestedFranchiseIds,
    hasInterests,
    loading,
    idsLoading,
    idsError,
    error,
    actionGameId,
    isInterested,
    isFranchiseInterested,
    load,
    loadGameIds,
    loadFranchiseIds,
    addInterest,
    removeInterest,
    clearError
  }
})
