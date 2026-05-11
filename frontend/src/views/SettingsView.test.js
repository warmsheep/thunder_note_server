import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const source = readFileSync(join(process.cwd(), 'src/views/SettingsView.vue'), 'utf8')

describe('SettingsView UI placement', () => {
  it('设置页不再展示修改密码入口', () => {
    expect(source).not.toContain("router.push({ name: 'change-password' })")
    expect(source).not.toContain('修改密码')
    expect(source).toContain('关于 Thunder Note')
  })
})
