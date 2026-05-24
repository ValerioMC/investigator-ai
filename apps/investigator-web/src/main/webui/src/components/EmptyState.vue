<script setup lang="ts">
defineProps<{
  title: string
  description: string
  action?: { label: string; href: string }
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-20 px-6 text-center">
    <!-- SVG: magnifying glass over a minimal network graph -->
    <svg
      width="120"
      height="120"
      viewBox="0 0 120 120"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="mb-6 opacity-40"
      aria-hidden="true"
    >
      <!-- network nodes -->
      <circle cx="22" cy="80" r="5" stroke="#475569" stroke-width="1.5" />
      <circle cx="45" cy="95" r="4" stroke="#475569" stroke-width="1.5" />
      <circle cx="10" cy="60" r="3.5" stroke="#475569" stroke-width="1.5" />
      <!-- network edges -->
      <line x1="22" y1="80" x2="45" y2="95" stroke="#334155" stroke-width="1" />
      <line x1="22" y1="80" x2="10" y2="60" stroke="#334155" stroke-width="1" />
      <line x1="45" y1="95" x2="38" y2="74" stroke="#334155" stroke-width="1" />

      <!-- magnifying glass circle -->
      <circle cx="62" cy="54" r="28" stroke="#475569" stroke-width="2" />
      <circle cx="62" cy="54" r="28" stroke="#3b82f6" stroke-width="2" stroke-dasharray="4 6" opacity="0.4" />

      <!-- inner network visible through glass -->
      <circle cx="55" cy="48" r="4" stroke="#64748b" stroke-width="1.5" />
      <circle cx="70" cy="44" r="3" stroke="#64748b" stroke-width="1.5" />
      <circle cx="67" cy="62" r="3.5" stroke="#64748b" stroke-width="1.5" />
      <line x1="55" y1="48" x2="70" y2="44" stroke="#475569" stroke-width="1" />
      <line x1="70" y1="44" x2="67" y2="62" stroke="#475569" stroke-width="1" />
      <line x1="55" y1="48" x2="67" y2="62" stroke="#475569" stroke-width="1" />

      <!-- handle -->
      <line x1="83" y1="75" x2="104" y2="98" stroke="#475569" stroke-width="3" stroke-linecap="round" />
    </svg>

    <h3 class="text-base font-semibold text-text-primary mb-2">{{ title }}</h3>
    <p class="text-sm text-text-muted max-w-sm leading-relaxed">{{ description }}</p>

    <a
      v-if="action"
      :href="action.href"
      class="mt-6 btn-primary focus-ring"
    >
      {{ action.label }}
    </a>
  </div>
</template>
