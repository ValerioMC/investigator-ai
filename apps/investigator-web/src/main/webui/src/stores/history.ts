import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'
import type { InvestigationSession } from '@/types/api'
import { useMockData } from '@/composables/useMockData'

export interface HistoryFilters {
  dateFrom: string
  dateTo: string
  confidence: string
  workspace: string
}

export const useHistoryStore = defineStore('history', () => {
  const sessions = ref<InvestigationSession[]>([])
  const loading = ref(false)
  const filters = ref<HistoryFilters>({
    dateFrom: '',
    dateTo: '',
    confidence: '',
    workspace: '',
  })

  async function fetchSessions() {
    loading.value = true
    try {
      const resp = await axios.get<InvestigationSession[]>('/api/web/v1/history')
      sessions.value = resp.data
    } catch {
      const { mockSessions } = useMockData()
      sessions.value = mockSessions
    } finally {
      loading.value = false
    }
  }

  async function deleteSession(id: string) {
    try {
      await axios.delete(`/api/web/v1/history/${id}`)
    } catch {
      // silent fail — still remove from local state
    }
    sessions.value = sessions.value.filter((s) => s.id !== id)
  }

  async function exportSession(id: string): Promise<string> {
    try {
      const resp = await axios.get<{ url: string }>(`/api/web/v1/history/${id}/export`)
      return resp.data.url
    } catch {
      // return a data URI placeholder when API isn't available
      const session = sessions.value.find((s) => s.id === id)
      const data = JSON.stringify(session, null, 2)
      return `data:application/json;charset=utf-8,${encodeURIComponent(data)}`
    }
  }

  function updateFilters(partial: Partial<HistoryFilters>) {
    filters.value = { ...filters.value, ...partial }
  }

  function resetFilters() {
    filters.value = { dateFrom: '', dateTo: '', confidence: '', workspace: '' }
  }

  return {
    sessions,
    loading,
    filters,
    fetchSessions,
    deleteSession,
    exportSession,
    updateFilters,
    resetFilters,
  }
})
