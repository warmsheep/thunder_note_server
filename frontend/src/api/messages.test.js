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
  countMessages,
  mergeMessages
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

  it('listMessages rejects when both flashNoteId and peerUserId are missing', async () => {
    await expect(listMessages({})).rejects.toThrow(/flashNoteId or peerUserId/)
  })

  it('listMessages with peerUserId posts peerUserId-only body', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = JSON.parse(config.data)
      return { status: 200, data: apiResponse({ records: [] }), headers: {}, config }
    })
    await listMessages({ peerUserId: 42 })
    expect(captured).toEqual({ flashNoteId: null, peerUserId: 42, page: 1, limit: 30 })
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

  it('sendMessage rejects when both flashNoteId and receiverId are missing', async () => {
    await expect(sendMessage({ content: 'hi' })).rejects.toThrow(/flashNoteId or receiverId/)
  })

  it('sendMessage with receiverId posts receiverId-only body (no flashNoteId field)', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = JSON.parse(config.data)
      return { status: 200, data: apiResponse({ id: 7 }), headers: {}, config }
    })
    await sendMessage({ receiverId: 42, content: 'hi', clientRequestId: 'cr-x' })
    // peer 模式下不应部携 flashNoteId，只携 receiverId
    expect(captured.flashNoteId).toBeUndefined()
    expect(captured.receiverId).toBe(42)
    expect(captured.content).toBe('hi')
    expect(captured.clientRequestId).toBe('cr-x')
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

  it('mergeMessages posts /api/messages/merge with title trimmed', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return { status: 200, data: apiResponse({ id: 1001, mediaType: 'COMPOSITE' }), headers: {}, config }
    })
    const card = await mergeMessages({ title: '  会议纪要  ', messageIds: [1, 2, 3], flashNoteId: 9 })
    expect(captured).toEqual({
      url: '/api/messages/merge',
      method: 'post',
      body: { title: '会议纪要', messageIds: [1, 2, 3], flashNoteId: 9, receiverId: null }
    })
    expect(card.id).toBe(1001)
  })

  it('mergeMessages rejects when title is blank', async () => {
    await expect(mergeMessages({ title: '   ', messageIds: [1], flashNoteId: 9 })).rejects.toThrow(/title/)
  })

  it('mergeMessages rejects when messageIds is empty', async () => {
    await expect(mergeMessages({ title: 'x', messageIds: [], flashNoteId: 9 })).rejects.toThrow(/messageIds/)
  })

  it('mergeMessages rejects when messageIds size > 50', async () => {
    const ids = Array.from({ length: 51 }, (_, i) => i + 1)
    await expect(mergeMessages({ title: 'x', messageIds: ids, flashNoteId: 9 })).rejects.toThrow(/<= 50/)
  })

  it('mergeMessages rejects when both flashNoteId and receiverId are missing', async () => {
    await expect(mergeMessages({ title: 'x', messageIds: [1] })).rejects.toThrow(/flashNoteId/)
  })
})
