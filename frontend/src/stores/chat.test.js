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

  it('send coerces non-string content (e.g. {text,file} object) without throwing trim is not a function', async () => {
    // 回归保护：曾经 ChatView 把 MessageComposer 的 {text, file} payload 整个当作 content 传进来，
    // 导致 (content || '').trim() 报 "trim is not a function"。这里验证 store 已加 String() 兜底。
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    messagesApi.sendMessage.mockResolvedValueOnce({ id: 999, flashNoteId: 7, content: '' })
    const store = useChatStore()
    await store.openConversation(7)
    // 用对象当 content + 提供 media，应不抛 trim 错误，且能成功发送
    await expect(store.send({
      content: { text: 'hi', file: null },
      currentUserId: 1,
      media: { mediaType: 'file', mediaUrl: 'u/1.bin', fileName: 'a.bin', fileSize: 1 }
    })).resolves.toBeDefined()
    expect(messagesApi.sendMessage).toHaveBeenCalledTimes(1)
  })

  it('send rejects empty content with no media (still validates)', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const store = useChatStore()
    await store.openConversation(7)
    await expect(store.send({ content: '   ', currentUserId: 1 })).rejects.toThrow(/不能为空/)
    await expect(store.send({ content: null, currentUserId: 1 })).rejects.toThrow(/不能为空/)
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
      flashNoteId: 7,
      receiverId: null
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

  it('forwardSelected refuses when no target chosen (flash and peer both null)', async () => {
    messagesApi.listMessages.mockResolvedValueOnce(pageOf([msg(1, 'a', '2026-01-01T00:00:00')]))
    const store = useChatStore()
    await store.openConversation(7)
    store.enterSelectMode()
    store.toggleSelect(1)
    await expect(
      store.forwardSelected({ targetFlashNoteId: null, targetPeerUserId: null })
    ).rejects.toThrow(/转发目标/)
    expect(messagesApi.sendMessage).not.toHaveBeenCalled()
  })

  // D1-W20-03 联系人 1v1 模式
  describe('peer mode (D1-W20)', () => {
    it('openConversation({ peerUserId }) 走 peer 模式，listMessages 传 peerUserId，mode = peer', async () => {
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([
        msg(101, 'hi', '2026-02-01T00:00:00')
      ], 1, 1))
      const store = useChatStore()
      await store.openConversation({ peerUserId: 42 })
      expect(messagesApi.listMessages).toHaveBeenCalledWith({
        flashNoteId: null,
        peerUserId: 42,
        page: 1,
        limit: 30
      })
      expect(store.peerUserId).toBe(42)
      expect(store.flashNoteId).toBeNull()
      expect(store.mode).toBe('peer')
      expect(store.isPeerMode).toBe(true)
      expect(store.isFlashMode).toBe(false)
      expect(store.conversationKey).toBe('peer:42')
      expect(store.isInbox).toBe(false)
    })

    it('flash 模式 conversationKey 为 fn:<id>，收集箱为 fn:-1', async () => {
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([], 1, 0))
      const store = useChatStore()
      await store.openConversation({ flashNoteId: -1 })
      expect(store.conversationKey).toBe('fn:-1')
      expect(store.isInbox).toBe(true)
    })

    it('peer 模式 send 会传 receiverId 不传 flashNoteId', async () => {
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([], 1, 0))
      messagesApi.sendMessage.mockResolvedValueOnce({
        id: 200,
        clientRequestId: 'cr-x',
        senderId: 1,
        receiverId: 42,
        content: 'hi'
      })
      const store = useChatStore()
      await store.openConversation({ peerUserId: 42 })
      await store.send({ content: 'hi', currentUserId: 1 })
      expect(messagesApi.sendMessage).toHaveBeenCalledTimes(1)
      const args = messagesApi.sendMessage.mock.calls[0][0]
      expect(args.flashNoteId).toBeNull()
      expect(args.receiverId).toBe(42)
      expect(args.content).toBe('hi')
    })

    it('peer 模式 loadMore 会传 peerUserId', async () => {
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([
        msg(10, 'a', '2026-01-01T00:00:00'),
        msg(11, 'b', '2026-01-02T00:00:00')
      ], 1, 2))
      const store = useChatStore()
      await store.openConversation({ peerUserId: 42 })
      // 人工推到还有第 2 页
      store.pages = 2
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([
        msg(8, 'older', '2025-12-31T00:00:00')
      ], 2, 3))
      await store.loadMore()
      const lastCall = messagesApi.listMessages.mock.calls.at(-1)[0]
      expect(lastCall.peerUserId).toBe(42)
      expect(lastCall.flashNoteId).toBeNull()
      expect(lastCall.page).toBe(2)
    })

    it('peer 模式 mergeSelected 传 receiverId，不传 flashNoteId', async () => {
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([
        msg(1, 'a', '2026-01-01T00:00:00'),
        msg(2, 'b', '2026-01-02T00:00:00')
      ], 1, 2))
      const card = {
        id: 999,
        mediaType: 'COMPOSITE',
        receiverId: 42,
        content: 'merged',
        payload: { cardType: 'MESSAGE_COLLECTION', title: 'merged' }
      }
      messagesApi.mergeMessages.mockResolvedValueOnce(card)
      const store = useChatStore()
      await store.openConversation({ peerUserId: 42 })
      store.enterSelectMode()
      store.toggleSelect(1)
      store.toggleSelect(2)
      await store.mergeSelected({ title: 'merged' })
      expect(messagesApi.mergeMessages).toHaveBeenCalledWith({
        title: 'merged',
        messageIds: [1, 2],
        flashNoteId: null,
        receiverId: 42
      })
    })

    it('forwardSelected 支持 targetPeerUserId（联系人作为转发目标）', async () => {
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([
        msg(1, 'a', '2026-01-01T00:00:00')
      ], 1, 1))
      messagesApi.sendMessage.mockResolvedValueOnce({ id: 1001, content: 'a' })
      const store = useChatStore()
      await store.openConversation(7)
      store.enterSelectMode()
      store.toggleSelect(1)
      const r = await store.forwardSelected({ targetPeerUserId: 99 })
      expect(messagesApi.sendMessage).toHaveBeenCalledTimes(1)
      const args = messagesApi.sendMessage.mock.calls[0][0]
      expect(args.flashNoteId).toBeNull()
      expect(args.receiverId).toBe(99)
      expect(r.successCount).toBe(1)
    })

    it('peer 模式 clearInbox 拒绝（只有闪记收集箱才能清空）', async () => {
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([], 1, 0))
      const store = useChatStore()
      await store.openConversation({ peerUserId: 42 })
      await expect(store.clearInbox()).rejects.toThrow(/收集箱/)
    })

    it('reset 同时清空 flashNoteId 和 peerUserId', async () => {
      messagesApi.listMessages.mockResolvedValueOnce(pageOf([], 1, 0))
      const store = useChatStore()
      await store.openConversation({ peerUserId: 42 })
      store.reset()
      expect(store.peerUserId).toBeNull()
      expect(store.flashNoteId).toBeNull()
      expect(store.mode).toBeNull()
      expect(store.conversationKey).toBeNull()
    })
  })
})
