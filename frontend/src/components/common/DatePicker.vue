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

interface Props {
  modelValue?: Date | string | null
  dateFormat?: string
  showIcon?: boolean
  manualInput?: boolean
  disabled?: boolean
  placeholder?: string
  minDate?: Date
  maxDate?: Date
  selectionMode?: 'single' | 'multiple' | 'range'
  showButtonBar?: boolean
}

withDefaults(defineProps<Props>(), {
  dateFormat: 'yy-mm-dd',
  showIcon: false,
  manualInput: false,
  disabled: false,
  selectionMode: 'single',
  showButtonBar: false
})

interface Emits {
  (e: 'update:modelValue', value: Date | string | null): void
  (e: 'dateSelect', value: Date): void
}

defineEmits<Emits>()
</script>
