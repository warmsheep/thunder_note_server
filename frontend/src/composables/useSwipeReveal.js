import { ref } from 'vue'

const DEFAULT_MAX_REVEAL = 96
const DEFAULT_THRESHOLD = 0.4

function readTouch(event) {
  return event?.touches?.[0] || event?.changedTouches?.[0] || null
}

function clampNegative(value, min) {
  if (!Number.isFinite(value)) return 0
  if (value > 0) return 0
  return Math.max(min, value)
}

export function useSwipeReveal({ maxReveal = DEFAULT_MAX_REVEAL, threshold = DEFAULT_THRESHOLD } = {}) {
  const openId = ref(null)
  const draggingId = ref(null)
  const draggingOffset = ref(0)

  let startX = 0
  let rowWidth = 0

  function begin(id, event, width) {
    const touch = readTouch(event)
    if (!touch || id == null) return false
    draggingId.value = id
    startX = touch.clientX
    rowWidth = Math.max(1, Number(width) || 1)
    draggingOffset.value = openId.value === id ? -maxReveal : 0
    if (openId.value != null && openId.value !== id) {
      openId.value = null
    }
    return true
  }

  function move(id, event) {
    if (draggingId.value !== id) return 0
    const touch = readTouch(event)
    if (!touch) return draggingOffset.value
    const deltaX = touch.clientX - startX
    draggingOffset.value = clampNegative(deltaX + (openId.value === id ? -maxReveal : 0), -maxReveal)
    return draggingOffset.value
  }

  function end(id) {
    if (draggingId.value !== id) {
      return openId.value === id
    }
    const shouldOpen = Math.abs(draggingOffset.value) / rowWidth >= threshold
    openId.value = shouldOpen ? id : null
    draggingId.value = null
    draggingOffset.value = 0
    rowWidth = 0
    return shouldOpen
  }

  function close(id = null) {
    if (id == null || openId.value === id) {
      openId.value = null
    }
    if (id == null || draggingId.value === id) {
      draggingId.value = null
      draggingOffset.value = 0
      rowWidth = 0
    }
  }

  function offsetOf(id) {
    if (draggingId.value === id) return draggingOffset.value
    if (openId.value === id) return -maxReveal
    return 0
  }

  function isOpen(id) {
    return openId.value === id
  }

  return {
    openId,
    begin,
    move,
    end,
    close,
    offsetOf,
    isOpen
  }
}
