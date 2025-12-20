import { ref, computed, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/composables/useAuth'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCache } from '@/composables/useCache'
import { useConflictWarnings } from '@/composables/useConflictWarnings'
import { useMultiUndoDelete } from '@/composables/useMultiUndoDelete'
import { TimeEntriesService, PublicHolidaysService, WorkingHoursService, TimeOffService, type CreateTimeEntryRequest, type CreateTimeOffRequest } from '@/api/generated'
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
  const { loadWarnings } = useConflictWarnings()
  const { deleteWithUndo } = useMultiUndoDelete()

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
  const showManualEntryDialog = ref(false)
  
  // Manual entry form state
  const manualEntryDate = ref<Date>(new Date())
  const manualEntryStartTime = ref<Date>(new Date())
  const manualEntryEndTime = ref<Date>(new Date())
  const manualEntryBreakMinutes = ref(0)
  const manualEntryNotes = ref('')
  const hasWorkingHoursForSelectedDay = ref(true)

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
      const today = formatDateString(new Date())
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
      const today = formatDateString(todayDateObj)
      const oneYearLater = new Date()
      oneYearLater.setFullYear(oneYearLater.getFullYear() + 1)
      const endDate = formatDateString(oneYearLater)

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
  // Refresh specific day(s) instead of full reload
  const refreshDays = async (dates: string[]) => {
    if (dates.length === 0) return

    try {
      // Find min and max dates for the API call
      const sortedDates = dates.sort()
      const startDate = sortedDates[0]
      const endDate = sortedDates[sortedDates.length - 1]

      // Fetch summaries for the date range
      const summaries = await TimeEntriesService.getDailySummary(startDate, endDate)

      // Get public holidays for the years in the range
      const yearsNeeded = new Set([
        new Date(startDate).getFullYear(),
        new Date(endDate).getFullYear()
      ])
      const holidayResponses = await Promise.all(
        Array.from(yearsNeeded).map(y => getPublicHolidaysForYear(y))
      )
      const publicHolidays = holidayResponses.flat() as PublicHolidayResponse[]
      const summariesWithHolidays = mergePublicHolidays(summaries, publicHolidays)

      // Check if any summary has a conflict warning - only load warnings if needed
      const hasConflictWarning = summariesWithHolidays.some(s => s.conflictWarning != null)
      if (hasConflictWarning) {
        await loadWarnings(false)
      }

      // Update cache for each day
      summariesWithHolidays.forEach(summary => {
        if (summary.date) {
          dailySummaryCache.setCache(summary.date, summary)
        }
      })

      // Update the dailySummaries array - replace or add summaries for the refreshed days
      const summariesMap = new Map(dailySummaries.value.map(s => [s.date, s]))
      summariesWithHolidays.forEach(summary => {
        if (summary.date) {
          summariesMap.set(summary.date, summary)
        }
      })
      dailySummaries.value = Array.from(summariesMap.values()).sort((a, b) => 
        a.date && b.date ? a.date.localeCompare(b.date) : 0
      )

      // Update cache range metadata
      dailySummaryCache.updateCacheRange(startDate, endDate)
    } catch (error) {
      handleError(error, t('dashboard.loadError'))
    }
  }

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

      // Refresh today's summary since we just clocked in
      const today = formatDateString(new Date())
      await refreshDays([today])
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

      // Refresh today's summary since we just clocked out
      // refreshDays will check for conflict warnings and load them if needed
      const today = formatDateString(new Date())
      await refreshDays([today])

      await calculateOvertime()
    } catch (error: any) {
      handleError(error, t('dashboard.clockOutError'))
    }
  }

  // Quick clock out - clock out without clocking in first
  // Uses start time from today's working hours config and end time as "now"
  const quickClockOutNow = async () => {
    try {
      if (!workingHours.value) return

      const now = new Date()
      const dayOfWeek = now.getDay() === 0 ? 7 : now.getDay()
      const todayWorkingHours = workingHours.value.workingDays.find(wd => wd.weekday === dayOfWeek)

      // Validation: Check if working hours exist for today
      if (!todayWorkingHours || !todayWorkingHours.isWorkingDay || !todayWorkingHours.startTime) {
        handleWarning(t('dashboard.noWorkingHoursToday'))
        return
      }

      const [startHour, startMin] = todayWorkingHours.startTime.split(':').map(Number)

      if (startHour === undefined || startMin === undefined) {
        handleWarning(t('dashboard.noWorkingHoursToday'))
        return
      }

      const clockIn = new Date(now)
      clockIn.setHours(startHour, startMin, 0, 0)

      // Validation: Check if start time is before now (work time can't be negative)
      if (clockIn >= now) {
        handleWarning(t('dashboard.workStartTimeAfterNow'))
        return
      }

      // Create time entry with start time from config and end time as now
      await TimeEntriesService.createTimeEntry({
        clockIn: clockIn.toISOString(),
        clockOut: now.toISOString(),
        breakMinutes: todayWorkingHours.breakMinutes ?? 0,
        entryType: 'WORK' as any,
        notes: ''
      })

      handleSuccess(t('dashboard.clockOutSuccess'))

      // Refresh today's summary
      const today = formatDateString(now)
      await refreshDays([today])

      await calculateOvertime()
    } catch (error: any) {
      handleError(error, t('dashboard.clockOutError'))
    }
  }

  // Cancel active entry
  const cancelEntry = async () => {
    try {
      if (activeEntry.value?.id) {
        const entryDate = activeEntry.value.entryDate || formatDateString(new Date())
        await TimeEntriesService.deleteTimeEntry(activeEntry.value.id)
        handleSuccess(t('dashboard.entryCancelled'))
        activeEntry.value = null

        // Refresh the day where entry was deleted
        await refreshDays([entryDate])
        await calculateOvertime()
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
        breakMinutes: todayWorkingHours.breakMinutes ?? 0,
        entryType: 'WORK' as any, // API expects specific string literal type
        notes: ''
      })

      // No success toast - calendar update is immediate feedback

      // Refresh only the affected day(s) - entry date
      // refreshDays will check for conflict warnings and load them if needed
      const entryDate = formatDateString(today)
      await refreshDays([entryDate])

      await calculateOvertime()
    } catch (error: any) {
      handleError(error, t('dashboard.quickEntryError'))
    }
  }

  // Handle quick entry from calendar
  const handleQuickEntryFromCalendar = async (payload: { date: string; startTime: string; endTime: string; breakMinutes?: number }) => {
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
        breakMinutes: payload.breakMinutes ?? 0,
        entryType: 'WORK' as any, // API expects specific string literal type
        notes: ''
      })

      // No success toast - calendar update is immediate feedback

      // Refresh only the affected day(s) - entry date
      // refreshDays will check for conflict warnings and load them if needed
      const entryDate = formatDateString(date)
      await refreshDays([entryDate])

      await calculateOvertime()
    } catch (error: any) {
      handleError(error, t('dashboard.quickEntryError'))
    }
  }

  // Handle manual entry from calendar
  const handleManualEntryFromCalendar = async (payload: { date: string }) => {
    const date = new Date(payload.date)
    
    // Initialize date field
    manualEntryDate.value = new Date(date)
    
    manualEntryBreakMinutes.value = 0
    manualEntryNotes.value = ''
    hasWorkingHoursForSelectedDay.value = true
    
    // Automatically apply default working hours
    await applyDefaultWorkingHours(date)
    
    showManualEntryDialog.value = true
  }

  // Apply default working hours for manual entry
  const applyDefaultWorkingHours = async (selectedDate: Date) => {
    try {
      if (!workingHours.value) {
        workingHours.value = await WorkingHoursService.getWorkingHours()
      }

      // Get day of week (1=Monday, 7=Sunday)
      const dayOfWeek = selectedDate.getDay() === 0 ? 7 : selectedDate.getDay()

      // Find working hours for selected day
      const dayWorkingHours = workingHours.value.workingDays.find(
        wd => wd.weekday === dayOfWeek
      )

      if (!dayWorkingHours || !dayWorkingHours.isWorkingDay || !dayWorkingHours.startTime || !dayWorkingHours.endTime) {
        hasWorkingHoursForSelectedDay.value = false
        handleWarning(t('dashboard.noWorkingHoursForDay'))
        return
      }

      hasWorkingHoursForSelectedDay.value = true

      // Set time fields from working hours configuration
      const startTimeStr = dayWorkingHours.startTime
      const endTimeStr = dayWorkingHours.endTime
      const [startHour, startMin] = startTimeStr.split(':').map(Number)
      const [endHour, endMin] = endTimeStr.split(':').map(Number)

      if (startHour === undefined || startMin === undefined || endHour === undefined || endMin === undefined ||
          isNaN(startHour) || isNaN(startMin) || isNaN(endHour) || isNaN(endMin)) {
        hasWorkingHoursForSelectedDay.value = false
        handleWarning(t('dashboard.noWorkingHoursForDay'))
        return
      }

      const startTime = new Date()
      startTime.setHours(startHour, startMin, 0, 0)
      manualEntryStartTime.value = startTime

      const endTime = new Date()
      endTime.setHours(endHour, endMin, 0, 0)
      manualEntryEndTime.value = endTime
    } catch (error: any) {
      hasWorkingHoursForSelectedDay.value = false
      handleError(error, t('dashboard.defaultHoursError'))
    }
  }

  // Handle date change in manual entry - always apply default working hours
  const onManualEntryDateChange = async (date: Date) => {
    if (date) {
      await applyDefaultWorkingHours(date)
    }
  }

  // Create manual entry
  const createManualEntry = async () => {
    try {
      const date = manualEntryDate.value

      const clockIn = new Date(date)
      clockIn.setHours(manualEntryStartTime.value.getHours(), manualEntryStartTime.value.getMinutes(), 0, 0)

      const clockOut = new Date(date)
      clockOut.setHours(manualEntryEndTime.value.getHours(), manualEntryEndTime.value.getMinutes(), 0, 0)

      await TimeEntriesService.createTimeEntry({
        clockIn: clockIn.toISOString(),
        clockOut: clockOut.toISOString(),
        breakMinutes: manualEntryBreakMinutes.value || 0,
        entryType: 'WORK' as any,
        notes: manualEntryNotes.value
      })

      handleSuccess(t('dashboard.manualEntryCreated'))
      showManualEntryDialog.value = false

      // Refresh only the affected day(s) - entry date
      // refreshDays will check for conflict warnings and load them if needed
      const entryDate = formatDateString(date)
      await refreshDays([entryDate])

      await calculateOvertime()
    } catch (error: any) {
      handleError(error, t('dashboard.manualEntryError'))
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

  // Handle quick delete from calendar popover (for single entries only)
  const handleQuickDeleteFromCalendar = async (payload: { date: string; entry?: TimeEntryResponse; timeOffEntry?: TimeOffResponse }) => {
    const dateRange = { startDate: payload.date, endDate: payload.date }
    
    const reloadFn = async () => {
      await refreshDays([payload.date])
      await loadWarnings(false)
      await calculateOvertime()
    }
    
    if (payload.entry) {
      // Delete time entry
      await deleteWithUndo(
        'time-entry',
        payload.entry,
        async (id) => {
          await TimeEntriesService.deleteTimeEntry(id as number)
        },
        reloadFn,
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
    } else if (payload.timeOffEntry) {
      // Delete time off entry
      const timeOff = payload.timeOffEntry
      await deleteWithUndo(
        'time-off',
        timeOff,
        async (id) => {
          await TimeOffService.deleteTimeOff(id as number)
        },
        async () => {
          // For time-off, refresh all days in the range
          const datesToRefresh: string[] = []
          const [startYear, startMonth, startDay] = timeOff.startDate.split('-').map(Number)
          const [endYear, endMonth, endDay] = timeOff.endDate.split('-').map(Number)
          const start = new Date(startYear, startMonth - 1, startDay)
          const end = new Date(endYear, endMonth - 1, endDay)
          const current = new Date(start)
          while (current <= end) {
            const year = current.getFullYear()
            const month = String(current.getMonth() + 1).padStart(2, '0')
            const day = String(current.getDate()).padStart(2, '0')
            datesToRefresh.push(`${year}-${month}-${day}`)
            current.setDate(current.getDate() + 1)
          }
          await refreshDays(datesToRefresh)
          await loadWarnings(false)
          await loadNextVacation()
          await calculateOvertime()
        },
        (item) => {
          const typedItem = item as TimeOffResponse
          const startDate = new Date(typedItem.startDate)
          const endDate = new Date(typedItem.endDate)
          const startStr = startDate.toLocaleDateString(t('locale'), { day: '2-digit', month: '2-digit', year: 'numeric' })
          const endStr = endDate.toLocaleDateString(t('locale'), { day: '2-digit', month: '2-digit', year: 'numeric' })
          return t('timeOff.deleteSuccess') + ` (${startStr} - ${endStr})`
        },
        async (item) => {
          const typedItem = item as TimeOffResponse
          const createRequest: CreateTimeOffRequest = {
            timeOffType: typedItem.timeOffType,
            startDate: typedItem.startDate,
            endDate: typedItem.endDate,
            hoursPerDay: typedItem.hoursPerDay,
            notes: typedItem.notes
          }
          await TimeOffService.createTimeOff(createRequest)
        }
      )
    }
  }

  // Handle form saved - can receive date range for time off entries
  const handleFormSaved = async (dateRange?: { startDate: string; endDate: string }) => {
    // Refresh only the affected day(s) instead of full reload
    if (dateRange) {
      // Time off entry - refresh all days in the range
      const datesToRefresh: string[] = []

      // Parse dates as YYYY-MM-DD strings to avoid timezone issues
      const [startYear, startMonth, startDay] = dateRange.startDate.split('-').map(Number)
      const [endYear, endMonth, endDay] = dateRange.endDate.split('-').map(Number)

      const start = new Date(startYear, startMonth - 1, startDay)
      const end = new Date(endYear, endMonth - 1, endDay)
      const current = new Date(start)

      while (current <= end) {
        const year = current.getFullYear()
        const month = String(current.getMonth() + 1).padStart(2, '0')
        const day = String(current.getDate()).padStart(2, '0')
        datesToRefresh.push(`${year}-${month}-${day}`)
        current.setDate(current.getDate() + 1)
      }

      await refreshDays(datesToRefresh)
    } else if (selectedDate.value) {
      // Single day entry - refresh just that day
      await refreshDays([selectedDate.value])
    } else {
      // Fallback - refresh today
      const today = formatDateString(new Date())
      await refreshDays([today])
    }
    
    // Always reload warnings when form is saved (entries/time-off created/updated/deleted)
    // This ensures navbar updates immediately when warnings are added or removed
    await loadWarnings(false)
    
    // Reload next vacation since it may have changed
    await loadNextVacation()
    
    await calculateOvertime()
    
    // Update entries for the selected date if modal is open
    if (selectedDate.value) {
      const summary = dailySummaries.value.find(s => s.date === selectedDate.value)
      if (summary) {
        selectedEntries.value = summary.entries || []
        selectedTimeOffEntries.value = summary.timeOffEntries || []
        
        // Close modal if no entries remain
        if (selectedEntries.value.length === 0 && selectedTimeOffEntries.value.length === 0) {
          showEditDialog.value = false
        }
      } else {
        // If summary not found, close modal (date might be out of range)
        showEditDialog.value = false
      }
    }
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
    showManualEntryDialog,
    manualEntryDate,
    manualEntryStartTime,
    manualEntryEndTime,
    manualEntryBreakMinutes,
    manualEntryNotes,

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
    quickClockOutNow,
    cancelEntry,
    createQuickWorkEntry,
    handleQuickEntryFromCalendar,
    handleManualEntryFromCalendar,
    handleTimeOffFromCalendar,
    handleEditAllFromCalendar,
    handleQuickDeleteFromCalendar,
    handleFormSaved,
    applyDefaultWorkingHours,
    onManualEntryDateChange,
    createManualEntry
  }
}
