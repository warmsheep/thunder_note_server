<script setup>
import EmptyState from '../components/EmptyState.vue'
</script>

<template>
  <div class="page">
    <EmptyState
      icon="⭐"
      title="收藏"
      description="此页将在 D1-W7 接入 /api/favorites/list"
    />
  </div>
</template>

<style scoped>
.page {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-divider);
  min-height: 320px;
}
</style>
