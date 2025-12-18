<template>
  <Dialog
    v-model:visible="isVisible"
    :header="t('dashboard.selectedDay.editTimeOffTitle')"
    :modal="true"
    :style="{ width: '90vw', maxWidth: '700px' }"
    :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
    @update:visible="handleVisibilityChange"
  >
    <div v-if="timeOffEntries.length === 0" class="no-entries">
      <i class="pi pi-info-circle"></i>
      <p>{{ t('dashboard.selectedDay.noTimeOffToEdit') }}</p>
    </div>

    <div v-else class="entries-list">
      <div v-for="entry in filteredTimeOffEntries" :key="entry.id" class="entry-item">
        <div class="entry-info">
          <div class="entry-header">
            <Tag :severity="getTypeSeverity(entry.timeOffType)">
              {{ getTypeLabel(entry.timeOffType) }}
            </Tag>
          </div>
          <div class="entry-dates">
            <i class="pi pi-calendar"></i>
            <span>{{ formatDate(entry.startDate) }} - {{ formatDate(entry.endDate) }}</span>
            <span class="days-badge">{{ entry.days }} {{ t('timeOff.days') }}</span>
          </div>
          <div v-if="entry.notes" class="entry-notes">
            <i class="pi pi-comment"></i>
            {{ entry.notes }}
          </div>
        </div>
        <div class="entry-actions">
          <Button
            icon="pi pi-pencil"
            text
            rounded
            severity="info"
            @click="handleEdit(entry)"
            :aria-label="t('edit')"
          />
          <Button
            icon="pi pi-trash"
            text
            rounded
            severity="danger"
            @click="handleDelete(entry)"
            :aria-label="t('delete')"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <Button
        :label="t('close')"
        icon="pi pi-times"
        @click="handleClose"
      />
    </template>
  </Dialog>

  <!-- Delete Confirmation Dialog -->
  <Dialog
    v-model:visible="deleteDialogVisible"
    :header="t('confirm')"
    :modal="true"
    :style="{ width: '90vw', maxWidth: '450px' }"
    :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
  >
    <div class="flex align-items-center">
      <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
      <span>{{ t('dashboard.selectedDay.deleteTimeOffConfirm') }}</span>
    </div>
    <template #footer>
      <Button
        :label="t('no')"
        icon="pi pi-times"
        class="p-button-text"
        @click="deleteDialogVisible = false"
      />
      <Button
        :label="t('yes')"
        icon="pi pi-check"
        class="p-button-danger"
        @click="confirmDelete"
        :loading="deleting"
      />
    </template>
  </Dialog>

  <!-- Edit Time Off Dialog -->
  <Dialog
    v-model:visible="editDialogVisible"
    :header="t('dashboard.selectedDay.editTimeOffEntryTitle')"
    :modal="true"
    :style="{ width: '90vw', maxWidth: '500px' }"
    :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
  >
    <div v-if="editingEntry" class="p-fluid">
      <div class="field">
        <label for="editType">{{ t('timeOff.type.label') }} *</label>
        <Select
          id="editType"
          v-model="editForm.timeOffType"
          :options="timeOffTypeOptions"
          option-label="label"
          option-value="value"
        />
      </div>

      <div class="field">
        <label for="editStartDate">{{ t('timeOff.startDate') }} *</label>
        <DatePicker
          id="editStartDate"
          v-model="editForm.startDate"
        />
      </div>

      <div class="field">
        <label for="editEndDate">{{ t('timeOff.endDate') }} *</label>
        <DatePicker
          id="editEndDate"
          v-model="editForm.endDate"
        />
      </div>

      <div class="field">
        <label for="editHoursPerDay">{{ t('timeOff.hoursPerDay') }}</label>
        <InputNumber
          id="editHoursPerDay"
          v-model="editForm.hoursPerDay"
          :min="0"
          :max="24"
          :max-fraction-digits="2"
          suffix=" h"
        />
        <small>{{ t('timeOff.hoursPerDayHint') }}</small>
      </div>

      <div class="field">
        <label for="editNotes">{{ t('timeOff.notes') }}</label>
        <Textarea
          id="editNotes"
          v-model="editForm.notes"
          rows="3"
        />
      </div>
    </div>

    <template #footer>
      <Button
        :label="t('cancel')"
        icon="pi pi-times"
        class="p-button-text"
        @click="editDialogVisible = false"
      />
      <Button
        :label="t('save')"
        icon="pi pi-check"
        @click="saveEdit"
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
import Tag from 'primevue/tag'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import DatePicker from '@/components/common/DatePicker.vue'
import { TimeOffService } from '@/api/generated'
import type { TimeOffResponse } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()

interface Props {
  visible: boolean
  timeOffEntries: TimeOffResponse[]
}

const props = defineProps<Props>()

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'changed'): void
}

