import { describe, it, expect } from 'vitest'
import {
  buildAvatarUrl,
  extractObjectName,
  needsAuthenticatedFetch,
  isEmojiAvatar
} from './avatarHelpers'

describe('buildAvatarUrl', () => {
  it('returns empty string for falsy objectName', () => {
    expect(buildAvatarUrl('')).toBe('')
    expect(buildAvatarUrl(null)).toBe('')
  })

  it('packs objectName into download URL with explicit origin', () => {
    expect(buildAvatarUrl('u1/abc.png', 'http://example.com')).toBe(
      'http://example.com/api/files/download?objectName=u1%2Fabc.png'
    )
  })

  it('uses window.location.origin when origin not provided', () => {
    const url = buildAvatarUrl('u1/abc.png')
    expect(url.endsWith('/api/files/download?objectName=u1%2Fabc.png')).toBe(true)
  })
})

describe('extractObjectName', () => {
  it('returns null for empty input', () => {
    expect(extractObjectName('')).toBeNull()
    expect(extractObjectName(null)).toBeNull()
  })

  it('extracts from absolute download URL', () => {
    expect(
      extractObjectName('http://example.com/api/files/download?objectName=u1%2Fabc.png')
    ).toBe('u1/abc.png')
  })

  it('extracts from path-only download URL', () => {
    expect(extractObjectName('/api/files/download?objectName=u1%2Fabc.png')).toBe('u1/abc.png')
  })

  it('returns raw string when input is bare objectName', () => {
    expect(extractObjectName('u1/abc.png')).toBe('u1/abc.png')
  })

  it('returns null for unrelated URLs (e.g. cdn.example.com)', () => {
    expect(extractObjectName('https://cdn.example.com/avatars/a.png')).toBeNull()
  })
})

describe('needsAuthenticatedFetch', () => {
  it('true for our download URLs and bare objectNames', () => {
    expect(needsAuthenticatedFetch('http://h/api/files/download?objectName=x')).toBe(true)
    expect(needsAuthenticatedFetch('/api/files/download?objectName=x')).toBe(true)
    expect(needsAuthenticatedFetch('u1/abc.png')).toBe(true)
  })
  it('false for unrelated absolute URLs', () => {
    expect(needsAuthenticatedFetch('https://cdn.example.com/avatar.png')).toBe(false)
  })
  it('false for empty', () => {
    expect(needsAuthenticatedFetch('')).toBe(false)
    expect(needsAuthenticatedFetch(null)).toBe(false)
  })
  // D1-W23-01 emoji 视为非对象名，不走鉴权 fetch
  it('false for emoji-like short strings', () => {
    expect(needsAuthenticatedFetch('💼')).toBe(false)
    expect(needsAuthenticatedFetch('🌟')).toBe(false)
    expect(needsAuthenticatedFetch('😊')).toBe(false)
  })
})

describe('isEmojiAvatar', () => {
  it('matches single emoji / short strings', () => {
    expect(isEmojiAvatar('💼')).toBe(true)
    expect(isEmojiAvatar('🌟')).toBe(true)
    // 组合 emoji：❤️ 是 U+2764 + U+FE0F（2 个 code unit），在 8 字符阈值内
    expect(isEmojiAvatar('❤️')).toBe(true)
    // 空白会被 trim 掉后仍是单 emoji
    expect(isEmojiAvatar('  📷  ')).toBe(true)
  })
  it('rejects URLs and paths and object names', () => {
    expect(isEmojiAvatar('https://cdn.example.com/a.png')).toBe(false)
    expect(isEmojiAvatar('http://h/api/files/download?objectName=x')).toBe(false)
    expect(isEmojiAvatar('/api/files/download?objectName=x')).toBe(false)
    expect(isEmojiAvatar('u1/abc.png')).toBe(false)
  })
  it('rejects empty / null', () => {
    expect(isEmojiAvatar('')).toBe(false)
    expect(isEmojiAvatar(null)).toBe(false)
    expect(isEmojiAvatar('   ')).toBe(false)
  })
  it('rejects overly long strings', () => {
    expect(isEmojiAvatar('a'.repeat(9))).toBe(false)
  })
})

describe('extractObjectName edge: emoji', () => {
  // D1-W23-01 emoji 不当作 objectName
  it('returns null for emoji', () => {
    expect(extractObjectName('💼')).toBeNull()
    expect(extractObjectName('🌟')).toBeNull()
  })
})
