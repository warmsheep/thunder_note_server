<script setup>
defineProps({
  icon: { type: String, default: '📭' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' }
})
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon" aria-hidden="true">{{ icon }}</div>
    <p class="empty-title">{{ title }}</p>
    <p v-if="description" class="empty-description">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 64px 24px;
  text-align: center;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 4px;
}
.empty-title {
  margin: 0;
  font-size: 16px;
  color: var(--color-text-primary);
  font-weight: 500;
}
.empty-description {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-hint);
}
</style>
