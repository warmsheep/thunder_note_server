import { describe, it, expect } from 'vitest'
import {
  formatFileSize,
  getExtension,
  isImage,
  isVideo,
  isAudio,
  inferMediaType,
  shortenFileName
} from './fileHelpers'

describe('formatFileSize', () => {
  it('returns empty for non-numeric input', () => {
    expect(formatFileSize(null)).toBe('')
    expect(formatFileSize(undefined)).toBe('')
    expect(formatFileSize('abc')).toBe('')
  })
  it('formats bytes / KB / MB / GB', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(1024 * 1024 * 3)).toBe('3.0 MB')
    expect(formatFileSize(1024 * 1024 * 1024 * 2)).toBe('2.00 GB')
  })
})

describe('getExtension', () => {
  it('handles missing or no-ext names', () => {
    expect(getExtension('')).toBe('')
    expect(getExtension('noext')).toBe('')
  })
  it('returns lowercase extension', () => {
    expect(getExtension('a.PNG')).toBe('png')
    expect(getExtension('archive.tar.gz')).toBe('gz')
  })
})

describe('media type detection', () => {
  it('isImage by mediaType / contentType / extension', () => {
    expect(isImage({ mediaType: 'image' })).toBe(true)
    expect(isImage({ contentType: 'image/jpeg' })).toBe(true)
    expect(isImage({ fileName: 'a.png' })).toBe(true)
    expect(isImage({ fileName: 'a.txt' })).toBe(false)
    expect(isImage({})).toBe(false)
  })
  it('isVideo by mediaType / contentType / extension', () => {
    expect(isVideo({ mediaType: 'video' })).toBe(true)
    expect(isVideo({ contentType: 'video/mp4' })).toBe(true)
    expect(isVideo({ fileName: 'a.mp4' })).toBe(true)
    expect(isVideo({ fileName: 'a.png' })).toBe(false)
  })
  it('isAudio by mediaType / contentType / extension', () => {
    expect(isAudio({ mediaType: 'audio' })).toBe(true)
    expect(isAudio({ contentType: 'audio/mpeg' })).toBe(true)
    expect(isAudio({ fileName: 'a.mp3' })).toBe(true)
    expect(isAudio({ fileName: 'a.png' })).toBe(false)
  })
})

describe('inferMediaType', () => {
  it('returns "file" for null or unknown', () => {
    expect(inferMediaType(null)).toBe('file')
    expect(inferMediaType({ name: 'a.unknown', type: '' })).toBe('file')
  })
  it('returns image / video / audio for matching files', () => {
    expect(inferMediaType({ name: 'a.png', type: 'image/png' })).toBe('image')
    expect(inferMediaType({ name: 'a.mp4', type: 'video/mp4' })).toBe('video')
    expect(inferMediaType({ name: 'a.mp3', type: 'audio/mpeg' })).toBe('audio')
  })
  it('falls back to extension when MIME is missing', () => {
    expect(inferMediaType({ name: 'a.png', type: '' })).toBe('image')
  })
})

describe('shortenFileName', () => {
  it('returns name as-is when shorter than max', () => {
    expect(shortenFileName('hello.png')).toBe('hello.png')
  })
  it('preserves extension while truncating long head', () => {
    const long = 'a'.repeat(100) + '.zip'
    const out = shortenFileName(long, 20)
    expect(out.endsWith('.zip')).toBe(true)
    expect(out.length).toBeLessThanOrEqual(20)
  })
  it('handles empty input', () => {
    expect(shortenFileName('')).toBe('')
    expect(shortenFileName(null)).toBe('')
  })
})
