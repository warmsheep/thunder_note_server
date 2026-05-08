// D1-W3 表单校验纯函数，约束与后端 DTO（LoginRequest/RegisterRequest）一致。
// 故意不抛异常，只返回 { ok: boolean, message?: string }，便于在 UI 与单测中复用。

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateUsername(value) {
  const v = (value || '').trim()
  if (!v) {
    return { ok: false, message: '请输入用户名' }
  }
  if (v.length < 3 || v.length > 32) {
    return { ok: false, message: '用户名长度需在 3-32 之间' }
  }
  return { ok: true }
}

export function validatePasswordRequired(value) {
  if (!value) {
    return { ok: false, message: '请输入密码' }
  }
  return { ok: true }
}

export function validatePasswordStrength(value) {
  if (!value) {
    return { ok: false, message: '请输入密码' }
  }
  if (value.length < 6 || value.length > 128) {
    return { ok: false, message: '密码长度需在 6-128 之间' }
  }
  return { ok: true }
}

export function validateEmail(value) {
  const v = (value || '').trim()
  if (!v) {
    return { ok: false, message: '请输入邮箱' }
  }
  if (!EMAIL_REGEX.test(v)) {
    return { ok: false, message: '邮箱格式不正确' }
  }
  return { ok: true }
}

export function validateConfirmPassword(password, confirm) {
  if (!confirm) {
    return { ok: false, message: '请再次输入密码' }
  }
  if (password !== confirm) {
    return { ok: false, message: '两次输入的密码不一致' }
  }
  return { ok: true }
}

export function validateLoginForm({ username, password }) {
  const u = validateUsername(username)
  if (!u.ok) return u
  const p = validatePasswordRequired(password)
  if (!p.ok) return p
  return { ok: true }
}

export function validateRegisterForm({ username, email, password, confirmPassword }) {
  const u = validateUsername(username)
  if (!u.ok) return u
  const e = validateEmail(email)
  if (!e.ok) return e
  const p = validatePasswordStrength(password)
  if (!p.ok) return p
  const c = validateConfirmPassword(password, confirmPassword)
  if (!c.ok) return c
  return { ok: true }
}
