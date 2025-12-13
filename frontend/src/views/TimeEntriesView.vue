<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Calendar from 'primevue/calendar'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import Textarea from 'primevue/textarea'
import Tag from 'primevue/tag'
import InputNumber from 'primevue/inputnumber'
import { TimeEntriesService, OpenAPI } from '@/api/generated'
import type { TimeEntryResponse, ClockInRequest, ClockOutRequest, UpdateTimeEntryRequest, CreateTimeEntryRequest, DailySummaryResponse } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()

const timeEntries = ref<TimeEntryResponse[]>([])
const dailySummaries = ref<DailySummaryResponse[]>([])
const loading = ref(false)
const clockInDialogVisible = ref(false)
const clockOutDialogVisible = ref(false)
const manualEntryDialogVisible = ref(false)
const editDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const clockInNotes = ref('')
const clockOutNotes = ref('')
const activeEntry = ref<TimeEntryResponse | null>(null)
const currentTimeEntry = ref<Partial<UpdateTimeEntryRequest>>({})
const newManualEntry = ref<Partial<CreateTimeEntryRequest>>({
  entryType: 'WORK' as any,
  breakMinutes: 0
})
const timeEntryToDelete = ref<TimeEntryResponse | null>(null)

// Date range filter - default to current month
const now = new Date()
const startDateFilter = ref<string>(new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0])
const endDateFilter = ref<string>(new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().split('T')[0])

const viewMode = ref<'entries' | 'summary'>('entries')

const entryTypeOptions = [
  { label: t('timeEntries.type.WORK'), value: 'WORK' },
  { label: t('timeEntries.type.SICK'), value: 'SICK' },
  { label: t('timeEntries.type.PTO'), value: 'PTO' },
  { label: t('timeEntries.type.EVENT'), value: 'EVENT' }
]

const loadTimeEntries = async () => {
  loading.value = true
  try {
    const response = await TimeEntriesService.getTimeEntries(startDateFilter.value, endDateFilter.value)
    timeEntries.value = response

    // Check if there's an active entry
    activeEntry.value = response.find(entry => entry.isActive) || null
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('timeEntries.loadError'),
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const loadDailySummary = async () => {
  loading.value = true
  try {
    const response = await TimeEntriesService.getDailySummary(startDateFilter.value, endDateFilter.value)
    dailySummaries.value = response
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('timeEntries.summaryLoadError'),
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const openClockInDialog = () => {
  clockInNotes.value = ''
  clockInDialogVisible.value = true
}

const clockIn = async () => {
  try {
    const request: ClockInRequest = {
      notes: clockInNotes.value || undefined
    }
    const response = await TimeEntriesService.clockIn(request)
    activeEntry.value = response

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('timeEntries.clockInSuccess'),
      life: 3000
    })

    clockInDialogVisible.value = false
    await loadTimeEntries()
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('timeEntries.clockInError'),
      life: 3000
    })
  }
}

const openClockOutDialog = () => {
  clockOutNotes.value = activeEntry.value?.notes || ''
  clockOutDialogVisible.value = true
}

const clockOut = async () => {
  try {
    const request: ClockOutRequest = {
      notes: clockOutNotes.value || undefined
    }
    await TimeEntriesService.clockOut(request)
    activeEntry.value = null

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('timeEntries.clockOutSuccess'),
      life: 3000
    })

    clockOutDialogVisible.value = false
    await loadTimeEntries()
    if (viewMode.value === 'summary') {
      await loadDailySummary()
    }
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('timeEntries.clockOutError'),
      life: 3000
    })
  }
}

const openManualEntryDialog = () => {
  newManualEntry.value = {
    clockIn: new Date() as any,
    clockOut: new Date() as any,
    entryType: 'WORK' as any,
    breakMinutes: 0,
    notes: ''
  }
  manualEntryDialogVisible.value = true
}

