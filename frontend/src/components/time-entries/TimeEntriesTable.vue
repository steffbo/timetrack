<template>
  <DataTable
    :value="entries"
    :loading="loading"
    :row-class="getRowBackgroundClass"
    responsive-layout="scroll"
    :empty-message="t('timeEntries.noEntries')"
  >
    <Column field="date" :header="t('timeEntries.date')">
      <template #body="{ data: entry }">
        {{ formatDate(entry.date) }}
      </template>
    </Column>
    <Column :header="t('timeEntries.type.label')">
      <template #body="{ data: entry }">
        <div class="type-emojis">
          <span
            v-for="(typeEntry, idx) in entry.types"
            :key="idx"
            class="type-emoji"
            v-tooltip.top="getTypeTooltip(typeEntry)"
          >
            {{ getTypeEmoji(typeEntry) }}
          </span>
          <i
            v-if="entry.hasConflict"
            class="pi pi-exclamation-triangle conflict-warning-icon"
            v-tooltip.top="t('timeEntries.conflictWarning')"
          ></i>
        </div>
      </template>
    </Column>
    <Column field="clockIn" :header="t('timeEntries.clockIn')">
      <template #body="{ data: entry }">
        <span v-if="entry.workEntry">
          {{ formatDateTime(entry.workEntry.clockIn) }}
        </span>
        <span v-else>-</span>
      </template>
    </Column>
    <Column field="clockOut" :header="t('timeEntries.clockOut')">
      <template #body="{ data: entry }">
        <span v-if="entry.workEntry">
          {{ formatDateTime(entry.workEntry.clockOut) }}
        </span>
        <span v-else>-</span>
      </template>
    </Column>
    <Column field="breakMinutes" :header="t('timeEntries.breakMinutes')">
      <template #body="{ data: entry }">
        <span v-if="entry.workEntry">
          {{ entry.workEntry.breakMinutes || 0 }} min
        </span>
        <span v-else>-</span>
      </template>
    </Column>
    <Column field="hoursWorked" :header="t('timeEntries.hoursWorked')">
      <template #body="{ data: entry }">
        <span v-if="entry.workEntry">
          {{ formatHours(entry.workEntry.hoursWorked) }}
        </span>
        <span v-else>-</span>
      </template>
    </Column>
    <Column field="difference" :header="t('timeEntries.difference')">
      <template #body="{ data: entry }">
        <Tag
          v-if="entry.workEntry && getDifferenceSeverity(entry.workEntry)"
          :value="getHoursDifference(entry.workEntry)"
          :severity="getDifferenceSeverity(entry.workEntry)"
        />
        <span v-else>-</span>
      </template>
    </Column>
    <Column field="notes" :header="t('timeEntries.notes')">
      <template #body="{ data: entry }">
        <template v-if="entry.types.length === 1">
          <span v-if="entry.types[0].type === 'recurring-off'">
            {{ (entry.types[0].data as RecurringOffDayResponse).description || '-' }}
          </span>
          <span v-else-if="entry.types[0].type === 'weekend'">
            -
          </span>
          <span v-else-if="entry.types[0].type === 'public-holiday'">
            {{ (entry.types[0].data as PublicHolidayResponse).name || '-' }}
          </span>
          <span v-else>
            {{ (entry.types[0].data as { notes?: string }).notes || '-' }}
          </span>
        </template>
        <template v-else>
          <span v-if="entry.workEntry">
            {{ entry.workEntry.notes || '-' }}
          </span>
          <span v-else>-</span>
        </template>
      </template>
    </Column>
    <Column :header="t('actions')">
      <template #body="{ data: entry }">
        <template v-if="entry.workEntry">
          <Button
            icon="pi pi-pencil"
            class="p-button-text p-button-sm"
            @click="emit('edit', entry.workEntry)"
            :disabled="isActiveEntry(entry.workEntry)"
          />
          <Button
            icon="pi pi-trash"
            class="p-button-text p-button-danger p-button-sm"
            @click="emit('delete', entry.workEntry)"
            :disabled="isActiveEntry(entry.workEntry)"
          />
        </template>
        <span v-else>-</span>
      </template>
    </Column>
  </DataTable>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import { parseLocalDate } from '@/utils/dateTimeUtils'
import type { WorkingHoursResponse, TimeEntryResponse, TimeOffResponse, PublicHolidayResponse, RecurringOffDayResponse } from '@/api/generated'
import type { DisplayEntry, TypeEntry } from '@/types/timeEntries'

interface Props {
  entries: DisplayEntry[]
  loading: boolean
  workingHours: WorkingHoursResponse | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'edit', entry: TimeEntryResponse): void
  (e: 'delete', entry: TimeEntryResponse): void
}>()

const { t } = useI18n()

