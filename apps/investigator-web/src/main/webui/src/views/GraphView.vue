<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { ZoomIn, ZoomOut, Maximize2, RotateCcw, Search, X } from 'lucide-vue-next'
import cytoscape from 'cytoscape'
import { useGraphStore, type GraphLayout } from '@/stores/graph'
import type { GraphNode } from '@/types/api'
import EntityTag from '@/components/EntityTag.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const store = useGraphStore()

const containerRef = ref<HTMLElement | null>(null)
const nodeSearchQuery = ref('')
let cy: cytoscape.Core | null = null

onMounted(async () => {
  await store.fetchGraphData()
  initCytoscape()
})

onBeforeUnmount(() => {
  cy?.destroy()
})

watch(() => store.layout, () => applyLayout())

function nodeColor(node: GraphNode): string {
  if (node.type === 'Person') {
    const risk = (node.riskScore ?? 0)
    if (risk >= 0.7) return '#ef4444'
    if (risk >= 0.4) return '#f59e0b'
    return '#10b981'
  }
  if (node.type === 'Company') {
    const j = node.jurisdiction ?? ''
    if (j === 'IT') return '#3b82f6'
    if (j === 'LU') return '#f59e0b'
    if (j === 'VG') return '#ef4444'
    return '#6366f1'
  }
  if (node.type === 'Contract') return '#6366f1'
  if (node.type === 'PublicBody') return '#0891b2'
  return '#64748b'
}


function layoutOptions(layout: GraphLayout): cytoscape.LayoutOptions {
  switch (layout) {
    case 'hierarchical':
      return { name: 'breadthfirst', directed: true, spacingFactor: 1.4 } as cytoscape.LayoutOptions
    case 'circular':
      return { name: 'circle', spacingFactor: 1.2 } as cytoscape.LayoutOptions
    default:
      return {
        name: 'cose',
        animate: true,
        animationDuration: 500,
        nodeRepulsion: () => 4096,
        idealEdgeLength: () => 120,
        nodeOverlap: 20,
        fit: true,
        padding: 40,
      } as cytoscape.LayoutOptions
  }
}

function initCytoscape() {
  if (!containerRef.value) return

  const elements = [
    ...store.nodes.map((n) => ({
      data: {
        id: n.id,
        label: n.label,
        type: n.type,
        color: nodeColor(n),
        ...n.properties,
      },
    })),
    ...store.edges.map((e) => ({
      data: {
        id: e.id,
        source: e.source,
        target: e.target,
        label: e.relationshipType,
      },
    })),
  ]

  cy = cytoscape({
    container: containerRef.value,
    elements,
    style: [
      {
        selector: 'node',
        style: {
          'background-color': 'data(color)',
          'background-opacity': 0.9,
          label: 'data(label)',
          'font-size': 11,
          'font-family': 'Inter, sans-serif',
          color: '#e2e8f0',
          'text-valign': 'bottom',
          'text-halign': 'center',
          'text-margin-y': 6,
          'text-outline-width': 2,
          'text-outline-color': '#080d1a',
          'border-width': 1.5,
          'border-color': 'rgba(255,255,255,0.15)',
          width: 40,
          height: 40,
          // shape set via element data mapper below
        },
      },
      {
        selector: 'node:selected',
        style: {
          'border-width': 2.5,
          'border-color': '#f59e0b',
          'background-opacity': 1,
          width: 50,
          height: 50,
        },
      },
      {
        selector: 'node.highlighted',
        style: {
          'border-width': 2,
          'border-color': '#3b82f6',
          'background-opacity': 1,
        },
      },
      {
        selector: 'node[type = "Person"]',
        style: { shape: 'ellipse' },
      },
      {
        selector: 'node[type = "Company"]',
        style: { shape: 'roundrectangle' },
      },
      {
        selector: 'node[type = "Contract"]',
        style: { shape: 'diamond' },
      },
      {
        selector: 'node[type = "PublicBody"]',
        style: { shape: 'pentagon' },
      },
      {
        selector: 'node.dimmed',
        style: {
          opacity: 0.25,
        },
      },
      {
        selector: 'edge',
        style: {
          width: 1.2,
          'line-color': 'rgba(100,116,139,0.5)',
          'target-arrow-color': 'rgba(100,116,139,0.5)',
          'target-arrow-shape': 'triangle',
          'curve-style': 'bezier',
          label: 'data(label)',
          'font-size': 9,
          'font-family': 'JetBrains Mono, monospace',
          color: 'rgba(100,116,139,0.7)',
          'text-background-color': '#080d1a',
          'text-background-opacity': 0.8,
          'text-background-padding': '3px',
        },
      },
      {
        selector: 'edge:selected',
        style: {
          'line-color': '#3b82f6',
          'target-arrow-color': '#3b82f6',
          width: 2,
        },
      },
    ],
    layout: layoutOptions(store.layout),
    userZoomingEnabled: true,
    userPanningEnabled: true,
    boxSelectionEnabled: false,
  })

  cy.on('tap', 'node', (evt) => {
    const nodeId = evt.target.id() as string
    const graphNode = store.nodes.find((n) => n.id === nodeId) ?? null
    store.selectNode(graphNode)
  })

  cy.on('tap', (evt) => {
    if (evt.target === cy) {
      store.selectNode(null)
    }
  })
}

