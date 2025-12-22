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
let refreshPromise: Promise<void> | null = null

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
    // EXCLUDE auth endpoints (login, refresh, logout) from auto-handling to avoid loops
    const isAuthRequest = originalRequest.url?.includes('/api/auth/login') || 
                         originalRequest.url?.includes('/api/auth/refresh') ||
                         originalRequest.url?.includes('/api/auth/logout')

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthRequest) {
      originalRequest._retry = true

      try {
        // If refresh is already in progress, wait for it
        if (!refreshPromise) {
          // Start refresh process
          refreshPromise = (async () => {
            try {
              // Import dynamically to avoid circular dependency
              const { useAuth } = await import('@/composables/useAuth')
              const { refreshAccessToken } = useAuth()
              await refreshAccessToken()
            } finally {
              refreshPromise = null
            }
          })()
        }

        await refreshPromise
        // Retry original request with new token
        return apiClient(originalRequest)
      } catch (refreshError) {
        // Refresh failed, clear tokens and redirect
        clearTokensAndRedirect()
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  }
)

function clearTokensAndRedirect() {
  // Clear tokens immediately to prevent further refresh attempts
  accessToken = null
  setAccessToken(null)
  
  // Clear localStorage
  localStorage.removeItem('timetrack_access_token')
  localStorage.removeItem('timetrack_refresh_token')
  localStorage.removeItem('timetrack_user')
  
  // Redirect to login without making logout API call (which would trigger another 401)
  window.location.href = '/login'
}

export default apiClient
