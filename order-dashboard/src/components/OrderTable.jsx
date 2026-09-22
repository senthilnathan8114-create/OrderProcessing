import React from 'react'
import { OrderStatusBadge } from './OrderStatusBadge'
import { orderApi } from '../services/api'

function formatTime(isoString) {
  if (!isoString) return '-'
  return new Date(isoString).toLocaleTimeString()
}

export function OrderTable({ orders, onRetry }) {
  const handleRetry = async (orderId) => {
    try {
      await orderApi.retry(orderId)
      if (onRetry) onRetry()
    } catch (e) {
      console.error('Retry failed:', e)
    }
  }

  return (
    <div className="bg-ops-card border border-ops-border rounded-xl overflow-hidden">
      <div className="px-5 py-4 border-b border-ops-border">
        <h3 className="text-sm font-semibold text-gray-200 uppercase tracking-wider">Live Orders</h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ops-border">
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider">Order #</th>
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider">Status</th>
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider">Items</th>
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider">Retries</th>
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider">Time</th>
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody>
            {orders.length === 0 ? (
              <tr>
                <td colSpan={6} className="text-center py-8 text-gray-500">No orders yet. Submit an order to get started.</td>
              </tr>
            ) : (
              orders.slice(0, 50).map(order => (
                <tr key={order.id} className="border-b border-ops-border/50 hover:bg-white/5 transition-colors">
                  <td className="px-5 py-3 font-mono text-xs text-gray-300">{order.orderNumber}</td>
                  <td className="px-5 py-3"><OrderStatusBadge status={order.status} /></td>
                  <td className="px-5 py-3 text-gray-400">
                    {order.items?.map(i => `${i.productName} x${i.quantity}`).join(', ')}
                  </td>
                  <td className="px-5 py-3 text-gray-400">{order.retryCount ?? 0}</td>
                  <td className="px-5 py-3 text-gray-500 text-xs">{formatTime(order.createdAt)}</td>
                  <td className="px-5 py-3">
                    {(order.status === 'OUT_OF_STOCK' || order.status === 'DEAD_LETTER') && (
                      <button
                        onClick={() => handleRetry(order.id)}
                        className="text-xs px-2 py-1 rounded bg-ops-accent/20 text-ops-accent hover:bg-ops-accent/30 border border-ops-accent/30 transition-colors"
                      >
                        Retry
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
