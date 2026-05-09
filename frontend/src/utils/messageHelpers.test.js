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
  captionForMediaContent,
  textOfMessage,
  buildCardSummary
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

// D1-W21-02 textOfMessage：复制消息时取的纯文本
describe('textOfMessage', () => {
  it('text message returns content', () => {
    expect(textOfMessage({ content: 'hello' })).toBe('hello')
  })
  it('card message returns payload.title || payload.summary', () => {
    expect(
      textOfMessage({
        content: '[卡片消息]',
        payload: { cardType: 'COMPOSITE_CARD', title: '会议纪要', summary: 'xxx' }
      })
    ).toBe('会议纪要')
    expect(
      textOfMessage({
        payload: { cardType: 'COMPOSITE_CARD', summary: '只有摘要' }
      })
    ).toBe('只有摘要')
  })
  it('media message with placeholder content returns fileName', () => {
    expect(
      textOfMessage({
        content: '[图片]',
        mediaType: 'IMAGE',
        fileName: 'cat.png'
      })
    ).toBe('cat.png')
  })
  it('media message with real caption keeps caption', () => {
    expect(
      textOfMessage({
        content: '快看这只猫',
        mediaType: 'IMAGE',
        fileName: 'cat.png'
      })
    ).toBe('快看这只猫')
  })
  it('null/empty message returns empty string', () => {
    expect(textOfMessage(null)).toBe('')
    expect(textOfMessage({})).toBe('')
  })
})

// D1-W25-04 卡片 summary 智能兜底（与 Android `MessageCompositeBinder.buildSummary` 对齐）
describe('buildCardSummary', () => {
  it('payload.summary 非空 → 直接返回（保留原文，不截断）', () => {
    const payload = {
      summary: '会议纪要：\n- 讨论 W25 卡片对齐\n- 排期下周',
      items: [{ type: 'IMAGE', content: '不应被使用' }]
    }
    expect(buildCardSummary(payload)).toBe('会议纪要：\n- 讨论 W25 卡片对齐\n- 排期下周')
  })

  it('payload.summary 为空时按 items[0..2] item.content 优先拼接', () => {
    const payload = {
      summary: '',
      items: [
        { type: 'TEXT', content: '第一段内容' },
        { type: 'TEXT', content: '第二段内容' },
        { type: 'TEXT', content: '第三段内容' }
      ]
    }
    expect(buildCardSummary(payload)).toBe('第一段内容\n第二段内容\n第三段内容')
  })

  it('item.content 为空 → 用 type 占位符（[图片]/[视频]/[文件]/[语音]）', () => {
    const payload = {
      items: [
        { type: 'IMAGE' },
        { type: 'video' }, // 大小写不敏感
        { type: 'FILE' }
      ]
    }
    expect(buildCardSummary(payload)).toBe('[图片]\n[视频]\n[文件]')
  })

  it('VOICE 与 AUDIO 都映射成 [语音]', () => {
    const a = { items: [{ type: 'VOICE' }] }
    const b = { items: [{ type: 'AUDIO' }] }
    expect(buildCardSummary(a)).toBe('[语音]')
    expect(buildCardSummary(b)).toBe('[语音]')
  })

  it('items 多于 3 时只取前 3 行', () => {
    const payload = {
      items: [
        { type: 'IMAGE' },
        { type: 'IMAGE' },
        { type: 'IMAGE' },
        { type: 'IMAGE' },
        { type: 'IMAGE' }
      ]
    }
    expect(buildCardSummary(payload)).toBe('[图片]\n[图片]\n[图片]')
  })

  it('items 为空 / 缺失 → 返回空串', () => {
    expect(buildCardSummary(null)).toBe('')
    expect(buildCardSummary({})).toBe('')
    expect(buildCardSummary({ summary: '   ', items: [] })).toBe('')
  })

  it('TEXT item 的 content 与媒体 item 占位混合，保持顺序', () => {
    const payload = {
      items: [
        { type: 'TEXT', content: '前言' },
        { type: 'IMAGE' },
        { type: 'FILE', fileName: 'spec.pdf' } // fileName 不参与 fallback，应该走 [文件]
      ]
    }
    expect(buildCardSummary(payload)).toBe('前言\n[图片]\n[文件]')
  })
})
