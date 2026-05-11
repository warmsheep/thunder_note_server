import { describe, it, expect } from 'vitest'
import { buildNavItems } from './MainShell.vue'

// D1-W26-01 主导航数据：联系人作为顶级 tab + badge 数据流测试

describe('buildNavItems', () => {
  it('共 5 项，顺序与 Android `menu_bottom_tabs.xml` 对齐', () => {
    const items = buildNavItems(0)
    expect(items.map((i) => i.name)).toEqual([
      'notes',
      'collections',
      'contacts',
      'favorites',
      'profile'
    ])
  })

  it('每一项都有 label / icon / to 字段', () => {
    const items = buildNavItems(0)
    for (const item of items) {
      expect(typeof item.label).toBe('string')
      expect(item.label.length).toBeGreaterThan(0)
      expect(typeof item.icon).toBe('string')
      expect(item.icon.length).toBeGreaterThan(0)
      expect(typeof item.to).toBe('string')
      expect(item.to.startsWith('/')).toBe(true)
    }
  })

  it('联系人项的 to=/contacts 且文案 = 联系人', () => {
    const contacts = buildNavItems(0).find((i) => i.name === 'contacts')
    expect(contacts).toBeTruthy()
    expect(contacts.to).toBe('/contacts')
    expect(contacts.label).toBe('联系人')
    expect(contacts.icon).toBe('contact')
  })

  it('pendingCount = 0 时联系人项 badge = 0（前端用 v-if="item.badge" 隐藏）', () => {
    const contacts = buildNavItems(0).find((i) => i.name === 'contacts')
    expect(contacts.badge).toBe(0)
  })

  it('pendingCount > 0 时联系人项 badge 直接透传整数值', () => {
    const contacts = buildNavItems(7).find((i) => i.name === 'contacts')
    expect(contacts.badge).toBe(7)
  })

  it('非数字 / 负值 / 小数 pendingCount 都被规范化', () => {
    expect(buildNavItems(null).find((i) => i.name === 'contacts').badge).toBe(0)
    expect(buildNavItems(undefined).find((i) => i.name === 'contacts').badge).toBe(0)
    expect(buildNavItems('abc').find((i) => i.name === 'contacts').badge).toBe(0)
    expect(buildNavItems(-3).find((i) => i.name === 'contacts').badge).toBe(0)
    expect(buildNavItems(2.7).find((i) => i.name === 'contacts').badge).toBe(2)
  })

  it('pendingCount 不影响其他 4 项，且这些项没有 badge 字段', () => {
    const items = buildNavItems(99)
    for (const item of items) {
      if (item.name !== 'contacts') {
        expect(item.badge).toBeUndefined()
      }
    }
  })

  it('品牌项独立存在：sidebar-brand 的 ⚡ 闪记 与导航 5 项不冲突（不在 navItems 内）', () => {
    const items = buildNavItems(0)
    expect(items.find((i) => i.icon === '⚡')).toBeUndefined()
  })

  it('搜索不在底部导航项中，搜索入口由闪记页右上角承担', () => {
    const items = buildNavItems(0)
    expect(items.find((i) => i.name === 'search')).toBeUndefined()
  })
})
