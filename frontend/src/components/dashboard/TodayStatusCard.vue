<template>
  <div class="info-card today-status-card" :class="{ 'compact-off': isOffDay }">
    <div class="card-header">
      <span class="card-icon">📅</span>
      <h4>{{ t('dashboard.todayStatus.title') }}</h4>
      <!-- Day type badge in header -->
      <div v-if="dayTypeInfo" class="day-type-badge header-badge" :class="dayTypeInfo.class">
        {{ dayTypeInfo.label }}
      </div>
      <span class="today-date">{{ formattedDate }}</span>
    </div>
    
    <!-- Hide card-content entirely for PTO days -->
    <div v-if="!hasTimeOff" class="card-content">
      <!-- Hours progress (only for regular working days, not recurring off-days) -->
      <div v-if="showHoursProgress" class="hours-progress">
        <div class="hours-display">
          <span class="hours-worked">{{ formatHours(hoursWorked) }}</span>
          <span class="hours-separator">/</span>
          <span class="hours-target">{{ formatHours(expectedHours) }}</span>
          <span v-if="workingTimeRange" class="time-range">{{ workingTimeRange }}</span>
        </div>
        <div class="progress-bar">
          <div 
            class="progress-fill" 
            :class="progressClass"
            :style="{ width: progressPercentage + '%' }"
          ></div>
        </div>
      </div>
      
      <!-- Active session indicator -->
      <div v-if="activeEntry" class="active-session">
        <span class="pulse-dot"></span>
        <span>{{ t('dashboard.todayStatus.activeSession') }}</span>
        <span class="session-time">{{ activeSessionDuration }}</span>
      </div>
      
      <!-- Quick Actions -->
      <div class="quick-actions">
        <!-- When not clocked in -->
        <template v-if="!activeEntry">
          <!-- Show Create Exemption button when it's a recurring off-day -->
          <template v-if="isRecurringOffDay">
            <button 
              class="action-btn action-create-exemption"
              @click="$emit('create-exemption')"
            >
              <i class="pi pi-calendar-plus"></i>
              <span>{{ t('dashboard.createExemption') }}</span>
            </button>
          </template>
          <!-- Normal buttons when not a recurring off-day -->
          <template v-else>
            <button 
              class="action-btn action-clock-in"
              :class="{ disabled: !hasTodayWorkingHours }"
              :disabled="!hasTodayWorkingHours"
              @click="$emit('clock-in')"
            >
              <i class="pi pi-play-circle"></i>
              <span>{{ t('dashboard.clockInNow') }}</span>
            </button>
            <button 
              class="action-btn action-quick-clock-out"
              :class="{ disabled: !hasTodayWorkingHours }"
              :disabled="!hasTodayWorkingHours"
              @click="$emit('quick-clock-out')"
            >
              <i class="pi pi-stopwatch"></i>
              <span>{{ t('dashboard.quickClockOut') }}</span>
            </button>
            <button 
              class="action-btn action-quick-entry"
              :class="{ disabled: !hasTodayWorkingHours }"
              :disabled="!hasTodayWorkingHours"
              @click="$emit('quick-entry')"
            >
              <i class="pi pi-bolt"></i>
              <span>{{ t('dashboard.quickWorkEntry') }}</span>
            </button>
          </template>
        </template>
        <!-- When clocked in -->
        <template v-else>
          <button 
            class="action-btn action-clock-out"
            @click="$emit('clock-out')"
          >
            <i class="pi pi-stop-circle"></i>
            <span>{{ t('dashboard.clockOutNow') }}</span>
          </button>
          <button 
            class="action-btn action-cancel"
            @click="$emit('cancel-entry')"
          >
            <i class="pi pi-times"></i>
            <span>{{ t('dashboard.cancelEntry') }}</span>
          </button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DailySummaryResponse, WorkingHoursResponse, TimeEntryResponse } from '@/api/generated'

const props = defineProps<{
  todaySummary: DailySummaryResponse | null
  workingHours: WorkingHoursResponse | null
  activeEntry: TimeEntryResponse | null
  hasTodayWorkingHours: boolean
}>()

defineEmits<{
  'clock-in': []
  'clock-out': []
  'cancel-entry': []
  'quick-entry': []
  'quick-clock-out': []
  'create-exemption': []
}>()

const { t } = useI18n()

