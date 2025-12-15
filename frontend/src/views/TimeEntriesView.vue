<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Card from 'primevue/card'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import Textarea from 'primevue/textarea'
import Tag from 'primevue/tag'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import Toast from 'primevue/toast'
import DatePicker from '@/components/common/DatePicker.vue'
import DateTimePicker from '@/components/common/DateTimePicker.vue'
import DateRangeFilter from '@/components/common/DateRangeFilter.vue'
import { TimeEntriesService, WorkingHoursService, TimeOffService, RecurringOffDaysService, PublicHolidaysService } from '@/api/generated'
import type { TimeEntryResponse, ClockInRequest, ClockOutRequest, UpdateTimeEntryRequest, CreateTimeEntryRequest, TimeOffResponse, RecurringOffDayResponse, WorkingHoursResponse, PublicHolidayResponse } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()

const timeEntries = ref<TimeEntryResponse[]>([])
const timeOffEntries = ref<TimeOffResponse[]>([])
const recurringOffDays = ref<RecurringOffDayResponse[]>([])
const publicHolidays = ref<PublicHolidayResponse[]>([])
const workingHours = ref<WorkingHoursResponse | null>(null)
const showTimeOff = ref(false)
const loading = ref(false)

// Combined entries for display - now groups multiple types per date
type TypeEntry = {
  type: 'work' | 'timeoff' | 'recurring-off' | 'weekend' | 'public-holiday'
  data: TimeEntryResponse | TimeOffResponse | RecurringOffDayResponse | PublicHolidayResponse | { description: string }
}

type DisplayEntry = {
  date: string
  types: TypeEntry[]
  workEntry?: TimeEntryResponse // The main work entry if exists
}

