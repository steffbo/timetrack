<template>
  <div class="info-card monthly-overview-card">
    <div class="card-header">
      <span class="card-icon">📆</span>
      <h4>{{ t('dashboard.monthlyOverview.title') }}</h4>
    </div>
    
    <div class="card-content">
      <!-- Days progress -->
      <div class="days-progress">
        <div class="days-display">
          <span class="days-worked">{{ workingDaysWorked }}</span>
          <span class="days-separator">/</span>
          <span class="days-total">{{ totalWorkingDays }}</span>
          <span class="days-label">{{ t('dashboard.monthlyOverview.workingDays') }}</span>
        </div>
        
        <!-- Progress bar -->
        <div class="progress-bar">
          <div 
            class="progress-fill" 
            :class="progressClass"
            :style="{ width: daysProgressPercentage + '%' }"
          ></div>
        </div>
      </div>
      
      <!-- Overtime display -->
      <div class="overtime-section">
        <div class="overtime-display" :class="overtimeClass">
          <i v-if="overtimeSelectedMonth !== 0" :class="overtimeIcon"></i>
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

// Progress percentage (based on days, not hours)
const daysProgressPercentage = computed(() => {
  if (totalWorkingDays.value === 0) return 0
  return Math.min((workingDaysWorked.value / totalWorkingDays.value) * 100, 100)
})

// Progress class
const progressClass = computed(() => {
  if (daysProgressPercentage.value >= 100) return 'complete'
  if (daysProgressPercentage.value >= 75) return 'almost'
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
  return formatHours(Math.abs(props.overtimeSelectedMonth))
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

.card-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

/* Days progress */
.days-progress {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-xs);
}

.days-display {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.days-worked {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--tt-text-primary);
  line-height: 1;
}

.days-separator {
  font-size: 1rem;
  color: var(--tt-text-tertiary);
}

.days-total {
  font-size: 1rem;
  color: var(--tt-text-secondary);
}

.days-label {
  margin-left: auto;
  font-size: 0.75rem;
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