// Check if today is a recurring off-day
const isRecurringOffDay = computed(() => {
  return (props.todaySummary?.recurringOffDays?.length ?? 0) > 0
})

// Check if today has time-off (vacation, sick, etc.)
const hasTimeOff = computed(() => {
  return (props.todaySummary?.timeOffEntries?.length ?? 0) > 0
})

// Check if it's any kind of off-day (for compact styling)
const isOffDay = computed(() => {
  return hasTimeOff.value || isRecurringOffDay.value
})

// Today's date formatted
const formattedDate = computed(() => {
  const today = new Date()
  return today.toLocaleDateString(t('locale'), { weekday: 'short', day: 'numeric', month: 'short' })
})

// Get today's working day config
const todayConfig = computed(() => {
  if (!props.workingHours?.workingDays) return null
  const today = new Date()
  const dayOfWeek = today.getDay() === 0 ? 7 : today.getDay()
  return props.workingHours.workingDays.find(wd => wd.weekday === dayOfWeek) || null
})

const isWorkingDay = computed(() => todayConfig.value?.isWorkingDay ?? false)

// Day type info (time-off, holiday, recurring off-day)
const dayTypeInfo = computed(() => {
  // Check for time-off first
  const timeOff = props.todaySummary?.timeOffEntries?.[0]
  if (timeOff) {
    const typeLabels: Record<string, { label: string; class: string }> = {
      VACATION: { label: t('timeOff.type.VACATION'), class: 'type-vacation' },
      SICK: { label: t('timeOff.type.SICK'), class: 'type-sick' },
      CHILD_SICK: { label: t('timeOff.type.CHILD_SICK'), class: 'type-sick' },
      PERSONAL: { label: t('timeOff.type.PERSONAL'), class: 'type-personal' },
      EDUCATION: { label: t('timeOff.type.EDUCATION'), class: 'type-education' },
      PUBLIC_HOLIDAY: { label: t('timeOff.type.PUBLIC_HOLIDAY'), class: 'type-holiday' }
    }
    return typeLabels[timeOff.timeOffType || ''] || null
  }
  
  // Check for recurring off-day
  if (isRecurringOffDay.value) {
    return { label: t('dashboard.calendar.recurringOffDay'), class: 'type-off' }
  }
  
  // Check for non-working day (weekend)
  if (!isWorkingDay.value) {
    return { label: t('dashboard.todayStatus.nonWorkingDay'), class: 'type-off' }
  }
  
  return null
})

// Active session duration
const activeSessionDuration = computed(() => {
  if (!props.activeEntry?.clockIn) return ''
  const clockIn = new Date(props.activeEntry.clockIn)
  const now = new Date()
  const diffMs = now.getTime() - clockIn.getTime()
  const hours = Math.floor(diffMs / (1000 * 60 * 60))
  const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60))
  return `${hours}h ${minutes}m`
})

// Show hours progress only for regular working days (not recurring off-days, time-off, or non-working days)
const showHoursProgress = computed(() => {
  // Don't show for recurring off-days
  if (isRecurringOffDay.value) return false
  // Don't show for time-off entries
  if (props.todaySummary?.timeOffEntries?.length) return false
  // Only show for working days
  return isWorkingDay.value
})

// Hours worked today
const hoursWorked = computed(() => {
  return props.todaySummary?.actualHours ?? 0
})

// Expected hours for today (net hours, break already subtracted)
const expectedHours = computed(() => {
  const targetHours = todayConfig.value?.hours ?? 0
  return targetHours
})

// Working time range (start - end) for display
const workingTimeRange = computed(() => {
  if (!todayConfig.value?.startTime || !todayConfig.value?.endTime) return null
  const formatTime = (time: string) => time.substring(0, 5) // "09:00:00" -> "09:00"
  return `${formatTime(todayConfig.value.startTime)} - ${formatTime(todayConfig.value.endTime)}`
})

// Progress percentage
const progressPercentage = computed(() => {
  if (expectedHours.value === 0) return 0
  const percentage = (hoursWorked.value / expectedHours.value) * 100
  return Math.min(percentage, 100)
})

// Progress bar class
const progressClass = computed(() => {
  const percentage = progressPercentage.value
  if (percentage >= 100) return 'complete'
  if (percentage >= 75) return 'good'
  if (percentage >= 50) return 'moderate'
  return 'low'
})

