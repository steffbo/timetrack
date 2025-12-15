<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import Tag from 'primevue/tag'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import DatePicker from '@/components/common/DatePicker.vue'
import DateRangeFilter from '@/components/common/DateRangeFilter.vue'
import { TimeOffService, VacationBalanceService } from '@/api/generated'
import type { TimeOffResponse, CreateTimeOffRequest, UpdateTimeOffRequest, VacationBalanceResponse, UpdateVacationBalanceRequest } from '@/api/generated'
import { useAuth } from '@/composables/useAuth'

const { t } = useI18n()
const toast = useToast()
const { currentUser } = useAuth()

const timeOffs = ref<TimeOffResponse[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const editMode = ref(false)
const currentTimeOff = ref<Partial<CreateTimeOffRequest | UpdateTimeOffRequest>>({
  timeOffType: 'VACATION'
})
const timeOffToDelete = ref<TimeOffResponse | null>(null)

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

const timeOffTypeOptions = [
  { label: t('timeOff.type.VACATION'), value: 'VACATION' },
  { label: t('timeOff.type.SICK'), value: 'SICK' },
  { label: t('timeOff.type.PERSONAL'), value: 'PERSONAL' },
  { label: t('timeOff.type.PUBLIC_HOLIDAY'), value: 'PUBLIC_HOLIDAY' }
]

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
      console.error('Failed to load vacation balance:', error)
    }
  } finally {
    balanceLoading.value = false
  }
}

const calculateSickDays = async () => {
  try {
    const startDate = `${selectedYear.value}-01-01`
    const endDate = `${selectedYear.value}-12-31`

    const response = await TimeOffService.getTimeOffEntries(startDate, endDate)
    const sickEntries = response.filter(entry => entry.timeOffType === 'SICK')

    // Use the 'days' field which counts working days
    const total = sickEntries.reduce((sum, entry) => sum + entry.days, 0)
    sickDaysCount.value = total
  } catch (error) {
    console.error('Failed to calculate sick days:', error)
    sickDaysCount.value = 0
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
  await loadBalance()
  await calculateSickDays()
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
    await calculateSickDays()
  } catch (error: any) {
    console.error('Failed to load time offs:', error)
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error?.body?.message || t('timeOff.loadError'),
      life: 5000
    })
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  editMode.value = false
  const today = new Date()
  currentTimeOff.value = {
    timeOffType: 'VACATION',
    startDate: today,
    endDate: today
  }
  dialogVisible.value = true
}

const openEditDialog = (timeOff: TimeOffResponse) => {
  editMode.value = true
  currentTimeOff.value = {
    id: timeOff.id,
    timeOffType: timeOff.timeOffType,
    startDate: timeOff.startDate,
    endDate: timeOff.endDate,
    hoursPerDay: timeOff.hoursPerDay,
    notes: timeOff.notes
  }
  dialogVisible.value = true
}

const saveTimeOff = async () => {
  try {
    // Convert Date objects to ISO date strings (YYYY-MM-DD) without timezone shifts
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

    const requestData = {
      ...currentTimeOff.value,
      startDate: formatDate(currentTimeOff.value.startDate),
      endDate: formatDate(currentTimeOff.value.endDate)
    }

    if (editMode.value && requestData.id) {
      await TimeOffService.updateTimeOff(
        requestData.id,
        requestData as UpdateTimeOffRequest
      )
      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('timeOff.updateSuccess'),
        life: 3000
      })
    } else {
      await TimeOffService.createTimeOff(
        requestData as CreateTimeOffRequest
      )
      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('timeOff.createSuccess'),
        life: 3000
      })
    }
    dialogVisible.value = false
    await loadTimeOffs()
  } catch (error: any) {
    console.error('Failed to save time off:', error)
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error?.body?.message || (editMode.value ? t('timeOff.updateError') : t('timeOff.createError')),
      life: 5000
    })
  }
}

const confirmDelete = (timeOff: TimeOffResponse) => {
  timeOffToDelete.value = timeOff
  deleteDialogVisible.value = true
}

const deleteTimeOff = async () => {
  if (!timeOffToDelete.value) return

  try {
    await TimeOffService.deleteTimeOff(timeOffToDelete.value.id)
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('timeOff.deleteSuccess'),
      life: 3000
    })
    deleteDialogVisible.value = false
    await loadTimeOffs()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('timeOff.deleteError'),
      life: 3000
    })
  }
}

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

onMounted(() => {
  // Default filter: previous month + current month
  const now = new Date()
  const previousMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  const endOfCurrentMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0)

  const formatDate = (date: Date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  startDateFilter.value = formatDate(previousMonth)
  endDateFilter.value = formatDate(endOfCurrentMonth)
  loadBalance()
  loadTimeOffs()
})
</script>

