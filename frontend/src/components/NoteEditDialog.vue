<script>
// D1-W26-03 闪记图标 emoji 预设（与 Android `R.array.flashnote_icons` 对齐，12 项）
// 暴露为 normal script 便于测试与外部引用（script setup 不允许 export）
export const FLASHNOTE_ICONS = [
  '💼', '📚', '❤️', '🍀',
  '🌟', '🎯', '🚀', '🎨',
  '🎵', '📷', '📝', '💡'
]
</script>

<script setup>
import { ref, watch } from 'vue'
import { validateCreateForm, validateUpdateForm, toggleSingleTag } from '../utils/flashNoteValidators'

// 创建/编辑闪记弹窗
// - props.open: 控制可见
// - props.mode: 'create' | 'edit'
// - props.initial: 编辑时的初始字段
// - emit submit({ title, icon })
// - emit cancel
//
// D1-W26-03 图标输入升级：12 项 emoji chip 预设（FLASHNOTE_ICONS）+ 自定义文本框混合，
// 与 Android `DialogFlashNoteEdit` 的 `flashnote_icons` Chip 预设对齐

const props = defineProps({
  open: { type: Boolean, default: false },
  mode: { type: String, default: 'create' }, // 'create' | 'edit'
  initial: { type: Object, default: () => ({}) },
  busy: { type: Boolean, default: false },
  // D1-W28-02 合集列表来源：父级注入 useCollectionsStore().sortedList，不在弹窗内自取数据
  // 与后端约束对齐：FlashNote.tags 是单一字符串，等于 collection.name；空串/null 表示「不分入合集」
  collections: { type: Array, default: () => [] }
})

const emit = defineEmits(['submit', 'cancel'])

const title = ref('')
const icon = ref('')
// D1-W28-02 tags 当前后端语义为单一合集名字符串；空串=不分入合集
const tags = ref('')
const errorMessage = ref('')

watch(
  () => [props.open, props.initial],
  ([open]) => {
    if (open) {
      title.value = props.initial?.title || ''
      icon.value = props.initial?.icon || ''
      tags.value = props.initial?.tags || ''
      errorMessage.value = ''
    }
  },
  { immediate: true }
)

function handleSubmit() {
  errorMessage.value = ''
  const payload = {
    title: title.value.trim(),
    icon: icon.value.trim(),
    // D1-W28-02 后端约定：tags 字段更新时必须显式传字符串，空串表示「移出合集」
    // FlashNoteUpdateRequest.tags 走 @TableField(updateStrategy=ALWAYS)，传 "" 会真正写入空
    tags: tags.value.trim()
  }
  const result = props.mode === 'edit' ? validateUpdateForm(payload) : validateCreateForm(payload)
  if (!result.ok) {
    errorMessage.value = result.message
    return
  }
  emit('submit', payload)
}

// D1-W28-02 单选合集：复用 toggleSingleTag 纯函数，让行为可独立单测
function pickCollection(name) {
  if (props.busy) return
  tags.value = toggleSingleTag(tags.value, name)
}

function isCollectionSelected(name) {
  return tags.value === name
}

// D1-W26-03 选中预设图标 → 写入 icon 字段；再次点击同一项 → 取消选中（清空）
function pickPresetIcon(emoji) {
  if (props.busy) return
  if (icon.value === emoji) {
    icon.value = ''
  } else {
    icon.value = emoji
  }
}

function isPresetSelected(emoji) {
  return icon.value === emoji
}
</script>