const displayEntries = computed<DisplayEntry[]>(() => {
  // Group entries by date
  const dateGroups = new Map<string, TypeEntry[]>()

  // Helper to add entry to a date
  const addEntry = (date: string, typeEntry: TypeEntry) => {
    if (!dateGroups.has(date)) {
      dateGroups.set(date, [])
    }
    dateGroups.get(date)!.push(typeEntry)
  }

  // Add work entries (always show these)
  timeEntries.value.forEach(entry => {
    const date = entry.entryDate || ''
    addEntry(date, {
      type: 'work',
      data: entry
    })
  })

  // Add time-off entries if toggle is enabled
  if (showTimeOff.value) {
    // Add public holidays (only within the filter date range)
    publicHolidays.value.forEach(holiday => {
      const dateStr = holiday.date || ''
      // Only add if within the filter range
      if (startDateFilter.value && endDateFilter.value) {
        if (dateStr >= startDateFilter.value && dateStr <= endDateFilter.value) {
          addEntry(dateStr, {
            type: 'public-holiday',
            data: holiday
          })
        }
      } else {
        addEntry(dateStr, {
          type: 'public-holiday',
          data: holiday
        })
      }
    })

    // Add time-off entries (vacation, sick, personal)
    timeOffEntries.value.forEach(timeOff => {
      const start = new Date(timeOff.startDate)
      const end = new Date(timeOff.endDate)

      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        const dateStr = d.toISOString().split('T')[0]
        addEntry(dateStr, {
          type: 'timeoff',
          data: timeOff
        })
      }
    })

    // Add recurring off-days
    if (recurringOffDays.value.length > 0 && startDateFilter.value && endDateFilter.value) {
      const start = new Date(startDateFilter.value)
      const end = new Date(endDateFilter.value)

      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        const dateStr = d.toISOString().split('T')[0]
        const dayOfWeek = d.getDay() === 0 ? 7 : d.getDay()

        recurringOffDays.value.forEach(rod => {
          if (!rod.isActive) return
          if (rod.weekday !== dayOfWeek) return

          const rodStart = new Date(rod.startDate)
          const rodEnd = rod.endDate ? new Date(rod.endDate) : null
          if (d < rodStart || (rodEnd && d > rodEnd)) return

          if (rod.recurrencePattern === 'EVERY_NTH_WEEK' && rod.referenceDate && rod.weekInterval) {
            const refDate = new Date(rod.referenceDate)
            const daysDiff = Math.floor((d.getTime() - refDate.getTime()) / (1000 * 60 * 60 * 24))
            const weeksDiff = Math.floor(daysDiff / 7)
            if (weeksDiff % rod.weekInterval === 0 && d.getDay() === refDate.getDay()) {
              addEntry(dateStr, {
                type: 'recurring-off',
                data: rod
              })
            }
          } else if (rod.recurrencePattern === 'NTH_WEEKDAY_OF_MONTH' && rod.weekOfMonth) {
            const nthOccurrence = Math.ceil(d.getDate() / 7)
            if (rod.weekOfMonth === nthOccurrence) {
              addEntry(dateStr, {
                type: 'recurring-off',
                data: rod
              })
            }
          }
        })
      }
    }

    // Add weekend days (only actual Saturday/Sunday based on working hours config)
    if (workingHours.value && startDateFilter.value && endDateFilter.value) {
      const start = new Date(startDateFilter.value)
      const end = new Date(endDateFilter.value)

      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        const dateStr = d.toISOString().split('T')[0]
        const dayOfWeek = d.getDay() === 0 ? 7 : d.getDay()

        // Only add weekend entry for days marked as non-working in the config
        // This typically means Saturday (6) and Sunday (7), but respects user's working hours setup
        const workingDay = workingHours.value.workingDays?.find(wd => wd.weekday === dayOfWeek)
        if (workingDay && !workingDay.isWorkingDay) {
          addEntry(dateStr, {
            type: 'weekend',
            data: { description: t('timeEntries.weekend') }
          })
        }
      }
    }
  }

  // Convert to display entries and sort types by precedence
  const entries: DisplayEntry[] = []
  dateGroups.forEach((types, date) => {
    // Sort by precedence: work, public-holiday, timeoff, recurring-off, weekend
    const precedence = { 'work': 0, 'public-holiday': 1, 'timeoff': 2, 'recurring-off': 3, 'weekend': 4 }
    types.sort((a, b) => precedence[a.type] - precedence[b.type])

    const workEntry = types.find(t => t.type === 'work')?.data as TimeEntryResponse | undefined

    entries.push({
      date,
      types,
      workEntry
    })
  })

  // Sort by date descending
  return entries.sort((a, b) => b.date.localeCompare(a.date))
})
const clockOutDialogVisible = ref(false)
const manualEntryDialogVisible = ref(false)
const editDialogVisible = ref(false)
const clockOutNotes = ref('')
const activeEntry = ref<TimeEntryResponse | null>(null)
const currentTimeEntry = ref<Partial<UpdateTimeEntryRequest>>({})
const timeEntryToEdit = ref<TimeEntryResponse | null>(null)
const newManualEntry = ref<Partial<CreateTimeEntryRequest>>({
  entryType: 'WORK' as any,
  breakMinutes: 0
})
const manualEntryDate = ref<Date>(new Date())  // Separate date field
const manualEntryStartTime = ref<Date>(new Date())  // Start time
const manualEntryEndTime = ref<Date>(new Date())    // End time
const useDefaultHours = ref(false)  // Toggle for using default working hours
const hasWorkingHoursForSelectedDay = ref(true)  // Track if selected day has working hours
const cachedWorkingHours = ref<WorkingHoursResponse | null>(null)  // Cache working hours
// Store the values when default hours are applied, so they persist when toggling
const savedStartTime = ref<Date | null>(null)
const savedEndTime = ref<Date | null>(null)
// Last deleted entry for undo
const lastDeletedEntry = ref<TimeEntryResponse | null>(null)

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

    // Load working hours if not already loaded (needed for difference calculation)
    if (!workingHours.value) {
      await loadWorkingHours()
    }

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

const loadPublicHolidays = async () => {
  try {
    const year = new Date().getFullYear()
    const response = await PublicHolidaysService.getPublicHolidays(year)
    publicHolidays.value = response
  } catch (error) {
    console.error('Error loading public holidays:', error)
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
        loadPublicHolidays(),
        loadWorkingHours()
      ])
    } finally {
      loading.value = false
    }
  }
}

const clockIn = async () => {
  try {
    const request: ClockInRequest = {
      notes: undefined
    }
    const response = await TimeEntriesService.clockIn(request)
    activeEntry.value = response

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('timeEntries.clockInSuccess'),
      life: 3000
    })

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

const cancelActiveEntry = async () => {
  try {
    if (activeEntry.value?.id) {
      await TimeEntriesService.deleteTimeEntry(activeEntry.value.id)
      activeEntry.value = null

      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('timeEntries.entryCancelled'),
        life: 3000
      })

      await loadTimeEntries()
    }
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('timeEntries.cancelError'),
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
  timeEntryToEdit.value = entry
  editDialogVisible.value = true
}

