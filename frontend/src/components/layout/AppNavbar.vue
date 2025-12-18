<template>
  <Menubar :model="menuItems">
    <template #end>
      <div class="navbar-end">
        <!-- Warnings Icon -->
        <Button
          v-if="warningsCount > 0"
          :badge="warningsCount.toString()"
          badgeSeverity="warn"
          icon="pi pi-exclamation-triangle"
          severity="warning"
          text
          rounded
          @click="toggleWarnings"
          :aria-label="t('dashboard.warnings.title')"
          class="warnings-button"
        />
        <OverlayPanel ref="warningsPanel" :dismissable="true" :style="{ width: '500px' }">
          <WarningsCard :inline="true" />
        </OverlayPanel>

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
import OverlayPanel from 'primevue/overlaypanel'
import WarningsCard from '@/components/dashboard/WarningsCard.vue'
import { useAuth } from '@/composables/useAuth'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { RecurringOffDayWarningsService } from '@/api/generated'
import type { MenuItem } from 'primevue/menuitem'

const router = useRouter()
const { t } = useI18n()
const { currentUser, isAdmin, logout } = useAuth()
const { handleError } = useErrorHandler()
const userMenu = ref()
const warningsPanel = ref()
const warningsCount = ref(0)

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
      label: t('nav.dashboard'),
      icon: 'pi pi-home',
      command: () => router.push('/dashboard')
    },
    {
      label: t('nav.timeTracking'),
      icon: 'pi pi-calendar',
      items: [
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
    }
  ]

  return items
})

function toggleUserMenu(event: Event) {
  userMenu.value.toggle(event)
}

function toggleWarnings(event: Event) {
  warningsPanel.value.toggle(event)
}

async function loadWarningsCount() {
  try {
    const warnings = await RecurringOffDayWarningsService.getConflictWarnings()
    const unacknowledged = warnings.filter(w => !w.acknowledged)
    warningsCount.value = unacknowledged.length
  } catch (error) {
    handleError(error, 'Failed to load warnings count', { logError: true, severity: 'warn' })
  }
}

async function handleLogout() {
  await logout()
  router.push('/login')
}

onMounted(() => {
  loadWarningsCount()
  // Reload warnings count every minute
  setInterval(loadWarningsCount, 60000)
})
</script>

<style scoped>
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
