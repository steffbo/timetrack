import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import type { ToastMessageOptions } from 'primevue/toast'
import { getLocalizedErrorMessage } from '@/utils/errorLocalization'

/**
 * Composable for undo delete functionality
 * Provides a reusable pattern for immediate deletion with undo capability
 */
type UndoToastData = {
  label: string
  onAction: () => void | Promise<void>
}

type UndoToastMessage = ToastMessageOptions & {
  data?: UndoToastData
}

type UndoToastOptions = {
  undoLabel?: string
  undoSeverity?: ToastMessageOptions['severity']
  undoLife?: number
  undoClosable?: boolean
  showUndoSuccessToast?: boolean
  undoSuccessToast?: Pick<ToastMessageOptions, 'severity' | 'summary' | 'detail' | 'life'>
  onUndoSuccess?: () => void | Promise<void>
}

export function useUndoDelete() {
  const { t } = useI18n()
  const toast = useToast()

  const showUndoToast = (
    message: string,
    undoAction: () => Promise<void>,
    options?: UndoToastOptions
  ) => {
    const actionLabel = options?.undoLabel ?? t('timeEntries.undo')
    const undoSeverity = options?.undoSeverity ?? 'info'
    const undoLife = options?.undoLife ?? 0
    const undoClosable = options?.undoClosable ?? true

    const toastMessage: UndoToastMessage = {
      severity: undoSeverity,
      summary: message,
      life: undoLife,
      closable: undoClosable
    }

    let isUndoing = false

    toastMessage.data = {
      label: actionLabel,
      onAction: async () => {
        if (isUndoing) return
        isUndoing = true
        try {
          await undoAction()
          toast.remove(toastMessage)
          if (options?.onUndoSuccess) {
            await options.onUndoSuccess()
          }
          if (options?.showUndoSuccessToast) {
            const successToast = options?.undoSuccessToast ?? {
              severity: 'success',
              summary: t('success'),
              detail: t('undoSuccess'),
              life: 3000
            }
            toast.add(successToast)
          }
        } catch (error: any) {
          toast.add({
            severity: 'error',
            summary: t('error'),
            detail: getLocalizedErrorMessage(error, t, t('undoError')),
            life: 5000
          })
        } finally {
          isUndoing = false
        }
      }
    }

    toast.add(toastMessage)
  }

  /**
   * Delete an item immediately and show undo toast
   * @param item The item to delete
   * @param deleteFn Function that performs the actual deletion
   * @param reloadFn Function to reload data after deletion
   * @param getMessage Function that returns the message to show in toast
   * @param recreateFn Function that recreates the item
   */
  const deleteWithUndo = async <T extends { id: number | string }>(
    item: T,
    deleteFn: (id: number | string) => Promise<void>,
    reloadFn: () => Promise<void>,
    getMessage: (item: T) => string,
    recreateFn: (item: T) => Promise<void>,
    options?: UndoToastOptions
  ) => {
    try {
      const deletedSnapshot = { ...item } as T

      // Delete immediately via API
      await deleteFn(item.id)

      // Reload data
      await reloadFn()

      showUndoToast(
        getMessage(item),
        async () => {
          await recreateFn(deletedSnapshot)
          await reloadFn()
        },
        options
      )
    } catch (error: any) {
      toast.add({
        severity: 'error',
        summary: t('error'),
        detail: getLocalizedErrorMessage(error, t, t('deleteError')),
        life: 5000
      })
    }
  }

  return {
    deleteWithUndo
  }
}
