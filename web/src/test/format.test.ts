import { describe, expect, it } from 'vitest'
import { linkTimestamps } from '../components/Markdown'
import { clock, duration, looksLikeYouTubeUrl } from '../lib/format'

describe('format helpers', () => {
  it('formats clock times', () => {
    expect(clock(5)).toBe('0:05')
    expect(clock(65)).toBe('1:05')
    expect(clock(3723)).toBe('1:02:03')
    expect(clock(null)).toBe('')
  })

  it('formats durations', () => {
    expect(duration(30)).toBe('1 min')
    expect(duration(3900)).toBe('1 h 5 min')
  })

  it('pre-validates YouTube links', () => {
    expect(looksLikeYouTubeUrl('https://www.youtube.com/watch?v=dQw4w9WgXcQ')).toBe(true)
    expect(looksLikeYouTubeUrl('youtu.be/dQw4w9WgXcQ')).toBe(true)
    expect(looksLikeYouTubeUrl('dQw4w9WgXcQ')).toBe(true)
    expect(looksLikeYouTubeUrl('https://evil.com/watch?v=dQw4w9WgXcQ')).toBe(false)
    expect(looksLikeYouTubeUrl('not a url')).toBe(false)
  })

  it('turns citations into timestamp links', () => {
    expect(linkTimestamps('See [1:05] and [1:02:03].')).toBe('See [1:05](#t=65) and [1:02:03](#t=3723).')
    expect(linkTimestamps('Already [1:05](https://x)')).toBe('Already [1:05](https://x)')
  })
})
