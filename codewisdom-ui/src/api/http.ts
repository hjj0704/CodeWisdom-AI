import axios, { type AxiosRequestConfig } from 'axios'
import { useAuthStore } from '../stores/auth'

export interface ApiResult<T> {
  code: number
  message: string
  data: T
  success: boolean
  traceId?: string
}

const http = axios.create({
  baseURL: '/api',
  timeout: 300_000,
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const code = error.response?.data?.code
    if (code === 40100) {
      const auth = useAuthStore()
      auth.logout()
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        window.location.assign('/login?redirect=' + encodeURIComponent(window.location.pathname))
      }
    }
    return Promise.reject(error)
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResult<T>>(config)
  const body = response.data
  if (body.code === 40100) {
    const auth = useAuthStore()
    auth.logout()
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      window.location.assign('/login?redirect=' + encodeURIComponent(window.location.pathname))
    }
  }
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

export { http }