// Format hours for display
const formatHours = (hours: number): string => {
  const h = Math.floor(hours)
  const m = Math.round((hours - h) * 60)
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
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

.info-card.compact-off {
  padding: var(--tt-spacing-sm) var(--tt-spacing-md);
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
}

.today-date {
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
  margin-left: auto;
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

/* Day type badge */
.day-type-badge {
  display: inline-flex;
  padding: var(--tt-spacing-xs) var(--tt-spacing-sm);
  border-radius: var(--tt-radius-sm);
  font-size: 0.75rem;
  font-weight: 500;
}

.day-type-badge.header-badge {
  padding: 2px var(--tt-spacing-xs);
  font-size: 0.65rem;
}

.day-type-badge.type-vacation {
  background: rgba(16, 185, 129, 0.15);
  color: var(--tt-emerald-from);
}

.day-type-badge.type-sick {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.day-type-badge.type-personal {
  background: rgba(139, 92, 246, 0.15);
  color: #8b5cf6;
}

.day-type-badge.type-education {
  background: rgba(99, 102, 241, 0.15);
  color: #6366f1;
}

.day-type-badge.type-holiday {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
}

.day-type-badge.type-off {
  background: var(--tt-bg-light);
  color: var(--tt-text-secondary);
}

/* Hours progress */
.hours-progress {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-xs);
}

.hours-display {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 0.875rem;
}

.hours-worked {
  font-weight: 600;
  color: var(--tt-text-primary);
}

.hours-separator {
  color: var(--tt-text-secondary);
}

.hours-target {
  color: var(--tt-text-secondary);
}

.time-range {
  margin-left: auto;
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
}

.progress-bar {
  height: 6px;
  background: var(--tt-bg-light);
  border-radius: var(--tt-radius-sm);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: var(--tt-radius-sm);
  transition: width 0.3s ease;
}

.progress-fill.low {
  background: #ef4444;
}

.progress-fill.moderate {
  background: #f59e0b;
}

.progress-fill.good {
  background: #84cc16;
}

.progress-fill.complete {
  background: var(--tt-emerald-from);
}

/* Active session */
.active-session {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);
  padding: var(--tt-spacing-xs) var(--tt-spacing-sm);
  background: rgba(16, 185, 129, 0.1);
  border-radius: var(--tt-radius-sm);
  font-size: 0.75rem;
  color: var(--tt-emerald-from);
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: var(--tt-emerald-from);
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.8);
  }
}

.session-time {
  margin-left: auto;
  font-weight: 600;
}

/* Quick Actions */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--tt-spacing-xs);
  padding-top: var(--tt-spacing-sm);
  border-top: 1px solid var(--tt-bg-light);
}

/* When clocked in, only 2 buttons */
.quick-actions:has(.action-cancel) {
  grid-template-columns: 1fr 1fr;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: var(--tt-spacing-sm) var(--tt-spacing-xs);
  border: none;
  border-radius: var(--tt-radius-sm);
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn i {
  font-size: 0.875rem;
}

.action-btn.action-clock-in {
  background: linear-gradient(135deg, var(--tt-emerald-from) 0%, var(--tt-emerald-to) 100%);
  color: white;
}

.action-btn.action-clock-in:hover:not(.disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.action-btn.action-clock-out {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
}

.action-btn.action-clock-out:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.action-btn.action-quick-entry {
  background: rgba(16, 185, 129, 0.1);
  color: var(--tt-emerald-from);
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.action-btn.action-quick-entry:hover:not(.disabled) {
  background: rgba(16, 185, 129, 0.15);
  border-color: rgba(16, 185, 129, 0.3);
}

.action-btn.action-quick-clock-out {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.action-btn.action-quick-clock-out:hover:not(.disabled) {
  background: rgba(239, 68, 68, 0.15);
  border-color: rgba(239, 68, 68, 0.3);
}

.action-btn.action-create-exemption {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.2);
  grid-column: 1 / -1;
}

.action-btn.action-create-exemption:hover {
  background: rgba(245, 158, 11, 0.15);
  border-color: rgba(245, 158, 11, 0.3);
}

.action-btn.action-cancel {
  background: var(--tt-bg-light);
  color: var(--tt-text-secondary);
}

.action-btn.action-cancel:hover {
  background: #e5e7eb;
}

.action-btn.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
