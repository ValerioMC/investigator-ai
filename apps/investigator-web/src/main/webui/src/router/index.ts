import { createRouter, createWebHistory } from 'vue-router'
import InvestigationView from '@/views/InvestigationView.vue'
import EntityBrowserView from '@/views/EntityBrowserView.vue'
import GraphView from '@/views/GraphView.vue'
import HistoryView from '@/views/HistoryView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/investigate',
    },
    {
      path: '/investigate',
      name: 'investigate',
      component: InvestigationView,
      meta: { title: 'Investigation' },
    },
    {
      path: '/entities',
      name: 'entities',
      component: EntityBrowserView,
      meta: { title: 'Entity Browser' },
    },
    {
      path: '/graph',
      name: 'graph',
      component: GraphView,
      meta: { title: 'Relationship Graph' },
    },
    {
      path: '/history',
      name: 'history',
      component: HistoryView,
      meta: { title: 'Investigation History' },
    },
  ],
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} — InvestigatorAI` : 'InvestigatorAI'
})

export default router
