import { describe, it, expect } from 'vitest'
import {
  validateTitle,
  validateIcon,
  validateCreateForm,
  validateUpdateForm,
  toggleSingleTag
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

// D1-W28-02 NoteEditDialog 单选合集 chip：toggleSingleTag 是其点击行为的纯函数版本
describe('toggleSingleTag', () => {
  it('当前为空 + 选某 chip → 切到该 chip', () => {
    expect(toggleSingleTag('', '工作')).toBe('工作')
    expect(toggleSingleTag(null, '生活')).toBe('生活')
    expect(toggleSingleTag(undefined, '阅读')).toBe('阅读')
  })
  it('当前为 A + 选 B → 切到 B', () => {
    expect(toggleSingleTag('工作', '生活')).toBe('生活')
  })
  it('当前为 A + 再选 A → 清空（即「不分入合集」）', () => {
    expect(toggleSingleTag('工作', '工作')).toBe('')
  })
  it('选「无」（空字符串 chip）→ 始终回到空', () => {
    expect(toggleSingleTag('工作', '')).toBe('')
    expect(toggleSingleTag('', '')).toBe('')
    expect(toggleSingleTag(null, '')).toBe('')
  })
  it('数字 / 非字符串入参也安全转字符串', () => {
    expect(toggleSingleTag(123, '123')).toBe('')
    expect(toggleSingleTag('1', 1)).toBe('')
  })
})
