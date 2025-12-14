<template>
  <Card class="monthly-calendar">
    <template #header>
      <div class="calendar-header">
        <div class="nav-buttons-left">
          <Button
            icon="pi pi-chevron-left"
            @click="previousMonth"
            text
            :aria-label="t('dashboard.calendar.previousMonth')"
          />
        </div>
        <div class="calendar-title-section">
          <h3 class="calendar-title">
            {{ monthName }} {{ currentYear }}
          </h3>
          <div class="calendar-actions">
            <Button
              :label="t('dashboard.calendar.today')"
              @click="goToToday"
              size="small"
              outlined
              :class="{ 'invisible-button': isCurrentMonth }"
            />
            <Button
              :label="t('dashboard.calendar.exportPdf')"
              @click="exportMonthlyPdf"
              size="small"
              icon="pi pi-file-pdf"
              outlined
              :loading="exportLoading"
            />
          </div>
        </div>
        <div class="nav-buttons-right">
          <Button
            icon="pi pi-chevron-right"
            @click="nextMonth"
            text
            :aria-label="t('dashboard.calendar.nextMonth')"
          />
        </div>
      </div>
    </template>
    <template #content>
      <div class="calendar-grid">
        <!-- Weekday headers -->
        <div
          v-for="weekday in weekdays"
          :key="weekday"
          class="calendar-weekday"
        >
          {{ weekday }}
        </div>

        <!-- Empty cells for days before month starts -->
        <div
          v-for="n in emptyDaysAtStart"
          :key="`empty-${n}`"
          class="calendar-day empty"
        />

        <!-- Days of the month -->
        <div
          v-for="day in daysInMonth"
          :key="day"
          :ref="el => setDayRef(day, el)"
          class="calendar-day"
          :class="getDayClasses(day)"
          :style="getDayStyle(day)"
          @click="handleDayClick(day, $event)"
          @mouseenter="handleDayHover(day, $event)"
          @mouseleave="handleDayLeave()"
        >
          <div class="day-content">
            <span class="day-number">{{ day }}</span>
            <i
              v-if="getDayStatusIcon(day)"
              :class="['status-icon', getDayStatusIcon(day)]"
            />
          </div>
        </div>
      </div>
    </template>
  </Card>

  <!-- Hover overlay panel for day details -->
  <OverlayPanel ref="hoverPanel" :dismissable="false">
    <div v-if="hoveredDay !== null" class="day-details">
      <div class="day-details-content" v-html="formatDayDetailsHtml(hoveredDay)"></div>
    </div>
  </OverlayPanel>

  <!-- Sticky overlay panel for day details -->
  <OverlayPanel ref="stickyPanel" :dismissable="true" @hide="handleOverlayHide">
    <div v-if="stickyDay !== null" class="day-details">
      <div class="day-details-content" v-html="formatDayDetailsHtml(stickyDay)"></div>
    </div>
  </OverlayPanel>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import Button from 'primevue/button'
import OverlayPanel from 'primevue/overlaypanel'
import type { DailySummaryResponse, WorkingHoursResponse } from '@/api/generated'
import { OpenAPI } from '@/api/generated'
import axios from 'axios'

const { t } = useI18n()
const toast = useToast()

// State for hover and sticky overlays
const hoveredDay = ref<number | null>(null) // Currently hovered day
const stickyDay = ref<number | null>(null) // Day that is sticky (clicked)
const hoverPanel = ref<InstanceType<typeof OverlayPanel> | null>(null)
const stickyPanel = ref<InstanceType<typeof OverlayPanel> | null>(null)
const dayRefs = ref<Map<number, HTMLElement>>(new Map())
let hoverTimeout: ReturnType<typeof setTimeout> | null = null

// State for PDF export
const exportLoading = ref(false)

interface Props {
  currentMonth: Date
  dailySummaries: DailySummaryResponse[]
  workingHours: WorkingHoursResponse | null
}

const props = defineProps<Props>()

interface Emits {
  (e: 'monthChange', date: Date): void
}

const emit = defineEmits<Emits>()

