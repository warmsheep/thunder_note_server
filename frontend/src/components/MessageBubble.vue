<script setup>
import { computed, ref, onBeforeUnmount } from 'vue'
import { renderMarkdown } from '../utils/markdownRenderer'
import { buildCardSummary, captionForMediaContent, textOfMessage } from '../utils/messageHelpers'
import MediaPreview from './MediaPreview.vue'
import MessageActionMenu from './MessageActionMenu.vue'
import CardMediaGrid from './CardMediaGrid.vue'

// D1-W6 / D1-W21 单条消息气泡
// - 自己发送的右对齐绿色气泡，对方/系统左对齐灰色气泡
// - 文本：D1-W17-04 用 markdown 渲染（marked + DOMPurify）
// - 卡片：D1-W17-02 点击 → 打开详情查看 items 列表
// - 媒体：W8 实现
// - 选择模式：左侧多选 checkbox；非选择模式顶右键/长按调出上下文菜单
//
// W21-01 上下文菜单（对齐 Android PopupMessageActions）：复制 / 转发 / 收藏 / 多选 / 删除
//   - 右键 contextmenu 与触摸长按 600ms 两种触发
//   - W21-04 永久按钮收敛：只保留「重试」（failed 状态限定）

const LONG_PRESS_MS = 600
const LONG_PRESS_TOLERANCE_PX = 8

const props = defineProps({
  message: { type: Object, required: true },
  mine: { type: Boolean, default: false },
  selectMode: { type: Boolean, default: false },
  selected: { type: Boolean, default: false },
  favorited: { type: Boolean, default: false }
})

// W21-03 新增 forward-single 事件（与多选转发区分）
// W21-01 新增 enter-select-with 事件：入口多选模式并预选中该条
const emit = defineEmits([
  'toggle-select',
  'delete',
  'retry',
  'toggle-favorite',
  'open-card',
  'forward-single',
  'enter-select-with',
  'download'
])

const m = computed(() => props.message || {})

const status = computed(() => m.value.__status || 'sent')

const isCard = computed(() => Boolean(m.value.payload && m.value.payload.cardType))
const isMedia = computed(() => Boolean(m.value.mediaType) && !isCard.value)

// D1-W25-01 / W25-04 卡片气泡内的 items / 文件列表 / 智能 summary
const cardItems = computed(() => {
  const items = m.value.payload && m.value.payload.items
  return Array.isArray(items) ? items : []
})
// 非图片/视频 item 单独以「📎 fileName」行渲染在网格下方（FILE / VOICE / AUDIO）
const cardFileItems = computed(() => {
  return cardItems.value.filter((it) => {
    const t = (it && it.type ? String(it.type) : '').toUpperCase()
    return t === 'FILE' || t === 'VOICE' || t === 'AUDIO'
  })
})
// summary 智能兜底：payload.summary 为空时按 items[0..2] 占位拼接
const cardSummary = computed(() => buildCardSummary(m.value.payload))

// D1-W17-04 仅纯文本气泡渲染 markdown；媒体气泡的 caption 仍保留纯文本（避免 caption 内嵌过多结构）
const renderedHtml = computed(() => renderMarkdown(m.value.content))

// 媒体气泡 caption：把后端写入的 [图片] / [视频] / [语音] / [文件] 占位过滤掉
// 否则缩略图下方会重复出现一行「[图片]」字样
const mediaCaption = computed(() => captionForMediaContent(m.value.content))

