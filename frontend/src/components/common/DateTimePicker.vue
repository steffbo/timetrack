<template>
  <Calendar
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
import Calendar from 'primevue/calendar'

interface Props {
  modelValue?: Date | string | null
  showTime?: boolean
  hourFormat?: '12' | '24'
  dateFormat?: string
  showIcon?: boolean
  manualInput?: boolean
  disabled?: boolean
  placeholder?: string
  minDate?: Date
  maxDate?: Date
  timeOnly?: boolean
  showButtonBar?: boolean
}

withDefaults(defineProps<Props>(), {
  showTime: true,
  hourFormat: '24',
  dateFormat: 'yy-mm-dd',
  showIcon: false,
  manualInput: true,
  disabled: false,
  timeOnly: false,
  showButtonBar: false
})

interface Emits {
  (e: 'update:modelValue', value: Date | string | null): void
  (e: 'dateSelect', value: Date): void
}

defineEmits<Emits>()
</script>
