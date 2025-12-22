import { ref, computed, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useUndoDelete } from '@/composables/useUndoDelete'
import { formatDateISO, parseLocalDate } from '@/utils/dateTimeUtils'
import {
  TimeEntriesService,
  WorkingHoursService,
  TimeOffService,
  RecurringOffDaysService,
  PublicHolidaysService,
  RecurringOffDayWarningsService
} from '@/api/generated'
import type {
  TimeEntryResponse,
  TimeOffResponse,
  RecurringOffDayResponse,
  WorkingHoursResponse,
  PublicHolidayResponse,
  RecurringOffDayConflictWarningResponse,
  CreateTimeEntryRequest
} from '@/api/generated'
import type { DisplayEntry, TypeEntry } from '@/types/timeEntries'

export function useTimeEntries() {
  const { t } = useI18n()
  const toast = useToast()
  const { handleError } = useErrorHandler()
  const { deleteWithUndo } = useUndoDelete()

  // Use shallowRef for large arrays to improve performance
  const timeEntries = shallowRef<TimeEntryResponse[]>([])
  const timeOffEntries = shallowRef<TimeOffResponse[]>([])
  const recurringOffDays = shallowRef<RecurringOffDayResponse[]>([])
  const publicHolidays = shallowRef<PublicHolidayResponse[]>([])
  const workingHours = ref<WorkingHoursResponse | null>(null)
  const conflictWarnings = shallowRef<RecurringOffDayConflictWarningResponse[]>([])
  const showTimeOff = ref(false)
  const loading = ref(false)
  const activeEntry = ref<TimeEntryResponse | null>(null)

  // Date range filter - default to 50-day window (today ±25 days)
  const now = new Date()
  const startDate = new Date(now)
  startDate.setDate(now.getDate() - 25)
  const endDate = new Date(now)
  endDate.setDate(now.getDate() + 25)
  const startDateFilter = ref<string | undefined>(formatDateISO(startDate))
  const endDateFilter = ref<string | undefined>(formatDateISO(endDate))

  // Combined entries for display - now groups multiple types per date
  const displayEntries = computed<DisplayEntry[]>(() => {
    const dateGroups = new Map<string, TypeEntry[]>()

    const addEntry = (date: string, typeEntry: TypeEntry) => {
      if (!dateGroups.has(date)) {
        dateGroups.set(date, [])
      }
      dateGroups.get(date)!.push(typeEntry)
    }

    timeEntries.value.forEach(entry => {
      const date = entry.entryDate || ''
      addEntry(date, {
        type: 'work',
        data: entry
      })
    })

    if (showTimeOff.value) {
      publicHolidays.value.forEach(holiday => {
        const dateStr = holiday.date || ''
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

      timeOffEntries.value.forEach(timeOff => {
        const start = parseLocalDate(timeOff.startDate)
        const end = parseLocalDate(timeOff.endDate)

        for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
          const dateStr = formatDateISO(d)
          addEntry(dateStr, {
            type: 'timeoff',
            data: timeOff
          })
        }
      })

      if (recurringOffDays.value.length > 0 && startDateFilter.value && endDateFilter.value) {
        const start = parseLocalDate(startDateFilter.value)
        const end = parseLocalDate(endDateFilter.value)

        for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
          const dateStr = formatDateISO(d)
          const dayOfWeek = d.getDay() === 0 ? 7 : d.getDay()

          recurringOffDays.value.forEach(rod => {
            if (!rod.isActive) return
            if (rod.weekday !== dayOfWeek) return

            const rodStart = parseLocalDate(rod.startDate)
            const rodEnd = rod.endDate ? parseLocalDate(rod.endDate) : null
            if (d < rodStart || (rodEnd && d > rodEnd)) return

            if (rod.recurrencePattern === 'EVERY_NTH_WEEK' && rod.referenceDate && rod.weekInterval) {
              const refDate = parseLocalDate(rod.referenceDate)
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

      if (workingHours.value && startDateFilter.value && endDateFilter.value) {
        const start = parseLocalDate(startDateFilter.value)
        const end = parseLocalDate(endDateFilter.value)

        for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
          const dateStr = formatDateISO(d)
          const dayOfWeek = d.getDay() === 0 ? 7 : d.getDay()
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

    const entries: DisplayEntry[] = []
    dateGroups.forEach((types, date) => {
      const precedence = { 'work': 0, 'public-holiday': 1, 'timeoff': 2, 'recurring-off': 3, 'weekend': 4 }
      types.sort((a, b) => precedence[a.type] - precedence[b.type])

      const workEntry = types.find(t => t.type === 'work')?.data as TimeEntryResponse | undefined
      const hasConflict = conflictWarnings.value.some(w => w.conflictDate === date)

      entries.push({
        date,
        types,
        workEntry,
        hasConflict
      })
    })

    return entries.sort((a, b) => b.date.localeCompare(a.date))
  })

  const loadConflictWarnings = async () => {
    try {
      conflictWarnings.value = await RecurringOffDayWarningsService.getConflictWarnings(false)
    } catch (error) {
      handleError(error, 'Failed to load conflict warnings', { logError: true })
      conflictWarnings.value = []
    }
  }

  const loadTimeOff = async () => {
    try {
      const response = await TimeOffService.getTimeOffEntries(startDateFilter.value, endDateFilter.value)
      timeOffEntries.value = response
    } catch (error) {
      handleError(error, t('timeEntries.timeOffLoadError'))
    }
  }

  const loadRecurringOffDays = async () => {
    try {
      const response = await RecurringOffDaysService.getRecurringOffDays()
      recurringOffDays.value = response
    } catch (error) {
      handleError(error, 'Failed to load recurring off-days', { logError: true })
    }
  }

  const getPublicHolidayYears = (startDate?: string, endDate?: string): number[] => {
    if (!startDate || !endDate) {
      return [new Date().getFullYear()]
    }

    const startYear = Number(startDate.slice(0, 4))
    const endYear = Number(endDate.slice(0, 4))
    if (Number.isNaN(startYear) || Number.isNaN(endYear)) {
      return [new Date().getFullYear()]
    }

    const years: number[] = []
    const fromYear = Math.min(startYear, endYear)
    const toYear = Math.max(startYear, endYear)

    for (let year = fromYear; year <= toYear; year += 1) {
      years.push(year)
    }

    return years
  }

  const loadPublicHolidays = async (startDate?: string, endDate?: string) => {
    try {
      const years = getPublicHolidayYears(startDate, endDate)
      const responses = await Promise.all(
        years.map(year => PublicHolidaysService.getPublicHolidays(year))
      )
      publicHolidays.value = responses.flat()
    } catch (error) {
      handleError(error, 'Failed to load public holidays', { logError: true })
    }
  }

  const loadWorkingHours = async () => {
    try {
      const response = await WorkingHoursService.getWorkingHours()
      workingHours.value = response
    } catch (error) {
      handleError(error, 'Failed to load working hours', { logError: true })
    }
  }

  const loadTimeEntries = async () => {
    loading.value = true
    try {
      const response = await TimeEntriesService.getTimeEntries(startDateFilter.value, endDateFilter.value)
      timeEntries.value = response
      activeEntry.value = response.find(entry => entry.isActive) || null

      if (!workingHours.value) {
        await loadWorkingHours()
      }

      await loadConflictWarnings()

      if (showTimeOff.value) {
        await Promise.all([
          loadTimeOff(),
          loadRecurringOffDays(),
          loadPublicHolidays(startDateFilter.value, endDateFilter.value)
        ])
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

  const toggleTimeOff = async () => {
    showTimeOff.value = !showTimeOff.value
    if (showTimeOff.value) {
      loading.value = true
      try {
        const tasks = [
          loadTimeOff(),
          loadRecurringOffDays(),
          loadPublicHolidays(startDateFilter.value, endDateFilter.value)
        ]
        if (!workingHours.value) {
          tasks.push(loadWorkingHours())
        }
        await Promise.all(tasks)
      } finally {
        loading.value = false
      }
    }
  }

  const deleteTimeEntry = async (entry: TimeEntryResponse) => {
    await deleteWithUndo(
      entry,
      async (id) => {
        await TimeEntriesService.deleteTimeEntry(id as number)
      },
      async () => {
        await loadTimeEntries()
      },
      (item) => {
        const typedItem = item as TimeEntryResponse
        const entryDate = new Date(typedItem.clockIn)
        const dayString = entryDate.toLocaleDateString(t('locale'), {
          weekday: 'short',
          day: '2-digit',
          month: '2-digit',
          year: 'numeric'
        })
        return t('timeEntries.deletedForDay', { day: dayString })
      },
      async (item) => {
        const typedItem = item as TimeEntryResponse
        const createRequest: CreateTimeEntryRequest = {
          entryType: 'WORK' as any,
          clockIn: typedItem.clockIn,
          clockOut: typedItem.clockOut,
          breakMinutes: typedItem.breakMinutes,
          notes: typedItem.notes
        }
        await TimeEntriesService.createTimeEntry(createRequest)
      }
    )
  }

  return {
    activeEntry,
    displayEntries,
    loading,
    showTimeOff,
    startDateFilter,
    endDateFilter,
    workingHours,
    loadTimeEntries,
    toggleTimeOff,
    deleteTimeEntry
  }
}
