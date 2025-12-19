<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import DatePicker from '@/components/common/DatePicker.vue'
import DateRangeFilter from '@/components/common/DateRangeFilter.vue'
import TimeOffQuickForm from '@/components/dashboard/TimeOffQuickForm.vue'
import UndoDeleteToast from '@/components/common/UndoDeleteToast.vue'
import { TimeOffService, VacationBalanceService } from '@/api/generated'
import type { TimeOffResponse, CreateTimeOffRequest, UpdateTimeOffRequest, VacationBalanceResponse, UpdateVacationBalanceRequest } from '@/api/generated'
import { useAuth } from '@/composables/useAuth'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useUndoDelete } from '@/composables/useUndoDelete'

const { t } = useI18n()
const toast = useToast()
const { currentUser } = useAuth()
const { handleError } = useErrorHandler()

const timeOffs = ref<TimeOffResponse[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const timeOffToEdit = ref<TimeOffResponse | null>(null)

// Undo delete composable
const timeOffUndo = useUndoDelete<TimeOffResponse>('delete-undo-timeoff')

// Date range filter
const startDateFilter = ref<string>()
const endDateFilter = ref<string>()

// Vacation balance
const balance = ref<VacationBalanceResponse | null>(null)
const balanceLoading = ref(false)
const editBalanceDialogVisible = ref(false)
const selectedYear = ref(new Date().getFullYear())
const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() + i - 2)

const editBalanceForm = ref<UpdateVacationBalanceRequest>({
  userId: currentUser.value?.id || 0,
  year: new Date().getFullYear(),
  annualAllowanceDays: 30.0,
  carriedOverDays: 0.0,
  adjustmentDays: 0.0
})

// Sick days statistics
const sickDaysCount = ref(0)
const childSickDaysCount = ref(0)


const remainingDays = computed(() => {
  if (!balance.value) return 0
  return balance.value.remainingDays
})

const totalAvailableDays = computed(() => {
  if (!balance.value) return 0
  return balance.value.annualAllowanceDays + balance.value.carriedOverDays + balance.value.adjustmentDays
})

const loadBalance = async () => {
  balanceLoading.value = true
  try {
    const response = await VacationBalanceService.getVacationBalance(selectedYear.value)
    balance.value = response
  } catch (error: any) {
    if (error?.status === 404) {
      balance.value = null
    } else {
      handleError(error, 'Failed to load vacation balance', { logError: true })
    }
  } finally {
    balanceLoading.value = false
  }
}

const calculateSickDays = (entries: TimeOffResponse[]) => {
  try {
    // Filter for entries in the selected year
    const yearStart = new Date(selectedYear.value, 0, 1)
    const yearEnd = new Date(selectedYear.value, 11, 31)

    const sickEntries = entries.filter(entry => {
      const entryDate = new Date(entry.startDate)
      return entry.timeOffType === 'SICK' &&
             entryDate >= yearStart &&
             entryDate <= yearEnd
    })

    // Use the 'days' field which counts working days
    const total = sickEntries.reduce((sum, entry) => sum + entry.days, 0)
    sickDaysCount.value = total
  } catch (error) {
    handleError(error, 'Failed to calculate sick days', { logError: true })
    sickDaysCount.value = 0
  }
}

const calculateChildSickDays = (entries: TimeOffResponse[]) => {
  try {
    // Filter for entries in the selected year
    const yearStart = new Date(selectedYear.value, 0, 1)
    const yearEnd = new Date(selectedYear.value, 11, 31)

    const childSickEntries = entries.filter(entry => {
      const entryDate = new Date(entry.startDate)
      return entry.timeOffType === 'CHILD_SICK' &&
             entryDate >= yearStart &&
             entryDate <= yearEnd
    })

    // Use the 'days' field which counts working days
    const total = childSickEntries.reduce((sum, entry) => sum + entry.days, 0)
    childSickDaysCount.value = total
  } catch (error) {
    handleError(error, 'Failed to calculate child sick days', { logError: true })
    childSickDaysCount.value = 0
  }
}

