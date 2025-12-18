import { ref, computed, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/composables/useAuth'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCache } from '@/composables/useCache'
import { TimeEntriesService, PublicHolidaysService, WorkingHoursService, TimeOffService } from '@/api/generated'
import type { DailySummaryResponse, PublicHolidayResponse, TimeOffResponse, WorkingHoursResponse, TimeEntryResponse } from '@/api/generated'
import { TimeOffType } from '@/types/enums'

/**
 * Composable for dashboard functionality
 * Handles calendar data, time entries, caching, and dashboard actions
 */
export function useDashboard() {
  const { t } = useI18n()
  const { currentUser } = useAuth()
  const { handleError, handleSuccess, handleWarning } = useErrorHandler()

  // State
  const currentMonth = ref<Date>(new Date())
  const dailySummaries = shallowRef<DailySummaryResponse[]>([]) // Large array - use shallowRef for performance
  const workingHours = ref<WorkingHoursResponse | null>(null)
  const loading = ref(false)
  const activeEntry = ref<TimeEntryResponse | null>(null)
  const nextVacation = ref<TimeOffResponse | null>(null)
  const overtimeSelectedMonth = ref<number>(0)

  // Dialog state
  const selectedDate = ref<string | null>(null)
  const selectedEntries = shallowRef<TimeEntryResponse[]>([]) // Array - use shallowRef
  const selectedTimeOffEntries = shallowRef<TimeOffResponse[]>([]) // Array - use shallowRef
  const showTimeOffDialog = ref(false)
  const showEditDialog = ref(false)

  // Caches
  const dailySummaryCache = useCache<DailySummaryResponse>()
  const publicHolidaysCache = ref<Map<number, PublicHolidayResponse[]>>(new Map())

  // Computed
  const hasTodayWorkingHours = computed(() => {
    if (!workingHours.value) return false
    const today = new Date()
    const dayOfWeek = today.getDay() === 0 ? 7 : today.getDay()
    const workingDay = workingHours.value.workingDays?.find(wd => wd.weekday === dayOfWeek)
    return workingDay?.isWorkingDay || false
  })

  const nextVacationText = computed(() => {
    if (!nextVacation.value) return t('dashboard.noUpcomingVacation')

    const startDate = new Date(nextVacation.value.startDate)
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    startDate.setHours(0, 0, 0, 0)

    const diffTime = startDate.getTime() - today.getTime()
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))

    if (diffDays === 0) return t('dashboard.vacationToday')
    if (diffDays === 1) return t('dashboard.vacationTomorrow')
    return t('dashboard.vacationInDays', { days: diffDays })
  })

  // Helper: Get public holidays for a specific year
  const getPublicHolidaysForYear = async (year: number): Promise<PublicHolidayResponse[]> => {
    if (publicHolidaysCache.value.has(year)) {
      return publicHolidaysCache.value.get(year)!
    }

    const holidays = await PublicHolidaysService.getPublicHolidays(year)
    publicHolidaysCache.value.set(year, holidays)
    return holidays
  }

  // Helper: Format date string
  const formatDateString = (date: Date): string => {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  }

  // Helper: Merge public holidays into summaries
  const mergePublicHolidays = (summaries: DailySummaryResponse[], holidays: PublicHolidayResponse[]): DailySummaryResponse[] => {
    return summaries.map(summary => {
      const holiday = holidays.find(h => h.date === summary.date)
      if (holiday && holiday.date) {
          const holidayTimeOff: TimeOffResponse = {
            id: 0,
            userId: currentUser.value?.id || 0,
            startDate: holiday.date,
            endDate: holiday.date,
            timeOffType: TimeOffType.PUBLIC_HOLIDAY as any, // API expects string literal
            notes: holiday.name,
            days: 1 // Public holidays are always 1 day
          }
        return {
          ...summary,
          timeOffEntries: [holidayTimeOff, ...(summary.timeOffEntries || [])]
        }
      }
      return summary
    })
  }

  // Helper: Calculate calendar date range for display
  const calculateCalendarDateRange = (year: number, month: number) => {
    const startDate = new Date(year, month, 1)
    const endDate = new Date(year, month + 1, 0)
    const firstDayOfWeek = startDate.getDay()
    const emptyDaysAtStart = firstDayOfWeek === 0 ? 6 : firstDayOfWeek - 1
    const daysInCurrentMonth = endDate.getDate()
    const usedCells = emptyDaysAtStart + daysInCurrentMonth
    const remainderInWeek = usedCells % 7
    const emptyDaysAtEnd = remainderInWeek === 0 ? 0 : 7 - remainderInWeek

    const fetchStartDate = new Date(year, month, 1 - emptyDaysAtStart)
    const fetchEndDate = new Date(year, month, daysInCurrentMonth + emptyDaysAtEnd)

    return {
      startDate: fetchStartDate,
      endDate: fetchEndDate,
      startDateStr: formatDateString(fetchStartDate),
      endDateStr: formatDateString(fetchEndDate)
    }
  }

  // Load initial data: Batch load 9 months back + 3 months ahead
  const loadInitialData = async () => {
    loading.value = true
    try {
      const today = new Date()

      // Calculate date range: 9 months ago to 3 months ahead
      const startDate = new Date(today)
      startDate.setMonth(today.getMonth() - 9)
      startDate.setDate(1)

      const endDate = new Date(today)
      endDate.setMonth(today.getMonth() + 3)
      endDate.setMonth(endDate.getMonth() + 1)
      endDate.setDate(0)

      const startDateStr = formatDateString(startDate)
      const endDateStr = formatDateString(endDate)

      // Determine which years we need holidays for
      const yearsNeeded = new Set([startDate.getFullYear(), endDate.getFullYear()])

      // Fetch all data in parallel
      const [summaries, ...holidayResponses] = await Promise.all([
        TimeEntriesService.getDailySummary(startDateStr, endDateStr),
        ...Array.from(yearsNeeded).map(y => getPublicHolidaysForYear(y)),
        WorkingHoursService.getWorkingHours()
      ])

      // Store working hours (last item)
      const workingHoursData = holidayResponses[holidayResponses.length - 1] as WorkingHoursResponse
      workingHours.value = workingHoursData

      // Flatten public holidays
      const publicHolidays = holidayResponses.slice(0, -1).flat() as PublicHolidayResponse[]

      // Merge public holidays with summaries
      const summariesWithHolidays = mergePublicHolidays(summaries, publicHolidays)

      // Store summaries in cache (clear first, then store)
      dailySummaryCache.clearCache()
      summariesWithHolidays.forEach(summary => {
        if (summary.date) {
          dailySummaryCache.setCache(summary.date, summary)
        }
      })

      // Update cache range AFTER storing data
      dailySummaryCache.updateCacheRange(startDateStr, endDateStr)

      // Initialize display for current month - filter summaries for display range
      const { startDateStr: displayStart, endDateStr: displayEnd } = calculateCalendarDateRange(
        currentMonth.value.getFullYear(),
        currentMonth.value.getMonth()
      )

      // Filter summaries for the display range
      const displaySummaries = summariesWithHolidays.filter(summary => {
        if (!summary.date) return false
        return summary.date >= displayStart && summary.date <= displayEnd
      })

      if (displaySummaries.length > 0) {
        dailySummaries.value = displaySummaries
      }
    } catch (error) {
      handleError(error, t('dashboard.loadError'))
    } finally {
      loading.value = false
    }
  }

  // Load daily summaries for current month
  const loadDailySummaries = async () => {
    try {
      const year = currentMonth.value.getFullYear()
      const month = currentMonth.value.getMonth()
      const { startDateStr, endDateStr } = calculateCalendarDateRange(year, month)

      // Try cache first - check if range is fully cached
      if (dailySummaryCache.isRangeCached(startDateStr, endDateStr)) {
        const cachedSummaries = dailySummaryCache.getCachedRange(startDateStr, endDateStr)
        if (cachedSummaries.length > 0) {
          dailySummaries.value = cachedSummaries
          return
        }
      }

      // Cache miss - fetch from API
      loading.value = true
      try {
        const yearsNeeded = new Set([
          new Date(startDateStr).getFullYear(),
          new Date(endDateStr).getFullYear()
        ])

        const [summaries, ...holidayResponses] = await Promise.all([
          TimeEntriesService.getDailySummary(startDateStr, endDateStr),
          ...Array.from(yearsNeeded).map(y => getPublicHolidaysForYear(y))
        ])

        const publicHolidays = holidayResponses.flat() as PublicHolidayResponse[]
        const summariesWithHolidays = mergePublicHolidays(summaries, publicHolidays)

        // Update cache
        summariesWithHolidays.forEach(summary => {
          if (summary.date) {
            dailySummaryCache.setCache(summary.date, summary)
          }
        })

        dailySummaryCache.updateCacheRange(startDateStr, endDateStr)
        dailySummaries.value = summariesWithHolidays
      } finally {
        loading.value = false
      }
    } catch (error) {
      handleError(error, t('dashboard.loadError'))
    }
  }

  // Handle month change
  const handleMonthChange = async (newMonth: Date) => {
    currentMonth.value = newMonth
    await loadDailySummaries()
    await calculateOvertime()
  }

  // Load active time entry
  const loadActiveEntry = async () => {
    try {
      const today = new Date().toISOString().split('T')[0]
      const entries = await TimeEntriesService.getTimeEntries(today, today)
      activeEntry.value = entries.find(e => e.isActive) || null
    } catch (error) {
      handleError(error, 'Failed to load active entry', { logError: true })
    }
  }

  // Load next vacation
  const loadNextVacation = async () => {
    try {
      const todayDateObj = new Date()
      const today = todayDateObj.toISOString().split('T')[0]
      const oneYearLater = new Date()
      oneYearLater.setFullYear(oneYearLater.getFullYear() + 1)
      const endDate = oneYearLater.toISOString().split('T')[0]

      const vacations = await TimeOffService.getTimeOffEntries(today, endDate)
      const todayDate = todayDateObj
      const upcomingVacations = vacations
        .filter((v): v is TimeOffResponse & { startDate: string } => {
          if (v.timeOffType !== 'VACATION' || !v.startDate) return false
          const startDate = new Date(v.startDate)
          return startDate >= todayDate
        })
        .sort((a, b) => {
          const dateA = new Date(a.startDate).getTime()
          const dateB = new Date(b.startDate).getTime()
          return dateA - dateB
        })

      nextVacation.value = upcomingVacations[0] || null
    } catch (error) {
      handleError(error, 'Failed to load next vacation', { logError: true })
    }
  }

  // Calculate overtime for selected month
  const calculateOvertime = async () => {
    try {
      const selectedMonth = currentMonth.value
      const today = new Date()

      const monthStart = new Date(selectedMonth.getFullYear(), selectedMonth.getMonth(), 1)
      const monthEnd = new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + 1, 0)

      let endDate: Date
      if (selectedMonth.getFullYear() === today.getFullYear() && selectedMonth.getMonth() === today.getMonth()) {
        endDate = today
      } else if (selectedMonth > today) {
        endDate = monthStart
      } else {
        endDate = monthEnd
      }

      const startDateStr = formatDateString(monthStart)
      const endDateStr = formatDateString(endDate)

      let monthSummaries = dailySummaryCache.getCachedRange(startDateStr, endDateStr)
      if (monthSummaries.length === 0) {
        monthSummaries = await TimeEntriesService.getDailySummary(startDateStr, endDateStr)
      }

      const daysWithEntries = monthSummaries.filter(day => day.actualHours > 0)
      overtimeSelectedMonth.value = daysWithEntries.reduce(
        (sum, day) => sum + (day.actualHours - day.expectedHours),
        0
      )
    } catch (error) {
      handleError(error, 'Failed to calculate overtime', { logError: true })
    }
  }

  // Invalidate cache and reload
  const invalidateCacheAndReload = async () => {
    dailySummaryCache.clearCache()
    await loadInitialData()
    await calculateOvertime()
  }

  // Format overtime hours
  const formatOvertime = (hours: number) => {
    const sign = hours >= 0 ? '+' : ''
    return `${sign}${hours.toFixed(1)}h`
  }

  // Clock in
  const clockInNow = async () => {
    try {
      await TimeEntriesService.clockIn({ notes: '' })
      handleSuccess(t('dashboard.clockInSuccess'))
      await loadActiveEntry()
    } catch (error: any) {
      handleError(error, t('dashboard.clockInError'))
    }
  }

  // Clock out
  const clockOutNow = async () => {
    try {
      await TimeEntriesService.clockOut({ notes: '' })
      handleSuccess(t('dashboard.clockOutSuccess'))
      activeEntry.value = null
      await invalidateCacheAndReload()
    } catch (error: any) {
      handleError(error, t('dashboard.clockOutError'))
    }
  }

  // Cancel active entry
  const cancelEntry = async () => {
    try {
      if (activeEntry.value?.id) {
        await TimeEntriesService.deleteTimeEntry(activeEntry.value.id)
        handleSuccess(t('dashboard.entryCancelled'))
        activeEntry.value = null
      }
    } catch (error) {
      handleError(error, t('dashboard.cancelError'))
    }
  }

  // Create quick work entry
  const createQuickWorkEntry = async () => {
    try {
      if (!workingHours.value) return

      const today = new Date()
      const dayOfWeek = today.getDay() === 0 ? 7 : today.getDay()
      const todayWorkingHours = workingHours.value.workingDays.find(wd => wd.weekday === dayOfWeek)

      if (!todayWorkingHours || !todayWorkingHours.isWorkingDay || !todayWorkingHours.startTime || !todayWorkingHours.endTime) {
        handleWarning(t('dashboard.noWorkingHoursToday'))
        return
      }

      const [startHour, startMin] = todayWorkingHours.startTime.split(':').map(Number)
      const [endHour, endMin] = todayWorkingHours.endTime.split(':').map(Number)

      if (startHour === undefined || startMin === undefined || endHour === undefined || endMin === undefined) {
        handleWarning(t('dashboard.noWorkingHoursToday'))
        return
      }

      const clockIn = new Date(today)
      clockIn.setHours(startHour, startMin, 0, 0)

      const clockOut = new Date(today)
      clockOut.setHours(endHour, endMin, 0, 0)

      await TimeEntriesService.createTimeEntry({
        clockIn: clockIn.toISOString(),
        clockOut: clockOut.toISOString(),
        breakMinutes: 0,
        entryType: 'WORK' as any, // API expects specific string literal type
        notes: ''
      })

      handleSuccess(t('dashboard.quickEntryCreated'))
      await invalidateCacheAndReload()
    } catch (error: any) {
      handleError(error, t('dashboard.quickEntryError'))
    }
  }

  // Handle quick entry from calendar
  const handleQuickEntryFromCalendar = async (payload: { date: string; startTime: string; endTime: string }) => {
    try {
      const date = new Date(payload.date)
      const startParts = payload.startTime.split(':').map(Number)
      const endParts = payload.endTime.split(':').map(Number)
      
      const startHour = startParts[0]
      const startMin = startParts[1]
      const endHour = endParts[0]
      const endMin = endParts[1]

      if (startHour === undefined || startMin === undefined || endHour === undefined || endMin === undefined || 
          isNaN(startHour) || isNaN(startMin) || isNaN(endHour) || isNaN(endMin)) {
        handleError(new Error('Invalid time format'), t('dashboard.quickEntryError'))
        return
      }

      const clockIn = new Date(date)
      clockIn.setHours(startHour, startMin, 0, 0)

      const clockOut = new Date(date)
      clockOut.setHours(endHour, endMin, 0, 0)

      await TimeEntriesService.createTimeEntry({
        clockIn: clockIn.toISOString(),
        clockOut: clockOut.toISOString(),
        breakMinutes: 0,
        entryType: 'WORK' as any, // API expects specific string literal type
        notes: ''
      })

      handleSuccess(t('dashboard.quickEntryCreated'))
      await invalidateCacheAndReload()
    } catch (error: any) {
      handleError(error, t('dashboard.quickEntryError'))
    }
  }

  // Handle time off from calendar
  const handleTimeOffFromCalendar = (payload: { date: string }) => {
    selectedDate.value = payload.date
    showTimeOffDialog.value = true
  }

  // Handle edit all from calendar
  const handleEditAllFromCalendar = (payload: { date: string; entries: TimeEntryResponse[]; timeOffEntries: TimeOffResponse[] }) => {
    selectedDate.value = payload.date
    selectedEntries.value = payload.entries
    selectedTimeOffEntries.value = payload.timeOffEntries
    showEditDialog.value = true
  }

  // Handle form saved
  const handleFormSaved = async () => {
    await invalidateCacheAndReload()
  }

  return {
    // State
    currentMonth,
    dailySummaries,
    workingHours,
    loading,
    activeEntry,
    nextVacation,
    overtimeSelectedMonth,
    selectedDate,
    selectedEntries,
    selectedTimeOffEntries,
    showTimeOffDialog,
    showEditDialog,

    // Computed
    hasTodayWorkingHours,
    nextVacationText,

    // Methods
    loadInitialData,
    loadDailySummaries,
    handleMonthChange,
    loadActiveEntry,
    loadNextVacation,
    calculateOvertime,
    invalidateCacheAndReload,
    formatOvertime,
    clockInNow,
    clockOutNow,
    cancelEntry,
    createQuickWorkEntry,
    handleQuickEntryFromCalendar,
    handleTimeOffFromCalendar,
    handleEditAllFromCalendar,
    handleFormSaved
  }
}
