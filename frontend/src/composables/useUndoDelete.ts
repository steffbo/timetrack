import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'

/**
 * Composable for undo delete functionality
 * Provides a reusable pattern for immediate deletion with undo capability
 */
export function useUndoDelete<T extends { id: number | string }>(groupName = 'delete-undo') {
  const { t } = useI18n()
  const toast = useToast()

  const lastDeletedItem = ref<T | null>(null)
  const undoGroup = groupName

  /**
   * Delete an item immediately and show undo toast
   * @param item The item to delete
   * @param deleteFn Function that performs the actual deletion
   * @param reloadFn Function to reload data after deletion
   * @param getMessage Function that returns the message to show in toast
   */
  const deleteWithUndo = async (
    item: T,
    deleteFn: (id: number | string) => Promise<void>,
    reloadFn: () => Promise<void>,
    getMessage: (item: T) => string
  ) => {
    try {
      // Store the deleted item for potential undo
      lastDeletedItem.value = { ...item } as T

      // Delete immediately via API
      await deleteFn(item.id)

      // Reload data
      await reloadFn()

      // Close any existing delete toast
      toast.removeGroup(undoGroup)

      // Show compact toast with undo option (stays until dismissed)
      toast.add({
        severity: 'info',
        summary: getMessage(item),
        life: 0, // Stays until manually dismissed
        closable: true,
        group: undoGroup
      })
    } catch (error: any) {
      toast.add({
        severity: 'error',
        summary: t('error'),
        detail: error?.body?.message || t('deleteError'),
        life: 5000
      })
      // Clear stored item on error
      lastDeletedItem.value = null
    }
  }

  /**
   * Undo the last deletion
   * @param recreateFn Function that recreates the item
   * @param reloadFn Function to reload data after undo
   */
  const undoDelete = async (
    recreateFn: (item: T) => Promise<void>,
    reloadFn: () => Promise<void>
  ) => {
    if (!lastDeletedItem.value) return

    try {
      // Close the delete toast
      toast.removeGroup(undoGroup)

      // Re-create the item via API
      await recreateFn(lastDeletedItem.value)

      // Clear last deleted
      lastDeletedItem.value = null

      // Reload data
      await reloadFn()

      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('undoSuccess'),
        life: 3000
      })
    } catch (error: any) {
      toast.add({
        severity: 'error',
        summary: t('error'),
        detail: error?.body?.message || t('undoError'),
        life: 5000
      })
    }
  }

  /**
   * Clear the stored deleted item (e.g., when user dismisses toast)
   */
  const clearUndo = () => {
    lastDeletedItem.value = null
    toast.removeGroup(undoGroup)
  }

  return {
    lastDeletedItem,
    deleteWithUndo,
    undoDelete,
    clearUndo,
    undoGroup
  }
}
