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
          :value="sortedWorkingDays"
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
              <Checkbox v-model="data.isWorkingDay" :binary="true" @change="handleFieldChange(data)" />
            </template>
          </Column>

          <Column field="startTime" :header="t('workingHours.startTime')" class="col-time">
            <template #body="{ data }">
              <InputText
                v-model="data.startTime"
                type="time"
                :disabled="!data.isWorkingDay"
                @change="handleFieldChange(data)"
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
                @change="handleFieldChange(data)"
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
                @update:model-value="handleFieldChange(data)"
                class="compact-input-number"
              />
            </template>
          </Column>

          <Column field="hours" :header="t('workingHours.hours')" class="col-hours">
            <template #body="{ data }">
              <InputNumber
                v-model="data.hours"
                :min="0"
                :max="24"
                :disabled="!data.isWorkingDay || hasTimeValues(data)"
                showButtons
                @update:model-value="handleFieldChange(data)"
                class="compact-input-number"
              />
            </template>
          </Column>
        </DataTable>
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
          <Column :header="t('recurringOffDays.exemptions')">
            <template #body="{ data }">
              <Button
                :label="t('recurringOffDays.manageExemptions')"
                icon="pi pi-calendar-minus"
                class="p-button-text p-button-sm"
                @click="openExemptionsDialog(data)"
              />
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

    <!-- Exemptions Dialog -->
    <Dialog
      v-model:visible="exemptionsDialogVisible"
      :header="t('recurringOffDays.exemptions') + (selectedOffDayForExemptions?.description ? ` - ${selectedOffDayForExemptions.description}` : '')"
      :modal="true"
      :style="{ width: '90vw', maxWidth: '700px' }"
      :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
    >
      <div class="exemptions-content">
        <!-- Add Exemption Form -->
        <div class="add-exemption-form">
          <div class="form-row">
            <div class="field">
              <label>{{ t('recurringOffDays.exemptionDate') }}</label>
              <DatePicker
                v-model="newExemptionDate"
                :placeholder="t('recurringOffDays.selectExemptionDate')"
              />
            </div>
            <div class="field flex-grow">
              <label>{{ t('recurringOffDays.exemptionReason') }}</label>
              <InputText
                v-model="newExemptionReason"
                :placeholder="t('recurringOffDays.exemptionReasonPlaceholder')"
              />
            </div>
            <div class="field-button">
              <Button
                :label="t('add')"
                icon="pi pi-plus"
                :disabled="!newExemptionDate"
                @click="addExemption"
              />
            </div>
          </div>
        </div>

        <!-- Exemptions List -->
        <div class="exemptions-list">
          <DataTable
            :value="exemptions"
            :loading="isLoadingExemptions"
            responsive-layout="scroll"
            size="small"
          >
            <Column field="exemptionDate" :header="t('recurringOffDays.exemptionDate')">
              <template #body="{ data }">
                {{ formatDisplayDate(data.exemptionDate) }}
              </template>
            </Column>
            <Column field="reason" :header="t('recurringOffDays.exemptionReason')">
              <template #body="{ data }">
                {{ data.reason || '-' }}
              </template>
            </Column>
            <Column :header="t('actions')" style="width: 80px">
              <template #body="{ data }">
                <Button
                  icon="pi pi-trash"
                  class="p-button-text p-button-danger p-button-sm"
                  @click="deleteExemption(data)"
                />
              </template>
            </Column>
            <template #empty>
              <div class="text-center py-4 text-gray-500">
                {{ t('recurringOffDays.noExemptions') }}
              </div>
            </template>
          </DataTable>
        </div>
      </div>
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
import { RecurringOffDaysService, RecurringOffDayExemptionsService, WorkingHoursService } from '@/api/generated'
import type { WorkingDayConfig, RecurringOffDayResponse, CreateRecurringOffDayRequest, UpdateRecurringOffDayRequest, RecurringOffDayExemptionResponse, CreateRecurringOffDayExemptionRequest } from '@/api/generated'
import { useUndoDelete } from '@/composables/useUndoDelete'

const { t } = useI18n()
const toast = useToast()

const { deleteWithUndo } = useUndoDelete()

// ===== Working Hours State =====
const isLoadingWorkingHours = ref(false)
const workingDays = ref<WorkingDayConfig[]>([])
const sortedWorkingDays = computed(() =>
  [...workingDays.value].sort((a, b) => (a.weekday || 0) - (b.weekday || 0))
)

// Map weekday number (1-7) to day name
const weekdayMap = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

// Calculate weekly sum of working hours
// Backend already returns net hours (hours minus break)
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

function hasPartialTimeValues(data: WorkingDayConfig): boolean {
  const hasStart = !!data.startTime
  const hasEnd = !!data.endTime
  return (hasStart && !hasEnd) || (!hasStart && hasEnd)
}

