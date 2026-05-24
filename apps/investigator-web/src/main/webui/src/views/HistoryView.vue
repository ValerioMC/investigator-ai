<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Plus, Trash2, RefreshCw, Download, ChevronDown, ChevronUp, AlertCircle, CheckCircle, Loader2 } from 'lucide-vue-next'
import { useHistoryStore } from '@/stores/history'
import type { InvestigationSession } from '@/types/api'
import ConfidenceBadge from '@/components/ConfidenceBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const store = useHistoryStore()

const expandedSession = ref<string | null>(null)
const deletingId = ref<string | null>(null)
const confirmDeleteId = ref<string | null>(null)

onMounted(() => store.fetchSessions())

function formatRelativeTime(dateStr: string): string {
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diffMs = now - then
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins} minute${diffMins === 1 ? '' : 's'} ago`
  if (diffHours < 24) return `${diffHours} hour${diffHours === 1 ? '' : 's'} ago`
  if (diffDays < 7) return `${diffDays} day${diffDays === 1 ? '' : 's'} ago`
  return new Intl.DateTimeFormat('it-IT', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(dateStr),
  )
}

const filteredSessions = computed(() => {
  const f = store.filters
  return store.sessions.filter((s) => {
    if (f.confidence && f.confidence !== '') {
      const dist = s.confidenceDistribution
      if (f.confidence === 'HIGH' && dist.high === 0) return false
      if (f.confidence === 'MEDIUM' && dist.medium === 0) return false
      if (f.confidence === 'LOW' && dist.low === 0) return false
    }
    if (f.workspace && f.workspace !== '' && s.workspace !== f.workspace) return false
    if (f.dateFrom && new Date(s.createdAt) < new Date(f.dateFrom)) return false
    if (f.dateTo && new Date(s.createdAt) > new Date(f.dateTo)) return false
    return true
  })
})

function toggleExpand(id: string) {
  expandedSession.value = expandedSession.value === id ? null : id
}

async function handleDelete(id: string) {
  if (confirmDeleteId.value !== id) {
    confirmDeleteId.value = id
    return
  }
  deletingId.value = id
  confirmDeleteId.value = null
  await store.deleteSession(id)
  deletingId.value = null
  if (expandedSession.value === id) expandedSession.value = null
}

async function handleExport(id: string) {
  const url = await store.exportSession(id)
  const a = document.createElement('a')
  a.href = url
  a.download = `investigation-${id}.json`
  a.click()
}

function confidenceBarWidth(session: InvestigationSession, level: 'high' | 'medium' | 'low' | 'unverified') {
  const total = session.findingCount
  if (total === 0) return '0%'
  return `${(session.confidenceDistribution[level] / total) * 100}%`
}

const workspaces = computed(() => [...new Set(store.sessions.map((s) => s.workspace))])

function statusIcon(status: InvestigationSession['status']) {
  switch (status) {
    case 'COMPLETED': return CheckCircle
    case 'RUNNING': return Loader2
    case 'FAILED': return AlertCircle
  }
}

function statusColor(status: InvestigationSession['status']) {
  switch (status) {
    case 'COMPLETED': return 'text-confidence-high'
    case 'RUNNING': return 'text-accent-blue animate-spin'
    case 'FAILED': return 'text-danger'
  }
}
</script>

<template>
  <div class="min-h-full p-6">
    <div class="max-w-7xl mx-auto">
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-xl font-semibold text-text-primary">Investigation History</h1>
          <p class="text-sm text-text-muted mt-1">
            {{ store.sessions.length }} session{{ store.sessions.length === 1 ? '' : 's' }} recorded
          </p>
        </div>
        <a href="/investigate" class="btn-amber focus-ring">
          <Plus :size="16" />
          New Investigation
        </a>
      </div>

      <!-- Filter bar -->
      <div class="glass-card p-4 mb-6 flex flex-wrap items-end gap-3">
        <div class="flex flex-col gap-1">
          <label class="data-label">From</label>
          <input
            type="date"
            :value="store.filters.dateFrom"
            class="bg-bg-elevated border border-white/[0.08] rounded-lg px-3 py-1.5 text-sm text-text-primary
                   focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50
                   focus:border-accent-blue/40 transition-colors duration-200 cursor-pointer"
            @change="store.updateFilters({ dateFrom: ($event.target as HTMLInputElement).value })"
          />
        </div>
        <div class="flex flex-col gap-1">
          <label class="data-label">To</label>
          <input
            type="date"
            :value="store.filters.dateTo"
            class="bg-bg-elevated border border-white/[0.08] rounded-lg px-3 py-1.5 text-sm text-text-primary
                   focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50
                   focus:border-accent-blue/40 transition-colors duration-200 cursor-pointer"
            @change="store.updateFilters({ dateTo: ($event.target as HTMLInputElement).value })"
          />
        </div>

        <div class="flex flex-col gap-1">
          <label class="data-label">Confidence</label>
          <select
            :value="store.filters.confidence"
            class="bg-bg-elevated border border-white/[0.08] rounded-lg px-3 py-1.5 text-sm text-text-primary
                   focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50
                   cursor-pointer appearance-none pr-8"
            @change="store.updateFilters({ confidence: ($event.target as HTMLSelectElement).value })"
          >
            <option value="">All confidence</option>
            <option value="HIGH">HIGH findings</option>
            <option value="MEDIUM">MEDIUM findings</option>
            <option value="LOW">LOW findings</option>
          </select>
        </div>

        <div class="flex flex-col gap-1">
          <label class="data-label">Workspace</label>
          <select
            :value="store.filters.workspace"
            class="bg-bg-elevated border border-white/[0.08] rounded-lg px-3 py-1.5 text-sm text-text-primary
                   focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50
                   cursor-pointer appearance-none pr-8"
            @change="store.updateFilters({ workspace: ($event.target as HTMLSelectElement).value })"
          >
            <option value="">All workspaces</option>
            <option v-for="ws in workspaces" :key="ws" :value="ws">{{ ws }}</option>
          </select>
        </div>

        <button
          type="button"
          class="btn-ghost focus-ring self-end"
          @click="store.resetFilters"
        >
          Reset
        </button>
      </div>

      <!-- Loading -->
      <div v-if="store.loading" class="flex items-center justify-center py-20">
        <LoadingSpinner :size="36">Loading sessions...</LoadingSpinner>
      </div>

      <!-- Empty state -->
      <EmptyState
        v-else-if="filteredSessions.length === 0"
        title="No investigations yet"
        description="Start your first investigation to uncover hidden relationships and conflicts of interest."
        :action="{ label: 'New Investigation', href: '/investigate' }"
      />

      <!-- Card grid -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="session in filteredSessions"
          :key="session.id"
          :class="[
            'glass-card overflow-hidden',
            'transition-transform duration-200 ease-spring',
            expandedSession !== session.id ? 'hover:scale-[1.01]' : '',
          ]"
        >
          <!-- Card header (always visible) -->
          <div class="p-4">
            <div class="flex items-start justify-between gap-2 mb-3">
              <div class="flex items-center gap-2">
                <!-- Status icon -->
                <component
                  :is="statusIcon(session.status)"
                  :size="14"
                  :class="['shrink-0', statusColor(session.status)]"
                />
                <span
                  :class="[
                    'text-[10px] font-semibold font-mono px-1.5 py-0.5 rounded border',
                    session.status === 'COMPLETED'
                      ? 'bg-confidence-high/10 text-confidence-high border-confidence-high/20'
                      : session.status === 'RUNNING'
                      ? 'bg-accent-blue/10 text-accent-blue border-accent-blue/20'
                      : 'bg-danger/10 text-danger border-danger/20',
                  ]"
                >
                  {{ session.status }}
                </span>
              </div>
              <span class="text-[11px] text-text-muted shrink-0">
                {{ formatRelativeTime(session.createdAt) }}
              </span>
            </div>

            <!-- Query text -->
            <p class="text-sm text-text-primary font-medium line-clamp-2 mb-3 leading-snug">
              {{ session.query }}
            </p>

            <!-- Workspace -->
            <p class="text-[11px] text-text-muted mb-3 truncate">
              <span class="opacity-50">Workspace:</span> {{ session.workspace }}
            </p>

            <!-- Stats row -->
            <div class="flex items-center gap-3 mb-3">
              <div class="flex items-center gap-1.5">
                <span class="text-[11px] text-text-muted">Findings:</span>
                <span class="text-xs font-mono font-semibold text-text-data">{{ session.findingCount }}</span>
              </div>
            </div>

            <!-- Confidence bar -->
            <div v-if="session.findingCount > 0" class="mb-1">
              <p class="data-label mb-1.5">Confidence distribution</p>
              <div class="flex h-1.5 rounded-full overflow-hidden gap-px">
                <div
                  class="bg-confidence-high rounded-full transition-all duration-500"
                  :style="`width: ${confidenceBarWidth(session, 'high')}`"
                />
                <div
                  class="bg-confidence-medium transition-all duration-500"
                  :style="`width: ${confidenceBarWidth(session, 'medium')}`"
                />
                <div
                  class="bg-confidence-low transition-all duration-500"
                  :style="`width: ${confidenceBarWidth(session, 'low')}`"
                />
                <div
                  class="bg-slate-600 rounded-full transition-all duration-500"
                  :style="`width: ${confidenceBarWidth(session, 'unverified')}`"
                />
              </div>
              <div class="flex justify-between mt-1">
                <span class="text-[10px] text-confidence-high">{{ session.confidenceDistribution.high }}H</span>
                <span class="text-[10px] text-confidence-medium">{{ session.confidenceDistribution.medium }}M</span>
                <span class="text-[10px] text-confidence-low">{{ session.confidenceDistribution.low }}L</span>
              </div>
            </div>

            <!-- Actions row -->
            <div class="flex items-center gap-1 mt-3 pt-3 border-t border-white/[0.05]">
              <button
                v-if="session.report"
                type="button"
                class="btn-ghost text-xs py-1.5 focus-ring flex-1 justify-center"
                @click="toggleExpand(session.id)"
              >
                <component :is="expandedSession === session.id ? ChevronUp : ChevronDown" :size="13" />
                {{ expandedSession === session.id ? 'Collapse' : 'View' }}
              </button>

              <button
                type="button"
                class="btn-ghost text-xs py-1.5 focus-ring"
                title="Re-run investigation"
                @click="() => { /* re-run: navigate to investigate with query prefilled */ }"
              >
                <RefreshCw :size="13" />
              </button>

              <button
                type="button"
                class="btn-ghost text-xs py-1.5 focus-ring"
                title="Export as JSON"
                @click="handleExport(session.id)"
              >
                <Download :size="13" />
              </button>

              <button
                type="button"
                :class="[
                  'btn-ghost text-xs py-1.5 focus-ring transition-colors duration-200',
                  confirmDeleteId === session.id ? 'text-danger hover:text-danger bg-danger/10' : '',
                ]"
                :title="confirmDeleteId === session.id ? 'Click again to confirm delete' : 'Delete session'"
                :disabled="deletingId === session.id"
                @click="handleDelete(session.id)"
                @blur="confirmDeleteId = null"
              >
                <Loader2 v-if="deletingId === session.id" :size="13" class="animate-spin" />
                <Trash2 v-else :size="13" />
              </button>
            </div>
          </div>

          <!-- Expanded results (inline) -->
          <Transition name="slide-down">
            <div
              v-if="expandedSession === session.id && session.report"
              class="border-t border-white/[0.06] p-4 bg-bg-elevated/60 space-y-3"
            >
              <!-- Summary -->
              <div>
                <p class="data-label mb-1.5">Summary</p>
                <p class="text-xs text-text-primary leading-relaxed">{{ session.report.summary }}</p>
              </div>

              <!-- Findings preview -->
              <div>
                <p class="data-label mb-2">Findings</p>
                <div class="space-y-2">
                  <div
                    v-for="(finding, fi) in session.report.findings"
                    :key="fi"
                    :class="[
                      'p-2.5 rounded-lg border border-white/[0.04] bg-bg-base/40',
                      'border-l-2',
                      finding.confidence === 'HIGH' ? 'border-l-confidence-high' :
                      finding.confidence === 'MEDIUM' ? 'border-l-confidence-medium' :
                      finding.confidence === 'LOW' ? 'border-l-confidence-low' :
                      'border-l-slate-600',
                    ]"
                  >
                    <p class="text-xs text-text-primary leading-snug mb-1.5">{{ finding.claim }}</p>
                    <div class="flex items-center gap-2">
                      <ConfidenceBadge :confidence="finding.confidence" />
                      <span class="text-[10px] text-text-muted font-mono">{{ finding.agentSource }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Disclaimer -->
              <p class="text-[10px] text-text-muted leading-relaxed italic">
                {{ session.report.disclaimer }}
              </p>
            </div>
          </Transition>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
input[type='date']::-webkit-calendar-picker-indicator {
  filter: invert(0.5);
  cursor: pointer;
}

select {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 0.75rem center;
  padding-right: 2rem;
}
</style>
