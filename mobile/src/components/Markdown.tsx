import { Fragment } from 'react'
import { Text, View } from 'react-native'
import { useColors } from '../lib/theme'

const INLINE = /(\*\*[^*]+\*\*|`[^`]+`|\[(?:\d{1,2}:)?\d{1,2}:\d{2}\](?!\())/g

function toSeconds(stamp: string) {
  return stamp.split(':').reduce((a, p) => a * 60 + Number(p), 0)
}

/**
 * Small native markdown renderer for model output: headings, bullet/numbered lists, code blocks,
 * bold, inline code and tappable [mm:ss] timestamps. Text only, so nothing executable is rendered.
 */
export function Markdown({ children, onTimestamp }: { children: string | null | undefined; onTimestamp?: (s: number) => void }) {
  const c = useColors()
  if (!children) return null
  const lines = children.replace(/\r/g, '').split('\n')
  const blocks: React.ReactNode[] = []
  let code: string[] | null = null

  const inline = (text: string, key: string) =>
    text.split(INLINE).map((part, i) => {
      if (!part) return null
      if (part.startsWith('**')) return <Text key={`${key}-${i}`} style={{ fontWeight: '700' }}>{part.slice(2, -2)}</Text>
      if (part.startsWith('`')) return <Text key={`${key}-${i}`} style={{ fontFamily: 'monospace', backgroundColor: c.brandSoft }}>{part.slice(1, -1)}</Text>
      if (/^\[[\d:]+\]$/.test(part) && onTimestamp) {
        const stamp = part.slice(1, -1)
        return (
          <Text key={`${key}-${i}`} onPress={() => onTimestamp(toSeconds(stamp))} style={{ color: c.brandText, fontWeight: '700' }} accessibilityRole="button">
            ▶ {stamp}
          </Text>
        )
      }
      return <Fragment key={`${key}-${i}`}>{part}</Fragment>
    })

  lines.forEach((raw, idx) => {
    const line = raw.trimEnd()
    if (line.trim().startsWith('```')) {
      if (code) {
        blocks.push(
          <View key={idx} style={{ backgroundColor: '#0F172A', borderRadius: 12, padding: 12, marginVertical: 6 }}>
            <Text style={{ color: '#E2E8F0', fontFamily: 'monospace', fontSize: 13 }}>{code.join('\n')}</Text>
          </View>,
        )
        code = null
      } else code = []
      return
    }
    if (code) return void code.push(raw)
    const t = line.trim()
    if (!t) return
    const heading = /^(#{1,4})\s+(.*)$/.exec(t)
    if (heading) {
      const size = [22, 19, 17, 16][heading[1].length - 1]
      blocks.push(
        <Text key={idx} style={{ color: c.text, fontSize: size, fontWeight: '800', marginTop: 12, marginBottom: 4 }}>
          {inline(heading[2], String(idx))}
        </Text>,
      )
      return
    }
    const bullet = /^([-*+]|\d+[.)])\s+(?:\[[ xX]\]\s+)?(.*)$/.exec(t)
    if (bullet) {
      const marker = /\d/.test(bullet[1]) ? bullet[1] : '•'
      blocks.push(
        <View key={idx} style={{ flexDirection: 'row', gap: 8, marginVertical: 2, paddingLeft: raw.startsWith('  ') ? 16 : 0 }}>
          <Text style={{ color: c.brand, fontSize: 15, lineHeight: 22 }}>{marker}</Text>
          <Text style={{ color: c.text, fontSize: 15, lineHeight: 22, flex: 1 }}>{inline(bullet[2], String(idx))}</Text>
        </View>,
      )
      return
    }
    blocks.push(
      <Text key={idx} style={{ color: c.text, fontSize: 15, lineHeight: 23, marginVertical: 4 }}>
        {inline(t.replace(/^>\s?/, ''), String(idx))}
      </Text>,
    )
  })
  if (code) blocks.push(<Text key="code-tail" style={{ fontFamily: 'monospace', color: c.text }}>{(code as string[]).join('\n')}</Text>)
  return <View>{blocks}</View>
}
