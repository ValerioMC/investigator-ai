import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'
import type { Person, Company, Contract } from '@/types/api'
import { useMockData } from '@/composables/useMockData'

export type EntityType = 'person' | 'company' | 'contract'

export const useEntitiesStore = defineStore('entities', () => {
  const persons = ref<Person[]>([])
  const companies = ref<Company[]>([])
  const contracts = ref<Contract[]>([])
  const loading = ref(false)
  const selectedEntity = ref<Person | Company | Contract | null>(null)
  const activeTab = ref<EntityType>('person')

  async function fetchPersons() {
    loading.value = true
    try {
      const resp = await axios.get<Person[]>('/api/web/v1/entities/persons')
      persons.value = resp.data
    } catch {
      const { mockPersons } = useMockData()
      persons.value = mockPersons
    } finally {
      loading.value = false
    }
  }

  async function fetchCompanies() {
    loading.value = true
    try {
      const resp = await axios.get<Company[]>('/api/web/v1/entities/companies')
      companies.value = resp.data
    } catch {
      const { mockCompanies } = useMockData()
      companies.value = mockCompanies
    } finally {
      loading.value = false
    }
  }

  async function fetchContracts() {
    loading.value = true
    try {
      const resp = await axios.get<Contract[]>('/api/web/v1/entities/contracts')
      contracts.value = resp.data
    } catch {
      const { mockContracts } = useMockData()
      contracts.value = mockContracts
    } finally {
      loading.value = false
    }
  }

  async function fetchAll() {
    await Promise.all([fetchPersons(), fetchCompanies(), fetchContracts()])
  }

  function selectEntity(entity: Person | Company | Contract | null) {
    selectedEntity.value = entity
  }

  function setActiveTab(tab: EntityType) {
    activeTab.value = tab
    selectedEntity.value = null
  }

  return {
    persons,
    companies,
    contracts,
    loading,
    selectedEntity,
    activeTab,
    fetchPersons,
    fetchCompanies,
    fetchContracts,
    fetchAll,
    selectEntity,
    setActiveTab,
  }
})
