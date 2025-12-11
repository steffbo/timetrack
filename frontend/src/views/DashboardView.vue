<template>
  <div class="dashboard">
    <Card>
      <template #title>
        {{ t('dashboard.welcome', { name: currentUser?.firstName || currentUser?.email }) }}
      </template>
      <template #content>
        <p>{{ t('common.loading') }}</p>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Card from 'primevue/card'
import { useAuth } from '@/composables/useAuth'

const { t } = useI18n()
const { currentUser } = useAuth()
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
  margin: 0 auto;
}
</style>
