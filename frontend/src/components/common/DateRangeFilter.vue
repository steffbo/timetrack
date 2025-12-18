<template>
  <div class="filter-row">
    <DatePicker
      id="startDateFilter"
      v-model="internalStartDate"
      show-icon
      :placeholder="t('timeEntries.startDate')"
    />
    <DatePicker
      id="endDateFilter"
      v-model="internalEndDate"
      show-icon
      :placeholder="t('timeEntries.endDate')"
    />
    <Button
      :label="t('filter')"
      icon="pi pi-filter"
      severity="success"
      @click="handleFilter"
    />
    <slot name="extra-actions"></slot>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import DatePicker from '@/components/common/DatePicker.vue'
import { isValidDateString } from '@/types/enums'

const { t } = useI18n()

const props = defineProps({
  startDate: {
    type: String,
    default: undefined,
    validator: (value: string | undefined) => {
      if (value === undefined) return true
      return isValidDateString(value)
    }
  },
  endDate: {
    type: String,
    default: undefined,
    validator: (value: string | undefined) => {
      if (value === undefined) return true
      return isValidDateString(value)
    }
  }
})

interface Emits {
  (e: 'update:startDate', value: string | undefined): void
  (e: 'update:endDate', value: string | undefined): void
  (e: 'filter'): void
}

const emit = defineEmits<Emits>()

const internalStartDate = ref(props.startDate)
const internalEndDate = ref(props.endDate)

watch(() => props.startDate, (newVal) => {
  internalStartDate.value = newVal
})

watch(() => props.endDate, (newVal) => {
  internalEndDate.value = newVal
})

const handleFilter = () => {
  emit('update:startDate', internalStartDate.value)
  emit('update:endDate', internalEndDate.value)
  emit('filter')
}
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.filter-row :deep(.p-datepicker) {
  flex: 0 0 auto;
  min-width: 180px;
}
</style>
