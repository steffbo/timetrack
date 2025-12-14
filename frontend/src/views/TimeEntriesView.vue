<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import Textarea from 'primevue/textarea'
import Tag from 'primevue/tag'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import DatePicker from '@/components/common/DatePicker.vue'
import DateTimePicker from '@/components/common/DateTimePicker.vue'
import DateRangeFilter from '@/components/common/DateRangeFilter.vue'
import { TimeEntriesService, WorkingHoursService, TimeOffService, RecurringOffDaysService } from '@/api/generated'
import type { TimeEntryResponse, ClockInRequest, ClockOutRequest, UpdateTimeEntryRequest, CreateTimeEntryRequest, TimeOffResponse, RecurringOffDayResponse, WorkingHoursResponse } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()

const timeEntries = ref<TimeEntryResponse[]>([])
const timeOffEntries = ref<TimeOffResponse[]>([])
const recurringOffDays = ref<RecurringOffDayResponse[]>([])
const workingHours = ref<WorkingHoursResponse | null>(null)
const showTimeOff = ref(false)
const loading = ref(false)

// Combined entries for display
type DisplayEntry = {
  type: 'work' | 'timeoff' | 'recurring-off' | 'weekend'
  date: string
  data: TimeEntryResponse | TimeOffResponse | RecurringOffDayResponse | { description: string }
}

const displayEntries = computed<DisplayEntry[]>(() => {
  const entries: DisplayEntry[] = []
  const dateMap = new Map<string, boolean>() // Track dates with work entries

  // Add work entries
  timeEntries.value.forEach(entry => {
    const date = entry.entryDate || ''
    dateMap.set(date, true)
    entries.push({
      type: 'work',
      date,
      data: entry
    })
  })

  // Add time-off entries if toggle is enabled
  if (showTimeOff.value) {
    timeOffEntries.value.forEach(timeOff => {
      // Create entries for each day in the time-off range
      const start = new Date(timeOff.startDate)
      const end = new Date(timeOff.endDate)

      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        const dateStr = d.toISOString().split('T')[0]
        // Only add if no work entry exists for this date
        if (!dateMap.has(dateStr)) {
          entries.push({
            type: 'timeoff',
            date: dateStr,
            data: timeOff
          })
        }
      }
    })

    // Add recurring off-days for the date range
    if (recurringOffDays.value.length > 0 && startDateFilter.value && endDateFilter.value) {
      const start = new Date(startDateFilter.value)
      const end = new Date(endDateFilter.value)

      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        const dateStr = d.toISOString().split('T')[0]
        const dayOfWeek = d.getDay() === 0 ? 7 : d.getDay() // Convert to 1=Mon, 7=Sun

        // Check if this date matches any recurring off-day
        recurringOffDays.value.forEach(rod => {
          if (rod.weekday === dayOfWeek && !dateMap.has(dateStr)) {
            // Check if it matches the recurrence pattern
            if (rod.recurrencePattern === 'EVERY_NTH_WEEK') {
              // For now, we'll show all matching weekdays if it's an EVERY_NTH_WEEK pattern
              // A more sophisticated implementation would check the week intervals
              entries.push({
                type: 'recurring-off',
                date: dateStr,
                data: rod
              })
              dateMap.set(dateStr, true)
            } else if (rod.recurrencePattern === 'NTH_WEEKDAY_OF_MONTH') {
              // Check if it's the Nth occurrence of this weekday in the month
              const nthOccurrence = Math.ceil(d.getDate() / 7)
              if (rod.intervalValue === nthOccurrence) {
                entries.push({
                  type: 'recurring-off',
                  date: dateStr,
                  data: rod
                })
                dateMap.set(dateStr, true)
              }
            }
          }
        })
      }
    }

    // Add weekend days
    if (workingHours.value && startDateFilter.value && endDateFilter.value) {
      const start = new Date(startDateFilter.value)
      const end = new Date(endDateFilter.value)

      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        const dateStr = d.toISOString().split('T')[0]
        const dayOfWeek = d.getDay() === 0 ? 7 : d.getDay()

        // Check if this day is not a working day
        const workingDay = workingHours.value.workingDays?.find(wd => wd.weekday === dayOfWeek)
        if (!workingDay?.isWorkingDay && !dateMap.has(dateStr)) {
          entries.push({
            type: 'weekend',
            date: dateStr,
            data: { description: t('timeEntries.weekend') }
          })
          dateMap.set(dateStr, true)
        }
      }
    }
  }

  // Sort by date descending
  return entries.sort((a, b) => b.date.localeCompare(a.date))
})
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
const manualEntryDate = ref<Date>(new Date())  // Separate date field
const manualEntryStartTime = ref<Date>(new Date())  // Start time
const manualEntryEndTime = ref<Date>(new Date())    // End time
const timeEntryToDelete = ref<TimeEntryResponse | null>(null)
const useDefaultHours = ref(false)  // Toggle for using default working hours
const hasWorkingHoursForSelectedDay = ref(true)  // Track if selected day has working hours
const cachedWorkingHours = ref<WorkingHoursResponse | null>(null)  // Cache working hours
// Store the values when default hours are applied, so they persist when toggling
const savedStartTime = ref<Date | null>(null)
const savedEndTime = ref<Date | null>(null)

