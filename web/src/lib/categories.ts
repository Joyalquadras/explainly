import { Atom, BarChart3, Brain, Briefcase, Calculator, Cloud, Code2, Dumbbell, Globe2, Layers, Palette, Shield, Sparkles, Sprout, type LucideIcon } from 'lucide-react'

export const CATEGORIES: { name: string; icon: LucideIcon; color: string }[] = [
  { name: 'Programming', icon: Code2, color: 'text-indigo-600 bg-indigo-50 dark:bg-indigo-950/40 dark:text-indigo-300' },
  { name: 'Software Engineering', icon: Layers, color: 'text-sky-600 bg-sky-50 dark:bg-sky-950/40 dark:text-sky-300' },
  { name: 'AI & Machine Learning', icon: Brain, color: 'text-violet-600 bg-violet-50 dark:bg-violet-950/40 dark:text-violet-300' },
  { name: 'Data Science', icon: BarChart3, color: 'text-cyan-600 bg-cyan-50 dark:bg-cyan-950/40 dark:text-cyan-300' },
  { name: 'Cloud & DevOps', icon: Cloud, color: 'text-blue-600 bg-blue-50 dark:bg-blue-950/40 dark:text-blue-300' },
  { name: 'Networking & Security', icon: Shield, color: 'text-emerald-600 bg-emerald-50 dark:bg-emerald-950/40 dark:text-emerald-300' },
  { name: 'Mathematics', icon: Calculator, color: 'text-amber-600 bg-amber-50 dark:bg-amber-950/40 dark:text-amber-300' },
  { name: 'Science', icon: Atom, color: 'text-teal-600 bg-teal-50 dark:bg-teal-950/40 dark:text-teal-300' },
  { name: 'Business & Finance', icon: Briefcase, color: 'text-orange-600 bg-orange-50 dark:bg-orange-950/40 dark:text-orange-300' },
  { name: 'Personal Development', icon: Sprout, color: 'text-lime-600 bg-lime-50 dark:bg-lime-950/40 dark:text-lime-300' },
  { name: 'Language & Humanities', icon: Globe2, color: 'text-rose-600 bg-rose-50 dark:bg-rose-950/40 dark:text-rose-300' },
  { name: 'Health & Fitness', icon: Dumbbell, color: 'text-red-600 bg-red-50 dark:bg-red-950/40 dark:text-red-300' },
  { name: 'Arts & Design', icon: Palette, color: 'text-pink-600 bg-pink-50 dark:bg-pink-950/40 dark:text-pink-300' },
  { name: 'Other', icon: Sparkles, color: 'text-slate-600 bg-slate-100 dark:bg-slate-800 dark:text-slate-300' },
]

export function categoryMeta(name: string | null | undefined) {
  return CATEGORIES.find((c) => c.name === name) ?? CATEGORIES[CATEGORIES.length - 1]
}
