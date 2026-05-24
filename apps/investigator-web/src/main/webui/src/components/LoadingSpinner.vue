<script setup lang="ts">
defineProps<{
  size?: number
  color?: string
}>()
</script>

<template>
  <span class="inline-flex flex-col items-center gap-2">
    <svg
      :width="size ?? 32"
      :height="size ?? 32"
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      class="spinner-svg"
    >
      <!-- outer ring -->
      <circle
        cx="16"
        cy="16"
        r="14"
        stroke="rgba(59,130,246,0.15)"
        stroke-width="1.5"
      />
      <!-- spinning arc -->
      <circle
        cx="16"
        cy="16"
        r="14"
        stroke="#3b82f6"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-dasharray="44 44"
        class="arc-outer"
      />
      <!-- middle ring -->
      <circle
        cx="16"
        cy="16"
        r="9"
        stroke="rgba(245,158,11,0.12)"
        stroke-width="1"
      />
      <circle
        cx="16"
        cy="16"
        r="9"
        stroke="#f59e0b"
        stroke-width="1"
        stroke-linecap="round"
        stroke-dasharray="18 38"
        class="arc-middle"
      />
      <!-- center dot -->
      <circle cx="16" cy="16" r="2" :fill="color ?? '#3b82f6'" class="dot-pulse" />
    </svg>
    <span v-if="$slots.default" class="text-xs text-text-muted font-mono">
      <slot />
    </span>
  </span>
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
  .arc-outer {
    transform-origin: 16px 16px;
    animation: spin-cw 1.2s linear infinite;
  }

  .arc-middle {
    transform-origin: 16px 16px;
    animation: spin-ccw 0.9s linear infinite;
  }

  .dot-pulse {
    animation: pulse-dot 1.2s cubic-bezier(0.16, 1, 0.3, 1) infinite;
  }
}

@keyframes spin-cw {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

@keyframes spin-ccw {
  from { transform: rotate(0deg); }
  to   { transform: rotate(-360deg); }
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.3; }
}
</style>
