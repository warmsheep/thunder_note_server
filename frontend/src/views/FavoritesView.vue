<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useFavoritesStore } from '../stores/favorites'
import { useToast } from '../composables/useToast'
import { renderMarkdown } from '../utils/markdownRenderer'
import { captionForMediaContent, textOfMessage } from '../utils/messageHelpers'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import MediaPreview from '../components/MediaPreview.vue'
import MessageActionMenu from '../components/MessageActionMenu.vue'

// D1-W8 收藏列表
// - 拉 favorites/list 展示已收藏消息（W8-01）
// - 每项可"取消收藏"（W8-03）
// - 点击项跳到 /chat/:flashNoteId（W8-04）；flashNoteId 缺失时给提示

const router = useRouter()
const store = useFavoritesStore()
const { showSuccess, showError } = useToast()

const removingId = ref(null)

onMounted(() => {
  if (!store.loaded) {
    store.fetchList().catch(() => {})
  }
})

const initialLoading = computed(() => store.loading && !store.loaded)
const showError_ = computed(() => Boolean(store.error) && !store.loaded)
const showEmpty = computed(() => store.loaded && store.list.length === 0)

function handleRetry() {
  store.fetchList()
}

async function handleRemove(item) {
  if (!item || item.messageId == null) return
  removingId.value = item.messageId
  try {
    await store.remove(item.messageId)
    showSuccess('已取消收藏')
  } catch (e) {
    showError(e?.serverMessage || e?.message || '取消收藏失败')
  } finally {
    removingId.value = null
  }
}

function openOrigin(item) {
  if (!item) return
  if (item.flashNoteId == null) {
    showError('原闪记已不存在')
    return
  }
  router.push({ name: 'chat', params: { flashNoteId: String(item.flashNoteId) } })
}

function isCardItem(item) {
  return Boolean(item && item.payload && item.payload.cardType)
}

function isMediaItem(item) {
  return Boolean(item && item.mediaType) && !isCardItem(item)
}

// 卡片摘要：[卡片] 标题
function cardSummary(item) {
  if (!item || !item.payload) return ''
  return `[卡片] ${item.payload.title || item.payload.cardType || ''}`
}

// 媒体气泡下方的 caption：过滤后端 [图片] / [视频] / [文件] 占位
function captionFor(item) {
  return captionForMediaContent(item && item.content)
}

// 文本类消息：复用 chat 页同款 markdown + DOMPurify 渲染
function renderedTextHtml(item) {
  return renderMarkdown(item && item.content)
}

