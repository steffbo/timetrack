# Timetrack Frontend Implementation Plan

## Overview
Build a Vue 3 + TypeScript frontend for the timetrack API with JWT Authorization header authentication, PrimeVue UI components, and German/English i18n support. The frontend will be part of a monorepo structure and deployed alongside the backend using Docker Compose.

**Design Philosophy:** Keep frontend complexity LOW. Favor more API calls over complex client-side state management. This is a low-traffic internal app - simplicity and maintainability over performance.

## Technology Stack Decisions

### Core Framework
- **Vue 3** - Using Composition API with `<script setup>`
- **TypeScript** - Full type safety with strict mode
- **Vite** - Fast build tool and dev server

### UI Framework
- **PrimeVue** - Comprehensive component library with excellent theming support
- **PrimeIcons** - Icon library
- **Rationale**: Rich component set (DataTable, Dialog, Toast, etc.), good German locale support, minimal custom styling needed

### API Integration
- **Axios** - HTTP client with interceptors
- **OpenAPI TypeScript Codegen** - Auto-generate TypeScript types and API client from `backend/src/main/resources/openapi/api.yaml`
- **Authentication**: JWT tokens in Authorization header (mobile-friendly, simpler CORS)

### Internationalization
- **vue-i18n** - i18n library supporting German (primary) and English
- **Approach**: JSON translation files for all UI strings

### State Management
- **No Pinia/Vuex** - Keep it simple with Vue's reactive refs and composables
- **Pattern**: Each view fetches its own data, shared state in composables where needed
- **Rationale**: Reduces complexity, easier to understand and maintain

### Routing
- **Vue Router 4** - Client-side routing with navigation guards

## Repository Structure

```
timetrack/
├── backend/              # Spring Boot application
│   └── ...
├── frontend/             # Vue 3 + TypeScript application
│   ├── src/
│   │   ├── main.ts                    # App entry point
│   │   ├── App.vue                    # Root component
│   │   ├── router/
│   │   │   └── index.ts               # Routes + auth guards
│   │   ├── api/
│   │   │   ├── generated/             # Auto-generated from OpenAPI
│   │   │   ├── client.ts              # Axios config with interceptors
│   │   │   └── auth.ts                # Authentication API wrapper
│   │   ├── composables/
│   │   │   └── useAuth.ts             # Auth state management
│   │   ├── i18n/
│   │   │   ├── index.ts               # i18n configuration
│   │   │   └── locales/
│   │   │       ├── de.json            # German translations
│   │   │       └── en.json            # English translations
│   │   ├── views/
│   │   │   ├── LoginView.vue
│   │   │   ├── DashboardView.vue
│   │   │   ├── ProfileView.vue
│   │   │   ├── WorkingHoursView.vue
│   │   │   └── AdminUsersView.vue
│   │   ├── components/
│   │   │   └── layout/
│   │   │       ├── AppLayout.vue      # Main layout with navbar
│   │   │       └── AppNavbar.vue      # Navigation bar
│   │   ├── types/
│   │   │   └── index.ts               # Custom TypeScript types
│   │   └── assets/
│   │       └── styles/
│   │           └── main.css           # Global styles
│   ├── public/
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── Dockerfile                      # Multi-stage build
│   ├── nginx.conf                      # Nginx config for production
│   └── .env.example
├── docker-compose.yml
└── README.md
```

## Backend Prerequisites

The backend is ready and supports JWT authentication via Authorization header. Key endpoints available:

- `POST /api/auth/login` - Returns `{accessToken, refreshToken, tokenType, expiresIn, user}`
- `POST /api/auth/refresh` - Accepts `{refreshToken}`, returns new tokens
- `POST /api/auth/logout` - Invalidates refresh token
- `GET /api/users` - List all users (Admin only)
- `GET /api/users/me` - Get current user profile
- All other authenticated endpoints require `Authorization: Bearer <token>` header

No backend changes needed - ready to implement frontend!

## Frontend Implementation Steps

### Phase 1: Project Initialization

```bash
cd /Users/stefan.remer/workspace/timetrack
npm create vite@latest frontend -- --template vue-ts
cd frontend

# Install core dependencies
npm install vue-router@4 axios vue-i18n@9 primevue primeicons

# Install dev dependencies
npm install -D @types/node openapi-typescript-codegen
```

