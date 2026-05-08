import { describe, it, expect } from 'vitest'
import {
  validateTitle,
  validateIcon,
  validateCreateForm,
  validateUpdateForm
} from './flashNoteValidators'

describe('validateTitle', () => {
  it('rejects empty when required', () => {
    expect(validateTitle('').ok).toBe(false)
    expect(validateTitle('   ').ok).toBe(false)
    expect(validateTitle(null).ok).toBe(false)
  })
  it('allows empty when not required', () => {
    expect(validateTitle('', { required: false }).ok).toBe(true)
  })
  it('rejects over 500 chars', () => {
    expect(validateTitle('a'.repeat(501)).ok).toBe(false)
  })
  it('accepts 1-500 chars', () => {
    expect(validateTitle('hi').ok).toBe(true)
    expect(validateTitle('a'.repeat(500)).ok).toBe(true)
  })
})

describe('validateIcon', () => {
  it('accepts empty', () => {
    expect(validateIcon('').ok).toBe(true)
    expect(validateIcon(null).ok).toBe(true)
  })
  it('rejects > 64 chars', () => {
    expect(validateIcon('x'.repeat(65)).ok).toBe(false)
  })
  it('accepts emoji or short string', () => {
    expect(validateIcon('📝').ok).toBe(true)
    expect(validateIcon('icon').ok).toBe(true)
  })
})

describe('validateCreateForm', () => {
  it('rejects when title is empty', () => {
    expect(validateCreateForm({ title: '', icon: '' }).ok).toBe(false)
  })
  it('passes minimum required', () => {
    expect(validateCreateForm({ title: '我的笔记' }).ok).toBe(true)
  })
  it('walks fields in order: title → icon', () => {
    expect(validateCreateForm({ title: '', icon: '📝' }).message).toMatch(/名称/)
    expect(validateCreateForm({ title: 'ok', icon: 'x'.repeat(65) }).message).toMatch(/图标/)
  })
})

describe('validateUpdateForm', () => {
  it('passes when all fields omitted (partial update)', () => {
    expect(validateUpdateForm({}).ok).toBe(true)
  })
  it('rejects when title explicitly set to blank', () => {
    expect(validateUpdateForm({ title: '   ' }).ok).toBe(false)
  })
  it('rejects when title too long', () => {
    expect(validateUpdateForm({ title: 'a'.repeat(501) }).ok).toBe(false)
  })
  it('accepts undefined title (means no change)', () => {
    expect(validateUpdateForm({ icon: '📝' }).ok).toBe(true)
  })
})
