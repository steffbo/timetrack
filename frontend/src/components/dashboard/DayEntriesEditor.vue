<template>
  <Dialog
    v-model:visible="isVisible"
    :header="t('dashboard.selectedDay.editEntriesTitle')"
    :modal="true"
    :style="{ width: '90vw', maxWidth: '700px' }"
    :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
    @update:visible="handleVisibilityChange"
  >
    <div v-if="entries.length === 0 && timeOffEntries.length === 0" class="no-entries">
      <i class="pi pi-info-circle"></i>
      <p>{{ t('dashboard.selectedDay.noEntriesToEdit') }}</p>
    </div>

    <div v-else class="entries-list">
      <!-- Time Off Entries -->
      <div v-for="entry in timeOffEntries" :key="'timeoff-' + entry.id" class="entry-item time-off-item">
        <div class="entry-info">
          <div class="entry-header">
            <Tag :severity="getTimeOffTypeSeverity(entry.timeOffType)">
              {{ t(`timeOff.type.${entry.timeOffType}`) }}
            </Tag>
          </div>
          <div class="entry-dates">
            <i class="pi pi-calendar"></i>
            <span>{{ formatDate(entry.startDate) }} - {{ formatDate(entry.endDate) }}</span>
            <span v-if="entry.days" class="days-badge">{{ entry.days }} {{ t('timeOff.days') }}</span>
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
            @click="handleEditTimeOff(entry)"
            :aria-label="t('edit')"
          />
          <Button
            icon="pi pi-trash"
            text
            rounded
            severity="danger"
            @click="handleDeleteTimeOff(entry)"
            :aria-label="t('delete')"
          />
        </div>
      </div>

      <!-- Work Entries -->
      <div v-for="entry in entries" :key="entry.id" class="entry-item">
        <div class="entry-info">
          <div class="entry-header">
            <Tag severity="secondary">
              {{ t('timeEntries.type.WORK') }}
            </Tag>
          </div>
          <div class="entry-time">
            <i class="pi pi-clock"></i>
            <span>{{ formatTime(entry.clockIn) }} - {{ entry.clockOut ? formatTime(entry.clockOut) : t('dashboard.selectedDay.active') }}</span>
          </div>
          <div class="entry-details">
            <span v-if="entry.breakMinutes > 0" class="detail-item">
              <i class="pi pi-pause"></i>
              {{ entry.breakMinutes }} min
            </span>
            <span class="detail-item">
              <i class="pi pi-clock"></i>
              {{ calculateDuration(entry.clockIn, entry.clockOut, entry.breakMinutes) }}
            </span>
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
      <span>{{ timeOffToDelete ? t('dashboard.selectedDay.deleteTimeOffConfirm') : t('dashboard.selectedDay.deleteEntryConfirm') }}</span>
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
        @click="timeOffToDelete ? confirmDeleteTimeOff() : confirmDelete()"
        :loading="deleting"
      />
    </template>
  </Dialog>

  <!-- Edit Entry Dialog -->
  <Dialog
    v-model:visible="editDialogVisible"
    :header="t('dashboard.selectedDay.editEntryTitle')"
    :modal="true"
    :style="{ width: '90vw', maxWidth: '500px' }"
    :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
  >
    <div v-if="editingEntry" class="p-fluid">
      <div class="field">
        <label for="editClockIn">{{ t('timeEntries.clockIn') }} *</label>
        <DatePicker
          id="editClockIn"
          v-model="editForm.clockIn"
          timeOnly
          hourFormat="24"
        />
      </div>

      <div class="field">
        <label for="editClockOut">{{ t('timeEntries.clockOut') }} *</label>
        <DatePicker
          id="editClockOut"
          v-model="editForm.clockOut"
          timeOnly
          hourFormat="24"
        />
      </div>

      <div class="field">
        <label for="editBreak">{{ t('timeEntries.breakMinutes') }}</label>
        <InputNumber
          id="editBreak"
          v-model="editForm.breakMinutes"
          :min="0"
          :max="480"
          suffix=" min"
        />
      </div>

      <div class="field">
        <label for="editNotes">{{ t('timeEntries.notes') }}</label>
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
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import { TimeEntriesService, TimeOffService } from '@/api/generated'
import type { TimeEntryResponse, TimeOffResponse } from '@/api/generated'
import { formatTime, formatDate, calculateDuration } from '@/utils/dateTimeUtils'

const { t } = useI18n()
const toast = useToast()

