<script setup>
import { computed } from 'vue'

// D1-W6 单条消息气泡
// - 自己发送的右对齐绿色气泡，对方/系统左对齐灰色气泡
// - 文本：渲染 content；卡片：用 payload 展示标题+摘要（W6-09 只读）；媒体：占位（W8 实现）
// - 选择模式：左侧多选 checkbox；非选择模式：hover 显示删除入口

const props = defineProps({
  message: { type: Object, required: true },
  mine: { type: Boolean, default: false },
  selectMode: { type: Boolean, default: false },
  selected: { type: Boolean, default: false },
  favorited: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle-select', 'delete', 'retry', 'toggle-favorite'])

const m = computed(() => props.message || {})

const status = computed(() => m.value.__status || 'sent')

const isCard = computed(() => Boolean(m.value.payload && m.value.payload.cardType))
const isMedia = computed(() => Boolean(m.value.mediaType))

function timeText(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}
</script>

<template>
  <div class="bubble-row" :class="[mine ? 'mine' : 'other', { selectable: selectMode }]">
    <label v-if="selectMode && m.id != null" class="select-box">
      <input type="checkbox" :checked="selected" @change="emit('toggle-select', m.id)" />
    </label>

    <div class="bubble-wrap">
      <div class="bubble" :class="[mine ? 'bubble-mine' : 'bubble-other', `status-${status}`]">
        <template v-if="isCard">
          <p class="card-type">📇 {{ m.payload.cardType }}</p>
          <p v-if="m.payload.title" class="card-title">{{ m.payload.title }}</p>
          <p v-if="m.payload.summary" class="card-summary">{{ m.payload.summary }}</p>
        </template>
        <template v-else-if="isMedia">
          <MediaPreview :message="m" />
          <p v-if="m.content" class="text caption">{{ m.content }}</p>
        </template>
        <template v-else>
          <p class="text">{{ m.content }}</p>
        </template>
      </div>

      <div class="meta">
        <span class="time">{{ timeText(m.createdAt) }}</span>
        <span v-if="status === 'pending'" class="status-tag pending">发送中...</span>
        <span v-else-if="status === 'failed'" class="status-tag failed">发送失败</span>
        <button
          v-if="status === 'failed'"
          type="button"
          class="action-btn"
          @click="emit('retry', m.clientRequestId)"
        >重试</button>
        <button
          v-if="!selectMode && m.id != null"
          type="button"
          class="action-btn"
          :class="{ favored: favorited }"
          :aria-pressed="favorited ? 'true' : 'false'"
          :title="favorited ? '取消收藏' : '收藏'"
          @click="emit('toggle-favorite', m)"
        >{{ favorited ? '★ 已收藏' : '☆ 收藏' }}</button>
        <button
          v-if="!selectMode && m.id != null"
          type="button"
          class="action-btn delete"
          @click="emit('delete', m)"
        >删除</button>
      </div>
    </div>
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
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
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
.action-btn.favored {
  color: #d97706;
  border-color: #fde68a;
  background: #fffbeb;
}
.action-btn.favored:hover {
  border-color: #d97706;
}

.select-box {
  margin-top: 6px;
}
.select-box input {
  cursor: pointer;
}
</style>
