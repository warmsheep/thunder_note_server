<script setup>
defineProps({
  text: { type: String, default: '加载中...' },
  inline: { type: Boolean, default: false }
})
</script>

<template>
  <div class="loading-state" :class="{ inline }" role="status" aria-live="polite">
    <span class="loading-spinner" aria-hidden="true"></span>
    <span class="loading-text">{{ text }}</span>
  </div>
</template>

<style scoped>
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 24px;
  color: var(--color-text-secondary);
  font-size: 14px;
}
.loading-state.inline {
  flex-direction: row;
  padding: 8px 12px;
  gap: 8px;
}
.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--color-divider);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.loading-state.inline .loading-spinner {
  width: 14px;
  height: 14px;
  border-width: 2px;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
