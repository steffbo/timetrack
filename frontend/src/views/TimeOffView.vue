<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import InputNumber from 'primevue/inputnumber'
import TimeOffQuickForm from '@/components/dashboard/TimeOffQuickForm.vue'
import { TimeOffService, VacationBalanceService } from '@/api/generated'
import type { TimeOffResponse, CreateTimeOffRequest, VacationBalanceResponse, UpdateVacationBalanceRequest } from '@/api/generated'
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

const { deleteWithUndo } = useUndoDelete()

// Year selection (replaces date range filter)
const selectedYear = ref(new Date().getFullYear())
const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() + i - 2)

// Vacation balance
const balance = ref<VacationBalanceResponse | null>(null)
const balanceLoading = ref(false)
const editBalanceDialogVisible = ref(false)

const editBalanceForm = ref<UpdateVacationBalanceRequest>({
  userId: currentUser.value?.id || 0,
  year: new Date().getFullYear(),
  annualAllowanceDays: 30.0,
  carriedOverDays: 0.0,
  adjustmentDays: 0.0
})

// Time-off type statistics (calculated from loaded data)
const timeOffByType = computed(() => {
  const yearStart = new Date(selectedYear.value, 0, 1)
  const yearEnd = new Date(selectedYear.value, 11, 31)
  
  const types = {
    VACATION: 0,
    SICK: 0,
    CHILD_SICK: 0,
    PERSONAL: 0,
    EDUCATION: 0,
    PUBLIC_HOLIDAY: 0
  }
  
  timeOffs.value.forEach(entry => {
    const entryDate = new Date(entry.startDate)
    if (entryDate >= yearStart && entryDate <= yearEnd) {
      const type = entry.timeOffType as keyof typeof types
      if (type in types) {
        types[type] += entry.days
      }
    }
  })
  
  return types
})


const remainingDays = computed(() => {
  if (!balance.value) return 0
  return balance.value.remainingDays
})

const totalAvailableDays = computed(() => {
  if (!balance.value) return 0
  return balance.value.annualAllowanceDays + balance.value.carriedOverDays + balance.value.adjustmentDays
})

// Progress bar percentages
const usedPercentage = computed(() => {
  if (!balance.value || totalAvailableDays.value === 0) return 0
  return Math.min((balance.value.usedDays / totalAvailableDays.value) * 100, 100)
})

