import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import { searchFlashNotes } from './flashNotes'

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('searchFlashNotes', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('posts query to /api/flash-notes/search', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return {
        status: 200,
        data: apiResponse({ noteNameMatched: [], messageContentMatched: [] }),
        headers: {},
        config
      }
    })
    await searchFlashNotes('hello')
    expect(captured).toEqual({
      url: '/api/flash-notes/search',
      method: 'post',
      body: { query: 'hello' }
    })
  })

  it('coerces null/undefined query to empty string', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = JSON.parse(config.data)
      return {
        status: 200,
        data: apiResponse({ noteNameMatched: [], messageContentMatched: [] }),
        headers: {},
        config
      }
    })
    await searchFlashNotes(null)
    expect(captured.query).toBe('')
  })

  it('returns search response data', async () => {
    installMockAdapter(async (config) => ({
      status: 200,
      data: apiResponse({
        noteNameMatched: [{ flashNote: { id: 1, title: 'a' }, noteMatched: true }],
        messageContentMatched: []
      }),
      headers: {},
      config
    }))
    const data = await searchFlashNotes('a')
    expect(data.noteNameMatched).toHaveLength(1)
  })
})
