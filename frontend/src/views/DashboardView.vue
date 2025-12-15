<template>
  <div class="dashboard">
    <!-- Quick Actions & Stats -->
    <div class="quick-panel">
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

    <!-- Calendar -->
    <div class="dashboard-grid">
      <div class="calendar-section">
        <MonthlyCalendar
          :current-month="currentMonth"
          :daily-summaries="dailySummaries"
          :working-hours="workingHours"
          @month-change="handleMonthChange"
        />
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

    // Fetch daily summaries, public holidays, and working hours in parallel
    const [summaries, publicHolidays, workingHoursData] = await Promise.all([
      TimeEntriesService.getDailySummary(startDateStr, endDateStr),
      PublicHolidaysService.getPublicHolidays(year),
      WorkingHoursService.getWorkingHours(currentUser.value?.id || 0)
    ])

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
  padding: 1rem 2rem 2rem 2rem;
}

/* Quick Panel */
.quick-panel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
  margin-bottom: 2rem;
}

.quick-panel h3 {
  margin: 0 0 1rem 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: #1f2937;
}

/* Quick Actions Section */
.quick-actions-section {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 1.5rem;
}

.action-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.action-card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
  cursor: pointer;
  text-align: center;
  position: relative;
}

.action-card:hover:not(.disabled) {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.action-card.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-icon {
  font-size: 2.5rem;
  margin-bottom: 0.75rem;
  display: block;
}

.action-label {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1f2937;
}

.action-hint {
  display: block;
  margin-top: 0.5rem;
  color: #6c757d;
  font-size: 0.75rem;
}

.active-entry-cards {
  display: flex;
  gap: 1rem;
}

.active-entry-cards .action-card {
  flex: 1;
}

/* Action card colors */
.action-card.action-clock-in {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.action-card.action-clock-in .action-icon,
.action-card.action-clock-in .action-label {
  color: white;
}

.action-card.action-clock-out {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
}

.action-card.action-clock-out .action-icon,
.action-card.action-clock-out .action-label {
  color: white;
}

.action-card.action-cancel {
  background: linear-gradient(135deg, #6b7280 0%, #4b5563 100%);
}

.action-card.action-cancel .action-icon,
.action-card.action-cancel .action-label {
  color: white;
}

.action-card.action-quick-entry {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
}

.action-card.action-quick-entry .action-icon,
.action-card.action-quick-entry .action-label {
  color: white;
}

/* Statistics Section */
.stats-section {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 1.5rem;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
  text-align: center;
}

.stat-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.stat-label {
  font-size: 0.85rem;
  color: #6c757d;
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1f2937;
  line-height: 1;
}

/* Stat card gradient colors */
.stat-card.stat-vacation {
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
}

.stat-card.stat-vacation .stat-label,
.stat-card.stat-vacation .stat-value {
  color: white;
}

.stat-card.stat-current {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
}

.stat-card.stat-current .stat-label,
.stat-card.stat-current .stat-value {
  color: white;
}

.stat-card.stat-last {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.stat-card.stat-last .stat-label,
.stat-card.stat-last .stat-value {
  color: white;
}

.stat-card.stat-average {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.stat-card.stat-average .stat-label,
.stat-card.stat-average .stat-value {
  color: white;
}

/* Calendar */
.dashboard-grid {
  display: flex;
  justify-content: center;
}

.calendar-section {
  width: 75%;
  min-height: 500px;
}

/* Responsive layout */
@media (max-width: 1024px) {
  .quick-panel {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .calendar-section {
    width: 100%;
    min-height: auto;
  }

  .active-entry-cards {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