const saveTimeEntry = async () => {
  try {
    if (timeEntryToEdit.value?.id) {
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
        timeEntryToEdit.value.id,
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

const deleteTimeEntry = async (entry: TimeEntryResponse) => {
  try {
    // Store the deleted entry for potential undo
    lastDeletedEntry.value = { ...entry }

    // Delete immediately via API
    await TimeEntriesService.deleteTimeEntry(entry.id)

    // Reload entries
    await loadTimeEntries()

    // Format the day for the toast message (short format)
    const entryDate = new Date(entry.clockIn)
    const dayString = entryDate.toLocaleDateString(t('locale'), {
      weekday: 'short',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    })

    // Close any existing delete toast
    toast.removeGroup('delete-undo')

    // Show compact toast with undo option (stays until dismissed)
    toast.add({
      severity: 'info',
      summary: t('timeEntries.deletedForDay', { day: dayString }),
      life: 0, // Stays until manually dismissed
      closable: true,
      group: 'delete-undo'
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('timeEntries.deleteError'),
      life: 3000
    })
  }
}

const undoDelete = async () => {
  if (!lastDeletedEntry.value) return

  try {
    // Close the delete toast
    toast.removeGroup('delete-undo')

    // Re-create the entry via API
    const createRequest: CreateTimeEntryRequest = {
      entryType: 'WORK' as any,
      clockIn: lastDeletedEntry.value.clockIn,
      clockOut: lastDeletedEntry.value.clockOut,
      breakMinutes: lastDeletedEntry.value.breakMinutes,
      notes: lastDeletedEntry.value.notes
    }

    await TimeEntriesService.createTimeEntry(createRequest)

    // Clear last deleted
    lastDeletedEntry.value = null

    // Reload entries
    await loadTimeEntries()

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('timeEntries.undoSuccess'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('timeEntries.undoError'),
      life: 3000
    })
  }
}

const formatDateTime = (dateTime: string | undefined) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleTimeString(t('locale'), {
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatDate = (date: string | undefined) => {
  if (!date) return '-'
  const dateObj = new Date(date)
  const day = String(dateObj.getDate()).padStart(2, '0')
  const month = String(dateObj.getMonth() + 1).padStart(2, '0')
  const year = dateObj.getFullYear()
  return `${day}.${month}.${year}`
}

const formatHours = (hours: number | undefined) => {
  if (hours === undefined || hours === null) return '-'
  const h = Math.floor(hours)
  const m = Math.round((hours - h) * 60)
  return m > 0 ? `${h}h ${m}m` : `${h}h`
}

const getExpectedHoursForEntry = (entry: TimeEntryResponse): number | null => {
  if (!workingHours.value || !entry.entryDate) return null

  // Get day of week from entry date (1=Monday, 7=Sunday)
  const entryDate = new Date(entry.entryDate)
  const dayOfWeek = entryDate.getDay() === 0 ? 7 : entryDate.getDay()

  // Find working hours for this day
  const dayWorkingHours = workingHours.value.workingDays.find(
    wd => wd.weekday === dayOfWeek
  )

  if (!dayWorkingHours || !dayWorkingHours.isWorkingDay) {
    return 0 // Non-working day
  }

  return dayWorkingHours.hours
}

const getHoursDifference = (entry: TimeEntryResponse): string => {
  const expectedHours = getExpectedHoursForEntry(entry)
  if (expectedHours === null) return '-'

  const actualHours = entry.hoursWorked || 0
  const diffHours = actualHours - expectedHours
  const diffMinutes = Math.round(diffHours * 60)

  if (diffMinutes === 0) return '±0 min'

  const sign = diffMinutes > 0 ? '+' : ''
  return `${sign}${diffMinutes} min`
}

const getDifferenceSeverity = (entry: TimeEntryResponse): 'success' | 'warn' | 'danger' | 'secondary' | null => {
  const expectedHours = getExpectedHoursForEntry(entry)
  if (expectedHours === null) return null

  const actualHours = entry.hoursWorked || 0
  const diffHours = actualHours - expectedHours
  const diffMinutes = Math.abs(Math.round(diffHours * 60))

  // Within 15 minutes: green (success)
  if (diffMinutes <= 15) return 'success'

  // Between 15-45 minutes: yellow (warn)
  if (diffMinutes <= 45) return 'warn'

  // Over 45 minutes: red (danger)
  return 'danger'
}

const isActiveEntry = (entry: TimeEntryResponse) => {
  return entry.isActive
}

const getTimeOffSeverity = (type: string): 'success' | 'warn' | 'danger' | 'info' | 'secondary' | 'contrast' | null => {
  switch (type) {
    case 'VACATION':
      return 'success' // Green for vacation
    case 'SICK':
      return 'warn' // Yellow/Orange for sick days
    case 'PERSONAL':
      return 'info' // Blue for personal days
    case 'PUBLIC_HOLIDAY':
      return 'danger' // Red for public holidays
    default:
      return 'secondary'
  }
}

const getTypeEmoji = (typeEntry: TypeEntry): string => {
  if (typeEntry.type === 'work') {
    return '🏢'
  } else if (typeEntry.type === 'public-holiday') {
    return '🎊'
  } else if (typeEntry.type === 'timeoff') {
    const timeOff = typeEntry.data as TimeOffResponse
    switch (timeOff.timeOffType) {
      case 'VACATION':
        return '🏝️'
      case 'SICK':
        return '😵‍💫'
      case 'CHILD_SICK':
        return '👩‍👧'
      case 'PERSONAL':
        return '🏠'
      case 'PUBLIC_HOLIDAY':
        return '🎊'
      default:
        return '📅'
    }
  } else if (typeEntry.type === 'recurring-off') {
    return '📴'
  } else if (typeEntry.type === 'weekend') {
    return '🗓️'
  }
  return '📅'
}

const getTypeTooltip = (typeEntry: TypeEntry): string => {
  if (typeEntry.type === 'work') {
    return t('timeEntries.type.WORK')
  } else if (typeEntry.type === 'public-holiday') {
    return (typeEntry.data as PublicHolidayResponse).name || ''
  } else if (typeEntry.type === 'timeoff') {
    const timeOff = typeEntry.data as TimeOffResponse
    return t(`timeOff.type.${timeOff.timeOffType}`)
  } else if (typeEntry.type === 'recurring-off') {
    return t('timeEntries.recurringOffDay')
  } else if (typeEntry.type === 'weekend') {
    return t('timeEntries.weekend')
  }
  return ''
}

const getRowBackgroundClass = (entry: DisplayEntry): string => {
  // Get the highest precedence type (first in the array after sorting)
  if (entry.types.length === 0) return ''

  const primaryType = entry.types[0].type

  switch (primaryType) {
    case 'work':
      return 'row-bg-work'
    case 'public-holiday':
      return 'row-bg-public-holiday'
    case 'timeoff':
      const timeOff = entry.types[0].data as TimeOffResponse
      switch (timeOff.timeOffType) {
        case 'VACATION':
          return 'row-bg-vacation'
        case 'SICK':
          return 'row-bg-sick'
        case 'CHILD_SICK':
          return 'row-bg-sick'
        case 'PERSONAL':
          return 'row-bg-personal'
        default:
          return ''
      }
    case 'recurring-off':
      return 'row-bg-recurring-off'
    case 'weekend':
      return 'row-bg-weekend'
    default:
      return ''
  }
}

onMounted(() => {
  loadTimeEntries()
})
</script>

<template>
  <div class="time-entries-view">
    <h1 class="page-title">{{ t('timeEntries.title') }}</h1>

    <!-- Action Cards -->
    <div class="action-cards-container mb-3">
      <div
        v-if="!activeEntry"
        class="action-card-small action-clock-in"
        @click="clockIn"
      >
        <i class="pi pi-play-circle action-icon-small"></i>
        <div class="action-label-small">{{ t('timeEntries.clockIn') }}</div>
      </div>
      <template v-else>
        <div
          class="action-card-small action-clock-out"
          @click="openClockOutDialog"
        >
          <i class="pi pi-stop-circle action-icon-small"></i>
          <div class="action-label-small">{{ t('timeEntries.clockOut') }}</div>
        </div>
        <div
          class="action-card-small action-cancel"
          @click="cancelActiveEntry"
        >
          <i class="pi pi-times action-icon-small"></i>
          <div class="action-label-small">{{ t('timeEntries.cancel') }}</div>
        </div>
      </template>

      <div
        class="action-card-small action-quick-entry"
        @click="createQuickWorkEntry"
      >
        <i class="pi pi-bolt action-icon-small"></i>
        <div class="action-label-small">{{ t('timeEntries.quickWorkEntry') }}</div>
      </div>

      <div
        class="action-card-small action-manual-entry"
        @click="openManualEntryDialog"
      >
        <i class="pi pi-plus action-icon-small"></i>
        <div class="action-label-small">{{ t('timeEntries.addManualEntry') }}</div>
      </div>
    </div>

    <Card class="section-card">
      <template #content>

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
        :row-class="getRowBackgroundClass"
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
            <div class="type-emojis">
              <span
                v-for="(typeEntry, idx) in entry.types"
                :key="idx"
                class="type-emoji"
                v-tooltip.top="getTypeTooltip(typeEntry)"
              >
                {{ getTypeEmoji(typeEntry) }}
              </span>
            </div>
          </template>
        </Column>
        <Column field="clockIn" :header="t('timeEntries.clockIn')">
          <template #body="{ data: entry }">
            <span v-if="entry.workEntry">
              {{ formatDateTime(entry.workEntry.clockIn) }}
            </span>
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="clockOut" :header="t('timeEntries.clockOut')">
          <template #body="{ data: entry }">
            <span v-if="entry.workEntry">
              {{ formatDateTime(entry.workEntry.clockOut) }}
            </span>
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="breakMinutes" :header="t('timeEntries.breakMinutes')">
          <template #body="{ data: entry }">
            <span v-if="entry.workEntry">
              {{ entry.workEntry.breakMinutes || 0 }} min
            </span>
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="hoursWorked" :header="t('timeEntries.hoursWorked')">
          <template #body="{ data: entry }">
            <span v-if="entry.workEntry">
              {{ formatHours(entry.workEntry.hoursWorked) }}
            </span>
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="difference" :header="t('timeEntries.difference')">
          <template #body="{ data: entry }">
            <Tag
              v-if="entry.workEntry && getDifferenceSeverity(entry.workEntry)"
              :value="getHoursDifference(entry.workEntry)"
              :severity="getDifferenceSeverity(entry.workEntry)"
            />
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="notes" :header="t('timeEntries.notes')">
          <template #body="{ data: entry }">
            <template v-if="entry.types.length === 1">
              <span v-if="entry.types[0].type === 'recurring-off'">
                {{ (entry.types[0].data as RecurringOffDayResponse).description || '-' }}
              </span>
              <span v-else-if="entry.types[0].type === 'weekend'">
                -
              </span>
              <span v-else-if="entry.types[0].type === 'public-holiday'">
                {{ (entry.types[0].data as PublicHolidayResponse).name || '-' }}
              </span>
              <span v-else>
                {{ entry.types[0].data.notes || '-' }}
              </span>
            </template>
            <template v-else>
              <span v-if="entry.workEntry">
                {{ entry.workEntry.notes || '-' }}
              </span>
              <span v-else>-</span>
            </template>
          </template>
        </Column>
        <Column :header="t('actions')">
          <template #body="{ data: entry }">
            <template v-if="entry.workEntry">
              <Button
                icon="pi pi-pencil"
                class="p-button-text p-button-sm"
                @click="openEditDialog(entry.workEntry)"
                :disabled="isActiveEntry(entry.workEntry)"
              />
              <Button
                icon="pi pi-trash"
                class="p-button-text p-button-danger p-button-sm"
                @click="deleteTimeEntry(entry.workEntry)"
                :disabled="isActiveEntry(entry.workEntry)"
              />
            </template>
            <span v-else>-</span>
          </template>
        </Column>
      </DataTable>
      </template>
    </Card>

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

    <!-- Toast for undo delete -->
    <Toast position="bottom-center" group="delete-undo">
      <template #message="slotProps">
        <div class="flex align-items-center gap-3 flex-1">
          <span class="flex-1 text-sm">{{ slotProps.message.summary }}</span>
          <Button
            :label="t('timeEntries.undo')"
            severity="secondary"
            size="small"
            outlined
            @click="undoDelete"
          />
        </div>
      </template>
    </Toast>
  </div>
</template>

<style scoped>
/* Using shared layout styles from layouts.css */
.time-entries-view {
  padding: var(--tt-view-padding);
}

/* All shared styles (action cards, forms, datatables, row backgrounds) moved to:
   - /styles/components/action-cards.css
   - /styles/components/forms.css
   - /styles/components/data-tables.css
   - /styles/layouts.css
*/
</style>