// Date range filter - default to previous month start and current month end
const now = new Date()
const startDateFilter = ref<string>(new Date(now.getFullYear(), now.getMonth() - 1, 1).toISOString().split('T')[0])
const endDateFilter = ref<string>(new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().split('T')[0])

// Only WORK type supported - absences are tracked via TimeOff entity
const entryTypeOptions = [
  { label: t('timeEntries.type.WORK'), value: 'WORK' }
]

const loadTimeEntries = async () => {
  loading.value = true
  try {
    const response = await TimeEntriesService.getTimeEntries(startDateFilter.value, endDateFilter.value)
    timeEntries.value = response

    // Check if there's an active entry
    activeEntry.value = response.find(entry => entry.isActive) || null

    // Load time-off entries if toggle is enabled
    if (showTimeOff.value) {
      await loadTimeOff()
    }
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

const loadTimeOff = async () => {
  try {
    const response = await TimeOffService.getTimeOffEntries(startDateFilter.value, endDateFilter.value)
    timeOffEntries.value = response
  } catch (error) {
    console.error('Error loading time-off entries:', error)
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('timeEntries.timeOffLoadError'),
      life: 3000
    })
  }
}

const loadRecurringOffDays = async () => {
  try {
    const response = await RecurringOffDaysService.getRecurringOffDays()
    recurringOffDays.value = response
  } catch (error) {
    console.error('Error loading recurring off-days:', error)
  }
}

const loadWorkingHours = async () => {
  try {
    const response = await WorkingHoursService.getWorkingHours()
    workingHours.value = response
  } catch (error) {
    console.error('Error loading working hours:', error)
  }
}

