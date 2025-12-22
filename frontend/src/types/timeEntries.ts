import type {
  TimeEntryResponse,
  TimeOffResponse,
  RecurringOffDayResponse,
  PublicHolidayResponse
} from '@/api/generated'

export type TypeEntry = {
  type: 'work' | 'timeoff' | 'recurring-off' | 'weekend' | 'public-holiday'
  data:
    | TimeEntryResponse
    | TimeOffResponse
    | RecurringOffDayResponse
    | PublicHolidayResponse
    | { description: string }
}

export type DisplayEntry = {
  date: string
  types: TypeEntry[]
  workEntry?: TimeEntryResponse
  hasConflict?: boolean
}