<template>
  <div class="time-off-view">
    <!-- Statistics Cards -->
    <div class="stats-section mb-4">
      <div class="flex justify-content-between align-items-center mb-3">
        <h2 class="section-title">{{ t('timeOff.statistics') }}</h2>
        <div class="flex gap-2 align-items-center">
          <Select
            v-model="selectedYear"
            :options="years"
            @change="onYearChange"
            class="w-auto year-select"
          />
          <Button
            v-if="balance"
            :label="t('edit')"
            icon="pi pi-pencil"
            size="small"
            @click="openEditBalanceDialog"
          />
        </div>
      </div>

      <div v-if="balanceLoading" class="flex justify-content-center py-4">
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
      </div>

      <div v-else class="text-center py-3 text-gray-500">
        <i class="pi pi-info-circle text-2xl mb-2" />
        <p class="text-sm">{{ t('vacationBalance.noData') }}</p>
      </div>
    </div>

    <!-- Time Off Entries -->
    <div class="card">
      <div class="flex justify-content-between align-items-center mb-4">
        <h1>{{ t('timeOff.entries') }}</h1>
        <Button
          :label="t('timeOff.create')"
          icon="pi pi-plus"
          @click="openCreateDialog"
        />
      </div>

      <div class="filters mb-4">
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
            {{ new Date(data.startDate).toLocaleDateString('de-DE') }}
          </template>
        </Column>
        <Column field="endDate" :header="t('timeOff.endDate')" sortable>
          <template #body="{ data }">
            {{ new Date(data.endDate).toLocaleDateString('de-DE') }}
          </template>
        </Column>
        <Column field="days" :header="t('timeOff.days')">
          <template #body="{ data }">
            {{ data.days }}
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
                @click="confirmDelete(data)"
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
    <Dialog
      v-model:visible="dialogVisible"
      :header="editMode ? t('timeOff.edit') : t('timeOff.create')"
      :modal="true"
      :style="{ width: '600px' }"
    >
      <div class="p-fluid">
        <div class="field">
          <label for="timeOffType">{{ t('timeOff.type.label') }} *</label>
          <Select
            id="timeOffType"
            v-model="currentTimeOff.timeOffType"
            :options="timeOffTypeOptions"
            option-label="label"
            option-value="value"
          />
        </div>

        <div class="field">
          <label for="startDate">{{ t('timeOff.startDate') }} *</label>
          <DatePicker
            id="startDate"
            v-model="currentTimeOff.startDate"
          />
        </div>

        <div class="field">
          <label for="endDate">{{ t('timeOff.endDate') }} *</label>
          <DatePicker
            id="endDate"
            v-model="currentTimeOff.endDate"
          />
        </div>

        <div class="field">
          <label for="hoursPerDay">{{ t('timeOff.hoursPerDay') }}</label>
          <InputNumber
            id="hoursPerDay"
            v-model="currentTimeOff.hoursPerDay"
            :min="0"
            :max="24"
            :max-fraction-digits="2"
            suffix=" h"
          />
          <small>{{ t('timeOff.hoursPerDayHint') }}</small>
        </div>

        <div class="field">
          <label for="notes">{{ t('timeOff.notes') }}</label>
          <Textarea
            id="notes"
            v-model="currentTimeOff.notes"
            rows="3"
          />
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('cancel')"
          icon="pi pi-times"
          class="p-button-text"
          @click="dialogVisible = false"
        />
        <Button
          :label="t('save')"
          icon="pi pi-check"
          @click="saveTimeOff"
        />
      </template>
    </Dialog>

    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="deleteDialogVisible"
      :header="t('confirm')"
      :modal="true"
      :style="{ width: '450px' }"
    >
      <div class="flex align-items-center">
        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
        <span>{{ t('timeOff.deleteConfirm') }}</span>
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
          @click="deleteTimeOff"
        />
      </template>
    </Dialog>

    <!-- Edit Vacation Balance Dialog -->
    <Dialog
      v-model:visible="editBalanceDialogVisible"
      :header="t('vacationBalance.edit')"
      :modal="true"
      :style="{ width: '500px' }"
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

h1 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--tt-spacing-md);
}

.stat-unit {
  font-size: 0.875rem;
  color: #9ca3af;
  font-weight: 500;
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
  min-width: 100px;
}

.field small {
  display: block;
  margin-top: 0.25rem;
  color: #6c757d;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-value {
    font-size: 1.5rem;
  }
}

@media (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
