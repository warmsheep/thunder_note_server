import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import {
  listCollections,
  createCollection,
  updateCollection,
  deleteCollection
} from './collections'

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('collections api wrappers', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('listCollections posts to /api/collections/list', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse([{ id: 1, name: '工作' }]), headers: {}, config }
    })
    const data = await listCollections()
    expect(captured).toEqual({ url: '/api/collections/list', method: 'post' })
    expect(data).toEqual([{ id: 1, name: '工作' }])
  })

  it('createCollection posts payload', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return { status: 200, data: apiResponse({ id: 9, name: '新合集' }), headers: {}, config }
    })
    const created = await createCollection({ name: '新合集', description: 'd' })
    expect(captured).toEqual({
      url: '/api/collections',
      method: 'post',
      body: { name: '新合集', description: 'd' }
    })
    expect(created.id).toBe(9)
  })

  it('updateCollection puts to /api/collections/:id', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return { status: 200, data: apiResponse({ id: 5, name: '改后' }), headers: {}, config }
    })
    await updateCollection(5, { name: '改后', description: '' })
    expect(captured.url).toBe('/api/collections/5')
    expect(captured.method).toBe('put')
    expect(captured.body).toEqual({ name: '改后', description: '' })
  })

  it('updateCollection rejects when id is missing', async () => {
    await expect(updateCollection(null, {})).rejects.toThrow(/id is required/)
  })

  it('deleteCollection sends DELETE', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await deleteCollection(7)
    expect(captured).toEqual({ url: '/api/collections/7', method: 'delete' })
  })

  it('deleteCollection rejects when id is missing', async () => {
    await expect(deleteCollection(undefined)).rejects.toThrow(/id is required/)
  })
})
