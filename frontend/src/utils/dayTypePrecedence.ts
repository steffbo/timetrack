/**
 * Day Type Precedence
 *
 * Defines the precedence order for determining what type of day takes priority
 * when multiple conditions apply to the same date.
 *
 * Lower numbers have higher priority.
 *
 * See /precedence-rules.md for detailed documentation and rationale.
 */
export enum DayTypePrecedence {
  WEEKEND = 1,           // Not a working day per config
  PUBLIC_HOLIDAY = 2,    // Always wins
  SICK = 3,              // Medical - overrides recurring
  CHILD_SICK = 3,        // Child sick - same priority as SICK
  PERSONAL = 4,          // Personal - overrides recurring
  EDUCATION = 4,         // Education/Training - same priority as PERSONAL
  RECURRING_OFF_DAY = 5, // Scheduled off-days
  VACATION = 6,          // Planned vacation
  WORK = 7,              // Actual work entries
  NO_ENTRY = 8           // No data
}

/**
 * Get the numeric precedence value for a day type string.
 * Lower values indicate higher priority.
 *
 * @param type The day type as a string
 * @returns The precedence value (or 99 if unknown)
 */
export function getPrecedence(type: string): number {
  const normalizedType = type.toUpperCase().replace(/-/g, '_')

  const precedenceMap: Record<string, number> = {
    'WEEKEND': DayTypePrecedence.WEEKEND,
    'PUBLIC_HOLIDAY': DayTypePrecedence.PUBLIC_HOLIDAY,
    'SICK': DayTypePrecedence.SICK,
    'CHILD_SICK': DayTypePrecedence.CHILD_SICK,
    'SICK_LEAVE': DayTypePrecedence.SICK, // Alias
    'PERSONAL': DayTypePrecedence.PERSONAL,
    'EDUCATION': DayTypePrecedence.EDUCATION,
    'RECURRING_OFF': DayTypePrecedence.RECURRING_OFF_DAY, // Alias
    'RECURRING_OFF_DAY': DayTypePrecedence.RECURRING_OFF_DAY,
    'VACATION': DayTypePrecedence.VACATION,
    'WORK': DayTypePrecedence.WORK,
    'NO_ENTRY': DayTypePrecedence.NO_ENTRY
  }

  return precedenceMap[normalizedType] ?? 99
}

/**
 * Compare two day types and return the one with higher priority.
 *
 * @param type1 First day type
 * @param type2 Second day type
 * @returns The day type with higher priority (lower precedence number)
 */
export function getHigherPriority(type1: string, type2: string): string {
  const p1 = getPrecedence(type1)
  const p2 = getPrecedence(type2)
  return p1 <= p2 ? type1 : type2
}

/**
 * Resolve the primary day type from a daily summary.
 * Applies precedence rules to determine which type should be displayed/used.
 *
 * @param summary The daily summary data
 * @param isWorkingDay Whether the day is configured as a working day
 * @returns The primary day type string
 */
export function resolvePrimaryDayType(
  summary: any,
  isWorkingDay: boolean
): string {
  // If no summary, determine based on working day configuration
  if (!summary) {
    return isWorkingDay ? 'NO_ENTRY' : 'WEEKEND'
  }

  let primaryType = isWorkingDay ? 'NO_ENTRY' : 'WEEKEND'
  let currentPrecedence = getPrecedence(primaryType)

  // Check public holidays (highest priority after weekend)
  if (summary.timeOffEntries && summary.timeOffEntries.length > 0) {
    const publicHoliday = summary.timeOffEntries.find((e: any) => e.timeOffType === 'PUBLIC_HOLIDAY')
    if (publicHoliday) {
      const p = getPrecedence('PUBLIC_HOLIDAY')
      if (p < currentPrecedence) {
        primaryType = 'PUBLIC_HOLIDAY'
        currentPrecedence = p
      }
    }

    // Check sick leave
    const sick = summary.timeOffEntries.find((e: any) => e.timeOffType === 'SICK')
    if (sick) {
      const p = getPrecedence('SICK')
      if (p < currentPrecedence) {
        primaryType = 'SICK_LEAVE'
        currentPrecedence = p
      }
    }

    // Check child sick leave
    const childSick = summary.timeOffEntries.find((e: any) => e.timeOffType === 'CHILD_SICK')
    if (childSick) {
      const p = getPrecedence('CHILD_SICK')
      if (p < currentPrecedence) {
        primaryType = 'CHILD_SICK'
        currentPrecedence = p
      }
    }

    // Check personal leave
    const personal = summary.timeOffEntries.find((e: any) => e.timeOffType === 'PERSONAL')
    if (personal) {
      const p = getPrecedence('PERSONAL')
      if (p < currentPrecedence) {
        primaryType = 'PERSONAL'
        currentPrecedence = p
      }
    }

    // Check education/training leave
    const education = summary.timeOffEntries.find((e: any) => e.timeOffType === 'EDUCATION')
    if (education) {
      const p = getPrecedence('EDUCATION')
      if (p < currentPrecedence) {
        primaryType = 'EDUCATION'
        currentPrecedence = p
      }
    }

    // Check vacation
    const vacation = summary.timeOffEntries.find((e: any) => e.timeOffType === 'VACATION')
    if (vacation) {
      const p = getPrecedence('VACATION')
      if (p < currentPrecedence) {
        primaryType = 'VACATION'
        currentPrecedence = p
      }
    }
  }

  // Check recurring off-days
  if (summary.recurringOffDays && summary.recurringOffDays.length > 0) {
    const p = getPrecedence('RECURRING_OFF_DAY')
    if (p < currentPrecedence) {
      primaryType = 'RECURRING_OFF'
      currentPrecedence = p
    }
  }

  // Check regular work entries
  if (summary.entries && summary.entries.length > 0) {
    const p = getPrecedence('WORK')
    if (p < currentPrecedence) {
      // Determine specific work type
      const hasSick = summary.entries.some((e: any) => e.entryType === 'SICK')
      const hasPTO = summary.entries.some((e: any) => e.entryType === 'PTO')
      const hasEvent = summary.entries.some((e: any) => e.entryType === 'EVENT')

      if (hasSick) {
        primaryType = 'SICK'
      } else if (hasPTO) {
        primaryType = 'PTO'
      } else if (hasEvent) {
        primaryType = 'EVENT'
      } else {
        primaryType = 'WORK'
      }
      currentPrecedence = p
    }
  }

  return primaryType
}
