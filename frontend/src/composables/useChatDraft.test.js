import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  loadDraft,
  saveDraft,
  clearDraft,
  __resetChatDraftForTests,
  __DRAFT_PREFIX
} from './useChatDraft'

describe('useChatDraft', () => {
  beforeEach(() => {
    sessionStorage.clear()
    __resetChatDraftForTests()
  })

  it('按 conversationKey 写入、读取并清理 sessionStorage 草稿', () => {
    saveDraft('fn:7', '未发送文本', 'user:1')
    expect(sessionStorage.getItem(`${__DRAFT_PREFIX}user:1:fn:7`)).toBe('未发送文本')
    expect(loadDraft('fn:7', 'user:1')).toBe('未发送文本')

    clearDraft('fn:7', 'user:1')
    expect(loadDraft('fn:7', 'user:1')).toBe('')
    expect(sessionStorage.getItem(`${__DRAFT_PREFIX}user:1:fn:7`)).toBeNull()
  })

  it('空字符串等同删除，避免残留空键', () => {
    saveDraft('peer:9', 'hello', 'user:2')
    saveDraft('peer:9', '', 'user:2')
    expect(loadDraft('peer:9', 'user:2')).toBe('')
    expect(sessionStorage.getItem(`${__DRAFT_PREFIX}user:2:peer:9`)).toBeNull()
  })

  it('sessionStorage 不可用时回退到内存 Map', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied')
    })
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied')
    })
    const removeItem = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('denied')
    })

    saveDraft('fn:11', 'fallback', 'user:3')
    expect(loadDraft('fn:11', 'user:3')).toBe('fallback')
    clearDraft('fn:11', 'user:3')
    expect(loadDraft('fn:11', 'user:3')).toBe('')

    setItem.mockRestore()
    getItem.mockRestore()
    removeItem.mockRestore()
  })

  it('相同会话 key 在不同用户 scope 下互不污染', () => {
    saveDraft('fn:7', 'alice draft', 'user:alice')
    saveDraft('fn:7', 'bob draft', 'user:bob')

    expect(loadDraft('fn:7', 'user:alice')).toBe('alice draft')
    expect(loadDraft('fn:7', 'user:bob')).toBe('bob draft')
  })
})
