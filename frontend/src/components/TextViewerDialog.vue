<script setup>
import { watch, onBeforeUnmount } from 'vue'

// D1-W18 文本/代码文件全屏预览
// 输入：open / text / fileName / loading
// - 用 <pre> 渲染原始文本（保留缩进/换行）
// - 不做语法高亮（保持轻量），后续可叠加 highlight.js
// - 文本由父组件 fetch + decode 后通过 prop 传入；本组件只负责展示与关闭

const props = defineProps({
  open: { type: Boolean, default: false },
  text: { type: String, default: '' },
  fileName: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  errorMessage: { type: String, default: '' }
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
      class="text-overlay"
      role="dialog"
      aria-modal="true"
      :aria-label="fileName || '文本预览'"
      @click.self="close"
    >
      <header class="text-header">
        <span class="text-name">📝 {{ fileName || '文本' }}</span>
        <button type="button" class="text-close" @click="close" aria-label="关闭">×</button>
      </header>
      <div class="text-body">
        <div v-if="loading" class="text-stub">加载中...</div>
        <div v-else-if="errorMessage" class="text-stub failed">{{ errorMessage }}</div>
        <pre v-else class="text-pre">{{ text }}</pre>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.text-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  z-index: 9000;
  display: flex;
  flex-direction: column;
}
.text-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  flex-shrink: 0;
}
.text-name {
  font-size: 13px;
  word-break: break-all;
  margin-right: 12px;
}
.text-close {
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
.text-close:hover { background: rgba(255, 255, 255, 0.3); }

.text-body {
  flex: 1;
  min-height: 0;
  background: #1e1e1e;
  overflow: auto;
}
.text-stub {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #cccccc;
  font-size: 13px;
}
.text-stub.failed {
  color: #ef4444;
}
.text-pre {
  margin: 0;
  padding: 16px 20px;
  color: #e6e6e6;
  font-family: 'SFMono-Regular', Menlo, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  tab-size: 2;
}
</style>