const toggleTimeOff = async () => {
  showTimeOff.value = !showTimeOff.value
  if (showTimeOff.value && timeOffEntries.value.length === 0) {
    loading.value = true
    try {
      await Promise.all([
        loadTimeOff(),
        loadRecurringOffDays(),
        loadWorkingHours()
      ])
    } finally {
      loading.value = false
    }
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
  const now = new Date()

  // Initialize date field
  manualEntryDate.value = new Date(now)

  // Initialize time fields (default to current time and 1 hour later)
  manualEntryStartTime.value = new Date(now)

  const oneHourLater = new Date(now)
  oneHourLater.setHours(now.getHours() + 1)
  manualEntryEndTime.value = oneHourLater

  newManualEntry.value = {
    entryType: 'WORK' as any,  // Only WORK supported
    breakMinutes: 0,
    notes: ''
  }

  useDefaultHours.value = false
  hasWorkingHoursForSelectedDay.value = true  // Reset the flag
  savedStartTime.value = null  // Clear saved times
  savedEndTime.value = null
  manualEntryDialogVisible.value = true
}

const applyDefaultWorkingHours = async (selectedDate: Date) => {
  try {
    // Use cached working hours if available, otherwise fetch
    let workingHoursResponse = cachedWorkingHours.value
    if (!workingHoursResponse) {
      workingHoursResponse = await WorkingHoursService.getWorkingHours()
      cachedWorkingHours.value = workingHoursResponse
    }

    // Get day of week (1=Monday, 7=Sunday)
    const dayOfWeek = selectedDate.getDay() === 0 ? 7 : selectedDate.getDay()

    // Find working hours for selected day
    const dayWorkingHours = workingHoursResponse.workingDays.find(
      wd => wd.weekday === dayOfWeek
    )

    if (!dayWorkingHours || !dayWorkingHours.isWorkingDay) {
      hasWorkingHoursForSelectedDay.value = false
      toast.add({
        severity: 'warn',
        summary: t('warning'),
        detail: t('timeEntries.noWorkingHoursForDay'),
        life: 3000
      })
      return
    }

    hasWorkingHoursForSelectedDay.value = true

    // Set time fields from working hours configuration
    const [startHour, startMin] = dayWorkingHours.startTime.split(':').map(Number)
    const [endHour, endMin] = dayWorkingHours.endTime.split(':').map(Number)

    const startTime = new Date()
    startTime.setHours(startHour, startMin, 0, 0)
    manualEntryStartTime.value = startTime
    savedStartTime.value = new Date(startTime) // Save a copy

    const endTime = new Date()
    endTime.setHours(endHour, endMin, 0, 0)
    manualEntryEndTime.value = endTime
    savedEndTime.value = new Date(endTime) // Save a copy

    // Remove success toast - user will get it after clicking save
  } catch (error: any) {
    hasWorkingHoursForSelectedDay.value = false
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('timeEntries.defaultHoursError'),
      life: 3000
    })
  }
}

const onDateChange = (date: Date) => {
  if (useDefaultHours.value && date) {
    applyDefaultWorkingHours(date)
  }
}

const onUseDefaultHoursChange = async () => {
  if (useDefaultHours.value && manualEntryDate.value) {
    // Apply default hours immediately when checkbox is checked
    await applyDefaultWorkingHours(manualEntryDate.value)
  } else if (!useDefaultHours.value && savedStartTime.value && savedEndTime.value) {
    // When unchecking, restore the saved values that were set by default hours
    // This ensures the time pickers show the correct times instead of reverting to initial values
    manualEntryStartTime.value = new Date(savedStartTime.value)
    manualEntryEndTime.value = new Date(savedEndTime.value)
  }
}

