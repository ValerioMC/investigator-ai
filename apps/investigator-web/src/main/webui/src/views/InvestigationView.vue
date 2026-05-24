<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Search,
  Shield,
  AlertTriangle,
  X,
  ChevronDown,
  ChevronUp,
  Plus,
} from 'lucide-vue-next'
import { useInvestigationStore } from '@/stores/investigation'
import ConfidenceBadge from '@/components/ConfidenceBadge.vue'
import EntityTag from '@/components/EntityTag.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import type { ConfidenceLevel } from '@/types/api'

const store = useInvestigationStore()

const entityInput = ref('')
const expandedFindings = ref<Set<number>>(new Set())
const dismissedDisclaimer = ref(false)
const followUpChecked = ref<boolean[]>([])

function handleEntityKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    if (entityInput.value.trim()) {
      store.addFocusEntity(entityInput.value)
      entityInput.value = ''
    }
  }
}

function toggleFinding(idx: number) {
  if (expandedFindings.value.has(idx)) {
    expandedFindings.value.delete(idx)
  } else {
    expandedFindings.value.add(idx)
  }
}

function initFollowUps() {
  if (store.result) {
    followUpChecked.value = new Array(store.result.recommendedFollowUps.length).fill(false)
  }
}

async function handleSubmit() {
  dismissedDisclaimer.value = false
  expandedFindings.value.clear()
  await store.runInvestigation()
  initFollowUps()
}

const depthColor = computed(() => {
  const d = store.depth
  if (d <= 3) return '#10b981'
  if (d <= 6) return '#f59e0b'
  return '#ef4444'
})

const overallConfidence = computed((): ConfidenceLevel => {
  if (!store.result) return 'UNVERIFIED'
  const findings = store.result.findings
  if (findings.length === 0) return 'UNVERIFIED'
  const high = findings.filter((f) => f.confidence === 'HIGH').length
  const medium = findings.filter((f) => f.confidence === 'MEDIUM').length
  if (high / findings.length > 0.5) return 'HIGH'
  if ((high + medium) / findings.length > 0.5) return 'MEDIUM'
  return 'LOW'
})
</script>

