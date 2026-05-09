import { describe, it, expect } from 'vitest'
import {
  INBOX_FLASH_NOTE_ID,
  isInboxFlashNoteId,
  isOwnMessage,
  compareByCreatedAtAsc,
  normalizePageRecords,
  newClientRequestId,
  createOptimisticMessage,
  mergeOlderRecords,
  buildInitialMessages,
  replaceOptimisticByClientId,
  isMediaPlaceholderContent,
  captionForMediaContent
} from './messageHelpers'

describe('inbox helpers', () => {
  it('INBOX_FLASH_NOTE_ID is -1', () => {
    expect(INBOX_FLASH_NOTE_ID).toBe(-1)
  })
  it('isInboxFlashNoteId handles number / string / null', () => {
    expect(isInboxFlashNoteId(-1)).toBe(true)
    expect(isInboxFlashNoteId('-1')).toBe(true)
    expect(isInboxFlashNoteId(0)).toBe(false)
    expect(isInboxFlashNoteId(1)).toBe(false)
    expect(isInboxFlashNoteId(null)).toBe(false)
  })
})

describe('media placeholder content', () => {
  it('isMediaPlaceholderContent matches backend [图片]/[视频]/[语音]/[文件]/[卡片消息]', () => {
    expect(isMediaPlaceholderContent('[图片]')).toBe(true)
    expect(isMediaPlaceholderContent('[视频]')).toBe(true)
    expect(isMediaPlaceholderContent('[语音]')).toBe(true)
    expect(isMediaPlaceholderContent('[音频]')).toBe(true)
    expect(isMediaPlaceholderContent('[文件]')).toBe(true)
    expect(isMediaPlaceholderContent('[卡片消息]')).toBe(true)
    expect(isMediaPlaceholderContent('  [图片]  ')).toBe(true) // 容忍前后空白
  })
  it('isMediaPlaceholderContent does NOT match real text containing [图片]', () => {
    expect(isMediaPlaceholderContent('我刚发了 [图片]，请查收')).toBe(false)
    expect(isMediaPlaceholderContent('[图片]这是说明')).toBe(false)
    expect(isMediaPlaceholderContent('hello')).toBe(false)
    expect(isMediaPlaceholderContent('')).toBe(false)
    expect(isMediaPlaceholderContent(null)).toBe(false)
    expect(isMediaPlaceholderContent(undefined)).toBe(false)
  })
  it('captionForMediaContent strips placeholder, keeps real captions', () => {
    expect(captionForMediaContent('[图片]')).toBe('')
    expect(captionForMediaContent('   [视频]   ')).toBe('')
    expect(captionForMediaContent('hello world')).toBe('hello world')
    expect(captionForMediaContent('  hello  ')).toBe('hello')
    expect(captionForMediaContent('')).toBe('')
    expect(captionForMediaContent(null)).toBe('')
  })
})

describe('isOwnMessage', () => {
  it('returns true when senderId equals currentUserId', () => {
    expect(isOwnMessage({ senderId: 5 }, 5)).toBe(true)
    expect(isOwnMessage({ senderId: '5' }, 5)).toBe(true)
  })
  it('returns false when ids differ or missing', () => {
    expect(isOwnMessage({ senderId: 5 }, 6)).toBe(false)
    expect(isOwnMessage({}, 5)).toBe(false)
    expect(isOwnMessage(null, 5)).toBe(false)
    expect(isOwnMessage({ senderId: 5 }, null)).toBe(false)
  })
})

describe('compareByCreatedAtAsc', () => {
  it('orders by createdAt ascending', () => {
    const a = { createdAt: '2026-01-01T10:00:00' }
    const b = { createdAt: '2026-01-01T09:00:00' }
    expect(compareByCreatedAtAsc(a, b) > 0).toBe(true)
  })
  it('uses id tiebreaker when createdAt equal', () => {
    const a = { createdAt: '2026-01-01T10:00:00', id: 2 }
    const b = { createdAt: '2026-01-01T10:00:00', id: 1 }
    expect(compareByCreatedAtAsc(a, b) > 0).toBe(true)
  })
})

describe('normalizePageRecords', () => {
  it('returns empty array for non-array input', () => {
    expect(normalizePageRecords(null)).toEqual([])
    expect(normalizePageRecords(undefined)).toEqual([])
  })
  it('sorts records ascending by createdAt', () => {
    const records = [
      { id: 1, createdAt: '2026-01-02T00:00:00' },
      { id: 2, createdAt: '2026-01-01T00:00:00' }
    ]
    expect(normalizePageRecords(records).map((m) => m.id)).toEqual([2, 1])
  })
})

describe('newClientRequestId', () => {
  it('returns a non-empty string', () => {
    const id = newClientRequestId()
    expect(typeof id).toBe('string')
    expect(id.length).toBeGreaterThan(5)
  })
  it('returns different ids on consecutive calls', () => {
    expect(newClientRequestId()).not.toBe(newClientRequestId())
  })
})

describe('createOptimisticMessage', () => {
  it('produces a local pending message with the given fields', () => {
    const m = createOptimisticMessage({
      flashNoteId: 7,
      content: 'hello',
      clientRequestId: 'cr-1',
      currentUserId: 9
    })
    expect(m.flashNoteId).toBe(7)
    expect(m.content).toBe('hello')
    expect(m.clientRequestId).toBe('cr-1')
    expect(m.senderId).toBe(9)
    expect(m.__status).toBe('pending')
    expect(m.__local).toBe(true)
    expect(m.id).toBeNull()
  })
})

describe('mergeOlderRecords', () => {
  it('prepends older records and keeps original order', () => {
    const messages = [
      { id: 3, createdAt: '2026-01-03T00:00:00' },
      { id: 4, createdAt: '2026-01-04T00:00:00' }
    ]
    const older = [
      { id: 2, createdAt: '2026-01-02T00:00:00' },
      { id: 1, createdAt: '2026-01-01T00:00:00' }
    ]
    expect(mergeOlderRecords(messages, older).map((m) => m.id)).toEqual([1, 2, 3, 4])
  })
  it('deduplicates by id', () => {
    const messages = [{ id: 3 }]
    const older = [{ id: 3 }, { id: 2 }]
    expect(mergeOlderRecords(messages, older).map((m) => m.id)).toEqual([2, 3])
  })
})

describe('buildInitialMessages', () => {
  it('sorts ascending', () => {
    const records = [
      { id: 2, createdAt: '2026-01-02T00:00:00' },
      { id: 1, createdAt: '2026-01-01T00:00:00' }
    ]
    expect(buildInitialMessages(records).map((m) => m.id)).toEqual([1, 2])
  })
})

describe('replaceOptimisticByClientId', () => {
  it('replaces local pending by clientRequestId', () => {
    const messages = [
      { id: 1 },
      { __local: true, clientRequestId: 'cr-x', content: 'hi' }
    ]
    const server = { id: 99, clientRequestId: 'cr-x', content: 'hi' }
    const next = replaceOptimisticByClientId(messages, server)
    expect(next[1]).toEqual(server)
    expect(next[1].__local).toBeUndefined()
  })
  it('appends when no matching pending and id not seen', () => {
    const messages = [{ id: 1 }]
    const server = { id: 2 }
    const next = replaceOptimisticByClientId(messages, server)
    expect(next.map((m) => m.id)).toEqual([1, 2])
  })
  it('skips appending when id already exists', () => {
    const messages = [{ id: 1 }]
    const server = { id: 1 }
    const next = replaceOptimisticByClientId(messages, server)
    expect(next).toHaveLength(1)
  })
})