const formatDateTime = (dateTime: string | undefined) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleTimeString(t('locale'), {
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatDate = (date: string | undefined) => {
  if (!date) return '-'
  const dateObj = parseLocalDate(date)
  const day = String(dateObj.getDate()).padStart(2, '0')
  const month = String(dateObj.getMonth() + 1).padStart(2, '0')
  const year = dateObj.getFullYear()
  return `${day}.${month}.${year}`
}

const formatHours = (hours: number | undefined) => {
  if (hours === undefined || hours === null) return '-'
  const h = Math.floor(hours)
  const m = Math.round((hours - h) * 60)
  return m > 0 ? `${h}h ${m}m` : `${h}h`
}

const getExpectedHoursForEntry = (entry: TimeEntryResponse, workingHours: WorkingHoursResponse | null): number | null => {
  if (!workingHours || !entry.entryDate) return null

  const entryDate = parseLocalDate(entry.entryDate)
  const dayOfWeek = entryDate.getDay() === 0 ? 7 : entryDate.getDay()
  const dayWorkingHours = workingHours.workingDays.find(
    wd => wd.weekday === dayOfWeek
  )

  if (!dayWorkingHours || !dayWorkingHours.isWorkingDay) {
    return 0
  }

  // Hours field already contains net hours (break subtracted when saved)
  return dayWorkingHours.hours || 0
}

const getHoursDifference = (entry: TimeEntryResponse): string => {
  const expectedHours = getExpectedHoursForEntry(entry, props.workingHours)
  if (expectedHours === null) return '-'

  const actualHours = entry.hoursWorked || 0
  const diffHours = actualHours - expectedHours
  const diffMinutes = Math.round(diffHours * 60)

  if (diffMinutes === 0) return '±0 min'

  const sign = diffMinutes > 0 ? '+' : ''
  return `${sign}${diffMinutes} min`
}

const getDifferenceSeverity = (entry: TimeEntryResponse): 'success' | 'warn' | 'danger' | 'secondary' | null => {
  const expectedHours = getExpectedHoursForEntry(entry, props.workingHours)
  if (expectedHours === null) return null

  const actualHours = entry.hoursWorked || 0
  const diffHours = actualHours - expectedHours
  const diffMinutes = Math.abs(Math.round(diffHours * 60))

  if (diffMinutes <= 15) return 'success'
  if (diffMinutes <= 45) return 'warn'
  return 'danger'
}

const isActiveEntry = (entry: TimeEntryResponse) => {
  return entry.isActive
}

const getTypeEmoji = (typeEntry: TypeEntry): string => {
  if (typeEntry.type === 'work') {
    return '🏢'
  } else if (typeEntry.type === 'public-holiday') {
    return '🎊'
  } else if (typeEntry.type === 'timeoff') {
    const timeOff = typeEntry.data as TimeOffResponse
    switch (timeOff.timeOffType) {
      case 'VACATION':
        return '🏝️'
      case 'SICK':
        return '😵‍💫'
      case 'CHILD_SICK':
        return '👩‍👧'
      case 'PERSONAL':
        return '🏠'
      case 'EDUCATION':
        return '📚'
      case 'PUBLIC_HOLIDAY':
        return '🎊'
      default:
        return '📅'
    }
  } else if (typeEntry.type === 'recurring-off') {
    return '📴'
  } else if (typeEntry.type === 'weekend') {
    return '🗓️'
  }
  return '📅'
}

const getTypeTooltip = (typeEntry: TypeEntry): string => {
  if (typeEntry.type === 'work') {
    return t('timeEntries.type.WORK')
  } else if (typeEntry.type === 'public-holiday') {
    return (typeEntry.data as PublicHolidayResponse).name || ''
  } else if (typeEntry.type === 'timeoff') {
    const timeOff = typeEntry.data as TimeOffResponse
    return t(`timeOff.type.${timeOff.timeOffType}`)
  } else if (typeEntry.type === 'recurring-off') {
    return t('timeEntries.recurringOffDay')
  } else if (typeEntry.type === 'weekend') {
    return t('timeEntries.weekend')
  }
  return ''
}

const getRowBackgroundClass = (entry: DisplayEntry): string => {
  if (entry.types.length === 0) return ''

  const primaryType = entry.types[0].type

  switch (primaryType) {
    case 'work':
      return 'row-bg-work'
    case 'public-holiday':
      return 'row-bg-public-holiday'
    case 'timeoff':
      const timeOff = entry.types[0].data as TimeOffResponse
      switch (timeOff.timeOffType) {
        case 'VACATION':
          return 'row-bg-vacation'
        case 'SICK':
          return 'row-bg-sick'
        case 'CHILD_SICK':
          return 'row-bg-sick'
        case 'PERSONAL':
          return 'row-bg-personal'
        case 'EDUCATION':
          return 'row-bg-education'
        default:
          return ''
      }
    case 'recurring-off':
      return 'row-bg-recurring-off'
    case 'weekend':
      return 'row-bg-weekend'
    default:
      return ''
  }
}
</script>

<style scoped>
.type-emojis {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.type-emoji {
  font-size: 1.25rem;
}

.conflict-warning-icon {
  color: var(--p-orange-500);
  font-size: 1rem;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>
