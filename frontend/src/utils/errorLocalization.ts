type TFunction = (key: string, params?: Record<string, any>) => string

const formatDateTime = (value: string, t: TFunction): string => {
  const normalized = value.replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString(t('locale'), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const localizeBackendMessage = (message: string, t: TFunction): string | null => {
  const overlapMatch = message.match(/^Time entry overlaps with existing entry from (.+) to (.+)$/)
  if (overlapMatch) {
    const [, start, end] = overlapMatch
    return t('errors.timeEntryOverlap', {
      start: formatDateTime(start, t),
      end: formatDateTime(end, t)
    })
  }

  if (message === 'Clock out time must be after clock in time') {
    return t('errors.clockOutAfterClockIn')
  }

  if (message === 'Cannot create manual entry while an active session exists. Please clock out first.') {
    return t('errors.activeSessionExists')
  }

  return null
}

export const getLocalizedErrorMessage = (
  error: any,
  t: TFunction | null | undefined,
  fallback: string
): string => {
  const rawMessage =
    error?.body?.message ||
    error?.response?.data?.message ||
    error?.message ||
    (typeof error === 'string' ? error : null)

  if (!rawMessage) {
    return fallback
  }

  if (t) {
    const localized = localizeBackendMessage(String(rawMessage), t)
    if (localized) {
      return localized
    }
  }

  return String(rawMessage)
}
