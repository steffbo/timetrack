<template>
  <div class="schedule-view">
    <h1 class="page-title">{{ t('schedule.pageTitle') }}</h1>

    <!-- Working Hours Section -->
    <Card class="section-card">
      <template #title>
        <div class="card-title-row">
          <span>{{ t('workingHours.title') }}</span>
          <div class="weekly-sum-header">
            <strong>{{ t('workingHours.weeklySum') }}:</strong>
            <span>{{ weeklySum.toFixed(2) }} {{ t('workingHours.hours') }}</span>
          </div>
        </div>
      </template>
      <template #content>
        <DataTable
          :value="workingDays"
          :loading="isLoadingWorkingHours"
          responsive-layout="scroll"
          class="working-hours-table"
        >
          <Column field="weekday" header="Tag" class="col-weekday">
            <template #body="{ data }">
              {{ getWeekdayName(data.weekday) }}
            </template>
          </Column>

          <Column field="isWorkingDay" :header="t('workingHours.isWorkingDay')" class="col-active">
            <template #body="{ data }">
              <Checkbox v-model="data.isWorkingDay" :binary="true" />
            </template>
          </Column>

          <Column field="startTime" :header="t('workingHours.startTime')" class="col-time">
            <template #body="{ data }">
              <InputText
                v-model="data.startTime"
                type="time"
                :disabled="!data.isWorkingDay"
                @change="handleTimeChange(data)"
                fluid
                class="time-input"
              />
            </template>
          </Column>

          <Column field="endTime" :header="t('workingHours.endTime')" class="col-time">
            <template #body="{ data }">
              <InputText
                v-model="data.endTime"
                type="time"
                :disabled="!data.isWorkingDay"
                @change="handleTimeChange(data)"
                fluid
                class="time-input"
              />
            </template>
          </Column>

          <Column field="breakMinutes" :header="t('workingHours.breakMinutes')" class="col-break">
            <template #body="{ data }">
              <InputNumber
                v-model="data.breakMinutes"
                :min="0"
                :max="480"
                :disabled="!data.isWorkingDay"
                suffix=" min"
                showButtons
                @change="handleBreakChange(data)"
                class="compact-input-number"
              />
            </template>
          </Column>

          <Column field="hours" :header="t('workingHours.hours')" class="col-hours">
            <template #body="{ data }">
              <InputNumber
                :model-value="getNetHours(data)"
                @update:model-value="(value) => setNetHours(data, value)"
                :min="0"
                :max="24"
                :disabled="!data.isWorkingDay || hasTimeValues(data)"
                showButtons
                class="compact-input-number"
              />
            </template>
          </Column>
        </DataTable>

        <div class="button-group-right">
          <Button
            :label="t('workingHours.save')"
            :loading="isSavingWorkingHours"
            @click="handleSaveWorkingHours"
          />
        </div>
      </template>
    </Card>

    <!-- Recurring Off-Days Section -->
    <Card class="section-card">
      <template #title>
        {{ t('recurringOffDays.title') }}
      </template>
      <template #content>
        <div class="section-header">
          <Button
            :label="t('recurringOffDays.create')"
            icon="pi pi-plus"
            @click="openCreateDialog"
          />
        </div>
        <DataTable
          :value="offDays"
          :loading="isLoadingOffDays"
          striped-rows
          responsive-layout="scroll"
        >
          <Column field="isActive" :header="t('recurringOffDays.active')">
            <template #body="{ data }">
              <Tag :severity="data.isActive ? 'success' : 'secondary'">
                {{ data.isActive ? t('yes') : t('no') }}
              </Tag>
            </template>
          </Column>
          <Column field="pattern" :header="t('recurringOffDays.pattern.label')">
            <template #body="{ data }">
              {{ getPatternDescription(data) }}
            </template>
          </Column>
          <Column field="startDate" :header="t('recurringOffDays.startDate')">
            <template #body="{ data }">
              {{ formatDisplayDate(data.startDate) }}
            </template>
          </Column>
          <Column field="endDate" :header="t('recurringOffDays.endDate')">
            <template #body="{ data }">
              {{ data.endDate ? formatDisplayDate(data.endDate) : '-' }}
            </template>
          </Column>
          <Column field="description" :header="t('recurringOffDays.description')">
            <template #body="{ data }">
              {{ data.description || '-' }}
            </template>
          </Column>
          <Column :header="t('actions')">
            <template #body="{ data }">
              <Button
                icon="pi pi-pencil"
                class="p-button-text p-button-sm"
                @click="openEditDialog(data)"
              />
              <Button
                icon="pi pi-trash"
                class="p-button-text p-button-danger p-button-sm"
                @click="deleteOffDay(data)"
              />
            </template>
          </Column>
        </DataTable>

        <div v-if="offDays.length === 0 && !isLoadingOffDays" class="text-center py-4 text-gray-500">
          {{ t('recurringOffDays.noData') }}
        </div>
      </template>
    </Card>

    <!-- Create/Edit Dialog for Recurring Off-Days -->
    <Dialog
      v-model:visible="dialogVisible"
      :header="editMode ? t('recurringOffDays.edit') : t('recurringOffDays.create')"
      :modal="true"
      :style="{ width: '90vw', maxWidth: '600px' }"
      :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
    >
      <div class="p-fluid">
        <div class="field">
          <label for="pattern">{{ t('recurringOffDays.pattern.label') }} *</label>
          <Select
            id="pattern"
            v-model="currentOffDay.recurrencePattern"
            :options="patternOptions"
            option-label="label"
            option-value="value"
          />
        </div>

        <div class="field">
          <label for="weekday">{{ t('recurringOffDays.weekday') }} *</label>
          <Select
            id="weekday"
            v-model="currentOffDay.weekday"
            :options="weekdayOptions"
            option-label="label"
            option-value="value"
          />
        </div>

        <div v-if="currentOffDay.recurrencePattern === 'EVERY_NTH_WEEK'" class="field">
          <label for="weekInterval">{{ t('recurringOffDays.weekInterval') }} *</label>
          <InputNumber
            id="weekInterval"
            v-model="currentOffDay.weekInterval"
            :min="1"
            :max="52"
          />
        </div>

        <div v-if="currentOffDay.recurrencePattern === 'EVERY_NTH_WEEK'" class="field">
          <label for="referenceDate">
            {{ t('recurringOffDays.referenceDate') }} *
            <i
              class="pi pi-info-circle info-icon"
              v-tooltip.right="t('recurringOffDays.referenceDateTooltip')"
            />
          </label>
          <DatePicker
            id="referenceDate"
            v-model="currentOffDay.referenceDate"
          />
        </div>

        <div v-if="currentOffDay.recurrencePattern === 'NTH_WEEKDAY_OF_MONTH'" class="field">
          <label for="weekOfMonth">{{ t('recurringOffDays.weekOfMonth.label') }} *</label>
          <Select
            id="weekOfMonth"
            v-model="currentOffDay.weekOfMonth"
            :options="weekOfMonthOptions"
            option-label="label"
            option-value="value"
          />
        </div>

        <div class="field">
          <label for="startDate">
            {{ t('recurringOffDays.startDate') }} *
            <i
              class="pi pi-info-circle info-icon"
              v-tooltip.right="t('recurringOffDays.startDateTooltip')"
            />
          </label>
          <DatePicker
            id="startDate"
            v-model="currentOffDay.startDate"
          />
        </div>

        <div class="field">
          <label for="endDate">{{ t('recurringOffDays.endDate') }}</label>
          <DatePicker
            id="endDate"
            v-model="currentOffDay.endDate"
          />
        </div>

        <div class="field">
          <label for="description">{{ t('recurringOffDays.description') }}</label>
          <Textarea
            id="description"
            v-model="currentOffDay.description"
            rows="3"
          />
        </div>

        <div class="field-checkbox">
          <Checkbox
            id="isActive"
            v-model="currentOffDay.isActive"
            :binary="true"
          />
          <label for="isActive">{{ t('recurringOffDays.active') }}</label>
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
          @click="saveOffDay"
        />
      </template>
    </Dialog>

    <!-- Toast for undo delete -->
    <UndoDeleteToast :group="recurringOffDayUndo.undoGroup" :on-undo="undoRecurringOffDayDelete" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Checkbox from 'primevue/checkbox'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import Tag from 'primevue/tag'