### Phase 2: Configuration

#### package.json Scripts
```json
{
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc && vite build",
    "preview": "vite preview",
    "generate-api": "openapi --input ./openapi/api.yaml --output ./src/api/generated --client axios"
  }
}
```

#### vite.config.ts
```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

#### tsconfig.json
Enable strict mode, path aliases:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "strict": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

#### Environment Variables
- `.env.development`: `VITE_API_BASE_URL=http://localhost:5173`
- `.env.production`: `VITE_API_BASE_URL=http://localhost:8080`

### Phase 3: API Client Setup

Copy OpenAPI spec:
```bash
mkdir openapi
cp ../backend/src/main/resources/openapi/api.yaml openapi/
npm run generate-api
```

#### src/api/client.ts
```typescript
import axios from 'axios'
import type { AxiosInstance } from 'axios'
import { useAuth } from '@/composables/useAuth'

const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor to add Authorization header
apiClient.interceptors.request.use(
  (config) => {
    const { getAccessToken } = useAuth()
    const token = getAccessToken()

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
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
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        const { refreshAccessToken } = useAuth()
        await refreshAccessToken()

        // Retry original request with new token
        return apiClient(originalRequest)
      } catch (refreshError) {
        // Refresh failed, redirect to login
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
```

#### src/api/auth.ts
```typescript
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

  async refreshToken(refreshToken: string): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/auth/refresh', {
      refreshToken
    } as RefreshTokenRequest)
    return response.data
  }
}
```

### Phase 4: Authentication Composable

#### src/composables/useAuth.ts
```typescript
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { UserResponse, LoginRequest } from '@/api/generated'

// Global reactive state (shared across components)
const currentUser = ref<UserResponse | null>(null)
const accessToken = ref<string | null>(null)
const refreshToken = ref<string | null>(null)
const isLoading = ref(false)

export function useAuth() {
  const isAuthenticated = computed(() => accessToken.value !== null)
  const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

  function getAccessToken() {
    return accessToken.value
  }

  async function login(credentials: LoginRequest) {
    isLoading.value = true
    try {
      const response = await authApi.login(credentials)
      accessToken.value = response.accessToken
      refreshToken.value = response.refreshToken
      currentUser.value = response.user!
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
      accessToken.value = null
      refreshToken.value = null
      currentUser.value = null
    }
  }

  async function refreshAccessToken() {
    if (!refreshToken.value) {
      throw new Error('No refresh token available')
    }

    const response = await authApi.refreshToken(refreshToken.value)
    accessToken.value = response.accessToken
    refreshToken.value = response.refreshToken
    if (response.user) {
      currentUser.value = response.user
    }
  }

  async function checkAuth() {
    if (!accessToken.value) {
      return false
    }

    try {
      const user = await authApi.getCurrentUser()
      currentUser.value = user
      return true
    } catch (error) {
      accessToken.value = null
      refreshToken.value = null
      currentUser.value = null
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
```

**Key Pattern**: Tokens are stored in memory (reactive refs). This means they're lost on page refresh, requiring re-login. For persistence across refreshes, consider using localStorage with the same pattern.

### Phase 5: Router Setup

#### src/router/index.ts
```typescript
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    redirect: '/dashboard',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue')
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue')
      },
      {
        path: 'working-hours',
        name: 'working-hours',
        component: () => import('@/views/WorkingHoursView.vue')
      },
      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/views/AdminUsersView.vue'),
        meta: { requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard
router.beforeEach(async (to, from, next) => {
  const { isAuthenticated, isAdmin, checkAuth } = useAuth()

  // Check authentication if needed
  if (!isAuthenticated.value && to.meta.requiresAuth !== false) {
    await checkAuth()
  }

  // Redirect to login if not authenticated
  if (to.meta.requiresAuth !== false && !isAuthenticated.value) {
    next('/login')
    return
  }

  // Redirect to dashboard if authenticated user tries to access login
  if (to.name === 'login' && isAuthenticated.value) {
    next('/dashboard')
    return
  }

  // Check admin access
  if (to.meta.requiresAdmin && !isAdmin.value) {
    next('/dashboard')
    return
  }

  next()
})

export default router
```

### Phase 6: i18n Setup