<template>
  <Teleport to="body">
    <transition name="fade">
      <div v-if="open" class="dialog-mask" @click.self="emit('cancel')">
        <section class="dialog-card" role="dialog" aria-modal="true">
          <header class="dialog-header">
            <h2 class="dialog-title">{{ mode === 'edit' ? '编辑闪记' : '新建闪记' }}</h2>
          </header>
          <form class="dialog-form" @submit.prevent="handleSubmit">
            <label class="field">
              <span>名称</span>
              <input
                v-model="title"
                type="text"
                maxlength="500"
                :disabled="busy"
                placeholder="给这条闪记起个名字"
                required
              />
            </label>
            <!-- D1-W28-02 合集（可选）：单选 chip 网格，点击切换；无合集时仅渲染说明 + 「无」chip
                 与 Android `DialogFlashNoteEdit` 的合集 Spinner 等价但走 chip 形式 -->
            <div class="field">
              <span>合集（可选）</span>
              <div class="collection-row">
                <button
                  type="button"
                  class="collection-chip"
                  :class="{ active: !tags }"
                  :disabled="busy"
                  :aria-pressed="!tags"
                  @click="pickCollection('')"
                >无</button>
                <button
                  v-for="c in collections"
                  :key="c.id"
                  type="button"
                  class="collection-chip"
                  :class="{ active: isCollectionSelected(c.name) }"
                  :disabled="busy"
                  :aria-pressed="isCollectionSelected(c.name)"
                  :title="c.description || c.name"
                  @click="pickCollection(c.name)"
                >{{ c.name }}</button>
              </div>
              <p v-if="!collections.length" class="field-hint">还没有合集，可在「合集」页面创建后回来选择</p>
            </div>
            <div class="field">
              <span>图标（可选）</span>
              <!-- D1-W26-03 12 项 emoji 预设网格 + 自定义输入混合，对齐 Android `flashnote_icons` -->
              <div class="icon-grid" role="listbox" aria-label="选择图标">
                <button
                  v-for="emoji in FLASHNOTE_ICONS"
                  :key="emoji"
                  type="button"
                  class="icon-chip"
                  :class="{ active: isPresetSelected(emoji) }"
                  :disabled="busy"
                  :aria-pressed="isPresetSelected(emoji)"
                  :title="emoji"
                  @click="pickPresetIcon(emoji)"
                >{{ emoji }}</button>
              </div>
              <input
                v-model="icon"
                type="text"
                maxlength="64"
                :disabled="busy"
                placeholder="或输入自定义图标 / 文字"
              />
            </div>
            <p v-if="errorMessage" class="field-error" role="alert">{{ errorMessage }}</p>
            <footer class="dialog-actions">
              <button
                type="button"
                class="btn btn-secondary"
                :disabled="busy"
                @click="emit('cancel')"
              >取消</button>
              <button
                type="submit"
                class="btn btn-primary"
                :disabled="busy"
              >{{ busy ? '保存中...' : (mode === 'edit' ? '保存' : '创建') }}</button>
            </footer>
          </form>
        </section>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 900;
  padding: 16px;
}
.dialog-card {
  width: 100%;
  max-width: 420px;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-md);
}
.dialog-header {
  margin-bottom: 16px;
}
.dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
  color: var(--color-text-primary);
}
.field input {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text-primary);
}
.field input:focus {
  border-color: var(--color-primary);
}
.field input:disabled {
  background: var(--color-bg);
  cursor: not-allowed;
}
.field-error {
  margin: 0;
  padding: 8px 12px;
  background: var(--color-danger-bg);
  color: var(--color-danger);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

/* D1-W28-02 合集 chip：横向自适应换行，单选高亮，与图标 chip 视觉一致但宽度自由 */
.collection-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.collection-chip {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 1.2;
  cursor: pointer;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.collection-chip:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.collection-chip.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary);
}
.collection-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.field-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-hint);
}

/* D1-W26-03 图标 chip 网格：6×2 布局，移动端 ≤480 自动换 4 列 */
.icon-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 6px;
}
.icon-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  width: 100%;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}
.icon-chip:hover:not(:disabled) {
  border-color: var(--color-primary);
}
.icon-chip.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
.icon-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@media (max-width: 480px) {
  .icon-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.btn {
  padding: 8px 16px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  font-size: 14px;
  cursor: pointer;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-secondary {
  border-color: var(--color-border);
  color: var(--color-text-primary);
}
.btn-secondary:hover:not(:disabled) {
  background: var(--color-bg);
}
.btn-primary {
  background: var(--color-primary);
  color: #ffffff;
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}
</style>