import DatePicker from '@/components/common/DatePicker.vue'
import apiClient from '@/api/client'
import { RecurringOffDaysService } from '@/api/generated'
import type { WorkingDayConfig, RecurringOffDayResponse, CreateRecurringOffDayRequest, UpdateRecurringOffDayRequest } from '@/api/generated'
import { useUndoDelete } from '@/composables/useUndoDelete'
import Toast from 'primevue/toast'

const { t } = useI18n()
const toast = useToast()

// Undo delete composable
const recurringOffDayUndo = useUndoDelete<RecurringOffDayResponse>('delete-undo-recurring-offday')

// ===== Working Hours State =====
const isLoadingWorkingHours = ref(false)
const isSavingWorkingHours = ref(false)
const workingDays = ref<WorkingDayConfig[]>([])

// Map weekday number (1-7) to day name
const weekdayMap = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

// Calculate net hours (gross hours minus break)
function getNetHours(data: WorkingDayConfig): number {
  const grossHours = data.hours || 0
  const breakHours = (data.breakMinutes || 0) / 60.0
  return Math.max(0, grossHours - breakHours)
}

// Set net hours (add break back to get gross hours for backend)
function setNetHours(data: WorkingDayConfig, netHours: number | null) {
  if (netHours === null) {
    data.hours = 0
    return
  }
  const breakHours = (data.breakMinutes || 0) / 60.0
  data.hours = netHours + breakHours
}

