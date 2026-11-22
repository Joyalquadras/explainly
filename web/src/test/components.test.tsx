import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Markdown } from '../components/Markdown'
import { Timestamp } from '../components/Timestamp'

describe('Markdown', () => {
  it('renders citations as buttons that seek the video', async () => {
    const onTimestamp = vi.fn()
    render(<Markdown onTimestamp={onTimestamp}>{'Controllers handle requests [0:30].'}</Markdown>)
    await userEvent.click(screen.getByRole('button', { name: /play video from 0:30/i }))
    expect(onTimestamp).toHaveBeenCalledWith(30)
  })

  it('does not render raw HTML from model output', () => {
    const { container } = render(<Markdown>{'<img src=x onerror="alert(1)"> **bold**'}</Markdown>)
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('strong')?.textContent).toBe('bold')
  })
})

describe('Timestamp', () => {
  it('seeks when clicked and hides when absent', async () => {
    const onSeek = vi.fn()
    const { rerender, container } = render(<Timestamp seconds={75} onSeek={onSeek} />)
    await userEvent.click(screen.getByRole('button', { name: 'Play from 1:15' }))
    expect(onSeek).toHaveBeenCalledWith(75)
    rerender(<Timestamp seconds={null} onSeek={onSeek} />)
    expect(container).toBeEmptyDOMElement()
  })
})
