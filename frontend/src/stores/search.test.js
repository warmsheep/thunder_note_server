import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/flashNotes', () => ({
  searchFlashNotes: vi.fn()
}))

import * as flashNotesApi from '../api/flashNotes'
import { useSearchStore } from './search'

describe('search store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('search loads results, sets activeQuery / hasSearched / loading', async () => {
    flashNotesApi.searchFlashNotes.mockResolvedValueOnce({
      noteNameMatched: [{ flashNote: { id: 1, title: 'a' }, noteMatched: true }],
      messageContentMatched: []
    })
    const store = useSearchStore()
    await store.search('a')
    expect(store.activeQuery).toBe('a')
    expect(store.hasSearched).toBe(true)
    expect(store.loading).toBe(false)
    expect(store.totalHits).toBe(1)
  })

  it('search trims query', async () => {
    flashNotesApi.searchFlashNotes.mockResolvedValueOnce({
      noteNameMatched: [],
      messageContentMatched: []
    })
    const store = useSearchStore()
    await store.search('  hello  ')
    expect(flashNotesApi.searchFlashNotes).toHaveBeenCalledWith('hello')
    expect(store.activeQuery).toBe('hello')
  })

  it('empty/whitespace query clears results without calling api', async () => {
    const store = useSearchStore()
    store.results = { noteNameMatched: [{ flashNote: {} }], messageContentMatched: [] }
    store.hasSearched = true
    await store.search('   ')
    expect(flashNotesApi.searchFlashNotes).not.toHaveBeenCalled()
    expect(store.totalHits).toBe(0)
    expect(store.hasSearched).toBe(false)
  })

  it('captures error and resets results', async () => {
    const err = new Error('nope')
    err.serverMessage = '后端错误'
    flashNotesApi.searchFlashNotes.mockRejectedValueOnce(err)
    const store = useSearchStore()
    await store.search('x')
    expect(store.error).toBe('后端错误')
    expect(store.totalHits).toBe(0)
  })

  it('isEmptyAfterSearch is true only after a non-trivial search returned empty', async () => {
    const store = useSearchStore()
    expect(store.isEmptyAfterSearch).toBe(false)

    flashNotesApi.searchFlashNotes.mockResolvedValueOnce({
      noteNameMatched: [],
      messageContentMatched: []
    })
    await store.search('x')
    expect(store.isEmptyAfterSearch).toBe(true)
  })

  it('runId guards against late results overwriting newer ones', async () => {
    let resolveFirst
    const first = new Promise((r) => { resolveFirst = r })
    flashNotesApi.searchFlashNotes
      .mockReturnValueOnce(first)
      .mockResolvedValueOnce({
        noteNameMatched: [{ flashNote: { id: 9, title: 'newer' } }],
        messageContentMatched: []
      })

    const store = useSearchStore()
    const p1 = store.search('old')
    const p2 = store.search('new') // 立即覆盖
    await p2
    // 第一个请求尽管也 resolve，但结果应被丢弃
    resolveFirst({
      noteNameMatched: [{ flashNote: { id: 1, title: 'old' } }],
      messageContentMatched: []
    })
    await p1
    expect(store.activeQuery).toBe('new')
    expect(store.noteHits[0]?.flashNote.id).toBe(9)
  })

  it('clear cancels in-flight and empties results', async () => {
    let resolveFirst
    const first = new Promise((r) => { resolveFirst = r })
    flashNotesApi.searchFlashNotes.mockReturnValueOnce(first)
    const store = useSearchStore()
    const p = store.search('a')
    store.clear()
    resolveFirst({
      noteNameMatched: [{ flashNote: { id: 1 } }],
      messageContentMatched: []
    })
    await p
    expect(store.totalHits).toBe(0)
    expect(store.activeQuery).toBe('')
    expect(store.hasSearched).toBe(false)
  })
})
