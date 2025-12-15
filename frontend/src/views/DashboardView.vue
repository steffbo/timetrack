<template>
  <div class="dashboard">
    <div class="dashboard-layout">
      <!-- Left Side: Calendar -->
      <div class="calendar-section">
        <MonthlyCalendar
          :current-month="currentMonth"
          :daily-summaries="dailySummaries"
          :working-hours="workingHours"
          :half-day-holidays-enabled="currentUser?.halfDayHolidaysEnabled || false"
          @month-change="handleMonthChange"
        />
      </div>

      <!-- Right Side: Actions & Stats -->
      <div class="sidebar-section">
        <!-- Warnings Card -->
        <WarningsCard />

        <!-- Quick Actions -->
        <div class="quick-actions-section">
          <h3>{{ t('dashboard.quickActions') }}</h3>
          <div class="action-cards">
            <!-- Clock In/Out Card -->
            <div
              v-if="!activeEntry"
              class="action-card action-clock-in"
              :class="{ disabled: !hasTodayWorkingHours }"
              @click="hasTodayWorkingHours && clockInNow()"
            >
              <i class="pi pi-play-circle action-icon"></i>
              <div class="action-label">{{ t('dashboard.clockInNow') }}</div>
              <small v-if="!hasTodayWorkingHours" class="action-hint">
                {{ t('dashboard.noWorkingHoursToday') }}
              </small>
            </div>
            <div v-else class="active-entry-cards">
              <div class="action-card action-clock-out" @click="clockOutNow">
                <i class="pi pi-stop-circle action-icon"></i>
                <div class="action-label">{{ t('dashboard.clockOutNow') }}</div>
              </div>
              <div class="action-card action-cancel" @click="cancelEntry">
                <i class="pi pi-times action-icon"></i>
                <div class="action-label">{{ t('dashboard.cancelEntry') }}</div>
              </div>
            </div>

            <!-- Quick Work Entry Card -->
            <div
              class="action-card action-quick-entry"
              :class="{ disabled: !hasTodayWorkingHours }"
              @click="hasTodayWorkingHours && createQuickWorkEntry()"
            >
              <i class="pi pi-bolt action-icon"></i>
              <div class="action-label">{{ t('dashboard.quickWorkEntry') }}</div>
              <small v-if="!hasTodayWorkingHours" class="action-hint">
                {{ t('dashboard.noWorkingHoursToday') }}
              </small>
            </div>
          </div>
        </div>

        <!-- Statistics -->
        <div class="stats-section">
          <h3>{{ t('dashboard.overview') }}</h3>
          <div class="stats-grid">
            <div class="stat-card stat-vacation">
              <div class="stat-label">{{ t('dashboard.nextVacation') }}</div>
              <div class="stat-value">{{ nextVacationText }}</div>
            </div>
            <div class="stat-card stat-current">
              <div class="stat-label">{{ t('dashboard.overtimeThisMonth') }}</div>
              <div class="stat-value">{{ formatOvertime(overtimeThisMonth) }}</div>
            </div>
            <div class="stat-card stat-last">
              <div class="stat-label">{{ t('dashboard.overtimeLastMonth') }}</div>
              <div class="stat-value">{{ formatOvertime(overtimeLastMonth) }}</div>
            </div>
            <div class="stat-card stat-average">
              <div class="stat-label">{{ t('dashboard.overtimeAverage') }}</div>
              <div class="stat-value">{{ formatOvertime(overtimeAverage) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import { useAuth } from '@/composables/useAuth'
import MonthlyCalendar from '@/components/dashboard/MonthlyCalendar.vue'
import WarningsCard from '@/components/dashboard/WarningsCard.vue'
import { TimeEntriesService, PublicHolidaysService, WorkingHoursService, TimeOffService } from '@/api/generated'
import type { DailySummaryResponse, PublicHolidayResponse, TimeOffResponse, WorkingHoursResponse, TimeEntryResponse } from '@/api/generated'

const { t } = useI18n()
const { currentUser } = useAuth()
const toast = useToast()

const currentMonth = ref<Date>(new Date())
const dailySummaries = ref<DailySummaryResponse[]>([])
const workingHours = ref<WorkingHoursResponse | null>(null)
const loading = ref(false)
const activeEntry = ref<TimeEntryResponse | null>(null)
const nextVacation = ref<TimeOffResponse | null>(null)
const overtimeThisMonth = ref<number>(0)
const overtimeLastMonth = ref<number>(0)
const overtimeAverage = ref<number>(0)

// Check if today has working hours configured
const hasTodayWorkingHours = computed(() => {
  if (!workingHours.value) return false
  const today = new Date()
  const dayOfWeek = today.getDay() === 0 ? 7 : today.getDay()
  const workingDay = workingHours.value.workingDays?.find(wd => wd.weekday === dayOfWeek)
  return workingDay?.isWorkingDay || false
})

// Next vacation countdown text
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

const loadDailySummaries = async () => {
  loading.value = true
  try {
    const year = currentMonth.value.getFullYear()
    const month = currentMonth.value.getMonth()

    // Get first and last day of the month
    const startDate = new Date(year, month, 1)
    const endDate = new Date(year, month + 1, 0)

    // Calculate how many days from adjacent months to fetch
    const firstDayOfWeek = startDate.getDay()
    const emptyDaysAtStart = firstDayOfWeek === 0 ? 6 : firstDayOfWeek - 1

    // Calculate end padding days - only fill the last row
    const daysInCurrentMonth = endDate.getDate()
    const usedCells = emptyDaysAtStart + daysInCurrentMonth
    const remainderInWeek = usedCells % 7
    const emptyDaysAtEnd = remainderInWeek === 0 ? 0 : 7 - remainderInWeek

    // Fetch data starting from the first day shown (including previous month days)
    const fetchStartDate = new Date(year, month, 1 - emptyDaysAtStart)
    // For end date, add emptyDaysAtEnd to the last day of current month
    const fetchEndDate = new Date(year, month, daysInCurrentMonth + emptyDaysAtEnd)

    // Format dates in local timezone to avoid timezone conversion issues
    const startDateStr = `${fetchStartDate.getFullYear()}-${String(fetchStartDate.getMonth() + 1).padStart(2, '0')}-${String(fetchStartDate.getDate()).padStart(2, '0')}`
    const endDateStr = `${fetchEndDate.getFullYear()}-${String(fetchEndDate.getMonth() + 1).padStart(2, '0')}-${String(fetchEndDate.getDate()).padStart(2, '0')}`

    // Determine which years we need holidays for (adjacent months might cross year boundaries)
    const yearsNeeded = new Set([fetchStartDate.getFullYear(), fetchEndDate.getFullYear()])

    // Fetch daily summaries, public holidays for all needed years, and working hours in parallel
    const [summaries, ...holidayResponses] = await Promise.all([
      TimeEntriesService.getDailySummary(startDateStr, endDateStr),
      ...Array.from(yearsNeeded).map(y => PublicHolidaysService.getPublicHolidays(y)),
      WorkingHoursService.getWorkingHours(currentUser.value?.id || 0)
    ])

    // Combine holidays from all years and get working hours (last item in Promise.all results)
    const publicHolidays = holidayResponses.slice(0, -1).flat()
    const workingHoursData = holidayResponses[holidayResponses.length - 1] as WorkingHoursResponse

    workingHours.value = workingHoursData

    // Merge public holidays into daily summaries
    const summariesWithHolidays = summaries.map(summary => {
      // Check if this date is a public holiday
      const holiday = publicHolidays.find(h => h.date === summary.date)

      if (holiday) {
        // Create a time-off entry for the public holiday
        const holidayTimeOff: TimeOffResponse = {
          id: 0, // Dummy ID for display purposes
          userId: currentUser.value?.id || 0,
          startDate: holiday.date!,
          endDate: holiday.date!,
          timeOffType: 'PUBLIC_HOLIDAY' as any,
          notes: holiday.name
        }

        // Add the holiday to the beginning of timeOffEntries (highest precedence)
        return {
          ...summary,
          timeOffEntries: [holidayTimeOff, ...(summary.timeOffEntries || [])]
        }
      }

      return summary
    })

    dailySummaries.value = summariesWithHolidays
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('dashboard.loadError'),
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const handleMonthChange = (newMonth: Date) => {
  currentMonth.value = newMonth
  loadDailySummaries()
}

// Load active time entry
const loadActiveEntry = async () => {
  try {
    const today = new Date().toISOString().split('T')[0]
    const entries = await TimeEntriesService.getTimeEntries(today, today)
    activeEntry.value = entries.find(e => e.isActive) || null
  } catch (error) {
    console.error('Error loading active entry:', error)
  }
}

// Load next vacation
const loadNextVacation = async () => {
  try {
    const today = new Date().toISOString().split('T')[0]
    const oneYearLater = new Date()
    oneYearLater.setFullYear(oneYearLater.getFullYear() + 1)
    const endDate = oneYearLater.toISOString().split('T')[0]

    const vacations = await TimeOffService.getTimeOffEntries(today, endDate)
    const upcomingVacations = vacations.filter(v =>
      v.timeOffType === 'VACATION' && new Date(v.startDate) >= new Date(today)
    ).sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime())

    nextVacation.value = upcomingVacations[0] || null
  } catch (error) {
    console.error('Error loading next vacation:', error)
  }
}

// Calculate overtime statistics
const calculateOvertime = async () => {
  try {
    const today = new Date()

    // This month - only count days with actual entries (missing days are neutral)
    const thisMonthStart = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().split('T')[0]
    const todayStr = today.toISOString().split('T')[0]
    const thisMonthSummaries = await TimeEntriesService.getDailySummary(thisMonthStart, todayStr)

    // Only count days with time entries
    const daysWithEntries = thisMonthSummaries.filter(day => day.actualHours > 0)
    overtimeThisMonth.value = daysWithEntries.reduce((sum, day) => sum + (day.actualHours - day.expectedHours), 0)

    // Last month - only count days with actual entries (missing days are neutral)
    const lastMonthStart = new Date(today.getFullYear(), today.getMonth() - 1, 1).toISOString().split('T')[0]
    const lastMonthEnd = new Date(today.getFullYear(), today.getMonth(), 0).toISOString().split('T')[0]
    const lastMonthSummaries = await TimeEntriesService.getDailySummary(lastMonthStart, lastMonthEnd)
    overtimeLastMonth.value = lastMonthSummaries
      .filter(day => day.actualHours > 0) // Only count days with time entries
      .reduce((sum, day) => sum + (day.actualHours - day.expectedHours), 0)

    // 12-month average - only count days with actual entries (missing days are neutral)
    const twelveMonthsAgo = new Date(today.getFullYear(), today.getMonth() - 11, 1).toISOString().split('T')[0]
    const twelveMonthSummaries = await TimeEntriesService.getDailySummary(twelveMonthsAgo, todayStr)
    const daysWithEntriesYear = twelveMonthSummaries.filter(day => day.actualHours > 0)
    const totalOvertime = daysWithEntriesYear.reduce((sum, day) => sum + (day.actualHours - day.expectedHours), 0)
    overtimeAverage.value = totalOvertime / 12
  } catch (error) {
    console.error('Error calculating overtime:', error)
  }
}

// Format overtime hours
const formatOvertime = (hours: number) => {
  const sign = hours >= 0 ? '+' : ''
  return `${sign}${hours.toFixed(1)}h`
}

// Clock in now
const clockInNow = async () => {
  try {
    await TimeEntriesService.clockIn({ notes: '' })
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.clockInSuccess'),
      life: 3000
    })
    await loadActiveEntry()
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('dashboard.clockInError'),
      life: 3000
    })
  }
}

