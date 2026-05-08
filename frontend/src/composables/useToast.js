import { reactive } from 'vue'

// D1-W4-04 全局 Toast composable
// - 单例 reactive 队列：任何组件 useToast() 拿到的是同一个状态
// - showToast/showSuccess/showError 三个便捷入口
// - 自动按 duration 移除，组件层只渲染当前 list

const state = reactive({
  list: [] // [{ id, type, message }]
})

let nextId = 1

function push(type, message, duration) {
  const id = nextId++
  state.list.push({ id, type, message })
  const ttl = duration > 0 ? duration : 2400
  setTimeout(() => {
    const idx = state.list.findIndex((t) => t.id === id)
    if (idx >= 0) {
      state.list.splice(idx, 1)
    }
  }, ttl)
  return id
}

function removeToast(id) {
  const idx = state.list.findIndex((t) => t.id === id)
  if (idx >= 0) {
    state.list.splice(idx, 1)
  }
}

export function useToast() {
  return {
    list: state.list,
    showToast: (message, duration) => push('info', message, duration),
    showSuccess: (message, duration) => push('success', message, duration),
    showError: (message, duration) => push('error', message, duration),
    removeToast
  }
}

// 仅用于测试：清空所有 toast
export function __clearToastsForTests() {
  state.list.splice(0, state.list.length)
  nextId = 1
}
