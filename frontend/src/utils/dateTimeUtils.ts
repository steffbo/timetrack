/**
 * Shared date and time utility functions for the dashboard components
 */

/**
 * Format a duration in hours to "Xh Ym" format
 */
export const formatDuration = (hours: number): string => {
  const h = Math.floor(hours)
  const m = Math.round((hours - h) * 60)
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

/**
 * Format a time string (ISO) to HH:MM format
 */
export const formatTime = (dateStr: string): string => {
  const date = new Date(dateStr)
  return date.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
}

/**
 * Format a date string to DD.MM.YYYY format
 */
export const formatDate = (dateStr: string | Date): string => {
  const date = typeof dateStr === 'string' ? new Date(dateStr) : dateStr
  return date.toLocaleDateString('de-DE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

/**
 * Format a date string to ISO date format (YYYY-MM-DD)
 */
export const formatDateISO = (date: Date): string => {
  return date.toISOString().split('T')[0]
}

/**
 * Calculate time difference in minutes between two time strings (HH:MM format)
 */
export const calculateTimeDiffMinutes = (time1: string, time2: string): number => {
  const [h1, m1] = time1.split(':').map(Number)
  const [h2, m2] = time2.split(':').map(Number)
  const minutes1 = h1 * 60 + m1
  const minutes2 = h2 * 60 + m2
  return Math.abs(minutes1 - minutes2)
}

/**
 * Get weekday number (1=Monday, 7=Sunday) from a date
 */
export const getWeekdayNumber = (date: Date): number => {
  const dayOfWeek = date.getDay() // 0=Sunday, 1=Monday, ..., 6=Saturday
  return dayOfWeek === 0 ? 7 : dayOfWeek // Convert to 1=Monday, 7=Sunday
}

/**
 * Calculate duration between two dates in "Xh Ym" format
 */
export const calculateDuration = (clockIn: string, clockOut: string | null, breakMinutes: number = 0): string => {
  if (!clockOut) return '-'

  const start = new Date(clockIn)
  const end = new Date(clockOut)
  const diffMs = end.getTime() - start.getTime()
  const diffMinutes = Math.floor(diffMs / 60000) - breakMinutes
  const hours = Math.floor(diffMinutes / 60)
  const minutes = diffMinutes % 60

  return `${hours}h ${minutes}m`
}
