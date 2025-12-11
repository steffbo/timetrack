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
