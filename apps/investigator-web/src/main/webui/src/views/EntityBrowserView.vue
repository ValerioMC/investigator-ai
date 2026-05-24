<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Search, X, ChevronUp, ChevronDown, ExternalLink, User, Building2, FileText } from 'lucide-vue-next'
import { useDebounceFn } from '@vueuse/core'
import { useEntitiesStore } from '@/stores/entities'
import type { Person, Company, Contract } from '@/types/api'
import EntityTag from '@/components/EntityTag.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const store = useEntitiesStore()

const searchQuery = ref('')
const sortKey = ref('')
const sortDir = ref<'asc' | 'desc'>('asc')

onMounted(() => store.fetchAll())

function sortBy(key: string) {
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDir.value = 'asc'
  }
}

const debouncedSearch = useDebounceFn((val: string) => {
  searchQuery.value = val
}, 300)

const rawSearch = ref('')
watch(rawSearch, (v) => debouncedSearch(v))

// --- Persons ---
const filteredPersons = computed(() => {
  let list = [...store.persons]
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(
      (p) =>
        p.fullName.toLowerCase().includes(q) ||
        (p.nationality ?? '').toLowerCase().includes(q) ||
        (p.politicalRole ?? '').toLowerCase().includes(q),
    )
  }
  if (sortKey.value) {
    list.sort((a, b) => {
      const va = (a as Record<string, unknown>)[sortKey.value]
      const vb = (b as Record<string, unknown>)[sortKey.value]
      const cmp = String(va ?? '').localeCompare(String(vb ?? ''))
      return sortDir.value === 'asc' ? cmp : -cmp
    })
  }
  return list
})

// --- Companies ---
const filteredCompanies = computed(() => {
  let list = [...store.companies]
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.jurisdiction.toLowerCase().includes(q) ||
        (c.legalForm ?? '').toLowerCase().includes(q),
    )
  }
  if (sortKey.value) {
    list.sort((a, b) => {
      const va = (a as Record<string, unknown>)[sortKey.value]
      const vb = (b as Record<string, unknown>)[sortKey.value]
      const cmp = String(va ?? '').localeCompare(String(vb ?? ''))
      return sortDir.value === 'asc' ? cmp : -cmp
    })
  }
  return list
})

// --- Contracts ---
const filteredContracts = computed(() => {
  let list = [...store.contracts]
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(
      (k) =>
        k.title.toLowerCase().includes(q) ||
        k.publicBodyName.toLowerCase().includes(q) ||
        k.awardedTo.toLowerCase().includes(q),
    )
  }
  if (sortKey.value) {
    list.sort((a, b) => {
      const va = (a as Record<string, unknown>)[sortKey.value]
      const vb = (b as Record<string, unknown>)[sortKey.value]
      if (typeof va === 'number' && typeof vb === 'number') {
        return sortDir.value === 'asc' ? va - vb : vb - va
      }
      const cmp = String(va ?? '').localeCompare(String(vb ?? ''))
      return sortDir.value === 'asc' ? cmp : -cmp
    })
  }
  return list
})

function formatCurrency(amount: number) {
  return new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' }).format(amount)
}

function formatDate(dateStr: string) {
  return new Intl.DateTimeFormat('it-IT', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(dateStr),
  )
}

function riskColor(score: number) {
  if (score >= 0.7) return { text: 'text-danger', bg: 'bg-danger/10 border-danger/30' }
  if (score >= 0.4) return { text: 'text-confidence-medium', bg: 'bg-confidence-medium/10 border-confidence-medium/30' }
  return { text: 'text-confidence-high', bg: 'bg-confidence-high/10 border-confidence-high/30' }
}

const tabs = [
  { key: 'person' as const, label: 'Persons', icon: User },
  { key: 'company' as const, label: 'Companies', icon: Building2 },
  { key: 'contract' as const, label: 'Contracts', icon: FileText },
]

const tabCounts = computed(() => ({
  person: store.persons.length,
  company: store.companies.length,
  contract: store.contracts.length,
}))

// Slide-over
const slideOverEntity = ref<Person | Company | Contract | null>(null)
const slideOverType = ref<'person' | 'company' | 'contract'>('person')

function openSlideOver(entity: Person | Company | Contract, type: 'person' | 'company' | 'contract') {
  slideOverEntity.value = entity
  slideOverType.value = type
}

function closeSlideOver() {
  slideOverEntity.value = null
}

