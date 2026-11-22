import { useColorScheme } from 'react-native'

const light = {
  bg: '#F8FAFC',
  card: '#FFFFFF',
  border: '#E2E8F0',
  text: '#0F172A',
  muted: '#64748B',
  brand: '#4F46E5',
  brandSoft: '#EEF2FF',
  brandText: '#4338CA',
  success: '#059669',
  successSoft: '#ECFDF5',
  danger: '#E11D48',
  dangerSoft: '#FFF1F2',
  warnSoft: '#FFFBEB',
  warnText: '#92400E',
  input: '#FFFFFF',
}
const dark: typeof light = {
  bg: '#020617',
  card: '#0F172A',
  border: '#1E293B',
  text: '#F1F5F9',
  muted: '#94A3B8',
  brand: '#6366F1',
  brandSoft: '#1E1B4B',
  brandText: '#C7D2FE',
  success: '#34D399',
  successSoft: '#052E2B',
  danger: '#FB7185',
  dangerSoft: '#3F0D1A',
  warnSoft: '#3A2A06',
  warnText: '#FDE68A',
  input: '#0B1220',
}

export type Colors = typeof light

export function useColors(): Colors {
  return useColorScheme() === 'dark' ? dark : light
}
