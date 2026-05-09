<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { fetchAsObjectUrl, fetchAsText } from '../api/files'
import {
  isImage, isVideo, isAudio, isVoice, isPdf, isTextLike, isOfficeDoc,
  formatFileSize, shortenFileName
} from '../utils/fileHelpers'
import { useToast } from '../composables/useToast'
import { notifyAudioPlay, notifyAudioPauseOrEnded } from '../composables/useExclusiveAudio'
import ImageLightbox from './ImageLightbox.vue'
import PdfViewerDialog from './PdfViewerDialog.vue'
import TextViewerDialog from './TextViewerDialog.vue'
import VoicePlayer from './VoicePlayer.vue'

// D1-W9 / D1-W18 媒体消息渲染：
// - image / video / audio: fetch blob → URL.createObjectURL，解决鉴权下载问题
// - pdf: 文件气泡显示「预览」按钮，点击后按需 fetch blob → iframe 全屏渲染（PdfViewerDialog）
// - 文本/代码: 文件气泡显示「预览」按钮，按需 fetch + UTF-8 解码 → <pre> 全屏（TextViewerDialog），最大 1MB
// - office: 浏览器无法本地预览，仅显示「暂不支持在线预览，请下载后查看」+ 下载入口
// - 其他文件: 仅显示文件名/大小/下载
// - 组件卸载或 objectName 变更时释放所有 blob URL，避免泄漏

const props = defineProps({
  message: { type: Object, required: true }
})

const { showError } = useToast()

const blobUrl = ref(null) // image / video / audio 主 blob
const loading = ref(false)
const failed = ref(false)

const m = computed(() => props.message || {})
const objectName = computed(() => m.value.mediaUrl || null)
const fileName = computed(() => m.value.fileName || (objectName.value ? objectName.value.split('/').pop() : '附件'))
const fileSize = computed(() => m.value.fileSize)

const detectionCtx = computed(() => ({
  mediaType: m.value.mediaType,
  fileName: fileName.value
}))

const previewKind = computed(() => {
  if (isImage(detectionCtx.value)) return 'image'
  if (isVideo(detectionCtx.value)) return 'video'
  if (isAudio(detectionCtx.value)) return 'audio'
  return 'file'
})