interface Props {
  visible: boolean
  selectedDate?: string
  entries: TimeEntryResponse[]
  timeOffEntries?: TimeOffResponse[]
}

const props = withDefaults(defineProps<Props>(), {
  timeOffEntries: () => []
})

// Validate selectedDate format if provided
if (props.selectedDate && !/^\d{4}-\d{2}-\d{2}$/.test(props.selectedDate)) {
  console.warn(`Invalid date format for selectedDate: ${props.selectedDate}. Expected YYYY-MM-DD format.`)
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}

const emit = defineEmits<Emits>()

const isVisible = ref(props.visible)
const deleteDialogVisible = ref(false)
const editDialogVisible = ref(false)
const entryToDelete = ref<TimeEntryResponse | null>(null)
const editingEntry = ref<TimeEntryResponse | null>(null)
const deleting = ref(false)
const saving = ref(false)

// Time-off related state
const timeOffToDelete = ref<TimeOffResponse | null>(null)
const editingTimeOff = ref<TimeOffResponse | null>(null)

const editForm = ref({
  clockIn: new Date(),
  clockOut: new Date(),
  breakMinutes: 0,
  notes: ''
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

const handleEdit = (entry: TimeEntryResponse) => {
  editingEntry.value = entry
  editForm.value = {
    clockIn: new Date(entry.clockIn),
    clockOut: entry.clockOut ? new Date(entry.clockOut) : new Date(),
    breakMinutes: entry.breakMinutes || 0,
    notes: entry.notes || ''
  }
  editDialogVisible.value = true
}

const handleDelete = (entry: TimeEntryResponse) => {
  entryToDelete.value = entry
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (!entryToDelete.value) return

  deleting.value = true
  try {
    await TimeEntriesService.deleteTimeEntry(entryToDelete.value.id)

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.selectedDay.entryDeleted'),
      life: 3000
    })

    deleteDialogVisible.value = false
    entryToDelete.value = null
    emit('saved')
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
    await TimeEntriesService.updateTimeEntry(editingEntry.value.id, {
      clockIn: editForm.value.clockIn.toISOString(),
      clockOut: editForm.value.clockOut.toISOString(),
      breakMinutes: editForm.value.breakMinutes,
      entryType: editingEntry.value.entryType,
      notes: editForm.value.notes || ''
    })

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.selectedDay.entrySaved'),
      life: 3000
    })

    editDialogVisible.value = false
    editingEntry.value = null
    emit('saved')
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

// Time-off helper methods
const getTimeOffTypeSeverity = (type: string): string => {
  const severityMap: Record<string, string> = {
    'VACATION': 'success',
    'SICK': 'danger',
    'CHILD_SICK': 'warn',
    'PERSONAL': 'info',
    'PUBLIC_HOLIDAY': 'secondary'
  }
  return severityMap[type] || 'secondary'
}

const handleEditTimeOff = (entry: TimeOffResponse) => {
  // For now, just show a toast - full edit dialog can be added later
  toast.add({
    severity: 'info',
    summary: t('info'),
    detail: 'Time off editing coming soon',
    life: 3000
  })
}

const handleDeleteTimeOff = (entry: TimeOffResponse) => {
  timeOffToDelete.value = entry
  deleteDialogVisible.value = true
}

const confirmDeleteTimeOff = async () => {
  if (!timeOffToDelete.value) return

  deleting.value = true
  try {
    await TimeOffService.deleteTimeOff(timeOffToDelete.value.id)

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.selectedDay.timeOffDeleted'),
      life: 3000
    })

    deleteDialogVisible.value = false
    timeOffToDelete.value = null
    emit('saved')
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
  gap: 0.5rem;
}

.entry-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0.75rem;
  border: 1px solid var(--p-surface-border);
  border-radius: var(--tt-radius-sm);
  background: var(--p-surface-0);
}

.entry-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.entry-time {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  font-size: 1rem;
}

.entry-time i {
  color: var(--p-primary-color);
}

.entry-details {
  display: flex;
  gap: 1rem;
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
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

/* Field styles are now in shared forms.css */

/* Time-off specific styles */
.entry-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.entry-dates {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
}

.entry-dates i {
  color: var(--p-text-muted-color);
}

.days-badge {
  padding: 0.125rem 0.5rem;
  background: var(--p-surface-100);
  border-radius: var(--tt-radius-sm);
  font-weight: 600;
  font-size: 0.75rem;
}
</style>
