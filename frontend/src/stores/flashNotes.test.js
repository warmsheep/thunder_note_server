import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock api 层，避免真实网络。每个测试用例可重设 mock 实现。
vi.mock('../api/flashNotes', () => ({
  listFlashNotes: vi.fn(),
  createFlashNote: vi.fn(),
  updateFlashNote: vi.fn(),
  setFlashNotePinned: vi.fn(),
  setFlashNoteHidden: vi.fn(),
  deleteFlashNote: vi.fn()
}))

import * as flashNotesApi from '../api/flashNotes'
import { useFlashNotesStore } from './flashNotes'

function note(overrides = {}) {
  return {
    id: 1,
    userId: 1,
    title: 'note',
    icon: '📝',
    content: '',
    tags: '',
    latestMessage: null,
    deleted: false,
    pinned: false,
    hidden: false,
    inbox: false,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
    ...overrides
  }
}

describe('flashNotes store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList loads list, sets loaded, clears loading', async () => {
    flashNotesApi.listFlashNotes.mockResolvedValueOnce([note({ id: 1 }), note({ id: 2 })])
    const store = useFlashNotesStore()
    expect(store.loading).toBe(false)
    const promise = store.fetchList()
    expect(store.loading).toBe(true)
    await promise
    expect(store.list).toHaveLength(2)
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('fetchList captures server error message and rethrows', async () => {
    const err = new Error('boom')
    err.serverMessage = '服务器错误'
    flashNotesApi.listFlashNotes.mockRejectedValueOnce(err)
    const store = useFlashNotesStore()
    await expect(store.fetchList()).rejects.toThrow('boom')
    expect(store.error).toBe('服务器错误')
    expect(store.loading).toBe(false)
  })

  it('inboxNote getter returns the inbox flagged item', () => {
    const store = useFlashNotesStore()
    store.list = [note({ id: 1 }), note({ id: 2, inbox: true }), note({ id: 3 })]
    expect(store.inboxNote.id).toBe(2)
  })

  it('pinnedList / normalList / hiddenList split correctly and exclude inbox', () => {
    const store = useFlashNotesStore()
    store.list = [
      note({ id: 1, inbox: true }),
      note({ id: 2, pinned: true, updatedAt: '2026-02-01T00:00:00' }),
      note({ id: 3, pinned: true, updatedAt: '2026-02-02T00:00:00' }),
      note({ id: 4, updatedAt: '2026-03-01T00:00:00' }),
      note({ id: 5, updatedAt: '2026-03-02T00:00:00' }),
      note({ id: 6, hidden: true })
    ]
    expect(store.pinnedList.map((n) => n.id)).toEqual([3, 2])
    expect(store.normalList.map((n) => n.id)).toEqual([5, 4])
    expect(store.hiddenList.map((n) => n.id)).toEqual([6])
    // inbox 不应混入任何分组
    for (const list of [store.pinnedList, store.normalList, store.hiddenList]) {
      expect(list.find((n) => n.inbox)).toBeUndefined()
    }
  })

  it('create prepends new note to list', async () => {
    const store = useFlashNotesStore()
    store.list = [note({ id: 1 })]
    flashNotesApi.createFlashNote.mockResolvedValueOnce(note({ id: 99, title: 'new' }))
    const created = await store.create({ title: 'new' })
    expect(created.id).toBe(99)
    expect(store.list[0].id).toBe(99)
    expect(store.list).toHaveLength(2)
  })

  it('update replaces matching item locally', async () => {
    const store = useFlashNotesStore()
    store.list = [note({ id: 1, title: 'old' }), note({ id: 2 })]
    flashNotesApi.updateFlashNote.mockResolvedValueOnce(note({ id: 1, title: 'new' }))
    await store.update(1, { title: 'new' })
    expect(store.list.find((n) => n.id === 1).title).toBe('new')
  })

  it('setPinned and setHidden replace local item', async () => {
    const store = useFlashNotesStore()
    store.list = [note({ id: 1, pinned: false, hidden: false })]
    flashNotesApi.setFlashNotePinned.mockResolvedValueOnce(note({ id: 1, pinned: true }))
    await store.setPinned(1, true)
    expect(store.list[0].pinned).toBe(true)
    flashNotesApi.setFlashNoteHidden.mockResolvedValueOnce(note({ id: 1, pinned: true, hidden: true }))
    await store.setHidden(1, true)
    expect(store.list[0].hidden).toBe(true)
  })

  it('remove drops item from list on success', async () => {
    const store = useFlashNotesStore()
    store.list = [note({ id: 1 }), note({ id: 2 })]
    flashNotesApi.deleteFlashNote.mockResolvedValueOnce(null)
    await store.remove(1)
    expect(store.list.map((n) => n.id)).toEqual([2])
  })

  it('remove preserves list when api rejects', async () => {
    const store = useFlashNotesStore()
    store.list = [note({ id: 1 }), note({ id: 2 })]
    flashNotesApi.deleteFlashNote.mockRejectedValueOnce(new Error('nope'))
    await expect(store.remove(1)).rejects.toThrow('nope')
    expect(store.list).toHaveLength(2)
  })

  it('reset clears state', () => {
    const store = useFlashNotesStore()
    store.list = [note({ id: 1 })]
    store.error = 'x'
    store.loaded = true
    store.reset()
    expect(store.list).toEqual([])
    expect(store.error).toBeNull()
    expect(store.loaded).toBe(false)
  })
})
