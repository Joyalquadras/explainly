import { useState } from 'react'
import { Modal, Pressable, ScrollView, Text, View, useWindowDimensions } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { SvgXml } from 'react-native-svg'
import { Button } from '../../components/ui'
import { useColors } from '../../lib/theme'
import type { Illustration } from '../../lib/types'

function viewBox(svg: string): [number, number] {
  const m = /viewBox="0 0 ([\d.]+) ([\d.]+)"/.exec(svg)
  return m ? [Number(m[1]), Number(m[2])] : [600, 400]
}

export function Diagram({ illustration }: { illustration: Illustration }) {
  const c = useColors()
  const { width: screen } = useWindowDimensions()
  const [open, setOpen] = useState(false)
  if (!illustration.svg) return null
  const [w, h] = viewBox(illustration.svg)
  const displayW = Math.min(screen - 64, w)
  return (
    <View style={{ borderWidth: 1, borderColor: c.border, borderRadius: 16, overflow: 'hidden' }}>
      <View style={{ padding: 12, gap: 2 }}>
        <Text style={{ color: c.brandText, fontSize: 11, fontWeight: '800', textTransform: 'uppercase' }}>{illustration.type}</Text>
        <Text style={{ color: c.text, fontWeight: '700' }}>{illustration.title}</Text>
      </View>
      <Pressable onPress={() => setOpen(true)} accessibilityRole="imagebutton" accessibilityLabel={`${illustration.title}. Tap to enlarge.`} style={{ backgroundColor: '#fff', alignItems: 'center', padding: 8 }}>
        <SvgXml xml={illustration.svg} width={displayW} height={(displayW * h) / w} />
      </Pressable>
      <View style={{ padding: 12, gap: 4 }}>
        {!!illustration.explanation && <Text style={{ color: c.text }}>{illustration.explanation}</Text>}
        {!!illustration.example && <Text style={{ color: c.muted }}>Example: {illustration.example}</Text>}
      </View>
      <Modal visible={open} animationType="slide" onRequestClose={() => setOpen(false)}>
        <SafeAreaView style={{ flex: 1, backgroundColor: '#fff' }}>
          <ScrollView horizontal contentContainerStyle={{ padding: 12 }}>
            <ScrollView>
              <SvgXml xml={illustration.svg} width={Math.max(w, screen)} height={(Math.max(w, screen) * h) / w} />
            </ScrollView>
          </ScrollView>
          <Button title="Close" variant="secondary" onPress={() => setOpen(false)} style={{ margin: 16 }} />
        </SafeAreaView>
      </Modal>
    </View>
  )
}
