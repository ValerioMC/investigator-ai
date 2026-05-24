import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'
import type { GraphNode, GraphEdge } from '@/types/api'
import { useMockData } from '@/composables/useMockData'

export type GraphLayout = 'force' | 'hierarchical' | 'circular'

export const useGraphStore = defineStore('graph', () => {
  const nodes = ref<GraphNode[]>([])
  const edges = ref<GraphEdge[]>([])
  const selectedNode = ref<GraphNode | null>(null)
  const layout = ref<GraphLayout>('force')
  const loading = ref(false)

  async function fetchGraphData() {
    loading.value = true
    try {
      const resp = await axios.get<{ nodes: GraphNode[]; edges: GraphEdge[] }>('/api/web/v1/graph')
      nodes.value = resp.data.nodes
      edges.value = resp.data.edges
    } catch {
      const { mockGraphData } = useMockData()
      nodes.value = mockGraphData.nodes
      edges.value = mockGraphData.edges
    } finally {
      loading.value = false
    }
  }

  function selectNode(node: GraphNode | null) {
    selectedNode.value = node
  }

  function setLayout(newLayout: GraphLayout) {
    layout.value = newLayout
  }

  return {
    nodes,
    edges,
    selectedNode,
    layout,
    loading,
    fetchGraphData,
    selectNode,
    setLayout,
  }
})
