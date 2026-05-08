<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { fetchAsObjectUrl, triggerDownload } from '../api/files'
import { isImage, isVideo, isAudio, formatFileSize, shortenFileName } from '../utils/fileHelpers'
import { useToast } from '../composables/useToast'

// D1-W9 媒体消息渲染：
// - 图片/视频/音频通过 fetch blob → URL.createObjectURL 解决鉴权下载问题
// - 其他类型只展示文件名 + 大小 + 下载按钮（W9-05 兜底）
// - 组件卸载时释放 blob URL，避免内存泄漏
// - 失败时降级为下载入口

const props = defineProps({
  message: { type: Object, required: true }
})

const { showError } = useToast()

const blobUrl = ref(null)
const loading = ref(false)
const failed = ref(false)
const downloading = ref(false)

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

const isLocalBlob = computed(() => typeof objectName.value === 'string' && objectName.value.startsWith('blob:'))

async function loadMedia() {
  // 本地 optimistic 消息可能已经把 blob: URL 直接放到 mediaUrl 里
  if (isLocalBlob.value) {
    blobUrl.value = objectName.value
    return
  }
  if (!objectName.value) {
    return
  }
  if (previewKind.value === 'file') {
    return // 文件类不预加载
  }
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

function disposeBlob() {
  if (blobUrl.value && !isLocalBlob.value) {
    try { URL.revokeObjectURL(blobUrl.value) } catch (_e) { /* ignore */ }
  }
  blobUrl.value = null
}

async function handleDownload() {
  if (!objectName.value) {
    showError('文件信息缺失')
    return
  }
  downloading.value = true
  try {
    await triggerDownload(objectName.value, fileName.value)
  } catch (e) {
    showError(e?.serverMessage || e?.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

onMounted(() => {
  loadMedia()
})

onBeforeUnmount(() => {
  disposeBlob()
})

// objectName 变更（比如 optimistic 消息被替换为 server 消息）时重新加载
watch(
  () => objectName.value,
  () => {
    disposeBlob()
    loadMedia()
  }
)
</script>

<template>
  <div class="media-preview" :class="`kind-${previewKind}`">
    <template v-if="previewKind === 'image'">
      <div v-if="loading" class="media-stub">图片加载中...</div>
      <img v-else-if="blobUrl" :src="blobUrl" :alt="fileName" class="media-image" />
      <div v-else class="media-stub failed">图片加载失败 · {{ fileName }}</div>
    </template>

    <template v-else-if="previewKind === 'video'">
      <div v-if="loading" class="media-stub">视频加载中...</div>
      <video
        v-else-if="blobUrl"
        controls
        :src="blobUrl"
        class="media-video"
        preload="metadata"
      ></video>
      <div v-else class="media-stub failed">视频加载失败 · {{ fileName }}</div>
    </template>

    <template v-else-if="previewKind === 'audio'">
      <div v-if="loading" class="media-stub">音频加载中...</div>
      <audio
        v-else-if="blobUrl"
        controls
        :src="blobUrl"
        class="media-audio"
        preload="metadata"
      ></audio>
      <div v-else class="media-stub failed">音频加载失败 · {{ fileName }}</div>
    </template>

    <div v-else class="media-file">
      <span class="file-icon" aria-hidden="true">📄</span>
      <div class="file-meta">
        <p class="file-name">{{ shortenFileName(fileName, 36) }}</p>
        <p v-if="fileSize" class="file-size">{{ formatFileSize(fileSize) }}</p>
      </div>
    </div>

    <div v-if="!isLocalBlob" class="media-actions">
      <button
        type="button"
        class="download-btn"
        :disabled="downloading"
        @click="handleDownload"
      >{{ downloading ? '下载中...' : '下载' }}</button>
    </div>
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
.media-video {
  max-width: 100%;
  max-height: 320px;
  border-radius: var(--radius-sm);
  background: #000;
}
.media-audio {
  width: 260px;
  max-width: 100%;
}

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

.media-actions {
  display: flex;
  justify-content: flex-end;
}
.download-btn {
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.download-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.download-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
