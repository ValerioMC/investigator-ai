import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'
import type { InvestigationReport } from '@/types/api'

export const useInvestigationStore = defineStore('investigation', () => {
  const query = ref('')
  const depth = ref(5)
  const focusEntities = ref<string[]>([])
  const loading = ref(false)
  const result = ref<InvestigationReport | null>(null)
  const error = ref<string | null>(null)

  async function runInvestigation() {
    if (!query.value.trim()) return

    loading.value = true
    error.value = null
    result.value = null

    try {
      const response = await axios.post('/api/web/v1/sessions', {
        query: query.value,
        depth: depth.value,
        focusEntities: focusEntities.value,
      })
      // SessionResource returns a session; extract the report field
      const session = response.data
      if (session.report) {
        try {
          result.value = typeof session.report === 'string'
            ? JSON.parse(session.report)
            : session.report
        } catch {
          result.value = {
            query: query.value,
            summary: session.report,
            findings: [],
            entityMap: { persons: [], companies: [], contracts: [] },
            recommendedFollowUps: [],
            disclaimer: 'This report is a journalistic aid. Claims require editorial verification before publication.',
          }
        }
      } else if (session.status === 'FAILED') {
        error.value = session.errorMessage ?? 'Investigation failed'
      }
    } catch (err: any) {
      error.value = err?.response?.data?.message ?? err?.message ?? 'Request failed'
    } finally {
      loading.value = false
    }
  }

  function clearResult() {
    result.value = null
    error.value = null
  }

  function addFocusEntity(entity: string) {
    const trimmed = entity.trim()
    if (trimmed && !focusEntities.value.includes(trimmed)) {
      focusEntities.value.push(trimmed)
    }
  }

  function removeFocusEntity(entity: string) {
    focusEntities.value = focusEntities.value.filter((e) => e !== entity)
  }

  return {
    query,
    depth,
    focusEntities,
    loading,
    result,
    error,
    runInvestigation,
    clearResult,
    addFocusEntity,
    removeFocusEntity,
  }
})