const createManualEntry = async () => {
  try {
    const request: CreateTimeEntryRequest = {
      clockIn: newManualEntry.value.clockIn instanceof Date
        ? newManualEntry.value.clockIn.toISOString()
        : newManualEntry.value.clockIn as string,
      clockOut: newManualEntry.value.clockOut instanceof Date
        ? newManualEntry.value.clockOut.toISOString()
        : newManualEntry.value.clockOut as string,
      breakMinutes: newManualEntry.value.breakMinutes || 0,
      entryType: newManualEntry.value.entryType!,
      notes: newManualEntry.value.notes
    }

    await TimeEntriesService.createTimeEntry(request)

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('timeEntries.createSuccess'),
      life: 3000
    })

    manualEntryDialogVisible.value = false
    await loadTimeEntries()
    if (viewMode.value === 'summary') {
      await loadDailySummary()
    }
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('timeEntries.createError'),
      life: 3000
    })
  }
}

const openEditDialog = (entry: TimeEntryResponse) => {
  currentTimeEntry.value = {
    clockIn: new Date(entry.clockIn) as any,
    clockOut: entry.clockOut ? new Date(entry.clockOut) as any : undefined,
    breakMinutes: entry.breakMinutes || 0,
    entryType: entry.entryType,
    notes: entry.notes
  }
  timeEntryToDelete.value = entry
  editDialogVisible.value = true
}

const saveTimeEntry = async () => {
  try {
    if (timeEntryToDelete.value?.id) {
      // Convert Date objects back to ISO strings
      const request: UpdateTimeEntryRequest = {
        clockIn: currentTimeEntry.value.clockIn instanceof Date
          ? currentTimeEntry.value.clockIn.toISOString()
          : currentTimeEntry.value.clockIn as string,
        clockOut: currentTimeEntry.value.clockOut instanceof Date
          ? currentTimeEntry.value.clockOut.toISOString()
          : currentTimeEntry.value.clockOut as string | undefined,
        breakMinutes: currentTimeEntry.value.breakMinutes || 0,
        entryType: currentTimeEntry.value.entryType!,
        notes: currentTimeEntry.value.notes
      }

      await TimeEntriesService.updateTimeEntry(
        timeEntryToDelete.value.id,
        request
      )

      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('timeEntries.updateSuccess'),
        life: 3000
      })

      editDialogVisible.value = false
      await loadTimeEntries()
      if (viewMode.value === 'summary') {
        await loadDailySummary()
      }
    }
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('timeEntries.updateError'),
      life: 3000
    })
  }
}

const openDeleteDialog = (entry: TimeEntryResponse) => {
  timeEntryToDelete.value = entry
  deleteDialogVisible.value = true
}

const deleteTimeEntry = async () => {
  try {
    if (timeEntryToDelete.value?.id) {
      await TimeEntriesService.deleteTimeEntry(timeEntryToDelete.value.id)

      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('timeEntries.deleteSuccess'),
        life: 3000
      })

      deleteDialogVisible.value = false
      await loadTimeEntries()
      if (viewMode.value === 'summary') {
        await loadDailySummary()
      }
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('timeEntries.deleteError'),
      life: 3000
    })
  }
}

const switchViewMode = (mode: 'entries' | 'summary') => {
  viewMode.value = mode
  if (mode === 'summary') {
    loadDailySummary()
  } else {
    loadTimeEntries()
  }
}

const formatDateTime = (dateTime: string | undefined) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString(t('locale'))
}

const formatDate = (date: string | undefined) => {
  if (!date) return '-'
  return new Date(date).toLocaleDateString(t('locale'))
}

const formatHours = (hours: number | undefined) => {
  if (hours === undefined || hours === null) return '-'
  return hours.toFixed(2) + 'h'
}

const getEntryTypeSeverity = (type: string) => {
  switch (type) {
    case 'WORK': return 'info'
    case 'SICK': return 'warn'
    case 'PTO': return 'success'
    case 'EVENT': return 'secondary'
    default: return 'info'
  }
}

