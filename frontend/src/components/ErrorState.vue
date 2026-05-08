<script setup>
defineProps({
  title: { type: String, default: '加载失败' },
  message: { type: String, default: '请稍后重试' },
  retryLabel: { type: String, default: '重试' },
  showRetry: { type: Boolean, default: true }
})

const emit = defineEmits(['retry'])
</script>

<template>
  <div class="error-state" role="alert">
    <div class="error-icon" aria-hidden="true">⚠️</div>
    <p class="error-title">{{ title }}</p>
    <p class="error-message">{{ message }}</p>
    <button
      v-if="showRetry"
      type="button"
      class="error-retry"
      @click="emit('retry')"
    >{{ retryLabel }}</button>
  </div>
</template>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 64px 24px;
  text-align: center;
}
.error-icon {
  font-size: 36px;
}
.error-title {
  margin: 0;
  font-size: 16px;
  color: var(--color-text-primary);
  font-weight: 500;
}
.error-message {
  margin: 0;
  font-size: 13px;
  color: var(--color-danger);
  max-width: 320px;
  word-break: break-word;
}
.error-retry {
  margin-top: 12px;
  padding: 8px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-primary);
  font-size: 14px;
  cursor: pointer;
}
.error-retry:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>
