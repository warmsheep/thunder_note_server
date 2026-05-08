import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import { listFavorites, addFavorite, removeFavorite } from './favorites'

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('favorites api wrappers', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('listFavorites posts to /api/favorites/list', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse([{ id: 1, messageId: 11 }]), headers: {}, config }
    })
    const data = await listFavorites()
    expect(captured).toEqual({ url: '/api/favorites/list', method: 'post' })
    expect(data).toEqual([{ id: 1, messageId: 11 }])
  })

  it('addFavorite posts to /api/favorites/:messageId', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse({ id: 9, messageId: 33 }), headers: {}, config }
    })
    const item = await addFavorite(33)
    expect(captured).toEqual({ url: '/api/favorites/33', method: 'post' })
    expect(item.id).toBe(9)
  })

  it('addFavorite rejects when messageId is missing', async () => {
    await expect(addFavorite(null)).rejects.toThrow(/messageId/)
  })

  it('removeFavorite sends DELETE to /api/favorites/:messageId', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await removeFavorite(42)
    expect(captured).toEqual({ url: '/api/favorites/42', method: 'delete' })
  })

  it('removeFavorite rejects when messageId is missing', async () => {
    await expect(removeFavorite(undefined)).rejects.toThrow(/messageId/)
  })
})
