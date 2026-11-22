import { Menu, MenuButton, MenuItem, MenuItems } from '@headlessui/react'
import clsx from 'clsx'
import { Bookmark, Home, Library, LogOut, Monitor, Moon, Settings, Sun, UserRound } from 'lucide-react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useTheme, type ThemePreference } from '../lib/theme'
import { Logo } from './Logo'

const NAV = [
  { to: '/', label: 'Home', icon: Home, end: true },
  { to: '/library', label: 'Library', icon: Library },
  { to: '/bookmarks', label: 'Bookmarks', icon: Bookmark },
]

function ThemeMenu() {
  const { preference, resolved, setPreference } = useTheme()
  const options: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
    { value: 'light', label: 'Light', icon: Sun },
    { value: 'dark', label: 'Dark', icon: Moon },
    { value: 'system', label: 'System', icon: Monitor },
  ]
  return (
    <Menu as="div" className="relative">
      <MenuButton className="btn-ghost px-2.5" aria-label="Change theme">
        {resolved === 'dark' ? <Moon className="size-5" /> : <Sun className="size-5" />}
      </MenuButton>
      <MenuItems anchor="bottom end" className="z-40 mt-2 w-40 rounded-xl border border-slate-200 bg-white p-1 shadow-lg focus:outline-none dark:border-slate-700 dark:bg-slate-900">
        {options.map((o) => (
          <MenuItem key={o.value}>
            <button
              onClick={() => setPreference(o.value)}
              className={clsx(
                'flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm data-focus:bg-slate-100 dark:data-focus:bg-slate-800',
                preference === o.value && 'font-semibold text-brand-600 dark:text-brand-300',
              )}
            >
              <o.icon className="size-4" /> {o.label}
            </button>
          </MenuItem>
        ))}
      </MenuItems>
    </Menu>
  )
}

function UserMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  if (!user) {
    return (
      <Link to="/signin" className="btn-primary px-3.5 py-2">
        Sign in
      </Link>
    )
  }
  return (
    <Menu as="div" className="relative">
      <MenuButton className="flex items-center gap-2 rounded-full p-0.5 hover:ring-2 hover:ring-brand-200 dark:hover:ring-brand-800" aria-label="Account menu">
        {user.avatarUrl ? (
          <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" className="size-8 rounded-full" />
        ) : (
          <span className="flex size-8 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700 dark:bg-brand-900/50 dark:text-brand-200">
            {user.displayName.slice(0, 1).toUpperCase()}
          </span>
        )}
      </MenuButton>
      <MenuItems anchor="bottom end" className="z-40 mt-2 w-56 rounded-xl border border-slate-200 bg-white p-1 shadow-lg focus:outline-none dark:border-slate-700 dark:bg-slate-900">
        <div className="px-3 py-2">
          <p className="truncate text-sm font-semibold">{user.displayName}</p>
          <p className="truncate text-xs text-slate-500">{user.role === 'GUEST' ? 'Guest session' : user.email}</p>
        </div>
        <MenuItem>
          <Link to="/settings" className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm data-focus:bg-slate-100 dark:data-focus:bg-slate-800">
            <Settings className="size-4" /> Settings
          </Link>
        </MenuItem>
        <MenuItem>
          <button
            onClick={async () => {
              await logout()
              navigate('/')
            }}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-rose-600 data-focus:bg-rose-50 dark:data-focus:bg-rose-950/40"
          >
            <LogOut className="size-4" /> Sign out
          </button>
        </MenuItem>
      </MenuItems>
    </Menu>
  )
}

export function Layout() {
  const { user } = useAuth()
  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-white focus:px-3 focus:py-2">
        Skip to content
      </a>
      <header className="sticky top-0 z-30 border-b border-slate-200/70 bg-white/80 backdrop-blur-lg dark:border-slate-800/70 dark:bg-slate-950/80">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2.5 font-bold tracking-tight">
            <Logo size={30} />
            <span className="text-lg">Explainly</span>
          </Link>
          <nav className="ml-6 hidden items-center gap-1 md:flex" aria-label="Main">
            {NAV.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                end={n.end}
                className={({ isActive }) =>
                  clsx('rounded-lg px-3 py-2 text-sm font-medium transition', isActive ? 'bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-white' : 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white')
                }
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            {user?.role === 'GUEST' && (
              <Link to="/signin?mode=register" className="hidden text-sm font-medium text-brand-600 hover:underline sm:block dark:text-brand-300">
                Save your progress
              </Link>
            )}
            <ThemeMenu />
            <UserMenu />
          </div>
        </div>
      </header>
      <main id="main" className="flex-1 pb-24 md:pb-10">
        <Outlet />
      </main>
      <nav
        className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-4 border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden dark:border-slate-800 dark:bg-slate-950/95"
        aria-label="Mobile"
      >
        {[...NAV, { to: user ? '/settings' : '/signin', label: user ? 'Account' : 'Sign in', icon: UserRound, end: false }].map((n) => (
          <NavLink
            key={n.label}
            to={n.to}
            end={n.end}
            className={({ isActive }) => clsx('flex flex-col items-center gap-0.5 py-2.5 text-[11px] font-medium', isActive ? 'text-brand-600 dark:text-brand-300' : 'text-slate-500')}
          >
            <n.icon className="size-5" />
            {n.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
