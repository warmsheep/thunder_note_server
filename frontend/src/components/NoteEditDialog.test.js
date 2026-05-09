import { describe, it, expect } from 'vitest'
import { FLASHNOTE_ICONS } from './NoteEditDialog.vue'

// D1-W26-03 闪记图标 emoji 预设：与 Android `R.array.flashnote_icons` 完全对齐
//
// Android arrays.xml 顺序（thunder_note_android/app/src/main/res/values/arrays.xml）：
//   💼 📚 ❤️ 🍀 🌟 🎯 🚀 🎨 🎵 📷 📝 💡

describe('FLASHNOTE_ICONS', () => {
  it('与 Android `R.array.flashnote_icons` 顺序与项数完全一致', () => {
    expect(FLASHNOTE_ICONS).toEqual([
      '💼', '📚', '❤️', '🍀',
      '🌟', '🎯', '🚀', '🎨',
      '🎵', '📷', '📝', '💡'
    ])
  })

  it('共 12 项，与 Android 12 项 chip 预设对齐', () => {
    expect(FLASHNOTE_ICONS).toHaveLength(12)
  })

  it('每一项都是非空字符串', () => {
    for (const emoji of FLASHNOTE_ICONS) {
      expect(typeof emoji).toBe('string')
      expect(emoji.length).toBeGreaterThan(0)
    }
  })
})
