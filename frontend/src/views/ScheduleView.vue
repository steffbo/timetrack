<template>
  <div class="schedule-view">
    <h1 class="page-title">{{ t('schedule.pageTitle') }}</h1>

    <!-- Working Hours Section -->
    <Card class="section-card">
      <template #title>
        {{ t('workingHours.title') }}
      </template>
      <template #content>
        <DataTable :value="workingDays" :loading="isLoadingWorkingHours">
          <Column field="weekday" header="Tag">
            <template #body="{ data }">
              {{ getWeekdayName(data.weekday) }}
            </template>
          </Column>

          <Column field="isWorkingDay" :header="t('workingHours.isWorkingDay')">
            <template #body="{ data }">
              <Checkbox v-model="data.isWorkingDay" :binary="true" />
            </template>
          </Column>

          <Column field="startTime" :header="t('workingHours.startTime')">
            <template #body="{ data }">
              <InputText
                v-model="data.startTime"
                type="time"
                :disabled="!data.isWorkingDay"
                @change="handleTimeChange(data)"
                fluid
              />
            </template>
          </Column>

          <Column field="endTime" :header="t('workingHours.endTime')">
            <template #body="{ data }">
              <InputText
                v-model="data.endTime"
                type="time"
                :disabled="!data.isWorkingDay"
                @change="handleTimeChange(data)"
                fluid
              />
            </template>
          </Column>

          <Column field="breakMinutes" :header="t('workingHours.breakMinutes')" style="width: 110px">
            <template #body="{ data }">
              <InputNumber
                v-model="data.breakMinutes"
                :min="0"
                :max="480"
                :disabled="!data.isWorkingDay"
                suffix=" min"
                showButtons
              />
            </template>
          </Column>

          <Column field="hours" :header="t('workingHours.hours')" style="width: 100px">
            <template #body="{ data }">
              <InputNumber
                v-model="data.hours"
                :min="0"
                :max="24"
                :disabled="!data.isWorkingDay || hasTimeValues(data)"
                showButtons
              />
            </template>
          </Column>
        </DataTable>

        <div class="weekly-sum">
          <strong>{{ t('workingHours.weeklySum') }}:</strong>
          <span>{{ weeklySum.toFixed(2) }} {{ t('workingHours.hours') }}</span>
        </div>

        <div class="button-group">
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
                @click="confirmDelete(data)"
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
        <span>{{ t('recurringOffDays.deleteConfirm') }}</span>
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
          @click="deleteOffDay"
        />
      </template>
    </Dialog>
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

const { t } = useI18n()
const toast = useToast()

// ===== Working Hours State =====
const isLoadingWorkingHours = ref(false)
const isSavingWorkingHours = ref(false)
const workingDays = ref<WorkingDayConfig[]>([])

// Map weekday number (1-7) to day name
const weekdayMap = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

// Calculate weekly sum of working hours
const weeklySum = computed(() => {
  return workingDays.value
    .filter(day => day.isWorkingDay)
    .reduce((sum, day) => sum + (day.hours || 0), 0)
})

function getWeekdayName(weekday: number): string {
  return t(`workingHours.weekdays.${weekdayMap[weekday - 1]}`)
}

function hasTimeValues(data: WorkingDayConfig): boolean {
  return !!(data.startTime && data.endTime)
}

function handleTimeChange(data: WorkingDayConfig) {
  // Calculate hours from start and end time if both are set
  if (data.startTime && data.endTime) {
    const start = parseTime(data.startTime)
    const end = parseTime(data.endTime)

    if (start && end && end > start) {
      const diffMinutes = (end - start) / (1000 * 60)
      data.hours = Math.round((diffMinutes / 60) * 100) / 100
    }
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
    await apiClient.put(
      `/api/working-hours`,
      { workingDays: workingDays.value }
    )

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
const deleteDialogVisible = ref(false)
const editMode = ref(false)
const currentOffDay = ref<Partial<CreateRecurringOffDayRequest | UpdateRecurringOffDayRequest>>({
  recurrencePattern: 'EVERY_NTH_WEEK',
  weekday: 1,
  weekInterval: 4,
  isActive: true
})
const offDayToDelete = ref<RecurringOffDayResponse | null>(null)

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

const confirmDelete = (offDay: RecurringOffDayResponse) => {
  offDayToDelete.value = offDay
  deleteDialogVisible.value = true
}

const deleteOffDay = async () => {
  if (!offDayToDelete.value) return

  try {
    await RecurringOffDaysService.deleteRecurringOffDay(offDayToDelete.value.id)
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('recurringOffDays.deleteSuccess'),
      life: 3000
    })
    deleteDialogVisible.value = false
    await loadOffDays()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('recurringOffDays.deleteError'),
      life: 3000
    })
  }
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

/* All other shared styles moved to CSS modules */
</style>
