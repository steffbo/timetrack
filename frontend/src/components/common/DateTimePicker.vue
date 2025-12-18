<template>
  <PrimeDatePicker
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :show-time="showTime"
    :hour-format="hourFormat"
    :date-format="dateFormat"
    :show-icon="showIcon"
    :manual-input="manualInput"
    :disabled="disabled"
    :placeholder="placeholder"
    :min-date="minDate"
    :max-date="maxDate"
    :time-only="timeOnly"
    :show-button-bar="showButtonBar"
    @date-select="$emit('dateSelect', $event)"
  />
</template>

<script setup lang="ts">
import PrimeDatePicker from 'primevue/datepicker'
import type { DateTimePickerProps, DatePickerEmits } from '@/types/datePicker'

withDefaults(defineProps<DateTimePickerProps>(), {
  showTime: true,
  hourFormat: '24',
  dateFormat: 'yy-mm-dd',
  showIcon: false,
  manualInput: true,
  disabled: false,
  timeOnly: false,
  showButtonBar: false
})

defineEmits<DatePickerEmits>()
</script>