<template>
  <div class="min-h-full p-6">
    <div class="max-w-7xl mx-auto">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        <!-- Query form -->
        <section aria-label="Investigation query">
          <div class="glass-card p-6">
            <!-- Header -->
            <div class="flex items-center gap-3 mb-6">
              <div class="relative">
                <div
                  :class="[
                    'w-2 h-2 rounded-full',
                    store.loading ? 'bg-accent-amber' : 'bg-confidence-high',
                  ]"
                />
                <div
                  v-if="store.loading"
                  class="absolute inset-0 w-2 h-2 rounded-full bg-accent-amber animate-ping"
                />
              </div>
              <h2 class="text-base font-semibold text-text-primary">New Investigation</h2>
            </div>

            <form @submit.prevent="handleSubmit" class="space-y-5">
              <!-- Query textarea -->
              <div>
                <label for="query" class="data-label block mb-2">Investigation Objective</label>
                <textarea
                  id="query"
                  v-model="store.query"
                  rows="5"
                  class="w-full bg-bg-elevated border border-white/[0.08] rounded-lg px-4 py-3
                         text-sm text-text-primary placeholder-text-muted resize-none
                         focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50
                         focus:border-accent-blue/40 transition-colors duration-200"
                  placeholder="Describe your investigation objective...&#10;&#10;Example: Who really controls Costruzioni Ferretti Srl and are there conflicts of interest in their public contracts?"
                  :disabled="store.loading"
                />
              </div>

              <!-- Depth slider -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <label for="depth" class="data-label">Investigation Depth</label>
                  <span
                    class="text-xs font-semibold font-mono px-2 py-0.5 rounded-full"
                    :style="`color: ${depthColor}; background: ${depthColor}20; border: 1px solid ${depthColor}40`"
                  >
                    {{ store.depth }}
                  </span>
                </div>
                <div class="relative">
                  <input
                    id="depth"
                    v-model.number="store.depth"
                    type="range"
                    min="1"
                    max="10"
                    step="1"
                    class="w-full h-1.5 rounded-full appearance-none cursor-pointer bg-bg-elevated
                           focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50"
                    :style="`background: linear-gradient(to right, ${depthColor} 0%, ${depthColor} ${(store.depth - 1) / 9 * 100}%, rgba(255,255,255,0.1) ${(store.depth - 1) / 9 * 100}%, rgba(255,255,255,0.1) 100%)`"
                  />
                </div>
                <div class="flex justify-between mt-1">
                  <span class="text-[11px] text-text-muted">Shallow</span>
                  <span class="text-[11px] text-text-muted">Deep</span>
                </div>
              </div>

              <!-- Focus entities -->
              <div>
                <label for="entity-input" class="data-label block mb-2">Focus Entities (optional)</label>
                <div class="flex gap-2 mb-2">
                  <input
                    id="entity-input"
                    v-model="entityInput"
                    type="text"
                    placeholder="Entity name — press Enter to add"
                    class="flex-1 bg-bg-elevated border border-white/[0.08] rounded-lg px-3 py-2
                           text-sm text-text-primary placeholder-text-muted
                           focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50
                           focus:border-accent-blue/40 transition-colors duration-200"
                    @keydown="handleEntityKeydown"
                  />
                  <button
                    type="button"
                    class="btn-ghost focus-ring"
                    :disabled="!entityInput.trim()"
                    aria-label="Add entity"
                    @click="store.addFocusEntity(entityInput); entityInput = ''"
                  >
                    <Plus :size="16" />
                  </button>
                </div>
                <div v-if="store.focusEntities.length > 0" class="flex flex-wrap gap-2">
                  <EntityTag
                    v-for="entity in store.focusEntities"
                    :key="entity"
                    type="person"
                    :label="entity"
                    :dismissible="true"
                    @dismiss="store.removeFocusEntity(entity)"
                  />
                </div>
              </div>

              <!-- Submit -->
              <div class="pt-1">
                <button
                  type="submit"
                  class="w-full btn-amber focus-ring py-3 text-sm font-semibold"
                  :disabled="store.loading || !store.query.trim()"
                >
                  <LoadingSpinner v-if="store.loading" :size="16" />
                  <Search v-else :size="16" />
                  {{ store.loading ? 'Investigating...' : 'Run Investigation' }}
                </button>
                <p class="text-center text-[11px] text-text-muted mt-2.5">
                  Powered by qwen3.6:35b via Ollama
                </p>
              </div>
            </form>
          </div>
        </section>

        <!-- Results panel -->
        <section aria-label="Investigation results" aria-live="polite">
          <!-- Error state -->
          <div v-if="store.error && !store.loading" class="glass-card p-5 border border-red-500/20">
            <div class="flex items-start gap-3">
              <AlertTriangle :size="18" class="text-red-400 shrink-0 mt-0.5" />
              <div>
                <p class="text-sm font-medium text-red-400 mb-1">Investigation failed</p>
                <p class="text-xs text-text-muted font-mono">{{ store.error }}</p>
              </div>
            </div>
          </div>

          <!-- Loading state -->
          <div v-if="store.loading" class="glass-card p-10 flex flex-col items-center justify-center gap-4">
            <LoadingSpinner :size="48">
              Analyzing relationships...
            </LoadingSpinner>
            <p class="text-xs text-text-muted text-center max-w-xs leading-relaxed">
              Running agents: CorporateAgent, PersonProfileAgent, FinancialFlowAgent, SourceVerificationAgent
            </p>
          </div>

          <!-- Results -->
          <Transition name="slide-down">
            <div v-if="store.result && !store.loading" class="space-y-4">
              <!-- Executive summary -->
              <div class="glass-card p-5">
                <div class="flex items-start gap-3 mb-3">
                  <div class="p-2 rounded-lg bg-accent-blue/10 shrink-0">
                    <Shield :size="18" class="text-accent-blue" />
                  </div>
                  <div class="min-w-0">
                    <div class="flex items-center gap-2 flex-wrap mb-1">
                      <h3 class="text-sm font-semibold text-text-primary">Investigation Summary</h3>
                      <ConfidenceBadge :confidence="overallConfidence" />
                    </div>
                    <p class="text-xs text-text-muted font-mono truncate">{{ store.result.query }}</p>
                  </div>
                </div>
                <p class="text-sm text-text-primary leading-relaxed">{{ store.result.summary }}</p>
              </div>

              <!-- Findings -->
              <div class="glass-card p-5">
                <h3 class="text-sm font-semibold text-text-primary mb-4">
                  Findings
                  <span class="ml-2 text-xs text-text-muted font-normal">
                    ({{ store.result.findings.length }})
                  </span>
                </h3>
                <div class="space-y-3">
                  <article
                    v-for="(finding, idx) in store.result.findings"
                    :key="idx"
                    :class="[
                      'rounded-lg p-4 border border-white/[0.06] bg-bg-elevated',
                      'border-l-[3px]',
                      finding.confidence === 'HIGH' ? 'border-l-confidence-high' :
                      finding.confidence === 'MEDIUM' ? 'border-l-confidence-medium' :
                      finding.confidence === 'LOW' ? 'border-l-confidence-low' :
                      'border-l-slate-600',
                    ]"
                  >
                    <p class="text-sm text-text-primary font-medium leading-snug mb-3">
                      {{ finding.claim }}
                    </p>

                    <div class="flex items-center gap-2 flex-wrap mb-3">
                      <ConfidenceBadge :confidence="finding.confidence" />
                      <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-slate-800 text-slate-400 border border-slate-700">
                        {{ finding.agentSource }}
                      </span>
                    </div>

                    <!-- Evidence chips (collapsed) -->
                    <div v-if="!expandedFindings.has(idx)" class="flex flex-wrap gap-1.5">
                      <span
                        v-for="(ev, ei) in finding.evidence.slice(0, 2)"
                        :key="ei"
                        class="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono
                               bg-bg-base border border-white/[0.06] text-text-data truncate max-w-[200px]"
                        :title="ev"
                      >
                        {{ ev }}
                      </span>
                      <button
                        v-if="finding.evidence.length > 0"
                        type="button"
                        class="inline-flex items-center gap-1 text-[11px] text-accent-blue
                               hover:text-blue-400 cursor-pointer transition-colors duration-150
                               focus:outline-none focus-visible:ring-1 focus-visible:ring-accent-blue/50 rounded"
                        @click="toggleFinding(idx)"
                      >
                        <ChevronDown :size="12" />
                        {{ finding.evidence.length }} evidence
                      </button>
                    </div>

                    <!-- Evidence expanded -->
                    <div v-else>
                      <div class="flex flex-wrap gap-1.5 mb-2">
                        <span
                          v-for="(ev, ei) in finding.evidence"
                          :key="ei"
                          class="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono
                                 bg-bg-base border border-white/[0.06] text-text-data break-all"
                        >
                          {{ ev }}
                        </span>
                      </div>
                      <button
                        type="button"
                        class="inline-flex items-center gap-1 text-[11px] text-accent-blue
                               hover:text-blue-400 cursor-pointer transition-colors duration-150
                               focus:outline-none focus-visible:ring-1 focus-visible:ring-accent-blue/50 rounded"
                        @click="toggleFinding(idx)"
                      >
                        <ChevronUp :size="12" />
                        Collapse
                      </button>
                    </div>
                  </article>
                </div>
              </div>

              <!-- Entity map -->
              <div class="glass-card p-5">
                <h3 class="text-sm font-semibold text-text-primary mb-4">Entity Map</h3>
                <div class="space-y-3">
                  <div v-if="store.result.entityMap.persons.length > 0">
                    <p class="data-label mb-2">Persons</p>
                    <div class="flex flex-wrap gap-2">
                      <EntityTag
                        v-for="p in store.result.entityMap.persons"
                        :key="p"
                        type="person"
                        :label="p"
                      />
                    </div>
                  </div>
                  <div v-if="store.result.entityMap.companies.length > 0">
                    <p class="data-label mb-2">Companies</p>
                    <div class="flex flex-wrap gap-2">
                      <EntityTag
                        v-for="c in store.result.entityMap.companies"
                        :key="c"
                        type="company"
                        :label="c"
                      />
                    </div>
                  </div>
                  <div v-if="store.result.entityMap.contracts.length > 0">
                    <p class="data-label mb-2">Contracts</p>
                    <div class="flex flex-wrap gap-2">
                      <EntityTag
                        v-for="k in store.result.entityMap.contracts"
                        :key="k"
                        type="contract"
                        :label="k"
                      />
                    </div>
                  </div>
                </div>
              </div>

              <!-- Follow-ups -->
              <div class="glass-card p-5">
                <h3 class="text-sm font-semibold text-text-primary mb-4">Recommended Follow-ups</h3>
                <ol class="space-y-2">
                  <li
                    v-for="(followUp, idx) in store.result.recommendedFollowUps"
                    :key="idx"
                    class="flex items-start gap-3"
                  >
                    <label class="flex items-start gap-3 cursor-pointer group">
                      <input
                        type="checkbox"
                        v-model="followUpChecked[idx]"
                        class="mt-0.5 w-4 h-4 rounded border-white/20 bg-bg-elevated
                               text-accent-blue cursor-pointer
                               focus:ring-2 focus:ring-accent-blue/50 focus:ring-offset-0"
                      />
                      <span
                        :class="[
                          'text-sm leading-snug transition-colors duration-150',
                          followUpChecked[idx]
                            ? 'text-text-muted line-through'
                            : 'text-text-primary group-hover:text-white',
                        ]"
                      >
                        {{ followUp }}
                      </span>
                    </label>
                  </li>
                </ol>
              </div>

              <!-- Disclaimer -->
              <Transition name="slide-down">
                <div
                  v-if="!dismissedDisclaimer"
                  class="flex items-start gap-3 p-4 rounded-lg bg-accent-amber/10 border border-accent-amber/20"
                >
                  <AlertTriangle :size="16" class="text-accent-amber shrink-0 mt-0.5" />
                  <p class="text-xs text-accent-amber/90 leading-relaxed flex-1">
                    {{ store.result.disclaimer }}
                  </p>
                  <button
                    type="button"
                    class="shrink-0 text-accent-amber/60 hover:text-accent-amber cursor-pointer
                           transition-colors duration-150 focus:outline-none focus-visible:ring-1
                           focus-visible:ring-accent-amber/50 rounded"
                    aria-label="Dismiss disclaimer"
                    @click="dismissedDisclaimer = true"
                  >
                    <X :size="14" />
                  </button>
                </div>
              </Transition>
            </div>
          </Transition>

          <!-- Empty state when no result -->
          <div v-if="!store.result && !store.loading" class="glass-card">
            <div class="flex flex-col items-center justify-center py-16 px-6 text-center">
              <div class="w-16 h-16 rounded-full bg-accent-blue/10 flex items-center justify-center mb-4">
                <Search :size="24" class="text-accent-blue/60" />
              </div>
              <h3 class="text-sm font-semibold text-text-primary mb-2">Ready to investigate</h3>
              <p class="text-sm text-text-muted max-w-sm leading-relaxed">
                Enter your investigation objective in the form and run the analysis. Results appear here.
              </p>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
input[type='range']::-webkit-slider-thumb {
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #f59e0b;
  cursor: pointer;
  border: 2px solid #0a0f1e;
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.4);
}

input[type='range']::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #f59e0b;
  cursor: pointer;
  border: 2px solid #0a0f1e;
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.4);
}
</style>
