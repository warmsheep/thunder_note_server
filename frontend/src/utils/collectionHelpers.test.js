import { describe, it, expect } from 'vitest'
import {
  validateCollectionName,
  validateCollectionForm,
  groupNotesByCollection,
  countNotesInCollection,
  listNotesInCollection,
  UNCATEGORIZED_KEY
} from './collectionHelpers'

describe('validateCollectionName', () => {
  it('rejects empty when required', () => {
    expect(validateCollectionName('').ok).toBe(false)
    expect(validateCollectionName('   ').ok).toBe(false)
  })
  it('allows empty when not required', () => {
    expect(validateCollectionName('', { required: false }).ok).toBe(true)
  })
  it('rejects > 255 chars', () => {
    expect(validateCollectionName('x'.repeat(256)).ok).toBe(false)
  })
  it('accepts valid name', () => {
    expect(validateCollectionName('工作').ok).toBe(true)
  })
})

describe('validateCollectionForm', () => {
  it('walks name field', () => {
    expect(validateCollectionForm({ name: '' }).ok).toBe(false)
    expect(validateCollectionForm({ name: '工作' }).ok).toBe(true)
  })
})

describe('UNCATEGORIZED_KEY', () => {
  it('is a stable sentinel string', () => {
    expect(typeof UNCATEGORIZED_KEY).toBe('string')
    expect(UNCATEGORIZED_KEY.length).toBeGreaterThan(0)
  })
})

describe('groupNotesByCollection', () => {
  const collections = [
    { id: 1, name: '工作' },
    { id: 2, name: '生活' }
  ]

  it('returns empty maps when collections is missing', () => {
    const r = groupNotesByCollection([], null)
    expect(r.byCollectionId.size).toBe(0)
    expect(r.uncategorized).toEqual([])
  })

  it('groups notes by tags == collection.name', () => {
    const notes = [
      { id: 1, title: 'a', tags: '工作' },
      { id: 2, title: 'b', tags: '生活' },
      { id: 3, title: 'c', tags: '工作' },
      { id: 4, title: 'd', tags: null },
      { id: 5, title: 'e', tags: '不存在' }
    ]
    const r = groupNotesByCollection(notes, collections)
    expect(r.byCollectionId.get(1).map((n) => n.id)).toEqual([1, 3])
    expect(r.byCollectionId.get(2).map((n) => n.id)).toEqual([2])
    expect(r.uncategorized.map((n) => n.id)).toEqual([4, 5])
  })

  it('skips inbox notes and deleted notes', () => {
    const notes = [
      { id: 1, title: 'inbox', inbox: true, tags: '工作' },
      { id: 2, title: 'gone', deleted: true, tags: '工作' },
      { id: 3, title: 'ok', tags: '工作' }
    ]
    const r = groupNotesByCollection(notes, collections)
    expect(r.byCollectionId.get(1).map((n) => n.id)).toEqual([3])
    expect(r.uncategorized).toEqual([])
  })
})

describe('countNotesInCollection / listNotesInCollection', () => {
  const notes = [
    { id: 1, tags: '工作' },
    { id: 2, tags: '工作' },
    { id: 3, tags: '生活' },
    { id: 4, tags: null },
    { id: 5, tags: '工作', inbox: true },
    { id: 6, tags: '工作', deleted: true }
  ]
  it('countNotesInCollection counts active notes only', () => {
    expect(countNotesInCollection(notes, '工作')).toBe(2)
    expect(countNotesInCollection(notes, '生活')).toBe(1)
    expect(countNotesInCollection(notes, '其他')).toBe(0)
  })
  it('listNotesInCollection returns matching notes', () => {
    expect(listNotesInCollection(notes, '工作').map((n) => n.id)).toEqual([1, 2])
  })
  it('handles missing inputs gracefully', () => {
    expect(countNotesInCollection(null, '工作')).toBe(0)
    expect(listNotesInCollection([], '工作')).toEqual([])
    expect(countNotesInCollection(notes, '')).toBe(0)
  })
})