const emit = defineEmits<Emits>()

const isVisible = ref(props.visible)
const deleteDialogVisible = ref(false)
const editDialogVisible = ref(false)
const entryToDelete = ref<TimeOffResponse | null>(null)
const editingEntry = ref<TimeOffResponse | null>(null)
const deleting = ref(false)
const saving = ref(false)

const timeOffTypeOptions = [
  { label: t('timeOff.type.VACATION'), value: 'VACATION' },
  { label: t('timeOff.type.SICK'), value: 'SICK' },
  { label: t('timeOff.type.CHILD_SICK'), value: 'CHILD_SICK' },
  { label: t('timeOff.type.PERSONAL'), value: 'PERSONAL' }
]

const editForm = ref({
  timeOffType: 'VACATION' as any,
  startDate: new Date(),
  endDate: new Date(),
  hoursPerDay: undefined as number | undefined,
  notes: ''
})

// Filter out public holidays (not editable)
const filteredTimeOffEntries = computed(() => {
  return props.timeOffEntries.filter(entry => entry.timeOffType !== 'PUBLIC_HOLIDAY')
})

watch(() => props.visible, (newValue) => {
  isVisible.value = newValue
})

const handleVisibilityChange = (value: boolean) => {
  emit('update:visible', value)
}

const handleClose = () => {
  emit('update:visible', false)
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

const getTypeLabel = (type: string) => {
  const option = timeOffTypeOptions.find(t => t.value === type)
  return option ? option.label : type
}

const getTypeSeverity = (type: string) => {
  switch (type) {
    case 'VACATION': return 'info'
    case 'SICK': return 'danger'
    case 'CHILD_SICK': return 'danger'
    case 'PERSONAL': return 'warning'
    default: return 'secondary'
  }
}

const handleEdit = (entry: TimeOffResponse) => {
  editingEntry.value = entry
  editForm.value = {
    timeOffType: entry.timeOffType,
    startDate: new Date(entry.startDate),
    endDate: new Date(entry.endDate),
    hoursPerDay: entry.hoursPerDay,
    notes: entry.notes || ''
  }
  editDialogVisible.value = true
}

const handleDelete = (entry: TimeOffResponse) => {
  entryToDelete.value = entry
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (!entryToDelete.value) return

  deleting.value = true
  try {
    await TimeOffService.deleteTimeOff(entryToDelete.value.id)

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.selectedDay.timeOffDeleted'),
      life: 3000
    })

    deleteDialogVisible.value = false
    entryToDelete.value = null
    emit('changed')
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error?.body?.message || t('dashboard.selectedDay.deleteError'),
      life: 5000
    })
  } finally {
    deleting.value = false
  }
}

const saveEdit = async () => {
  if (!editingEntry.value) return

  saving.value = true
  try {
    // Format dates as ISO date strings
    const formatDate = (date: any) => {
      if (!date) return undefined
      if (typeof date === 'string') return date
      if (date instanceof Date) {
        const year = date.getFullYear()
        const month = String(date.getMonth() + 1).padStart(2, '0')
        const day = String(date.getDate()).padStart(2, '0')
        return `${year}-${month}-${day}`
      }
      return date
    }

    await TimeOffService.updateTimeOff(editingEntry.value.id, {
      timeOffType: editForm.value.timeOffType,
      startDate: formatDate(editForm.value.startDate)!,
      endDate: formatDate(editForm.value.endDate)!,
      hoursPerDay: editForm.value.hoursPerDay,
      notes: editForm.value.notes || undefined
    })

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.selectedDay.timeOffSaved'),
      life: 3000
    })

    editDialogVisible.value = false
    editingEntry.value = null
    emit('changed')
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error?.body?.message || t('dashboard.selectedDay.saveError'),
      life: 5000
    })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.no-entries {
  text-align: center;
  padding: 2rem;
  color: var(--p-text-muted-color);
}

.no-entries i {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.entries-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.entry-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 1rem;
  border: 1px solid var(--p-surface-border);
  border-radius: var(--tt-radius-sm);
  background: var(--p-surface-0);
}

.entry-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.entry-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.entry-dates {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9375rem;
}

.entry-dates i {
  color: var(--p-primary-color);
}

.days-badge {
  padding: 0.125rem 0.5rem;
  background: var(--p-primary-100);
  color: var(--p-primary-700);
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
}

.entry-notes {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--p-text-color);
  margin-top: 0.25rem;
}

.entry-notes i {
  margin-top: 0.125rem;
  color: var(--p-text-muted-color);
}

.entry-actions {
  display: flex;
  gap: 0.25rem;
}

.field {
  margin-bottom: 1rem;
}

.field label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
  color: var(--p-text-color);
}

.field small {
  display: block;
  margin-top: 0.25rem;
  color: #6c757d;
}
</style>