const plannedPercentage = computed(() => {
  if (!balance.value || totalAvailableDays.value === 0) return 0
  const futurePlanned = balance.value.plannedDays - balance.value.usedDays
  return Math.min((futurePlanned / totalAvailableDays.value) * 100, 100 - usedPercentage.value)
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

const selectYear = async (year: number) => {
  selectedYear.value = year
  // Reload all data for the selected year
  await loadBalance()
  await loadTimeOffs()
}

const loadTimeOffs = async () => {
  loading.value = true
  try {
    // Load entries for the selected year
    const startDate = `${selectedYear.value}-01-01`
    const endDate = `${selectedYear.value}-12-31`

    const response = await TimeOffService.getTimeOffEntries(startDate, endDate)
    timeOffs.value = response
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
  await deleteWithUndo(
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
    },
    async (item) => {
      const createRequest: CreateTimeOffRequest = {
        timeOffType: item.timeOffType,
        startDate: item.startDate,
        endDate: item.endDate,
        hoursPerDay: item.hoursPerDay,
        notes: item.notes,
        confirmed: item.confirmed
      }
      await TimeOffService.createTimeOff(createRequest)
    },
    {
      showUndoSuccessToast: true
    }
  )
}

const timeOffTypeOptions = [
  { label: t('timeOff.type.VACATION'), value: 'VACATION' },
  { label: t('timeOff.type.SICK'), value: 'SICK' },
  { label: t('timeOff.type.CHILD_SICK'), value: 'CHILD_SICK' },
  { label: t('timeOff.type.PERSONAL'), value: 'PERSONAL' },
  { label: t('timeOff.type.EDUCATION'), value: 'EDUCATION' },
  { label: t('timeOff.type.PUBLIC_HOLIDAY'), value: 'PUBLIC_HOLIDAY' }
]

const getTypeLabel = (type: string) => {
  const option = timeOffTypeOptions.find(t => t.value === type)
  return option ? option.label : type
}

const getTypeEmoji = (type: string) => {
  switch (type) {
    case 'VACATION': return '🏝️'
    case 'SICK': return '😵‍💫'
    case 'CHILD_SICK': return '👶'
    case 'PERSONAL': return '🏠'
    case 'EDUCATION': return '📚'
    case 'PUBLIC_HOLIDAY': return '🎊'
    default: return '📅'
  }
}

const formatDisplayDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const year = date.getFullYear()
  return `${day}.${month}.${year}`
}

// Format days: show whole number if x.0, otherwise one decimal
const formatDays = (value: number) => {
  return value % 1 === 0 ? value.toFixed(0) : value.toFixed(1)
}

onMounted(() => {
  loadBalance()
  loadTimeOffs()
})
</script>

<template>
  <div class="time-off-view">
    <h1 class="page-title">{{ t('timeOff.title') }}</h1>

    <!-- Year Filter -->
    <div class="filter-card">
      <label class="filter-label">{{ t('publicHolidays.year') }}</label>
      <div class="filter-buttons">
        <button
          v-for="year in years"
          :key="year"
          :class="['filter-btn', { active: selectedYear === year }]"
          @click="selectYear(year)"
        >
          {{ year }}
        </button>
      </div>
    </div>

    <!-- Vacation Balance Summary with Progress Bar -->
    <div v-if="balance" class="vacation-summary">
      <div class="vacation-header">
        <div class="vacation-main">
          <span class="vacation-icon">🏝️</span>
          <span class="vacation-value">{{ formatDays(remainingDays) }}</span>
          <span class="vacation-label">{{ t('vacationBalance.leftForPlanning') }}</span>
        </div>
        <button 
          class="vacation-edit-btn" 
          @click="openEditBalanceDialog"
          :title="t('vacationBalance.edit')"
        >
          <i class="pi pi-cog"></i>
        </button>
      </div>
      <div class="progress-container">
        <div class="progress-bar">
          <div 
            class="progress-used" 
            :style="{ width: usedPercentage + '%' }"
          ></div>
          <div 
            class="progress-planned" 
            :style="{ width: plannedPercentage + '%', left: usedPercentage + '%' }"
          ></div>
        </div>
        <div class="progress-labels">
          <span>{{ formatDays(balance.usedDays) }} {{ t('vacationBalance.usedDays') }}</span>
          <span>{{ formatDays(balance.plannedDays) }} {{ t('vacationBalance.plannedDays') }}</span>
          <span>{{ formatDays(totalAvailableDays) }} {{ t('dashboard.vacationBalance.total') }}</span>
        </div>
      </div>
    </div>

    <!-- Time Off by Type -->
    <div class="type-section">
      <h3 class="section-title">{{ t('timeOff.byType') }}</h3>
      <div v-if="loading" class="loading-state">
        <i class="pi pi-spin pi-spinner"></i>
      </div>
      <div v-else class="type-grid">
        <div class="type-card type-vacation">
          <span class="type-emoji">🏝️</span>
          <span class="type-label">{{ t('timeOff.type.VACATION') }}</span>
          <span class="type-value">{{ formatDays(timeOffByType.VACATION) }}</span>
        </div>
        <div class="type-card type-sick">
          <span class="type-emoji">😵‍💫</span>
          <span class="type-label">{{ t('timeOff.type.SICK') }}</span>
          <span class="type-value">{{ formatDays(timeOffByType.SICK) }}</span>
        </div>
        <div class="type-card type-child-sick">
          <span class="type-emoji">👶</span>
          <span class="type-label">{{ t('timeOff.type.CHILD_SICK') }}</span>
          <span class="type-value">{{ formatDays(timeOffByType.CHILD_SICK) }}</span>
        </div>
        <div class="type-card type-personal">
          <span class="type-emoji">🏠</span>
          <span class="type-label">{{ t('timeOff.type.PERSONAL') }}</span>
          <span class="type-value">{{ formatDays(timeOffByType.PERSONAL) }}</span>
        </div>
        <div class="type-card type-education">
          <span class="type-emoji">📚</span>
          <span class="type-label">{{ t('timeOff.type.EDUCATION') }}</span>
          <span class="type-value">{{ formatDays(timeOffByType.EDUCATION) }}</span>
        </div>
      </div>
    </div>

    <!-- Time Off Entries List -->
    <div class="entries-section">
      <div class="section-header">
        <h3 class="section-title">{{ t('timeOff.entries') }}</h3>
        <Button
          :label="t('timeOff.create')"
          icon="pi pi-plus"
          size="small"
          @click="openCreateDialog"
        />
      </div>

      <div v-if="loading" class="loading-state">
        <i class="pi pi-spin pi-spinner"></i>
      </div>

      <div v-else-if="timeOffs.length === 0" class="empty-state">
        {{ t('timeOff.noData') }}
      </div>

      <DataTable
        v-else
        :value="timeOffs"
        :loading="loading"
        striped-rows
        responsive-layout="scroll"
        sort-field="startDate"
        :sort-order="-1"
        class="time-off-table"
      >
        <Column field="timeOffType" :header="t('timeOff.type.label')">
          <template #body="{ data }">
            <span class="type-badge" :class="'type-' + data.timeOffType.toLowerCase().replace('_', '-')">
              {{ getTypeEmoji(data.timeOffType) }} {{ getTypeLabel(data.timeOffType) }}
            </span>
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
            {{ typeof data.days === 'number' ? formatDays(data.days) : data.days }}
          </template>
        </Column>
        <Column field="notes" :header="t('timeOff.notes')">
          <template #body="{ data }">
            {{ data.notes || '-' }}
          </template>
        </Column>
        <Column field="confirmed" :header="t('timeOff.confirmed')">
          <template #body="{ data }">
            <span :class="['status-badge', data.confirmed ? 'status-confirmed' : 'status-unconfirmed']">
              {{ data.confirmed ? '✓ ' + t('timeOff.confirmedStatus') : '⏳ ' + t('timeOff.unconfirmedStatus') }}
            </span>
          </template>
        </Column>
        <Column :header="t('actions')">
          <template #body="{ data }">
            <div class="action-buttons">
              <Button
                icon="pi pi-pencil"
                text
                rounded
                size="small"
                @click="openEditDialog(data)"
              />
              <Button
                icon="pi pi-trash"
                text
                rounded
                size="small"
                severity="danger"
                @click="deleteTimeOff(data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Create/Edit Dialog -->
    <TimeOffQuickForm
      v-model:visible="dialogVisible"
      :time-off="timeOffToEdit"
      @saved="handleTimeOffSaved"
    />

    <!-- Edit Vacation Balance Dialog -->
    <Dialog
      v-model:visible="editBalanceDialogVisible"
      :header="t('vacationBalance.edit') + ' ' + selectedYear"
      :modal="true"
      :style="{ width: '90vw', maxWidth: '550px' }"
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
.time-off-view {
  padding: var(--tt-view-padding);
}

.page-title {
  margin: 0 0 1rem 0;
}

/* Year Filter - inline compact */
.filter-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 1rem;
  background: var(--p-surface-0);
  border: 1px solid var(--p-surface-border);
  border-radius: 0.5rem;
  margin-bottom: 1rem;
}