// Calculate weekly sum of working hours (using net hours)
const weeklySum = computed(() => {
  return workingDays.value
    .filter(day => day.isWorkingDay)
    .reduce((sum, day) => sum + getNetHours(day), 0)
})

function getWeekdayName(weekday: number): string {
  return t(`workingHours.weekdays.${weekdayMap[weekday - 1]}`)
}

function hasTimeValues(data: WorkingDayConfig): boolean {
  return !!(data.startTime && data.endTime)
}

function handleTimeChange(data: WorkingDayConfig) {
  // Calculate hours from start and end time if both are set
  // Subtract break minutes to get net working hours
  if (data.startTime && data.endTime) {
    const start = parseTime(data.startTime)
    const end = parseTime(data.endTime)

    if (start && end && end > start) {
      const diffMinutes = (end - start) / (1000 * 60)
      const breakMinutes = data.breakMinutes || 0
      const netMinutes = diffMinutes - breakMinutes
      data.hours = Math.max(0, Math.round((netMinutes / 60) * 100) / 100)
    }
  }
}

function handleBreakChange(data: WorkingDayConfig) {
  // Recalculate hours when break minutes change
  if (data.startTime && data.endTime) {
    handleTimeChange(data)
  }
}

function parseTime(timeStr: string): number | null {
  if (!timeStr) return null
  const [hours, minutes] = timeStr.split(':').map(Number)
  if (isNaN(hours) || isNaN(minutes) || hours === undefined || minutes === undefined) return null
  return new Date(1970, 0, 1, hours, minutes).getTime()
}

async function loadWorkingHours() {
  isLoadingWorkingHours.value = true
  try {
    const response = await apiClient.get(`/api/working-hours`)
    workingDays.value = response.data.workingDays
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('workingHours.saveError'),
      life: 3000
    })
  } finally {
    isLoadingWorkingHours.value = false
  }
}