const getSummaryStatusSeverity = (status: string) => {
  switch (status) {
    case 'NO_ENTRY': return 'warn'
    case 'BELOW_EXPECTED': return 'warn'
    case 'MATCHED': return 'success'
    case 'ABOVE_EXPECTED': return 'info'
    default: return 'secondary'
  }
}

const isActiveEntry = (entry: TimeEntryResponse) => {
  return entry.isActive
}

const exportMonthlyReport = async () => {
  try {
    // Get year and month from the start date filter
    const startDate = new Date(startDateFilter.value)
    const year = startDate.getFullYear()
    const month = startDate.getMonth() + 1

    console.log('Exporting report for:', year, month)

    loading.value = true

    // Make direct API call with proper responseType
    const axios = (await import('axios')).default
    // OpenAPI.TOKEN is a function, need to call it
    const token = typeof OpenAPI.TOKEN === 'function' ? OpenAPI.TOKEN() : (OpenAPI.TOKEN || localStorage.getItem('accessToken'))
    const baseUrl = OpenAPI.BASE || 'http://localhost:8080'

    console.log('Making request to:', `${baseUrl}/api/time-entries/monthly-report?year=${year}&month=${month}`)
    console.log('Using token:', token ? 'Token present' : 'No token')

    const response = await axios.get(
      `${baseUrl}/api/time-entries/monthly-report?year=${year}&month=${month}`,
      {
        headers: {
          'Authorization': `Bearer ${token}`
        },
        responseType: 'blob'
      }
    )

    console.log('Response received:', response.status, response.headers)

    // Create blob and download
    const blob = new Blob([response.data], { type: 'application/pdf' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `stundenzettel_${year}_${month.toString().padStart(2, '0')}.pdf`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('timeEntries.exportSuccess'),
      life: 3000
    })
  } catch (error: any) {
    console.error('Export error:', error)
    console.error('Error details:', {
      message: error.message,
      response: error.response,
      stack: error.stack
    })
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.message || error.response?.data?.message || t('timeEntries.exportError'),
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTimeEntries()
})
</script>

