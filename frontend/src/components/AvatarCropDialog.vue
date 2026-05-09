<script setup>
import { ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'

// D1-W23-02 头像裁剪对话框
//
// 用原生 canvas 实现 1:1 方形裁剪（CSS 外观呈圆形预览）：
//   - 不依赖第三方 cropper 库（保持打包体积）
//   - 用户操作：鼠标拖动 / 触摸拖动 → 移动图片；滚轮 / +/- 按钮 → 缩放
//   - 裁剪输出：固定 512×512 像素的 JPEG Blob，通过 'confirm' 事件向父级返回
//
// 逻辑模型：
//   - CROP_SIZE = 300px：CSS 裁剪框（圆形 overlay 直径）
//   - OUTPUT_SIZE = 512px：最终输出图的宽高
//   - imgW/H、scale、offsetX/Y：图像在 canvas 坐标系中的位置
//     裁剪时把 (0,0)~(CROP_SIZE,CROP_SIZE) 反算回图片像素坐标 → 画到输出 canvas

const CROP_SIZE = 300
const OUTPUT_SIZE = 512

const props = defineProps({
  open: { type: Boolean, default: false },
  // File / Blob；父级负责传入原始图像
  source: { type: [File, Blob], default: null }
})

const emit = defineEmits(['confirm', 'cancel', 'update:open'])

const canvasEl = ref(null)
const sourceUrl = ref('')
const imageEl = ref(null) // HTMLImageElement
const imgNaturalW = ref(0)
const imgNaturalH = ref(0)
const scale = ref(1)
const minScale = ref(1)
const maxScale = ref(5)
const offsetX = ref(0)
const offsetY = ref(0)
const busy = ref(false)
const errorMessage = ref('')

const canConfirm = computed(() => !busy.value && imgNaturalW.value > 0 && imgNaturalH.value > 0)

async function resetState() {
  if (sourceUrl.value) {
    try {
      URL.revokeObjectURL(sourceUrl.value)
    } catch (_e) { /* ignore */ }
  }
  sourceUrl.value = ''
  imageEl.value = null
  imgNaturalW.value = 0
  imgNaturalH.value = 0
  scale.value = 1
  minScale.value = 1
  maxScale.value = 5
  offsetX.value = 0
  offsetY.value = 0
  errorMessage.value = ''
  busy.value = false
}

async function loadSource() {
  await resetState()
  if (!props.source) return
  const url = URL.createObjectURL(props.source)
  sourceUrl.value = url
  await new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      imageEl.value = img
      imgNaturalW.value = img.naturalWidth || img.width
      imgNaturalH.value = img.naturalHeight || img.height
      // 初始 scale：让短边刚好覆盖裁剪框
      const fitScale = Math.max(
        CROP_SIZE / imgNaturalW.value,
        CROP_SIZE / imgNaturalH.value
      )
      minScale.value = fitScale
      scale.value = fitScale
      // 居中放置
      offsetX.value = (CROP_SIZE - imgNaturalW.value * scale.value) / 2
      offsetY.value = (CROP_SIZE - imgNaturalH.value * scale.value) / 2
      clampOffsets()
      redraw()
      resolve()
    }
    img.onerror = () => {
      errorMessage.value = '无法加载图片'
      reject(new Error('image load failed'))
    }
    img.src = url
  }).catch(() => {})
}

function clampOffsets() {
  // 图像当前缩放后尺寸
  const w = imgNaturalW.value * scale.value
  const h = imgNaturalH.value * scale.value
  // 保证图像至少覆盖 CROP_SIZE 框：left ∈ [CROP_SIZE - w, 0]
  const minX = CROP_SIZE - w
  const minY = CROP_SIZE - h
  if (offsetX.value > 0) offsetX.value = 0
  if (offsetX.value < minX) offsetX.value = minX
  if (offsetY.value > 0) offsetY.value = 0
  if (offsetY.value < minY) offsetY.value = minY
}

function redraw() {
  const canvas = canvasEl.value
  if (!canvas || !imageEl.value) return
  canvas.width = CROP_SIZE
  canvas.height = CROP_SIZE
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, CROP_SIZE, CROP_SIZE)
  ctx.fillStyle = '#000'
  ctx.fillRect(0, 0, CROP_SIZE, CROP_SIZE)
  ctx.drawImage(
    imageEl.value,
    offsetX.value,
    offsetY.value,
    imgNaturalW.value * scale.value,
    imgNaturalH.value * scale.value
  )
}

// ----- 拖动 -----
let dragActive = false
let dragStart = { x: 0, y: 0 }
let dragOrigin = { x: 0, y: 0 }

function onPointerDown(e) {
  if (!imageEl.value) return
  dragActive = true
  const p = pointerCoord(e)
  dragStart = { x: p.x, y: p.y }
  dragOrigin = { x: offsetX.value, y: offsetY.value }
  // 捕获后续 move/up，保证离开 canvas 也能跟踪
  if (e.target && typeof e.target.setPointerCapture === 'function' && e.pointerId != null) {
    try { e.target.setPointerCapture(e.pointerId) } catch (_err) { /* ignore */ }
  }
}
function onPointerMove(e) {
  if (!dragActive) return
  const p = pointerCoord(e)
  offsetX.value = dragOrigin.x + (p.x - dragStart.x)
  offsetY.value = dragOrigin.y + (p.y - dragStart.y)
  clampOffsets()
  redraw()
}
function onPointerUp() {
  dragActive = false
}
function pointerCoord(e) {
  if (e.touches && e.touches[0]) return { x: e.touches[0].clientX, y: e.touches[0].clientY }
  return { x: e.clientX, y: e.clientY }
}

