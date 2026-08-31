// @vitest-environment happy-dom
import { createApp } from 'vue'
import { createI18n } from 'vue-i18n'
import { afterEach, describe, expect, it } from 'vitest'
import ChatInput from '../ChatInput.vue'

const apps: Array<ReturnType<typeof createApp>> = []

const messages = {
  chat: {
    messagePlaceholder: 'Type a message',
    promptOptimize: 'Optimize prompt',
    promptOptimizeFailed: 'Prompt optimization failed, please retry',
    thinkingOn: 'Deep thinking enabled',
    thinkingOff: 'Click to enable deep thinking',
    thinkingUnsupported: 'Current model does not support deep thinking',
  },
}

function mountChatInput(props: Record<string, unknown>, onOptimize?: () => void) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const app = createApp(ChatInput, {
    placeholder: messages.chat.messagePlaceholder,
    ...props,
    onOptimize: () => onOptimize?.(),
  })
  app.use(createI18n({ legacy: false, locale: 'en', messages: { en: messages } }))
  app.mount(host)
  apps.push(app)
  return { host }
}

afterEach(() => {
  apps.splice(0).forEach(app => app.unmount())
  document.body.innerHTML = ''
})

describe('ChatInput prompt optimize button', () => {
  it('emits optimize when clicked with non-empty draft', () => {
    let emitted = 0
    const { host } = mountChatInput({ modelValue: '帮我写周报' }, () => {
      emitted++
    })

    const btn = host.querySelector<HTMLButtonElement>('.optimize-btn')
    expect(btn).not.toBeNull()
    expect(btn?.disabled).toBe(false)
    expect(btn?.title).toBe('Optimize prompt')

    btn?.click()
    expect(emitted).toBe(1)
  })

  it('disables the button when the draft is empty or whitespace', () => {
    for (const value of ['', '   ']) {
      const { host } = mountChatInput({ modelValue: value })
      const btn = host.querySelector<HTMLButtonElement>('.optimize-btn')
      expect(btn?.disabled).toBe(true)
    }
  })

  it('disables the button while the agent is running or the input is disabled', () => {
    const running = mountChatInput({ modelValue: 'draft', loading: true })
    expect(running.host.querySelector<HTMLButtonElement>('.optimize-btn')?.disabled).toBe(true)

    const disabled = mountChatInput({ modelValue: 'draft', disabled: true })
    expect(disabled.host.querySelector<HTMLButtonElement>('.optimize-btn')?.disabled).toBe(true)
  })

  it('disables the button and shows a spinner while optimizing', () => {
    const { host } = mountChatInput({ modelValue: 'draft', optimizing: true })
    const btn = host.querySelector<HTMLButtonElement>('.optimize-btn')
    expect(btn?.disabled).toBe(true)
    // Loading spinner icon rendered instead of the pen icon.
    expect(btn?.querySelector('.stop-spinner')).not.toBeNull()
  })

  it('does not emit optimize while optimizing', () => {
    let emitted = 0
    const { host } = mountChatInput({ modelValue: 'draft', optimizing: true }, () => {
      emitted++
    })
    // happy-dom honors the disabled attribute: clicks on disabled buttons do
    // not fire listeners. Guard assertion documents the contract anyway.
    const btn = host.querySelector<HTMLButtonElement>('.optimize-btn')
    btn?.click()
    expect(emitted).toBe(0)
  })
})