// D1-W27-03 语音消息：紧凑播放器布局，宽度按 mediaDuration 在 [120, 240]px 间线性映射
const isVoiceMessage = computed(() => isVoice({ mediaType: m.value.mediaType }))
const voiceDurationSec = computed(() => {
  const d = Number(m.value.mediaDuration)
  return Number.isFinite(d) && d >= 0 ? Math.round(d) : 0
})
const voiceWidthPx = computed(() => {
  // 0~60s 线性映射 120~240，超过 60s 截断到 240
  const sec = voiceDurationSec.value
  if (sec <= 0) return 160
  const ratio = Math.min(1, sec / 60)
  return Math.round(120 + ratio * 120)
})
function formatVoiceDuration(sec) {
  const s = Math.max(0, Math.floor(Number(sec) || 0))
  const mm = String(Math.floor(s / 60)).padStart(1, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

// D1-W27-03 互斥播放：监听 audio 元素的 play / pause / ended，与全局 currentAudio 协作
const audioEl = ref(null)
function onAudioPlay(ev) {
  notifyAudioPlay(ev?.target || audioEl.value)
}
function onAudioPauseOrEnded(ev) {
  notifyAudioPauseOrEnded(ev?.target || audioEl.value)
}

const isPdfFile = computed(() => isPdf(detectionCtx.value))
const isTextFile = computed(() => !isPdfFile.value && !isOfficeDoc(detectionCtx.value) && isTextLike(detectionCtx.value))
const isOfficeFile = computed(() => isOfficeDoc(detectionCtx.value))

const isLocalBlob = computed(
  () => typeof objectName.value === 'string' && objectName.value.startsWith('blob:')
)

// 图标统一走 emoji，避免乱码（之前残留过 '?'）
const fileIcon = computed(() => {
  if (isPdfFile.value) return '📕'
  if (isOfficeFile.value) return '📊'
  if (isTextFile.value) return '📝'
  return '📄'
})

// === image / video / audio blob 加载 ===
async function loadMedia() {
  if (isLocalBlob.value) {
    blobUrl.value = objectName.value
    return
  }
  if (!objectName.value) return
  if (previewKind.value === 'file') return // 文件类不预加载
  loading.value = true
  failed.value = false
  try {
    blobUrl.value = await fetchAsObjectUrl(objectName.value)
  } catch (_e) {
    failed.value = true
  } finally {
    loading.value = false
  }
}

// === 图片 lightbox ===
const lightboxOpen = ref(false)
function openLightbox() {
  if (blobUrl.value) lightboxOpen.value = true
}
function closeLightbox() {
  lightboxOpen.value = false
}

// === 视频全屏 ===
const videoEl = ref(null)
function enterVideoFullscreen() {
  const el = videoEl.value
  if (!el) return
  // iOS Safari 专用 API
  if (typeof el.webkitEnterFullscreen === 'function') {
    try { el.webkitEnterFullscreen(); return } catch (_e) { /* fallthrough */ }
  }
  if (typeof el.requestFullscreen === 'function') {
    el.requestFullscreen().catch(() => { /* ignore */ })
  }
}

// === PDF dialog（按需懒加载 blob，避免 PDF 消息一上来就消耗 token 拉鉴权字节） ===
const pdfBlobUrl = ref(null)
const pdfDialogOpen = ref(false)
const pdfLoading = ref(false)

async function openPdfPreview() {
  if (!objectName.value) {
    showError('文件信息缺失')
    return
  }
  if (pdfBlobUrl.value) {
    pdfDialogOpen.value = true
    return
  }
  pdfLoading.value = true
  try {
    pdfBlobUrl.value = await fetchAsObjectUrl(objectName.value)
    pdfDialogOpen.value = true
  } catch (e) {
    showError(e?.serverMessage || e?.message || 'PDF 加载失败')
  } finally {
    pdfLoading.value = false
  }
}
function closePdfPreview() {
  pdfDialogOpen.value = false
}
function disposePdfBlob() {
  if (pdfBlobUrl.value) {
    try { URL.revokeObjectURL(pdfBlobUrl.value) } catch (_e) { /* ignore */ }
  }
  pdfBlobUrl.value = null
}

// === 文本/代码 dialog ===
// 上限 1MB：超过这个大小不在浏览器内全文加载，提示用户下载
const TEXT_PREVIEW_MAX_BYTES = 1024 * 1024
const textDialogOpen = ref(false)
const textContent = ref('')
const textLoading = ref(false)
const textError = ref('')

async function openTextPreview() {
  if (!objectName.value) {
    showError('文件信息缺失')
    return
  }
  if (fileSize.value && Number(fileSize.value) > TEXT_PREVIEW_MAX_BYTES) {
    showError('文件较大（>1MB），建议下载后查看')
    return
  }
  if (textContent.value) {
    textDialogOpen.value = true
    return
  }
  textLoading.value = true
  textError.value = ''
  textDialogOpen.value = true
  try {
    textContent.value = await fetchAsText(objectName.value)
  } catch (e) {
    textError.value = e?.serverMessage || e?.message || '文本加载失败'
  } finally {
    textLoading.value = false
  }
}
// D1-W28-08 文件气泡整体点击预览：仅 PDF / 纯文本可触发；
// office / 未识别类型沉默不响应，由 hint 文本告知用户走右键下载。
const isClickablePreview = computed(
  () => previewKind.value === 'file' && (isPdfFile.value || isTextFile.value)
)
const filePreviewLoading = computed(() => {
  if (isPdfFile.value) return pdfLoading.value
  if (isTextFile.value) return textLoading.value
  return false
})
const filePreviewTitle = computed(() => {
  if (isPdfFile.value) return '点击预览 PDF（下载请右键消息）'
  if (isTextFile.value) return '点击预览文本（下载请右键消息）'
  if (isOfficeFile.value) return '暂不支持在线预览，请右键消息选择「下载」'
  return ''
})
function onFileBubbleClick() {
  if (!isClickablePreview.value) return
  if (filePreviewLoading.value) return
  if (isPdfFile.value) {
    openPdfPreview()
  } else if (isTextFile.value) {
    openTextPreview()
  }
}

function closeTextPreview() {
  textDialogOpen.value = false
}

// D1-W28-08 内嵌下载入口已删除：下载统一收到 MessageBubble 右键菜单（消息层级），
// FavoritesView 在自己的右键菜单里处理收藏项的下载，二者都直接调用 `api/files#triggerDownload`，
// 不再走 MediaPreview 内部按钮。

// === dispose ===
function disposeBlob() {
  if (blobUrl.value && !isLocalBlob.value) {
    try { URL.revokeObjectURL(blobUrl.value) } catch (_e) { /* ignore */ }
  }
  blobUrl.value = null
}

onMounted(() => {
  loadMedia()
})

onBeforeUnmount(() => {
  // D1-W27-03 互斥播放：组件销毁前若自身正在播放，注销全局当前 audio
  if (audioEl.value) {
    notifyAudioPauseOrEnded(audioEl.value)
  }
  disposeBlob()
  disposePdfBlob()
})

// objectName 变更（比如 optimistic 消息被替换为 server 消息）时重新加载并清缓存
watch(
  () => objectName.value,
  () => {
    disposeBlob()
    disposePdfBlob()
    textContent.value = ''
    textError.value = ''
    loadMedia()
  }
)
</script>

<template>
  <div class="media-preview" :class="`kind-${previewKind}`">
    <template v-if="previewKind === 'image'">
      <div v-if="loading" class="media-stub">图片加载中...</div>
      <img
        v-else-if="blobUrl"
        :src="blobUrl"
        :alt="fileName"
        class="media-image"
        @click="openLightbox"
      />
      <div v-else class="media-stub failed">图片加载失败 · {{ fileName }}</div>
    </template>

    <template v-else-if="previewKind === 'video'">
      <div v-if="loading" class="media-stub">视频加载中...</div>
      <div v-else-if="blobUrl" class="media-video-wrap">
        <video
          ref="videoEl"
          controls
          :src="blobUrl"
          class="media-video"
          preload="metadata"
          @click.stop
        ></video>
        <button
          type="button"
          class="video-fullscreen-btn"
          title="全屏播放"
          aria-label="全屏播放"
          @click="enterVideoFullscreen"
        >⛶</button>
      </div>
      <div v-else class="media-stub failed">视频加载失败 · {{ fileName }}</div>
    </template>

    <template v-else-if="previewKind === 'audio'">
      <div v-if="loading" class="media-stub">{{ isVoiceMessage ? '语音加载中...' : '音频加载中...' }}</div>
      <!-- D1-W28-03 VOICE 专属：自绘 VoicePlayer，去除原生 audio controls 黑框；
           普通 audio 文件继续走原生 audio controls -->
      <template v-else-if="blobUrl">
        <VoicePlayer
          v-if="isVoiceMessage"
          :src="blobUrl"
          :duration="voiceDurationSec"
          :width-px="voiceWidthPx"
        />
        <audio
          v-else
          ref="audioEl"
          controls
          :src="blobUrl"
          class="media-audio"
          preload="metadata"
          @play="onAudioPlay"
          @pause="onAudioPauseOrEnded"
          @ended="onAudioPauseOrEnded"
        ></audio>
      </template>
      <div v-else class="media-stub failed">{{ isVoiceMessage ? '语音加载失败' : '音频加载失败' }} · {{ fileName }}</div>
    </template>

    <!-- D1-W28-08 文件气泡可整体点击预览：PDF / 文本走对应 dialog；
         office / 其他不可预览类型保持 disabled 视觉，只展示文件名 + hint。
         下载入口已统一收到 MessageBubble 右键菜单（D1-W21），这里不再放显式按钮。 -->
    <div
      v-else
      class="media-file"
      :class="{ 'is-clickable': isClickablePreview, 'is-loading': filePreviewLoading }"
      :title="filePreviewTitle"
      :role="isClickablePreview ? 'button' : null"
      :tabindex="isClickablePreview ? 0 : null"
      @click="onFileBubbleClick"
      @keydown.enter.prevent="onFileBubbleClick"
      @keydown.space.prevent="onFileBubbleClick"
    >
      <span class="file-icon" aria-hidden="true">{{ fileIcon }}</span>
      <div class="file-meta">
        <p class="file-name">{{ shortenFileName(fileName, 36) }}</p>
        <p v-if="fileSize" class="file-size">{{ formatFileSize(fileSize) }}</p>
        <p v-if="isOfficeFile" class="file-hint">暂不支持在线预览，可右键「下载」后查看</p>
        <p v-else-if="filePreviewLoading" class="file-hint">加载中...</p>
        <p v-else-if="isClickablePreview" class="file-hint">点击预览，右键「下载」保存到本地</p>
      </div>
    </div>

    <!-- D1-W18-01 图片全屏预览 -->
    <ImageLightbox
      :open="lightboxOpen"
      :src="blobUrl || ''"
      :alt="fileName"
      @close="closeLightbox"
    />

    <!-- D1-W18-02 PDF 内嵌预览 -->
    <PdfViewerDialog
      :open="pdfDialogOpen"
      :blob-url="pdfBlobUrl || ''"
      :file-name="fileName"
      @close="closePdfPreview"
    />

    <!-- D1-W18 文本/代码预览 -->
    <TextViewerDialog
      :open="textDialogOpen"
      :text="textContent"
      :file-name="fileName"
      :loading="textLoading"
      :error-message="textError"
      @close="closeTextPreview"
    />
  </div>
</template>

<style scoped>
.media-preview {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.media-stub {
  padding: 10px 12px;
  background: var(--color-bg);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-text-hint);
}
.media-stub.failed {
  color: var(--color-danger);
}

.media-image {
  max-width: 100%;
  max-height: 320px;
  border-radius: var(--radius-sm);
  display: block;
  cursor: zoom-in;
  background: var(--color-bg);
}
.media-video-wrap {
  position: relative;
  display: inline-block;
  max-width: 100%;
}
.media-video {
  max-width: 100%;
  max-height: 320px;
  border-radius: var(--radius-sm);
  background: #000;
  display: block;
}
.video-fullscreen-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.15s;
}
.media-video-wrap:hover .video-fullscreen-btn {
  opacity: 1;
}
.video-fullscreen-btn:hover {
  background: rgba(0, 0, 0, 0.75);
}
.media-audio {
  width: 260px;
  max-width: 100%;
}
/* D1-W28-03 VOICE 紧凑播放器已迁移到独立组件 VoicePlayer，原 .voice-bubble 样式删除 */

.media-file {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: var(--color-bg);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-sm);
  min-width: 200px;
  max-width: 320px;
  /* 默认非可点击（office / unknown）：保持 default 光标，避免误导 */
}
/* D1-W28-08 PDF / 纯文本：整块可点击预览 */
.media-file.is-clickable {
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.media-file.is-clickable:hover {
  border-color: var(--color-primary);
}
.media-file.is-clickable:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.media-file.is-loading {
  cursor: progress;
  opacity: 0.7;
}
.file-icon {
  font-size: 24px;
}
.file-meta {
  min-width: 0;
}
.file-name {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  word-break: break-word;
}
.file-size {
  margin: 2px 0 0 0;
  font-size: 11px;
  color: var(--color-text-hint);
}
.file-hint {
  margin: 4px 0 0 0;
  font-size: 11px;
  color: var(--color-text-hint);
  font-style: italic;
}

/* D1-W28-08 .media-actions / .download-btn / .preview-btn 已删除：
   下载与预览入口分别迁到右键菜单与文件气泡整体点击。 */
</style>
