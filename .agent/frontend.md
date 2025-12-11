# Timetrack Frontend Plan

## Overview
Build a Vue 3 + TypeScript frontend for the timetrack API. The frontend will be part of a monorepo structure and deployed alongside the backend using Docker Compose. External Caddy reverse proxy will handle domain routing.

**Design Philosophy:** Keep frontend complexity LOW. Favor more API calls over complex client-side state management. This is a low-traffic internal app - simplicity and maintainability over performance.

## Repository Structure

### Monorepo Layout
```
timetrack/
├── backend/              # Existing Spring Boot application (moved)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── ...
├── frontend/             # New Vue 3 + TypeScript application
│   ├── src/
│   │   ├── api/         # Auto-generated API client from OpenAPI
│   │   ├── assets/
│   │   ├── components/  # Reusable UI pieces (buttons, cards, inputs)
│   │   ├── composables/ # Optional: shared logic (useAuth, useApi)
│   │   ├── router/
│   │   ├── views/       # Full page components (connected to routes)
│   │   ├── App.vue
│   │   └── main.ts
│   ├── public/
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── Dockerfile
│   └── .env.example
├── docker-compose.yml    # Backend + Frontend services
├── .env.example
└── README.md
```

## Technology Stack

### Core Framework
- **Vue 3** - Using Composition API with `<script setup>`
- **TypeScript** - Full type safety
- **Vite** - Fast build tool and dev server

### Routing
- **Vue Router** - Client-side routing (necessary for multi-page navigation)

### State Management
- **No Pinia/Vuex** - Keep it simple!
- Use Vue's reactive refs directly in components or simple composables
- Each view fetches its own data when mounted
- Trade more API calls for less frontend complexity

### API Integration
- **Axios** - HTTP client with JWT interceptor
- **OpenAPI TypeScript Codegen** - Auto-generate TypeScript types and API client from `backend/src/main/resources/openapi/api.yaml`
- Simple JWT token management (localStorage + axios interceptor)

### UI Framework (To be decided)
Options:
1. **PrimeVue** - Comprehensive component library with themes
2. **Vuetify** - Material Design components
3. **Naive UI** - Modern, lightweight components
4. **TailwindCSS + Headless UI** - Custom styling with utility classes

### Development Tools
- **ESLint** - Code linting
- **Prettier** - Code formatting
- **TypeScript** - Type checking
- **Vite** - Hot module replacement

## Deployment Architecture

### Docker Compose Services
```yaml
services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL
      - JWT_SECRET

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    environment:
      - VITE_API_BASE_URL=http://backend:8080
```

### Caddy Reverse Proxy (External)
Caddy configuration (not in repo):
- `your-domain.com/api` → backend:8080
- `your-domain.com` → frontend:80

## Key Features to Implement

### 1. Authentication
- **Login page** (`/login`)
  - Email + password form
  - JWT token storage (httpOnly cookies or localStorage)
  - Redirect to dashboard on success
- **Token refresh** mechanism
  - Automatic refresh before expiration
  - Axios interceptor for 401 handling
- **Logout** functionality
  - Clear tokens
  - Redirect to login
- **Auth guard** for protected routes

### 2. User Management
- **User dashboard** (`/dashboard`)
  - Current user profile display
  - Quick stats overview
- **Profile page** (`/profile`)
  - View/edit own profile
  - Change password
- **Admin user management** (`/admin/users`)
  - List all users
  - Create new users
  - Edit/delete users
  - Role-based access control

### 3. Working Hours Configuration
- **Working hours setup** (`/working-hours`)
  - Display 7 days of the week
  - Toggle working day on/off
  - Set hours per day (0-24)
  - Save configuration
  - Visual representation (chart/table)

### 4. Time Tracking (Future - not in current API)
- Clock in/out functionality
- Time entry history
- Edit past entries
- Delete entries

### 5. Statistics & Reports (Future - not in current API)
- Daily/weekly/monthly summaries
- Hours worked vs. target hours
- Overtime calculations
- Export reports

## Component Architecture

### Views vs Components

**Views** = Full page components connected to routes (in `src/views/`)
- LoginView.vue
- DashboardView.vue
- WorkingHoursView.vue
- ProfileView.vue
- UserManagementView.vue

**Components** = Reusable UI pieces (in `src/components/`)
- WorkingDayInput.vue (used 7 times in WorkingHoursView)
- UserCard.vue (display user info)
- NavBar.vue (used in all views)
- LoadingSpinner.vue (used everywhere)

**Note:** Technically both are just `.vue` components. The distinction is purely organizational - views are connected to routes, components are reusable pieces.

## Data Fetching Pattern

**Simple approach:** Each view fetches its own data when mounted. No central store.

