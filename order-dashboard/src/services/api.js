import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_URL || ''

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

export const orderApi = {
  getAll: () => api.get('/api/orders').then(r => r.data),
  getById: (id) => api.get(`/api/orders/${id}`).then(r => r.data),
  create: (data) => api.post('/api/orders', data).then(r => r.data),
  retry: (id) => api.post(`/api/orders/${id}/retry`).then(r => r.data),
}

export const inventoryApi = {
  getAll: () => api.get('/api/inventory').then(r => r.data),
  getById: (id) => api.get(`/api/inventory/${id}`).then(r => r.data),
}

export const dashboardApi = {
  getStats: () => api.get('/api/dashboard/stats').then(r => r.data),
}

export const deadLetterApi = {
  getAll: () => api.get('/api/dead-letter').then(r => r.data),
  retry: (id) => api.post(`/api/dead-letter/${id}/retry`).then(r => r.data),
}

export const devApi = {
  stressTest: (productId, concurrentOrders) =>
    api.post('/api/dev/stress-test', { productId, concurrentOrders }).then(r => r.data),
}

export default api
