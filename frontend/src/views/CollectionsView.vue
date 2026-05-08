<script setup>
import EmptyState from '../components/EmptyState.vue'
</script>

<template>
  <div class="page">
    <EmptyState
      icon="📂"
      title="合集"
      description="此页将在后续阶段接入合集列表与详情"
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