// Computed properties for calendar display
const currentYear = computed(() => props.currentMonth.getFullYear())
const currentMonthIndex = computed(() => props.currentMonth.getMonth())

const monthName = computed(() => {
  return t(`dashboard.calendar.monthNames.${currentMonthIndex.value}`)
})

const weekdays = computed(() => {
  return Array.from({ length: 7 }, (_, i) =>
    t(`dashboard.calendar.weekdays.${i}`)
  )
})

const daysInMonth = computed(() => {
  return new Date(currentYear.value, currentMonthIndex.value + 1, 0).getDate()
})

const emptyDaysAtStart = computed(() => {
  const firstDay = new Date(currentYear.value, currentMonthIndex.value, 1).getDay()
  // Convert Sunday (0) to 7, then subtract 1 to make Monday = 0
  return firstDay === 0 ? 6 : firstDay - 1
})

const isCurrentMonth = computed(() => {
  const today = new Date()
  return currentYear.value === today.getFullYear() && currentMonthIndex.value === today.getMonth()
})

// Helper to get summary for a specific day
const getSummaryForDay = (day: number): DailySummaryResponse | undefined => {
  const dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  return props.dailySummaries.find(summary => summary.date === dateStr)
}

// Helper to determine primary entry type for a day
const getPrimaryEntryType = (day: number): string => {
  const summary = getSummaryForDay(day)
  if (!summary) return 'NO_ENTRY'

  // Priority: TIME_OFF (specific types) > RECURRING_OFF > SICK > PTO > EVENT > WORK
  if (summary.timeOffEntries && summary.timeOffEntries.length > 0) {
    // Check for specific time off types
    const publicHoliday = summary.timeOffEntries.find(e => e.timeOffType === 'PUBLIC_HOLIDAY')
    if (publicHoliday) return 'PUBLIC_HOLIDAY'

    const vacation = summary.timeOffEntries.find(e => e.timeOffType === 'VACATION')
    if (vacation) return 'VACATION'

    const sick = summary.timeOffEntries.find(e => e.timeOffType === 'SICK')
    if (sick) return 'SICK_LEAVE'

    // Generic time off
    return 'TIME_OFF'
  }
  if (summary.recurringOffDays && summary.recurringOffDays.length > 0) {
    return 'RECURRING_OFF'
  }
  if (summary.entries && summary.entries.length > 0) {
    // Check if SICK, PTO, or EVENT entries exist
    const hasSick = summary.entries.some(e => e.entryType === 'SICK')
    const hasPTO = summary.entries.some(e => e.entryType === 'PTO')
    const hasEvent = summary.entries.some(e => e.entryType === 'EVENT')

    if (hasSick) return 'SICK'
    if (hasPTO) return 'PTO'
    if (hasEvent) return 'EVENT'
    return 'WORK'
  }

  return 'NO_ENTRY'
}

// Get CSS classes for a day cell
const getDayClasses = (day: number) => {
  const today = new Date()
  const isToday =
    day === today.getDate() &&
    currentMonthIndex.value === today.getMonth() &&
    currentYear.value === today.getFullYear()

  return {
    'is-today': isToday,
    'is-sticky': day === stickyDay.value
  }
}

// Get background color style for a day cell
const getDayStyle = (day: number) => {
  const entryType = getPrimaryEntryType(day)

  const colorMap: Record<string, string> = {
    'WORK': 'var(--p-green-50)',
    'SICK': 'var(--p-red-50)',
    'PTO': 'var(--p-blue-50)',
    'EVENT': 'var(--p-purple-50)',
    'VACATION': 'var(--p-cyan-50)',
    'SICK_LEAVE': 'var(--p-red-50)',
    'PUBLIC_HOLIDAY': 'var(--p-orange-50)',
    'TIME_OFF': 'var(--p-amber-50)',
    'RECURRING_OFF': 'var(--p-gray-100)',
    'NO_ENTRY': 'var(--p-surface-0)'
  }

  return {
    backgroundColor: colorMap[entryType] || colorMap['NO_ENTRY']
  }
}