async function handleSaveWorkingHours() {
  isSavingWorkingHours.value = true
  try {
    // Prepare data for saving: if times are not set, ensure hours includes break (gross hours)
    // If times are set, backend will recalculate net hours anyway
    const dataToSave = workingDays.value.map(day => {
      const dayCopy = { ...day }
      // If no times are set and user edited hours directly, we need to ensure backend gets gross hours
      // But since we're displaying net hours and the backend expects gross when no times are set,
      // we need to add break back to the hours value
      if (!day.startTime && !day.endTime && day.hours !== undefined) {
        // The hours value in our model is already gross (because setNetHours adds break back)
        // So we can use it as-is
      }
      return dayCopy
    })

    const response = await apiClient.put(
      `/api/working-hours`,
      { workingDays: dataToSave }
    )

    // Use the updated working hours returned by the PUT request
    workingDays.value = response.data.workingDays

    toast.add({
      severity: 'success',
      summary: t('workingHours.saveSuccess'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('workingHours.saveError'),
      life: 3000
    })
  } finally {
    isSavingWorkingHours.value = false
  }
}

// ===== Recurring Off-Days State =====
const offDays = ref<RecurringOffDayResponse[]>([])
const isLoadingOffDays = ref(false)
const dialogVisible = ref(false)
const editMode = ref(false)
const currentOffDay = ref<Partial<CreateRecurringOffDayRequest | UpdateRecurringOffDayRequest>>({
  recurrencePattern: 'EVERY_NTH_WEEK',
  weekday: 1,
  weekInterval: 4,
  isActive: true
})

const weekdayOptions = [
  { label: t('weekday.monday'), value: 1 },
  { label: t('weekday.tuesday'), value: 2 },
  { label: t('weekday.wednesday'), value: 3 },
  { label: t('weekday.thursday'), value: 4 },
  { label: t('weekday.friday'), value: 5 },
  { label: t('weekday.saturday'), value: 6 },
  { label: t('weekday.sunday'), value: 7 }
]

const patternOptions = [
  { label: t('recurringOffDays.pattern.EVERY_NTH_WEEK'), value: 'EVERY_NTH_WEEK' },
  { label: t('recurringOffDays.pattern.NTH_WEEKDAY_OF_MONTH'), value: 'NTH_WEEKDAY_OF_MONTH' }
]

const weekOfMonthOptions = [
  { label: t('recurringOffDays.weekOfMonth.1'), value: 1 },
  { label: t('recurringOffDays.weekOfMonth.2'), value: 2 },
  { label: t('recurringOffDays.weekOfMonth.3'), value: 3 },
  { label: t('recurringOffDays.weekOfMonth.4'), value: 4 },
  { label: t('recurringOffDays.weekOfMonth.5'), value: 5 }
]

const loadOffDays = async () => {
  isLoadingOffDays.value = true
  try {
    const response = await RecurringOffDaysService.getRecurringOffDays()
    offDays.value = response
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('recurringOffDays.loadError'),
      life: 3000
    })
  } finally {
    isLoadingOffDays.value = false
  }
}

const openCreateDialog = () => {
  editMode.value = false
  currentOffDay.value = {
    recurrencePattern: 'EVERY_NTH_WEEK',
    weekday: 1,
    weekInterval: 4,
    isActive: true,
    startDate: new Date().toISOString().split('T')[0]
  }
  dialogVisible.value = true
}

const openEditDialog = (offDay: RecurringOffDayResponse) => {
  editMode.value = true
  currentOffDay.value = {
    id: offDay.id,
    recurrencePattern: offDay.recurrencePattern,
    weekday: offDay.weekday,
    weekInterval: offDay.weekInterval,
    referenceDate: offDay.referenceDate,
    weekOfMonth: offDay.weekOfMonth,
    startDate: offDay.startDate,
    endDate: offDay.endDate,
    isActive: offDay.isActive,
    description: offDay.description
  }
  dialogVisible.value = true
}

const saveOffDay = async () => {
  try {
    const requestData = { ...currentOffDay.value }

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

    requestData.startDate = formatDate(requestData.startDate)
    requestData.endDate = formatDate(requestData.endDate)
    requestData.referenceDate = formatDate(requestData.referenceDate)

    // Clean up fields based on pattern type to satisfy database constraints
    if (requestData.recurrencePattern === 'EVERY_NTH_WEEK') {
      delete requestData.weekOfMonth
    } else if (requestData.recurrencePattern === 'NTH_WEEKDAY_OF_MONTH') {
      delete requestData.weekInterval
      delete requestData.referenceDate
    }

    if (editMode.value && requestData.id) {
      await RecurringOffDaysService.updateRecurringOffDay(
        requestData.id,
        requestData as UpdateRecurringOffDayRequest
      )
      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('recurringOffDays.updateSuccess'),
        life: 3000
      })
    } else {
      await RecurringOffDaysService.createRecurringOffDay(
        requestData as CreateRecurringOffDayRequest
      )
      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('recurringOffDays.createSuccess'),
        life: 3000
      })
    }
    dialogVisible.value = false
    await loadOffDays()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: editMode.value ? t('recurringOffDays.updateError') : t('recurringOffDays.createError'),
      life: 3000
    })
  }
}

