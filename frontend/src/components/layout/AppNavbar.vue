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
          :style="{ width: '90vw', maxWidth: '500px' }"
        >
          <WarningsCard :inline="true" />
        </Popover>

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
  // Load warnings on mount so navbar icon appears
  loadWarnings(false)
  // Reload warnings count every minute
  setInterval(() => loadWarnings(false), 60000)
})
</script>

<style scoped>
/* Ensure menubar end section stays right-aligned */
:deep(.p-menubar-end) {
  margin-left: auto;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
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
  gap: 0.75rem;
}

.user-name {
  color: var(--p-text-color);
  font-weight: 500;
  font-size: 0.95rem;
}

.user-avatar-button {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  background: var(--p-primary-color);
  color: white;
  font-weight: 600;
  font-size: 0.9rem;
  padding: 0;
  min-width: 2.5rem;
}

.user-avatar-button:hover {
  background: var(--p-primary-600);
}
</style>
