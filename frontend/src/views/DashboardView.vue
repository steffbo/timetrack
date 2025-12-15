<template>
  <div class="dashboard">
    <!-- Quick Actions & Stats -->
    <div class="quick-panel">
      <!-- Quick Actions -->
      <div class="quick-actions">
        <h3>{{ t('dashboard.quickActions') }}</h3>
        <div class="action-buttons">
          <!-- Clock In/Out Button -->
          <div v-if="!activeEntry" class="action-button-wrapper">
            <Button
              :label="t('dashboard.clockInNow')"
              icon="pi pi-play-circle"
              severity="success"
              size="large"
              @click="clockInNow"
              :disabled="!hasTodayWorkingHours"
              class="action-button"
            />
            <small v-if="!hasTodayWorkingHours" class="button-hint">
              {{ t('dashboard.noWorkingHoursToday') }}
            </small>
          </div>
          <div v-else class="active-entry-actions">
            <Button
              :label="t('dashboard.clockOutNow')"
              icon="pi pi-stop-circle"
              severity="danger"
              size="large"
              @click="clockOutNow"
              class="action-button"
            />
            <Button
              :label="t('dashboard.cancelEntry')"
              icon="pi pi-times"
              severity="secondary"
              size="large"
              outlined
              @click="cancelEntry"
              class="action-button"
            />
          </div>

          <!-- Quick Work Entry -->
          <div class="action-button-wrapper">
            <Button
              :label="t('dashboard.quickWorkEntry')"
              icon="pi pi-bolt"
              severity="info"
              size="large"
              outlined
              @click="createQuickWorkEntry"
              :disabled="!hasTodayWorkingHours"
              class="action-button"
            />
            <small v-if="!hasTodayWorkingHours" class="button-hint">
              {{ t('dashboard.noWorkingHoursToday') }}
            </small>
          </div>
        </div>
      </div>

      <!-- Statistics -->
      <div class="quick-stats">
        <h3>{{ t('dashboard.overview') }}</h3>
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon">
              <i class="pi pi-calendar-plus"></i>
            </div>
            <div class="stat-content">
              <span class="stat-label">{{ t('dashboard.nextVacation') }}</span>
              <span class="stat-value">{{ nextVacationText }}</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">
              <i class="pi pi-clock"></i>
            </div>
            <div class="stat-content">
              <span class="stat-label">{{ t('dashboard.overtimeThisMonth') }}</span>
              <span class="stat-value">{{ formatOvertime(overtimeThisMonth) }}</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">
              <i class="pi pi-history"></i>
            </div>
            <div class="stat-content">
              <span class="stat-label">{{ t('dashboard.overtimeLastMonth') }}</span>
              <span class="stat-value">{{ formatOvertime(overtimeLastMonth) }}</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">
              <i class="pi pi-chart-line"></i>
            </div>
            <div class="stat-content">
              <span class="stat-label">{{ t('dashboard.overtimeAverage') }}</span>
              <span class="stat-value">{{ formatOvertime(overtimeAverage) }}</span>
            </div>
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

    // This month - calculate daily average instead of total
    const thisMonthStart = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().split('T')[0]
    const todayStr = today.toISOString().split('T')[0]
    const thisMonthSummaries = await TimeEntriesService.getDailySummary(thisMonthStart, todayStr)

    // Filter to only completed days (not future days)
    const completedDays = thisMonthSummaries.filter(day => {
      const dayDate = new Date(day.date)
      return dayDate <= today
    })

    if (completedDays.length > 0) {
      const totalOvertime = completedDays.reduce((sum, day) => sum + (day.actualHours - day.expectedHours), 0)
      overtimeThisMonth.value = totalOvertime / completedDays.length
    } else {
      overtimeThisMonth.value = 0
    }

    // Last month
    const lastMonthStart = new Date(today.getFullYear(), today.getMonth() - 1, 1).toISOString().split('T')[0]
    const lastMonthEnd = new Date(today.getFullYear(), today.getMonth(), 0).toISOString().split('T')[0]
    const lastMonthSummaries = await TimeEntriesService.getDailySummary(lastMonthStart, lastMonthEnd)
    overtimeLastMonth.value = lastMonthSummaries.reduce((sum, day) => sum + (day.actualHours - day.expectedHours), 0)

    // 12-month average
    const twelveMonthsAgo = new Date(today.getFullYear(), today.getMonth() - 11, 1).toISOString().split('T')[0]
    const twelveMonthSummaries = await TimeEntriesService.getDailySummary(twelveMonthsAgo, thisMonthEnd)
    const totalOvertime = twelveMonthSummaries.reduce((sum, day) => sum + (day.actualHours - day.expectedHours), 0)
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
  padding: 0;
}

/* Quick Panel */
.quick-panel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: var(--surface-card);
  border-radius: var(--border-radius);
  border: 1px solid var(--surface-border);
}

.quick-panel h3 {
  margin: 0 0 1rem 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--p-text-color);
}

/* Quick Actions */
.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.action-button-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.action-button {
  width: 100%;
}

.active-entry-actions {
  display: flex;
  gap: 0.75rem;
}

.button-hint {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
  margin-left: 0.5rem;
}

/* Statistics */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: var(--surface-50);
  border-radius: var(--border-radius);
  border: 1px solid var(--surface-200);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  background: var(--primary-color);
  color: white;
  font-size: 1.5rem;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
}

.stat-label {
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.stat-value {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--p-text-color);
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
}
</style>
