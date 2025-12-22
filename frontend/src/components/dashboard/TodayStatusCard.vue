<template>
  <div class="info-card today-status-card">
    <div class="card-header">
      <span class="card-icon">📅</span>
      <h4>{{ t('dashboard.todayStatus.title') }}</h4>
      <span class="today-date">{{ formattedDate }}</span>
    </div>
    
    <div class="card-content">
      <!-- Day type indicator (working day, time-off, holiday, etc.) -->
      <div v-if="dayTypeInfo" class="day-type-badge" :class="dayTypeInfo.class">
        {{ dayTypeInfo.label }}
      </div>
      
      <!-- Hours display for working days -->
      <div v-if="isWorkingDay && !hasFullDayOff" class="hours-display">
        <div class="hours-row">
          <div class="hours-item">
            <span class="hours-value">{{ formatHours(hoursLogged) }}</span>
            <span class="hours-label">{{ t('dashboard.todayStatus.logged') }}</span>
          </div>
          <div class="hours-divider">/</div>
          <div class="hours-item">
            <span class="hours-value target">{{ formatHours(targetHours) }}</span>
            <span class="hours-label">{{ t('dashboard.todayStatus.target') }}</span>
          </div>
        </div>
        
        <!-- Progress bar -->
        <div class="progress-bar">
          <div 
            class="progress-fill" 
            :class="progressClass"
            :style="{ width: progressPercentage + '%' }"
          ></div>
        </div>
        
        <!-- Remaining/Overtime indicator -->
        <div class="status-indicator" :class="statusClass">
          <i :class="statusIcon"></i>
          <span>{{ statusText }}</span>
        </div>
      </div>
      
      <!-- Schedule info -->
      <div v-if="isWorkingDay && !hasFullDayOff && scheduleInfo" class="schedule-info">
        <i class="pi pi-clock"></i>
        <span>{{ scheduleInfo }}</span>
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
}>()

const { t } = useI18n()

// Today's date formatted
const formattedDate = computed(() => {
  const today = new Date()
  return today.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
})

// Get today's working day config
const todayConfig = computed(() => {
  if (!props.workingHours?.workingDays) return null
  const today = new Date()
  const dayOfWeek = today.getDay() === 0 ? 7 : today.getDay()
  return props.workingHours.workingDays.find(wd => wd.weekday === dayOfWeek) || null
})

const isWorkingDay = computed(() => todayConfig.value?.isWorkingDay ?? false)

const targetHours = computed(() => todayConfig.value?.hours ?? 0)

const hoursLogged = computed(() => {
  if (!props.todaySummary) return 0
  // Include active session time if there is one
  if (props.activeEntry?.clockIn) {
    const clockInTime = new Date(props.activeEntry.clockIn)
    const now = new Date()
    const activeHours = (now.getTime() - clockInTime.getTime()) / (1000 * 60 * 60)
    return (props.todaySummary.actualHours || 0) + activeHours
  }
  return props.todaySummary.actualHours || 0
})

// Check for full day time-off
const hasFullDayOff = computed(() => {
  if (!props.todaySummary?.timeOffEntries?.length) return false
  // Check if there's a full-day time-off entry
  return props.todaySummary.timeOffEntries.some(entry => {
    // If hoursPerDay is not set or equals target, it's full day
    return !entry.hoursPerDay || entry.hoursPerDay >= targetHours.value
  })
})

