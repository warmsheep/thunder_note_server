import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const source = readFileSync(join(process.cwd(), 'src/views/ProfileView.vue'), 'utf8')

describe('ProfileView UI placement', () => {
  it('橙色资料头部包含个人简介展示', () => {
    expect(source).toContain('class="profile-bio"')
    expect(source.indexOf('class="profile-bio"')).toBeLessThan(source.indexOf('</header>'))
    expect(source).not.toContain('<template v-if="!editing">')
  })

  it('我的页面直接提供修改密码入口', () => {
    expect(source).toContain("$router.push({ name: 'change-password' })")
    expect(source).toContain('修改密码')
  })
})
