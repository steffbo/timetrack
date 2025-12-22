<template>
  <DateRangeFilter
    :start-date="startDate"
    :end-date="endDate"
    @update:start-date="emit('update:startDate', $event)"
    @update:end-date="emit('update:endDate', $event)"
    @filter="emit('filter')"
  >
    <template #extra-actions>
      <Button
        :label="showTimeOff ? t('timeEntries.hideTimeOff') : t('timeEntries.showTimeOff')"
        :icon="showTimeOff ? 'pi pi-eye-slash' : 'pi pi-eye'"
        severity="secondary"
        outlined
        @click="emit('toggle-time-off')"
      />
    </template>
  </DateRangeFilter>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import DateRangeFilter from '@/components/common/DateRangeFilter.vue'

interface Props {
  startDate?: string
  endDate?: string
  showTimeOff: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:startDate', value: string | undefined): void
  (e: 'update:endDate', value: string | undefined): void
  (e: 'filter'): void
  (e: 'toggle-time-off'): void
}>()

const { t } = useI18n()
</script>
