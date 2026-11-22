import Ionicons from '@expo/vector-icons/Ionicons'
import type { ComponentProps, ReactNode } from 'react'
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View, type TextInputProps, type ViewProps, type ViewStyle } from 'react-native'
import { clock } from '../lib/format'
import { useColors } from '../lib/theme'

export type IconName = ComponentProps<typeof Ionicons>['name']

export function Button({
  title,
  onPress,
  variant = 'primary',
  icon,
  loading,
  disabled,
  style,
}: {
  title: string
  onPress: () => void
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  icon?: IconName
  loading?: boolean
  disabled?: boolean
  style?: ViewStyle
}) {
  const c = useColors()
  const bg = variant === 'primary' ? c.brand : variant === 'danger' ? c.danger : variant === 'secondary' ? c.card : 'transparent'
  const fg = variant === 'primary' || variant === 'danger' ? '#FFFFFF' : variant === 'ghost' ? c.brandText : c.text
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: disabled || loading }}
      onPress={onPress}
      disabled={disabled || loading}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, borderColor: variant === 'secondary' ? c.border : 'transparent', opacity: disabled ? 0.5 : pressed ? 0.85 : 1 },
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={fg} size="small" /> : icon ? <Ionicons name={icon} size={18} color={fg} /> : null}
      <Text style={[styles.buttonText, { color: fg }]}>{title}</Text>
    </Pressable>
  )
}

export function Card({ children, style, ...rest }: { children: ReactNode; style?: ViewStyle } & Omit<ViewProps, 'style'>) {
  const c = useColors()
  return (
    <View {...rest} style={[styles.card, { backgroundColor: c.card, borderColor: c.border }, style]}>
      {children}
    </View>
  )
}

export function Chip({ label, active, onPress, icon }: { label: string; active?: boolean; onPress?: () => void; icon?: IconName }) {
  const c = useColors()
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? 'button' : 'text'}
      accessibilityState={{ selected: active }}
      style={[styles.chip, { backgroundColor: active ? c.brand : c.brandSoft }]}
    >
      {icon && <Ionicons name={icon} size={14} color={active ? '#fff' : c.brandText} />}
      <Text style={{ color: active ? '#fff' : c.brandText, fontSize: 13, fontWeight: '600' }}>{label}</Text>
    </Pressable>
  )
}

export function Input(props: TextInputProps) {
  const c = useColors()
  return (
    <TextInput
      placeholderTextColor={c.muted}
      {...props}
      style={[styles.input, { backgroundColor: c.input, borderColor: c.border, color: c.text }, props.style]}
    />
  )
}

export function H1({ children }: { children: ReactNode }) {
  return <Text style={[styles.h1, { color: useColors().text }]}>{children}</Text>
}

export function Body({ children, muted, style }: { children: ReactNode; muted?: boolean; style?: object }) {
  const c = useColors()
  return <Text style={[{ color: muted ? c.muted : c.text, fontSize: 15, lineHeight: 22 }, style]}>{children}</Text>
}

export function Timestamp({ seconds, onSeek }: { seconds: number | null | undefined; onSeek: (s: number) => void }) {
  const c = useColors()
  if (seconds == null) return null
  return (
    <Pressable
      onPress={() => onSeek(seconds)}
      accessibilityRole="button"
      accessibilityLabel={`Play from ${clock(seconds)}`}
      hitSlop={8}
      style={[styles.stamp, { backgroundColor: c.brandSoft }]}
    >
      <Ionicons name="play" size={11} color={c.brandText} />
      <Text style={{ color: c.brandText, fontWeight: '700', fontSize: 12, fontVariant: ['tabular-nums'] }}>{clock(seconds)}</Text>
    </Pressable>
  )
}

export function Loading({ label }: { label?: string }) {
  const c = useColors()
  return (
    <View style={styles.center}>
      <ActivityIndicator color={c.brand} />
      {label && <Text style={{ color: c.muted, marginTop: 8 }}>{label}</Text>}
    </View>
  )
}

export function ErrorView({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const c = useColors()
  return (
    <View style={[styles.notice, { backgroundColor: c.dangerSoft }]} accessibilityRole="alert">
      <Ionicons name="alert-circle" size={22} color={c.danger} />
      <Text style={{ color: c.text, textAlign: 'center' }}>{message}</Text>
      {onRetry && <Button title="Try again" variant="secondary" onPress={onRetry} />}
    </View>
  )
}

export function EmptyView({ icon, title, text, children }: { icon: IconName; title: string; text?: string; children?: ReactNode }) {
  const c = useColors()
  return (
    <View style={[styles.empty, { borderColor: c.border }]}>
      <View style={{ backgroundColor: c.brandSoft, padding: 12, borderRadius: 16 }}>
        <Ionicons name={icon} size={24} color={c.brandText} />
      </View>
      <Text style={{ color: c.text, fontWeight: '700', fontSize: 16 }}>{title}</Text>
      {text && <Text style={{ color: c.muted, textAlign: 'center' }}>{text}</Text>}
      {children}
    </View>
  )
}

const styles = StyleSheet.create({
  button: { minHeight: 46, paddingHorizontal: 16, borderRadius: 14, borderWidth: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  buttonText: { fontSize: 15, fontWeight: '700' },
  card: { borderRadius: 18, borderWidth: 1, padding: 16 },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 999 },
  input: { minHeight: 48, borderWidth: 1, borderRadius: 14, paddingHorizontal: 14, fontSize: 16 },
  h1: { fontSize: 26, fontWeight: '800', letterSpacing: -0.5 },
  stamp: { flexDirection: 'row', alignItems: 'center', gap: 3, paddingHorizontal: 7, paddingVertical: 3, borderRadius: 7, alignSelf: 'flex-start' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  notice: { borderRadius: 16, padding: 16, alignItems: 'center', gap: 10 },
  empty: { borderWidth: 1, borderStyle: 'dashed', borderRadius: 18, padding: 24, alignItems: 'center', gap: 10 },
})
