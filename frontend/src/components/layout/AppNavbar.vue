<template>
  <Menubar :model="menuItems">
    <template #end>
      <div class="navbar-end">
        <span class="user-email">{{ currentUser?.email }}</span>
        <Button
          :label="t('nav.logout')"
          icon="pi pi-sign-out"
          text
          @click="handleLogout"
        />
      </div>
    </template>
  </Menubar>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Menubar from 'primevue/menubar'
import Button from 'primevue/button'
import { useAuth } from '@/composables/useAuth'
import type { MenuItem } from 'primevue/menuitem'

const router = useRouter()
const { t } = useI18n()
const { currentUser, isAdmin, logout } = useAuth()

const menuItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [
    {
      label: t('nav.dashboard'),
      icon: 'pi pi-home',
      command: () => router.push('/dashboard')
    },
    {
      label: t('nav.profile'),
      icon: 'pi pi-user',
      command: () => router.push('/profile')
    },
    {
      label: t('nav.workingHours'),
      icon: 'pi pi-clock',
      command: () => router.push('/working-hours')
    }
  ]

  if (isAdmin.value) {
    items.push({
      label: t('nav.adminUsers'),
      icon: 'pi pi-users',
      command: () => router.push('/admin/users')
    })
  }

  return items
})

async function handleLogout() {
  await logout()
  router.push('/login')
}
</script>

<style scoped>
.navbar-end {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-email {
  font-size: 0.9rem;
  color: var(--p-text-color);
}
</style>
