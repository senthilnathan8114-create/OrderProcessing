import React from 'react'
import clsx from 'clsx'

const STATUS_CONFIG = {
  PENDING:     { label: 'Pending',     color: 'bg-gray-800 text-gray-300 border-gray-600' },
  PROCESSING:  { label: 'Processing',  color: 'bg-blue-900/50 text-blue-300 border-blue-700' },
  COMPLETED:   { label: 'Completed',   color: 'bg-green-900/50 text-green-300 border-green-700' },
  OUT_OF_STOCK:{ label: 'Out of Stock',color: 'bg-red-900/50 text-red-300 border-red-700' },
  RETRYING:    { label: 'Retrying',    color: 'bg-yellow-900/50 text-yellow-300 border-yellow-700' },
  DEAD_LETTER: { label: 'Dead Letter', color: 'bg-purple-900/50 text-purple-300 border-purple-700' },
}

export function OrderStatusBadge({ status }) {
  const config = STATUS_CONFIG[status] || { label: status, color: 'bg-gray-800 text-gray-300 border-gray-600' }

  return (
    <span className={clsx(
      'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border',
      config.color
    )}>
      {config.label}
    </span>
  )
}
