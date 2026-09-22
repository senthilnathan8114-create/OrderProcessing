import React from 'react'
import clsx from 'clsx'

function StockBar({ quantity, max = 20 }) {
  const pct = Math.min((quantity / max) * 100, 100)
  const color = quantity === 0 ? 'bg-ops-danger' : quantity <= 3 ? 'bg-ops-warning' : 'bg-ops-success'

  return (
    <div className="flex items-center gap-3">
      <div className="flex-1 bg-gray-800 rounded-full h-1.5">
        <div
          className={clsx('h-1.5 rounded-full transition-all duration-500', color)}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className={clsx(
        'text-sm font-mono font-bold w-8 text-right',
        quantity === 0 ? 'text-ops-danger' : quantity <= 3 ? 'text-ops-warning' : 'text-ops-success'
      )}>
        {quantity}
      </span>
    </div>
  )
}

export function InventoryTable({ products }) {
  return (
    <div className="bg-ops-card border border-ops-border rounded-xl overflow-hidden">
      <div className="px-5 py-4 border-b border-ops-border">
        <h3 className="text-sm font-semibold text-gray-200 uppercase tracking-wider">Live Inventory</h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ops-border">
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider">Product</th>
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider">SKU</th>
              <th className="text-left px-5 py-3 text-xs text-gray-400 uppercase tracking-wider w-48">Stock</th>
            </tr>
          </thead>
          <tbody>
            {products.length === 0 ? (
              <tr>
                <td colSpan={3} className="text-center py-8 text-gray-500">Loading inventory...</td>
              </tr>
            ) : (
              products.map(p => (
                <tr key={p.id} className="border-b border-ops-border/50 hover:bg-white/5 transition-colors">
                  <td className="px-5 py-3 text-gray-200 font-medium">{p.name}</td>
                  <td className="px-5 py-3 font-mono text-xs text-gray-400">{p.sku}</td>
                  <td className="px-5 py-3 w-48"><StockBar quantity={p.quantity} max={20} /></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
