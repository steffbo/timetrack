import { ref, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'

export type DeletedItemType = 'time-entry' | 'time-off'

export interface DeletedItem {
  id: string
  type: DeletedItemType
  item: unknown
  message: string
  recreateFn: (item: unknown) => Promise<void>
  reloadFn: () => Promise<void>
  deletedAt: number
}

const MAX_UNDO_ITEMS = 3
const TOAST_GROUP = 'multi-undo-delete'

// Shared state across all instances
const deletedItemsStack: Ref<DeletedItem[]> = ref([])
let toastInstance: ReturnType<typeof useToast> | null = null

/**
 * Composable for unified multi-undo delete functionality
 * Supports stacking multiple deletions (up to 3) with individual undo capability
 * Works for both time entries and PTO deletions in the dashboard
 */
export function useMultiUndoDelete() {
  const { t } = useI18n()
  const toast = useToast()
  
  // Store toast instance for use in undo operations
  toastInstance = toast

  /**
   * Add a deleted item to the stack and show unified toast
   */
  const addDeletedItem = <T>(
    type: DeletedItemType,
    item: T,
    message: string,
    recreateFn: (item: T) => Promise<void>,
    reloadFn: () => Promise<void>
  ) => {
    const deletedItem: DeletedItem = {
      id: `${type}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type,
      item: item as unknown,
      message,
      recreateFn: recreateFn as (item: unknown) => Promise<void>,
      reloadFn,
      deletedAt: Date.now()
    }

    // Add to stack, keeping max 3 items (FIFO - oldest items get removed)
    deletedItemsStack.value = [deletedItem, ...deletedItemsStack.value].slice(0, MAX_UNDO_ITEMS)

    // Refresh the toast
    refreshToast()
  }

  /**
   * Delete an item and add it to the undo stack
   */
  const deleteWithUndo = async <T extends { id: number | string }>(
    type: DeletedItemType,
    item: T,
    deleteFn: (id: number | string) => Promise<void>,
    reloadFn: () => Promise<void>,
    getMessage: (item: T) => string,
    recreateFn: (item: T) => Promise<void>
  ) => {
    try {
      // Delete via API
      await deleteFn(item.id)

      // Reload data
      await reloadFn()

      // Add to undo stack
      addDeletedItem(type, { ...item }, getMessage(item), recreateFn, reloadFn)
    } catch (error: any) {
      toast.add({
        severity: 'error',
        summary: t('error'),
        detail: error?.body?.message || t('deleteError'),
        life: 5000
      })
    }
  }

  /**
   * Undo a specific deletion by its ID
   */
  const undoDelete = async (itemId: string) => {
    const itemIndex = deletedItemsStack.value.findIndex(i => i.id === itemId)
    if (itemIndex === -1) return

    const deletedItem = deletedItemsStack.value[itemIndex]
    if (!deletedItem) return

    try {
      // Remove from stack first to prevent double-undo
      deletedItemsStack.value = deletedItemsStack.value.filter(i => i.id !== itemId)

      // Refresh toast immediately
      refreshToast()

      // Re-create the item via API
      await deletedItem.recreateFn(deletedItem.item)

      // Reload data
      await deletedItem.reloadFn()

      // No success toast - the item disappearing from the undo list is confirmation enough
    } catch (error: any) {
      // Re-add to stack on error
      deletedItemsStack.value = [deletedItem, ...deletedItemsStack.value.filter(Boolean)].slice(0, MAX_UNDO_ITEMS)
      refreshToast()

      toastInstance?.add({
        severity: 'error',
        summary: t('error'),
        detail: error?.body?.message || t('undoError'),
        life: 5000
      })
    }
  }

  /**
   * Dismiss a specific item from the stack without undoing
   */
  const dismissItem = (itemId: string) => {
    deletedItemsStack.value = deletedItemsStack.value.filter(i => i.id !== itemId)
    refreshToast()
  }

  /**
   * Clear all items from the stack
   */
  const clearAll = () => {
    deletedItemsStack.value = []
    toastInstance?.removeGroup(TOAST_GROUP)
  }

  /**
   * Refresh the toast to show current stack state
   */
  const refreshToast = () => {
    // Remove existing toast
    toastInstance?.removeGroup(TOAST_GROUP)

    // Show new toast if there are items
    if (deletedItemsStack.value.length > 0) {
      toastInstance?.add({
        severity: 'info',
        summary: '', // Summary will be rendered by custom template
        life: 0, // Stays until manually dismissed
        closable: false, // We handle close ourselves
        group: TOAST_GROUP
      })
    }
  }

  return {
    deletedItemsStack: computed(() => deletedItemsStack.value),
    toastGroup: TOAST_GROUP,
    deleteWithUndo,
    undoDelete,
    dismissItem,
    clearAll
  }
}
