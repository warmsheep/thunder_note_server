<script setup>
import EmptyState from '../components/EmptyState.vue'
</script>

<template>
  <div class="page">
    <EmptyState
      icon="⚡"
      title="闪记列表"
      description="此页将在 D1-W5 接入 /api/messages/list 与新建/编辑/删除闭环"
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
