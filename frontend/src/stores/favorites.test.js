import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/favorites', () => ({
  listFavorites: vi.fn(),
  addFavorite: vi.fn(),
  removeFavorite: vi.fn()
}))

import * as favoritesApi from '../api/favorites'
import { useFavoritesStore } from './favorites'

function fav(id, messageId, favoritedAt = '2026-01-01T00:00:00') {
  return {
    id,
    messageId,
    flashNoteId: 7,
    flashNoteTitle: 'note',
    flashNoteIcon: '⚡',
    role: 'user',
    content: `m${id}`,
    favoritedAt
  }
}

describe('favorites store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList loads list and builds favoritedSet', async () => {
    favoritesApi.listFavorites.mockResolvedValueOnce([fav(1, 11), fav(2, 22)])
    const store = useFavoritesStore()
    await store.fetchList()
    expect(store.list).toHaveLength(2)
    expect(store.isFavorited(11)).toBe(true)
    expect(store.isFavorited(22)).toBe(true)
    expect(store.isFavorited(99)).toBe(false)
    expect(store.loaded).toBe(true)
  })

  it('sortedList orders by favoritedAt desc', async () => {
    favoritesApi.listFavorites.mockResolvedValueOnce([
      fav(1, 11, '2026-01-01T10:00:00'),
      fav(2, 22, '2026-01-02T10:00:00'),
      fav(3, 33, '2026-01-03T10:00:00')
    ])
    const store = useFavoritesStore()
    await store.fetchList()
    expect(store.sortedList.map((x) => x.id)).toEqual([3, 2, 1])
  })

  it('add prepends item and marks favorited optimistically', async () => {
    let resolveAdd
    favoritesApi.addFavorite.mockReturnValueOnce(new Promise((r) => { resolveAdd = r }))
    const store = useFavoritesStore()
    const adding = store.add(55)
    expect(store.isFavorited(55)).toBe(true)
    resolveAdd(fav(9, 55, '2026-02-01T00:00:00'))
    await adding
    expect(store.list[0].messageId).toBe(55)
  })

  it('add rolls back favoritedSet when api rejects', async () => {
    favoritesApi.addFavorite.mockRejectedValueOnce(new Error('boom'))
    const store = useFavoritesStore()
    await expect(store.add(77)).rejects.toThrow('boom')
    expect(store.isFavorited(77)).toBe(false)
  })

  it('remove drops list entry and clears favoritedSet on success', async () => {
    favoritesApi.listFavorites.mockResolvedValueOnce([fav(1, 11), fav(2, 22)])
    const store = useFavoritesStore()
    await store.fetchList()
    favoritesApi.removeFavorite.mockResolvedValueOnce(null)
    await store.remove(11)
    expect(store.isFavorited(11)).toBe(false)
    expect(store.list.map((x) => x.messageId)).toEqual([22])
  })

  it('remove restores previous list and favoritedSet on failure', async () => {
    favoritesApi.listFavorites.mockResolvedValueOnce([fav(1, 11), fav(2, 22)])
    const store = useFavoritesStore()
    await store.fetchList()
    favoritesApi.removeFavorite.mockRejectedValueOnce(new Error('nope'))
    await expect(store.remove(11)).rejects.toThrow('nope')
    expect(store.isFavorited(11)).toBe(true)
    expect(store.list.find((x) => x.messageId === 11)).toBeTruthy()
  })

  it('reset clears state', async () => {
    favoritesApi.listFavorites.mockResolvedValueOnce([fav(1, 11)])
    const store = useFavoritesStore()
    await store.fetchList()
    store.reset()
    expect(store.list).toEqual([])
    expect(store.isFavorited(11)).toBe(false)
    expect(store.loaded).toBe(false)
  })
})
