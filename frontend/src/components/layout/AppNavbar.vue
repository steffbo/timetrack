<template>
  <Menubar :model="menuItems">
    <template #start>
      <div class="navbar-brand" @click="goToDashboard">
        <img src="/favicon-96x96.png" alt="TymeTrack" class="navbar-logo" />
        <span class="navbar-title">TymeTrack</span>
      </div>
    </template>
    <template #end>
      <div class="navbar-end">
        <!-- Warnings Icon -->
        <Button
          v-if="unacknowledgedCount > 0"
          ref="warningsButton"
          :badge="unacknowledgedCount.toString()"
          badgeSeverity="warn"
          icon="pi pi-exclamation-triangle"
          severity="warning"
          text
          rounded
          @click="toggleWarnings"
          :aria-label="t('dashboard.warnings.title')"
          class="warnings-button"
        />
        <Popover
          ref="warningsPopover"
          :dismissable="true"
          :style="{ width: '90vw', maxWidth: '550px' }"
        >
          <WarningsCard :inline="true" />
        </Popover>

        <!-- Impersonation Indicator -->
        <div v-if="isImpersonating" class="impersonation-indicator">
          <i class="pi pi-exclamation-triangle impersonation-icon"></i>
          <span class="impersonation-text">{{ impersonatedEmail }}</span>
          <Button
            icon="pi pi-times"
            severity="danger"
            text
            rounded
            @click="stopImpersonating"
            :aria-label="t('impersonation.stop')"
            class="impersonation-exit-button"
          />
        </div>

        <span class="user-name">{{ displayName }}</span>
        <Button
          class="user-avatar-button"
          :label="userInitials"
          text
          rounded
          @click="toggleUserMenu"
        />
        <Menu
          ref="userMenu"
          :model="userMenuItems"
          :popup="true"
        />
      </div>
    </template>
  </Menubar>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Menubar from 'primevue/menubar'
import Button from 'primevue/button'
import Menu from 'primevue/menu'
import Popover from 'primevue/popover'
import WarningsCard from '@/components/dashboard/WarningsCard.vue'
import { useAuth } from '@/composables/useAuth'
import { useConflictWarnings } from '@/composables/useConflictWarnings'
import type { MenuItem } from 'primevue/menuitem'

const router = useRouter()
const { t } = useI18n()
const { currentUser, isAdmin, logout } = useAuth()
const { unacknowledgedCount, loadWarnings } = useConflictWarnings()
const userMenu = ref()
const warningsButton = ref()
const warningsPopover = ref()

// Impersonation state
const isImpersonating = ref(false)
const impersonatedEmail = ref('')

function checkImpersonation() {
  const adminToken = sessionStorage.getItem('admin_token')
  const email = sessionStorage.getItem('impersonated_email')

  if (adminToken && email) {
    isImpersonating.value = true
    impersonatedEmail.value = email
  } else {
    isImpersonating.value = false
    impersonatedEmail.value = ''
  }
}

function stopImpersonating() {
  const adminToken = sessionStorage.getItem('admin_token')
  const adminRefreshToken = sessionStorage.getItem('admin_refresh_token')

  if (adminToken) {
    // Restore admin tokens using correct keys
    localStorage.setItem('timetrack_access_token', adminToken)
    if (adminRefreshToken) {
      localStorage.setItem('timetrack_refresh_token', adminRefreshToken)
    }

    // Clear impersonation data
    sessionStorage.removeItem('admin_token')
    sessionStorage.removeItem('admin_refresh_token')
    sessionStorage.removeItem('impersonated_email')

    // Force full page reload to clear all cached state
    window.location.replace('/admin/users')
  }
}

const displayName = computed(() => {
  if (!currentUser.value) return ''

  const firstName = currentUser.value.firstName
  if (firstName && firstName.trim()) {
    return firstName.trim()
  }

  return currentUser.value.email || ''
})

const userInitials = computed(() => {
  if (!currentUser.value) return '?'

  const firstName = currentUser.value.firstName?.trim() || ''
  const lastName = currentUser.value.lastName?.trim() || ''

  if (firstName && lastName) {
    return (firstName[0] + lastName[0]).toUpperCase()
  }

  if (currentUser.value.email) {
    return currentUser.value.email[0].toUpperCase()
  }

  return '?'
})

const userMenuItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [
    {
      label: t('nav.settings'),
      icon: 'pi pi-cog',
      command: () => router.push('/profile')
    }
  ]

  if (isAdmin.value) {
    items.push({
      label: t('nav.adminUsers'),
      icon: 'pi pi-users',
      command: () => router.push('/admin/users')
    })
  }

  items.push({
    separator: true
  })

  items.push({
    label: t('nav.logout'),
    icon: 'pi pi-sign-out',
    command: handleLogout
  })

  return items
})

const menuItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [
    {
      label: t('nav.schedule'),
      icon: 'pi pi-clock',
      command: () => router.push('/schedule')
    },
    {
      label: t('nav.timeEntries'),
      icon: 'pi pi-play-circle',
      command: () => router.push('/time-entries')
    },
    {
      label: t('nav.timeOff'),
      icon: 'pi pi-calendar-times',
      command: () => router.push('/time-off')
    },
    {
      label: t('nav.publicHolidays'),
      icon: 'pi pi-sun',
      command: () => router.push('/public-holidays')
    }
  ]

  return items
})

function toggleUserMenu(event: Event) {
  userMenu.value.toggle(event)
}

function toggleWarnings(event: Event) {
  if (warningsPopover.value && warningsButton.value) {
    const targetElement = warningsButton.value.$el || warningsButton.value
    warningsPopover.value.toggle(event, targetElement)
  }
}

function goToDashboard() {
  router.push('/dashboard')
}

async function handleLogout() {
  await logout()
  router.push('/login')
}

onMounted(() => {
  // Check impersonation state
  checkImpersonation()

  // Load warnings on mount so navbar icon appears
  loadWarnings(false)
  // Reload warnings count every minute
  setInterval(() => loadWarnings(false), 60000)
})
</script>

<style scoped>
/* 60-30-10 Color Distribution: Navbar is part of 30% (Dark Contrast) */
:deep(.p-menubar) {
  background: var(--tt-color-60-primary);  /* White background */
  border-bottom: 1px solid var(--p-surface-border);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);  /* Subtle depth */
  display: flex;
  flex-wrap: nowrap;
}

/* Ensure menubar end section stays right-aligned */
:deep(.p-menubar-end) {
  margin-left: auto;
}

:deep(.p-menubar-start),
:deep(.p-menubar-end) {
  flex: 0 0 auto;
}

:deep(.p-menubar-root-list) {
  flex: 1 1 auto;
  min-width: 0;
  flex-wrap: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

:deep(.p-menubar-root-list)::-webkit-scrollbar {
  display: none;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-sm);  /* 12px - aligned to grid */
  cursor: pointer;
  padding: var(--tt-spacing-2xs) var(--tt-spacing-xs);  /* 4px 8px - aligned to grid */
  border-radius: var(--p-border-radius);
  transition: background-color 0.2s;
}

.navbar-brand:hover {
  background-color: var(--p-surface-hover);
}

.navbar-logo {
  height: 2rem;
  width: 2rem;
  flex-shrink: 0;
}

.navbar-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--p-text-color);
  white-space: nowrap;
}

.navbar-end {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-sm);  /* 12px - aligned to grid */
}

.user-name {
  color: var(--p-text-color);
  font-weight: 500;
  font-size: 1rem;  /* 16px - aligned to grid */
}

.user-avatar-button {
  width: 2.5rem;   /* 40px - aligned to 8px grid */
  height: 2.5rem;  /* 40px - aligned to 8px grid */
  border-radius: 50%;
  background: var(--tt-color-10-primary);  /* Using 10% accent color */
  color: white;
  font-weight: 600;
  font-size: 1rem;  /* 16px - aligned to grid */
  padding: 0;
  min-width: 2.5rem;
}

.user-avatar-button:hover {
  background: var(--tt-color-10-hover);  /* Using 10% accent hover color */
}

/* Impersonation indicator */
.impersonation-indicator {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);  /* 8px - aligned to grid */
  background: rgba(239, 68, 68, 0.1);  /* Light red background */
  border: 1px solid #ef4444;
  border-radius: var(--p-border-radius);
  padding: var(--tt-spacing-2xs) var(--tt-spacing-xs);  /* 4px 8px */
}

.impersonation-icon {
  color: #ef4444;  /* Red color */
  font-size: 1rem;
  animation: pulse 2s ease-in-out infinite;
}

.impersonation-text {
  color: #dc2626;  /* Darker red */
  font-weight: 500;
  font-size: 0.875rem;  /* 14px */
  white-space: nowrap;
}

.impersonation-exit-button {
  color: #ef4444;
  width: 2rem;
  height: 2rem;
  padding: 0;
  min-width: 2rem;
}

.impersonation-exit-button:hover {
  background: rgba(239, 68, 68, 0.2);
}

@media (max-width: 1100px) {
  .navbar-title {
    display: none;
  }

  .navbar-logo {
    height: 1.75rem;
    width: 1.75rem;
  }

  .navbar-end {
    gap: var(--tt-spacing-xs);
  }

  .user-name {
    display: none;
  }

  :deep(.p-menubar-root-list > .p-menuitem > .p-menuitem-link) {
    padding: 0.5rem 0.5rem;
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}
</style>
