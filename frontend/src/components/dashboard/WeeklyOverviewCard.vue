<template>
  <div class="info-card weekly-overview-card">
    <div class="card-header">
      <span class="card-icon">📊</span>
      <h4>{{ t('dashboard.weeklyOverview.title') }}</h4>
      <span class="week-label">{{ weekLabel }}</span>
    </div>
    
    <div class="card-content">
      <!-- Day status indicators -->
      <div class="days-row">
        <div
          v-for="day in weekDays"
          :key="day.date"
          class="day-indicator"
          :class="day.statusClass"
          :title="day.tooltip"
        >
          <span class="day-label">{{ day.label }}</span>
          <span class="day-icon">{{ day.icon }}</span>
        </div>
      </div>
      
      <!-- Weekly totals -->
      <div class="weekly-totals">
        <div class="total-hours">
          <span class="total-value">{{ formatHours(totalHoursWorked) }}</span>
          <span class="total-separator">/</span>
          <span class="total-target">{{ formatHours(totalTargetHours) }}</span>
        </div>
        
        <!-- Progress bar -->
        <div class="progress-bar">
          <div 
            class="progress-fill" 
            :class="progressClass"
            :style="{ width: progressPercentage + '%' }"
          ></div>
        </div>
        
        <!-- Overtime/Undertime -->
        <div class="overtime-display" :class="overtimeClass">
          <span>{{ overtimeText }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DailySummaryResponse, WorkingHoursResponse } from '@/api/generated'

const props = defineProps<{
  dailySummaries: DailySummaryResponse[]
  workingHours: WorkingHoursResponse | null
}>()

const { t } = useI18n()

// Get current week's Monday-Sunday
const getWeekDates = () => {
  const today = new Date()
  const dayOfWeek = today.getDay()
  const monday = new Date(today)
  // Adjust to get Monday (day 0 = Sunday, so we need special handling)
  const daysFromMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1
  monday.setDate(today.getDate() - daysFromMonday)
  monday.setHours(0, 0, 0, 0)
  
  const dates: Date[] = []
  for (let i = 0; i < 7; i++) {
    const date = new Date(monday)
    date.setDate(monday.getDate() + i)
    dates.push(date)
  }
  return dates
}

// Format date to YYYY-MM-DD
const formatDateString = (date: Date): string => {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

// Week label (e.g., "Week 52" or "Dec 16-22")
const weekLabel = computed(() => {
  const dates = getWeekDates()
  const start = dates[0]!
  const end = dates[6]!
  const startDay = start.getDate()
  const endDay = end.getDate()
  const month = start.toLocaleDateString(undefined, { month: 'short' })
  return `${month} ${startDay}-${endDay}`
})

// Day labels
const dayLabels = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']

// Get working day config for a specific day of week
const getWorkingDayConfig = (dayOfWeek: number) => {
  if (!props.workingHours?.workingDays) return null
  // dayOfWeek: 0 = Sunday, 1 = Monday, etc.
  const weekday = dayOfWeek === 0 ? 7 : dayOfWeek
  return props.workingHours.workingDays.find(wd => wd.weekday === weekday) || null
}

// Get summary for a specific date
const getSummaryForDate = (dateStr: string): DailySummaryResponse | null => {
  return props.dailySummaries.find(s => s.date === dateStr) || null
}

// Process week days
const weekDays = computed(() => {
  const dates = getWeekDates()
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  
  return dates.map((date, index) => {
    const dateStr = formatDateString(date)
    const summary = getSummaryForDate(dateStr)
    const config = getWorkingDayConfig(date.getDay())
    const isToday = date.getTime() === today.getTime()
    const isPast = date < today
    const isFuture = date > today
    const isWorkingDay = config?.isWorkingDay ?? false
    // Hours field already contains net hours (break subtracted when saved)
    const targetHours = config?.hours ?? 0
    const actualHours = summary?.actualHours ?? 0
    
    // Determine status
    let statusClass = ''
    let icon = ''
    let tooltip = ''
    
    // Check for time-off
    const hasTimeOff = summary?.timeOffEntries?.length ?? 0 > 0
    const timeOffType = summary?.timeOffEntries?.[0]?.timeOffType
    
    // Check for recurring off-day
    const hasRecurringOff = summary?.recurringOffDays?.length ?? 0 > 0
    
    if (hasTimeOff) {
      if (timeOffType === 'VACATION') {
        statusClass = 'day-vacation'
        icon = '🏝️'
        tooltip = t('timeOff.types.VACATION')
      } else if (timeOffType === 'SICK' || timeOffType === 'CHILD_SICK') {
        statusClass = 'day-sick'
        icon = '😷'
        tooltip = t('timeOff.types.SICK')
      } else if (timeOffType === 'PUBLIC_HOLIDAY') {
        statusClass = 'day-holiday'
        icon = '🎊'
        tooltip = t('timeOff.types.PUBLIC_HOLIDAY')
      } else if (timeOffType === 'PERSONAL') {
        statusClass = 'day-personal'
        icon = '🏠'
        tooltip = t('timeOff.types.PERSONAL')
      } else {
        statusClass = 'day-off'
        icon = '📴'
        tooltip = t('dashboard.calendar.timeOff')
      }
    } else if (hasRecurringOff) {
      statusClass = 'day-off'
      icon = '📴'
      tooltip = t('dashboard.calendar.recurringOffDay')
    } else if (!isWorkingDay) {
      statusClass = 'day-off'
      icon = '—'
      tooltip = t('dashboard.todayStatus.nonWorkingDay')
    } else if (isFuture) {
      statusClass = 'day-future'
      icon = '—'
      tooltip = `${formatHours(targetHours)} ${t('dashboard.weeklyOverview.planned')}`
    } else if (actualHours >= targetHours) {
      statusClass = 'day-complete'
      icon = '✓'
      tooltip = `${formatHours(actualHours)} / ${formatHours(targetHours)}`
    } else if (actualHours > 0) {
      statusClass = 'day-partial'
      icon = '⏳'
      tooltip = `${formatHours(actualHours)} / ${formatHours(targetHours)}`
    } else if (isPast) {
      statusClass = 'day-missing'
      icon = '✗'
      tooltip = t('dashboard.weeklyOverview.noEntry')
    } else if (isToday) {
      statusClass = 'day-today'
      icon = '•'
      tooltip = t('dashboard.todayStatus.title')
    }
    
    // Mark today
    if (isToday) {
      statusClass += ' is-today'
    }
    
    return {
      date: dateStr,
      label: dayLabels[index],
      icon,
      statusClass,
      tooltip,
      actualHours,
      targetHours: isWorkingDay && !hasTimeOff && !hasRecurringOff ? targetHours : 0
    }
  })
})

// Calculate totals
const totalHoursWorked = computed(() => {
  return weekDays.value.reduce((sum, day) => sum + day.actualHours, 0)
})

const totalTargetHours = computed(() => {
  return weekDays.value.reduce((sum, day) => sum + day.targetHours, 0)
})

// Progress percentage
const progressPercentage = computed(() => {
  if (totalTargetHours.value === 0) return 0
  return Math.min((totalHoursWorked.value / totalTargetHours.value) * 100, 100)
})

// Progress class
const progressClass = computed(() => {
  if (progressPercentage.value >= 100) return 'complete'
  if (progressPercentage.value >= 75) return 'almost'
  return 'partial'
})

// Overtime/undertime
const overtime = computed(() => totalHoursWorked.value - totalTargetHours.value)

const overtimeClass = computed(() => {
  if (overtime.value > 0) return 'overtime-positive'
  if (overtime.value < 0) return 'overtime-negative'
  return 'overtime-neutral'
})

const overtimeText = computed(() => {
  if (overtime.value > 0) {
    return `+${formatHours(overtime.value)} ${t('dashboard.weeklyOverview.overtime')}`
  } else if (overtime.value < 0) {
    return `${formatHours(overtime.value)} ${t('dashboard.weeklyOverview.undertime')}`
  }
  return t('dashboard.weeklyOverview.onTarget')
})

// Format hours helper
const formatHours = (hours: number): string => {
  return hours.toFixed(1) + 'h'
}
</script>

<style scoped>
.info-card {
  background: var(--p-card-background);
  border-radius: var(--tt-radius-md);
  padding: var(--tt-spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);
}

.card-icon {
  font-size: 1.25rem;
}

.card-header h4 {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--tt-text-primary);
  flex: 1;
}

