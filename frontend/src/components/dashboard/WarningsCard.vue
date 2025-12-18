<template>
  <div v-if="hasWarnings || loading" class="warnings-card" :class="{ inline }">
    <Card v-if="!inline">
      <template #header>
        <div class="card-header">
          <i class="pi pi-exclamation-triangle warning-icon"></i>
          <h3>{{ t('dashboard.warnings.title') }}</h3>
        </div>
      </template>
      <template #content>
        <div v-if="loading" class="loading-state">
          <i class="pi pi-spin pi-spinner"></i>
          <span>{{ t('common.loading') }}</span>
        </div>

        <div v-else-if="unacknowledgedWarnings.length > 0" class="warnings-list">
          <div v-for="warning in unacknowledgedWarnings" :key="warning.id" class="warning-item">
            <div class="warning-icon-wrapper">
              <i class="pi pi-calendar warning-type-icon"></i>
            </div>
            <div class="warning-details">
              <strong class="warning-title">{{ t('dashboard.warnings.conflictTitle') }}</strong>
              <p class="warning-message">
                {{ t('dashboard.warnings.conflictMessage', {
                  date: formatDate(warning.conflictDate),
                  description: warning.recurringOffDayDescription || t('dashboard.calendar.recurringOffDay')
                }) }}
              </p>
              <div class="warning-actions">
                <Button
                  :label="t('dashboard.warnings.acknowledge')"
                  size="small"
                  severity="secondary"
                  outlined
                  @click="handleAcknowledge(warning.id)"
                  :loading="acknowledgingIds.has(warning.id)"
                />
              </div>
            </div>
          </div>
        </div>

        <div v-else class="no-warnings">
          <i class="pi pi-check-circle success-icon"></i>
          <span>{{ t('dashboard.warnings.noWarnings') }}</span>
        </div>
      </template>
    </Card>

    <!-- Inline version (for navbar) -->
    <div v-else class="inline-content">
      <div v-if="loading" class="loading-state">
        <i class="pi pi-spin pi-spinner"></i>
        <span>{{ t('common.loading') }}</span>
      </div>

      <div v-else-if="unacknowledgedWarnings.length > 0" class="warnings-list">
        <div v-for="warning in unacknowledgedWarnings" :key="warning.id" class="warning-item">
          <div class="warning-icon-wrapper">
            <i class="pi pi-calendar warning-type-icon"></i>
          </div>
          <div class="warning-details">
            <strong class="warning-title">{{ t('dashboard.warnings.conflictTitle') }}</strong>
            <p class="warning-message">
              {{ t('dashboard.warnings.conflictMessage', {
                date: formatDate(warning.conflictDate),
                description: warning.recurringOffDayDescription || t('dashboard.calendar.recurringOffDay')
              }) }}
            </p>
            <div class="warning-actions">
              <Button
                :label="t('dashboard.warnings.acknowledge')"
                size="small"
                severity="secondary"
                outlined
                @click="handleAcknowledge(warning.id)"
                :loading="acknowledgingIds.has(warning.id)"
              />
            </div>
          </div>
        </div>
      </div>

      <div v-else class="no-warnings">
        <i class="pi pi-check-circle success-icon"></i>
        <span>{{ t('dashboard.warnings.noWarnings') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  inline?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  inline: false
})

import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import Button from 'primevue/button'
import { useConflictWarnings } from '@/composables/useConflictWarnings'

const { t } = useI18n()
const toast = useToast()

const {
  warnings,
  unacknowledgedWarnings,
  loading,
  loadWarnings,
  acknowledgeWarning
} = useConflictWarnings()

const acknowledgingIds = ref<Set<number>>(new Set())

const hasWarnings = computed(() => warnings.value.length > 0)

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('de-DE', {
    weekday: 'short',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

const handleAcknowledge = async (warningId: number) => {
  acknowledgingIds.value.add(warningId)
  try {
    await acknowledgeWarning(warningId)
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.warnings.acknowledged'),
      life: 3000
    })
  } catch (error: any) {
    console.error('Error acknowledging warning:', error)
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.message || t('dashboard.warnings.acknowledgeError'),
      life: 3000
    })
  } finally {
    acknowledgingIds.value.delete(warningId)
  }
}

onMounted(async () => {
  console.log('[WarningsCard] Component mounted, loading warnings...')
  await loadWarnings()
  console.log('[WarningsCard] Warnings loaded:', warnings.value)
  console.log('[WarningsCard] Unacknowledged count:', unacknowledgedWarnings.value.length)
})
</script>

<style scoped>
.warnings-card {
  margin-bottom: var(--tt-spacing-lg);
}

.warnings-card :deep(.p-card) {
  box-shadow: none;
}

.warnings-card :deep(.p-card-header) {
  padding: 0;
}

.warnings-card :deep(.p-card-content) {
  padding: 0;
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-sm);
  padding: 1rem;
}

.card-header h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
}

.warning-icon {
  font-size: 1.5rem;
  color: var(--p-orange-500);
}

.loading-state {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-sm);
  padding: 1rem;
  color: var(--p-text-muted-color);
}

.warnings-list {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-md);
}

.warning-item {
  display: flex;
  gap: var(--tt-spacing-md);
  padding: 1rem;
  background: var(--p-orange-50);
  border-left: 4px solid var(--p-orange-500);
  border-radius: var(--tt-radius-sm);
}

.warning-icon-wrapper {
  flex-shrink: 0;
}

.warning-type-icon {
  font-size: 1.5rem;
  color: var(--p-orange-600);
}

.warning-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

.warning-title {
  font-size: 1rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.warning-message {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.5;
  color: #4b5563;
}

.warning-actions {
  display: flex;
  gap: var(--tt-spacing-sm);
  margin-top: var(--tt-spacing-xs);
}

.no-warnings {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-sm);
  padding: 1rem;
  color: var(--p-green-600);
}

.success-icon {
  font-size: 1.25rem;
}

@media (max-width: 768px) {
  .warning-item {
    flex-direction: column;
    gap: var(--tt-spacing-sm);
  }

  .card-header h3 {
    font-size: 1.1rem;
  }
}
</style>