function timeText(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const M = d.getMonth() + 1
  const D = d.getDate()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${M}/${D} ${hh}:${mm}`
}

// ---- D1-W21-05 上下文菜单（复制 / 取消收藏） ----
const LONG_PRESS_MS = 600
const LONG_PRESS_TOLERANCE_PX = 8

const menuOpen = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuTarget = ref(null)

const menuItems = computed(() => {
  if (!menuTarget.value) return []
  return [
    { key: 'copy', label: '复制', icon: '📋' },
    {
      key: 'download',
      label: '保存到本地',
      icon: '💾',
      disabled: !isMediaItem(menuTarget.value) || !menuTarget.value.mediaUrl
    },
    { key: 'remove', label: '取消收藏', icon: '☆', danger: true }
  ]
})

async function downloadMedia(item) {
  if (!item?.mediaUrl) return
  const { triggerDownload } = await import('../api/files')
  try {
    await triggerDownload(item.mediaUrl, item.fileName || 'download')
    showSuccess('已开始下载')
  } catch (e) {
    showError(e?.serverMessage || e?.message || '下载失败')
  }
}

function openMenuAt(item, clientX, clientY) {
  if (!item) return
  menuTarget.value = item
  menuX.value = Math.round(clientX)
  menuY.value = Math.round(clientY)
  menuOpen.value = true
}

function onContextMenuItem(e, item) {
  if (!item || item.messageId == null) return
  e.preventDefault()
  openMenuAt(item, e.clientX, e.clientY)
}

// 触摸长按
let longPressTimer = null
let pressStart = { x: 0, y: 0 }
let pressItem = null

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  pressItem = null
}

function onItemTouchStart(e, item) {
  if (!item || item.messageId == null) return
  const t = e.touches && e.touches[0]
  if (!t) return
  clearLongPress()
  pressItem = item
  pressStart = { x: t.clientX, y: t.clientY }
  longPressTimer = setTimeout(() => {
    longPressTimer = null
    if (pressItem) {
      openMenuAt(pressItem, pressStart.x, pressStart.y)
      pressItem = null
    }
  }, LONG_PRESS_MS)
}

function onItemTouchMove(e) {
  if (!longPressTimer) return
  const t = e.touches && e.touches[0]
  if (!t) return
  const dx = Math.abs(t.clientX - pressStart.x)
  const dy = Math.abs(t.clientY - pressStart.y)
  if (dx > LONG_PRESS_TOLERANCE_PX || dy > LONG_PRESS_TOLERANCE_PX) {
    clearLongPress()
  }
}

function onItemTouchEnd() {
  clearLongPress()
}

async function onMenuSelect(key) {
  const item = menuTarget.value
  if (!item) return
  if (key === 'copy') {
    const text = textOfMessage(item)
    try {
      if (navigator && navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(text)
      } else if (typeof document !== 'undefined' && document.execCommand) {
        const ta = document.createElement('textarea')
        ta.value = text
        ta.style.position = 'fixed'
        ta.style.opacity = '0'
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
      }
      showSuccess('已复制')
    } catch (e) {
      showError(e?.message || '复制失败')
    }
  } else if (key === 'download') {
    await downloadMedia(item)
  } else if (key === 'remove') {
    handleRemove(item)
  }
  menuTarget.value = null
}

onBeforeUnmount(() => {
  clearLongPress()
})
</script>

<template>
  <div class="favorites-page">
    <div class="page-toolbar">
      <span class="page-stats">共 {{ store.list.length }} 条收藏</span>
    </div>

    <LoadingState v-if="initialLoading" text="加载收藏中..." />
    <ErrorState
      v-else-if="showError_"
      :message="store.error"
      @retry="handleRetry"
    />
    <template v-else>
      <EmptyState
        v-if="showEmpty"
        icon="⭐"
        title="还没有收藏"
        description="长按聊天消息即可加入收藏"
      />
      <section v-else class="group">
        <article
          v-for="item in store.sortedList"
          :key="item.id"
          class="fav-item"
          @click="openOrigin(item)"
          @contextmenu="onContextMenuItem($event, item)"
          @touchstart.passive="onItemTouchStart($event, item)"
          @touchmove.passive="onItemTouchMove"
          @touchend="onItemTouchEnd"
          @touchcancel="onItemTouchEnd"
        >
          <div class="fav-flashnote">
            <span class="fn-icon" aria-hidden="true">{{ item.flashNoteIcon || '⚡' }}</span>
            <span class="fn-title">{{ item.flashNoteTitle || '未知闪记' }}</span>
          </div>

          <!-- 卡片：仅摘要 -->
          <p v-if="isCardItem(item)" class="fav-content">{{ cardSummary(item) }}</p>

          <!-- 媒体：复用 MediaPreview（图片/视频/音频缩略图，PDF / 文本 / Office 文件卡片） -->
          <template v-else-if="isMediaItem(item)">
            <div class="fav-media" @click.stop>
              <MediaPreview :message="item" />
            </div>
            <p v-if="captionFor(item)" class="fav-caption">{{ captionFor(item) }}</p>
          </template>

          <!-- 纯文本：与 chat 页同款 markdown + DOMPurify 渲染（防止显示成原始 <a>/<strong> 标签） -->
          <div v-else class="fav-content markdown-body" v-html="renderedTextHtml(item)"></div>

          <div class="fav-meta">
            <span class="fav-time">{{ timeText(item.favoritedAt) }} 收藏</span>
            <span
              v-if="removingId === item.messageId"
              class="removing-tag"
              aria-live="polite"
            >处理中...</span>
            <span v-else class="action-hint" aria-hidden="true">· 右键/长按菜单取消收藏</span>
          </div>
        </article>
      </section>
    </template>

    <!-- W21-05 上下文菜单（复制 / 取消收藏） -->
    <MessageActionMenu
      v-model:open="menuOpen"
      :x="menuX"
      :y="menuY"
      :items="menuItems"
      @select="onMenuSelect"
    />
  </div>
</template>

<style scoped>
.favorites-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-stats {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.removing-tag {
  font-size: 12px;
  color: var(--color-text-hint);
}
.action-hint {
  font-size: 12px;
  color: var(--color-text-hint);
}

.group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fav-item {
  background: var(--color-surface);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;
  transition: border-color 0.12s, background 0.12s;
}
.fav-item:hover {
  border-color: var(--color-primary);
  background: var(--color-bg);
}

.fav-flashnote {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.fn-icon {
  font-size: 16px;
}
.fn-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fav-content {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-primary);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 5em;
  overflow: hidden;
}

/* 媒体收藏卡片：限制图片 / 视频 / 文件卡片在收藏列表里的最大尺寸，避免抢眼。
   关键：不要在这里用 flex / align-items: stretch，否则 <img> 会被 stretch 到 100%
   宽，配合 max-height 出现高度被截但宽度仍被拉满的"拉伸"效果。
   用 block 容器 + width/height: auto 让浏览器按图片原始宽高比自由缩放。 */
.fav-media {
  display: block;
  cursor: default;
}
.fav-media :deep(.media-image),
.fav-media :deep(.media-video) {
  /* 显式 auto，覆盖父级可能传下来的 100% / stretch */
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 220px;
  /* object-fit: contain 在因 aspect-ratio 与容器不一致时仍保留比例（双保险） */
  object-fit: contain;
}
.fav-caption {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

/* markdown 文本收藏：与 chat 气泡同款排版（块级元素的 margin / list 缩进） */
.markdown-body {
  white-space: normal;
  max-height: none;
  overflow: visible;
}
.markdown-body :deep(p) { margin: 0; line-height: 1.5; }
.markdown-body :deep(p) + :deep(p) { margin-top: 6px; }
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 6px 0 4px 0;
  font-weight: 600;
  font-size: inherit;
  line-height: 1.4;
}
.markdown-body :deep(h1) { font-size: 1.15em; }
.markdown-body :deep(h2) { font-size: 1.1em; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 4px 0; padding-left: 22px; }
.markdown-body :deep(li) { margin: 2px 0; }
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
  margin: 6px 0;
}
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid currentColor;
  padding-left: 10px;
  margin: 4px 0;
  opacity: 0.85;
}
.markdown-body :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  margin: 8px 0;
}
.fav-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--color-text-hint);
}
.action {
  padding: 4px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.action.danger:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
</style>
