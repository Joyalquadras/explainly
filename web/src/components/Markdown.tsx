import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import clsx from 'clsx'

const STAMP = /\[((?:\d{1,2}:)?\d{1,2}:\d{2})\](?!\()/g

function toSeconds(stamp: string) {
  return stamp.split(':').reduce((acc, part) => acc * 60 + Number(part), 0)
}

/** Turns "[1:23]" citations into links handled by {@link Markdown}'s onTimestamp callback. */
export function linkTimestamps(text: string) {
  return text.replace(STAMP, (_, stamp: string) => `[${stamp}](#t=${toSeconds(stamp)})`)
}

/** Renders model-written markdown safely (react-markdown never renders raw HTML). */
export function Markdown({
  children,
  className,
  onTimestamp,
}: {
  children: string | null | undefined
  className?: string
  onTimestamp?: (seconds: number) => void
}) {
  if (!children) return null
  return (
    <div className={clsx('prose-explainly', className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ href, children: c }) => {
            if (href?.startsWith('#t=') && onTimestamp) {
              return (
                <button
                  type="button"
                  onClick={() => onTimestamp(Number(href.slice(3)))}
                  className="mx-0.5 inline-flex items-center rounded-md bg-brand-50 px-1.5 py-0.5 align-baseline font-mono text-[0.8em] font-semibold text-brand-700 no-underline hover:bg-brand-100 dark:bg-brand-900/40 dark:text-brand-200"
                  aria-label={`Play video from ${String(c)}`}
                >
                  ▶ {c}
                </button>
              )
            }
            return (
              <a href={href} target="_blank" rel="noopener noreferrer nofollow">
                {c}
              </a>
            )
          },
        }}
      >
        {onTimestamp ? linkTimestamps(children) : children}
      </ReactMarkdown>
    </div>
  )
}
