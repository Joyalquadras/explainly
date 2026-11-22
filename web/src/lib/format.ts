export function clock(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0 || Number.isNaN(seconds)) return ''
  const s = Math.floor(seconds)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}` : `${m}:${String(sec).padStart(2, '0')}`
}

export function duration(seconds: number | null | undefined): string {
  if (!seconds) return ''
  const h = Math.floor(seconds / 3600)
  const m = Math.round((seconds % 3600) / 60)
  return h > 0 ? `${h} h ${m} min` : `${Math.max(1, m)} min`
}

export function relativeTime(iso: string): string {
  const diff = (Date.now() - new Date(iso).getTime()) / 1000
  if (diff < 60) return 'just now'
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`
  if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`
  if (diff < 604800) return `${Math.floor(diff / 86400)} d ago`
  return new Date(iso).toLocaleDateString()
}

export function svgDataUri(svg: string): string {
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

const YT_ID = /^[A-Za-z0-9_-]{11}$/
const HOSTS = ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com', 'youtu.be', 'www.youtube-nocookie.com', 'youtube-nocookie.com']

/** Client-side pre-check for instant feedback. The server performs the authoritative validation. */
export function looksLikeYouTubeUrl(input: string): boolean {
  const value = input.trim()
  if (YT_ID.test(value)) return true
  try {
    const url = new URL(/^https?:\/\//i.test(value) ? value : `https://${value}`)
    return HOSTS.includes(url.hostname.toLowerCase())
  } catch {
    return false
  }
}