// Get status icon for a day cell
const getDayStatusIcon = (day: number): string | null => {
  const summary = getSummaryForDay(day)
  if (!summary || summary.status === 'NO_ENTRY') return null

  const iconMap: Record<string, string> = {
    'MATCHED': 'pi pi-check',
    'ABOVE_EXPECTED': 'pi pi-arrow-up',
    'BELOW_EXPECTED': 'pi pi-arrow-down'
  }

  return iconMap[summary.status] || null
}

// Navigation methods
const previousMonth = () => {
  const newDate = new Date(currentYear.value, currentMonthIndex.value - 1, 1)
  emit('monthChange', newDate)
}

const nextMonth = () => {
  const newDate = new Date(currentYear.value, currentMonthIndex.value + 1, 1)
  emit('monthChange', newDate)
}

const goToToday = () => {
  const today = new Date()
  const newDate = new Date(today.getFullYear(), today.getMonth(), 1)
  emit('monthChange', newDate)
}

// Day ref management
const setDayRef = (day: number, el: any) => {
  if (el) {
    dayRefs.value.set(day, el as HTMLElement)
  }
}

// Handle day hover
const handleDayHover = (day: number, event: MouseEvent) => {
  // Clear any pending hover timeout
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = null
  }

  // Skip hover panel if this day is already sticky
  if (day === stickyDay.value) {
    return
  }

  // Show hover overlay with reduced delay (100ms)
  hoverTimeout = setTimeout(() => {
    hoveredDay.value = day
    const targetElement = dayRefs.value.get(day)
    if (targetElement && hoverPanel.value) {
      hoverPanel.value.show(event, targetElement)
    }
  }, 100)
}

// Handle day leave
const handleDayLeave = () => {
  // Clear any pending hover timeout
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = null
  }

  // Hide the hover panel
  hoverPanel.value?.hide()
  hoveredDay.value = null
}

// Handle day click for sticky overlay
const handleDayClick = (day: number, event: MouseEvent) => {
  // Clear any hover timeout
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = null
  }

  // If clicking the same day that is already sticky, toggle off
  if (day === stickyDay.value) {
    stickyPanel.value?.hide()
    stickyDay.value = null
  } else {
    // Hide the previous sticky panel if it exists
    if (stickyDay.value !== null) {
      stickyPanel.value?.hide()
    }

    // Update the sticky day state and show the panel
    const targetElement = dayRefs.value.get(day)
    if (targetElement && stickyPanel.value) {
      // Use a small delay to ensure the hide completes before updating state and showing the new one
      setTimeout(() => {
        stickyDay.value = day
        if (stickyPanel.value && targetElement) {
          stickyPanel.value.show(event, targetElement)
        }
      }, 50)
    }
  }
}

// Handle overlay hide event
const handleOverlayHide = () => {
  // If user manually dismissed the overlay (click outside, ESC, etc), unsticky it
  stickyDay.value = null
}

// Helper to get working day config for a specific weekday (1=Monday, 7=Sunday)
const getWorkingDayConfig = (weekday: number) => {
  if (!props.workingHours?.workingDays) return null
  return props.workingHours.workingDays.find(wd => wd.weekday === weekday)
}

// Helper to get weekday number (1-7, where 1=Monday, 7=Sunday) from date
const getWeekdayNumber = (day: number): number => {
  const date = new Date(currentYear.value, currentMonthIndex.value, day)
  const dayOfWeek = date.getDay() // 0=Sunday, 1=Monday, ..., 6=Saturday
  return dayOfWeek === 0 ? 7 : dayOfWeek // Convert to 1=Monday, 7=Sunday
}

// Helper to calculate time difference in minutes
const calculateTimeDiffMinutes = (time1: string, time2: string): number => {
  const [h1, m1] = time1.split(':').map(Number)
  const [h2, m2] = time2.split(':').map(Number)
  const minutes1 = h1 * 60 + m1
  const minutes2 = h2 * 60 + m2
  return Math.abs(minutes1 - minutes2)
}

