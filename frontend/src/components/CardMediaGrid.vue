<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { fetchAsObjectUrl } from '../api/files'

// D1-W25-01 卡片气泡内的媒体缩略图网格（图片 / 视频）
//
// 与 Android `MessageCompositeBinder.bindCompositeGrid()` 对齐：
//   - 1 项 → 1 列大图；2 / 4 项 → 2 列；3 / 5 / 6 / 7 / 8 / 9 项 → 3 列
//   - 视频项叠播放图标，缩略图优先 thumbnailUrl，没有再退到 url
//   - 上限 9 项；多余的不渲染
//
// 字段约定（CardItem 在 MessageResponse.payload.items 里的真实字段）：
//   - type: IMAGE / VIDEO / FILE / TEXT / VOICE / AUDIO（大小写均接受）
//   - url: 主对象名（objectName）
//   - thumbnailUrl: 视频缩略图对象名（仅 VIDEO 有）
//
// 性能：每个 thumbnail 会按需 fetch 鉴权 blob URL；组件卸载或 items 变更时统一 revoke。
// 列表里若有几十条卡片消息，每条 9 张图，浏览器仍会发起总数较多的请求；
// 进一步优化（IntersectionObserver 懒加载）留给后续阶段处理。

const MAX_GRID = 9

const props = defineProps({
  items: { type: Array, default: () => [] }
})

const emit = defineEmits(['open'])

// 仅图片/视频类参与网格
const mediaItems = computed(() => {
  return (Array.isArray(props.items) ? props.items : [])
    .filter((it) => {
      const t = (it && it.type ? String(it.type) : '').toUpperCase()
      return t === 'IMAGE' || t === 'VIDEO'
    })
    .slice(0, MAX_GRID)
})

const cols = computed(() => {
  const n = mediaItems.value.length
  if (n <= 1) return 1
  if (n === 2 || n === 4) return 2
  return 3
})

// thumbBlob[index] = blob URL；fetch 失败为空字符串（占位）
const thumbBlobs = ref([])

function thumbObjectName(item) {
  if (!item) return null
  const t = (item.type || '').toUpperCase()
  if (t === 'VIDEO') {
    return item.thumbnailUrl || item.url || null
  }
  return item.url || null
}

function revokeAll() {
  for (const url of thumbBlobs.value) {
    if (url && typeof url === 'string' && url.startsWith('blob:')) {
      try { URL.revokeObjectURL(url) } catch (_e) { /* ignore */ }
    }
  }
  thumbBlobs.value = []
}

async function loadThumbnails() {
  revokeAll()
  const list = mediaItems.value
  // 先填占位避免抖动
  thumbBlobs.value = list.map(() => '')
  await Promise.all(list.map(async (item, idx) => {
    const obj = thumbObjectName(item)
    if (!obj) return
    // 本地 blob:（optimistic 阶段还没上传完成）直接用
    if (typeof obj === 'string' && obj.startsWith('blob:')) {
      thumbBlobs.value[idx] = obj
      return
    }
    try {
      const url = await fetchAsObjectUrl(obj)
      // items 切换后旧请求可能晚到；用长度兜底避免越界
      if (idx < thumbBlobs.value.length) {
        thumbBlobs.value[idx] = url
      } else {
        try { URL.revokeObjectURL(url) } catch (_e) { /* ignore */ }
      }
    } catch (_e) {
      // 失败就保留空字符串占位
    }
  }))
}

watch(
  () => props.items,
  () => { loadThumbnails() }
)

onMounted(() => { loadThumbnails() })
onBeforeUnmount(() => { revokeAll() })

function isVideo(item) {
  return (item.type || '').toUpperCase() === 'VIDEO'
}

function onClickThumb(index) {
  emit('open', { index, item: mediaItems.value[index] })
}
</script>

<template>
  <div
    v-if="mediaItems.length"
    class="card-media-grid"
    :class="`cols-${cols}`"
    :data-count="mediaItems.length"
  >
    <button
      v-for="(item, idx) in mediaItems"
      :key="idx"
      type="button"
      class="card-media-cell"
      :class="{ video: isVideo(item) }"
      :title="item.fileName || ''"
      @click.stop="onClickThumb(idx)"
    >
      <img
        v-if="thumbBlobs[idx]"
        :src="thumbBlobs[idx]"
        :alt="item.fileName || ''"
        loading="lazy"
        draggable="false"
      />
      <span v-else class="placeholder">📷</span>
      <span v-if="isVideo(item)" class="play-overlay" aria-hidden="true">▶</span>
    </button>
  </div>
</template>

<style scoped>
.card-media-grid {
  display: grid;
  gap: 4px;
  margin-top: 6px;
  width: 100%;
}
.card-media-grid.cols-1 {
  grid-template-columns: 1fr;
}
.card-media-grid.cols-2 {
  grid-template-columns: 1fr 1fr;
}
.card-media-grid.cols-3 {
  grid-template-columns: 1fr 1fr 1fr;
}
.card-media-cell {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  border: 1px solid var(--color-divider);
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.04);
  overflow: hidden;
  padding: 0;
  cursor: pointer;
}
/* 单张图允许放大些，与 Android `chat_media_preview_width` 视觉对齐 */
.card-media-grid.cols-1 .card-media-cell {
  aspect-ratio: 4 / 3;
  max-height: 220px;
}
.card-media-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.card-media-cell .placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: var(--color-text-hint);
}
.card-media-cell .play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 22px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
  pointer-events: none;
}
.card-media-cell:hover {
  filter: brightness(0.96);
}
</style>
