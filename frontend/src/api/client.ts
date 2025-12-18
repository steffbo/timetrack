import axios from 'axios'
import type { AxiosInstance } from 'axios'

const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Store for access token (will be set by useAuth composable)
let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

// Request interceptor to add Authorization header
apiClient.interceptors.request.use(
  (config) => {
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor for 401 handling and token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // If 401 and not already retried, try to refresh token
    // EXCLUDE login and refresh endpoints from auto-handling to avoid loops/redirects
    const isAuthRequest = originalRequest.url?.includes('/api/auth/login') || originalRequest.url?.includes('/api/auth/refresh')

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthRequest) {
      originalRequest._retry = true

      try {
        // Import dynamically to avoid circular dependency
        const { useAuth } = await import('@/composables/useAuth')
        const { refreshAccessToken } = useAuth()
        await refreshAccessToken()

        // Retry original request with new token
        return apiClient(originalRequest)
      } catch (refreshError) {
        // Refresh failed, redirect to login
        const { useAuth } = await import('@/composables/useAuth')
        const { logout } = useAuth()
        logout()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  }
)

export default apiClient