const createQuickWorkEntry = async () => {
  try {
    loading.value = true
    // Get working hours configuration
    const workingHoursResponse = await WorkingHoursService.getWorkingHours()

    // Get current day of week (1=Monday, 7=Sunday)
    const today = new Date()
    const dayOfWeek = today.getDay() === 0 ? 7 : today.getDay()

    // Find working hours for today
    const todayWorkingHours = workingHoursResponse.workingDays.find(
      wd => wd.weekday === dayOfWeek
    )

    if (!todayWorkingHours || !todayWorkingHours.isWorkingDay) {
      toast.add({
        severity: 'warn',
        summary: t('warning'),
        detail: t('timeEntries.noWorkingHoursForToday'),
        life: 3000
      })
      return
    }

    // Parse start and end times
    const [startHour, startMin] = todayWorkingHours.startTime.split(':').map(Number)
    const [endHour, endMin] = todayWorkingHours.endTime.split(':').map(Number)

    const clockIn = new Date(today)
    clockIn.setHours(startHour, startMin, 0, 0)

    const clockOut = new Date(today)
    clockOut.setHours(endHour, endMin, 0, 0)

    const request: CreateTimeEntryRequest = {
      clockIn: clockIn.toISOString(),
      clockOut: clockOut.toISOString(),
      breakMinutes: 0,
      entryType: 'WORK',
      notes: ''
    }

    await TimeEntriesService.createTimeEntry(request)

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('timeEntries.quickEntryCreated'),
      life: 3000
    })

    await loadTimeEntries()
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('timeEntries.quickEntryError'),
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const createManualEntry = async () => {
  try {
    // Combine date and time fields into ISO datetime strings
    const date = manualEntryDate.value

    const clockIn = new Date(date)
    clockIn.setHours(manualEntryStartTime.value.getHours(), manualEntryStartTime.value.getMinutes(), 0, 0)

    const clockOut = new Date(date)
    clockOut.setHours(manualEntryEndTime.value.getHours(), manualEntryEndTime.value.getMinutes(), 0, 0)

    const request: CreateTimeEntryRequest = {
      clockIn: clockIn.toISOString(),
      clockOut: clockOut.toISOString(),
      breakMinutes: newManualEntry.value.breakMinutes || 0,
      entryType: 'WORK',  // Always WORK
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

const isActiveEntry = (entry: TimeEntryResponse) => {
  return entry.isActive
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
            :label="t('timeEntries.quickWorkEntry')"
            icon="pi pi-bolt"
            severity="success"
            @click="createQuickWorkEntry"
            outlined
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
        <DateRangeFilter
          v-model:start-date="startDateFilter"
          v-model:end-date="endDateFilter"
          @filter="loadTimeEntries()"
        >
          <template #extra-actions>
            <Button
              :label="showTimeOff ? t('timeEntries.hideTimeOff') : t('timeEntries.showTimeOff')"
              :icon="showTimeOff ? 'pi pi-eye-slash' : 'pi pi-eye'"
              severity="secondary"
              outlined
              @click="toggleTimeOff()"
            />
          </template>
        </DateRangeFilter>
      </div>

      <!-- Combined Entries Table -->
      <DataTable
        :value="displayEntries"
        :loading="loading"
        striped-rows
        responsive-layout="scroll"
        :empty-message="t('timeEntries.noEntries')"
      >
        <Column field="date" :header="t('timeEntries.date')">
          <template #body="{ data: entry }">
            {{ formatDate(entry.date) }}
          </template>
        </Column>
        <Column :header="t('timeEntries.type.label')">
          <template #body="{ data: entry }">
            <Tag
              v-if="entry.type === 'work'"
              :value="t('timeEntries.type.WORK')"
              severity="info"
            />
            <Tag
              v-else-if="entry.type === 'timeoff'"
              :value="t(`timeOff.type.${(entry.data as TimeOffResponse).timeOffType}`)"
              severity="success"
            />
            <Tag
              v-else-if="entry.type === 'recurring-off'"
              :value="t('timeEntries.recurringOffDay')"
              severity="secondary"
            />
            <Tag
              v-else-if="entry.type === 'weekend'"
              :value="t('timeEntries.weekend')"
              severity="secondary"
            />
          </template>
        </Column>
        <Column field="clockIn" :header="t('timeEntries.clockIn')">
          <template #body="{ data: entry }">
            <span v-if="entry.type === 'work'">
              {{ formatDateTime((entry.data as TimeEntryResponse).clockIn) }}
            </span>
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="clockOut" :header="t('timeEntries.clockOut')">
          <template #body="{ data: entry }">
            <span v-if="entry.type === 'work'">
              {{ formatDateTime((entry.data as TimeEntryResponse).clockOut) }}
            </span>
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="breakMinutes" :header="t('timeEntries.breakMinutes')">
          <template #body="{ data: entry }">
            <span v-if="entry.type === 'work'">
              {{ (entry.data as TimeEntryResponse).breakMinutes || 0 }} min
            </span>
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="hoursWorked" :header="t('timeEntries.hoursWorked')">
          <template #body="{ data: entry }">
            <span v-if="entry.type === 'work'">
              {{ formatHours((entry.data as TimeEntryResponse).hoursWorked) }}
            </span>
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="notes" :header="t('timeEntries.notes')">
          <template #body="{ data: entry }">
            <span v-if="entry.type === 'recurring-off'">
              {{ (entry.data as RecurringOffDayResponse).description || '-' }}
            </span>
            <span v-else-if="entry.type === 'weekend'">
              -
            </span>
            <span v-else>
              {{ entry.data.notes || '-' }}
            </span>
          </template>
        </Column>
        <Column :header="t('actions')">
          <template #body="{ data: entry }">
            <div v-if="entry.type === 'work'" class="flex gap-2">
              <Button
                icon="pi pi-pencil"
                text
                rounded
                severity="info"
                @click="openEditDialog(entry.data as TimeEntryResponse)"
                :disabled="isActiveEntry(entry.data as TimeEntryResponse)"
              />
              <Button
                icon="pi pi-trash"
                text
                rounded
                severity="danger"
                @click="openDeleteDialog(entry.data as TimeEntryResponse)"
                :disabled="isActiveEntry(entry.data as TimeEntryResponse)"
              />
            </div>
            <span v-else>-</span>
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
      <div class="manual-entry-form">
        <!-- Type field removed - only WORK supported. For absences, use TimeOff view. -->
        <div class="field">
          <label for="manualEntryDate">{{ t('timeEntries.day') }}</label>
          <DatePicker
            id="manualEntryDate"
            v-model="manualEntryDate"
            show-icon
            @date-select="onDateChange"
            class="w-full"
          />
        </div>

        <div class="field checkbox-field">
          <Checkbox
            v-model="useDefaultHours"
            input-id="useDefaultHours"
            :binary="true"
            @change="onUseDefaultHoursChange"
          />
          <label for="useDefaultHours" class="checkbox-label">
            {{ t('timeEntries.useDefaultHours') }}
          </label>
        </div>

        <div class="field-row">
          <div class="field field-half">
            <label for="manualStartTime">{{ t('timeEntries.startTime') }}</label>
            <DatePicker
              id="manualStartTime"
              v-model="manualEntryStartTime"
              time-only
              :disabled="useDefaultHours"
              :manual-input="true"
              class="w-full"
            />
          </div>

          <div class="field field-half">
            <label for="manualEndTime">{{ t('timeEntries.endTime') }}</label>
            <DatePicker
              id="manualEndTime"
              v-model="manualEntryEndTime"
              time-only
              :disabled="useDefaultHours"
              :manual-input="true"
              class="w-full"
            />
          </div>
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
          <label for="manualNotes">{{ t('timeEntries.notes') }}</label>
          <Textarea
            id="manualNotes"
            v-model="newManualEntry.notes"
            rows="3"
            class="w-full"
          />
        </div>
      </div>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="manualEntryDialogVisible = false" />
        <Button
          :label="t('save')"
          severity="primary"
          @click="createManualEntry"
          :disabled="useDefaultHours && !hasWorkingHoursForSelectedDay"
        />
      </template>
    </Dialog>

    <!-- Edit Dialog -->
    <Dialog
      v-model:visible="editDialogVisible"
      :header="t('timeEntries.edit')"
      :modal="true"
      :style="{ width: '500px' }"
    >
      <div class="manual-entry-form">
        <!-- Type field removed - only WORK supported. -->
        <div class="field">
          <label for="editClockIn">{{ t('timeEntries.clockIn') }}</label>
          <DateTimePicker
            id="editClockIn"
            v-model="currentTimeEntry.clockIn"
            class="w-full"
          />
        </div>
        <div class="field">
          <label for="editClockOut">{{ t('timeEntries.clockOut') }}</label>
          <DateTimePicker
            id="editClockOut"
            v-model="currentTimeEntry.clockOut"
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
          <label for="editNotes">{{ t('timeEntries.notes') }}</label>
          <Textarea
            id="editNotes"
            v-model="currentTimeEntry.notes"
            rows="3"
            class="w-full"
          />
        </div>
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
  padding: 1rem;
  border-radius: var(--border-radius);
  border: 1px solid var(--surface-200);
}


.manual-entry-form .field {
  margin-bottom: 1.5rem;
}

.manual-entry-form .checkbox-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.manual-entry-form .checkbox-label {
  margin: 0;
  cursor: pointer;
  font-weight: 400;
  font-size: 0.875rem;
}

.manual-entry-form .field-row {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.manual-entry-form .field-half {
  flex: 1;
  margin-bottom: 0;
}

.manual-entry-form .field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--text-color);
  min-width: 150px;
}

.manual-entry-form .field :deep(.p-inputtext),
.manual-entry-form .field :deep(.p-dropdown),
.manual-entry-form .field :deep(.p-inputnumber),
.manual-entry-form .field :deep(.p-calendar),
.manual-entry-form .field :deep(.p-inputtextarea) {
  width: 100%;
}
</style>
