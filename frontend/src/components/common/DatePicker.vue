<template>
  <Calendar
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :date-format="dateFormat"
    :show-icon="showIcon"
    :manual-input="manualInput"
    :disabled="disabled"
    :placeholder="placeholder"
    :min-date="minDate"
    :max-date="maxDate"
    :selection-mode="selectionMode"
    :show-button-bar="showButtonBar"
    @date-select="$emit('dateSelect', $event)"
  />
</template>

<script setup lang="ts">
import Calendar from 'primevue/calendar'
import type { DatePickerProps, DatePickerEmits } from '@/types/datePicker'

withDefaults(defineProps<DatePickerProps>(), {
  dateFormat: 'yy-mm-dd',
  showIcon: false,
  manualInput: false,
  disabled: false,
  selectionMode: 'single',
  showButtonBar: false
})

defineEmits<DatePickerEmits>()
</script>