#### src/i18n/index.ts
```typescript
import { createI18n } from 'vue-i18n'
import de from './locales/de.json'
import en from './locales/en.json'

const i18n = createI18n({
  legacy: false, // Use Composition API mode
  locale: 'de',  // Default language
  fallbackLocale: 'en',
  messages: {
    de,
    en
  }
})

export default i18n
```

#### Translation Files
Create `src/i18n/locales/de.json` and `en.json` with translations for:
- Navigation (dashboard, profile, workingHours, adminUsers, logout)
- Login form (title, email, password, submit, error)
- Profile form (firstName, lastName, email, role, active, save, cancel, success messages)
- Working hours (weekday names, hours, isWorkingDay, save, success)
- User management (createUser, editUser, deleteUser, confirmations, success messages)
- Common (yes, no, error, loading)

### Phase 7: PrimeVue Setup

#### src/main.ts
```typescript
import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import App from './App.vue'
import router from './router'
import i18n from './i18n'

// PrimeVue CSS
import 'primevue/resources/themes/lara-light-blue/theme.css'
import 'primevue/resources/primevue.min.css'
import 'primeicons/primeicons.css'

const app = createApp(App)

app.use(PrimeVue)
app.use(ToastService)
app.use(ConfirmationService)
app.use(router)
app.use(i18n)

app.mount('#app')
```

### Phase 8: Views Implementation

Build views in this priority order:

1. **LoginView.vue** - Login form with email/password using PrimeVue Card, InputText, Password, Button components
2. **AppLayout.vue** - Container with AppNavbar + router-view outlet
3. **AppNavbar.vue** - Menubar with dynamic items based on role, language toggle, user email display, logout button
4. **DashboardView.vue** - Simple welcome page showing user name
5. **ProfileView.vue** - Form to edit own profile (firstName, lastName, email, password change)
6. **WorkingHoursView.vue** - DataTable with 7 weekdays, Checkbox for isWorkingDay, InputNumber for hours (0-24)
7. **AdminUsersView.vue** - DataTable for user list, Dialog for create/edit, ConfirmDialog for delete confirmation

**PrimeVue Components Used**:
- Card, InputText, Password, Button, Toast
- Menubar, DataTable, Column
- InputNumber, Checkbox, Dropdown
- Dialog, ConfirmDialog

**Role-Based UI Pattern**:
```vue
<script setup>
const { isAdmin } = useAuth()
</script>

<template>
  <MenuItem v-if="isAdmin" label="User Management" @click="navigate" />
</template>
```

### Phase 9: Docker Setup

#### Dockerfile
```dockerfile
# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

#### nginx.conf
```nginx
events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    server {
        listen 80;
        server_name localhost;
        root /usr/share/nginx/html;
        index index.html;

        gzip on;
        gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

        # SPA routing - serve index.html for all routes
        location / {
            try_files $uri $uri/ /index.html;
        }

        # Proxy API requests to backend
        location /api {
            proxy_pass http://app:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_pass_request_headers on;
        }

        # Cache static assets
        location ~* \.(jpg|jpeg|png|gif|ico|css|js|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }
}
```

#### Update docker-compose.yml
Add frontend service:
```yaml
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: timetrack-frontend
    ports:
      - "3000:80"
    depends_on:
      - app
    networks:
      - timetrack-network
    restart: unless-stopped
```

Update backend allowed origins:
```yaml
  app:
    environment:
      ALLOWED_ORIGINS: http://localhost:3000,http://frontend
```

## Authentication Flow with Authorization Header

### Login Flow
1. User submits credentials to `/api/auth/login`
2. Backend validates, returns `{accessToken, refreshToken, tokenType, expiresIn, user}`
3. Frontend stores tokens in memory (reactive refs) and user data
4. All subsequent API calls include `Authorization: Bearer <accessToken>` header (via axios interceptor)

### Auth Check
1. Check if `accessToken` exists in memory
2. If yes, call `GET /api/users/me` to verify token is still valid
3. If 200 with user data → authenticated
4. If 401 or no token → not authenticated

### Token Refresh
1. API call returns 401
2. Axios interceptor catches it
3. Calls `POST /api/auth/refresh` with `refreshToken` from memory
4. Backend validates, returns new `accessToken` and `refreshToken`
5. Update tokens in memory
6. Retry original request with new `Authorization` header
7. If refresh fails, clear tokens and redirect to login

### Logout
1. Call `POST /api/auth/logout` (invalidates refresh token on backend)
2. Clear tokens and user data from memory
3. Redirect to login

### Note on Persistence
Tokens are stored in memory, so they're lost on page refresh. For persistence:
- Option 1: Store in localStorage (simpler but XSS vulnerable)
- Option 2: Use sessionStorage (lost when tab closes)
- Option 3: Accept re-login on refresh (most secure)

## Testing Strategy

### Development Testing
```bash
# Terminal 1 - Backend
cd backend
./mvnw spring-boot:run

