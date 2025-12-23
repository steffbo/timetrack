<template>
  <div class="info-card tomorrow-preview-card" :class="{ 'compact-off': isOffDay }">
    <div class="card-header">
      <span class="card-icon">🔮</span>
      <h4>{{ t('dashboard.tomorrowPreview.title') }}</h4>
      <!-- Day type badge in header -->
      <div v-if="dayTypeInfo" class="day-type-badge header-badge" :class="dayTypeInfo.class">
        {{ dayTypeInfo.label }}
      </div>
      <span class="tomorrow-date">{{ formattedDate }}</span>
    </div>
    
    <!-- Hide card-content entirely for PTO days -->
    <div v-if="!hasTimeOff" class="card-content">
      <!-- Working hours info for regular working days -->
      <div v-if="isWorkingDay && !hasRecurringOff" class="work-schedule">
        <div class="schedule-row">
          <span class="schedule-icon">🕐</span>
          <span class="schedule-times">{{ workingTimeRange }}</span>
        </div>
        <div class="schedule-row">
          <span class="schedule-icon">⏱️</span>
          <span class="schedule-hours">{{ formatHours(expectedHours) }}</span>
        </div>
      </div>
      
      <!-- Non-working day or time-off message -->
      <div v-else class="day-off-message">
        <span v-if="hasTimeOff" class="off-icon">{{ timeOffIcon }}</span>
        <span v-else-if="hasRecurringOff" class="off-icon">📴</span>
        <span v-else class="off-icon">🛋️</span>
        <span class="off-text">{{ offDayText }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DailySummaryResponse, WorkingHoursResponse } from '@/api/generated'

const props = defineProps<{
  tomorrowSummary: DailySummaryResponse | null
  workingHours: WorkingHoursResponse | null
}>()

const { t } = useI18n()

// Tomorrow's date
const tomorrow = computed(() => {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return date
})

// Tomorrow's date formatted
const formattedDate = computed(() => {
  return tomorrow.value.toLocaleDateString(t('locale'), { weekday: 'short', day: 'numeric', month: 'short' })
})

// Get tomorrow's working day config
const tomorrowConfig = computed(() => {
  if (!props.workingHours?.workingDays) return null
  const dayOfWeek = tomorrow.value.getDay() === 0 ? 7 : tomorrow.value.getDay()
  return props.workingHours.workingDays.find(wd => wd.weekday === dayOfWeek) || null
})

const isWorkingDay = computed(() => tomorrowConfig.value?.isWorkingDay ?? false)

// Check for time-off
const hasTimeOff = computed(() => {
  return (props.tomorrowSummary?.timeOffEntries?.length ?? 0) > 0
})

const timeOffType = computed(() => {
  return props.tomorrowSummary?.timeOffEntries?.[0]?.timeOffType || null
})

// Check for recurring off-day
const hasRecurringOff = computed(() => {
  return (props.tomorrowSummary?.recurringOffDays?.length ?? 0) > 0
})

// Check if it's any kind of off-day (for compact styling)
const isOffDay = computed(() => {
  return hasTimeOff.value || hasRecurringOff.value || !isWorkingDay.value
})

// Day type info (time-off, holiday, recurring off-day)
const dayTypeInfo = computed(() => {
  if (hasTimeOff.value) {
    const typeLabels: Record<string, { label: string; class: string }> = {
      VACATION: { label: t('timeOff.type.VACATION'), class: 'type-vacation' },
      SICK: { label: t('timeOff.type.SICK'), class: 'type-sick' },
      CHILD_SICK: { label: t('timeOff.type.CHILD_SICK'), class: 'type-sick' },
      PERSONAL: { label: t('timeOff.type.PERSONAL'), class: 'type-personal' },
      EDUCATION: { label: t('timeOff.type.EDUCATION'), class: 'type-education' },
      PUBLIC_HOLIDAY: { label: t('timeOff.type.PUBLIC_HOLIDAY'), class: 'type-holiday' }
    }
    return typeLabels[timeOffType.value || ''] || null
  }
  
  if (hasRecurringOff.value) {
    return { label: t('dashboard.calendar.recurringOffDay'), class: 'type-off' }
  }
  
  if (!isWorkingDay.value) {
    return { label: t('dashboard.todayStatus.nonWorkingDay'), class: 'type-off' }
  }
  
  return null
})

// Time-off icon
const timeOffIcon = computed(() => {
  const icons: Record<string, string> = {
    VACATION: '🏝️',
    SICK: '😷',
    CHILD_SICK: '👶',
    PERSONAL: '🏠',
    EDUCATION: '📚',
    PUBLIC_HOLIDAY: '🎊'
  }
  return icons[timeOffType.value || ''] || '📴'
})

// Working time range
const workingTimeRange = computed(() => {
  if (!tomorrowConfig.value?.startTime || !tomorrowConfig.value?.endTime) return ''
  const formatTime = (time: string) => time.substring(0, 5)
  return `${formatTime(tomorrowConfig.value.startTime)} - ${formatTime(tomorrowConfig.value.endTime)}`
})

// Expected hours
const expectedHours = computed(() => {
  return tomorrowConfig.value?.hours ?? 0
})

// Off day text
const offDayText = computed(() => {
  if (hasTimeOff.value) {
    const timeOffEntry = props.tomorrowSummary?.timeOffEntries?.[0]
    if (timeOffEntry?.timeOffType === 'PUBLIC_HOLIDAY') {
      return timeOffEntry.notes || t('timeOff.type.PUBLIC_HOLIDAY')
    }
    return t(`timeOff.type.${timeOffType.value}`)
  }
  if (hasRecurringOff.value) {
    const offDay = props.tomorrowSummary?.recurringOffDays?.[0]
    return offDay?.description || t('dashboard.calendar.recurringOffDay')
  }
  return t('dashboard.todayStatus.nonWorkingDay')
})

// Format hours
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

.tomorrow-date {
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
  margin-left: auto;
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

.card-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

/* Work schedule display */
.work-schedule {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-xs);
}

.schedule-row {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);
}

.schedule-icon {
  font-size: 1rem;
}

.schedule-times {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--tt-text-primary);
}

.schedule-hours {
  font-size: 0.875rem;
  color: var(--tt-text-secondary);
}

/* Day off message */
.day-off-message {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-sm);
  padding: var(--tt-spacing-sm);
  background: var(--tt-bg-light);
  border-radius: var(--tt-radius-sm);
}

.off-icon {
  font-size: 1.5rem;
}

.off-text {
  font-size: 0.875rem;
  color: var(--tt-text-secondary);
}
</style>
