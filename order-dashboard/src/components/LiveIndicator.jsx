import React from 'react'
import clsx from 'clsx'

export function LiveIndicator({ connected }) {
  return (
    <div className="flex items-center gap-2">
      <div className={clsx(
        'w-2.5 h-2.5 rounded-full',
        connected
          ? 'bg-ops-success pulse-dot'
          : 'bg-ops-danger'
      )} />
      <span className={clsx(
        'text-xs font-medium uppercase tracking-wider',
        connected ? 'text-ops-success' : 'text-ops-danger'
      )}>
        {connected ? 'LIVE' : 'DISCONNECTED'}
      </span>
    </div>
  )
}
