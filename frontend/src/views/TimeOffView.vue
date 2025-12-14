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
import DatePicker from '@/components/common/DatePicker.vue'
import { TimeOffService } from '@/api/generated'
import type { TimeOffResponse, CreateTimeOffRequest, UpdateTimeOffRequest } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()

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

const timeOffTypeOptions = [
  { label: t('timeOff.type.VACATION'), value: 'VACATION' },
  { label: t('timeOff.type.SICK'), value: 'SICK' },
  { label: t('timeOff.type.PERSONAL'), value: 'PERSONAL' },
  { label: t('timeOff.type.PUBLIC_HOLIDAY'), value: 'PUBLIC_HOLIDAY' }
]

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

const calculateDays = (startDate: string, endDate: string) => {
  const start = new Date(startDate)
  const end = new Date(endDate)
  const diffTime = Math.abs(end.getTime() - start.getTime())
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1
  return diffDays
}

onMounted(() => {
  // Default filter: current year
  const now = new Date()
  startDateFilter.value = `${now.getFullYear()}-01-01`
  endDateFilter.value = `${now.getFullYear()}-12-31`
  loadTimeOffs()
})
</script>

<template>
  <div class="time-off-view">
    <div class="card">
      <div class="flex justify-content-between align-items-center mb-4">
        <h1>{{ t('timeOff.title') }}</h1>
        <Button
          :label="t('timeOff.create')"
          icon="pi pi-plus"
          @click="openCreateDialog"
        />
      </div>

      <div class="filters mb-4">
        <div class="flex gap-3 align-items-end">
          <div class="flex-1">
            <label for="startDateFilter">{{ t('timeOff.startDate') }}</label>
            <DatePicker
              id="startDateFilter"
              v-model="startDateFilter"
              show-icon
            />
          </div>
          <div class="flex-1">
            <label for="endDateFilter">{{ t('timeOff.endDate') }}</label>
            <DatePicker
              id="endDateFilter"
              v-model="endDateFilter"
              show-icon
            />
          </div>
          <div>
            <Button
              :label="t('filter')"
              icon="pi pi-filter"
              @click="loadTimeOffs"
            />
          </div>
        </div>
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
            {{ calculateDays(data.startDate, data.endDate) }}
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
  </div>
</template>

<style scoped>
.time-off-view {
  padding: 2rem;
}

h1 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.filters label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.field {
  margin-bottom: 1.5rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.field small {
  display: block;
  margin-top: 0.25rem;
  color: #6c757d;
}
</style>
