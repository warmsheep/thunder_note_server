<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'

// D1-W18-01 图片全屏 lightbox
// 输入：src（blob URL 或普通 URL）、alt（无障碍 alt 文案）、open
// 行为：
// - ESC 或点击背景关闭
// - 双击在 1× 与 2× 之间切换
// - 滚轮：缩放（0.5 ~ 5×）
// - 鼠标拖动：scale > 1 时平移图片
// - 关闭时复位 scale/offset
//
// 注：不在这里释放 blob URL，由父组件 MediaPreview 在 onBeforeUnmount 时统一释放，
// 这里只是查看入口。

const props = defineProps({
  open: { type: Boolean, default: false },
  src: { type: String, default: '' },
  alt: { type: String, default: '' }
})
const emit = defineEmits(['close'])

const scale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0, ox: 0, oy: 0 })

const MIN_SCALE = 0.5
const MAX_SCALE = 5

const transformStyle = computed(() => ({
  transform: `translate(${offsetX.value}px, ${offsetY.value}px) scale(${scale.value})`,
  cursor: scale.value > 1 ? (dragging.value ? 'grabbing' : 'grab') : 'zoom-in'
}))

function reset() {
  scale.value = 1
  offsetX.value = 0
  offsetY.value = 0
  dragging.value = false
}

function close() {
  reset()
  emit('close')
}

function onWheel(e) {
  e.preventDefault()
  const delta = -e.deltaY
  const factor = delta > 0 ? 1.1 : 1 / 1.1
  const next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale.value * factor))
  scale.value = next
  if (next === 1) {
    offsetX.value = 0
    offsetY.value = 0
  }
}

function onDoubleClick() {
  if (scale.value === 1) {
    scale.value = 2
  } else {
    reset()
  }
}

function onMouseDown(e) {
  if (scale.value <= 1) return
  dragging.value = true
  dragStart.value = {
    x: e.clientX,
    y: e.clientY,
    ox: offsetX.value,
    oy: offsetY.value
  }
}

function onMouseMove(e) {
  if (!dragging.value) return
  offsetX.value = dragStart.value.ox + (e.clientX - dragStart.value.x)
  offsetY.value = dragStart.value.oy + (e.clientY - dragStart.value.y)
}

function onMouseUp() {
  dragging.value = false
}

function onKey(e) {
  if (!props.open) return
  if (e.key === 'Escape') close()
  else if (e.key === '+' || e.key === '=') {
    scale.value = Math.min(MAX_SCALE, scale.value * 1.2)
  } else if (e.key === '-' || e.key === '_') {
    scale.value = Math.max(MIN_SCALE, scale.value / 1.2)
    if (scale.value === 1) { offsetX.value = 0; offsetY.value = 0 }
  } else if (e.key === '0') {
    reset()
  }
}

watch(() => props.open, (v) => {
  if (v) {
    reset()
    if (typeof window !== 'undefined') {
      window.addEventListener('keydown', onKey)
    }
  } else if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey)
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey)
  }
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="lightbox-overlay"
      role="dialog"
      aria-modal="true"
      :aria-label="alt || '图片预览'"
      @click.self="close"
      @mousemove="onMouseMove"
      @mouseup="onMouseUp"
      @mouseleave="onMouseUp"
      @wheel.passive.prevent="onWheel"
    >
      <button type="button" class="lightbox-close" @click="close" aria-label="关闭">×</button>

      <div class="lightbox-toolbar" @click.stop>
        <button type="button" class="tool-btn" title="缩小 ( - )" @click="scale = Math.max(MIN_SCALE, scale / 1.2)">－</button>
        <span class="scale-text">{{ Math.round(scale * 100) }}%</span>
        <button type="button" class="tool-btn" title="放大 ( + )" @click="scale = Math.min(MAX_SCALE, scale * 1.2)">＋</button>
        <button type="button" class="tool-btn" title="复位 ( 0 )" @click="reset">复位</button>
      </div>

      <img
        v-if="src"
        :src="src"
        :alt="alt"
        class="lightbox-image"
        :style="transformStyle"
        draggable="false"
        @dblclick="onDoubleClick"
        @mousedown.prevent="onMouseDown"
      />
    </div>
  </Teleport>
</template>

<style scoped>
.lightbox-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9000;
  overflow: hidden;
  user-select: none;
}
.lightbox-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #ffffff;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s;
}
.lightbox-close:hover { background: rgba(255, 255, 255, 0.3); }

.lightbox-toolbar {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 24px;
  z-index: 1;
}
.tool-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
}
.tool-btn:hover { background: rgba(255, 255, 255, 0.25); }
.scale-text {
  color: #ffffff;
  font-size: 12px;
  min-width: 48px;
  text-align: center;
}

.lightbox-image {
  max-width: 90vw;
  max-height: 88vh;
  transition: transform 0.05s linear;
  pointer-events: auto;
  will-change: transform;
}
</style>
