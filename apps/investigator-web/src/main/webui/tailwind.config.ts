import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        'bg-base': '#0a0f1e',
        'bg-surface': '#0f1629',
        'bg-elevated': '#141d35',
        'border-subtle': 'rgba(255,255,255,0.08)',
        'text-primary': '#e2e8f0',
        'text-muted': '#64748b',
        'text-data': '#a0b4d6',
        'accent-blue': '#3b82f6',
        'accent-amber': '#f59e0b',
        'confidence-high': '#10b981',
        'confidence-medium': '#f59e0b',
        'confidence-low': '#f97316',
        'confidence-unverified': '#64748b',
        danger: '#ef4444',
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      backgroundImage: {
        'grid-pattern':
          'linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px)',
      },
      backgroundSize: {
        'grid-size': '40px 40px',
      },
      transitionTimingFunction: {
        'spring': 'cubic-bezier(0.16, 1, 0.3, 1)',
      },
      boxShadow: {
        'glow-blue': '0 0 20px rgba(59,130,246,0.15)',
        'glow-amber': '0 0 20px rgba(245,158,11,0.15)',
      },
      backdropBlur: {
        glass: '12px',
      },
    },
  },
  plugins: [],
} satisfies Config
