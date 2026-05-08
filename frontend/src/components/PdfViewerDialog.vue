<script setup>
import { watch, onBeforeUnmount } from 'vue'

// D1-W18-02 PDF 全屏内嵌预览
// 输入：open / blobUrl / fileName
// 用 iframe 直接加载 blob URL，浏览器原生 PDF 阅读器渲染（Chrome/Edge/Firefox/Safari 均支持）
// blob URL 由父组件 MediaPreview 维护并释放，避免重复管理

const props = defineProps({
  open: { type: Boolean, default: false },
  blobUrl: { type: String, default: '' },
  fileName: { type: String, default: '' }
})
const emit = defineEmits(['close'])

function close() {
  emit('close')
}

function onKey(e) {
  if (!props.open) return
  if (e.key === 'Escape') close()
}

watch(() => props.open, (v) => {
  if (typeof window === 'undefined') return
  if (v) {
    window.addEventListener('keydown', onKey)
  } else {
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
      class="pdf-overlay"
      role="dialog"
      aria-modal="true"
      :aria-label="fileName || 'PDF 预览'"
      @click.self="close"
    >
      <header class="pdf-header">
        <span class="pdf-name">📄 {{ fileName || '文档' }}</span>
        <button type="button" class="pdf-close" @click="close" aria-label="关闭">×</button>
      </header>
      <div class="pdf-frame-wrap">
        <!-- 注：sandbox 不能完全空，否则 PDF.js 渲染插件可能受限 -->
        <iframe
          v-if="blobUrl"
          :src="blobUrl"
          :title="fileName || 'PDF'"
          class="pdf-frame"
          sandbox="allow-scripts allow-same-origin"
        ></iframe>
        <div v-else class="pdf-loading">PDF 载入中...</div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.pdf-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  z-index: 9000;
  display: flex;
  flex-direction: column;
}
.pdf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  flex-shrink: 0;
}
.pdf-name {
  font-size: 13px;
  word-break: break-all;
  margin-right: 12px;
}
.pdf-close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #ffffff;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
}
.pdf-close:hover { background: rgba(255, 255, 255, 0.3); }

.pdf-frame-wrap {
  flex: 1;
  min-height: 0;
  background: #525659;
  display: flex;
  align-items: stretch;
  justify-content: stretch;
}
.pdf-frame {
  flex: 1;
  width: 100%;
  height: 100%;
  border: none;
  background: #ffffff;
}
.pdf-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 13px;
}
</style>
