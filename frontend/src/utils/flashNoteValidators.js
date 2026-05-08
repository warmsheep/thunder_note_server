// D1-W5 闪记表单校验，约束与 FlashNoteCreateRequest / FlashNoteUpdateRequest 对齐。

const TITLE_MAX_LENGTH = 500
const ICON_MAX_LENGTH = 64

export function validateTitle(value, { required = true } = {}) {
  const v = (value || '').trim()
  if (!v) {
    if (required) {
      return { ok: false, message: '请输入闪记名称' }
    }
    return { ok: true }
  }
  if (v.length > TITLE_MAX_LENGTH) {
    return { ok: false, message: `名称不能超过 ${TITLE_MAX_LENGTH} 个字符` }
  }
  return { ok: true }
}

export function validateIcon(value) {
  const v = value || ''
  if (v.length > ICON_MAX_LENGTH) {
    return { ok: false, message: '图标过长' }
  }
  return { ok: true }
}

export function validateCreateForm(form) {
  const t = validateTitle(form && form.title, { required: true })
  if (!t.ok) return t
  const i = validateIcon(form && form.icon)
  if (!i.ok) return i
  return { ok: true }
}

export function validateUpdateForm(form) {
  // 更新时所有字段可选，但若 title 提供则不能为空白
  const value = form && form.title
  if (value !== undefined && value !== null) {
    const trimmed = String(value).trim()
    if (!trimmed) {
      return { ok: false, message: '名称不能为空' }
    }
    if (trimmed.length > TITLE_MAX_LENGTH) {
      return { ok: false, message: `名称不能超过 ${TITLE_MAX_LENGTH} 个字符` }
    }
  }
  const i = validateIcon(form && form.icon)
  if (!i.ok) return i
  return { ok: true }
}
