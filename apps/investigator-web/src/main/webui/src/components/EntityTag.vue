<script setup lang="ts">
import { User, Building2, FileText, X } from 'lucide-vue-next'

const props = defineProps<{
  type: 'person' | 'company' | 'contract'
  label: string
  dismissible?: boolean
}>()

const emit = defineEmits<{
  dismiss: []
}>()

const typeConfig = {
  person: {
    icon: User,
    classes: 'bg-blue-500/10 text-blue-400 border border-blue-500/20',
  },
  company: {
    icon: Building2,
    classes: 'bg-violet-500/10 text-violet-400 border border-violet-500/20',
  },
  contract: {
    icon: FileText,
    classes: 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20',
  },
}
</script>

<template>
  <span
    :class="[
      typeConfig[props.type].classes,
      'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium max-w-xs',
    ]"
  >
    <component :is="typeConfig[props.type].icon" :size="12" class="shrink-0" />
    <span class="truncate">{{ props.label }}</span>
    <button
      v-if="props.dismissible"
      type="button"
      class="shrink-0 ml-0.5 cursor-pointer hover:opacity-70 transition-opacity focus:outline-none focus-visible:ring-1 focus-visible:ring-current rounded-full"
      :aria-label="`Remove ${props.label}`"
      @click.stop="emit('dismiss')"
    >
      <X :size="10" />
    </button>
  </span>
</template>