.week-label {
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

/* Days row */
.days-row {
  display: flex;
  justify-content: space-between;
  gap: var(--tt-spacing-xs);
}

.day-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  flex: 1;
  padding: var(--tt-spacing-xs);
  border-radius: var(--tt-radius-sm);
  cursor: default;
  transition: background 0.2s ease;
}

.day-indicator:hover {
  background: var(--tt-bg-light);
}

.day-label {
  font-size: 0.65rem;
  font-weight: 500;
  color: var(--tt-text-secondary);
  text-transform: uppercase;
}

.day-icon {
  font-size: 1rem;
  line-height: 1;
}

/* Day states */
.day-indicator.day-complete .day-icon {
  color: var(--tt-emerald-from);
}

.day-indicator.day-partial .day-icon {
  color: #f59e0b;
}

.day-indicator.day-missing .day-icon {
  color: #ef4444;
}

.day-indicator.day-future .day-icon {
  color: var(--tt-text-tertiary);
}

.day-indicator.day-off .day-icon {
  color: var(--tt-text-tertiary);
}

.day-indicator.day-vacation {
  background: rgba(16, 185, 129, 0.1);
}

.day-indicator.day-sick {
  background: rgba(245, 158, 11, 0.1);
}

.day-indicator.day-holiday {
  background: rgba(59, 130, 246, 0.1);
}

.day-indicator.day-personal {
  background: rgba(139, 92, 246, 0.1);
}

.day-indicator.day-today {
  background: rgba(16, 185, 129, 0.1);
}

.day-indicator.is-today {
  border: 2px solid var(--tt-emerald-from);
}

/* Weekly totals */
.weekly-totals {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-xs);
  padding-top: var(--tt-spacing-xs);
  border-top: 1px solid var(--tt-bg-light);
}

.total-hours {
  display: flex;
  align-items: baseline;
  gap: var(--tt-spacing-xs);
}

.total-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--tt-text-primary);
}

.total-separator {
  color: var(--tt-text-tertiary);
}

.total-target {
  font-size: 0.875rem;
  color: var(--tt-text-secondary);
}

/* Progress bar */
.progress-bar {
  height: 6px;
  background: var(--tt-bg-light);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-fill.partial {
  background: linear-gradient(90deg, #fbbf24 0%, #f59e0b 100%);
}

.progress-fill.almost {
  background: linear-gradient(90deg, #34d399 0%, #10b981 100%);
}

.progress-fill.complete {
  background: linear-gradient(90deg, var(--tt-emerald-from) 0%, var(--tt-emerald-to) 100%);
}

/* Overtime display */
.overtime-display {
  font-size: 0.8rem;
  font-weight: 500;
}

.overtime-display.overtime-positive {
  color: var(--tt-emerald-from);
}

.overtime-display.overtime-negative {
  color: #ef4444;
}

.overtime-display.overtime-neutral {
  color: var(--tt-text-secondary);
}
</style>