.filter-label {
  font-weight: 600;
  color: var(--p-text-color);
  font-size: 0.8125rem;
}

.filter-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.filter-btn {
  padding: 0.375rem 0.75rem;
  background: var(--p-surface-50);
  border: 1px solid var(--p-surface-border);
  border-radius: 0.375rem;
  color: var(--p-text-color);
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn:hover {
  background: var(--p-surface-100);
  border-color: var(--p-primary-color);
}

.filter-btn.active {
  background: var(--p-primary-color);
  border-color: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
  font-weight: 600;
}

/* Vacation Summary with Progress Bar */
.vacation-summary {
  background: var(--p-surface-0);
  border: 1px solid var(--p-surface-border);
  border-radius: 0.5rem;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
}

.vacation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.vacation-main {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.vacation-icon {
  font-size: 1.25rem;
}

.vacation-value {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--tt-emerald-from);
  line-height: 1;
}

.vacation-label {
  font-size: 0.8125rem;
  color: var(--tt-text-secondary);
}

.vacation-edit-btn {
  background: transparent;
  border: none;
  color: var(--tt-text-secondary);
  cursor: pointer;
  padding: 0.375rem;
  border-radius: 4px;
  transition: all 0.2s;
}

.vacation-edit-btn:hover {
  background: var(--p-surface-100);
  color: var(--p-primary-color);
}

/* Progress bar - matching VacationBalanceCard */
.progress-container {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.progress-bar {
  position: relative;
  height: 8px;
  background: var(--tt-bg-light);
  border-radius: 4px;
  overflow: hidden;
}

.progress-used {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, var(--tt-emerald-from) 0%, var(--tt-emerald-to) 100%);
  border-radius: 4px 0 0 4px;
  transition: width 0.3s ease;
}

.progress-planned {
  position: absolute;
  top: 0;
  height: 100%;
  background: rgba(16, 185, 129, 0.3);
  transition: width 0.3s ease, left 0.3s ease;
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  font-size: 0.6875rem;
  color: var(--tt-text-secondary);
}

/* Type Section */
.type-section {
  background: var(--p-surface-0);
  border: 1px solid var(--p-surface-border);
  border-radius: 0.5rem;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
}

.section-title {
  margin: 0 0 0.625rem 0;
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--tt-text-primary);
}

/* Type breakdown grid */
.type-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 0.5rem;
}

.type-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.5rem 0.375rem;
  border-radius: 0.375rem;
  transition: transform 0.2s, box-shadow 0.2s;
}

.type-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--tt-shadow-sm);
}

.type-emoji {
  font-size: 1.25rem;
  margin-bottom: 0.125rem;
}

.type-label {
  font-size: 0.6875rem;
  color: var(--tt-text-secondary);
  text-align: center;
  margin-bottom: 0.125rem;
  line-height: 1.2;
}