# Terminal 2 - Frontend
cd frontend
npm run dev
```

Visit http://localhost:5173, login with admin@timetrack.local / admin

**Verify**:
- Browser DevTools → Network tab
- Login request should return tokens in response body
- Subsequent API requests should have `Authorization: Bearer <token>` header
- Refresh page will require re-login (tokens in memory only)

### Production Testing
```bash
docker-compose up --build
```

Visit http://localhost:3000

## Implementation Checklist

### Backend Prerequisites
- [x] Backend ready with Authorization header authentication
- [x] GET /api/users endpoint added
- [x] OpenAPI spec moved to repository root (single source of truth)

### Frontend Core
- [x] Initialize Vite project with Vue 3 + TypeScript
- [x] Install dependencies (vue-router, axios, vue-i18n, primevue, @primevue/themes)
- [x] Configure vite.config.ts with proxy and path aliases
- [x] Generate API client from OpenAPI spec (auto-generated types)
- [x] Create Axios client with Authorization header interceptors
- [x] Create auth API wrapper
- [x] Create useAuth composable with localStorage persistence
- [x] Set up i18n with German and English translations
- [x] Configure Vue Router with auth guards
- [x] Set up PrimeVue 4 in main.ts (Aura theme)

### Views
- [x] LoginView - JWT authentication form
- [x] AppLayout + AppNavbar - Main layout with role-based navigation
- [x] DashboardView - Welcome page
- [x] ProfileView - User profile editing with password change
- [x] WorkingHoursView - Weekly working hours configuration (inline DataTable editing)
- [x] AdminUsersView - Full CRUD user management (admin only)

### Development Testing
- [x] Login/logout flow working
- [x] Token persistence with localStorage (survives page reload)
- [x] Role-based navigation (admin sees extra menu items)
- [x] All views functional with backend API
- [x] German i18n active and working

### Docker
- [ ] Create Dockerfile (multi-stage)
- [ ] Create nginx.conf
- [ ] Update docker-compose.yml
- [ ] Test full stack deployment

## Status: Development Complete ✅
Frontend is fully functional in development mode. Docker deployment remaining.

## Key Decisions

### Why Authorization Header?
- **Mobile-Friendly**: Easy to implement in React Native, Flutter, etc.
- **Explicit Control**: Full control over when tokens are sent
- **Simpler CORS**: No need for `credentials: true` everywhere
- **Development**: Easier to debug (tokens visible in DevTools)

### Why PrimeVue?
- **Comprehensive**: Rich component library (DataTable, Dialog, Toast, etc.)
- **Themed**: Professional look out of the box
- **i18n Ready**: Good locale support for German
- **Less Code**: Minimal custom styling needed

### Why Simple State Management?
- **Maintainability**: Easier to understand and debug
- **Low Traffic**: Performance not critical
- **Simpler**: Each view fetches own data, no complex store
- **Good Enough**: Composables handle shared state (auth)

### Why TypeScript Strict Mode?
- **Type Safety**: Catch errors at compile time
- **Better DX**: Better IDE autocomplete and refactoring
- **Documentation**: Types serve as inline documentation
- **OpenAPI**: Generated types match backend exactly

## Estimated Timeline

- Backend changes: 1 day
- Frontend setup + API client: 1 day
- Auth + routing + i18n: 1 day
- Views implementation: 2 days
- Docker setup + testing: 1 day

**Total**: 5-6 days

## Future Enhancements

- Loading skeletons for better UX
- Form validation with visual feedback
- Pagination for user list
- Search/filter for DataTables
- Unit tests with Vitest
- E2E tests with Playwright
- Dark mode support
- PWA features (offline support, install prompt)
- Time entry tracking (when backend ready)
- Statistics and reports (when backend ready)
