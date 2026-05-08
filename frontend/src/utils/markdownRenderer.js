import { marked } from 'marked'
import DOMPurify from 'dompurify'

// D1-W17-04 Markdown 渲染器
// 设计原则：
// - 安全优先：所有渲染结果必须经过 DOMPurify，禁止内联事件、禁止 javascript:
// - 兼容降级：解析失败回退到纯文本（含 HTML 转义）
// - 链接安全：a 标签强制 target=_blank + rel=noopener noreferrer
// - 不渲染原始 HTML：marked 默认不解析 inline HTML（marked 5+ 已删除 sanitize 选项）
//
// 仅用于消息文本气泡（W6 文本类消息），不用于：
// - 用户名、昵称、闪记标题等结构化字段（保持纯文本即可，避免 XSS 面）
// - 卡片摘要（CardPayload.summary，由后端构造，应作纯文本对待）

// 配置 marked
marked.setOptions({
  gfm: true,        // GitHub Flavored Markdown
  breaks: true,     // 单换行也作为 <br>
  pedantic: false
})

// DOMPurify 钩子：让 a 标签默认带 target=_blank，避免外链导航污染当前 SPA
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node && node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

const ALLOWED_TAGS = [
  'a', 'b', 'strong', 'i', 'em', 'u', 's', 'del', 'ins',
  'p', 'br', 'hr',
  'ul', 'ol', 'li',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'blockquote',
  'code', 'pre',
  'span'
]

const ALLOWED_ATTR = ['href', 'title', 'target', 'rel', 'class']

export function renderMarkdown(text) {
  if (text == null) return ''
  const source = String(text)
  if (!source.trim()) return ''
  let raw
  try {
    raw = marked.parse(source)
  } catch (_e) {
    // 解析失败：转义后返回，保证不会注入 HTML
    return escapeHtml(source)
  }
  return DOMPurify.sanitize(raw, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'button'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur', 'style'],
    ALLOW_DATA_ATTR: false
  })
}

export function escapeHtml(text) {
  if (text == null) return ''
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
