import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/collections', () => ({
  listCollections: vi.fn(),
  createCollection: vi.fn(),
  updateCollection: vi.fn(),
  deleteCollection: vi.fn()
}))

import * as collectionsApi from '../api/collections'
import { useCollectionsStore } from './collections'

describe('collections store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList loads list and sets loaded', async () => {
    collectionsApi.listCollections.mockResolvedValueOnce([{ id: 1, name: 'a' }, { id: 2, name: 'b' }])
    const store = useCollectionsStore()
    await store.fetchList()
    expect(store.list).toHaveLength(2)
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('fetchList captures error message and rethrows', async () => {
    const err = new Error('oops')
    err.serverMessage = '后端报错'
    collectionsApi.listCollections.mockRejectedValueOnce(err)
    const store = useCollectionsStore()
    await expect(store.fetchList()).rejects.toThrow('oops')
    expect(store.error).toBe('后端报错')
  })

  it('sortedList returns name asc', () => {
    const store = useCollectionsStore()
    store.list = [{ id: 1, name: 'banana' }, { id: 2, name: 'apple' }, { id: 3, name: 'cherry' }]
    expect(store.sortedList.map((c) => c.name)).toEqual(['apple', 'banana', 'cherry'])
  })

  it('findById and findByName return correct entry', () => {
    const store = useCollectionsStore()
    store.list = [{ id: 1, name: 'a' }, { id: 2, name: 'b' }]
    expect(store.findById(2).name).toBe('b')
    expect(store.findByName('a').id).toBe(1)
    expect(store.findById(99)).toBeNull()
  })

  it('create prepends new item', async () => {
    collectionsApi.createCollection.mockResolvedValueOnce({ id: 9, name: '新' })
    const store = useCollectionsStore()
    store.list = [{ id: 1, name: 'a' }]
    const created = await store.create({ name: '新' })
    expect(created.id).toBe(9)
    expect(store.list[0].id).toBe(9)
  })

  it('update replaces local item', async () => {
    collectionsApi.updateCollection.mockResolvedValueOnce({ id: 1, name: '改' })
    const store = useCollectionsStore()
    store.list = [{ id: 1, name: '原' }, { id: 2, name: 'b' }]
    await store.update(1, { name: '改' })
    expect(store.list.find((c) => c.id === 1).name).toBe('改')
  })

  it('remove drops item on success', async () => {
    collectionsApi.deleteCollection.mockResolvedValueOnce(null)
    const store = useCollectionsStore()
    store.list = [{ id: 1, name: 'a' }, { id: 2, name: 'b' }]
    await store.remove(1)
    expect(store.list.map((c) => c.id)).toEqual([2])
  })

  it('remove preserves list when api rejects', async () => {
    collectionsApi.deleteCollection.mockRejectedValueOnce(new Error('boom'))
    const store = useCollectionsStore()
    store.list = [{ id: 1 }, { id: 2 }]
    await expect(store.remove(1)).rejects.toThrow('boom')
    expect(store.list).toHaveLength(2)
  })

  it('reset clears state', () => {
    const store = useCollectionsStore()
    store.list = [{ id: 1 }]
    store.loaded = true
    store.reset()
    expect(store.list).toEqual([])
    expect(store.loaded).toBe(false)
  })
})
