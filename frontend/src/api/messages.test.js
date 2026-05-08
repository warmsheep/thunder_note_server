import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import {
  listMessages,
  sendMessage,
  deleteMessage,
  deleteMessagesBatch,
  clearInbox,
  countMessages
} from './messages'

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('messages api wrappers', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('listMessages posts to /api/messages/list with required body', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return {
        status: 200,
        data: apiResponse({ records: [{ id: 1 }], current: 1, size: 30, total: 1, pages: 1 }),
        headers: {},
        config
      }
    })
    const page = await listMessages({ flashNoteId: 7, page: 2, limit: 50 })
    expect(captured.url).toBe('/api/messages/list')
    expect(captured.method).toBe('post')
    expect(captured.body).toEqual({ flashNoteId: 7, peerUserId: null, page: 2, limit: 50 })
    expect(page.records).toHaveLength(1)
    expect(page.current).toBe(1)
  })

  it('listMessages defaults page=1 limit=30', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = JSON.parse(config.data)
      return { status: 200, data: apiResponse({ records: [] }), headers: {}, config }
    })
    await listMessages({ flashNoteId: -1 })
    expect(captured).toEqual({ flashNoteId: -1, peerUserId: null, page: 1, limit: 30 })
  })

  it('listMessages rejects when flashNoteId is missing', async () => {
    await expect(listMessages({})).rejects.toThrow(/flashNoteId/)
  })

  it('sendMessage posts content/role/clientRequestId to /api/messages', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return { status: 200, data: apiResponse({ id: 99, content: 'hi' }), headers: {}, config }
    })
    const msg = await sendMessage({
      flashNoteId: 12,
      content: 'hi',
      clientRequestId: 'cr-1'
    })
    expect(captured.url).toBe('/api/messages')
    expect(captured.method).toBe('post')
    expect(captured.body).toEqual({
      flashNoteId: 12,
      content: 'hi',
      clientRequestId: 'cr-1',
      role: 'user'
    })
    expect(msg).toEqual({ id: 99, content: 'hi' })
  })

  it('sendMessage coerces content to string when given a number', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = JSON.parse(config.data)
      return { status: 200, data: apiResponse({ id: 1 }), headers: {}, config }
    })
    await sendMessage({ flashNoteId: 1, content: 123 })
    expect(captured.content).toBe('123')
  })

  it('sendMessage rejects when flashNoteId is missing', async () => {
    await expect(sendMessage({ content: 'hi' })).rejects.toThrow(/flashNoteId/)
  })

  it('deleteMessage sends DELETE to /api/messages/:id', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await deleteMessage(42)
    expect(captured).toEqual({ url: '/api/messages/42', method: 'delete' })
  })

  it('deleteMessagesBatch posts ids array', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, body: JSON.parse(config.data) }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await deleteMessagesBatch([1, 2, 3])
    expect(captured).toEqual({ url: '/api/messages/delete-batch', body: { ids: [1, 2, 3] } })
  })

  it('deleteMessagesBatch rejects empty array', async () => {
    await expect(deleteMessagesBatch([])).rejects.toThrow(/non-empty/)
  })

  it('clearInbox sends DELETE /api/messages/clear-inbox without body', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, data: config.data }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await clearInbox()
    expect(captured.url).toBe('/api/messages/clear-inbox')
    expect(captured.method).toBe('delete')
  })

  it('countMessages sends GET /api/messages/count and returns numeric data', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse(123), headers: {}, config }
    })
    const value = await countMessages()
    expect(captured).toEqual({ url: '/api/messages/count', method: 'get' })
    expect(value).toBe(123)
  })
})
