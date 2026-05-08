// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { renderMarkdown, escapeHtml } from './markdownRenderer'

describe('markdownRenderer.renderMarkdown', () => {
  it('renders bold/italic/code', () => {
    const html = renderMarkdown('**bold** and *italic* and `code`')
    expect(html).toContain('<strong>bold</strong>')
    expect(html).toContain('<em>italic</em>')
    expect(html).toContain('<code>code</code>')
  })

  it('renders headings and lists', () => {
    const html = renderMarkdown('# H1\n\n- a\n- b\n')
    expect(html).toContain('<h1>H1</h1>')
    expect(html).toContain('<ul>')
    expect(html).toContain('<li>a</li>')
  })

  it('renders link with safe target=_blank rel=noopener', () => {
    const html = renderMarkdown('[click](https://example.com)')
    expect(html).toContain('href="https://example.com"')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener noreferrer"')
  })

  it('strips javascript: protocol from links', () => {
    const html = renderMarkdown('[xss](javascript:alert(1))')
    expect(html).not.toContain('javascript:')
  })

  it('strips inline event handlers from raw HTML', () => {
    const html = renderMarkdown('<img src=x onerror="alert(1)" />')
    expect(html).not.toContain('onerror')
    expect(html).not.toMatch(/<script/i)
  })

  it('strips script tags', () => {
    const html = renderMarkdown('<script>alert(1)</script>hello')
    expect(html).not.toMatch(/<script/i)
    expect(html).toContain('hello')
  })

  it('strips iframe, object, form etc.', () => {
    const html = renderMarkdown('<iframe src="evil"></iframe><form><input/></form>')
    expect(html).not.toMatch(/<iframe/i)
    expect(html).not.toMatch(/<form/i)
    expect(html).not.toMatch(/<input/i)
  })

  it('strips style attribute (avoid CSS injection)', () => {
    const html = renderMarkdown('<p style="color:red">hi</p>')
    expect(html).not.toContain('style=')
  })

  it('returns empty string for null / empty / whitespace input', () => {
    expect(renderMarkdown(null)).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown('   \n  ')).toBe('')
  })

  it('preserves single newline as <br> (gfm breaks)', () => {
    const html = renderMarkdown('line1\nline2')
    expect(html).toMatch(/<br\s*\/?>/)
  })
})

describe('markdownRenderer.escapeHtml', () => {
  it('escapes special chars', () => {
    expect(escapeHtml('<a "b" \'c\' &d>')).toBe(
      '&lt;a &quot;b&quot; &#39;c&#39; &amp;d&gt;'
    )
  })

  it('returns empty string for null / undefined', () => {
    expect(escapeHtml(null)).toBe('')
    expect(escapeHtml(undefined)).toBe('')
  })
})