function applyLayout() {
  cy?.layout(layoutOptions(store.layout)).run()
}

function zoomIn() {
  cy?.zoom({ level: cy.zoom() * 1.2, renderedPosition: { x: cy.width() / 2, y: cy.height() / 2 } })
}

function zoomOut() {
  cy?.zoom({ level: cy.zoom() * 0.8, renderedPosition: { x: cy.width() / 2, y: cy.height() / 2 } })
}

function fitGraph() {
  cy?.fit(undefined, 40)
}

function resetGraph() {
  cy?.fit(undefined, 40)
  store.selectNode(null)
  clearNodeSearch()
}

function searchNode() {
  if (!cy || !nodeSearchQuery.value.trim()) {
    cy?.elements().removeClass('dimmed highlighted')
    return
  }
  const q = nodeSearchQuery.value.toLowerCase()
  cy.elements().addClass('dimmed')
  cy.elements('node').forEach((n) => {
    const label = String(n.data('label') ?? '').toLowerCase()
    if (label.includes(q)) {
      n.removeClass('dimmed').addClass('highlighted')
      n.connectedEdges().removeClass('dimmed')
    }
  })
}

function clearNodeSearch() {
  nodeSearchQuery.value = ''
  cy?.elements().removeClass('dimmed highlighted')
}

const layouts: { key: GraphLayout; label: string }[] = [
  { key: 'force', label: 'Force' },
  { key: 'hierarchical', label: 'Hierarchical' },
  { key: 'circular', label: 'Circular' },
]

const selectedNodeEntityType = computed(() => {
  if (!store.selectedNode) return 'person' as const
  const t = store.selectedNode.type
  if (t === 'Company') return 'company' as const
  if (t === 'Contract') return 'contract' as const
  return 'person' as const
})
</script>

