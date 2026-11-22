import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'

interface YTPlayer {
  seekTo(seconds: number, allowSeekAhead: boolean): void
  playVideo(): void
  getCurrentTime(): number
  destroy(): void
}

declare global {
  interface Window {
    YT?: { Player: new (el: HTMLElement, opts: unknown) => YTPlayer }
    onYouTubeIframeAPIReady?: () => void
  }
}

export interface PlayerHandle {
  seek: (seconds: number) => void
  currentTime: () => number | null
}

let apiPromise: Promise<void> | null = null
function loadApi(): Promise<void> {
  if (window.YT?.Player) return Promise.resolve()
  apiPromise ??= new Promise<void>((resolve) => {
    const previous = window.onYouTubeIframeAPIReady
    window.onYouTubeIframeAPIReady = () => {
      previous?.()
      resolve()
    }
    const script = document.createElement('script')
    script.src = 'https://www.youtube.com/iframe_api'
    script.async = true
    document.head.appendChild(script)
  })
  return apiPromise
}

/** Embedded YouTube player (official IFrame API) that exposes seek() for timestamp navigation. */
export const YouTubePlayer = forwardRef<PlayerHandle, { videoId: string; title: string; start?: number | null }>(
  function YouTubePlayer({ videoId, title, start }, ref) {
    const host = useRef<HTMLDivElement>(null)
    const player = useRef<YTPlayer | null>(null)
    const pendingSeek = useRef<number | null>(null)

    useEffect(() => {
      let cancelled = false
      void loadApi().then(() => {
        if (cancelled || !host.current || !window.YT) return
        const el = document.createElement('div')
        host.current.innerHTML = ''
        host.current.appendChild(el)
        player.current = new window.YT.Player(el, {
          videoId,
          host: 'https://www.youtube-nocookie.com',
          width: '100%',
          height: '100%',
          playerVars: { rel: 0, modestbranding: 1, playsinline: 1, start: start ?? undefined },
          events: {
            onReady: () => {
              if (pendingSeek.current != null) {
                player.current?.seekTo(pendingSeek.current, true)
                pendingSeek.current = null
              }
            },
          },
        })
      })
      return () => {
        cancelled = true
        player.current?.destroy()
        player.current = null
      }
      // start only matters for the initial load
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [videoId])

    useImperativeHandle(ref, () => ({
      seek: (seconds: number) => {
        const p = player.current
        if (p && typeof p.seekTo === 'function') {
          p.seekTo(seconds, true)
          p.playVideo()
        } else {
          pendingSeek.current = seconds
        }
      },
      currentTime: () => {
        const p = player.current
        return p && typeof p.getCurrentTime === 'function' ? p.getCurrentTime() : null
      },
    }))

    return (
      <div className="relative aspect-video w-full overflow-hidden rounded-2xl bg-black shadow-sm">
        <div ref={host} className="absolute inset-0 [&>iframe]:size-full" title={title} />
      </div>
    )
  },
)
