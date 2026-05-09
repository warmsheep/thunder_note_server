import { describe, it, expect } from 'vitest'
import { applyInlineToken, toggleLinePrefix } from './markdownEditor'

// D1-W25-03 markdown 工具栏纯函数测试

describe('applyInlineToken', () => {
  it('选中文本时在两侧插入，selection 包住整个新片段', () => {
    const r = applyInlineToken({
      text: 'hello world',
      selStart: 6,
      selEnd: 11,
      prefix: '**',
      suffix: '**'
    })
    expect(r.text).toBe('hello **world**')
    expect(r.selStart).toBe(6)
    expect(r.selEnd).toBe(15)
  })

  it('未选中时光标处插入 prefix+suffix，光标停在中间', () => {
    const r = applyInlineToken({
      text: 'hi',
      selStart: 2,
      selEnd: 2,
      prefix: '*',
      suffix: '*'
    })
    expect(r.text).toBe('hi**')
    // 光标在第一个 * 之后
    expect(r.selStart).toBe(3)
    expect(r.selEnd).toBe(3)
  })

  it('selStart > selEnd 时容错（视为一个区间，end 取大）', () => {
    const r = applyInlineToken({
      text: 'abc',
      selStart: 3,
      selEnd: 0,
      prefix: '**',
      suffix: '**'
    })
    // start=3, end=max(3, 0)=3，所以未选中
    expect(r.text).toBe('abc****')
    expect(r.selStart).toBe(5)
    expect(r.selEnd).toBe(5)
  })

  it('空字符串 + 默认参数', () => {
    const r = applyInlineToken({})
    expect(r.text).toBe('')
    expect(r.selStart).toBe(0)
    expect(r.selEnd).toBe(0)
  })

  it('selection 越界自动 clamp 到字符串长度内', () => {
    const r = applyInlineToken({
      text: 'abc',
      selStart: 999,
      selEnd: 999,
      prefix: '**',
      suffix: '**'
    })
    expect(r.text).toBe('abc****')
    expect(r.selStart).toBe(5) // start=3 clamp，光标在 prefix 后
  })
})

describe('toggleLinePrefix', () => {
  it('单行无 prefix → 添加；再调用 → 移除', () => {
    const text = 'first line'
    const added = toggleLinePrefix({
      text,
      selStart: 0,
      selEnd: 0,
      prefix: '> '
    })
    expect(added.text).toBe('> first line')
    // 光标位置：第 0 行起 +2 偏移
    expect(added.selStart).toBe(2)

    const removed = toggleLinePrefix({
      text: added.text,
      selStart: added.selStart,
      selEnd: added.selEnd,
      prefix: '> '
    })
    expect(removed.text).toBe('first line')
    expect(removed.selStart).toBe(0)
  })

  it('多行选中（部分有 prefix）→ 全部加 prefix', () => {
    const text = 'a\n> b\nc'
    // 选中跨三行
    const r = toggleLinePrefix({
      text,
      selStart: 0,
      selEnd: text.length,
      prefix: '> '
    })
    // 因为不是 "全部已有 prefix"，所以加
    expect(r.text).toBe('> a\n> > b\n> c')
  })

  it('多行选中（全部已有 prefix）→ 全部移除一次 prefix', () => {
    const text = '> a\n> b\n> c'
    const r = toggleLinePrefix({
      text,
      selStart: 0,
      selEnd: text.length,
      prefix: '> '
    })
    expect(r.text).toBe('a\nb\nc')
  })

  it('待办 prefix（- [ ] ）单行加 / 取消', () => {
    const t1 = toggleLinePrefix({
      text: 'task one',
      selStart: 0,
      selEnd: 0,
      prefix: '- [ ] '
    })
    expect(t1.text).toBe('- [ ] task one')

    const t2 = toggleLinePrefix({
      text: t1.text,
      selStart: 0,
      selEnd: 0,
      prefix: '- [ ] '
    })
    expect(t2.text).toBe('task one')
  })

  it('空 prefix → 直接返回原文', () => {
    const r = toggleLinePrefix({
      text: 'abc',
      selStart: 1,
      selEnd: 2,
      prefix: ''
    })
    expect(r.text).toBe('abc')
    expect(r.selStart).toBe(1)
    expect(r.selEnd).toBe(2)
  })

  it('选中区落在第二行（保持 prefix 已加）', () => {
    const text = 'first\nsecond\nthird'
    const start = 6 // 'second' 起点
    const end = 12  // 'second' 末
    const r = toggleLinePrefix({
      text,
      selStart: start,
      selEnd: end,
      prefix: '> '
    })
    // 只第二行被加 prefix
    expect(r.text).toBe('first\n> second\nthird')
  })
})
