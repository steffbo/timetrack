# Timetrack Frontend

Vue 3 + TypeScript frontend for the Timetrack time tracking application.

## Tech Stack

- **Framework**: Vue 3 with Composition API (`<script setup>`)
- **Language**: TypeScript
- **Build Tool**: Vite
- **UI Library**: PrimeVue 4 (Aura theme)
- **Routing**: Vue Router
- **I18n**: vue-i18n (German & English)
- **HTTP Client**: Axios
- **API Client**: OpenAPI-generated TypeScript client

## Project Structure

```
frontend/
├── src/
│   ├── main.ts                     # App entry point
│   ├── App.vue                     # Root component
│   ├── router/
│   │   └── index.ts                # Route definitions & navigation guards
│   ├── views/                      # Page components (9 views)
│   │   ├── DashboardView.vue       # Dashboard with calendar & statistics
│   │   ├── LoginView.vue           # Authentication
│   │   ├── ProfileView.vue         # User profile management
│   │   ├── ScheduleView.vue        # Working hours configuration
│   │   ├── TimeEntriesView.vue     # Clock in/out & time tracking
│   │   ├── TimeOffView.vue         # Vacation & absence management
│   │   ├── RecurringOffDaysView.vue # Recurring pattern configuration
│   │   ├── PublicHolidaysView.vue  # German public holidays display
│   │   └── AdminUsersView.vue      # User administration (admin only)
│   ├── components/
│   │   ├── layout/                 # Layout components (AppLayout, AppNavbar)
│   │   ├── dashboard/              # Dashboard-specific components
│   │   └── ...                     # Reusable UI components
│   ├── composables/
│   │   ├── useAuth.ts              # Authentication state management
│   │   ├── useConflictWarnings.ts  # Conflict warning state
│   │   └── ...                     # Other composables
│   ├── api/
│   │   ├── generated/              # OpenAPI-generated TypeScript client (DO NOT EDIT)
│   │   ├── client.ts               # Axios instance with interceptors
│   │   └── *.ts                    # API service wrappers
│   └── i18n/
│       ├── index.ts                # I18n configuration
│       └── locales/
│           ├── de.json             # German translations
│           └── en.json             # English translations
├── public/                         # Static assets
├── package.json                    # Dependencies & scripts
├── vite.config.ts                  # Vite configuration
└── tsconfig.json                   # TypeScript configuration
```

## Available Views

1. **Dashboard** (`/dashboard`) - Overview with calendar, statistics, conflict warnings
2. **Login** (`/login`) - JWT authentication
3. **Profile** (`/profile`) - User profile & settings
4. **Schedule** (`/schedule`) - Working hours per weekday
5. **Time Entries** (`/time-entries`) - Clock in/out, daily summaries
6. **Time Off** (`/time-off`) - Vacation, sick, personal days
7. **Recurring Off-Days** (`/recurring-off-days`) - Weekly/monthly patterns
8. **Public Holidays** (`/public-holidays`) - German holidays by state
9. **Admin Users** (`/admin/users`) - User management (admin only)

## Development Workflow

### Setup

```bash
npm install                 # Install dependencies
npm run dev                 # Start dev server (http://localhost:5173)
```

### OpenAPI Code Generation

The frontend uses an auto-generated TypeScript API client from the OpenAPI spec:

```bash
npm run generate-api       # Regenerate from ../openapi.yaml
```

**Important**: Run this after any OpenAPI spec changes in the backend.

Generated files are in `src/api/generated/` - **DO NOT EDIT MANUALLY**.

### Building

```bash
npm run build              # Production build → dist/
npm run preview            # Preview production build
```

### Type Checking

```bash
npm run type-check         # Run TypeScript compiler
```

## Key Patterns

### Authentication

- JWT tokens stored in localStorage (`timetrack_access_token`, `timetrack_refresh_token`)
- Automatic token refresh on 401 via Axios interceptor
- Router guard validates tokens on navigation
- See: `src/composables/useAuth.ts`, `src/api/client.ts`, `src/router/index.ts`

### API Calls

```typescript
import { authApi, timeEntriesApi } from '@/api/generated'

// Use generated API services
const user = await authApi.getCurrentUser()
const entries = await timeEntriesApi.getTimeEntries({
  startDate: '2025-01-01',
  endDate: '2025-12-31'
})
```

### I18n Usage

```vue
<template>
  <!-- Template usage -->
  <h1>{{ $t('dashboard.title') }}</h1>
  <Button :label="$t('common.save')" />
</template>

<script setup>
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

// Script usage
const message = t('messages.success')
</script>
```

Translation keys are in `src/i18n/locales/de.json` and `en.json`.

### State Management

No global state management (Pinia/Vuex). Uses:
- **Composables** for shared logic (`useAuth`, `useConflictWarnings`)
- **Props & Emits** for component communication
- **API calls** for data fetching

### Component Libraries

- **PrimeVue 4**: DataTable, Calendar, Button, Dialog, etc.
- **Aura Theme**: Modern, clean design
- **Icons**: PrimeIcons (`pi pi-*`)

Import components globally in `main.ts` or locally in components.

## Configuration

### Environment Variables

Create `.env.development.local` (gitignored):

```bash
VITE_API_BASE_URL=http://localhost:8080/api
```

For production, use `.env.production`.

### Routing

Routes defined in `src/router/index.ts`. Protected routes require authentication:

```typescript
{
  path: '/dashboard',
  component: () => import('@/views/DashboardView.vue'),
  meta: { requiresAuth: true }  // Requires JWT token
}
```

Admin-only routes:

```typescript
{
  path: '/admin/users',
  component: () => import('@/views/AdminUsersView.vue'),
  meta: { requiresAuth: true, requiresAdmin: true }
}
```

## Testing Strategy

Currently: **Manual testing** during development.

Future: Consider adding:
- **Vitest** for unit tests
- **Vue Test Utils** for component tests
- **Playwright/Cypress** for E2E tests

## Troubleshooting

### API Client Out of Sync

```bash
# Regenerate API client after backend OpenAPI changes
npm run generate-api
```

### Type Errors After OpenAPI Change

1. Regenerate API client
2. Restart Vite dev server
3. Restart TypeScript language server in IDE

### Authentication Issues

- Check localStorage for tokens (DevTools → Application → Local Storage)
- Inspect network requests (DevTools → Network)
- Check axios interceptor logs in console

## Links

- **Root README**: `../README.md` - Project overview & setup
- **CLAUDE.md**: `../CLAUDE.md` - Development guide & conventions
- **CHANGELOG**: `../CHANGELOG.md` - Feature history
- **OpenAPI Spec**: `../openapi.yaml` - API definition
