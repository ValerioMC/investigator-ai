<script setup lang="ts">
import type { ConfidenceLevel } from '@/types/api'

const props = defineProps<{
  confidence: ConfidenceLevel
}>()

const config: Record<ConfidenceLevel, { classes: string; label: string }> = {
  HIGH: {
    classes: 'bg-confidence-high/15 text-confidence-high border border-confidence-high/30',
    label: 'HIGH',
  },
  MEDIUM: {
    classes: 'bg-confidence-medium/15 text-confidence-medium border border-confidence-medium/30',
    label: 'MEDIUM',
  },
  LOW: {
    classes: 'bg-confidence-low/15 text-confidence-low border border-confidence-low/30',
    label: 'LOW',
  },
  UNVERIFIED: {
    classes: 'bg-slate-800 text-slate-400 border border-slate-700',
    label: 'UNVERIFIED',
  },
}
</script>

<template>
  <span
    :class="[
      config[props.confidence].classes,
      'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold font-mono tracking-wide',
    ]"
  >
    {{ config[props.confidence].label }}
  </span>
</template>
