import { useToast } from 'primevue/usetoast'
import { useI18n } from 'vue-i18n'

/**
 * Composable for consistent error handling across the application
 */
export function useErrorHandler() {
  const toast = useToast()
  const { t } = useI18n()

  /**
   * Handle errors with consistent toast notifications
   * @param error - The error object (can be any type)
   * @param defaultMessage - Default message key if error doesn't have a message
   * @param options - Additional options for error handling
   */
  const handleError = (
    error: any,
    defaultMessage: string,
    options?: {
      severity?: 'error' | 'warn' | 'info'
      life?: number
      logError?: boolean
    }
  ) => {
    const severity = options?.severity || 'error'
    const life = options?.life || 5000
    const logError = options?.logError !== false // Default to true

    // Extract error message from various possible error formats
    let message = defaultMessage
    if (error?.body?.message) {
      message = error.body.message
    } else if (error?.response?.data?.message) {
      message = error.response.data.message
    } else if (error?.message) {
      message = error.message
    } else if (typeof error === 'string') {
      message = error
    }

    // Show toast notification
    toast.add({
      severity,
      summary: t('error'),
      detail: message,
      life
    })

    // Log error to console for debugging
    if (logError) {
      console.error('Error:', error)
    }
  }

  /**
   * Handle success messages consistently
   */
  const handleSuccess = (message: string, life: number = 3000) => {
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: message,
      life
    })
  }

  /**
   * Handle warning messages consistently
   */
  const handleWarning = (message: string, life: number = 3000) => {
    toast.add({
      severity: 'warn',
      summary: t('warning'),
      detail: message,
      life
    })
  }

  /**
   * Handle info messages consistently
   */
  const handleInfo = (message: string, life: number = 3000) => {
    toast.add({
      severity: 'info',
      summary: t('info'),
      detail: message,
      life
    })
  }

  return {
    handleError,
    handleSuccess,
    handleWarning,
    handleInfo
  }
}
