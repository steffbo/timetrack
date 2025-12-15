import { ref, computed } from 'vue'
import { RecurringOffDayWarningsService } from '@/api/generated'
import type { RecurringOffDayConflictWarningResponse } from '@/api/generated'

const warnings = ref<RecurringOffDayConflictWarningResponse[]>([])
const loading = ref(false)

export function useConflictWarnings() {
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
      console.error('Error loading conflict warnings:', error)
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
      console.error('Error acknowledging warning:', error)
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
