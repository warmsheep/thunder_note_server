<script setup>
import { useRouter } from 'vue-router'

// D1-W23-04 关于页
// - 对齐 Android `AboutFragment`：应用名称 / 版本号 / 构建时间 / 项目主页 / 致谢 / 技术栈 / 许可证
// - 所有版本与构建时间从 Vite 注入的环境变量读取，构建时由 CI 或 `vite.config` define 填充

const router = useRouter()

// D1-W26-04 与 Android `strings.xml` `about_app_title` / `about_slogan` 文案对齐
const APP_NAME = '闪记 Thunder Note'
const APP_DESC = '快速记录，灵感闪现\n让笔记像闪电一样快捷'
const clientVersion = import.meta.env?.VITE_APP_VERSION || 'd1.6-dev'
const buildTime = import.meta.env?.VITE_APP_BUILD_TIME || '—'
const PROJECT_HOME = 'https://github.com/'

const TECH_STACK = [
  { name: 'Vue 3', url: 'https://vuejs.org/' },
  { name: 'Vite', url: 'https://vitejs.dev/' },
  { name: 'Pinia', url: 'https://pinia.vuejs.org/' },
  { name: 'Vue Router', url: 'https://router.vuejs.org/' },
  { name: 'marked + DOMPurify', url: 'https://github.com/cure53/DOMPurify' }
]
const CREDITS = [
  { name: 'Noto Color Emoji / 系统 Emoji', desc: '头像 emoji 渲染' }
]

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace({ name: 'settings' })
  }
}
</script>

<template>
  <div class="about-page">
    <header class="page-header">
      <button type="button" class="back-btn" @click="goBack" aria-label="返回">←</button>
      <h1 class="page-title">关于</h1>
    </header>

    <section class="hero-card">
      <div class="hero-icon" aria-hidden="true">⚡</div>
      <h2 class="hero-title">{{ APP_NAME }}</h2>
      <p class="hero-desc">{{ APP_DESC }}</p>
      <p class="hero-version">
        版本 <span class="mono">{{ clientVersion }}</span>
        <span v-if="buildTime !== '—'" class="build-time"> · 构建于 <span class="mono">{{ buildTime }}</span></span>
      </p>
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
            :href="PROJECT_HOME"
            target="_blank"
            rel="noopener noreferrer"
          >GitHub ↗</a>
        </li>
      </ul>
    </section>

    <section class="card">
      <header class="card-header">
        <h2 class="card-title">Web 技术栈</h2>
      </header>
      <ul class="kv-list">
        <li v-for="t in TECH_STACK" :key="t.name">
          <span class="kv-label">{{ t.name }}</span>
          <a
            class="kv-value link"
            :href="t.url"
            target="_blank"
            rel="noopener noreferrer"
          >↗</a>
        </li>
      </ul>
    </section>

    <section class="card">
      <header class="card-header">
        <h2 class="card-title">致谢</h2>
      </header>
      <ul class="kv-list">
        <li v-for="c in CREDITS" :key="c.name">
          <span class="kv-label">{{ c.name }}</span>
          <span class="kv-value">{{ c.desc }}</span>
        </li>
      </ul>
    </section>

    <section class="card license-card">
      <header class="card-header">
        <h2 class="card-title">许可证</h2>
      </header>
      <div class="license-body">
        <p>
          本项目使用自定义许可证，详见项目主页。第三方依赖遵循各自原始协议（MIT / Apache-2.0 / BSD
          等），未经允许不得用于商业用途。
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.about-page {
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
.hero-card {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-divider);
  padding: 28px 20px;
  text-align: center;
}
.hero-icon {
  font-size: 56px;
  line-height: 1;
  margin-bottom: 10px;
}
.hero-title {
  margin: 0 0 6px 0;
  font-size: 20px;
}
.hero-desc {
  margin: 0 0 14px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  /* W26-04 slogan 含 \n，让换行可见 */
  white-space: pre-line;
}
.hero-version {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-hint);
}
.build-time {
  color: var(--color-text-hint);
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
.kv-label {
  font-size: 14px;
  color: var(--color-text-primary);
}
.kv-value {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.link {
  color: var(--color-primary);
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, monospace;
}
.license-body {
  padding: 14px 18px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.license-body p {
  margin: 0;
}
</style>
