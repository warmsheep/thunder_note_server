<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useToast } from '../composables/useToast'
import { useProfileStore } from '../stores/profile'

// D1-W23-03 独立设置页
// - 对齐 Android `SettingsFragment`：「关于」「项目主页」「客户端版本」「服务地址」分块
// - 纯链接 / 只读 / 危险操作三种卡片；不做表单编辑（资料编辑仍在 ProfileView）

const router = useRouter()
const authStore = useAuthStore()
const profileStore = useProfileStore()
const { showError } = useToast()

const clientVersion = import.meta.env?.VITE_APP_VERSION || 'd1.6-dev'
const buildTime = import.meta.env?.VITE_APP_BUILD_TIME || ''
const apiBase = computed(() => {
  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    return window.location.origin
  }
  return '(unknown)'
})
const currentUser = computed(() => authStore.user || {})

async function handleLogout() {
  try {
    await authStore.logout()
  } catch (e) {
    showError(e?.serverMessage || e?.message || '登出失败')
    return
  } finally {
    profileStore.reset()
  }
  router.push({ name: 'login' })
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace({ name: 'profile' })
  }
}
</script>

<template>
  <div class="settings-page">
    <header class="page-header">
      <button type="button" class="back-btn" @click="goBack" aria-label="返回">←</button>
      <h1 class="page-title">设置</h1>
    </header>

    <section class="card">
      <header class="card-header">
        <h2 class="card-title">通用</h2>
      </header>
      <ul class="kv-list">
        <li class="link-row" @click="router.push({ name: 'change-password' })">
          <span class="kv-label">修改密码</span>
          <span class="chevron">›</span>
        </li>
        <li class="link-row" @click="router.push({ name: 'settings-about' })">
          <span class="kv-label">关于 Thunder Note</span>
          <span class="chevron">›</span>
        </li>
      </ul>
    </section>

    <section class="card">
      <header class="card-header">
        <h2 class="card-title">系统信息</h2>
      </header>
      <ul class="kv-list">
        <li>
          <span class="kv-label">服务地址</span>
          <span class="kv-value mono">{{ apiBase }}</span>
        </li>
        <li>
          <span class="kv-label">客户端版本</span>
          <span class="kv-value mono">{{ clientVersion }}</span>
        </li>
        <li v-if="buildTime">
          <span class="kv-label">构建时间</span>
          <span class="kv-value mono">{{ buildTime }}</span>
        </li>
        <li>
          <span class="kv-label">当前用户 ID</span>
          <span class="kv-value mono">{{ currentUser.id ?? '-' }}</span>
        </li>
        <li v-if="currentUser.username">
          <span class="kv-label">用户名</span>
          <span class="kv-value mono">{{ currentUser.username }}</span>
        </li>
      </ul>
    </section>

    <section class="card">
      <header class="card-header">
        <h2 class="card-title">项目</h2>
      </header>
      <ul class="kv-list">
        <li>
          <span class="kv-label">项目主页</span>
          <a
            class="kv-value link"
            href="https://github.com/"
            target="_blank"
            rel="noopener noreferrer"
          >GitHub ↗</a>
        </li>
      </ul>
    </section>

    <!-- D1-W28-11「退出登录」按钮已统一迁到 MainShell 侧边栏底部，
         设置页不再重复提供入口（避免三处同款）。 -->
  </div>
</template>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 640px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}
.back-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
  color: var(--color-text-primary);
}
.back-btn:hover {
  color: var(--color-primary);
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.card {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-divider);
  overflow: hidden;
}
.card-header {
  padding: 12px 18px;
  border-bottom: 1px solid var(--color-divider);
  background: var(--color-bg);
}
.card-title {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
}
.kv-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.kv-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 1px solid var(--color-divider);
  gap: 16px;
}
.kv-list li:last-child {
  border-bottom: none;
}
.link-row {
  cursor: pointer;
}
.link-row:hover {
  background: var(--color-bg);
}
.chevron {
  font-size: 18px;
  color: var(--color-text-hint);
}
.kv-label {
  font-size: 14px;
  color: var(--color-text-primary);
}
.kv-value {
  font-size: 13px;
  color: var(--color-text-secondary);
  text-align: right;
  word-break: break-all;
}
.kv-value.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, monospace;
  font-size: 12px;
}
.link {
  color: var(--color-primary);
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}

.danger-card {
  border-color: var(--color-divider);
}
.danger-body {
  padding: 14px 18px;
  display: flex;
  justify-content: flex-end;
}
.btn-danger {
  padding: 7px 16px;
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-danger);
  cursor: pointer;
  font-size: 14px;
}
.btn-danger:hover {
  background: var(--color-danger);
  color: #ffffff;
}
</style>
