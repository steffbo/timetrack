import apiClient from './client'
import type { LoginRequest, AuthResponse, UserResponse, RefreshTokenRequest } from './generated'

export const authApi = {
  async login(credentials: LoginRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/auth/login', credentials)
    return response.data
  },

  async logout(): Promise<void> {
    await apiClient.post('/api/auth/logout')
  },

  async getCurrentUser(): Promise<UserResponse> {
    const response = await apiClient.get<UserResponse>('/api/users/me')
    return response.data
  },

  async refreshToken(request: RefreshTokenRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/auth/refresh', request)
    return response.data
  }
}
