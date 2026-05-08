import { describe, it, expect } from 'vitest'
import {
  validateUsername,
  validatePasswordRequired,
  validatePasswordStrength,
  validateEmail,
  validateConfirmPassword,
  validateLoginForm,
  validateRegisterForm
} from './validators'

describe('validateUsername', () => {
  it('rejects empty', () => {
    expect(validateUsername('').ok).toBe(false)
    expect(validateUsername('   ').ok).toBe(false)
  })
  it('rejects too short or too long', () => {
    expect(validateUsername('ab').ok).toBe(false)
    expect(validateUsername('a'.repeat(33)).ok).toBe(false)
  })
  it('accepts 3-32 chars', () => {
    expect(validateUsername('abc').ok).toBe(true)
    expect(validateUsername('a'.repeat(32)).ok).toBe(true)
  })
})

describe('validatePasswordRequired', () => {
  it('rejects empty', () => {
    expect(validatePasswordRequired('').ok).toBe(false)
  })
  it('accepts any non-empty', () => {
    expect(validatePasswordRequired('x').ok).toBe(true)
  })
})

describe('validatePasswordStrength', () => {
  it('rejects under 6 chars', () => {
    expect(validatePasswordStrength('12345').ok).toBe(false)
  })
  it('rejects over 128 chars', () => {
    expect(validatePasswordStrength('a'.repeat(129)).ok).toBe(false)
  })
  it('accepts 6-128 chars', () => {
    expect(validatePasswordStrength('123456').ok).toBe(true)
    expect(validatePasswordStrength('a'.repeat(128)).ok).toBe(true)
  })
})

describe('validateEmail', () => {
  it('rejects empty', () => {
    expect(validateEmail('').ok).toBe(false)
  })
  it('rejects malformed', () => {
    expect(validateEmail('foo').ok).toBe(false)
    expect(validateEmail('foo@').ok).toBe(false)
    expect(validateEmail('foo@bar').ok).toBe(false)
  })
  it('accepts valid', () => {
    expect(validateEmail('a@b.com').ok).toBe(true)
    expect(validateEmail('user.name+tag@example.co.uk').ok).toBe(true)
  })
})

describe('validateConfirmPassword', () => {
  it('rejects empty confirm', () => {
    expect(validateConfirmPassword('abcdef', '').ok).toBe(false)
  })
  it('rejects mismatch', () => {
    expect(validateConfirmPassword('abcdef', 'abcdeg').ok).toBe(false)
  })
  it('accepts equal non-empty', () => {
    expect(validateConfirmPassword('abcdef', 'abcdef').ok).toBe(true)
  })
})

describe('validateLoginForm', () => {
  it('returns first failing field', () => {
    expect(validateLoginForm({ username: '', password: 'p' }).message).toMatch(/用户名/)
    expect(validateLoginForm({ username: 'alice', password: '' }).message).toMatch(/密码/)
  })
  it('passes with valid pair', () => {
    expect(validateLoginForm({ username: 'alice', password: 'p' }).ok).toBe(true)
  })
})

describe('validateRegisterForm', () => {
  it('walks fields in order: username → email → password → confirm', () => {
    expect(validateRegisterForm({ username: '', email: 'a@b.com', password: '123456', confirmPassword: '123456' }).message).toMatch(/用户名/)
    expect(validateRegisterForm({ username: 'alice', email: 'bad', password: '123456', confirmPassword: '123456' }).message).toMatch(/邮箱/)
    expect(validateRegisterForm({ username: 'alice', email: 'a@b.com', password: '12', confirmPassword: '12' }).message).toMatch(/密码/)
    expect(validateRegisterForm({ username: 'alice', email: 'a@b.com', password: '123456', confirmPassword: '654321' }).message).toMatch(/不一致/)
  })
  it('passes with all valid', () => {
    expect(validateRegisterForm({
      username: 'alice',
      email: 'a@b.com',
      password: '123456',
      confirmPassword: '123456'
    }).ok).toBe(true)
  })
})