function isPerson(e: Person | Company | Contract): e is Person {
  return 'fullName' in e
}

function isCompany(e: Person | Company | Contract): e is Company {
  return 'registrationNumber' in e
}

function isContract(e: Person | Company | Contract): e is Contract {
  return 'awardedAt' in e
}

function sortIcon(key: string) {
  if (sortKey.value !== key) return null
  return sortDir.value === 'asc' ? ChevronUp : ChevronDown
}
</script>

<template>
  <div class="min-h-full p-6">
    <div class="max-w-7xl mx-auto">
      <!-- Page header -->
      <div class="mb-6">
        <h1 class="text-xl font-semibold text-text-primary">Entity Browser</h1>
        <p class="text-sm text-text-muted mt-1">
          Persons, companies, and contracts extracted from investigation data
        </p>
      </div>

      <!-- Tab bar + Search -->
      <div class="glass-card mb-4">
        <div class="flex items-center justify-between px-4 pt-3 border-b border-white/[0.06]">
          <!-- Tabs -->
          <div class="flex gap-1" role="tablist">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              type="button"
              role="tab"
              :aria-selected="store.activeTab === tab.key"
              :class="[
                'flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px cursor-pointer',
                'transition-all duration-200 ease-spring',
                'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50',
                store.activeTab === tab.key
                  ? 'text-accent-blue border-accent-blue'
                  : 'text-text-muted border-transparent hover:text-text-primary hover:border-white/20',
              ]"
              @click="store.setActiveTab(tab.key)"
            >
              <component :is="tab.icon" :size="15" />
              {{ tab.label }}
              <span
                :class="[
                  'text-xs px-1.5 py-0.5 rounded-full font-mono',
                  store.activeTab === tab.key
                    ? 'bg-accent-blue/20 text-accent-blue'
                    : 'bg-white/5 text-text-muted',
                ]"
              >
                {{ tabCounts[tab.key] }}
              </span>
            </button>
          </div>

          <!-- Search -->
          <div class="relative flex items-center pb-3">
            <Search :size="15" class="absolute left-3 text-text-muted pointer-events-none" />
            <input
              v-model="rawSearch"
              type="search"
              placeholder="Search..."
              class="bg-bg-elevated border border-white/[0.08] rounded-lg pl-9 pr-8 py-1.5
                     text-sm text-text-primary placeholder-text-muted w-56
                     focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50
                     focus:border-accent-blue/40 transition-colors duration-200"
            />
            <button
              v-if="rawSearch"
              type="button"
              class="absolute right-2.5 text-text-muted hover:text-text-primary cursor-pointer
                     transition-colors duration-150 focus:outline-none"
              aria-label="Clear search"
              @click="rawSearch = ''"
            >
              <X :size="13" />
            </button>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="store.loading" class="flex items-center justify-center py-16">
          <LoadingSpinner :size="32">Loading entities...</LoadingSpinner>
        </div>

        <!-- Persons table -->
        <div v-else-if="store.activeTab === 'person'" class="overflow-x-auto">
          <table class="w-full text-sm" aria-label="Persons">
            <thead>
              <tr class="border-b border-white/[0.06]">
                <th
                  v-for="col in [
                    { key: 'fullName', label: 'Name' },
                    { key: 'nationality', label: 'Nationality' },
                    { key: 'politicalRole', label: 'Political Role' },
                    { key: 'riskScore', label: 'Risk Score' },
                    { key: '', label: 'Actions' },
                  ]"
                  :key="col.key"
                  :class="[
                    'text-left px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider',
                    col.key ? 'cursor-pointer hover:text-text-primary select-none' : '',
                  ]"
                  @click="col.key && sortBy(col.key)"
                >
                  <span class="inline-flex items-center gap-1">
                    {{ col.label }}
                    <component
                      :is="sortIcon(col.key)"
                      v-if="sortIcon(col.key) && col.key"
                      :size="12"
                      class="text-accent-blue"
                    />
                  </span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="person in filteredPersons"
                :key="person.id"
                class="border-b border-white/[0.03] hover:bg-white/[0.02] cursor-pointer transition-colors duration-150"
                tabindex="0"
                @click="openSlideOver(person, 'person')"
                @keydown.enter="openSlideOver(person, 'person')"
              >
                <td class="px-4 py-3 font-medium text-text-primary">{{ person.fullName }}</td>
                <td class="px-4 py-3 text-text-muted font-mono text-xs">
                  {{ person.nationality ?? '—' }}
                </td>
                <td class="px-4 py-3 text-text-muted text-xs max-w-[200px] truncate">
                  {{ person.politicalRole ?? '—' }}
                </td>
                <td class="px-4 py-3">
                  <span
                    :class="[
                      'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-mono font-semibold border',
                      riskColor(person.riskScore).bg,
                      riskColor(person.riskScore).text,
                    ]"
                  >
                    {{ (person.riskScore * 10).toFixed(1) }}
                  </span>
                </td>
                <td class="px-4 py-3">
                  <button
                    type="button"
                    class="btn-ghost text-xs py-1 focus-ring"
                    @click.stop="openSlideOver(person, 'person')"
                  >
                    <ExternalLink :size="13" />
                    View
                  </button>
                </td>
              </tr>
              <tr v-if="filteredPersons.length === 0">
                <td colspan="5" class="px-4 py-10 text-center text-sm text-text-muted">
                  No persons match your search.
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Companies table -->
        <div v-else-if="store.activeTab === 'company'" class="overflow-x-auto">
          <table class="w-full text-sm" aria-label="Companies">
            <thead>
              <tr class="border-b border-white/[0.06]">
                <th
                  v-for="col in [
                    { key: 'name', label: 'Name' },
                    { key: 'jurisdiction', label: 'Jurisdiction' },
                    { key: 'legalForm', label: 'Legal Form' },
                    { key: 'active', label: 'Status' },
                    { key: '', label: 'Actions' },
                  ]"
                  :key="col.key"
                  :class="[
                    'text-left px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider',
                    col.key ? 'cursor-pointer hover:text-text-primary select-none' : '',
                  ]"
                  @click="col.key && sortBy(col.key)"
                >
                  <span class="inline-flex items-center gap-1">
                    {{ col.label }}
                    <component
                      :is="sortIcon(col.key)"
                      v-if="sortIcon(col.key) && col.key"
                      :size="12"
                      class="text-accent-blue"
                    />
                  </span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="company in filteredCompanies"
                :key="company.id"
                class="border-b border-white/[0.03] hover:bg-white/[0.02] cursor-pointer transition-colors duration-150"
                tabindex="0"
                @click="openSlideOver(company, 'company')"
                @keydown.enter="openSlideOver(company, 'company')"
              >
                <td class="px-4 py-3">
                  <span class="font-medium text-text-primary">{{ company.name }}</span>
                  <span
                    v-if="company.taxHaven"
                    class="ml-2 text-[10px] px-1.5 py-0.5 rounded bg-danger/10 text-danger border border-danger/20"
                  >
                    TAX HAVEN
                  </span>
                </td>
                <td class="px-4 py-3 font-mono text-xs text-text-data">{{ company.jurisdiction }}</td>
                <td class="px-4 py-3 text-text-muted text-xs">{{ company.legalForm ?? '—' }}</td>
                <td class="px-4 py-3">
                  <span class="inline-flex items-center gap-1.5 text-xs">
                    <span
                      :class="[
                        'w-1.5 h-1.5 rounded-full',
                        company.active ? 'bg-confidence-high' : 'bg-danger',
                      ]"
                    />
                    <span :class="company.active ? 'text-confidence-high' : 'text-danger'">
                      {{ company.active ? 'Active' : 'Inactive' }}
                    </span>
                  </span>
                </td>
                <td class="px-4 py-3">
                  <button
                    type="button"
                    class="btn-ghost text-xs py-1 focus-ring"
                    @click.stop="openSlideOver(company, 'company')"
                  >
                    <ExternalLink :size="13" />
                    View
                  </button>
                </td>
              </tr>
              <tr v-if="filteredCompanies.length === 0">
                <td colspan="5" class="px-4 py-10 text-center text-sm text-text-muted">
                  No companies match your search.
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Contracts table -->
        <div v-else-if="store.activeTab === 'contract'" class="overflow-x-auto">
          <table class="w-full text-sm" aria-label="Contracts">
            <thead>
              <tr class="border-b border-white/[0.06]">
                <th
                  v-for="col in [
                    { key: 'title', label: 'Title' },
                    { key: 'amount', label: 'Amount' },
                    { key: 'awardedAt', label: 'Awarded' },
                    { key: 'publicBodyName', label: 'Issuing Body' },
                    { key: '', label: 'Actions' },
                  ]"
                  :key="col.key"
                  :class="[
                    'text-left px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider',
                    col.key ? 'cursor-pointer hover:text-text-primary select-none' : '',
                  ]"
                  @click="col.key && sortBy(col.key)"
                >
                  <span class="inline-flex items-center gap-1">
                    {{ col.label }}
                    <component
                      :is="sortIcon(col.key)"
                      v-if="sortIcon(col.key) && col.key"
                      :size="12"
                      class="text-accent-blue"
                    />
                  </span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="contract in filteredContracts"
                :key="contract.id"
                class="border-b border-white/[0.03] hover:bg-white/[0.02] cursor-pointer transition-colors duration-150"
                tabindex="0"
                @click="openSlideOver(contract, 'contract')"
                @keydown.enter="openSlideOver(contract, 'contract')"
              >
                <td class="px-4 py-3 font-medium text-text-primary max-w-[260px]">
                  <span class="truncate block">{{ contract.title }}</span>
                </td>
                <td class="px-4 py-3 font-mono text-xs text-text-data">
                  {{ formatCurrency(contract.amount) }}
                </td>
                <td class="px-4 py-3 font-mono text-xs text-text-muted">
                  {{ formatDate(contract.awardedAt) }}
                </td>
                <td class="px-4 py-3 text-text-muted text-xs">{{ contract.publicBodyName }}</td>
                <td class="px-4 py-3">
                  <button
                    type="button"
                    class="btn-ghost text-xs py-1 focus-ring"
                    @click.stop="openSlideOver(contract, 'contract')"
                  >
                    <ExternalLink :size="13" />
                    View
                  </button>
                </td>
              </tr>
              <tr v-if="filteredContracts.length === 0">
                <td colspan="5" class="px-4 py-10 text-center text-sm text-text-muted">
                  No contracts match your search.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Slide-over panel -->
    <Teleport to="body">
      <Transition name="slide-right">
        <div
          v-if="slideOverEntity"
          class="fixed inset-0 z-50 flex justify-end"
          @click.self="closeSlideOver"
        >
          <!-- Backdrop -->
          <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="closeSlideOver" />

          <!-- Panel -->
          <aside
            class="relative w-full max-w-sm bg-bg-surface border-l border-white/[0.08]
                   overflow-y-auto z-10 flex flex-col"
            aria-label="Entity details"
          >
            <!-- Header -->
            <div class="flex items-start justify-between p-5 border-b border-white/[0.06] shrink-0">
              <div class="min-w-0">
                <div class="flex items-center gap-2 flex-wrap mb-1">
                  <h2 class="text-sm font-semibold text-text-primary truncate">
                    <template v-if="isPerson(slideOverEntity)">{{ slideOverEntity.fullName }}</template>
                    <template v-else-if="isCompany(slideOverEntity)">{{ slideOverEntity.name }}</template>
                    <template v-else-if="isContract(slideOverEntity)">{{ slideOverEntity.title }}</template>
                  </h2>
                  <EntityTag
                    :type="slideOverType"
                    :label="slideOverType.charAt(0).toUpperCase() + slideOverType.slice(1)"
                  />
                </div>
              </div>
              <button
                type="button"
                class="btn-ghost ml-2 shrink-0 focus-ring"
                aria-label="Close panel"
                @click="closeSlideOver"
              >
                <X :size="16" />
              </button>
            </div>

            <!-- Content -->
            <div class="flex-1 p-5 space-y-5">
              <!-- Person details -->
              <template v-if="isPerson(slideOverEntity)">
                <section>
                  <h3 class="data-label mb-3">Overview</h3>
                  <dl class="space-y-2">
                    <div v-if="slideOverEntity.birthDate" class="flex justify-between">
                      <dt class="text-xs text-text-muted">Date of Birth</dt>
                      <dd class="data-value">{{ formatDate(slideOverEntity.birthDate) }}</dd>
                    </div>
                    <div v-if="slideOverEntity.nationality" class="flex justify-between">
                      <dt class="text-xs text-text-muted">Nationality</dt>
                      <dd class="data-value">{{ slideOverEntity.nationality }}</dd>
                    </div>
                    <div v-if="slideOverEntity.politicalRole" class="flex justify-between gap-4">
                      <dt class="text-xs text-text-muted shrink-0">Political Role</dt>
                      <dd class="data-value text-right text-xs">{{ slideOverEntity.politicalRole }}</dd>
                    </div>
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Risk Score</dt>
                      <dd>
                        <span
                          :class="[
                            'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-mono font-semibold border',
                            riskColor(slideOverEntity.riskScore).bg,
                            riskColor(slideOverEntity.riskScore).text,
                          ]"
                        >
                          {{ (slideOverEntity.riskScore * 10).toFixed(1) }} / 10
                        </span>
                      </dd>
                    </div>
                  </dl>
                </section>
                <section v-if="slideOverEntity.relatedCompanies.length > 0">
                  <h3 class="data-label mb-3">Related Companies</h3>
                  <div class="flex flex-wrap gap-2">
                    <EntityTag
                      v-for="c in slideOverEntity.relatedCompanies"
                      :key="c"
                      type="company"
                      :label="c"
                    />
                  </div>
                </section>
                <section v-if="slideOverEntity.relatedContracts.length > 0">
                  <h3 class="data-label mb-3">Related Contracts</h3>
                  <div class="flex flex-wrap gap-2">
                    <EntityTag
                      v-for="k in slideOverEntity.relatedContracts"
                      :key="k"
                      type="contract"
                      :label="k"
                    />
                  </div>
                </section>
              </template>

              <!-- Company details -->
              <template v-else-if="isCompany(slideOverEntity)">
                <section>
                  <h3 class="data-label mb-3">Overview</h3>
                  <dl class="space-y-2">
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Registration No.</dt>
                      <dd class="data-value">{{ slideOverEntity.registrationNumber }}</dd>
                    </div>
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Jurisdiction</dt>
                      <dd class="data-value">{{ slideOverEntity.jurisdiction }}</dd>
                    </div>
                    <div v-if="slideOverEntity.legalForm" class="flex justify-between">
                      <dt class="text-xs text-text-muted">Legal Form</dt>
                      <dd class="data-value">{{ slideOverEntity.legalForm }}</dd>
                    </div>
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Status</dt>
                      <dd>
                        <span
                          :class="[
                            'text-xs font-medium',
                            slideOverEntity.active ? 'text-confidence-high' : 'text-danger',
                          ]"
                        >
                          {{ slideOverEntity.active ? 'Active' : 'Inactive' }}
                        </span>
                      </dd>
                    </div>
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Tax Haven</dt>
                      <dd>
                        <span :class="slideOverEntity.taxHaven ? 'text-danger text-xs font-medium' : 'text-confidence-high text-xs'">
                          {{ slideOverEntity.taxHaven ? 'Yes' : 'No' }}
                        </span>
                      </dd>
                    </div>
                  </dl>
                </section>
                <section v-if="slideOverEntity.ultimateBeneficialOwners.length > 0">
                  <h3 class="data-label mb-3">Ultimate Beneficial Owners</h3>
                  <div class="flex flex-wrap gap-2">
                    <EntityTag
                      v-for="ubo in slideOverEntity.ultimateBeneficialOwners"
                      :key="ubo"
                      type="person"
                      :label="ubo"
                    />
                  </div>
                </section>
              </template>

              <!-- Contract details -->
              <template v-else-if="isContract(slideOverEntity)">
                <section>
                  <h3 class="data-label mb-3">Overview</h3>
                  <dl class="space-y-2">
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Amount</dt>
                      <dd class="data-value text-accent-amber">{{ formatCurrency(slideOverEntity.amount) }}</dd>
                    </div>
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Awarded</dt>
                      <dd class="data-value">{{ formatDate(slideOverEntity.awardedAt) }}</dd>
                    </div>
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Issuing Body</dt>
                      <dd class="data-value">{{ slideOverEntity.publicBodyName }}</dd>
                    </div>
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Awarded To</dt>
                      <dd class="data-value">{{ slideOverEntity.awardedTo }}</dd>
                    </div>
                    <div class="flex justify-between">
                      <dt class="text-xs text-text-muted">Suspicion Score</dt>
                      <dd>
                        <span
                          :class="[
                            'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-mono font-semibold border',
                            riskColor(slideOverEntity.suspicionScore).bg,
                            riskColor(slideOverEntity.suspicionScore).text,
                          ]"
                        >
                          {{ (slideOverEntity.suspicionScore * 10).toFixed(1) }} / 10
                        </span>
                      </dd>
                    </div>
                  </dl>
                </section>
              </template>
            </div>
          </aside>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