async function loadWorkingHours() {
  isLoadingWorkingHours.value = true
  try {
    const response = await WorkingHoursService.getWorkingHours()
    // Backend returns net hours (hours minus break) - use as-is
    workingDays.value = [...response.workingDays].sort((a, b) => (a.weekday || 0) - (b.weekday || 0))
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

// Auto-save on field change
async function handleFieldChange(data: WorkingDayConfig) {
  if (data.isWorkingDay && hasPartialTimeValues(data)) {
    return
  }

  try {
    // Save single day to backend
    const updated = await WorkingHoursService.updateWorkingDay(data.weekday, {
      weekday: data.weekday,
      hours: data.hours,
      isWorkingDay: data.isWorkingDay,
      startTime: data.startTime,
      endTime: data.endTime,
      breakMinutes: data.breakMinutes
    })

    // Backend returns net hours - update local state with backend response
    const index = workingDays.value.findIndex(d => d.weekday === data.weekday)
    if (index !== -1) {
      workingDays.value[index] = updated
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('workingHours.saveError'),
      life: 3000
    })
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

// ===== Exemptions State =====
const exemptionsDialogVisible = ref(false)
const selectedOffDayForExemptions = ref<RecurringOffDayResponse | null>(null)
const exemptions = ref<RecurringOffDayExemptionResponse[]>([])
const isLoadingExemptions = ref(false)
const newExemptionDate = ref<string | null>(null)
const newExemptionReason = ref('')

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
  await deleteWithUndo(
    offDay,
    async (id) => {
      await RecurringOffDaysService.deleteRecurringOffDay(id as number)
    },
    async () => {
      await loadOffDays()
    },
    (item) => {
      return t('recurringOffDays.deleteSuccess') + (item.description ? `: ${item.description}` : '')
    },
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
    {
      showUndoSuccessToast: true
    }
  )
}

// ===== Exemptions Functions =====
const openExemptionsDialog = async (offDay: RecurringOffDayResponse) => {
  selectedOffDayForExemptions.value = offDay
  newExemptionDate.value = null
  newExemptionReason.value = ''
  exemptionsDialogVisible.value = true
  await loadExemptions()
}

const loadExemptions = async () => {
  if (!selectedOffDayForExemptions.value?.id) return
  
  isLoadingExemptions.value = true
  try {
    const response = await RecurringOffDayExemptionsService.getExemptions(
      selectedOffDayForExemptions.value.id
    )
    exemptions.value = response
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('recurringOffDays.loadExemptionsError'),
      life: 3000
    })
    exemptions.value = []
  } finally {
    isLoadingExemptions.value = false
  }
}

const addExemption = async () => {
  if (!selectedOffDayForExemptions.value?.id || !newExemptionDate.value) return
  
  try {
    // Format date properly - DatePicker returns Date object, API expects YYYY-MM-DD string
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
    
    const request: CreateRecurringOffDayExemptionRequest = {
      exemptionDate: formatDate(newExemptionDate.value),
      reason: newExemptionReason.value || undefined
    }
    
    await RecurringOffDayExemptionsService.createExemption(
      selectedOffDayForExemptions.value.id,
      request
    )
    
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('recurringOffDays.exemptionCreated'),
      life: 3000
    })
    
    newExemptionDate.value = null
    newExemptionReason.value = ''
    await loadExemptions()
  } catch (error: any) {
    const errorMessage = error?.body?.message || t('recurringOffDays.exemptionCreateError')
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: errorMessage,
      life: 3000
    })
  }
}

const deleteExemption = async (exemption: RecurringOffDayExemptionResponse) => {
  if (!selectedOffDayForExemptions.value?.id) return
  
  try {
    await RecurringOffDayExemptionsService.deleteExemption(
      selectedOffDayForExemptions.value.id,
      exemption.id
    )
    
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('recurringOffDays.exemptionDeleted'),
      life: 3000
    })
    
    await loadExemptions()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('recurringOffDays.exemptionDeleteError'),
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

/* Exemptions dialog styles */
.exemptions-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.add-exemption-form {
  padding: 1rem;
  background: var(--tt-color-00-bg);
  border-radius: 8px;
  border: 1px solid var(--tt-color-10-border);
}

.add-exemption-form .form-row {
  display: flex;
  gap: 1rem;
  align-items: flex-end;
}

.add-exemption-form .field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.add-exemption-form .field.flex-grow {
  flex: 1;
}

.add-exemption-form .field-button {
  display: flex;
  align-items: flex-end;
  padding-bottom: 1px; /* Align with input border */
}

.add-exemption-form label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--tt-color-30-primary);
}

@media (max-width: 640px) {
  .add-exemption-form .form-row {
    flex-direction: column;
    align-items: stretch;
  }
  
  .add-exemption-form .field-button {
    margin-top: 0.5rem;
  }
  
  .add-exemption-form .field-button .p-button {
    width: 100%;
  }
}
</style>
