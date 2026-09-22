import React, { useState, useEffect, useCallback, useRef } from 'react'
import { StatCard } from '../components/StatCard'
import { OrderTable } from '../components/OrderTable'
import { InventoryTable } from '../components/InventoryTable'
import { OrdersChart } from '../components/OrdersChart'
import { LiveIndicator } from '../components/LiveIndicator'
import { useOrderUpdates } from '../hooks/useOrderUpdates'
import { orderApi, inventoryApi, dashboardApi, devApi } from '../services/api'

const MAX_CHART_POINTS = 20

function timeLabel() {
  const now = new Date()
  return `${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}:${now.getSeconds().toString().padStart(2,'0')}`
}

export default function Dashboard() {
  const [orders, setOrders] = useState([])
  const [products, setProducts] = useState([])
  const [stats, setStats] = useState({
    totalOrders: 0, completedOrders: 0, failedOrders: 0,
    processingOrders: 0, retryingOrders: 0, deadLetterOrders: 0,
    pendingOrders: 0, ordersPerMinute: 0
  })
  const [chartData, setChartData] = useState([])
  const [stressForm, setStressForm] = useState({ productId: 1, concurrentOrders: 50 })
  const [stressResult, setStressResult] = useState(null)
  const [stressLoading, setStressLoading] = useState(false)
  const [createForm, setCreateForm] = useState({ productId: 1, quantity: 1 })
  const statsRef = useRef(stats)
  statsRef.current = stats

  // Load initial data
  const loadAll = useCallback(async () => {
    try {
      const [ordersData, productsData, statsData] = await Promise.all([
        orderApi.getAll(),
        inventoryApi.getAll(),
        dashboardApi.getStats(),
      ])
      setOrders(ordersData.sort((a, b) => b.id - a.id))
      setProducts(productsData)
      setStats(statsData)
    } catch (e) {
      console.error('Failed to load data:', e)
    }
  }, [])

  useEffect(() => {
    loadAll()
  }, [])

  // Update chart data every 5 seconds
  useEffect(() => {
    const interval = setInterval(() => {
      setChartData(prev => {
        const s = statsRef.current
        const newPoint = {
          time: timeLabel(),
          total: s.ordersPerMinute,
          completed: s.completedOrders,
          failed: s.failedOrders,
        }
        const updated = [...prev, newPoint]
        return updated.length > MAX_CHART_POINTS ? updated.slice(-MAX_CHART_POINTS) : updated
      })
    }, 5000)
    return () => clearInterval(interval)
  }, [])

  // WebSocket event handler
  const handleOrderEvent = useCallback((event) => {
    const { eventType, payload } = event

    setOrders(prev => {
      const existing = prev.findIndex(o => o.id === payload?.id)
      if (existing >= 0) {
        const updated = [...prev]
        updated[existing] = payload
        return updated.sort((a, b) => b.id - a.id)
      } else if (eventType === 'ORDER_CREATED') {
        return [payload, ...prev].sort((a, b) => b.id - a.id)
      }
      return prev
    })

    // Update stats on every event
    dashboardApi.getStats().then(setStats).catch(() => {})
  }, [])

  const handleInventoryEvent = useCallback((product) => {
    setProducts(prev => {
      const idx = prev.findIndex(p => p.id === product.id)
      if (idx >= 0) {
        const updated = [...prev]
        updated[idx] = product
        return updated
      }
      return prev
    })
  }, [])

  const { connected } = useOrderUpdates({
    onOrderEvent: handleOrderEvent,
    onInventoryEvent: handleInventoryEvent,
  })

  const handleCreateOrder = async (e) => {
    e.preventDefault()
    try {
      await orderApi.create({
        items: [{ productId: Number(createForm.productId), quantity: Number(createForm.quantity) }]
      })
    } catch (e) {
      console.error('Order creation failed:', e)
    }
  }

  const handleStressTest = async (e) => {
    e.preventDefault()
    setStressLoading(true)
    setStressResult(null)
    try {
      const result = await devApi.stressTest(
        Number(stressForm.productId),
        Number(stressForm.concurrentOrders)
      )
      setStressResult(result)
      await loadAll()
    } catch (e) {
      console.error('Stress test failed:', e)
    } finally {
      setStressLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-ops-bg">
      {/* Header */}
      <header className="border-b border-ops-border bg-ops-card/50 backdrop-blur sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-ops-accent flex items-center justify-center text-sm font-bold">⚡</div>
            <div>
              <h1 className="text-lg font-bold text-white">Order Processing</h1>
              <p className="text-xs text-gray-500">Operations Center</p>
            </div>
          </div>
          <LiveIndicator connected={connected} />
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-6 py-6 space-y-6">

        {/* Stat Cards */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <StatCard title="Total Orders" value={stats.totalOrders} color="blue" icon="📋" />
          <StatCard title="Completed" value={stats.completedOrders} color="green" icon="✅"
            subtitle={`${stats.totalOrders > 0 ? Math.round(stats.completedOrders / stats.totalOrders * 100) : 0}% success rate`} />
          <StatCard title="Failed / OOS" value={stats.failedOrders + stats.deadLetterOrders} color="red" icon="❌" />
          <StatCard title="Processing" value={stats.processingOrders + stats.retryingOrders} color="yellow" icon="⚙️"
            subtitle={`${stats.retryingOrders} retrying`} />
        </div>

        {/* Chart + Controls row */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2">
            <OrdersChart data={chartData} />
          </div>
          <div className="space-y-4">
            {/* Quick Create Order */}
            <div className="bg-ops-card border border-ops-border rounded-xl p-5">
              <h3 className="text-sm font-semibold text-gray-200 uppercase tracking-wider mb-3">Place Order</h3>
              <form onSubmit={handleCreateOrder} className="space-y-3">
                <div>
                  <label className="text-xs text-gray-400 block mb-1">Product ID</label>
                  <select
                    value={createForm.productId}
                    onChange={e => setCreateForm(f => ({ ...f, productId: e.target.value }))}
                    className="w-full bg-ops-bg border border-ops-border rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:border-ops-accent"
                  >
                    {products.map(p => (
                      <option key={p.id} value={p.id}>{p.name} (stock: {p.quantity})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-xs text-gray-400 block mb-1">Quantity</label>
                  <input
                    type="number" min="1" max="100"
                    value={createForm.quantity}
                    onChange={e => setCreateForm(f => ({ ...f, quantity: e.target.value }))}
                    className="w-full bg-ops-bg border border-ops-border rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:border-ops-accent"
                  />
                </div>
                <button type="submit" className="w-full py-2 rounded-lg bg-ops-accent text-white text-sm font-medium hover:bg-ops-accent/80 transition-colors">
                  Submit Order
                </button>
              </form>
            </div>

            {/* Stress Test */}
            <div className="bg-ops-card border border-ops-border rounded-xl p-5">
              <h3 className="text-sm font-semibold text-gray-200 uppercase tracking-wider mb-3">🔥 Stress Test</h3>
              <form onSubmit={handleStressTest} className="space-y-3">
                <div>
                  <label className="text-xs text-gray-400 block mb-1">Product ID</label>
                  <select
                    value={stressForm.productId}
                    onChange={e => setStressForm(f => ({ ...f, productId: e.target.value }))}
                    className="w-full bg-ops-bg border border-ops-border rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:border-ops-accent"
                  >
                    {products.map(p => (
                      <option key={p.id} value={p.id}>{p.name} (stock: {p.quantity})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-xs text-gray-400 block mb-1">Concurrent Orders</label>
                  <input
                    type="number" min="1" max="500"
                    value={stressForm.concurrentOrders}
                    onChange={e => setStressForm(f => ({ ...f, concurrentOrders: e.target.value }))}
                    className="w-full bg-ops-bg border border-ops-border rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:border-ops-accent"
                  />
                </div>
                <button
                  type="submit"
                  disabled={stressLoading}
                  className="w-full py-2 rounded-lg bg-ops-danger/80 text-white text-sm font-medium hover:bg-ops-danger transition-colors disabled:opacity-50"
                >
                  {stressLoading ? 'Running...' : 'Run Stress Test'}
                </button>
              </form>
              {stressResult && (
                <div className="mt-4 p-3 bg-ops-bg rounded-lg border border-ops-border text-xs space-y-1">
                  <p className="font-semibold text-gray-200">Results:</p>
                  <p className="text-ops-success">✅ Completed: {stressResult.completedOrders}</p>
                  <p className="text-ops-danger">❌ Out of Stock: {stressResult.outOfStockOrders}</p>
                  <p className="text-gray-400">📦 Remaining: {stressResult.remainingInventory}</p>
                  <p className={stressResult.inventoryNeverNegative ? 'text-ops-success' : 'text-ops-danger'}>
                    {stressResult.inventoryNeverNegative ? '✅ Never oversold' : '❌ OVERSOLD!'}
                  </p>
                  <p className="text-gray-500">⏱️ {stressResult.durationMs}ms</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Inventory Table */}
        <InventoryTable products={products} />

        {/* Orders Table */}
        <OrderTable orders={orders} onRetry={loadAll} />

      </main>
    </div>
  )
}
