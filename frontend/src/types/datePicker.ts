/**
 * Shared type definitions for DatePicker components
 */

export interface BaseDatePickerProps {
  modelValue?: Date | string | null
  dateFormat?: string
  showIcon?: boolean
  manualInput?: boolean
  disabled?: boolean
  placeholder?: string
  minDate?: Date
  maxDate?: Date
  showButtonBar?: boolean
}

export interface DatePickerProps extends BaseDatePickerProps {
  selectionMode?: 'single' | 'multiple' | 'range'
}

export interface DateTimePickerProps extends BaseDatePickerProps {
  showTime?: boolean
  hourFormat?: '12' | '24'
  timeOnly?: boolean
}

export interface DatePickerEmits {
  (e: 'update:modelValue', value: Date | string | null): void
  (e: 'dateSelect', value: Date): void
}
