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
              class="today-button"
              :class="{ 'invisible-button': isCurrentMonth }"
            />
            <Button
              :label="t('dashboard.calendar.exportPdf')"
              @click="exportMonthlyPdf"
              size="small"
              icon="pi pi-file-pdf"
              outlined
              class="pdf-button"
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

        <!-- Days from previous month -->
        <div
          v-for="day in previousMonthDays"
          :key="`prev-${day.day}`"
          :ref="el => setDayRef(day.day, el, 'prev')"
          class="calendar-day adjacent-month"
          :class="getAdjacentDayClasses(day.day, 'prev')"
          :style="getAdjacentDayStyle(day.day, 'prev')"
          @click="handleAdjacentDayClick(day.day, 'prev', $event)"
          @mouseenter="handleAdjacentDayHover(day.day, 'prev', $event)"
          @mouseleave="handleDayLeave()"
        >
          <div class="day-content">
            <span class="day-number">{{ day.day }}</span>
            <div class="day-indicators">
              <i
                v-if="getAdjacentDayStatusIcon(day.day, 'prev')"
                :class="['status-icon', getAdjacentDayStatusIcon(day.day, 'prev'), getAdjacentDayStatusIconColor(day.day, 'prev')]"
              />
              <span
                v-for="emoji in getAdjacentDayEmojis(day.day, 'prev')"
                :key="emoji"
                class="day-emoji"
              >{{ emoji }}</span>
            </div>
          </div>
        </div>

        <!-- Days of the current month -->
        <div
          v-for="day in daysInMonth"
          :key="day"
          :ref="el => setDayRef(day, el, 'current')"
          class="calendar-day"
          :class="getDayClasses(day)"
          :style="getDayStyle(day)"
          @click="handleDayClick(day, $event)"
          @mouseenter="handleDayHover(day, $event)"
          @mouseleave="handleDayLeave()"
        >
          <div class="day-content">
            <span class="day-number">{{ day }}</span>
            <div class="day-indicators">
              <i
                v-if="getDayStatusIcon(day)"
                :class="['status-icon', getDayStatusIcon(day), getDayStatusIconColor(day)]"
              />
              <span
                v-for="emoji in getDayEmojis(day)"
                :key="emoji"
                class="day-emoji"
              >{{ emoji }}</span>
            </div>
          </div>
        </div>

        <!-- Days from next month -->
        <div
          v-for="day in nextMonthDays"
          :key="`next-${day.day}`"
          :ref="el => setDayRef(day.day, el, 'next')"
          class="calendar-day adjacent-month"
          :class="getAdjacentDayClasses(day.day, 'next')"
          :style="getAdjacentDayStyle(day.day, 'next')"
          @click="handleAdjacentDayClick(day.day, 'next', $event)"
          @mouseenter="handleAdjacentDayHover(day.day, 'next', $event)"
          @mouseleave="handleDayLeave()"
        >
          <div class="day-content">
            <span class="day-number">{{ day.day }}</span>
            <div class="day-indicators">
              <i
                v-if="getAdjacentDayStatusIcon(day.day, 'next')"
                :class="['status-icon', getAdjacentDayStatusIcon(day.day, 'next'), getAdjacentDayStatusIconColor(day.day, 'next')]"
              />
              <span
                v-for="emoji in getAdjacentDayEmojis(day.day, 'next')"
                :key="emoji"
                class="day-emoji"
              >{{ emoji }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </Card>

  <!-- Hover popover for day details -->
  <Popover
    ref="hoverPopoverRef"
    :dismissable="false"
  >
    <div v-if="hoveredDay !== null" class="day-details">
      <div class="day-details-content">
        <div
          v-for="row in getDayDetails(hoveredDay)"
          :key="row.key"
          class="detail-row"
        >
          <span v-if="row.emoji" class="detail-emoji">{{ row.emoji }}</span>
          <strong v-if="row.strong" class="detail-text">{{ row.text }}</strong>
          <span v-else class="detail-text">{{ row.text }}</span>
        </div>
      </div>
    </div>
  </Popover>

  <!-- Sticky popover for day details -->
  <Popover
    ref="stickyPopoverRef"
    :dismissable="true"
    @hide="handleOverlayHide"
  >
    <div v-if="stickyDay !== null" class="day-details day-details-sticky">
      <div class="day-details-content">
        <div
          v-for="row in getDayDetails(stickyDay)"
          :key="row.key"
          class="detail-row"
        >
          <span v-if="row.emoji" class="detail-emoji">{{ row.emoji }}</span>
          <strong v-if="row.strong" class="detail-text">{{ row.text }}</strong>
          <span v-else class="detail-text">{{ row.text }}</span>
        </div>
      </div>

      <!-- Action buttons for sticky panel -->
      <div class="day-details-actions">
        <!-- Edit + Delete buttons when there's exactly one entry -->
        <div v-if="hasSingleEntry(stickyDay)" class="edit-delete-buttons">
          <Button
            :label="t('edit')"
            icon="pi pi-pencil"
            size="small"
            @click="handleEditAllClick(stickyDay)"
            outlined
            class="edit-button"
          />
          <Button
            icon="pi pi-times"
            size="small"
            @click="handleQuickDeleteClick(stickyDay)"
            severity="danger"
            class="delete-button"
            :title="t('delete')"
          />
        </div>
        <!-- Single Edit button if there are multiple entries or time off -->
        <Button
          v-else-if="hasTimeEntries(stickyDay) || hasTimeOff(stickyDay)"
          :label="t('edit')"
          icon="pi pi-pencil"
          size="small"
          @click="handleEditAllClick(stickyDay)"
          outlined
          fluid
        />
        <!-- Quick Entry buttons if no entries and is working day -->
        <div v-else-if="canCreateQuickEntry(stickyDay)" class="quick-entry-buttons">
          <Button
            :label="t('dashboard.selectedDay.quickEntry')"
            icon="pi pi-bolt"
            size="small"
            @click="handleQuickEntryClick(stickyDay)"
            class="quick-entry-left"
          />
          <Button
            icon="pi pi-pencil"
            size="small"
            @click="handleManualEntryClick(stickyDay)"
            class="quick-entry-right"
            outlined
            :title="t('dashboard.selectedDay.manualEntry')"
          />
        </div>
        <!-- Add Time Off button only if no entries exist -->
        <Button
          v-if="!hasTimeEntries(stickyDay) && !hasTimeOff(stickyDay)"
          :label="t('dashboard.selectedDay.addTimeOff')"
          icon="pi pi-calendar-plus"
          size="small"
          @click="handleTimeOffClick(stickyDay)"
          outlined
          fluid
        />
      </div>
    </div>
  </Popover>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Popover from 'primevue/popover'
import type { DailySummaryResponse, WorkingHoursResponse } from '@/api/generated'
import { OpenAPI } from '@/api/generated'
import axios from 'axios'
import { resolvePrimaryDayType } from '@/utils/dayTypePrecedence'
import { formatDuration, calculateTimeDiffMinutes, getWeekdayNumber as getWeekdayFromDate } from '@/utils/dateTimeUtils'
import { useErrorHandler } from '@/composables/useErrorHandler'

const { t } = useI18n()
const toast = useToast()
const { handleError } = useErrorHandler()

// State for hover and sticky overlays
const hoveredDay = ref<number | null>(null) // Currently hovered day
const stickyDay = ref<number | null>(null) // Day that is sticky (clicked)
const hoverPanelVisible = ref(false)
const stickyPanelVisible = ref(false)
const hoverPanelTarget = ref<HTMLElement | null>(null)
const stickyPanelTarget = ref<HTMLElement | null>(null)
const hoverPopoverRef = ref<any>(null)
const stickyPopoverRef = ref<any>(null)
const dayRefs = ref<Map<number, HTMLElement>>(new Map())
let hoverTimeout: ReturnType<typeof setTimeout> | null = null

// State for PDF export
const exportLoading = ref(false)

interface Props {
  currentMonth: Date
  dailySummaries: DailySummaryResponse[]
  workingHours: WorkingHoursResponse | null
  halfDayHolidaysEnabled: boolean
}

const props = defineProps<Props>()

import type { TimeEntryResponse, TimeOffResponse } from '@/api/generated'

interface Emits {
  (e: 'monthChange', date: Date): void
  (e: 'daySelected', payload: { date: string, summary: DailySummaryResponse | undefined }): void
  (e: 'quickEntry', payload: { date: string, startTime: string, endTime: string, breakMinutes?: number }): void
  (e: 'manualEntry', payload: { date: string }): void
  (e: 'addTimeOff', payload: { date: string }): void
  (e: 'editAll', payload: { date: string, entries: TimeEntryResponse[], timeOffEntries: TimeOffResponse[] }): void
  (e: 'quickDelete', payload: { date: string, entry?: TimeEntryResponse, timeOffEntry?: TimeOffResponse }): void
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

//Calculate days from previous month to show
const previousMonthDays = computed(() => {
  const count = emptyDaysAtStart.value
  if (count === 0) return []

  const prevMonth = currentMonthIndex.value === 0 ? 11 : currentMonthIndex.value - 1
  const prevYear = currentMonthIndex.value === 0 ? currentYear.value - 1 : currentYear.value
  const daysInPrevMonth = new Date(prevYear, prevMonth + 1, 0).getDate()

  const days = []
  for (let i = 0; i < count; i++) {
    days.push({ day: daysInPrevMonth - count + i + 1, month: prevMonth, year: prevYear })
  }
  return days
})

// Calculate days from next month to show - only fill the last row
const nextMonthDays = computed(() => {
  const usedCells = emptyDaysAtStart.value + daysInMonth.value
  const remainderInWeek = usedCells % 7
  const count = remainderInWeek === 0 ? 0 : 7 - remainderInWeek
  if (count === 0) return []

  const nextMonth = currentMonthIndex.value === 11 ? 0 : currentMonthIndex.value + 1
  const nextYear = currentMonthIndex.value === 11 ? currentYear.value + 1 : currentYear.value

  const days = []
  for (let i = 1; i <= count; i++) {
    days.push({ day: i, month: nextMonth, year: nextYear })
  }
  return days
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

// Helper to check if a day is a working day according to working hours config
const isWorkingDay = (day: number): boolean => {
  const weekday = getWeekdayNumber(day)
  const workingDayConfig = getWorkingDayConfig(weekday)
  return workingDayConfig?.isWorkingDay ?? false
}

// Helper to determine primary entry type for a day
// Uses centralized precedence rules from /precedence-rules.md
const getPrimaryEntryType = (day: number): string => {
  const summary = getSummaryForDay(day)
  const isDayWorking = isWorkingDay(day)

  return resolvePrimaryDayType(summary, isDayWorking)
}

// Get CSS classes for a day cell
const getDayClasses = (day: number) => {
  const today = new Date()
  const isToday =
    day === today.getDate() &&
    currentMonthIndex.value === today.getMonth() &&
    currentYear.value === today.getFullYear()

  const summary = getSummaryForDay(day)
  const hasConflict = summary?.conflictWarning != null

  return {
    'is-today': isToday,
    'is-sticky': day === stickyDay.value,
    'has-conflict': hasConflict
  }
}

// Get background color style for a day cell
const getDayStyle = (day: number) => {
  const summary = getSummaryForDay(day)
  const entryType = getPrimaryEntryType(day)

  // Check if this is a half-day holiday (Dec 24 or 31) with vacation
  const hasVacation = summary?.timeOffEntries?.some(e => e.timeOffType === 'VACATION')
  const isDecHalfDay = isHalfDayHoliday(day, currentMonthIndex.value + 1)

  // If it's a vacation on a half-day holiday, create diagonal split
  // Bottom-left: cyan (vacation), Top-right: orange (half-day holiday)
  if (hasVacation && isDecHalfDay) {
    return {
      background: 'linear-gradient(to top right, var(--p-cyan-50) 0%, var(--p-cyan-50) 49.5%, var(--p-orange-50) 50.5%, var(--p-orange-50) 100%)'
    }
  }

  const colorMap: Record<string, string> = {
    'WORK': 'var(--p-green-50)',
    'SICK': 'var(--p-red-50)',
    'CHILD_SICK': 'var(--p-red-50)',
    'PTO': 'var(--p-blue-50)',
    'EVENT': 'var(--p-purple-50)',
    'VACATION': 'var(--p-cyan-50)',
    'SICK_LEAVE': 'var(--p-red-50)',
    'PERSONAL': 'var(--p-blue-100)',
    'PUBLIC_HOLIDAY': 'var(--p-orange-50)',
    'TIME_OFF': 'var(--p-amber-50)',
    'RECURRING_OFF': 'var(--p-indigo-100)',
    'WEEKEND': 'var(--p-surface-100)',
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

// Get color class for status icon based on time difference
const getDayStatusIconColor = (day: number): string | null => {
  const summary = getSummaryForDay(day)
  if (!summary || summary.status === 'NO_ENTRY') return null

  // Calculate time difference in minutes
  const expectedHours = summary.expectedHours || 0
  const actualHours = summary.actualHours || 0
  const diffHours = Math.abs(actualHours - expectedHours)
  const diffMinutes = Math.round(diffHours * 60)

  // Apply same color logic as TimeEntriesView
  // Within 15 minutes: green
  if (diffMinutes <= 15) return 'status-icon-success'

  // Between 15-45 minutes: yellow
  if (diffMinutes <= 45) return 'status-icon-warn'

  // Over 45 minutes: red
  return 'status-icon-danger'
}

// Check if a specific date is a half-day holiday (Dec 24 or Dec 31)
// Only returns true if the user has enabled half-day holidays in their profile
const isHalfDayHoliday = (day: number, month: number): boolean => {
  return props.halfDayHolidaysEnabled && (month === 12 && (day === 24 || day === 31))
}

// Get emoji indicators for a day's time-off entries
// Shows emojis in precedence order: public-holiday > timeoff (sick/personal/vacation) > recurring
// Uses same emojis as the time entries view for consistency
const getDayEmojis = (day: number): string[] => {
  const summary = getSummaryForDay(day)
  if (!summary) return []

  const emojis: string[] = []

  // Check time-off entries in precedence order
  if (summary.timeOffEntries && summary.timeOffEntries.length > 0) {
    const hasSick = summary.timeOffEntries.some(e => e.timeOffType === 'SICK')
    const hasChildSick = summary.timeOffEntries.some(e => e.timeOffType === 'CHILD_SICK')
    const hasPersonal = summary.timeOffEntries.some(e => e.timeOffType === 'PERSONAL')
    const hasEducation = summary.timeOffEntries.some(e => e.timeOffType === 'EDUCATION')
    const hasVacation = summary.timeOffEntries.some(e => e.timeOffType === 'VACATION')
    const hasPublicHoliday = summary.timeOffEntries.some(e => e.timeOffType === 'PUBLIC_HOLIDAY')

    if (hasPublicHoliday) emojis.push('🎊')
    if (hasSick) emojis.push('😵‍💫')
    if (hasChildSick) emojis.push('👩‍👧')
    if (hasPersonal) emojis.push('🏠')
    if (hasEducation) emojis.push('📚')

    // For vacation, show regular emoji (half-day indication is in tooltip)
    if (hasVacation) {
      emojis.push('🏝️')
    }
  }

  // Check recurring off-days
  if (summary.recurringOffDays && summary.recurringOffDays.length > 0) {
    emojis.push('📴')
  }

  return emojis
}

// Helper to get summary for adjacent month day
const getAdjacentSummaryForDay = (day: number, type: 'prev' | 'next'): DailySummaryResponse | undefined => {
  const monthData = type === 'prev' ? previousMonthDays.value.find(d => d.day === day) : nextMonthDays.value.find(d => d.day === day)
  if (!monthData) return undefined

  const dateStr = `${monthData.year}-${String(monthData.month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  return props.dailySummaries.find(summary => summary.date === dateStr)
}

// Helper to get primary entry type for adjacent month day
// Uses centralized precedence rules from /precedence-rules.md
const getAdjacentPrimaryEntryType = (day: number, type: 'prev' | 'next'): string => {
  const summary = getAdjacentSummaryForDay(day, type)

  // For adjacent months, we don't have working hours config easily accessible
  // so we use a simplified approach - assume it's a working day if it has data
  const isDayWorking = summary?.entries?.length > 0 || summary?.timeOffEntries?.length > 0

  return resolvePrimaryDayType(summary, isDayWorking)
}

// Get CSS classes for adjacent month day
const getAdjacentDayClasses = (day: number, type: 'prev' | 'next') => {
  const summary = getAdjacentSummaryForDay(day, type)
  const hasConflict = summary?.conflictWarning != null

  return {
    'is-sticky': false, // Adjacent days can't be sticky
    'has-conflict': hasConflict
  }
}

// Get background color style for adjacent month day
const getAdjacentDayStyle = (day: number, type: 'prev' | 'next') => {
  const summary = getAdjacentSummaryForDay(day, type)
  const entryType = getAdjacentPrimaryEntryType(day, type)

  // Check if this is a half-day holiday (Dec 24 or 31) with vacation for adjacent month
  const hasVacation = summary?.timeOffEntries?.some(e => e.timeOffType === 'VACATION')
  const monthData = type === 'prev' ? previousMonthDays.value.find(d => d.day === day) : nextMonthDays.value.find(d => d.day === day)
  const isDecHalfDay = monthData ? isHalfDayHoliday(day, monthData.month + 1) : false

  // If it's a vacation on a half-day holiday, create diagonal split
  // Bottom-left: cyan (vacation), Top-right: orange (half-day holiday)
  if (hasVacation && isDecHalfDay) {
    return {
      background: 'linear-gradient(to top right, var(--p-cyan-50) 0%, var(--p-cyan-50) 49.5%, var(--p-orange-50) 50.5%, var(--p-orange-50) 100%)',
      opacity: '0.5' // Muted appearance for adjacent months
    }
  }

  const colorMap: Record<string, string> = {
    'WORK': 'var(--p-green-50)',
    'SICK': 'var(--p-red-50)',
    'CHILD_SICK': 'var(--p-red-50)',
    'PTO': 'var(--p-blue-50)',
    'EVENT': 'var(--p-purple-50)',
    'VACATION': 'var(--p-cyan-50)',
    'SICK_LEAVE': 'var(--p-red-50)',
    'PERSONAL': 'var(--p-blue-100)',
    'EDUCATION': 'var(--p-indigo-50)',
    'PUBLIC_HOLIDAY': 'var(--p-orange-50)',
    'TIME_OFF': 'var(--p-amber-50)',
    'RECURRING_OFF': 'var(--p-indigo-100)',
    'WEEKEND': 'var(--p-surface-100)',
    'NO_ENTRY': 'var(--p-surface-0)'
  }

  return {
    backgroundColor: colorMap[entryType] || colorMap['NO_ENTRY'],
    opacity: '0.5' // Muted appearance for adjacent months
  }
}

// Get status icon for adjacent month day
const getAdjacentDayStatusIcon = (day: number, type: 'prev' | 'next'): string | null => {
  const summary = getAdjacentSummaryForDay(day, type)
  if (!summary || summary.status === 'NO_ENTRY') return null

  const iconMap: Record<string, string> = {
    'MATCHED': 'pi pi-check',
    'ABOVE_EXPECTED': 'pi pi-arrow-up',
    'BELOW_EXPECTED': 'pi pi-arrow-down'
  }

  return iconMap[summary.status] || null
}

// Get color class for adjacent day status icon based on time difference
const getAdjacentDayStatusIconColor = (day: number, type: 'prev' | 'next'): string | null => {
  const summary = getAdjacentSummaryForDay(day, type)
  if (!summary || summary.status === 'NO_ENTRY') return null

  // Calculate time difference in minutes
  const expectedHours = summary.expectedHours || 0
  const actualHours = summary.actualHours || 0
  const diffHours = Math.abs(actualHours - expectedHours)
  const diffMinutes = Math.round(diffHours * 60)

  // Apply same color logic as TimeEntriesView
  // Within 15 minutes: green
  if (diffMinutes <= 15) return 'status-icon-success'

  // Between 15-45 minutes: yellow
  if (diffMinutes <= 45) return 'status-icon-warn'

  // Over 45 minutes: red
  return 'status-icon-danger'
}

// Get emoji indicators for adjacent month day's time-off entries
// Uses same emojis as the overlay panel for consistency
const getAdjacentDayEmojis = (day: number, type: 'prev' | 'next'): string[] => {
  const summary = getAdjacentSummaryForDay(day, type)
  if (!summary) return []

  const emojis: string[] = []

  // Check time-off entries in precedence order
  if (summary.timeOffEntries && summary.timeOffEntries.length > 0) {
    const hasSick = summary.timeOffEntries.some(e => e.timeOffType === 'SICK')
    const hasChildSick = summary.timeOffEntries.some(e => e.timeOffType === 'CHILD_SICK')
    const hasPersonal = summary.timeOffEntries.some(e => e.timeOffType === 'PERSONAL')
    const hasEducation = summary.timeOffEntries.some(e => e.timeOffType === 'EDUCATION')
    const hasVacation = summary.timeOffEntries.some(e => e.timeOffType === 'VACATION')
    const hasPublicHoliday = summary.timeOffEntries.some(e => e.timeOffType === 'PUBLIC_HOLIDAY')

    if (hasPublicHoliday) emojis.push('🎊')
    if (hasSick) emojis.push('😵‍💫')
    if (hasChildSick) emojis.push('👩‍👧')
    if (hasPersonal) emojis.push('🏠')
    if (hasEducation) emojis.push('📚')

    // For vacation, show regular emoji (half-day indication is in tooltip)
    if (hasVacation) {
      emojis.push('🏝️')
    }
  }

  // Check recurring off-days
  if (summary.recurringOffDays && summary.recurringOffDays.length > 0) {
    emojis.push('📴')
  }

  return emojis
}

// Handle adjacent day click
const handleAdjacentDayClick = async (day: number, type: 'prev' | 'next', event: MouseEvent) => {
  // Clear any pending hover timeout
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = null
  }

  // Hide hover panel
  if (hoverPopoverRef.value) {
    hoverPopoverRef.value.hide()
  }
  hoverPanelVisible.value = false

  // Get the actual date for this adjacent day
  const monthData = type === 'prev'
    ? previousMonthDays.value.find(d => d.day === day)
    : nextMonthDays.value.find(d => d.day === day)

  if (monthData) {
    const dateStr = `${monthData.year}-${String(monthData.month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    const summary = getAdjacentSummaryForDay(day, type)
    emit('daySelected', { date: dateStr, summary })
  }

  // Show sticky panel for adjacent day
  const key = `${type}-${day}`
  const targetElement = dayRefs.value.get(key as any)
  
  if (targetElement) {
    // If clicking the same day that is already sticky, toggle off
    if (key === stickyDay.value) {
      if (stickyPopoverRef.value) {
        stickyPopoverRef.value.hide()
      }
      stickyPanelVisible.value = false
      stickyDay.value = null
      stickyPanelTarget.value = null
    } else {
      // Hide the previous sticky panel if it exists
      if (stickyDay.value !== null && stickyPopoverRef.value) {
        stickyPopoverRef.value.hide()
        stickyPanelVisible.value = false
      }

      // First hide any existing panel
      if (stickyPopoverRef.value) {
        stickyPopoverRef.value.hide()
      }
      
      // Wait for hide to complete, then show
      await nextTick()
      setTimeout(() => {
        stickyDay.value = key as any
        stickyPanelTarget.value = targetElement
        
        // Use imperative API to show popover with target
        if (stickyPopoverRef.value) {
          stickyPopoverRef.value.show(event, targetElement)
          stickyPanelVisible.value = true
        }
      }, 100)
    }
  }
}

// Handle adjacent day hover
const handleAdjacentDayHover = (day: number, type: 'prev' | 'next', event: MouseEvent) => {
  // Clear any pending hover timeout
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = null
  }

  // Skip hover panel if this day is already sticky
  const key = `${type}-${day}`
  if (stickyDay.value === key as any) {
    return
  }

  // Show hover overlay with reduced delay (100ms)
  hoverTimeout = setTimeout(async () => {
    hoveredDay.value = key as any
    const targetElement = dayRefs.value.get(key as any)
    if (targetElement && hoverPopoverRef.value) {
      hoverPanelTarget.value = targetElement
      await nextTick()
      hoverPopoverRef.value.show(event, targetElement)
      hoverPanelVisible.value = true
    }
  }, 100)
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

const isTypingTarget = (target: EventTarget | null): boolean => {
  if (!target || !(target instanceof HTMLElement)) return false
  const tag = target.tagName
  return target.isContentEditable || tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT'
}

const handleKeydown = (event: KeyboardEvent) => {
  if (isTypingTarget(event.target)) return

  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    previousMonth()
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    nextMonth()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})

// Day ref management
const setDayRef = (day: number, el: any, type: 'prev' | 'current' | 'next' = 'current') => {
  if (el) {
    // Store refs with a unique key based on type and day
    const key = type === 'current' ? day : `${type}-${day}`
    dayRefs.value.set(key as any, el as HTMLElement)
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
    if (targetElement && hoverPopoverRef.value) {
      hoverPopoverRef.value.show(event, targetElement)
      hoverPanelVisible.value = true
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
  if (hoverPopoverRef.value) {
    hoverPopoverRef.value.hide()
  }
  hoverPanelVisible.value = false
  hoveredDay.value = null
  hoverPanelTarget.value = null
}

// Handle day click for sticky overlay
const handleDayClick = async (day: number, event: MouseEvent) => {
  // Clear any hover timeout
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = null
  }

  // Hide hover panel
  if (hoverPopoverRef.value) {
    hoverPopoverRef.value.hide()
  }
  hoverPanelVisible.value = false

  // Emit day selected event for dashboard interaction
  const dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  const summary = getSummaryForDay(day)
  emit('daySelected', { date: dateStr, summary })

  // If clicking the same day that is already sticky, toggle off
  if (day === stickyDay.value) {
    if (stickyPopoverRef.value) {
      stickyPopoverRef.value.hide()
    }
    stickyPanelVisible.value = false
    stickyDay.value = null
    stickyPanelTarget.value = null
  } else {
    // Hide the previous sticky panel if it exists
    if (stickyDay.value !== null && stickyPopoverRef.value) {
      stickyPopoverRef.value.hide()
      stickyPanelVisible.value = false
    }

    // Update the sticky day state and show the panel
    const targetElement = dayRefs.value.get(day)
    
    if (targetElement) {
      // First hide any existing panel
      if (stickyPopoverRef.value) {
        stickyPopoverRef.value.hide()
      }
      
      // Wait for hide to complete, then show
      await nextTick()
      setTimeout(() => {
        stickyDay.value = day
        
        // Use imperative API to show popover with target
        if (stickyPopoverRef.value) {
          stickyPopoverRef.value.show(event, targetElement)
          stickyPanelVisible.value = true
        }
      }, 100)
    }
  }
}

// Handle overlay hide event
const handleOverlayHide = () => {
  // If user manually dismissed the overlay (click outside, ESC, etc), unsticky it
  stickyPanelVisible.value = false
  stickyDay.value = null
  stickyPanelTarget.value = null
}

// Helper to get working day config for a specific weekday (1=Monday, 7=Sunday)
const getWorkingDayConfig = (weekday: number) => {
  if (!props.workingHours?.workingDays) return null
  return props.workingHours.workingDays.find(wd => wd.weekday === weekday)
}

// Helper to get weekday number (1-7, where 1=Monday, 7=Sunday) from day of month
const getWeekdayNumber = (day: number): number => {
  const date = new Date(currentYear.value, currentMonthIndex.value, day)
  return getWeekdayFromDate(date)
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
    handleError(error, t('dashboard.calendar.exportError'))
  } finally {
    exportLoading.value = false
  }
}

// Build day details for the overlay
type DayDetailRow = {
  key: string
  emoji?: string
  text: string
  strong?: boolean
}

const getDayDetails = (day: number | string): DayDetailRow[] => {
  // Check if this is an adjacent day
  let summary: DailySummaryResponse | undefined
  let dateStr: string
  let actualDay: number
  let isAdjacentDay = false

  if (typeof day === 'string' && (day.startsWith('prev-') || day.startsWith('next-'))) {
    // Adjacent month day
    isAdjacentDay = true
    const [type, dayNum] = day.split('-')
    actualDay = parseInt(dayNum)
    summary = getAdjacentSummaryForDay(actualDay, type as 'prev' | 'next')

    // Get the actual date for this adjacent day
    const monthData = type === 'prev'
      ? previousMonthDays.value.find(d => d.day === actualDay)
      : nextMonthDays.value.find(d => d.day === actualDay)

    if (monthData) {
      dateStr = `${monthData.year}-${String(monthData.month + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
    } else {
      return [{ key: `empty-${day}`, text: t('dashboard.calendar.noEntries') }]
    }
  } else {
    // Current month day
    actualDay = typeof day === 'number' ? day : parseInt(day)
    summary = getSummaryForDay(actualDay)
    dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
  }

  if (!summary) {
    return [{ key: `empty-${dateStr}`, text: t('dashboard.calendar.noEntries') }]
  }

  const rows: DayDetailRow[] = []

  // Date
  rows.push({
    key: `date-${dateStr}`,
    emoji: '📅',
    text: dateStr,
    strong: true
  })

  // Get emoji for time off type
  const getTimeOffEmoji = (timeOffType: string): string => {
    const emojiMap: Record<string, string> = {
      'VACATION': '🏝️',
      'SICK': '😵‍💫',
      'CHILD_SICK': '👩‍👧',
      'PUBLIC_HOLIDAY': '🎊',
      'PERSONAL': '🏠',
      'EDUCATION': '📚'
    }
    return emojiMap[timeOffType] || '📅'
  }

  // Time off - show first for PTO days
  const hasPTOEntries = summary.timeOffEntries && summary.timeOffEntries.length > 0
  if (hasPTOEntries) {
    summary.timeOffEntries!.forEach((timeOff, index) => {
      const typeLabel = t(`timeOff.type.${timeOff.timeOffType}`)
      const emoji = getTimeOffEmoji(timeOff.timeOffType)

      // For public holidays, show the holiday name (stored in notes)
      if (timeOff.timeOffType === 'PUBLIC_HOLIDAY' && timeOff.notes) {
        rows.push({
          key: `timeoff-${timeOff.id ?? 'none'}-${index}-holiday`,
          emoji,
          text: timeOff.notes,
          strong: true
        })
      } else {
        // For vacation on Dec 24 or 31, add "half-day" indication
        let label = typeLabel
        if (timeOff.timeOffType === 'VACATION') {
          // Determine the month for this day
          let monthToCheck: number
          if (isAdjacentDay) {
            const monthData = typeof day === 'string' && day.startsWith('prev-')
              ? previousMonthDays.value.find(d => d.day === actualDay)
              : nextMonthDays.value.find(d => d.day === actualDay)
            monthToCheck = monthData ? monthData.month + 1 : 0
          } else {
            monthToCheck = currentMonthIndex.value + 1
          }

          if (isHalfDayHoliday(actualDay, monthToCheck)) {
            label = `${typeLabel} (${t('timeOff.halfDay')})`
          }
        }
        rows.push({
          key: `timeoff-${timeOff.id ?? 'none'}-${index}`,
          emoji,
          text: label,
          strong: true
        })
      }
    })
  }

  // Recurring off-days
  if (summary.recurringOffDays && summary.recurringOffDays.length > 0) {
    summary.recurringOffDays.forEach((offDay, index) => {
      const description = offDay.description || t('dashboard.calendar.recurringOffDay')
      rows.push({
        key: `recurring-${offDay.id ?? 'none'}-${index}`,
        emoji: '📴',
        text: description,
        strong: true
      })
    })
  }

  // Show work hours info only if there are actual work entries
  if (summary.entries && summary.entries.length > 0) {
    // Get working day config for comparison
    let weekday: number
    if (isAdjacentDay) {
      // For adjacent days, calculate the weekday from the actual date
      const monthData = typeof day === 'string' && day.startsWith('prev-')
        ? previousMonthDays.value.find(d => d.day === actualDay)
        : nextMonthDays.value.find(d => d.day === actualDay)

      if (monthData) {
        const date = new Date(monthData.year, monthData.month, actualDay)
        const dayOfWeek = date.getDay()
        weekday = dayOfWeek === 0 ? 7 : dayOfWeek
      } else {
        weekday = 1 // Fallback to Monday
      }
    } else {
      weekday = getWeekdayNumber(actualDay)
    }
    const workingDayConfig = getWorkingDayConfig(weekday)

    // Show planned times first for comparison
    if (workingDayConfig?.isWorkingDay && workingDayConfig.startTime && workingDayConfig.endTime) {
      const plannedHours = workingDayConfig.hours || 0
      rows.push({
        key: `planned-${dateStr}`,
        emoji: '⏲️',
        text: `${workingDayConfig.startTime} - ${workingDayConfig.endTime} (${formatDuration(plannedHours)})`
      })
    }

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

      rows.push({
        key: `actual-${dateStr}`,
        emoji: bookEmoji,
        text: `${actualStart} - ${actualEnd} (${formatDuration(summary.actualHours)})`
      })
    }
  }

  return rows
}

// Helper to get summary for a day (works with both current and adjacent months)
const getSummaryForAnyDay = (day: number | string): DailySummaryResponse | undefined => {
  if (typeof day === 'string' && (day.startsWith('prev-') || day.startsWith('next-'))) {
    const [type, dayNum] = day.split('-')
    const actualDay = parseInt(dayNum)
    return getAdjacentSummaryForDay(actualDay, type as 'prev' | 'next')
  } else {
    const actualDay = typeof day === 'number' ? day : parseInt(day)
    return getSummaryForDay(actualDay)
  }
}

// Check if day has existing time entries
const hasTimeEntries = (day: number | string): boolean => {
  const summary = getSummaryForAnyDay(day)
  return summary?.entries && summary.entries.length > 0 || false
}

// Check if day has existing time off
const hasTimeOff = (day: number | string): boolean => {
  const summary = getSummaryForAnyDay(day)
  return summary?.timeOffEntries && summary.timeOffEntries.length > 0 || false
}

// Check if day has exactly one entry (either 1 time entry OR 1 time-off, not both)
const hasSingleEntry = (day: number | string): boolean => {
  const summary = getSummaryForAnyDay(day)
  if (!summary) return false
  const timeEntryCount = summary.entries?.length || 0
  const timeOffCount = summary.timeOffEntries?.length || 0
  // Exactly one entry total (1 time entry with no time-off, or 1 time-off with no time entries)
  return (timeEntryCount === 1 && timeOffCount === 0) || (timeOffCount === 1 && timeEntryCount === 0)
}

// Get the single entry for a day (returns either a time entry or time-off entry)
const getSingleEntry = (day: number | string): { entry?: TimeEntryResponse, timeOffEntry?: TimeOffResponse } => {
  const summary = getSummaryForAnyDay(day)
  if (!summary) return {}
  if (summary.entries?.length === 1 && (!summary.timeOffEntries || summary.timeOffEntries.length === 0)) {
    return { entry: summary.entries[0] }
  }
  if (summary.timeOffEntries?.length === 1 && (!summary.entries || summary.entries.length === 0)) {
    return { timeOffEntry: summary.timeOffEntries[0] }
  }
  return {}
}

// Check if quick entry can be created for a day (has working hours)
const canCreateQuickEntry = (day: number | string): boolean => {
  let weekday: number
  let isAdjacentDay = false

  if (typeof day === 'string' && (day.startsWith('prev-') || day.startsWith('next-'))) {
    // Adjacent month day
    isAdjacentDay = true
    const [type, dayNum] = day.split('-')
    const actualDay = parseInt(dayNum)

    const monthData = type === 'prev'
      ? previousMonthDays.value.find(d => d.day === actualDay)
      : nextMonthDays.value.find(d => d.day === actualDay)

    if (monthData) {
      const date = new Date(monthData.year, monthData.month, actualDay)
      const dayOfWeek = date.getDay()
      weekday = dayOfWeek === 0 ? 7 : dayOfWeek
    } else {
      return false
    }
  } else {
    // Current month day
    const actualDay = typeof day === 'number' ? day : parseInt(day)
    weekday = getWeekdayNumber(actualDay)
  }

  const workingDayConfig = getWorkingDayConfig(weekday)
  return workingDayConfig?.isWorkingDay || false
}

// Handle quick entry button click
const handleQuickEntryClick = (day: number | string) => {
  let dateStr: string
  let weekday: number

  if (typeof day === 'string' && (day.startsWith('prev-') || day.startsWith('next-'))) {
    // Adjacent month day
    const [type, dayNum] = day.split('-')
    const actualDay = parseInt(dayNum)
    const monthData = type === 'prev'
      ? previousMonthDays.value.find(d => d.day === actualDay)
      : nextMonthDays.value.find(d => d.day === actualDay)

    if (monthData) {
      dateStr = `${monthData.year}-${String(monthData.month + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
      const date = new Date(monthData.year, monthData.month, actualDay)
      const dayOfWeek = date.getDay()
      weekday = dayOfWeek === 0 ? 7 : dayOfWeek
    } else {
      return
    }
  } else {
    // Current month day
    const actualDay = typeof day === 'number' ? day : parseInt(day)
    dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
    weekday = getWeekdayNumber(actualDay)
  }

  // Get working hours for this weekday
  const workingDayConfig = getWorkingDayConfig(weekday)
  if (!workingDayConfig || !workingDayConfig.startTime || !workingDayConfig.endTime) {
    return
  }

  emit('quickEntry', {
    date: dateStr,
    startTime: workingDayConfig.startTime,
    endTime: workingDayConfig.endTime,
    breakMinutes: workingDayConfig.breakMinutes ?? 0
  })
  stickyPanelVisible.value = false
}

// Handle manual entry button click
const handleManualEntryClick = (day: number | string) => {
  let dateStr: string

  if (typeof day === 'string' && (day.startsWith('prev-') || day.startsWith('next-'))) {
    // Adjacent month day
    const [type, dayNum] = day.split('-')
    const actualDay = parseInt(dayNum)
    const monthData = type === 'prev'
      ? previousMonthDays.value.find(d => d.day === actualDay)
      : nextMonthDays.value.find(d => d.day === actualDay)

    if (monthData) {
      dateStr = `${monthData.year}-${String(monthData.month + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
    } else {
      return
    }
  } else {
    // Current month day
    const actualDay = typeof day === 'number' ? day : parseInt(day)
    dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
  }

  emit('manualEntry', { date: dateStr })
  stickyPanelVisible.value = false
}

// Handle time off button click
const handleTimeOffClick = (day: number | string) => {
  let dateStr: string

  if (typeof day === 'string' && (day.startsWith('prev-') || day.startsWith('next-'))) {
    // Adjacent month day
    const [type, dayNum] = day.split('-')
    const actualDay = parseInt(dayNum)
    const monthData = type === 'prev'
      ? previousMonthDays.value.find(d => d.day === actualDay)
      : nextMonthDays.value.find(d => d.day === actualDay)

    if (monthData) {
      dateStr = `${monthData.year}-${String(monthData.month + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
    } else {
      return
    }
  } else {
    // Current month day
    const actualDay = typeof day === 'number' ? day : parseInt(day)
    dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
  }

  emit('addTimeOff', { date: dateStr })
  stickyPanelVisible.value = false
}

// Handle edit all button click (both entries and time off)
const handleEditAllClick = (day: number | string) => {
  const summary = getSummaryForAnyDay(day)
  if (!summary) return

  let dateStr: string

  if (typeof day === 'string' && (day.startsWith('prev-') || day.startsWith('next-'))) {
    // Adjacent month day
    const [type, dayNum] = day.split('-')
    const actualDay = parseInt(dayNum)
    const monthData = type === 'prev'
      ? previousMonthDays.value.find(d => d.day === actualDay)
      : nextMonthDays.value.find(d => d.day === actualDay)

    if (monthData) {
      dateStr = `${monthData.year}-${String(monthData.month + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
    } else {
      return
    }
  } else {
    // Current month day
    const actualDay = typeof day === 'number' ? day : parseInt(day)
    dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
  }

  emit('editAll', {
    date: dateStr,
    entries: summary.entries || [],
    timeOffEntries: summary.timeOffEntries || []
  })
  stickyPanelVisible.value = false
}

const handleQuickDeleteClick = (day: number | string) => {
  const singleEntry = getSingleEntry(day)
  if (!singleEntry.entry && !singleEntry.timeOffEntry) return

  let dateStr: string

  if (typeof day === 'string' && (day.startsWith('prev-') || day.startsWith('next-'))) {
    // Adjacent month day
    const [type, dayNum] = day.split('-')
    const actualDay = parseInt(dayNum)
    const monthData = type === 'prev'
      ? previousMonthDays.value.find(d => d.day === actualDay)
      : nextMonthDays.value.find(d => d.day === actualDay)

    if (monthData) {
      dateStr = `${monthData.year}-${String(monthData.month + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
    } else {
      return
    }
  } else {
    // Current month day
    const actualDay = typeof day === 'number' ? day : parseInt(day)
    dateStr = `${currentYear.value}-${String(currentMonthIndex.value + 1).padStart(2, '0')}-${String(actualDay).padStart(2, '0')}`
  }

  emit('quickDelete', {
    date: dateStr,
    entry: singleEntry.entry,
    timeOffEntry: singleEntry.timeOffEntry
  })
  
  // Close the popover after triggering delete
  hideStickyPanel()
}

</script>

<style scoped>
.monthly-calendar {
  height: 100%;
  overflow: visible;
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
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  column-gap: 0.5rem;
  width: 100%;
  max-width: 320px;
  margin: 0 auto;
}

.today-button {
  justify-self: end;
}

.pdf-button {
  justify-self: center;
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

.calendar-day.adjacent-month {
  opacity: 0.6;
}

.calendar-day.adjacent-month .day-number {
  color: var(--p-text-muted-color);
  font-weight: 400;
}

.calendar-day.adjacent-month:hover {
  opacity: 0.8;
  cursor: pointer;
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

.calendar-day.has-conflict {
  outline: 3px solid var(--p-orange-500);
  outline-offset: -3px;
  position: relative;
}

.calendar-day.has-conflict.is-today {
  /* When both today and conflict, show both borders */
  border: 2px solid var(--p-primary-color);
  outline: 3px solid var(--p-orange-500);
  outline-offset: -5px;
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

.day-indicators {
  position: absolute;
  bottom: 2px;
  right: 2px;
  display: flex;
  gap: 2px;
  align-items: center;
}

.status-icon {
  font-size: 0.875rem;
  color: var(--p-text-color);
  opacity: 0.9;
}

.day-emoji {
  font-size: 0.75rem;
  line-height: 1;
}

/* Color classes for status icons based on time difference */
.status-icon.status-icon-success {
  color: var(--p-green-600);
}

.status-icon.status-icon-warn {
  color: var(--p-yellow-600);
}

.status-icon.status-icon-danger {
  color: var(--p-red-600);
}

/* Day details overlay styles - style the OverlayPanel itself */
:deep(.p-overlaypanel) {
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.15), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
}

:deep(.p-overlaypanel .p-overlaypanel-content) {
  padding: 1rem;
}

.day-details {
  min-width: 280px;
  max-width: 400px;
}

.day-details-content {
  font-size: 0.875rem;  /* 14px - compact display */
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.125rem 0;  /* 2px - minimal */
  line-height: 1.3;
}

.detail-emoji {
  flex-shrink: 0;
}

.detail-text {
  flex: 1;
}

.detail-row:first-child {
  font-size: 0.9375rem;  /* 15px */
  font-weight: 600;
  margin-bottom: 0.25rem;
  padding-bottom: 0.375rem;
  border-bottom: 1px solid var(--p-surface-border);
  color: var(--p-primary-color);
}

.day-details-actions {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--p-surface-border);
}

.day-details-actions .p-button {
  background: white;
  border-color: var(--p-primary-color);
  color: var(--p-primary-color);
}

.day-details-actions .p-button:hover {
  background: var(--p-primary-color);
  border-color: var(--p-primary-color);
  color: white;
}

.quick-entry-buttons {
  display: flex;
  gap: var(--tt-spacing-2xs);  /* 4px - aligned to grid */
  width: 100%;
}

.quick-entry-left {
  flex: 1;
  background: var(--p-primary-color);
  border-color: var(--p-primary-color);
  color: white;
}

.quick-entry-left:hover {
  background: var(--p-primary-color);
  opacity: 0.9;
}

.quick-entry-right {
  flex: 0 0 auto;
  min-width: 2.5rem;
  padding: 0.5rem;
}

.edit-delete-buttons {
  display: flex;
  gap: var(--tt-spacing-2xs);  /* 4px - aligned to grid */
  width: 100%;
}

.edit-delete-buttons .edit-button {
  flex: 1;
}

.edit-delete-buttons .delete-button {
  flex: 0 0 auto;
  min-width: 2.5rem;
  padding: 0.5rem;
}

.detail-section {
  margin-top: var(--tt-spacing-sm);   /* 12px - aligned to grid */
  margin-bottom: var(--tt-spacing-2xs);  /* 4px - aligned to grid */
}

.detail-item {
  margin-left: var(--tt-spacing-xs);   /* 8px - aligned to grid */
  margin-bottom: var(--tt-spacing-2xs);  /* 4px - aligned to grid */
  line-height: 1.4;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .calendar-title {
    font-size: 1.25rem;
  }

  .calendar-header {
    padding: 0.75rem;
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

  .day-emoji {
    font-size: 0.65rem;
  }

  .calendar-grid {
    gap: 0.15rem;
  }

  .calendar-weekday {
    padding: 0.375rem;
    font-size: 0.75rem;
  }
}

@media (max-width: 480px) {
  .calendar-header {
    padding: 0.5rem;
    flex-wrap: wrap;
  }

  .calendar-title {
    font-size: 1.1rem;
  }

  .calendar-title-section {
    order: 2;
    width: 100%;
    margin-top: 0.5rem;
  }

  .nav-buttons-left,
  .nav-buttons-right {
    min-width: auto;
  }

  .calendar-day {
    min-height: 45px;
    padding: 0.2rem;
  }

  .day-number {
    font-size: 0.8rem;
  }

  .calendar-grid {
    gap: 0.1rem;
  }

  .calendar-weekday {
    padding: 0.25rem;
    font-size: 0.7rem;
  }

  .day-details {
    min-width: 90vw;
    max-width: 90vw;
  }
}
</style>
