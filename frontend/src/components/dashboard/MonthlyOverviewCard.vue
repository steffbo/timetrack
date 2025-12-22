<template>
  <div class="info-card monthly-overview-card">
    <div class="card-header">
      <span class="card-icon">📆</span>
      <h4>{{ t('dashboard.monthlyOverview.title') }}</h4>
      <span class="month-label">{{ monthLabel }}</span>
    </div>
    
    <div class="card-content">
      <!-- Monthly stats -->
      <div class="monthly-stats">
        <div class="stat-item">
          <span class="stat-value">{{ workingDaysWorked }}</span>
          <span class="stat-label">{{ t('dashboard.monthlyOverview.daysWorked') }}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value">{{ totalWorkingDays }}</span>
          <span class="stat-label">{{ t('dashboard.monthlyOverview.workingDays') }}</span>
        </div>
      </div>
      
      <!-- Hours totals -->
      <div class="hours-totals">
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
      </div>
      
      <!-- Overtime display -->
      <div class="overtime-section">
        <div class="overtime-display" :class="overtimeClass">
          <i :class="overtimeIcon"></i>
          <span class="overtime-value">{{ overtimeText }}</span>
        </div>
        <span class="overtime-label">{{ t('dashboard.monthlyOverview.monthlyOvertime') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DailySummaryResponse, WorkingHoursResponse } from '@/api/generated'

const props = defineProps<{
  currentMonth: Date
  dailySummaries: DailySummaryResponse[]
  workingHours: WorkingHoursResponse | null
  overtimeSelectedMonth: number
}>()

const { t } = useI18n()

// Format date to YYYY-MM-DD
const formatDateString = (date: Date): string => {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

// Month label (e.g., "December 2025")
const monthLabel = computed(() => {
  return props.currentMonth.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
})

// Get working day config for a specific day of week
const getWorkingDayConfig = (dayOfWeek: number) => {
  if (!props.workingHours?.workingDays) return null
  const weekday = dayOfWeek === 0 ? 7 : dayOfWeek
  return props.workingHours.workingDays.find(wd => wd.weekday === weekday) || null
}

// Get all days in the current month up to today (or end of month if past)
const getMonthDays = () => {
  const year = props.currentMonth.getFullYear()
  const month = props.currentMonth.getMonth()
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  
  // If viewing current month, only count up to today
  const endDay = (year === today.getFullYear() && month === today.getMonth()) 
    ? today 
    : lastDay
  
  const days: Date[] = []
  const current = new Date(firstDay)
  while (current <= endDay) {
    days.push(new Date(current))
    current.setDate(current.getDate() + 1)
  }
  return days
}

// Get summary for a specific date
const getSummaryForDate = (dateStr: string): DailySummaryResponse | null => {
  return props.dailySummaries.find(s => s.date === dateStr) || null
}

// Calculate working days in month (up to today)
const totalWorkingDays = computed(() => {
  const days = getMonthDays()
  return days.filter(day => {
    const config = getWorkingDayConfig(day.getDay())
    if (!config?.isWorkingDay) return false
    
    // Check for time-off or recurring off-days
    const dateStr = formatDateString(day)
    const summary = getSummaryForDate(dateStr)
    if (summary?.timeOffEntries?.length || summary?.recurringOffDays?.length) return false
    
    return true
  }).length
})

// Count days with actual work entries
const workingDaysWorked = computed(() => {
  const days = getMonthDays()
  return days.filter(day => {
    const dateStr = formatDateString(day)
    const summary = getSummaryForDate(dateStr)
    return summary && summary.actualHours > 0
  }).length
})

// Total hours worked this month
const totalHoursWorked = computed(() => {
  const days = getMonthDays()
  return days.reduce((sum, day) => {
    const dateStr = formatDateString(day)
    const summary = getSummaryForDate(dateStr)
    return sum + (summary?.actualHours || 0)
  }, 0)
})

// Total target hours for the month (only working days up to today)
const totalTargetHours = computed(() => {
  const days = getMonthDays()
  return days.reduce((sum, day) => {
    const config = getWorkingDayConfig(day.getDay())
    if (!config?.isWorkingDay) return sum
    
    // Check for time-off or recurring off-days
    const dateStr = formatDateString(day)
    const summary = getSummaryForDate(dateStr)
    if (summary?.timeOffEntries?.length || summary?.recurringOffDays?.length) return sum
    
    return sum + (config.hours || 0)
  }, 0)
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

// Overtime display
const overtimeClass = computed(() => {
  if (props.overtimeSelectedMonth > 0) return 'overtime-positive'
  if (props.overtimeSelectedMonth < 0) return 'overtime-negative'
  return 'overtime-neutral'
})

const overtimeIcon = computed(() => {
  if (props.overtimeSelectedMonth > 0) return 'pi pi-arrow-up'
  if (props.overtimeSelectedMonth < 0) return 'pi pi-arrow-down'
  return 'pi pi-minus'
})

const overtimeText = computed(() => {
  const prefix = props.overtimeSelectedMonth >= 0 ? '+' : ''
  return `${prefix}${formatHours(props.overtimeSelectedMonth)}`
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

.month-label {
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

/* Monthly stats */
.monthly-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--tt-spacing-md);
  padding: var(--tt-spacing-sm);
  background: var(--tt-bg-light);
  border-radius: var(--tt-radius-sm);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-item .stat-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--tt-text-primary);
  line-height: 1;
}

.stat-item .stat-label {
  font-size: 0.65rem;
  color: var(--tt-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: var(--tt-text-tertiary);
  opacity: 0.3;
}

/* Hours totals */
.hours-totals {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-xs);
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

/* Overtime section */
.overtime-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--tt-spacing-xs);
  border-top: 1px solid var(--tt-bg-light);
}

.overtime-display {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);
}

.overtime-display i {
  font-size: 0.875rem;
}

.overtime-value {
  font-size: 1.1rem;
  font-weight: 700;
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

.overtime-label {
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
}
</style>