// Helper to format duration in hours and minutes
const formatDuration = (hours: number): string => {
  const h = Math.floor(hours)
  const m = Math.round((hours - h) * 60)
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

// Helper to get book emoji based on time discrepancy
const getBookEmoji = (actualStart: string, actualEnd: string, plannedStart: string, plannedEnd: string): string => {
  const startDiff = calculateTimeDiffMinutes(actualStart, plannedStart)
  const endDiff = calculateTimeDiffMinutes(actualEnd, plannedEnd)
  const maxDiff = Math.max(startDiff, endDiff)

  if (maxDiff <= 10) return '📗' // Green: within 10 minutes
  if (maxDiff <= 30) return '📒' // Yellow: 10-30 minutes
  return '📕' // Red: more than 30 minutes
}

// Export monthly PDF
const exportMonthlyPdf = async () => {
  exportLoading.value = true
  try {
    const year = currentYear.value
    const month = currentMonthIndex.value + 1 // API expects 1-12

    // Get the token - OpenAPI.TOKEN can be a string or a function
    const token = typeof OpenAPI.TOKEN === 'function' ? OpenAPI.TOKEN({} as any) : OpenAPI.TOKEN

    // Use axios directly with responseType: 'blob' to properly handle PDF response
    const response = await axios.get(
      `${OpenAPI.BASE}/api/time-entries/monthly-report`,
      {
        params: { year, month },
        responseType: 'blob',
        headers: {
          Authorization: `Bearer ${token}`
        }
      }
    )

    // Create a download link for the PDF
    const blob = new Blob([response.data], { type: 'application/pdf' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `timetrack-${year}-${String(month).padStart(2, '0')}.pdf`
    document.body.appendChild(link)
    link.click()

    // Clean up
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('dashboard.calendar.exportSuccess'),
      life: 3000
    })
  } catch (error) {
    console.error('PDF export error:', error)
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('dashboard.calendar.exportError'),
      life: 3000
    })
  } finally {
    exportLoading.value = false
  }
}

// Format day details as HTML for the overlay
const formatDayDetailsHtml = (day: number): string => {
  const summary = getSummaryForDay(day)
  if (!summary) return `<p>${t('dashboard.calendar.noEntries')}</p>`

  const parts: string[] = []

  // Date
  const dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  parts.push(`<div class="detail-row"><strong>📅 ${dateStr}</strong></div>`)

  // Get emoji for time off type
  const getTimeOffEmoji = (timeOffType: string): string => {
    const emojiMap: Record<string, string> = {
      'VACATION': '🏝️',
      'SICK': '😵‍💫',
      'PUBLIC_HOLIDAY': '🎊',
      'PERSONAL': '🏠'
    }
    return emojiMap[timeOffType] || '📅'
  }

  // Time off - show first for PTO days
  const hasPTOEntries = summary.timeOffEntries && summary.timeOffEntries.length > 0
  if (hasPTOEntries) {
    summary.timeOffEntries!.forEach(timeOff => {
      const typeLabel = t(`timeOff.type.${timeOff.timeOffType}`)
      const emoji = getTimeOffEmoji(timeOff.timeOffType)

      // For public holidays, show the holiday name (stored in notes)
      if (timeOff.timeOffType === 'PUBLIC_HOLIDAY' && timeOff.notes) {
        parts.push(`<div class="detail-row">${emoji} <strong>${timeOff.notes}</strong></div>`)
      } else {
        parts.push(`<div class="detail-row">${emoji} <strong>${typeLabel}</strong></div>`)
      }
    })
  }

  // Recurring off-days
  if (summary.recurringOffDays && summary.recurringOffDays.length > 0) {
    summary.recurringOffDays.forEach(offDay => {
      const description = offDay.description || t('dashboard.calendar.recurringOffDay')
      parts.push(`<div class="detail-row">📴 <strong>${description}</strong></div>`)
    })
  }

  // Hours info - only show if it's NOT an absence day (no PTO and no recurring off-days)
  const isAbsenceDay = hasPTOEntries || (summary.recurringOffDays && summary.recurringOffDays.length > 0)
  if (!isAbsenceDay) {
    // Get working day config for this day
    const weekday = getWeekdayNumber(day)
    const workingDayConfig = getWorkingDayConfig(weekday)

    // Show planned times if available
    if (workingDayConfig?.isWorkingDay && workingDayConfig.startTime && workingDayConfig.endTime) {
      const plannedHours = workingDayConfig.hours || 0
      parts.push(`<div class="detail-row">⏲️ ${workingDayConfig.startTime} - ${workingDayConfig.endTime} (${formatDuration(plannedHours)})</div>`)
    }

    // Show actual times if there are entries
    if (summary.entries && summary.entries.length > 0) {
      // Calculate actual start and end times from all entries
      const clockIns = summary.entries.filter(e => e.clockIn).map(e => e.clockIn!)
      const clockOuts = summary.entries.filter(e => e.clockOut).map(e => e.clockOut!)

      if (clockIns.length > 0 && clockOuts.length > 0) {
        const earliestClockIn = new Date(Math.min(...clockIns.map(d => new Date(d).getTime())))
        const latestClockOut = new Date(Math.max(...clockOuts.map(d => new Date(d).getTime())))

        const actualStart = earliestClockIn.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
        const actualEnd = latestClockOut.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })

        // Determine book emoji based on discrepancy with planned times
        let bookEmoji = '📘' // Default blue book
        if (workingDayConfig?.startTime && workingDayConfig?.endTime) {
          bookEmoji = getBookEmoji(actualStart, actualEnd, workingDayConfig.startTime, workingDayConfig.endTime)
        }

        parts.push(`<div class="detail-row">${bookEmoji} ${actualStart} - ${actualEnd} (${formatDuration(summary.actualHours)})</div>`)
      }
    }
  }

  return parts.join('')
}
</script>

