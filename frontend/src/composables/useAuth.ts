import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { setAccessToken } from '@/api/client'
import type { UserResponse, LoginRequest } from '@/api/generated'

// Storage keys
const ACCESS_TOKEN_KEY = 'timetrack_access_token'
const REFRESH_TOKEN_KEY = 'timetrack_refresh_token'
const USER_KEY = 'timetrack_user'

// Load from localStorage on initialization
const accessTokenValue = ref<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY))
const refreshTokenValue = ref<string | null>(localStorage.getItem(REFRESH_TOKEN_KEY))
const currentUser = ref<UserResponse | null>(
  localStorage.getItem(USER_KEY) ? JSON.parse(localStorage.getItem(USER_KEY)!) : null
)
const isLoading = ref(false)

// Initialize API client with stored token
if (accessTokenValue.value) {
  setAccessToken(accessTokenValue.value)
}

export function useAuth() {
  const isAuthenticated = computed(() => accessTokenValue.value !== null)
  const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

  function getAccessToken() {
    return accessTokenValue.value
  }

  async function login(credentials: LoginRequest) {
    isLoading.value = true
    try {
      const response = await authApi.login(credentials)
      accessTokenValue.value = response.accessToken!
      refreshTokenValue.value = response.refreshToken!
      currentUser.value = response.user!

      // Persist to localStorage
      localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken!)
      localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken!)
      localStorage.setItem(USER_KEY, JSON.stringify(response.user!))

      // Update the API client with the new token
      setAccessToken(response.accessToken!)

      return true
    } catch (error) {
      console.error('Login failed:', error)
      return false
    } finally {
      isLoading.value = false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (error) {
      console.error('Logout failed:', error)
    } finally {
      accessTokenValue.value = null
      refreshTokenValue.value = null
      currentUser.value = null

      // Clear localStorage
      localStorage.removeItem(ACCESS_TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
      localStorage.removeItem(USER_KEY)

      setAccessToken(null)
    }
  }

  async function refreshAccessToken() {
    if (!refreshTokenValue.value) {
      throw new Error('No refresh token available')
    }

    const response = await authApi.refreshToken(refreshTokenValue.value)
    accessTokenValue.value = response.accessToken!
    refreshTokenValue.value = response.refreshToken!
    if (response.user) {
      currentUser.value = response.user
    }

    // Persist to localStorage
    localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken!)
    localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken!)
    if (response.user) {
      localStorage.setItem(USER_KEY, JSON.stringify(response.user))
    }

    // Update the API client with the new token
    setAccessToken(response.accessToken!)
  }

  async function checkAuth() {
    if (!accessTokenValue.value) {
      return false
    }

    try {
      const user = await authApi.getCurrentUser()
      currentUser.value = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return true
    } catch (error) {
      accessTokenValue.value = null
      refreshTokenValue.value = null
      currentUser.value = null

      // Clear localStorage
      localStorage.removeItem(ACCESS_TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
      localStorage.removeItem(USER_KEY)

      setAccessToken(null)
      return false
    }
  }

  return {
    currentUser: computed(() => currentUser.value),
    isAuthenticated,
    isAdmin,
    isLoading: computed(() => isLoading.value),
    getAccessToken,
    login,
    logout,
    refreshAccessToken,
    checkAuth
  }
}
