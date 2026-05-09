<script setup>
import { ref, onMounted } from 'vue'

// D1-W26-04 启动闪屏（与 Android `SplashFragment` 视觉对齐）
//
// 行为：
//   - 仅在移动端布局（≤768）首次会话生效；桌面端不显示
//   - 不阻塞主路由渲染，仅作为顶层遮罩，0.5s 后淡出
//   - 用 sessionStorage 标志位避免单次会话内反复闪：登出 / 关闭 tab 后下一次再显示
//   - 显示内容：⚡ logo + 应用名 + slogan + 版本号
//
// 字段与 Android `strings.xml` 对齐：
//   - logo_emoji = "⚡"
//   - app_name = "闪记"
//   - about_slogan = "快速记录，灵感闪现\n让笔记像闪电一样快捷"
//   - splash_version_prefix = "v%1$s"

const SHOW_DURATION_MS = 500
const SESSION_KEY = 'tn:splash:shown'

const visible = ref(false)
const fading = ref(false)
const clientVersion = (import.meta.env && import.meta.env.VITE_APP_VERSION) || ''

function isMobile() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(max-width: 768px)').matches
}

function alreadyShownThisSession() {
  if (typeof window === 'undefined' || !window.sessionStorage) return false
  try {
    return window.sessionStorage.getItem(SESSION_KEY) === '1'
  } catch (_e) {
    return false
  }
}

function markShown() {
  if (typeof window === 'undefined' || !window.sessionStorage) return
  try {
    window.sessionStorage.setItem(SESSION_KEY, '1')
  } catch (_e) {
    /* ignore */
  }
}

onMounted(() => {
  if (!isMobile()) return
  if (alreadyShownThisSession()) return
  visible.value = true
  // 触发淡出
  setTimeout(() => {
    fading.value = true
    // 与 transition duration（200ms）对齐
    setTimeout(() => {
      visible.value = false
      markShown()
    }, 220)
  }, SHOW_DURATION_MS)
})
</script>

<template>
  <div
    v-if="visible"
    class="splash-overlay"
    :class="{ fading }"
    aria-hidden="true"
  >
    <div class="splash-card">
      <span class="splash-logo">⚡</span>
      <h1 class="splash-name">闪记</h1>
      <p class="splash-slogan">快速记录，灵感闪现<br />让笔记像闪电一样快捷</p>
      <p v-if="clientVersion" class="splash-version">v{{ clientVersion }}</p>
    </div>
  </div>
</template>

<style scoped>
.splash-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  transition: opacity 0.2s ease;
}
.splash-overlay.fading {
  opacity: 0;
  pointer-events: none;
}
.splash-card {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
}
.splash-logo {
  font-size: 56px;
  line-height: 1;
}
.splash-name {
  margin: 8px 0 0 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.splash-slogan {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.splash-version {
  margin: 12px 0 0 0;
  font-size: 11px;
  color: var(--color-text-hint);
}
</style>