// Clock out now
const clockOutNow = async () => {
  try {
    await TimeEntriesService.clockOut({ notes: '' })
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.clockOutSuccess'),
      life: 3000
    })
    activeEntry.value = null
    await loadDailySummaries()
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('dashboard.clockOutError'),
      life: 3000
    })
  }
}

// Cancel active entry
const cancelEntry = async () => {
  try {
    if (activeEntry.value?.id) {
      await TimeEntriesService.deleteTimeEntry(activeEntry.value.id)
      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('dashboard.entryCancelled'),
        life: 3000
      })
      activeEntry.value = null
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('dashboard.cancelError'),
      life: 3000
    })
  }
}

// Create quick work entry
const createQuickWorkEntry = async () => {
  try {
    if (!workingHours.value) return

    const today = new Date()
    const dayOfWeek = today.getDay() === 0 ? 7 : today.getDay()
    const todayWorkingHours = workingHours.value.workingDays.find(wd => wd.weekday === dayOfWeek)

    if (!todayWorkingHours || !todayWorkingHours.isWorkingDay) {
      toast.add({
        severity: 'warn',
        summary: t('warning'),
        detail: t('dashboard.noWorkingHoursToday'),
        life: 3000
      })
      return
    }

    const [startHour, startMin] = todayWorkingHours.startTime.split(':').map(Number)
    const [endHour, endMin] = todayWorkingHours.endTime.split(':').map(Number)

    const clockIn = new Date(today)
    clockIn.setHours(startHour, startMin, 0, 0)

    const clockOut = new Date(today)
    clockOut.setHours(endHour, endMin, 0, 0)

    await TimeEntriesService.createTimeEntry({
      clockIn: clockIn.toISOString(),
      clockOut: clockOut.toISOString(),
      breakMinutes: 0,
      entryType: 'WORK',
      notes: ''
    })

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.quickEntryCreated'),
      life: 3000
    })

    await loadDailySummaries()
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: error.body?.message || t('dashboard.quickEntryError'),
      life: 3000
    })
  }
}