// ----- 缩放 -----
function applyScale(nextScale) {
  const s = Math.max(minScale.value, Math.min(maxScale.value, nextScale))
  if (s === scale.value) return
  // 以裁剪框中心为锚点缩放：保持中心像素不动
  const cx = CROP_SIZE / 2
  const cy = CROP_SIZE / 2
  const ratio = s / scale.value
  offsetX.value = cx - (cx - offsetX.value) * ratio
  offsetY.value = cy - (cy - offsetY.value) * ratio
  scale.value = s
  clampOffsets()
  redraw()
}
function onWheel(e) {
  if (!imageEl.value) return
  e.preventDefault()
  const delta = e.deltaY > 0 ? 0.9 : 1.1
  applyScale(scale.value * delta)
}
function zoomIn() { applyScale(scale.value * 1.15) }
function zoomOut() { applyScale(scale.value / 1.15) }

// ----- 输出 -----
async function confirm() {
  if (!canConfirm.value) return
  busy.value = true
  try {
    const out = document.createElement('canvas')
    out.width = OUTPUT_SIZE
    out.height = OUTPUT_SIZE
    const ctx = out.getContext('2d')
    // 把 CROP_SIZE × CROP_SIZE 的裁剪框放大到 OUTPUT_SIZE × OUTPUT_SIZE
    const ratio = OUTPUT_SIZE / CROP_SIZE
    ctx.drawImage(
      imageEl.value,
      offsetX.value * ratio,
      offsetY.value * ratio,
      imgNaturalW.value * scale.value * ratio,
      imgNaturalH.value * scale.value * ratio
    )
    const blob = await new Promise((resolve, reject) => {
      out.toBlob(
        (b) => (b ? resolve(b) : reject(new Error('toBlob returned null'))),
        'image/jpeg',
        0.9
      )
    })
    emit('confirm', blob)
    emit('update:open', false)
  } catch (e) {
    errorMessage.value = e?.message || '裁剪失败'
  } finally {
    busy.value = false
  }
}

function cancel() {
  if (busy.value) return
  emit('cancel')
  emit('update:open', false)
}

watch(
  () => props.open,
  async (next) => {
    if (next) {
      await nextTick()
      await loadSource()
    } else {
      await resetState()
    }
  }
)

onBeforeUnmount(() => {
  if (sourceUrl.value) {
    try { URL.revokeObjectURL(sourceUrl.value) } catch (_e) { /* ignore */ }
  }
})
</script>

<template>
  <div v-if="open" class="modal-overlay" @click.self="cancel">
    <div class="modal" role="dialog" aria-label="裁剪头像">
      <header class="modal-header">
        <h2 class="modal-title">裁剪头像</h2>
        <button type="button" class="modal-close" :disabled="busy" @click="cancel">×</button>
      </header>

      <div class="modal-body">
        <div
          class="crop-area"
          :style="{ width: CROP_SIZE + 'px', height: CROP_SIZE + 'px' }"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
          @wheel="onWheel"
        >
          <canvas ref="canvasEl" class="crop-canvas"></canvas>
          <!-- 圆形遮罩：用 box-shadow 向外溢出“挖”出圆形视口 -->
          <div class="crop-mask" aria-hidden="true"></div>
        </div>

        <div class="zoom-bar">
          <button type="button" class="btn-icon" :disabled="busy" @click="zoomOut" aria-label="缩小">−</button>
          <input
            type="range"
            class="zoom-range"
            :min="minScale"
            :max="maxScale"
            :step="0.01"
            :value="scale"
            :disabled="busy"
            @input="applyScale(parseFloat($event.target.value))"
          />
          <button type="button" class="btn-icon" :disabled="busy" @click="zoomIn" aria-label="放大">+</button>
        </div>

        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
        <p v-else class="hint">拖动调整位置，滚轮 / 拖条 缩放；输出 512×512</p>
      </div>

      <footer class="modal-footer">
        <button type="button" class="btn-secondary" :disabled="busy" @click="cancel">取消</button>
        <button type="button" class="btn-primary" :disabled="!canConfirm" @click="confirm">
          {{ busy ? '处理中...' : '确认' }}
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1001;
  padding: 16px;
}
.modal {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-divider);
}
.modal-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.modal-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  color: var(--color-text-secondary);
}

.modal-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px;
}
.crop-area {
  position: relative;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: #000;
  touch-action: none;
  user-select: none;
  cursor: grab;
}
.crop-area:active {
  cursor: grabbing;
}
.crop-canvas {
  display: block;
  width: 100%;
  height: 100%;
}
.crop-mask {
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: 50%;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.55);
}
.zoom-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.zoom-range {
  flex: 1;
}
.btn-icon {
  width: 30px;
  height: 30px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-surface);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.btn-icon:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.btn-icon:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 10px 16px;
  border-top: 1px solid var(--color-divider);
}
.btn-primary {
  padding: 7px 16px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  cursor: pointer;
  font-size: 14px;
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}
.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.btn-secondary {
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-primary);
  cursor: pointer;
  font-size: 14px;
}
.btn-secondary:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.btn-secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.error-text {
  margin: 0;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger);
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
  text-align: center;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-hint);
  text-align: center;
}
</style>
