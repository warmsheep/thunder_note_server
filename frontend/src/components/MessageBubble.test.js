import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'

function mountBubble(message) {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const app = createApp({
    setup() {
      return () => h(MessageBubble, { message, mine: true })
    }
  })
  app.mount(root)
  return {
    root,
    unmount: () => {
      app.unmount()
      root.remove()
    }
  }
}

describe('MessageBubble', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders TEXT mediaType messages as plain text instead of media attachment', async () => {
    const ctx = mountBubble({
      id: 1,
      content: 'Android text message',
      mediaType: 'TEXT',
      createdAt: '2026-05-19T10:00:00'
    })
    await nextTick()

    expect(ctx.root.textContent).toContain('Android text message')
    expect(ctx.root.textContent).not.toContain('附件')
    ctx.unmount()
  })
})
