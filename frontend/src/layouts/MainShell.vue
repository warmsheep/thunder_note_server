<script>
// D1-W26-01 主导航数据源：与 Android `menu_bottom_tabs.xml` 顺序对齐
// 闪记 / 合集 / 联系人 / 收藏 / 搜索 / 我的（搜索为 Web 额外项）
// 暴露为 normal script 便于测试与外部引用（script setup 不允许 export）
export function buildNavItems(pendingCount = 0) {
  const safeCount = Number.isFinite(Number(pendingCount)) ? Math.max(0, Math.floor(Number(pendingCount))) : 0
  return [
    { name: 'notes', label: '闪记', icon: '⚡', to: '/notes' },
    { name: 'collections', label: '合集', icon: '📂', to: '/collections' },
    {
      name: 'contacts',
      label: '联系人',
      icon: '👥',
      to: '/contacts',
      badge: safeCount > 0 ? safeCount : 0
    },
    { name: 'favorites', label: '收藏', icon: '⭐', to: '/favorites' },
    { name: 'search', label: '搜索', icon: '🔍', to: '/search' },
    { name: 'profile', label: '我的', icon: '👤', to: '/profile' }
  ]
}
</script>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute, RouterView, RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useContactsStore } from '../stores/contacts'
import { useToast } from '../composables/useToast'

// D1-W4-01 / W4-02：主壳层
// 桌面：左侧栏 + 内容区；移动：顶栏 + 内容 + 底部 tab
//
// D1-W26-01 联系人提升为顶级导航项（与 Android `tab_contact` 对齐），
// 共 6 项：闪记 / 合集 / 联系人 / 收藏 / 搜索 / 我的。
// 桌面 sidebar 与移动 bottombar 共用同一份 `navItems` 数据源。
// 联系人项右侧带红点 / 数字徽标（来自 contactsStore.pendingCount）。
// 用户菜单中的「联系人」入口保留作为快捷路径，避免老用户找不到。

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const contactsStore = useContactsStore()
const { showSuccess, showError } = useToast()

// D1-W14-05：登录后拉一次 pending 计数；路由切换时静默刷新
onMounted(() => {
  if (authStore.isAuthenticated) {
    contactsStore.fetchPendingCount()
  }
})
watch(() => route.fullPath, () => {
  if (authStore.isAuthenticated) {
    contactsStore.fetchPendingCount()
  }
})

const userMenuOpen = ref(false)
function toggleUserMenu() {
  userMenuOpen.value = !userMenuOpen.value
}
function closeUserMenu() {
  userMenuOpen.value = false
}

// D1-W26-01 联系人提升为顶级导航项；buildNavItems 是 normal script 中导出的纯工厂，
// 这里包成 computed 让 contactsStore.pendingCount 变更触发 badge 重渲染
const navItems = computed(() => buildNavItems(contactsStore.pendingCount))

const currentTitle = computed(() => {
  const matched = navItems.value.find((item) => route.path.startsWith(item.to))
  return matched ? matched.label : '闪记'
})

const displayName = computed(() => authStore.displayName || '未登录')

async function handleLogout() {
  closeUserMenu()
  try {
    await authStore.logout()
    showSuccess('已退出登录')
    router.replace({ path: '/login' })
  } catch (e) {
    showError(e?.message || '退出失败')
  }
}
</script>

<template>
  <div class="shell" @click.self="closeUserMenu">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-mark" aria-hidden="true">⚡</span>
        <span class="brand-text">闪记</span>
      </div>
      <nav class="sidebar-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="item.to"
          class="sidebar-link"
          active-class="sidebar-link-active"
        >
          <span class="sidebar-icon" aria-hidden="true">{{ item.icon }}</span>
          <span class="sidebar-label">{{ item.label }}</span>
          <!-- D1-W26-01 联系人项 pendingCount 红点徽标 -->
          <span
            v-if="item.badge"
            class="nav-badge"
            :title="item.badge + ' 条好友请求'"
          >{{ item.badge > 99 ? '99+' : item.badge }}</span>
        </RouterLink>
      </nav>
      <!-- D1-W28-11 退出登录统一到侧边栏底部：原ProfileView / SettingsView 重复的
           「退出登录」入口都已删除，避免三处同类项重复 -->
      <div class="sidebar-bottom">
        <button
          type="button"
          class="sidebar-logout"
          @click="handleLogout"
          title="退出当前账号"
        >
          <span class="sidebar-icon" aria-hidden="true">⏻</span>
          <span class="sidebar-label">退出登录</span>
        </button>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <h1 class="topbar-title">{{ currentTitle }}</h1>
        <div class="topbar-user">
          <button
            type="button"
            class="user-trigger"
            :aria-expanded="userMenuOpen"
            aria-haspopup="menu"
            @click.stop="toggleUserMenu"
          >
            <span class="user-avatar" aria-hidden="true">
              {{ displayName.slice(0, 1) }}
              <span v-if="contactsStore.pendingCount > 0" class="trigger-dot" :title="contactsStore.pendingCount + ' 条好友请求'"></span>
            </span>
            <span class="user-name">{{ displayName }}</span>
          </button>
          <div v-if="userMenuOpen" class="user-menu" role="menu" @click.stop>
            <RouterLink to="/profile" class="user-menu-item" role="menuitem" @click="closeUserMenu">
              我的资料
            </RouterLink>
            <RouterLink to="/contacts" class="user-menu-item" role="menuitem" @click="closeUserMenu">
              <span>联系人</span>
              <span v-if="contactsStore.pendingCount > 0" class="user-menu-badge">{{ contactsStore.pendingCount }}</span>
            </RouterLink>
            <!-- D1-W28-11 退出登录已迁到侧边栏底部，用户菜单不再重复展示 -->
          </div>
        </div>
      </header>

      <section class="content">
        <RouterView />
      </section>
    </main>

    <nav class="bottombar" aria-label="移动端导航">
      <RouterLink
        v-for="item in navItems"
        :key="item.name"
        :to="item.to"
        class="bottom-link"
        active-class="bottom-link-active"
      >
        <span class="bottom-icon-wrap">
          <span class="bottom-icon" aria-hidden="true">{{ item.icon }}</span>
          <!-- D1-W26-01 移动端 bottombar 联系人项徽标 -->
          <span
            v-if="item.badge"
            class="bottom-badge"
            :title="item.badge + ' 条好友请求'"
          >{{ item.badge > 99 ? '99+' : item.badge }}</span>
        </span>
        <span class="bottom-label">{{ item.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 220px 1fr;
  min-height: 100vh;
}

.sidebar {
  background: var(--color-nav-bg);
  border-right: 1px solid var(--color-divider);
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--color-primary-light);
  color: var(--color-primary);
}
.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  /* D1-W28-11 让 nav 撑满 sidebar 剩余高度，把 sidebar-bottom 推到底部 */
  flex: 1;
  min-height: 0;
}
/* D1-W28-11 退出登录按钮：与 sidebar-link 同款外形 + 顶部分隔线 */
.sidebar-bottom {
  border-top: 1px solid var(--color-divider);
  padding-top: 12px;
  margin-top: auto;
}
.sidebar-logout {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.sidebar-logout:hover {
  background: var(--color-surface);
  color: var(--color-danger);
}
.sidebar-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: 14px;
  text-decoration: none;
}
.sidebar-link:hover {
  background: var(--color-surface);
  color: var(--color-text-primary);
}
.sidebar-link-active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 500;
}
.sidebar-icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
}
.sidebar-link {
  position: relative;
}
.sidebar-label {
  flex: 1;
}
/* D1-W26-01 sidebar / bottombar 通用徽标（红色圆角，最多 99+） */
.nav-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-danger);
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  margin-left: auto;
}