function timeText(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

// ---- W21-01 上下文菜单状态与触发 ----
const menuOpen = ref(false)
const menuX = ref(0)
const menuY = ref(0)

// 可启用条件：未进多选模式、且有服务端 ID（optimistic pending/failed 不弹菜单）
const canShowMenu = computed(() => !props.selectMode && m.value.id != null)

const menuItems = computed(() => {
  if (!canShowMenu.value) return []
  // 联系人与闪记会话菜单项一致；UI 仅依赖事件向上冲交给 ChatView
  return [
    { key: 'copy', label: '复制', icon: '📋' },
    { key: 'forward', label: '转发', icon: '↪' },
    {
      key: 'favorite',
      label: props.favorited ? '取消收藏' : '收藏',
      icon: props.favorited ? '★' : '☆'
    },
    {
      // D1-W28-08 「保存到本地」与「下载」语义完全重复，统一为「下载」（与右上角选项、
      // FavoritesView 菜单、Android 端 R.string.action_download 保持一致）
      key: 'download',
      label: '下载',
      icon: '⬇',
      disabled: !isMedia.value || !m.value.mediaUrl
    },
    { key: 'select', label: '多选', icon: '☑' },
    { key: 'delete', label: '删除', icon: '🗑', danger: true }
  ]
})

function openMenuAt(clientX, clientY) {
  if (!canShowMenu.value) return
  menuX.value = Math.round(clientX)
  menuY.value = Math.round(clientY)
  menuOpen.value = true
}

function onContextMenu(e) {
  if (!canShowMenu.value) return
  e.preventDefault()
  openMenuAt(e.clientX, e.clientY)
}

// D1-W28-13 多选模式下点击整条消息任意区域即可 toggle 选中，
// 不再要求精确点到 select-box 复选框。
// 用 @click.capture 在捕获阶段拦截，吞掉子元素点击（PDF 预览 / 卡片打开 / 文本展开等）
// 避免与选中冲突；复选框自身的 change 事件保持不变（双重保险）。
function onRowClick(e) {
  if (!props.selectMode || m.value.id == null) return
  e.preventDefault()
  e.stopPropagation()
  emit('toggle-select', m.value.id)
}

// 触摸长按：touchstart 后计时 LONG_PRESS_MS；期间移动超过容差则取消
let longPressTimer = null
let pressStart = { x: 0, y: 0 }

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function onTouchStart(e) {
  if (!canShowMenu.value) return
  const t = e.touches && e.touches[0]
  if (!t) return
  pressStart = { x: t.clientX, y: t.clientY }
  clearLongPress()
  longPressTimer = setTimeout(() => {
    longPressTimer = null
    openMenuAt(pressStart.x, pressStart.y)
  }, LONG_PRESS_MS)
}

function onTouchMove(e) {
  if (!longPressTimer) return
  const t = e.touches && e.touches[0]
  if (!t) return
  const dx = Math.abs(t.clientX - pressStart.x)
  const dy = Math.abs(t.clientY - pressStart.y)
  if (dx > LONG_PRESS_TOLERANCE_PX || dy > LONG_PRESS_TOLERANCE_PX) {
    clearLongPress()
  }
}

function onTouchEnd() {
  clearLongPress()
}

onBeforeUnmount(() => {
  clearLongPress()
})

// 菜单项点击处理
async function onMenuSelect(key) {
  switch (key) {
    case 'copy': {
      const text = textOfMessage(m.value)
      try {
        if (navigator && navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(text)
        } else if (typeof document !== 'undefined' && document.execCommand) {
          // Fallback：只在老浏览器 / 非 https 环境生效
          const ta = document.createElement('textarea')
          ta.value = text
          ta.style.position = 'fixed'
          ta.style.opacity = '0'
          document.body.appendChild(ta)
          ta.select()
          document.execCommand('copy')
          document.body.removeChild(ta)
        }
      } catch (_e) {
        // 静默处理；ChatView 层会由其他途径上报
      }
      // 向父级丢出事件，使 ChatView 可以 toast “已复制”（避免在子组件里武断引入 useToast）
      emit('forward-single', { type: 'copied', text })
      break
    }
    case 'forward':
      emit('forward-single', { type: 'forward', message: m.value })
      break
    case 'favorite':
      emit('toggle-favorite', m.value)
      break
    case 'download':
      emit('download', m.value)
      break
    case 'select':
      emit('enter-select-with', m.value)
      break
    case 'delete':
      emit('delete', m.value)
      break
    default:
      break
  }
}
</script>

<template>
  <div
    class="bubble-row"
    :class="[mine ? 'mine' : 'other', { selectable: selectMode }]"
    @click.capture="onRowClick"
    @contextmenu="onContextMenu"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchEnd"
  >
    <label v-if="selectMode && m.id != null" class="select-box">
      <input type="checkbox" :checked="selected" @change="emit('toggle-select', m.id)" />
    </label>

    <div class="bubble-wrap">
      <div class="bubble" :class="[mine ? 'bubble-mine' : 'bubble-other', `status-${status}`]">
        <template v-if="isCard">
          <!-- D1-W25-01 卡片气泡升级：在 title 之后真实渲染媒体网格 + 文件列表，
               与 Android `MessageCompositeBinder` 的视觉密度对齐；
               summary 为空时由 buildCardSummary 兜底拼接 items 摘要。 -->
          <button
            type="button"
            class="card-clickable"
            @click="emit('open-card', m)"
            :title="'查看卡片详情'"
          >
            <p class="card-type">📇 卡片</p>
            <p v-if="m.payload.title" class="card-title">{{ m.payload.title }}</p>
            <CardMediaGrid
              v-if="cardItems.length"
              :items="cardItems"
              @open="emit('open-card', m)"
            />
            <ul v-if="cardFileItems.length" class="card-file-list">
              <li v-for="(f, i) in cardFileItems" :key="i" class="card-file-row">
                <span class="card-file-icon" aria-hidden="true">📎</span>
                <span class="card-file-name">{{ f.fileName || (f.type || 'FILE') }}</span>
              </li>
            </ul>
            <p v-if="cardSummary" class="card-summary">{{ cardSummary }}</p>
            <p class="card-hint">点击查看详情</p>
          </button>
        </template>
        <template v-else-if="isMedia">
          <MediaPreview :message="m" />
          <p v-if="mediaCaption" class="text caption">{{ mediaCaption }}</p>
        </template>
        <template v-else>
          <!-- D1-W17-04 markdown 渲染（已经过 DOMPurify，安全 v-html） -->
          <div class="text markdown-body" v-html="renderedHtml"></div>
        </template>
      </div>

      <div class="meta">
        <span class="time">{{ timeText(m.createdAt) }}</span>
        <span v-if="status === 'pending'" class="status-tag pending">发送中...</span>
        <span v-else-if="status === 'failed'" class="status-tag failed">发送失败</span>
        <!-- W21-04 永久按钮收敛：仅保留「重试」（failed 状态）。
             收藏 / 转发 / 复制 / 多选 / 删除 全部走右键/长按上下文菜单。 -->
        <button
          v-if="status === 'failed'"
          type="button"
          class="action-btn"
          @click="emit('retry', m.clientRequestId)"
        >重试</button>
        <span
          v-if="favorited && !selectMode"
          class="favored-mark"
          :title="'已收藏'"
          aria-label="已收藏"
        >★</span>
      </div>
    </div>

    <!-- W21-01 上下文菜单（Teleport 到 body，不受气泡其他样式影响） -->
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
.bubble-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}
.bubble-row.mine {
  flex-direction: row-reverse;
}
.bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: min(70%, 560px);
}
.bubble-row.mine .bubble-wrap {
  align-items: flex-end;
}

