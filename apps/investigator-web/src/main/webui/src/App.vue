<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Search,
  Database,
  Network,
  Clock,
  ChevronLeft,
  ChevronRight,
  Bell,
  Settings,
  ChevronDown,
} from 'lucide-vue-next'

const route = useRoute()
const collapsed = ref(false)

const navItems = [
  { name: 'Investigation', path: '/investigate', icon: Search },
  { name: 'Entities', path: '/entities', icon: Database },
  { name: 'Graph', path: '/graph', icon: Network },
  { name: 'History', path: '/history', icon: Clock },
]

const workspaces = ['Inchiesta Appalti Brescia', 'Corruzione Municipale', 'Evasione Offshore']
const activeWorkspace = ref(workspaces[0])
const workspaceOpen = ref(false)
const notificationCount = ref(3)

const breadcrumb = computed(() => {
  const item = navItems.find((n) => route.path.startsWith(n.path))
  return item?.name ?? 'InvestigatorAI'
})
</script>

<template>
  <div class="flex h-screen bg-bg-base overflow-hidden">
    <!-- Sidebar -->
    <aside
      :class="[
        'flex flex-col shrink-0 bg-bg-surface border-r border-white/[0.06]',
        'transition-all duration-300 ease-spring z-20',
        collapsed ? 'w-16' : 'w-64',
      ]"
    >
      <!-- Logo -->
      <div
        :class="[
          'flex items-center h-16 border-b border-white/[0.06] px-4',
          collapsed ? 'justify-center' : 'gap-3',
        ]"
      >
        <svg width="28" height="28" viewBox="0 0 28 28" fill="none" aria-label="InvestigatorAI logo">
          <circle cx="12" cy="12" r="8" stroke="#f59e0b" stroke-width="2" />
          <line x1="18" y1="18" x2="25" y2="25" stroke="#f59e0b" stroke-width="2.5" stroke-linecap="round" />
          <circle cx="12" cy="12" r="4" stroke="#3b82f6" stroke-width="1.5" />
          <circle cx="10.5" cy="10.5" r="1.2" fill="#f59e0b" />
        </svg>
        <span
          v-if="!collapsed"
          class="text-sm font-semibold text-text-primary tracking-tight whitespace-nowrap"
        >
          Investigator<span class="text-accent-amber">AI</span>
        </span>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 px-2 py-4 space-y-0.5" aria-label="Main navigation">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :title="collapsed ? item.name : undefined"
          :class="[
            'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium',
            'transition-all duration-200 ease-spring cursor-pointer',
            'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50',
            collapsed ? 'justify-center' : '',
            route.path.startsWith(item.path)
              ? 'bg-accent-blue/10 text-accent-blue border-l-2 border-accent-blue pl-[10px]'
              : 'text-text-muted hover:text-text-primary hover:bg-white/5 border-l-2 border-transparent',
          ]"
        >
          <component :is="item.icon" :size="18" class="shrink-0" />
          <span v-if="!collapsed" class="truncate">{{ item.name }}</span>
        </RouterLink>
      </nav>

      <!-- Workspace selector -->
      <div class="px-2 py-3 border-t border-white/[0.06] space-y-2">
        <div v-if="!collapsed" class="relative">
          <button
            type="button"
            class="w-full flex items-center justify-between gap-2 px-3 py-2 rounded-lg text-xs
                   text-text-muted hover:text-text-primary hover:bg-white/5
                   transition-all duration-200 ease-spring cursor-pointer
                   focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50"
            @click="workspaceOpen = !workspaceOpen"
          >
            <span class="truncate font-medium">{{ activeWorkspace }}</span>
            <ChevronDown
              :size="14"
              :class="['shrink-0 transition-transform duration-200', workspaceOpen ? 'rotate-180' : '']"
            />
          </button>
          <div
            v-if="workspaceOpen"
            class="absolute bottom-full mb-1 left-0 right-0 glass-card py-1 z-30"
          >
            <button
              v-for="ws in workspaces"
              :key="ws"
              type="button"
              :class="[
                'w-full text-left px-3 py-2 text-xs truncate cursor-pointer',
                'transition-colors duration-150',
                ws === activeWorkspace
                  ? 'text-accent-blue bg-accent-blue/10'
                  : 'text-text-muted hover:text-text-primary hover:bg-white/5',
              ]"
              @click="activeWorkspace = ws; workspaceOpen = false"
            >
              {{ ws }}
            </button>
          </div>
        </div>

        <!-- User avatar -->
        <div
          :class="[
            'flex items-center gap-2.5 px-2 py-1.5 rounded-lg',
            collapsed ? 'justify-center' : '',
          ]"
        >
          <div
            class="w-7 h-7 rounded-full bg-gradient-to-br from-accent-blue to-violet-600
                   flex items-center justify-center text-xs font-bold text-white shrink-0"
            title="Valerio Casale"
          >
            VC
          </div>
          <div v-if="!collapsed" class="overflow-hidden">
            <p class="text-xs font-medium text-text-primary truncate">Valerio Casale</p>
            <p class="text-[11px] text-text-muted truncate">Editor</p>
          </div>
        </div>
      </div>

      <!-- Collapse toggle -->
      <button
        type="button"
        class="flex items-center justify-center h-10 border-t border-white/[0.06]
               text-text-muted hover:text-text-primary hover:bg-white/5
               transition-all duration-200 ease-spring cursor-pointer
               focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-blue/50"
        :aria-label="collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        @click="collapsed = !collapsed"
      >
        <component :is="collapsed ? ChevronRight : ChevronLeft" :size="16" />
      </button>
    </aside>

    <!-- Main area -->
    <div class="flex-1 flex flex-col min-w-0">
      <!-- Top bar -->
      <header class="h-16 flex items-center justify-between px-6 border-b border-white/[0.06] bg-bg-surface shrink-0">
        <nav aria-label="Breadcrumb">
          <ol class="flex items-center gap-2 text-sm">
            <li class="text-text-muted">InvestigatorAI</li>
            <li class="text-text-muted">/</li>
            <li class="text-text-primary font-medium">{{ breadcrumb }}</li>
          </ol>
        </nav>

        <div class="flex items-center gap-2">
          <!-- Notifications -->
          <button
            type="button"
            class="relative btn-ghost focus-ring"
            aria-label="Notifications"
          >
            <Bell :size="18" />
            <span
              v-if="notificationCount > 0"
              class="absolute -top-0.5 -right-0.5 w-4 h-4 rounded-full bg-danger
                     flex items-center justify-center text-[10px] font-bold text-white"
            >
              {{ notificationCount }}
            </span>
          </button>

          <!-- Settings -->
          <button
            type="button"
            class="btn-ghost focus-ring"
            aria-label="Settings"
          >
            <Settings :size="18" />
          </button>
        </div>
      </header>

      <!-- Page content with animated grid bg -->
      <main
        class="flex-1 overflow-auto relative"
        style="background-image: linear-gradient(rgba(255,255,255,0.018) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.018) 1px, transparent 1px); background-size: 40px 40px;"
      >
        <RouterView v-slot="{ Component }">
          <Transition name="fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>