onMounted(async () => {
  await Promise.all([
    loadDailySummaries(),
    loadActiveEntry(),
    loadNextVacation(),
    calculateOvertime()
  ])
})
</script>

<style scoped>
.dashboard {
  padding: var(--tt-view-padding);
  max-width: 100%;
  overflow-x: hidden;
}

/* Main Layout: Calendar Left, Sidebar Right */
.dashboard-layout {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 2rem;
  align-items: start;
  max-width: 100%;
}

/* Calendar Section */
.calendar-section {
  min-height: 600px;
  max-width: 100%;
  overflow-x: auto;
}

/* Sidebar Section */
.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-lg);
}

.sidebar-section h3 {
  margin: 0 0 var(--tt-spacing-md) 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
}

/* Quick Actions Section */
.quick-actions-section {
  background: #f8f9fa;
  border-radius: var(--tt-radius-md);
  padding: var(--tt-card-padding);
}

.action-cards {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

/* Styles for action cards are now in shared CSS */

/* Responsive layout */
@media (max-width: 1200px) {
  .dashboard-layout {
    grid-template-columns: 1fr 350px;
  }
}

@media (max-width: 1024px) {
  .dashboard-layout {
    grid-template-columns: 1fr;
    gap: var(--tt-spacing-lg);
  }

  .sidebar-section {
    order: -1; /* Show sidebar before calendar on mobile */
  }

  .calendar-section {
    min-height: auto;
  }
}

@media (max-width: 768px) {
  .dashboard {
    padding: var(--tt-view-padding-mobile);
  }

  .dashboard-layout {
    gap: var(--tt-spacing-md);
  }

  .quick-actions-section,
  .stats-section {
    padding: var(--tt-spacing-md);
  }

  .sidebar-section h3 {
    font-size: 1.1rem;
  }
}

@media (max-width: 480px) {
  .dashboard {
    padding: var(--tt-view-padding-xs);
  }

  .dashboard-layout {
    gap: var(--tt-spacing-sm);
  }

  .quick-actions-section,
  .stats-section {
    padding: var(--tt-spacing-sm);
    border-radius: var(--tt-radius-sm);
  }

  .action-card {
    padding: var(--tt-spacing-sm);
  }

  .stat-card {
    padding: var(--tt-spacing-sm);
  }
}
</style>
