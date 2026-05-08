import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/messages', () => ({
  listMessages: vi.fn(),
  sendMessage: vi.fn(),
  deleteMessage: vi.fn(),
  deleteMessagesBatch: vi.fn(),
  clearInbox: vi.fn(),
  mergeMessages: vi.fn()
}))

import * as messagesApi from '../api/messages'
import { useChatStore } from './chat'

function pageOf(records, current = 1, total = records.length, pages = 1, size = 30) {
  return { records, current, size, total, pages }
}

function msg(id, content, createdAt, senderId = 1) {
  return {
    id,
    senderId,
    receiverId: 2,
    flashNoteId: 7,
    content,
    role: 'user',
    createdAt,
    clientRequestId: null
  }
}

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('openConversation loads first page and sets pagination', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')], 1, 5, 2))
    const store = useChatStore()
    await store.openConversation(7)
    expect(store.flashNoteId).toBe(7)
    expect(store.messages).toHaveLength(1)
    expect(store.page).toBe(1)
    expect(store.pages).toBe(2)
    expect(store.hasMore).toBe(true)
  })

  it('openConversation resets prior state', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const store = useChatStore()
    store.flashNoteId = 99
    store.messages = [msg(900, 'old', '2025-01-01T00:00:00')]
    await store.openConversation(7)
    expect(store.flashNoteId).toBe(7)
    expect(store.messages.map((m) => m.id)).toEqual([1])
  })

  it('loadMore prepends older records', async () => {
    messagesApi.listMessages
      .mockResolvedValueOnce(pageOf([msg(3, 'c', '2026-01-03T00:00:00')], 1, 3, 2))
      .mockResolvedValueOnce(pageOf([
        msg(1, 'a', '2026-01-01T00:00:00'),
        msg(2, 'b', '2026-01-02T00:00:00')
      ], 2, 3, 2))
    const store = useChatStore()
    await store.openConversation(7)
    await store.loadMore()
    expect(store.messages.map((m) => m.id)).toEqual([1, 2, 3])
    expect(store.hasMore).toBe(false)
  })

  it('send adds optimistic, replaces with server response on success', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([]))
    const store = useChatStore()
    await store.openConversation(7)

    let resolveSend
    const sendPromise = new Promise((r) => { resolveSend = r })
    messagesApi.sendMessage.mockReturnValueOnce(sendPromise)

    const sending = store.send({ content: 'hello', currentUserId: 1 })
    // optimistic 已经入队
    expect(store.messages).toHaveLength(1)
    expect(store.messages[0].__status).toBe('pending')
    expect(store.messages[0].content).toBe('hello')

    const cr = store.messages[0].clientRequestId
    resolveSend({ id: 99, clientRequestId: cr, content: 'hello', senderId: 1, createdAt: '2026-01-01T00:00:00' })
    await sending
    expect(store.messages[0].id).toBe(99)
    expect(store.messages[0].__local).toBeUndefined()
    expect(store.sending).toBe(false)
  })

  it('send marks optimistic failed when api rejects', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([]))
    const store = useChatStore()
    await store.openConversation(7)
    messagesApi.sendMessage.mockRejectedValueOnce(new Error('network'))
    await expect(store.send({ content: 'hi', currentUserId: 1 })).rejects.toThrow('network')
    expect(store.messages).toHaveLength(1)
    expect(store.messages[0].__status).toBe('failed')
    expect(store.messages[0].__error).toMatch(/network/)
  })

  it('send rejects empty content without calling api', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([]))
    const store = useChatStore()
    await store.openConversation(7)
    await expect(store.send({ content: '   ', currentUserId: 1 })).rejects.toThrow(/不能为空/)
    expect(messagesApi.sendMessage).not.toHaveBeenCalled()
  })

  it('retryFailed re-sends and replaces on success', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([]))
    const store = useChatStore()
    await store.openConversation(7)
    messagesApi.sendMessage.mockRejectedValueOnce(new Error('boom'))
    await expect(store.send({ content: 'hi', currentUserId: 1 })).rejects.toThrow()
    const cr = store.messages[0].clientRequestId

    messagesApi.sendMessage.mockResolvedValueOnce({
      id: 50,
      clientRequestId: cr,
      content: 'hi',
      senderId: 1,
      createdAt: '2026-01-01T00:00:00'
    })
    await store.retryFailed(cr, 1)
    expect(store.messages[0].id).toBe(50)
  })

  it('remove deletes by id', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00'), msg(2, 'b', '2026-01-02T00:00:00')]))
    const store = useChatStore()
    await store.openConversation(7)
    messagesApi.deleteMessage.mockResolvedValueOnce(null)
    await store.remove(1)
    expect(store.messages.map((m) => m.id)).toEqual([2])
  })

  it('select mode toggles ids and batchRemove sends them', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([
      msg(1, 'a', '2026-01-01T00:00:00'),
      msg(2, 'b', '2026-01-02T00:00:00'),
      msg(3, 'c', '2026-01-03T00:00:00')
    ]))
    const store = useChatStore()
    await store.openConversation(7)
    store.enterSelectMode()
    store.toggleSelect(1)
    store.toggleSelect(2)
    store.toggleSelect(2)
    store.toggleSelect(3)
    expect(Array.from(store.selectedIds).sort()).toEqual([1, 3])

    messagesApi.deleteMessagesBatch.mockResolvedValueOnce(null)
    await store.batchRemove()
    expect(messagesApi.deleteMessagesBatch).toHaveBeenCalledWith([1, 3])
    expect(store.messages.map((m) => m.id)).toEqual([2])
    expect(store.selectMode).toBe(false)
  })

  it('batchRemove without selection is a noop', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([]))
    const store = useChatStore()
    await store.openConversation(7)
    await store.batchRemove()
    expect(messagesApi.deleteMessagesBatch).not.toHaveBeenCalled()
  })

  it('reset clears state', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const store = useChatStore()
    await store.openConversation(7)
    store.reset()
    expect(store.flashNoteId).toBeNull()
    expect(store.messages).toEqual([])
    expect(store.page).toBe(0)
  })

  it('clearInbox refuses non-inbox conversations', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const store = useChatStore()
    await store.openConversation(7) // 普通闪记，flashNoteId !== -1
    await expect(store.clearInbox()).rejects.toThrow(/收集箱/)
    expect(messagesApi.clearInbox).not.toHaveBeenCalled()
  })

  it('clearInbox clears local messages and resets pagination on inbox conversation', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([
      msg(1, 'a', '2026-01-01T00:00:00'),
      msg(2, 'b', '2026-01-02T00:00:00')
    ], 1, 50, 2))
    messagesApi.clearInbox.mockResolvedValueOnce(null)
    const store = useChatStore()
    await store.openConversation(-1) // 收集箱
    expect(store.messages).toHaveLength(2)
    expect(store.hasMore).toBe(true)

    await store.clearInbox()
    expect(messagesApi.clearInbox).toHaveBeenCalledTimes(1)
    expect(store.messages).toEqual([])
    expect(store.total).toBe(0)
    expect(store.hasMore).toBe(false)
    expect(store.selectedIds.size).toBe(0)
    expect(store.selectMode).toBe(false)
  })

  it('clearInbox surfaces server error and keeps local state', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const err = new Error('boom')
    err.serverMessage = '清空失败'
    messagesApi.clearInbox.mockRejectedValueOnce(err)
    const store = useChatStore()
    await store.openConversation(-1)
    await expect(store.clearInbox()).rejects.toThrow('boom')
    expect(store.error).toBe('清空失败')
    expect(store.messages).toHaveLength(1) // 本地保留
  })

  it('mergeSelected refuses when no selection', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const store = useChatStore()
    await store.openConversation(7)
    await expect(store.mergeSelected({ title: 'x' })).rejects.toThrow(/未选择/)
    expect(messagesApi.mergeMessages).not.toHaveBeenCalled()
  })

  it('mergeSelected appends new card to messages and exits select mode', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([
      msg(1, 'a', '2026-01-01T00:00:00'),
      msg(2, 'b', '2026-01-02T00:00:00')
    ], 1, 2))
    const card = { id: 1001, mediaType: 'COMPOSITE', flashNoteId: 7, content: 'merged', payload: { cardType: 'MESSAGE_COLLECTION', title: 'merged' } }
    messagesApi.mergeMessages.mockResolvedValueOnce(card)
    const store = useChatStore()
    await store.openConversation(7)
    store.enterSelectMode()
    store.toggleSelect(1)
    store.toggleSelect(2)
    expect(store.selectedIds.size).toBe(2)

    const result = await store.mergeSelected({ title: 'merged' })
    expect(messagesApi.mergeMessages).toHaveBeenCalledWith({
      title: 'merged',
      messageIds: [1, 2],
      flashNoteId: 7
    })
    expect(result).toEqual(card)
    expect(store.messages.map((m) => m.id)).toEqual([1, 2, 1001])
    expect(store.selectMode).toBe(false)
    expect(store.selectedIds.size).toBe(0)
  })

  it('mergeSelected surfaces server error and keeps state', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const err = new Error('bad')
    err.serverMessage = '消息不属于同一会话'
    messagesApi.mergeMessages.mockRejectedValueOnce(err)
    const store = useChatStore()
    await store.openConversation(7)
    store.enterSelectMode()
    store.toggleSelect(1)
    await expect(store.mergeSelected({ title: 't' })).rejects.toThrow('bad')
    expect(store.error).toBe('消息不属于同一会话')
    expect(store.selectMode).toBe(true) // 不退出多选，便于用户调整后重试
  })

  it('forwardSelected loops sendMessage and reports successCount', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([
      msg(1, 'a', '2026-01-01T00:00:00'),
      msg(2, 'b', '2026-01-02T00:00:00')
    ]))
    messagesApi.sendMessage.mockResolvedValue({ id: 999 })
    const store = useChatStore()
    await store.openConversation(7)
    store.enterSelectMode()
    store.toggleSelect(1)
    store.toggleSelect(2)
    const r = await store.forwardSelected({ targetFlashNoteId: 88 })
    expect(messagesApi.sendMessage).toHaveBeenCalledTimes(2)
    expect(messagesApi.sendMessage.mock.calls[0][0].flashNoteId).toBe(88)
    expect(r).toEqual({ successCount: 2, failures: [] })
    expect(store.selectMode).toBe(false)
  })

  it('forwardSelected does not abort on per-message failure', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([
      msg(1, 'a', '2026-01-01T00:00:00'),
      msg(2, 'b', '2026-01-02T00:00:00'),
      msg(3, 'c', '2026-01-03T00:00:00')
    ]))
    const err = new Error('fail')
    err.serverMessage = '禁止访问'
    messagesApi.sendMessage
      .mockResolvedValueOnce({ id: 1 })
      .mockRejectedValueOnce(err)
      .mockResolvedValueOnce({ id: 3 })
    const store = useChatStore()
    await store.openConversation(7)
    store.enterSelectMode()
    store.toggleSelect(1)
    store.toggleSelect(2)
    store.toggleSelect(3)
    const r = await store.forwardSelected({ targetFlashNoteId: 88 })
    expect(r.successCount).toBe(2)
    expect(r.failures).toHaveLength(1)
    expect(r.failures[0]).toMatchObject({ originalId: 2, error: '禁止访问' })
  })

  it('forwardSelected refuses when targetFlashNoteId is missing', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const store = useChatStore()
    await store.openConversation(7)
    store.enterSelectMode()
    store.toggleSelect(1)
    await expect(store.forwardSelected({ targetFlashNoteId: null })).rejects.toThrow(/目标闪记/)
    expect(messagesApi.sendMessage).not.toHaveBeenCalled()
  })
})
