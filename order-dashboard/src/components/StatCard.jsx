import React from 'react'
import clsx from 'clsx'

export function StatCard({ title, value, subtitle, color = 'blue', icon }) {
  const colorMap = {
    blue: 'text-ops-accent border-ops-accent/30 bg-ops-accent/5',
    green: 'text-ops-success border-ops-success/30 bg-ops-success/5',
    red: 'text-ops-danger border-ops-danger/30 bg-ops-danger/5',
    yellow: 'text-ops-warning border-ops-warning/30 bg-ops-warning/5',
    purple: 'text-ops-purple border-ops-purple/30 bg-ops-purple/5',
  }

  return (
    <div className={clsx(
      'rounded-xl border p-5 transition-all duration-200 hover:scale-[1.02]',
      'bg-ops-card',
      colorMap[color]
    )}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs text-gray-400 uppercase tracking-wider mb-1">{title}</p>
          <p className={clsx('text-3xl font-bold', colorMap[color].split(' ')[0])}>{value}</p>
          {subtitle && <p className="text-xs text-gray-500 mt-1">{subtitle}</p>}
        </div>
        {icon && (
          <div className={clsx('text-2xl opacity-60', colorMap[color].split(' ')[0])}>
            {icon}
          </div>
        )}
      </div>
    </div>
  )
}
