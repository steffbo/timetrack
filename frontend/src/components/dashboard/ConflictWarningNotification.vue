<template>
  <div v-if="unacknowledgedCount > 0" class="conflict-warning-notification">
    <Message severity="warn" :closable="false">
      <template #icon>
        <i class="pi pi-exclamation-triangle" style="font-size: 1.5rem"></i>
      </template>
      <div class="warning-content">
        <strong>{{ t('dashboard.warnings.unacknowledgedWarnings', { count: unacknowledgedCount }) }}</strong>
        <p v-for="warning in unacknowledgedWarnings" :key="warning.id" class="warning-item">
          {{ t('dashboard.warnings.conflictMessage', {
            date: formatDate(warning.conflictDate),
            description: warning.recurringOffDayDescription || t('dashboard.calendar.recurringOffDay')
          }) }}
        </p>
        <div class="warning-actions">
          <Button
            v-for="warning in unacknowledgedWarnings"
            :key="warning.id"
            :label="t('dashboard.warnings.acknowledge')"
            size="small"
            @click="handleAcknowledge(warning.id)"
            :loading="acknowledgingIds.has(warning.id)"
          />
        </div>
      </div>
    </Message>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Message from 'primevue/message'
import Button from 'primevue/button'
import { useConflictWarnings } from '@/composables/useConflictWarnings'

const { t } = useI18n()
const toast = useToast()

const {
  unacknowledgedWarnings,
  unacknowledgedCount,
  loadWarnings,
  acknowledgeWarning
} = useConflictWarnings()

const acknowledgingIds = ref<Set<number>>(new Set())

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('de-DE', {
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
      detail: t('dashboard.warnings.acknowledge'),
      life: 3000
    })
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.message || 'Error acknowledging warning',
      life: 3000
    })
  } finally {
    acknowledgingIds.value.delete(warningId)
  }
}

onMounted(async () => {
  await loadWarnings(true) // Load only unacknowledged warnings
})
</script>

<style scoped>
.conflict-warning-notification {
  margin-bottom: var(--tt-spacing-lg);
}

.warning-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

.warning-item {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.5;
}

.warning-actions {
  display: flex;
  gap: var(--tt-spacing-sm);
  margin-top: var(--tt-spacing-sm);
  flex-wrap: wrap;
}
</style>
