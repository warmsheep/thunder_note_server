<script setup>
import EmptyState from '../components/EmptyState.vue'
</script>

<template>
  <div class="page">
    <EmptyState
      icon="🔍"
      title="搜索"
      description="搜索能力暂未上线，将在后续阶段接入"
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
