import { ref, computed } from 'vue'
import { RecurringOffDayWarningsService } from '@/api/generated'
import type { RecurringOffDayConflictWarningResponse } from '@/api/generated'
import { useErrorHandler } from './useErrorHandler'

const warnings = ref<RecurringOffDayConflictWarningResponse[]>([])
const loading = ref(false)

export function useConflictWarnings() {
  const { handleError } = useErrorHandler()

  const unacknowledgedWarnings = computed(() =>
    warnings.value.filter(w => !w.acknowledged)
  )

  const unacknowledgedCount = computed(() =>
    unacknowledgedWarnings.value.length
  )

  const loadWarnings = async (unacknowledgedOnly = false) => {
    loading.value = true
    try {
      warnings.value = await RecurringOffDayWarningsService.getConflictWarnings(unacknowledgedOnly)
    } catch (error) {
      handleError(error, 'Failed to load conflict warnings', { logError: true })
      warnings.value = []
    } finally {
      loading.value = false
    }
  }

  const acknowledgeWarning = async (warningId: number) => {
    try {
      const updatedWarning = await RecurringOffDayWarningsService.acknowledgeWarning(warningId)

      // Update local state
      const index = warnings.value.findIndex(w => w.id === warningId)
      if (index !== -1) {
        warnings.value[index] = updatedWarning
      }

      return updatedWarning
    } catch (error) {
      handleError(error, 'Failed to acknowledge warning', { logError: true })
      throw error
    }
  }

  return {
    warnings,
    unacknowledgedWarnings,
    unacknowledgedCount,
    loading,
    loadWarnings,
    acknowledgeWarning
  }
}
