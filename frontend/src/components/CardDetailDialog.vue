<script setup>
import { computed, watch, ref, onBeforeUnmount } from 'vue'
import MediaPreview from './MediaPreview.vue'
import { renderMarkdown } from '../utils/markdownRenderer'

// D1-W25-02 卡片详情对话框（与 Android `CardDetailActivity` 对齐）
//
// 接收：
//   - message: 完整 server 消息对象，至少含 payload.{title, summary, items}
//   - open: v-model:open 控制可见
//
// 渲染：
//   - 标题 → payload.title（缺省 fallback「卡片消息」）
//   - 摘要 → payload.summary（不做 buildCardSummary 兜底，让用户主观选择是否填）
//   - items 列表：
//     - IMAGE / VIDEO / AUDIO / FILE → 复用 MediaPreview（含 PDF / 文本 / Office 等扩展行为）
//     - TEXT 类（无 url，仅 content）→ 走 markdown 渲染（与气泡 markdown 一致）
//     - 兜底 → 文本占位
//   - 关闭：modal-close × 按钮 / 点空白 / ESC（统一处理）
//
// blob 释放：每个 MediaPreview 自己 onBeforeUnmount 时 revoke；
// 这里只确保 dialog 关闭时组件被销毁（v-if 而不是 v-show）。

const props = defineProps({
  open: { type: Boolean, default: false },
  message: { type: Object, default: null }
})

const emit = defineEmits(['update:open', 'close'])

const payload = computed(() => (props.message && props.message.payload) || {})
const title = computed(() => payload.value.title || '卡片消息')
const summary = computed(() => payload.value.summary || '')
const items = computed(() => Array.isArray(payload.value.items) ? payload.value.items : [])

// 判断 item 是否走 MediaPreview：有 url 且 type 属于媒体白名单
function isMediaItem(item) {
  if (!item) return false
  const t = (item.type || '').toUpperCase()
  if (t !== 'IMAGE' && t !== 'VIDEO' && t !== 'AUDIO' && t !== 'FILE' && t !== 'VOICE') return false
  return Boolean(item.url || item.mediaUrl)
}

// CardItem → message-like，给 MediaPreview 直接消费
function itemAsMessage(item) {
  if (!item) return null
  const t = (item.type || '').toUpperCase()
  // VOICE 在 Android 是单独 mediaType；MediaPreview 暂不识别 VOICE 类型，
  // 用 AUDIO 兜底走 audio 分支（W27 录音落地后再细化）
  const mediaType = t === 'VOICE' ? 'AUDIO' : t
  return {
    mediaType,
    mediaUrl: item.url || item.mediaUrl || null,
    thumbnailUrl: item.thumbnailUrl || null,
    fileName: item.fileName || null,
    fileSize: item.fileSize || null,
    mediaDuration: item.mediaDuration || null,
    content: ''
  }
}

function renderedTextItem(item) {
  if (!item || !item.content) return ''
  return renderMarkdown(String(item.content))
}

function close() {
  emit('update:open', false)
  emit('close')
}

// ESC 关闭
function onKeydown(e) {
  if (e.key === 'Escape' && props.open) close()
}

watch(
  () => props.open,
  (v) => {
    if (typeof window === 'undefined') return
    if (v) {
      window.addEventListener('keydown', onKeydown)
    } else {
      window.removeEventListener('keydown', onKeydown)
    }
  }
)

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKeydown)
  }
})
</script>

<template>
  <div v-if="open" class="modal-overlay" @click.self="close">
    <div class="modal modal-large" role="dialog" aria-modal="true" aria-label="卡片详情">
      <header class="modal-header">
        <h2 class="modal-title">{{ title }}</h2>
        <button type="button" class="modal-close" @click="close" aria-label="关闭">×</button>
      </header>
      <div class="modal-body card-detail">
        <p v-if="summary" class="card-detail-summary">{{ summary }}</p>

        <ul v-if="items.length" class="card-items">
          <li v-for="(item, i) in items" :key="i" class="card-item">
            <span class="card-item-index">{{ i + 1 }}</span>
            <div class="card-item-body">
              <p class="card-item-meta">{{ (item.type || 'TEXT').toUpperCase() }}</p>
              <!-- 媒体类：用 MediaPreview，复用图片 lightbox / 视频全屏 / 音频播放 / 文件下载 -->
              <MediaPreview v-if="isMediaItem(item)" :message="itemAsMessage(item)" />
              <!-- 纯文本 item：markdown 渲染（与气泡一致） -->
              <div
                v-else-if="item.content"
                class="card-item-text markdown-body"
                v-html="renderedTextItem(item)"
              ></div>
              <p v-else-if="item.fileName" class="card-item-file">📎 {{ item.fileName }}</p>
              <p v-else class="card-item-empty">（空 item）</p>
            </div>
          </li>
        </ul>
        <p v-else class="card-detail-empty">该卡片没有附加内容</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}
.modal {
  background: var(--color-bg);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-divider);
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 32px);
  width: 100%;
  max-width: 520px;
}
.modal-large {
  max-width: 720px;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-divider);
}
.modal-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text-primary);
}
.modal-close {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  color: var(--color-text-secondary);
  padding: 0 6px;
}
.modal-close:hover {
  color: var(--color-text-primary);
}
.modal-body {
  padding: 16px;
  overflow-y: auto;
}

.card-detail-summary {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}
.card-detail-empty {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-hint);
  text-align: center;
  padding: 24px 0;
}

.card-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card-item {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
.card-item-index {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--color-primary-light);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
}
.card-item-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-item-meta {
  margin: 0;
  font-size: 11px;
  color: var(--color-text-hint);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.card-item-text {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-primary);
  line-height: 1.5;
}
.card-item-file {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.card-item-empty {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-hint);
  font-style: italic;
}

/* markdown 渲染样式（与 MessageBubble 保持基本一致） */
.markdown-body :deep(p) {
  margin: 0;
  line-height: 1.5;
}
.markdown-body :deep(p) + :deep(p) {
  margin-top: 6px;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 4px 0;
  padding-left: 22px;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid currentColor;
  padding-left: 10px;
  margin: 4px 0;
  opacity: 0.85;
}
.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.07);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 0.92em;
}
.markdown-body :deep(pre) {
  background: rgba(0, 0, 0, 0.08);
  padding: 8px 10px;
  border-radius: 6px;
  overflow-x: auto;
}
</style>