<template>
  <div class="relative flex h-[calc(100vh-4rem)] overflow-hidden">
    <!-- Loading overlay -->
    <div
      v-if="store.loading"
      class="absolute inset-0 z-10 flex items-center justify-center bg-bg-base/60 backdrop-blur-sm"
    >
      <LoadingSpinner :size="40">Loading graph...</LoadingSpinner>
    </div>

    <!-- Cytoscape container -->
    <div
      ref="containerRef"
      class="flex-1 h-full"
      style="background-color: #080d1a"
      aria-label="Relationship graph"
    />

    <!-- Toolbar (floating) -->
    <div
      class="absolute top-4 left-1/2 -translate-x-1/2 flex items-center gap-1 px-3 py-2 rounded-full
             glass-card z-10 glow-blue"
    >
      <!-- Zoom controls -->
      <button
        type="button"
        class="btn-ghost w-8 h-8 p-0 justify-center focus-ring rounded-full"
        aria-label="Zoom in"
        @click="zoomIn"
      >
        <ZoomIn :size="15" />
      </button>
      <button
        type="button"
        class="btn-ghost w-8 h-8 p-0 justify-center focus-ring rounded-full"
        aria-label="Zoom out"
        @click="zoomOut"
      >
        <ZoomOut :size="15" />
      </button>
      <button
        type="button"
        class="btn-ghost w-8 h-8 p-0 justify-center focus-ring rounded-full"
        aria-label="Fit to screen"
        @click="fitGraph"
      >
        <Maximize2 :size="15" />
      </button>
      <button
        type="button"
        class="btn-ghost w-8 h-8 p-0 justify-center focus-ring rounded-full"
        aria-label="Reset view"
        @click="resetGraph"
      >
        <RotateCcw :size="15" />
      </button>

      <div class="w-px h-5 bg-white/10 mx-1" />

      <!-- Layout selector -->
      <div class="flex items-center gap-0.5">
        <button
          v-for="l in layouts"
          :key="l.key"
          type="button"
          :class="[
            'px-2.5 py-1 rounded-full text-xs font-medium cursor-pointer',
            'transition-all duration-200 ease-spring focus-ring',
            store.layout === l.key
              ? 'bg-accent-blue/20 text-accent-blue'
              : 'text-text-muted hover:text-text-primary',
          ]"
          @click="store.setLayout(l.key)"
        >
          {{ l.label }}
        </button>
      </div>

      <div class="w-px h-5 bg-white/10 mx-1" />

      <!-- Node search -->
      <div class="relative flex items-center">
        <Search :size="13" class="absolute left-2 text-text-muted pointer-events-none" />
        <input
          v-model="nodeSearchQuery"
          type="text"
          placeholder="Search node..."
          class="bg-white/5 border border-white/[0.08] rounded-full pl-7 pr-7 py-1
                 text-xs text-text-primary placeholder-text-muted w-32
                 focus:outline-none focus-visible:ring-1 focus-visible:ring-accent-blue/50
                 transition-colors duration-200"
          @input="searchNode"
        />
        <button
          v-if="nodeSearchQuery"
          type="button"
          class="absolute right-2 text-text-muted hover:text-text-primary cursor-pointer"
          aria-label="Clear search"
          @click="clearNodeSearch"
        >
          <X :size="11" />
        </button>
      </div>
    </div>

    <!-- Node detail panel -->
    <Transition name="slide-right">
      <aside
        v-if="store.selectedNode"
        class="absolute top-4 right-4 w-72 glass-card p-4 z-10"
        aria-label="Node details"
      >
        <div class="flex items-start justify-between mb-3">
          <div class="min-w-0">
            <h3 class="text-sm font-semibold text-text-primary truncate">
              {{ store.selectedNode.label }}
            </h3>
            <EntityTag
              :type="selectedNodeEntityType"
              :label="store.selectedNode.type"
              class="mt-1"
            />
          </div>
          <button
            type="button"
            class="btn-ghost w-7 h-7 p-0 justify-center shrink-0 focus-ring"
            aria-label="Close node details"
            @click="store.selectNode(null)"
          >
            <X :size="14" />
          </button>
        </div>

        <!-- Properties -->
        <div class="space-y-1.5 mb-4">
          <p class="data-label mb-2">Properties</p>
          <dl class="space-y-1.5">
            <div
              v-for="(value, key) in store.selectedNode.properties"
              :key="String(key)"
              class="flex justify-between gap-3 py-1 border-b border-white/[0.04]"
            >
              <dt class="text-[11px] text-text-muted capitalize shrink-0">{{ String(key) }}</dt>
              <dd class="text-[11px] font-mono text-text-data text-right truncate">{{ String(value) }}</dd>
            </div>
          </dl>
        </div>

        <!-- Connected count -->
        <div class="flex items-center justify-between mb-4 py-2 px-3 rounded-lg bg-bg-elevated">
          <span class="text-xs text-text-muted">Connections</span>
          <span class="text-xs font-mono text-text-data">
            {{ store.edges.filter(e => e.source === store.selectedNode!.id || e.target === store.selectedNode!.id).length }}
          </span>
        </div>

        <a
          href="/entities"
          class="btn-primary w-full text-xs justify-center focus-ring"
        >
          View Profile
        </a>
      </aside>
    </Transition>

    <!-- Legend -->
    <div class="absolute bottom-4 left-4 glass-card p-3 z-10">
      <p class="data-label mb-2">Node Types</p>
      <div class="space-y-1.5">
        <div v-for="item in [
          { color: '#10b981', shape: '●', label: 'Person (low risk)' },
          { color: '#f59e0b', shape: '●', label: 'Person (medium risk)' },
          { color: '#ef4444', shape: '●', label: 'Person (high risk)' },
          { color: '#3b82f6', shape: '■', label: 'Company (IT)' },
          { color: '#f59e0b', shape: '■', label: 'Company (LU/tax haven)' },
          { color: '#6366f1', shape: '◆', label: 'Contract' },
          { color: '#0891b2', shape: '⬠', label: 'Public Body' },
        ]" :key="item.label" class="flex items-center gap-2">
          <span :style="`color: ${item.color}`" class="text-xs font-bold w-3">{{ item.shape }}</span>
          <span class="text-[11px] text-text-muted">{{ item.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
