import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useToast, __clearToastsForTests } from './useToast'

describe('useToast', () => {
  beforeEach(() => {
    __clearToastsForTests()
    vi.useFakeTimers()
  })

  it('showToast adds an info toast and auto-removes after duration', () => {
    const { list, showToast } = useToast()
    showToast('hello', 1000)
    expect(list.length).toBe(1)
    expect(list[0]).toMatchObject({ type: 'info', message: 'hello' })
    vi.advanceTimersByTime(1000)
    expect(list.length).toBe(0)
  })

  it('showSuccess and showError set correct types', () => {
    const { list, showSuccess, showError } = useToast()
    showSuccess('ok', 2000)
    showError('boom', 2000)
    expect(list[0].type).toBe('success')
    expect(list[1].type).toBe('error')
  })

  it('multiple toasts coexist and remove independently', () => {
    const { list, showToast } = useToast()
    showToast('first', 500)
    showToast('second', 1500)
    expect(list.length).toBe(2)
    vi.advanceTimersByTime(500)
    expect(list.length).toBe(1)
    expect(list[0].message).toBe('second')
    vi.advanceTimersByTime(1000)
    expect(list.length).toBe(0)
  })

  it('removeToast removes by id immediately', () => {
    const { list, showToast, removeToast } = useToast()
    const id = showToast('x', 5000)
    expect(list.length).toBe(1)
    removeToast(id)
    expect(list.length).toBe(0)
  })

  it('useToast returns a singleton list across calls', () => {
    const a = useToast()
    const b = useToast()
    a.showToast('x', 1000)
    expect(b.list.length).toBe(1)
  })
})
