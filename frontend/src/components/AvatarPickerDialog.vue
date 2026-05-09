<script>
// D1-W23-01 Emoji 列表（与 Android `R.array.profile_avatar_emojis` 对齐，12 项）。
// 以 normal script 暴露，方便测试与外部引用（script setup 不允许 export）。
export const PROFILE_EMOJIS = [
  '💼', '📚', '❤️',
  '🌟', '🎯', '🚀',
  '🎨', '🎵', '📷',
  '🍕', '⚽', '😊'
]
</script>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

// D1-W23-01 头像选择对话框
//
// 两个 tab：
//   - 📤 上传图片：选择本地图片 → 发 'pick-image' 事件（由父级走 W23-02 裁剪流程）
//   - 😊 Emoji：选中 emoji → 发 'select-emoji' 事件（父级直接调 PUT /api/users/avatar）
//
// 设计约束：
//   - 组件本身不调 API，不碰 store：让父级决定写入方式，避免循环依赖
//   - Emoji 列表与 Android `R.array.profile_avatar_emojis` 对齐（12 项）
//   - AuthenticatedAvatar 已能把非 URL 字符串当作 fallback 展示，这里不需特殊处理

const props = defineProps({
  open: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  // 当前头像：仅用于视觉提示选中；emoji 字符串直接对比
  currentAvatar: { type: String, default: '' }
})

const emit = defineEmits(['pick-image', 'select-emoji', 'cancel', 'update:open'])

const currentTab = ref('emoji') // 'upload' | 'emoji'
const fileInputEl = ref(null)
const errorMessage = ref('')

function isEmoji(v) {
  if (!v) return false
  const s = String(v).trim()
  // 简单判定：无 http(s) 前缀且不含 "/"（非 path），长度 ≤ 4（覆盖复合 emoji 如 ❤️ 带变体选择符）
  return !/^https?:\/\//.test(s) && !s.includes('/') && s.length <= 4
}

const selectedEmoji = computed(() => (isEmoji(props.currentAvatar) ? props.currentAvatar : ''))

function triggerUpload() {
  if (props.busy) return
  fileInputEl.value?.click()
}

function onFileChange(e) {
  const f = e.target.files && e.target.files[0]
  if (e.target) e.target.value = ''
  if (!f) return
  if (!f.type || !f.type.startsWith('image/')) {
    errorMessage.value = '请选择图片文件'
    return
  }
  errorMessage.value = ''
  emit('pick-image', f)
}

function pickEmoji(e) {
  if (props.busy) return
  emit('select-emoji', e)
}

function cancel() {
  if (props.busy) return
  emit('cancel')
  emit('update:open', false)
}

watch(
  () => props.open,
  async (next) => {
    if (next) {
      errorMessage.value = ''
      await nextTick()
    }
  }
)
</script>

<template>
  <div v-if="open" class="modal-overlay" @click.self="cancel">
    <div class="modal" role="dialog" aria-label="设置头像">
      <header class="modal-header">
        <h2 class="modal-title">设置头像</h2>
        <button type="button" class="modal-close" :disabled="busy" @click="cancel">×</button>
      </header>

      <div class="tab-bar" role="tablist">
        <button
          type="button"
          role="tab"
          class="tab-btn"
          :class="{ active: currentTab === 'emoji' }"
          :aria-selected="currentTab === 'emoji' ? 'true' : 'false'"
          :disabled="busy"
          @click="currentTab = 'emoji'"
        >😊 Emoji</button>
        <button
          type="button"
          role="tab"
          class="tab-btn"
          :class="{ active: currentTab === 'upload' }"
          :aria-selected="currentTab === 'upload' ? 'true' : 'false'"
          :disabled="busy"
          @click="currentTab = 'upload'"
        >📤 上传图片</button>
      </div>

      <div class="modal-body">
        <!-- Emoji tab -->
        <section v-if="currentTab === 'emoji'" class="emoji-grid" role="tabpanel">
          <button
            v-for="e in PROFILE_EMOJIS"
            :key="e"
            type="button"
            class="emoji-btn"
            :class="{ selected: e === selectedEmoji }"
            :disabled="busy"
            :aria-label="'选择头像 ' + e"
            @click="pickEmoji(e)"
          >{{ e }}</button>
        </section>

        <!-- Upload tab -->
        <section v-else class="upload-pane" role="tabpanel">
          <p class="upload-hint">
            选择一张图片，下一步会进入裁剪界面（输出 512×512 方形头像）。
          </p>
          <button
            type="button"
            class="btn-primary"
            :disabled="busy"
            @click="triggerUpload"
          >选择本地图片</button>
          <input
            ref="fileInputEl"
            type="file"
            accept="image/*"
            class="file-hidden"
            @change="onFileChange"
          />
        </section>

        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
      </div>

      <footer class="modal-footer">
        <button type="button" class="btn-secondary" :disabled="busy" @click="cancel">取消</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}
.modal {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 420px;
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
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
.modal-close:hover:not(:disabled) {
  color: var(--color-text-primary);
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--color-divider);
}
.tab-btn {
  flex: 1;
  padding: 10px 0;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--color-text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.tab-btn.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 500;
}
.tab-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.modal-body {
  padding: 16px 18px;
  overflow-y: auto;
  flex: 1;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 10px 18px;
  border-top: 1px solid var(--color-divider);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.emoji-btn {
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  background: var(--color-bg);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.12s, transform 0.08s;
}
.emoji-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
}
.emoji-btn:active:not(:disabled) {
  transform: scale(0.96);
}
.emoji-btn.selected {
  border-color: var(--color-primary);
  background: rgba(37, 99, 235, 0.08);
}
.emoji-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.upload-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
}
.upload-hint {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.file-hidden {
  display: none;
}

.error-text {
  margin: 10px 0 0 0;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger);
  font-size: 13px;
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
</style>