// Day type info (time-off, holiday, recurring off-day)
const dayTypeInfo = computed(() => {
  if (!props.todaySummary) {
    if (!isWorkingDay.value) {
      return { label: t('dashboard.todayStatus.nonWorkingDay'), class: 'type-off' }
    }
    return null
  }
  
  // Check for time-off
  const timeOff = props.todaySummary.timeOffEntries?.[0]
  if (timeOff) {
    const typeLabels: Record<string, { label: string; class: string }> = {
      VACATION: { label: t('timeOff.types.VACATION'), class: 'type-vacation' },
      SICK: { label: t('timeOff.types.SICK'), class: 'type-sick' },
      CHILD_SICK: { label: t('timeOff.types.CHILD_SICK'), class: 'type-sick' },
      PERSONAL: { label: t('timeOff.types.PERSONAL'), class: 'type-personal' },
      PUBLIC_HOLIDAY: { label: t('timeOff.types.PUBLIC_HOLIDAY'), class: 'type-holiday' }
    }
    return typeLabels[timeOff.timeOffType || ''] || null
  }
  
  // Check for recurring off-day
  if (props.todaySummary.recurringOffDays?.length) {
    return { label: t('dashboard.calendar.recurringOffDay'), class: 'type-off' }
  }
  
  if (!isWorkingDay.value) {
    return { label: t('dashboard.todayStatus.nonWorkingDay'), class: 'type-off' }
  }
  
  return null
})

// Progress percentage
const progressPercentage = computed(() => {
  if (targetHours.value === 0) return 0
  return Math.min((hoursLogged.value / targetHours.value) * 100, 100)
})

// Progress bar class
const progressClass = computed(() => {
  if (hoursLogged.value >= targetHours.value) return 'complete'
  if (progressPercentage.value >= 75) return 'almost'
  return 'partial'
})

// Status indicator
const remainingHours = computed(() => targetHours.value - hoursLogged.value)

const statusClass = computed(() => {
  if (remainingHours.value <= 0) return 'status-complete'
  return 'status-remaining'
})

const statusIcon = computed(() => {
  if (remainingHours.value <= 0) return 'pi pi-check-circle'
  return 'pi pi-clock'
})

const statusText = computed(() => {
  if (remainingHours.value <= 0) {
    const overtime = Math.abs(remainingHours.value)
    if (overtime > 0.08) { // More than ~5 minutes
      return t('dashboard.todayStatus.overtime', { hours: formatHours(overtime) })
    }
    return t('dashboard.todayStatus.complete')
  }
  return t('dashboard.todayStatus.remaining', { hours: formatHours(remainingHours.value) })
})

// Schedule info (start - end time)
const scheduleInfo = computed(() => {
  if (!todayConfig.value?.startTime || !todayConfig.value?.endTime) return null
  const start = todayConfig.value.startTime.substring(0, 5) // HH:mm
  const end = todayConfig.value.endTime.substring(0, 5)
  return `${start} - ${end}`
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

.today-date {
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

/* Day type badge */
.day-type-badge {
  display: inline-flex;
  align-self: flex-start;
  padding: var(--tt-spacing-xs) var(--tt-spacing-sm);
  border-radius: var(--tt-radius-sm);
  font-size: 0.75rem;
  font-weight: 500;
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

.day-type-badge.type-holiday {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
}

.day-type-badge.type-off {
  background: var(--tt-bg-light);
  color: var(--tt-text-secondary);
}

/* Hours display */
.hours-display {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-xs);
}

.hours-row {
  display: flex;
  align-items: baseline;
  gap: var(--tt-spacing-xs);
}

.hours-item {
  display: flex;
  flex-direction: column;
}

.hours-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--tt-text-primary);
  line-height: 1;
}

.hours-value.target {
  font-size: 1rem;
  font-weight: 500;
  color: var(--tt-text-secondary);
}

.hours-label {
  font-size: 0.7rem;
  color: var(--tt-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.hours-divider {
  font-size: 1rem;
  color: var(--tt-text-tertiary);
  margin: 0 var(--tt-spacing-xs);
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

/* Status indicator */
.status-indicator {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);
  font-size: 0.8rem;
  font-weight: 500;
}

.status-indicator i {
  font-size: 0.875rem;
}

.status-indicator.status-complete {
  color: var(--tt-emerald-from);
}

.status-indicator.status-remaining {
  color: var(--tt-text-secondary);
}

/* Schedule info */
.schedule-info {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
  padding-top: var(--tt-spacing-xs);
  border-top: 1px solid var(--tt-bg-light);
}

.schedule-info i {
  font-size: 0.75rem;
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
