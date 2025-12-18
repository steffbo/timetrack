<template>
  <Dialog
    v-model:visible="isVisible"
    :header="t('dashboard.selectedDay.addTimeOffTitle')"
    :modal="true"
    :style="{ width: '90vw', maxWidth: '600px' }"
    :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
    @update:visible="handleVisibilityChange"
  >
    <div class="p-fluid">
      <div class="field">
        <label class="field-label">{{ t('timeOff.type.label') }} *</label>
        <div class="type-buttons">
          <button
            v-for="option in timeOffTypeOptions"
            :key="option.value"
            :class="['type-btn', { active: formData.timeOffType === option.value }]"
            @click="selectType(option.value)"
            type="button"
          >
            <i :class="option.icon"></i>
            <span>{{ option.label }}</span>
          </button>
        </div>
      </div>

      <div class="field">
        <label class="field-label">{{ t('timeOff.dateRange') }} *</label>
        <DatePicker
          v-model="dateRange"
          selection-mode="range"
          :manual-input="false"
          date-format="dd.mm.yy"
          show-button-bar
          :placeholder="t('timeOff.selectDateRange')"
        />
      </div>

      <div class="field">
        <label for="hoursPerDay" class="field-label">{{ t('timeOff.hoursPerDay') }}</label>
        <InputNumber
          id="hoursPerDay"
          v-model="formData.hoursPerDay"
          :min="0"
          :max="24"
          :max-fraction-digits="2"
          suffix=" h"
          :placeholder="t('timeOff.hoursPerDayPlaceholder')"
        />
        <small>{{ t('timeOff.hoursPerDayHint') }}</small>
      </div>

      <div class="field">
        <label for="notes" class="field-label">{{ t('timeOff.notes') }}</label>
        <Textarea
          id="notes"
          v-model="formData.notes"
          rows="3"
          :placeholder="t('timeOff.notesPlaceholder')"
        />
      </div>
    </div>

    <template #footer>
      <Button
        :label="t('cancel')"
        icon="pi pi-times"
        class="p-button-text"
        @click="handleCancel"
      />
      <Button
        :label="t('save')"
        icon="pi pi-check"
        @click="handleSave"
        :loading="saving"
      />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import DatePicker from 'primevue/datepicker'
import { TimeOffService } from '@/api/generated'
import { formatDateISO } from '@/utils/dateTimeUtils'

const { t } = useI18n()
const toast = useToast()

interface Props {
  visible: boolean
  selectedDate: string
}

const props = defineProps<Props>()

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}

const emit = defineEmits<Emits>()

const isVisible = ref(props.visible)
const saving = ref(false)
const dateRange = ref<Date[] | null>(null)

const timeOffTypeOptions = [
  { label: t('timeOff.type.VACATION'), value: 'VACATION', icon: 'pi pi-sun' },
  { label: t('timeOff.type.SICK'), value: 'SICK', icon: 'pi pi-heart-fill' },
  { label: t('timeOff.type.CHILD_SICK'), value: 'CHILD_SICK', icon: 'pi pi-users' },
  { label: t('timeOff.type.PERSONAL'), value: 'PERSONAL', icon: 'pi pi-home' }
]

const formData = ref({
  timeOffType: 'VACATION' as any,
  hoursPerDay: undefined as number | undefined,
  notes: ''
})

// Initialize form with selected date
const initializeForm = () => {
  if (!props.selectedDate) return

  const date = new Date(props.selectedDate)
  dateRange.value = [date, date]

  formData.value = {
    timeOffType: 'VACATION',
    hoursPerDay: undefined,
    notes: ''
  }
}

const selectType = (type: string) => {
  formData.value.timeOffType = type
}

// Watch for visibility changes
watch(() => props.visible, (newValue) => {
  isVisible.value = newValue
  if (newValue) {
    initializeForm()
  }
})

watch(() => props.selectedDate, () => {
  if (props.visible) {
    initializeForm()
  }
})

const handleVisibilityChange = (value: boolean) => {
  emit('update:visible', value)
}

const handleCancel = () => {
  emit('update:visible', false)
}

const handleSave = async () => {
  // Validate date range
  if (!dateRange.value || dateRange.value.length !== 2 || !dateRange.value[0] || !dateRange.value[1]) {
    toast.add({
      severity: 'warn',
      summary: t('warning'),
      detail: t('timeOff.selectDateRangeRequired'),
      life: 3000
    })
    return
  }

  saving.value = true
  try {
    await TimeOffService.createTimeOff({
      timeOffType: formData.value.timeOffType,
      startDate: formatDateISO(dateRange.value[0]),
      endDate: formatDateISO(dateRange.value[1]),
      hoursPerDay: formData.value.hoursPerDay,
      notes: formData.value.notes || undefined
    })

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.selectedDay.timeOffSaved'),
      life: 3000
    })

    emit('update:visible', false)
    emit('saved')
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error?.body?.message || t('dashboard.selectedDay.timeOffSaveError'),
      life: 5000
    })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.field {
  margin-bottom: 1.25rem;
}

.field-label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
  color: var(--p-text-color);
  font-size: 0.875rem;
}

.field small {
  display: block;
  margin-top: 0.5rem;
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.type-buttons {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.75rem;
}

@media (max-width: 640px) {
  .type-buttons {
    grid-template-columns: 1fr;
  }
}

.type-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  background: var(--p-surface-50);
  border: 2px solid var(--p-surface-border);
  border-radius: 0.5rem;
  color: var(--p-text-color);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.type-btn i {
  font-size: 1.5rem;
  color: var(--p-text-muted-color);
  transition: color 0.2s;
}

.type-btn:hover {
  background: var(--p-surface-100);
  border-color: var(--p-primary-color);
}

.type-btn:hover i {
  color: var(--p-primary-color);
}

.type-btn.active {
  background: var(--p-primary-color);
  border-color: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
  font-weight: 600;
}

.type-btn.active i {
  color: var(--p-primary-contrast-color);
}
</style>