.type-value {
  font-size: 1rem;
  font-weight: 700;
}

/* Type card color variants */
.type-card.type-vacation {
  background: var(--tt-row-bg-vacation);
}
.type-card.type-vacation .type-value {
  color: var(--tt-emerald-to);
}

.type-card.type-sick {
  background: var(--tt-row-bg-sick);
}
.type-card.type-sick .type-value {
  color: var(--tt-coral-to);
}

.type-card.type-child-sick {
  background: var(--tt-row-bg-sick);
}
.type-card.type-child-sick .type-value {
  color: var(--tt-coral-to);
}

.type-card.type-personal {
  background: var(--tt-row-bg-personal);
}
.type-card.type-personal .type-value {
  color: var(--tt-cyan-to);
}

.type-card.type-education {
  background: var(--tt-row-bg-education);
}
.type-card.type-education .type-value {
  color: #6366f1;
}

/* Entries Section */
.entries-section {
  background: var(--p-surface-0);
  border: 1px solid var(--p-surface-border);
  border-radius: 0.5rem;
  padding: 0.75rem 1rem;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.625rem;
}

/* Table styling */
.time-off-table {
  font-size: 0.875rem;
}

.type-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 500;
}

.type-badge.type-vacation {
  background: var(--tt-row-bg-vacation);
  color: var(--tt-emerald-to);
}

.type-badge.type-sick {
  background: var(--tt-row-bg-sick);
  color: var(--tt-coral-to);
}

.type-badge.type-child-sick {
  background: var(--tt-row-bg-sick);
  color: var(--tt-coral-to);
}

.type-badge.type-personal {
  background: var(--tt-row-bg-personal);
  color: var(--tt-cyan-to);
}

.type-badge.type-education {
  background: var(--tt-row-bg-education);
  color: #6366f1;
}

.type-badge.type-public-holiday {
  background: var(--tt-row-bg-public-holiday);
  color: var(--tt-yellow-to);
}

.action-buttons {
  display: flex;
  gap: 0.125rem;
}

/* Status badges */
.status-badge {
  padding: 0.25rem 0.625rem;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 500;
  display: inline-block;
}

.status-confirmed {
  background: var(--tt-row-bg-vacation);
  color: var(--tt-emerald-to);
}

.status-unconfirmed {
  background: var(--p-surface-100);
  color: var(--p-text-muted-color);
}

/* Empty and loading states */
.loading-state,
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 1.5rem;
  color: var(--tt-text-secondary);
  font-size: 0.875rem;
}

/* Dialog styles */
.field small {
  display: block;
  margin-top: 0.25rem;
  color: #6c757d;
}

/* Responsive */
@media (max-width: 1024px) {
  .type-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .time-off-view {
    padding: 0.75rem;
  }

  .page-title {
    font-size: 1.375rem;
    margin-bottom: 0.75rem;
  }

  .filter-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
  }

  .vacation-summary {
    padding: 0.625rem 0.75rem;
  }

  .vacation-value {
    font-size: 1.5rem;
  }

  .type-section {
    padding: 0.625rem 0.75rem;
  }

  .type-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 0.375rem;
  }

  .type-card {
    padding: 0.375rem 0.25rem;
  }

  .type-emoji {
    font-size: 1.125rem;
  }

  .type-value {
    font-size: 0.875rem;
  }

  .entries-section {
    padding: 0.625rem 0.75rem;
  }

  .section-header {
    flex-direction: column;
    align-items: stretch;
    gap: 0.5rem;
  }
}

@media (max-width: 480px) {
  .time-off-view {
    padding: 0.5rem;
  }

  .page-title {
    font-size: 1.25rem;
    margin-bottom: 0.5rem;
  }

  .filter-card {
    padding: 0.5rem;
  }

  .filter-btn {
    padding: 0.25rem 0.5rem;
    font-size: 0.75rem;
  }

  .vacation-summary {
    padding: 0.5rem;
  }

  .vacation-icon {
    font-size: 1rem;
  }

  .vacation-value {
    font-size: 1.375rem;
  }

  .vacation-label {
    font-size: 0.75rem;
  }

  .progress-labels {
    font-size: 0.625rem;
  }

  .type-section {
    padding: 0.5rem;
  }

  .section-title {
    font-size: 0.875rem;
    margin-bottom: 0.5rem;
  }

  .type-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 0.25rem;
  }

  .type-card {
    padding: 0.375rem 0.25rem;
  }

  .type-emoji {
    font-size: 1rem;
  }

  .type-label {
    font-size: 0.625rem;
  }

  .type-value {
    font-size: 0.8125rem;
  }

  .entries-section {
    padding: 0.5rem;
  }

  .loading-state,
  .empty-state {
    padding: 1rem;
  }
}
</style>