<style scoped>
.monthly-calendar {
  height: 100%;
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem;
  background: var(--p-surface-0);
}

.nav-buttons-left,
.nav-buttons-right {
  display: flex;
  align-items: center;
  min-width: 50px;
}

.calendar-title-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
}

.calendar-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--p-text-color);
}

.calendar-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.invisible-button {
  visibility: hidden;
  pointer-events: none;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 0.25rem;
  padding: 0;
}

.calendar-weekday {
  text-align: center;
  font-weight: 600;
  padding: 0.5rem;
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
  border-bottom: 1px solid var(--p-surface-border);
}

.calendar-day {
  aspect-ratio: 1 / 1;
  min-height: 60px;
  padding: 0.5rem;
  border: 1px solid var(--p-surface-border);
  border-radius: 4px;
  transition: all 0.2s;
  position: relative;
}

.calendar-day.empty {
  background: transparent;
  border: none;
}

.calendar-day:not(.empty):hover,
.calendar-day.is-sticky {
  transform: scale(1.05);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  z-index: 1;
}

.calendar-day.is-today {
  border: 2px solid var(--p-primary-color);
  box-shadow: 0 0 0 1px var(--p-primary-color);
}

.day-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  height: 100%;
  position: relative;
}

.day-number {
  font-size: 1rem;
  font-weight: 500;
  color: var(--p-text-color);
}

.status-icon {
  position: absolute;
  bottom: 0;
  right: 0;
  font-size: 0.875rem;
  color: var(--p-text-color);
  opacity: 0.7;
}

/* Day details overlay styles */
.day-details {
  min-width: 250px;
  max-width: 400px;
}

.day-details-content {
  font-size: 0.875rem;
}

.detail-row {
  margin-bottom: 0.5rem;
  line-height: 1.5;
}

.detail-section {
  margin-top: 0.75rem;
  margin-bottom: 0.25rem;
}

.detail-item {
  margin-left: 0.5rem;
  margin-bottom: 0.25rem;
  line-height: 1.4;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .calendar-title {
    font-size: 1.25rem;
  }

  .calendar-day {
    min-height: 50px;
    padding: 0.25rem;
  }

  .day-number {
    font-size: 0.875rem;
  }

  .status-icon {
    font-size: 0.75rem;
  }
}
</style>