const openEditBalanceDialog = () => {
  if (!balance.value || !currentUser.value) return

  editBalanceForm.value = {
    userId: currentUser.value.id,
    year: selectedYear.value,
    annualAllowanceDays: balance.value.annualAllowanceDays,
    carriedOverDays: balance.value.carriedOverDays,
    adjustmentDays: balance.value.adjustmentDays
  }
  editBalanceDialogVisible.value = true
}

const saveBalance = async () => {
  try {
    await VacationBalanceService.updateVacationBalance(editBalanceForm.value)
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('vacationBalance.updateSuccess'),
      life: 3000
    })
    editBalanceDialogVisible.value = false
    await loadBalance()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('vacationBalance.updateError'),
      life: 3000
    })
  }
}

const onYearChange = async () => {
  // Update date filters to cover entire selected year
  startDateFilter.value = `${selectedYear.value}-01-01`
  endDateFilter.value = `${selectedYear.value}-12-31`

  // Reload all data
  await loadBalance()
  await loadTimeOffs()
}

const loadTimeOffs = async () => {
  loading.value = true
  try {
    // Convert Date objects to strings if needed
    const formatDateForApi = (date: any) => {
      if (!date) return undefined
      if (typeof date === 'string') return date
      if (date instanceof Date) {
        const year = date.getFullYear()
        const month = String(date.getMonth() + 1).padStart(2, '0')
        const day = String(date.getDate()).padStart(2, '0')
        return `${year}-${month}-${day}`
      }
      return undefined
    }

    const startDate = formatDateForApi(startDateFilter.value)
    const endDate = formatDateForApi(endDateFilter.value)

    const response = await TimeOffService.getTimeOffEntries(startDate, endDate)
    timeOffs.value = response

    // Calculate sick days from the loaded data (no additional API calls)
    calculateSickDays(response)
    calculateChildSickDays(response)
  } catch (error: any) {
    handleError(error, t('timeOff.loadError'))
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  timeOffToEdit.value = null
  dialogVisible.value = true
}

const openEditDialog = (timeOff: TimeOffResponse) => {
  timeOffToEdit.value = timeOff
  dialogVisible.value = true
}

const handleTimeOffSaved = async () => {
  await loadTimeOffs()
  await loadBalance() // Refresh vacation balance after create/update
}

const deleteTimeOff = async (timeOff: TimeOffResponse) => {
  await timeOffUndo.deleteWithUndo(
    timeOff,
    async (id) => {
      await TimeOffService.deleteTimeOff(id as number)
    },
    async () => {
      await loadTimeOffs()
      await loadBalance() // Refresh vacation balance after delete
    },
    (item) => {
      const startDate = new Date(item.startDate)
      const endDate = new Date(item.endDate)
      const startStr = startDate.toLocaleDateString(t('locale'), { day: '2-digit', month: '2-digit', year: 'numeric' })
      const endStr = endDate.toLocaleDateString(t('locale'), { day: '2-digit', month: '2-digit', year: 'numeric' })
      return t('timeOff.deleteSuccess') + ` (${startStr} - ${endStr})`
    }
  )
}

const undoTimeOffDelete = async () => {
  await timeOffUndo.undoDelete(
    async (item) => {
      const createRequest: CreateTimeOffRequest = {
        timeOffType: item.timeOffType,
        startDate: item.startDate,
        endDate: item.endDate,
        hoursPerDay: item.hoursPerDay,
        notes: item.notes
      }
      await TimeOffService.createTimeOff(createRequest)
    },
    async () => {
      await loadTimeOffs()
      await loadBalance()
    }
  )
}

const timeOffTypeOptions = [
  { label: t('timeOff.type.VACATION'), value: 'VACATION' },
  { label: t('timeOff.type.SICK'), value: 'SICK' },
  { label: t('timeOff.type.CHILD_SICK'), value: 'CHILD_SICK' },
  { label: t('timeOff.type.PERSONAL'), value: 'PERSONAL' },
  { label: t('timeOff.type.PUBLIC_HOLIDAY'), value: 'PUBLIC_HOLIDAY' }
]

const getTypeLabel = (type: string) => {
  const option = timeOffTypeOptions.find(t => t.value === type)
  return option ? option.label : type
}

const getTypeSeverity = (type: string) => {
  switch (type) {
    case 'VACATION': return 'info'
    case 'SICK': return 'danger'
    case 'PERSONAL': return 'warning'
    case 'PUBLIC_HOLIDAY': return 'success'
    default: return 'secondary'
  }
}

const formatDisplayDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const year = date.getFullYear()
  return `${day}.${month}.${year}`
}

