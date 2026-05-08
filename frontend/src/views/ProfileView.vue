<script setup>
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()

const user = computed(() => authStore.user || {})
</script>

<template>
  <div class="profile">
    <header class="profile-header">
      <div class="avatar">{{ (authStore.displayName || '?').slice(0, 1) }}</div>
      <div class="meta">
        <p class="name">{{ authStore.displayName || '未登录' }}</p>
        <p v-if="user.username" class="caption">用户名：{{ user.username }}</p>
        <p v-if="user.email" class="caption">邮箱：{{ user.email }}</p>
      </div>
    </header>
    <p class="hint">完整的资料编辑、修改密码、注销账号将在后续阶段接入。</p>
  </div>
</template>

<style scoped>
.profile {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-divider);
  padding: 32px 24px;
  min-height: 320px;
}
.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}
.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 22px;
  font-weight: 600;
}
.meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.caption {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.hint {
  margin: 0;
  padding-top: 16px;
  border-top: 1px solid var(--color-divider);
  font-size: 13px;
  color: var(--color-text-hint);
}
</style>
