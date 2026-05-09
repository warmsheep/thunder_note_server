<script setup>
import { ref, watch, onBeforeUnmount, nextTick } from 'vue'

// D1-W21-01 单条消息上下文菜单
//
// 通用下拉菜单组件，被 MessageBubble（W21-01）与 FavoritesView（W21-05）复用。
// - 受控：通过 v-model:open 控制显示；x / y 是相对视口的坐标（事件 clientX/Y）
// - items: [{ key, label, icon?, danger?, disabled? }]
// - emit:
//     - select(key)：用户选中某项；选中后自动关闭
//     - update:open(false)：自身请求关闭（ESC / 点击空白）
//
// 关闭策略：
//   - ESC 键
//   - 点击菜单外的空白（document mousedown/touchstart）
//   - 选中某项后自动关闭
//
// 边界处理：
//   - 菜单宽度估算 180px / 行高约 36px；如果 x/y 接近视口右/下边界，自动翻向左/上展开
//   - aria-hidden / role="menu" 满足基本可访问性
//
// 注：不引入 Popper / Portal 等第三方库，保持包体积；使用 position: fixed 即可

const props = defineProps({
  open: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  items: { type: Array, default: () => [] },
  // 估算菜单宽 / 单行高度，用于反翻边界判定
  menuWidth: { type: Number, default: 180 },
  itemHeight: { type: Number, default: 36 }
})

const emit = defineEmits(['select', 'update:open'])

const menuEl = ref(null)

// 计算显示坐标，处理右/下边界翻向
const adjustedX = ref(0)
const adjustedY = ref(0)

function recompute() {
  if (typeof window === 'undefined') {
    adjustedX.value = props.x
    adjustedY.value = props.y
    return
  }
  const vw = window.innerWidth
  const vh = window.innerHeight
  const totalH = (props.items.length || 1) * props.itemHeight + 8
  let x = props.x
  let y = props.y
  if (x + props.menuWidth > vw - 8) {
    x = Math.max(8, x - props.menuWidth)
  }
  if (y + totalH > vh - 8) {
    y = Math.max(8, y - totalH)
  }
  adjustedX.value = Math.max(8, x)
  adjustedY.value = Math.max(8, y)
}

function close() {
  emit('update:open', false)
}

function onItemClick(item) {
  if (item?.disabled) return
  emit('select', item.key)
  close()
}

function onDocPointerDown(e) {
  if (!props.open) return
  const el = menuEl.value
  if (el && el.contains(e.target)) return
  close()
}

function onDocKeydown(e) {
  if (!props.open) return
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
  }
}

// immediate=true 兜底：组件挂载时 open 已经是 true 也能立即绑定监听。
// 对一般打开路径（open 从 false → true），watch 也会照常触发；
// 通过两个 nextTick 把"打开瞬间的 mousedown"摘出去，避免被识别为 outside。
watch(
  () => props.open,
  async (next) => {
    if (next) {
      recompute()
      await nextTick()
      await nextTick()
      if (typeof document !== 'undefined') {
        document.addEventListener('mousedown', onDocPointerDown, true)
        document.addEventListener('touchstart', onDocPointerDown, true)
        document.addEventListener('keydown', onDocKeydown, true)
      }
    } else if (typeof document !== 'undefined') {
      document.removeEventListener('mousedown', onDocPointerDown, true)
      document.removeEventListener('touchstart', onDocPointerDown, true)
      document.removeEventListener('keydown', onDocKeydown, true)
    }
  },
  { immediate: true }
)

watch(
  () => [props.x, props.y, props.items.length],
  () => {
    if (props.open) recompute()
  }
)

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.removeEventListener('mousedown', onDocPointerDown, true)
    document.removeEventListener('touchstart', onDocPointerDown, true)
    document.removeEventListener('keydown', onDocKeydown, true)
  }
})
</script>

<template>
  <Teleport to="body">
    <ul
      v-if="open"
      ref="menuEl"
      class="action-menu"
      role="menu"
      :style="{ left: adjustedX + 'px', top: adjustedY + 'px', width: menuWidth + 'px' }"
    >
      <li
        v-for="item in items"
        :key="item.key"
        role="menuitem"
        class="action-menu-item"
        :class="{ danger: item.danger, disabled: item.disabled }"
        :aria-disabled="item.disabled ? 'true' : 'false'"
        @click="onItemClick(item)"
      >
        <span v-if="item.icon" class="item-icon" aria-hidden="true">{{ item.icon }}</span>
        <span class="item-label">{{ item.label }}</span>
      </li>
    </ul>
  </Teleport>
</template>

<style scoped>
.action-menu {
  position: fixed;
  z-index: 9999;
  list-style: none;
  margin: 0;
  padding: 4px;
  background: var(--color-surface);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  /* 不允许文本被默认选中，避免长按弹菜单时同时选中消息文本 */
  user-select: none;
}
.action-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-text-primary);
  cursor: pointer;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}
.action-menu-item:hover:not(.disabled) {
  background: var(--color-bg);
}
.action-menu-item.danger {
  color: var(--color-danger);
}
.action-menu-item.danger:hover:not(.disabled) {
  background: rgba(239, 68, 68, 0.08);
}
.action-menu-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.item-icon {
  width: 18px;
  text-align: center;
  font-size: 14px;
}
.item-label {
  flex: 1;
}
</style>