### Example: ProfileView.vue
```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/api'
import type { UserResponse } from '@/api/models'

const user = ref<UserResponse | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    user.value = await api.getCurrentUser()
  } catch (error) {
    console.error('Failed to load user', error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-if="loading">Loading...</div>
  <div v-else>
    <h1>{{ user.firstName }} {{ user.lastName }}</h1>
    <p>{{ user.email }}</p>
  </div>
</template>
```

### Optional: Composables for Shared Logic

If multiple views need the same data/logic, extract to a composable:

```typescript
// composables/useAuth.ts
import { ref } from 'vue'

const token = ref<string | null>(localStorage.getItem('token'))

export function useAuth() {
  async function login(email: string, password: string) {
    const response = await api.login({ email, password })
    token.value = response.accessToken
    localStorage.setItem('token', response.accessToken)
  }

  function logout() {
    token.value = null
    localStorage.removeItem('token')
  }

  return { token, login, logout }
}
```

**But:** Only add composables if actually needed. Start with direct API calls in views.

## Routing Structure

```typescript
routes: [
  {
    path: '/login',
    component: LoginView,
    meta: { public: true }
  },
  {
    path: '/',
    redirect: '/dashboard',
    meta: { requiresAuth: true }
  },
  {
    path: '/dashboard',
    component: DashboardView,
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    component: ProfileView,
    meta: { requiresAuth: true }
  },
  {
    path: '/working-hours',
    component: WorkingHoursView,
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresRole: 'ADMIN' },
    children: [
      {
        path: 'users',
        component: UserManagementView
      }
    ]
  }
]
```

## API Client Generation

### OpenAPI TypeScript Codegen
Use `openapi-typescript-codegen` to generate:
- TypeScript interfaces for all API models
- API client methods for all endpoints
- Automatic request/response typing

```bash
npx openapi-typescript-codegen \
  --input ../backend/src/main/resources/openapi/api.yaml \
  --output ./src/api \
  --client axios
```

### Axios Configuration
- Base URL from environment variable
- JWT token interceptor (add Bearer token to requests)
- Refresh token interceptor (handle 401, refresh, retry)
- Error handling interceptor

## Environment Configuration

### Frontend `.env` files
```env
VITE_API_BASE_URL=http://localhost:8080
```

For production:
```env
VITE_API_BASE_URL=/api
```

## Docker Setup

### Frontend Dockerfile
Multi-stage build:
1. **Build stage**: `node:20-alpine` - Build Vue app with Vite
2. **Production stage**: `nginx:alpine` - Serve static files

### Nginx Configuration
- Serve static files from `/usr/share/nginx/html`
- Fallback to `index.html` for SPA routing
- No API proxying (handled by external Caddy)

## Implementation Steps

1. **Repository Restructuring**
   - Move current backend code to `backend/` directory
   - Update paths in Docker and CI configs

2. **Frontend Initialization**
   - Create Vue 3 + TypeScript project with Vite
   - Install core dependencies (Vue Router, Axios)
   - Choose and install UI framework
   - Set up project structure

3. **API Client Setup**
   - Install openapi-typescript-codegen
   - Generate API client from OpenAPI spec
   - Configure Axios with JWT interceptor

4. **Authentication Implementation**
   - Create simple useAuth composable (or inline in LoginView)
   - Build login page
   - Implement JWT token management (localStorage + axios interceptor)
   - Add auth guards to router

5. **Core Pages**
   - Dashboard view
   - User profile page
   - Working hours configuration

6. **Admin Features**
   - User management interface
   - Role-based access control

7. **Docker Integration**
   - Create frontend Dockerfile
   - Update docker-compose.yml
   - Test full stack locally

## Testing Strategy

**Keep it simple for now:**
- **Type checking**: TypeScript strict mode (catches most bugs)
- **Manual testing**: For low-traffic internal app, manual testing is sufficient
- **Future**: Add unit tests (Vitest) or E2E tests (Playwright) if needed

## Open Questions

1. **UI Framework**: Which UI component library to use?
   - PrimeVue (comprehensive)
   - Vuetify (Material Design)
   - Naive UI (modern, lightweight)
   - TailwindCSS + Headless (custom styling)

2. **Token Storage**: Where to store JWT tokens?
   - localStorage (simple, XSS vulnerable)
   - httpOnly cookies (more secure, needs backend support)

3. **Internationalization**: Support multiple languages?
   - German as primary language (API errors are in German)
   - English as secondary?

4. **Time Zone Handling**: How to handle user time zones?
   - Store in UTC, display in user's local time
   - Add timezone field to user profile

## Future Enhancements

- Time entry tracking (when backend API is ready)
- Statistics and reports
- Calendar view for time entries
- Mobile responsive design
- Progressive Web App (PWA) features
- Dark mode
- Notifications for clock-in/out reminders