<template>
  <div class="time-entries-view">
    <div class="card">
      <div class="flex justify-content-between align-items-center mb-4">
        <h2>{{ t('timeEntries.title') }}</h2>
        <div class="flex gap-2">
          <Button
            v-if="!activeEntry"
            :label="t('timeEntries.clockIn')"
            icon="pi pi-play"
            severity="success"
            @click="openClockInDialog"
          />
          <Button
            v-else
            :label="t('timeEntries.clockOut')"
            icon="pi pi-stop"
            severity="danger"
            @click="openClockOutDialog"
          />
          <Button
            :label="t('timeEntries.addManualEntry')"
            icon="pi pi-plus"
            severity="secondary"
            @click="openManualEntryDialog"
            outlined
          />
        </div>
      </div>

      <!-- Active Entry Card -->
      <div v-if="activeEntry" class="active-entry-card mb-4 p-3 border-round" style="background: var(--green-50); border: 1px solid var(--green-200)">
        <div class="flex align-items-center gap-3">
          <i class="pi pi-clock text-3xl text-green-600"></i>
          <div class="flex-1">
            <div class="font-semibold text-lg">{{ t('timeEntries.activeSession') }}</div>
            <div class="text-sm">
              {{ t('timeEntries.clockedInAt') }}: {{ formatDateTime(activeEntry.clockIn) }}
            </div>
            <div v-if="activeEntry.notes" class="text-sm mt-1">
              {{ activeEntry.notes }}
            </div>
          </div>
        </div>
      </div>

      <!-- Filters Section -->
      <div class="filters-section mb-4">
        <!-- View Mode Toggle -->
        <div class="flex justify-content-between align-items-center mb-3">
          <div class="flex gap-2">
            <Button
              :label="t('timeEntries.viewEntries')"
              :severity="viewMode === 'entries' ? 'primary' : 'secondary'"
              size="small"
              @click="switchViewMode('entries')"
            />
            <Button
              :label="t('timeEntries.viewSummary')"
              :severity="viewMode === 'summary' ? 'primary' : 'secondary'"
              size="small"
              @click="switchViewMode('summary')"
            />
          </div>
        </div>

        <!-- Date Filter -->
        <div class="filter-bar">
          <div class="filter-fields">
            <div class="filter-field">
              <label for="startDate">{{ t('timeEntries.startDate') }}</label>
              <Calendar
                id="startDate"
                v-model="startDateFilter"
                date-format="yy-mm-dd"
                show-icon
              />
            </div>
            <div class="filter-field">
              <label for="endDate">{{ t('timeEntries.endDate') }}</label>
              <Calendar
                id="endDate"
                v-model="endDateFilter"
                date-format="yy-mm-dd"
                show-icon
              />
            </div>
          </div>
          <div class="filter-actions">
            <Button
              :label="t('filter')"
              icon="pi pi-filter"
              @click="viewMode === 'entries' ? loadTimeEntries() : loadDailySummary()"
            />
            <Button
              :label="t('timeEntries.exportPdf')"
              icon="pi pi-file-pdf"
              severity="secondary"
              @click="exportMonthlyReport"
              outlined
            />
          </div>
        </div>
      </div>

      <!-- Time Entries Table -->
      <DataTable
        v-if="viewMode === 'entries'"
        :value="timeEntries"
        :loading="loading"
        striped-rows
        responsive-layout="scroll"
        :empty-message="t('timeEntries.noEntries')"
      >
        <Column field="entryDate" :header="t('timeEntries.date')">
          <template #body="{ data }">
            {{ formatDate(data.entryDate) }}
          </template>
        </Column>
        <Column field="clockIn" :header="t('timeEntries.clockIn')">
          <template #body="{ data }">
            {{ formatDateTime(data.clockIn) }}
          </template>
        </Column>
        <Column field="clockOut" :header="t('timeEntries.clockOut')">
          <template #body="{ data }">
            {{ formatDateTime(data.clockOut) }}
          </template>
        </Column>
        <Column field="breakMinutes" :header="t('timeEntries.breakMinutes')">
          <template #body="{ data }">
            {{ data.breakMinutes || 0 }} min
          </template>
        </Column>
        <Column field="hoursWorked" :header="t('timeEntries.hoursWorked')">
          <template #body="{ data }">
            {{ formatHours(data.hoursWorked) }}
          </template>
        </Column>
        <Column field="entryType" :header="t('timeEntries.type.label')">
          <template #body="{ data }">
            <Tag :value="t(`timeEntries.type.${data.entryType}`)" :severity="getEntryTypeSeverity(data.entryType)" />
          </template>
        </Column>
        <Column field="notes" :header="t('timeEntries.notes')">
          <template #body="{ data }">
            {{ data.notes || '-' }}
          </template>
        </Column>
        <Column :header="t('actions')">
          <template #body="{ data }">
            <div class="flex gap-2">
              <Button
                icon="pi pi-pencil"
                text
                rounded
                severity="info"
                @click="openEditDialog(data)"
                :disabled="isActiveEntry(data)"
              />
              <Button
                icon="pi pi-trash"
                text
                rounded
                severity="danger"
                @click="openDeleteDialog(data)"
                :disabled="isActiveEntry(data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>

      <!-- Daily Summary Table -->
      <DataTable
        v-if="viewMode === 'summary'"
        :value="dailySummaries"
        :loading="loading"
        striped-rows
        responsive-layout="scroll"
        :empty-message="t('timeEntries.noSummaries')"
      >
        <Column field="date" :header="t('timeEntries.date')">
          <template #body="{ data }">
            {{ formatDate(data.date) }}
          </template>
        </Column>
        <Column field="actualHours" :header="t('timeEntries.actualHours')">
          <template #body="{ data }">
            {{ formatHours(data.actualHours) }}
          </template>
        </Column>
        <Column field="expectedHours" :header="t('timeEntries.expectedHours')">
          <template #body="{ data }">
            {{ formatHours(data.expectedHours) }}
          </template>
        </Column>
        <Column field="status" :header="t('timeEntries.status')">
          <template #body="{ data }">
            <Tag :value="t(`timeEntries.status.${data.status}`)" :severity="getSummaryStatusSeverity(data.status)" />
          </template>
        </Column>
        <Column :header="t('timeEntries.notes')">
          <template #body="{ data }">
            <div v-if="data.timeOffEntries && data.timeOffEntries.length > 0" class="mb-2">
              <div v-for="timeOff in data.timeOffEntries" :key="timeOff.id" class="mb-1">
                <Tag :value="t(`timeOff.type.${timeOff.timeOffType}`)" severity="success" class="mr-2" />
                <span v-if="timeOff.notes" class="text-sm">{{ timeOff.notes }}</span>
              </div>
            </div>
            <div v-if="data.recurringOffDays && data.recurringOffDays.length > 0">
              <div v-for="rod in data.recurringOffDays" :key="rod.id" class="mb-1">
                <Tag value="Recurring Off-Day" severity="secondary" class="mr-2" />
                <span v-if="rod.description" class="text-sm">{{ rod.description }}</span>
              </div>
            </div>
            <span v-if="(!data.timeOffEntries || data.timeOffEntries.length === 0) && (!data.recurringOffDays || data.recurringOffDays.length === 0)">-</span>
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Clock In Dialog -->
    <Dialog
      v-model:visible="clockInDialogVisible"
      :header="t('timeEntries.clockIn')"
      :modal="true"
      :style="{ width: '400px' }"
    >
      <div class="field">
        <label for="clockInNotes">{{ t('timeEntries.notes') }}</label>
        <Textarea
          id="clockInNotes"
          v-model="clockInNotes"
          rows="3"
          :placeholder="t('timeEntries.notesPlaceholder')"
          class="w-full"
        />
      </div>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="clockInDialogVisible = false" />
        <Button :label="t('timeEntries.clockIn')" severity="success" @click="clockIn" />
      </template>
    </Dialog>

    <!-- Clock Out Dialog -->
    <Dialog
      v-model:visible="clockOutDialogVisible"
      :header="t('timeEntries.clockOut')"
      :modal="true"
      :style="{ width: '400px' }"
    >
      <div class="field">
        <label for="clockOutNotes">{{ t('timeEntries.notes') }}</label>
        <Textarea
          id="clockOutNotes"
          v-model="clockOutNotes"
          rows="3"
          :placeholder="t('timeEntries.notesPlaceholder')"
          class="w-full"
        />
      </div>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="clockOutDialogVisible = false" />
        <Button :label="t('timeEntries.clockOut')" severity="danger" @click="clockOut" />
      </template>
    </Dialog>

    <!-- Manual Entry Dialog -->
    <Dialog
      v-model:visible="manualEntryDialogVisible"
      :header="t('timeEntries.manualEntry')"
      :modal="true"
      :style="{ width: '500px' }"
    >
      <div class="field">
        <label for="manualStartTime">{{ t('timeEntries.startTime') }}</label>
        <Calendar
          id="manualStartTime"
          v-model="newManualEntry.clockIn"
          show-time
          hour-format="24"
          date-format="yy-mm-dd"
          class="w-full"
        />
      </div>
      <div class="field">
        <label for="manualEndTime">{{ t('timeEntries.endTime') }}</label>
        <Calendar
          id="manualEndTime"
          v-model="newManualEntry.clockOut"
          show-time
          hour-format="24"
          date-format="yy-mm-dd"
          class="w-full"
        />
      </div>
      <div class="field">
        <label for="manualBreakMinutes">{{ t('timeEntries.breakMinutes') }}</label>
        <InputNumber
          id="manualBreakMinutes"
          v-model="newManualEntry.breakMinutes"
          :min="0"
          :max="480"
          suffix=" min"
          class="w-full"
        />
      </div>
      <div class="field">
        <label for="manualEntryType">{{ t('timeEntries.type.label') }}</label>
        <Dropdown
          id="manualEntryType"
          v-model="newManualEntry.entryType"
          :options="entryTypeOptions"
          option-label="label"
          option-value="value"
          class="w-full"
        />
      </div>
      <div class="field">
        <label for="manualNotes">{{ t('timeEntries.notes') }}</label>
        <Textarea
          id="manualNotes"
          v-model="newManualEntry.notes"
          rows="3"
          class="w-full"
        />
      </div>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="manualEntryDialogVisible = false" />
        <Button :label="t('save')" severity="primary" @click="createManualEntry" />
      </template>
    </Dialog>

    <!-- Edit Dialog -->
    <Dialog
      v-model:visible="editDialogVisible"
      :header="t('timeEntries.edit')"
      :modal="true"
      :style="{ width: '500px' }"
    >
      <div class="field">
        <label for="editClockIn">{{ t('timeEntries.clockIn') }}</label>
        <Calendar
          id="editClockIn"
          v-model="currentTimeEntry.clockIn"
          show-time
          hour-format="24"
          date-format="yy-mm-dd"
          class="w-full"
        />
      </div>
      <div class="field">
        <label for="editClockOut">{{ t('timeEntries.clockOut') }}</label>
        <Calendar
          id="editClockOut"
          v-model="currentTimeEntry.clockOut"
          show-time
          hour-format="24"
          date-format="yy-mm-dd"
          class="w-full"
        />
      </div>
      <div class="field">
        <label for="editBreakMinutes">{{ t('timeEntries.breakMinutes') }}</label>
        <InputNumber
          id="editBreakMinutes"
          v-model="currentTimeEntry.breakMinutes"
          :min="0"
          :max="480"
          suffix=" min"
          class="w-full"
        />
      </div>
      <div class="field">
        <label for="editEntryType">{{ t('timeEntries.type.label') }}</label>
        <Dropdown
          id="editEntryType"
          v-model="currentTimeEntry.entryType"
          :options="entryTypeOptions"
          option-label="label"
          option-value="value"
          class="w-full"
        />
      </div>
      <div class="field">
        <label for="editNotes">{{ t('timeEntries.notes') }}</label>
        <Textarea
          id="editNotes"
          v-model="currentTimeEntry.notes"
          rows="3"
          class="w-full"
        />
      </div>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="editDialogVisible = false" />
        <Button :label="t('save')" severity="primary" @click="saveTimeEntry" />
      </template>
    </Dialog>

    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="deleteDialogVisible"
      :header="t('timeEntries.deleteConfirmTitle')"
      :modal="true"
      :style="{ width: '400px' }"
    >
      <p>{{ t('timeEntries.deleteConfirmMessage') }}</p>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="deleteDialogVisible = false" />
        <Button :label="t('delete')" severity="danger" @click="deleteTimeEntry" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.time-entries-view {
  padding: 0;
}

h2 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.active-entry-card {
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.95;
  }
}

.filters-section {
  background: var(--surface-50);
  padding: 1.25rem;
  border-radius: var(--border-radius);
  border: 1px solid var(--surface-200);
}

.filter-bar {
  display: flex;
  gap: 1rem;
  align-items: flex-end;
  flex-wrap: wrap;
}

.filter-fields {
  display: flex;
  gap: 1rem;
  flex: 1;
  min-width: 300px;
}

.filter-field {
  flex: 1;
  min-width: 200px;
}

.filter-field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--text-color);
}

.filter-actions {
  display: flex;
  gap: 0.5rem;
  align-items: flex-end;
}
</style>
