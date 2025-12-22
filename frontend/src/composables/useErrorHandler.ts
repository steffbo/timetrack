import { getCurrentInstance } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useI18n } from 'vue-i18n'
import type { ToastServiceMethods } from 'primevue/toastservice'
import { getLocalizedErrorMessage } from '@/utils/errorLocalization'

/**
 * Composable for consistent error handling across the application
 * @param options - Optional dependencies for use outside component setup
 */
export function useErrorHandler(options?: {
  toast?: ToastServiceMethods | null
  t?: ((key: string) => string) | null
}) {
  // Check if we're in a component context
  const instance = getCurrentInstance()
  const isComponentContext = instance !== null

  // Try to get toast and i18n
  // If options are explicitly provided (including null), use them
  // If options are undefined and we're in a component context, try to call composables
  let toast: ToastServiceMethods | null = null
  let t: ((key: string) => string) | null = null

  // Check if toast was explicitly provided
  if (options?.toast !== undefined) {
    toast = options.toast
  } else if (isComponentContext) {
    // Not explicitly provided but in component context, try to get it from composable
    try {
      toast = useToast()
    } catch (e) {
      // If useToast fails, use null
      toast = null
    }
  }
  // If not in component context and not explicitly provided, toast remains null

  // Check if t was explicitly provided
  if (options?.t !== undefined) {
    t = options.t
  } else if (isComponentContext) {
    // Not explicitly provided but in component context, try to get it from composable
    try {
      const i18n = useI18n()
      t = i18n.t
    } catch (e) {
      // If useI18n fails, use null
      t = null
    }
  }
  // If not in component context and not explicitly provided, t remains null

  /**
   * Handle errors with consistent toast notifications
   * @param error - The error object (can be any type)
   * @param defaultMessage - Default message key if error doesn't have a message
   * @param options - Additional options for error handling
   */
  const handleError = (
    error: any,
    defaultMessage: string,
    errorOptions?: {
      severity?: 'error' | 'warn' | 'info'
      life?: number
      logError?: boolean
    }
  ) => {
    const severity = errorOptions?.severity || 'error'
    const life = errorOptions?.life || 5000
    const logError = errorOptions?.logError !== false // Default to true

    // Extract error message from various possible error formats
    const message = getLocalizedErrorMessage(error, t, defaultMessage)

    // Show toast notification if available
    if (toast) {
      const summary = t ? t('error') : 'Error'
      toast.add({
        severity,
        summary,
        detail: message,
        life
      })
    }

    // Log error to console for debugging
    if (logError) {
      console.error('Error:', error)
    }
  }

  /**
   * Handle success messages consistently
   */
  const handleSuccess = (message: string, life: number = 3000) => {
    if (toast) {
      const summary = t ? t('success') : 'Success'
      toast.add({
        severity: 'success',
        summary,
        detail: message,
        life
      })
    }
  }

  /**
   * Handle warning messages consistently
   */
  const handleWarning = (message: string, life: number = 3000) => {
    if (toast) {
      const summary = t ? t('warning') : 'Warning'
      toast.add({
        severity: 'warn',
        summary,
        detail: message,
        life
      })
    }
  }

  /**
   * Handle info messages consistently
   */
  const handleInfo = (message: string, life: number = 3000) => {
    if (toast) {
      const summary = t ? t('info') : 'Info'
      toast.add({
        severity: 'info',
        summary,
        detail: message,
        life
      })
    }
  }

  return {
    handleError,
    handleSuccess,
    handleWarning,
    handleInfo
  }
}