.main {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-bg);
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: var(--color-topbar-bg);
  border-bottom: 1px solid var(--color-divider);
}
.topbar-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.topbar-user {
  position: relative;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-text-secondary);
}
.user-trigger:hover {
  background: var(--color-surface);
  border-color: var(--color-divider);
}
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
}
.user-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  min-width: 160px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  padding: 4px;
  display: flex;
  flex-direction: column;
}
.user-menu-item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 8px 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: 14px;
  color: var(--color-text-primary);
  cursor: pointer;
  text-decoration: none;
}
.user-menu-item:hover {
  background: var(--color-bg);
}
.user-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.user-menu-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-danger);
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
}
.user-menu-danger {
  color: var(--color-danger);
}
.user-avatar {
  position: relative;
}
.trigger-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger);
  border: 2px solid var(--color-topbar-bg);
}

.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

/* W12-01 桌面超宽屏：MainShell 包裹的列表/合集/收藏/搜索/资料/联系人/改密页面
   居中限宽，避免在 4K 屏上一行字横跨太宽。
   注：ChatView 是独立路由（/chat/:flashNoteId），不在 MainShell 内，
   它的限宽由 ChatView 自身处理，不会被这里影响。 */
@media (min-width: 1280px) {
  .content {
    padding: 28px 40px;
  }
  .content > * {
    max-width: var(--container-max-lg);
    margin-left: auto;
    margin-right: auto;
  }
}

/* W12-01 大屏 sidebar 加宽，提供更舒适的视觉间距 */
@media (min-width: 1440px) {
  .shell {
    grid-template-columns: 248px 1fr;
  }
}

.bottombar {
  display: none;
}

/* W12-02 移动端 ≤768px */
@media (max-width: 768px) {
  .shell {
    grid-template-columns: 1fr;
  }
  .sidebar {
    display: none;
  }
  .topbar {
    padding: 12px 16px;
  }
  .content {
    padding: 16px;
    padding-bottom: 88px;
  }
  .bottombar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    display: flex;
    justify-content: space-around;
    background: var(--color-surface);
    border-top: 1px solid var(--color-divider);
    padding: 6px 0 calc(6px + env(safe-area-inset-bottom, 0px));
    z-index: 10;
  }
  .bottom-link {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: 6px 0;
    flex: 1;
    text-decoration: none;
    color: var(--color-text-secondary);
    font-size: 11px;
  }
  .bottom-link-active {
    color: var(--color-primary);
  }
  .bottom-icon-wrap {
    position: relative;
    display: inline-flex;
  }
  .bottom-icon {
    font-size: 20px;
  }
  /* D1-W26-01 bottombar 徽标：右上角红色圆点（数字版 / 红点版自动选） */
  .bottom-badge {
    position: absolute;
    top: -4px;
    right: -8px;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border-radius: 999px;
    background: var(--color-danger);
    color: #ffffff;
    font-size: 10px;
    font-weight: 600;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
    border: 2px solid var(--color-surface);
    box-sizing: content-box;
  }

  /* 移动端 user-name 在 topbar 太挤，隐藏，仅显示头像 */
  .user-name {
    display: none;
  }
}

/* W12-02 极小屏 ≤480px */
@media (max-width: 480px) {
  .topbar {
    padding: 10px 12px;
  }
  .topbar-title {
    font-size: 15px;
  }
  .content {
    padding: 12px;
    padding-bottom: 84px;
  }
  .bottom-link {
    font-size: 10px;
  }
  .bottom-icon {
    font-size: 18px;
  }
}
</style>
