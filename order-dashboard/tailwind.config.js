/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'ops-bg': '#0a0e1a',
        'ops-card': '#111827',
        'ops-border': '#1f2937',
        'ops-accent': '#3b82f6',
        'ops-success': '#10b981',
        'ops-danger': '#ef4444',
        'ops-warning': '#f59e0b',
        'ops-purple': '#8b5cf6',
      }
    },
  },
  plugins: [],
}