onMounted(() => {
  // Default filter: entire current year
  const now = new Date()
  const yearStart = new Date(now.getFullYear(), 0, 1)
  const yearEnd = new Date(now.getFullYear(), 11, 31)

  const formatDate = (date: Date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  startDateFilter.value = formatDate(yearStart)
  endDateFilter.value = formatDate(yearEnd)
  loadBalance()
  loadTimeOffs()
})
</script>

<template>
  <div class="time-off-view">
    <h1 class="page-title">{{ t('timeOff.title') }}</h1>

    <!-- Statistics Container with Year Selection -->
    <div class="stats-layout">
      <!-- Year Selection Card -->
      <div class="year-selection-card">
        <div class="year-selection-content">
          <div class="year-selection-info">
            <h3>{{ t('timeOff.yearSelection.title') }}</h3>
            <p class="year-selection-hint">{{ t('timeOff.yearSelection.hint') }}</p>
          </div>
          <Select
            v-model="selectedYear"
            :options="years"
            @change="onYearChange"
            class="year-select"
            :pt="{
              root: { class: 'year-select-large' }
            }"
          />
          <Button
            v-if="balance"
            :label="t('edit')"
            icon="pi pi-pencil"
            severity="secondary"
            outlined
            size="large"
            @click="openEditBalanceDialog"
            class="edit-balance-button"
          />
        </div>
      </div>

      <!-- Statistics Cards -->
      <div class="stats-container">
        <div v-if="balanceLoading" class="flex justify-content-center align-items-center" style="height: 100%;">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>

        <div v-else-if="balance" class="stats-grid">
          <!-- Vacation Cards -->
          <div class="stat-card">
            <div class="stat-label">{{ t('vacationBalance.annualAllowanceDays') }}</div>
            <div class="stat-value">{{ balance.annualAllowanceDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card stat-planned">
            <div class="stat-label">{{ t('vacationBalance.plannedDays') }}</div>
            <div class="stat-value">{{ balance.plannedDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card stat-used">
            <div class="stat-label">{{ t('vacationBalance.usedDays') }}</div>
            <div class="stat-value">{{ balance.usedDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card stat-remaining">
            <div class="stat-label">{{ t('vacationBalance.leftForPlanning') }}</div>
            <div class="stat-value">{{ remainingDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card">
            <div class="stat-label">{{ t('vacationBalance.carriedOverDays') }}</div>
            <div class="stat-value">{{ balance.carriedOverDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card">
            <div class="stat-label">{{ t('vacationBalance.adjustmentDays') }}</div>
            <div class="stat-value">{{ balance.adjustmentDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <!-- Sick Days Card -->
          <div class="stat-card stat-sick">
            <div class="stat-label">{{ t('timeOff.sickDaysThisYear') }}</div>
            <div class="stat-value">{{ sickDaysCount.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <!-- Child Sick Days Card -->
          <div class="stat-card stat-sick">
            <div class="stat-label">{{ t('timeOff.childSickDaysThisYear') }}</div>
            <div class="stat-value">{{ childSickDaysCount.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>
        </div>

        <div v-else class="text-center py-3 text-gray-500">
          <i class="pi pi-info-circle text-2xl mb-2" />
          <p class="text-sm">{{ t('vacationBalance.noData') }}</p>
        </div>
      </div>
    </div>

    <!-- Time Off Entries -->
    <div class="entries-container">
      <div class="entries-header">
        <h3>{{ t('timeOff.entries') }}</h3>
        <Button
          :label="t('timeOff.create')"
          icon="pi pi-plus"
          @click="openCreateDialog"
        />
      </div>

      <div class="filters">
        <DateRangeFilter
          v-model:start-date="startDateFilter"
          v-model:end-date="endDateFilter"
          @filter="loadTimeOffs"
        />
      </div>

      <DataTable
        :value="timeOffs"
        :loading="loading"
        striped-rows
        responsive-layout="scroll"
        sort-field="startDate"
        :sort-order="-1"
      >
        <Column field="timeOffType" :header="t('timeOff.type.label')">
          <template #body="{ data }">
            <Tag :severity="getTypeSeverity(data.timeOffType)">
              {{ getTypeLabel(data.timeOffType) }}
            </Tag>
          </template>
        </Column>
        <Column field="startDate" :header="t('timeOff.startDate')" sortable>
          <template #body="{ data }">
            {{ formatDisplayDate(data.startDate) }}
          </template>
        </Column>
        <Column field="endDate" :header="t('timeOff.endDate')" sortable>
          <template #body="{ data }">
            {{ formatDisplayDate(data.endDate) }}
          </template>
        </Column>
        <Column field="days" :header="t('timeOff.days')">
          <template #body="{ data }">
            {{ typeof data.days === 'number' ? data.days.toFixed(1) : data.days }}
          </template>
        </Column>
        <Column field="hoursPerDay" :header="t('timeOff.hoursPerDay')">
          <template #body="{ data }">
            {{ data.hoursPerDay ? data.hoursPerDay.toFixed(1) + 'h' : '-' }}
          </template>
        </Column>
        <Column field="notes" :header="t('timeOff.notes')">
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
              />
              <Button
                icon="pi pi-trash"
                text
                rounded
                severity="danger"
                @click="deleteTimeOff(data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>

      <div v-if="timeOffs.length === 0 && !loading" class="text-center py-4 text-gray-500">
        {{ t('timeOff.noData') }}
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <TimeOffQuickForm
      v-model:visible="dialogVisible"
      :time-off="timeOffToEdit"
      @saved="handleTimeOffSaved"
    />

    <!-- Toast for undo delete -->
    <UndoDeleteToast :group="timeOffUndo.undoGroup" :on-undo="undoTimeOffDelete" />

    <!-- Edit Vacation Balance Dialog -->
    <Dialog
      v-model:visible="editBalanceDialogVisible"
      :header="t('vacationBalance.edit')"
      :modal="true"
      :style="{ width: '90vw', maxWidth: '500px' }"
      :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
    >
      <div class="p-fluid">
        <div class="field">
          <label for="annualAllowanceDays">{{ t('vacationBalance.annualAllowanceDays') }} *</label>
          <InputNumber
            id="annualAllowanceDays"
            v-model="editBalanceForm.annualAllowanceDays"
            :min="0"
            :max="365"
            :max-fraction-digits="1"
            suffix=" days"
          />
        </div>

        <div class="field">
          <label for="carriedOverDays">{{ t('vacationBalance.carriedOverDays') }}</label>
          <InputNumber
            id="carriedOverDays"
            v-model="editBalanceForm.carriedOverDays"
            :min="0"
            :max="365"
            :max-fraction-digits="1"
            suffix=" days"
          />
          <small>{{ t('vacationBalance.carriedOverDaysHint') }}</small>
        </div>

        <div class="field">
          <label for="adjustmentDays">{{ t('vacationBalance.adjustmentDays') }}</label>
          <InputNumber
            id="adjustmentDays"
            v-model="editBalanceForm.adjustmentDays"
            :min="-365"
            :max="365"
            :max-fraction-digits="1"
            suffix=" days"
          />
          <small>{{ t('vacationBalance.adjustmentDaysHint') }}</small>
        </div>

        <Message severity="info" :closable="false">
          {{ t('vacationBalance.usedDaysAutoCalculated') }}
        </Message>
      </div>

      <template #footer>
        <Button
          :label="t('cancel')"
          icon="pi pi-times"
          class="p-button-text"
          @click="editBalanceDialogVisible = false"
        />
        <Button
          :label="t('save')"
          icon="pi pi-check"
          @click="saveBalance"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
/* Using shared layout, form, and stat-card styles */
.time-off-view {
  padding: var(--tt-view-padding);
}

/* Main Statistics Layout - 6 columns: 2 for year selection, 4 for stats */
.stats-layout {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  grid-auto-rows: minmax(120px, auto);
  gap: var(--tt-spacing-sm);
  margin-bottom: var(--tt-spacing-lg);
}

/* Year Selection Card - Takes 2 columns, 2 rows high */
.year-selection-card {
  grid-column: span 2;
  grid-row: span 2;
  background: linear-gradient(135deg, var(--tt-blue-from) 0%, var(--tt-blue-to) 100%);
  border-radius: var(--tt-radius-sm);
  padding: var(--tt-spacing-md);
  box-shadow: var(--tt-shadow-sm);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.year-selection-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

.year-selection-info h3 {
  margin: 0 0 var(--tt-spacing-xs) 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: white;
}

.year-selection-hint {
  margin: 0 0 var(--tt-spacing-sm) 0;
  font-size: 0.875rem;
  color: rgba(255, 255, 255, 0.95);
  line-height: 1.4;
}

.year-select {
  width: 100%;
}

.edit-balance-button {
  width: 100%;
  margin-top: var(--tt-spacing-xs);
  background: white;
  border: none;
  color: var(--tt-blue-to);
  font-weight: 600;
}

.edit-balance-button:hover {
  background: rgba(255, 255, 255, 0.9);
  color: var(--tt-blue-to);
}

/* Statistics Container - Spans remaining 4 columns, 2 rows */
.stats-container {
  grid-column: span 4;
  grid-row: span 2;
  display: flex;
  flex-direction: column;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: var(--tt-spacing-sm);
  flex: 1;
}

/* Override stat card sizing to be more compact */
.stat-card {
  padding: 0.5rem 0.75rem;
  min-height: 0;
}

.stat-label {
  font-size: 0.75rem;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 1.5rem;
  margin-bottom: 0.125rem;
  line-height: 1.2;
}

.stat-unit {
  font-size: 0.875rem;
  color: #9ca3af;
  font-weight: 500;
}

/* Entries Container - matches dashboard pattern */
.entries-container {
  background: white;
  border-radius: var(--tt-radius-md);
  padding: var(--tt-card-padding);
  box-shadow: var(--tt-shadow-sm);
}

.entries-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--tt-spacing-lg);
}

.entries-header h3 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: #1f2937;
}

/* Time-off specific stat colors */
.stat-card.stat-used {
  background: linear-gradient(135deg, #f59e0b 0%, #ef4444 100%);
}

.stat-card.stat-used .stat-label,
.stat-card.stat-used .stat-value,
.stat-card.stat-used .stat-unit {
  color: white;
}

.stat-card.stat-planned {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-card.stat-planned .stat-label,
.stat-card.stat-planned .stat-value,
.stat-card.stat-planned .stat-unit {
  color: white;
}

.stat-card.stat-remaining {
  background: linear-gradient(135deg, var(--tt-green-from) 0%, var(--tt-green-to) 100%);
}

.stat-card.stat-remaining .stat-label,
.stat-card.stat-remaining .stat-value,
.stat-card.stat-remaining .stat-unit {
  color: white;
}

.stat-card.stat-sick {
  background: linear-gradient(135deg, #dc2626 0%, #991b1b 100%);
}

.stat-card.stat-sick .stat-label,
.stat-card.stat-sick .stat-value,
.stat-card.stat-sick .stat-unit {
  color: white;
}

.year-select {
  min-width: 120px;
}

.filters {
  margin-bottom: var(--tt-spacing-md);
}

.field small {
  display: block;
  margin-top: 0.25rem;
  color: #6c757d;
}

/* Responsive adjustments */
@media (max-width: 1200px) {
  .stats-layout {
    grid-template-columns: repeat(5, 1fr);
  }

  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 992px) {
  .stats-layout {
    grid-template-columns: repeat(4, 1fr);
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .time-off-view {
    padding: var(--tt-view-padding-mobile);
  }

  .stats-layout {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
  }

  .year-selection-card {
    grid-column: 1;
    grid-row: 1;
  }

  .stats-container {
    grid-column: 1;
    grid-row: 2;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    grid-template-rows: auto;
  }

  .entries-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--tt-spacing-sm);
  }
}

@media (max-width: 480px) {
  .time-off-view {
    padding: var(--tt-view-padding-xs);
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
