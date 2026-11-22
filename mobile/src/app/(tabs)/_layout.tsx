import Ionicons from '@expo/vector-icons/Ionicons'
import { Tabs } from 'expo-router'
import type { ColorValue } from 'react-native'
import { useColors } from '../../lib/theme'

export default function TabsLayout() {
  const c = useColors()
  const icon = (name: React.ComponentProps<typeof Ionicons>['name']) =>
    function TabIcon({ color, size }: { color: ColorValue; size: number }) {
      return <Ionicons name={name} color={color as string} size={size} />
    }
  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: c.brand,
        tabBarInactiveTintColor: c.muted,
        tabBarStyle: { backgroundColor: c.card, borderTopColor: c.border },
        headerStyle: { backgroundColor: c.card },
        headerTintColor: c.text,
        headerTitleStyle: { fontWeight: '800' },
        sceneStyle: { backgroundColor: c.bg },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Explainly', tabBarLabel: 'Home', tabBarIcon: icon('home') }} />
      <Tabs.Screen name="library" options={{ title: 'Library', tabBarIcon: icon('library') }} />
      <Tabs.Screen name="bookmarks" options={{ title: 'Bookmarks', tabBarIcon: icon('bookmark') }} />
      <Tabs.Screen name="account" options={{ title: 'Account', tabBarIcon: icon('person-circle') }} />
    </Tabs>
  )
}