const deleteOffDay = async (offDay: RecurringOffDayResponse) => {
  await recurringOffDayUndo.deleteWithUndo(
    offDay,
    async (id) => {
      await RecurringOffDaysService.deleteRecurringOffDay(id as number)
    },
    async () => {
      await loadOffDays()
    },
    (item) => {
      return t('recurringOffDays.deleteSuccess') + (item.description ? `: ${item.description}` : '')
    }
  )
}

const undoRecurringOffDayDelete = async () => {
  await recurringOffDayUndo.undoDelete(
    async (item) => {
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

      const requestData: CreateRecurringOffDayRequest = {
        recurrencePattern: item.recurrencePattern,
        weekday: item.weekday,
        startDate: item.startDate,
        weekInterval: item.weekInterval,
        referenceDate: formatDate(item.referenceDate),
        weekOfMonth: item.weekOfMonth,
        endDate: formatDate(item.endDate),
        description: item.description
      }

      // Clean up fields based on pattern type
      if (requestData.recurrencePattern === 'EVERY_NTH_WEEK') {
        delete (requestData as any).weekOfMonth
      } else if (requestData.recurrencePattern === 'NTH_WEEKDAY_OF_MONTH') {
        delete (requestData as any).weekInterval
        delete (requestData as any).referenceDate
      }

      await RecurringOffDaysService.createRecurringOffDay(requestData)
    },
    async () => {
      await loadOffDays()
    }
  )
}

const getWeekdayLabel = (weekday: number) => {
  const option = weekdayOptions.find(w => w.value === weekday)
  return option ? option.label : ''
}

const getPatternDescription = (offDay: RecurringOffDayResponse) => {
  if (offDay.recurrencePattern === 'EVERY_NTH_WEEK') {
    return t('recurringOffDays.patternDesc.everyNthWeek', {
      n: offDay.weekInterval,
      weekday: getWeekdayLabel(offDay.weekday)
    })
  } else {
    return t('recurringOffDays.patternDesc.nthWeekdayOfMonth', {
      n: offDay.weekOfMonth,
      weekday: getWeekdayLabel(offDay.weekday)
    })
  }
}

const formatDisplayDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const year = date.getFullYear()
  return `${day}.${month}.${year}`
}

onMounted(async () => {
  await loadWorkingHours()
  await loadOffDays()
})
</script>

<style scoped>
/* Using shared layout, form, utility, and datatable styles */
.schedule-view {
  padding: var(--tt-view-padding);
}

@media (max-width: 768px) {
  .schedule-view {
    padding: var(--tt-view-padding-mobile);
  }
}

@media (max-width: 480px) {
  .schedule-view {
    padding: var(--tt-view-padding-xs);
  }
}

.compact-input-number {
  max-width: 100%;
}

.working-hours-table {
  table-layout: auto;
}

:deep(.col-weekday) {
  min-width: 100px;
}

:deep(.col-active) {
  width: 60px;
  text-align: center;
}

:deep(.col-time) {
  width: 110px;
}

:deep(.col-break) {
  width: 110px;
  min-width: 110px;
}

:deep(.col-hours) {
  width: 90px;
  min-width: 90px;
}

:deep(.p-datatable-tbody > tr > td) {
  padding: 0.5rem 0.25rem !important;
}

:deep(.compact-input-number.p-inputnumber) {
  width: 100% !important;
}

:deep(.compact-input-number .p-inputnumber-input) {
  width: 100% !important;
  min-width: 50px !important;
  padding-left: 0.4rem !important;
  padding-right: 0.4rem !important;
}

:deep(.compact-input-number .p-inputnumber-button) {
  width: 1.75rem !important;
  min-width: 1.75rem !important;
  flex-shrink: 0 !important;
}

:deep(.time-input.p-inputtext) {
  padding-left: 0.4rem !important;
  padding-right: 0.4rem !important;
  min-width: 0 !important;
}

/* Card title row with weekly sum */
.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 1rem;
}

.weekly-sum-header {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  font-size: 1rem;
  color: var(--tt-color-30-primary);
}

.weekly-sum-header strong {
  font-weight: 600;
}

/* Button group aligned to the right */
.button-group-right {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--tt-spacing-md);
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .card-title-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .weekly-sum-header {
    font-size: 0.9rem;
  }
}
</style>