.bubble {
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}
.bubble-mine {
  background: #95EC69;
  color: #111111;
}
.bubble-other {
  background: var(--color-surface);
  color: var(--color-text-primary);
  border: 1px solid var(--color-divider);
}
.bubble.status-pending {
  opacity: 0.65;
}
.bubble.status-failed {
  border: 1px solid var(--color-danger);
}

.text {
  margin: 0;
}
.card-type {
  margin: 0 0 4px 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.card-title {
  margin: 0 0 4px 0;
  font-weight: 600;
}
.card-summary {
  margin: 6px 0 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}
/* D1-W25-01 卡片底部文件列表（FILE / VOICE / AUDIO 类 item）：
   与 Android `bindCompositeFiles` 视觉对齐，占位符 + 文件名一行 */
.card-file-list {
  list-style: none;
  margin: 6px 0 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-file-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.card-file-icon {
  font-size: 14px;
  line-height: 1;
}
.card-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.text.caption {
  margin: 6px 0 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-hint);
}
.bubble-row.mine .meta {
  justify-content: flex-end;
}
.status-tag.pending {
  color: var(--color-text-hint);
}

/* D1-W17-04 markdown 渲染样式（仅消息气泡内） */
.markdown-body {
  white-space: normal;
}
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
.markdown-body :deep(li) {
  margin: 2px 0;
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
  margin: 6px 0;
}
.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid currentColor;
  padding-left: 10px;
  margin: 4px 0;
  opacity: 0.85;
}
.markdown-body :deep(a) {
  color: inherit;
  text-decoration: underline;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  margin: 8px 0;
}

/* D1-W17-02 卡片可点击样式 */
.card-clickable {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: inherit;
  font: inherit;
}
.card-clickable:hover .card-title {
  text-decoration: underline;
}
.card-hint {
  margin: 6px 0 0 0;
  font-size: 11px;
  opacity: 0.7;
}
.status-tag.failed {
  color: var(--color-danger);
}
.action-btn {
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: 11px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.action-btn:hover {
  border-color: var(--color-border);
  color: var(--color-text-primary);
}
.action-btn.delete:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
/* W21-04 永久收藏标记：仅 favorited=true 时在气泡底部出现一个小星标，
   不占多余点击面积；取消收藏走右键/长按菜单。 */
.favored-mark {
  color: #d97706;
  font-size: 12px;
  margin-left: 2px;
}

.select-box {
  margin-top: 6px;
}
.select-box input {
  cursor: pointer;
}
</style>
