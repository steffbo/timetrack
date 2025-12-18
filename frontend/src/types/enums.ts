/**
 * Enums and type definitions for better type safety
 */

export enum TimeOffType {
  VACATION = 'VACATION',
  SICK = 'SICK',
  CHILD_SICK = 'CHILD_SICK',
  PERSONAL = 'PERSONAL',
  PUBLIC_HOLIDAY = 'PUBLIC_HOLIDAY'
}

export enum EntryType {
  WORK = 'WORK',
  SICK = 'SICK',
  PTO = 'PTO',
  EVENT = 'EVENT'
}

/**
 * Validates date string format (YYYY-MM-DD)
 */
export function isValidDateString(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value)
}

/**
 * Validates that a date string is a valid date
 */
export function isValidDate(value: string): boolean {
  if (!isValidDateString(value)) return false
  const date = new Date(value)
  return !isNaN(date.getTime()) && date.toISOString().split('T')[0] === value
}
