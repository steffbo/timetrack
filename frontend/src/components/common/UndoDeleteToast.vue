<template>
  <Toast position="bottom-center" :group="group">
    <template #message="slotProps">
      <div class="undo-toast-content">
        <span class="undo-toast-message" v-html="formatMessage(slotProps.message.summary)"></span>
        <button
          class="undo-button"
          :aria-label="t('timeEntries.undo')"
          v-tooltip="t('timeEntries.undo')"
          @click="handleUndo"
        >
          <i class="pi pi-undo"></i>
        </button>
      </div>
    </template>
  </Toast>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Toast from 'primevue/toast'

interface Props {
  group: string
  onUndo: () => void | Promise<void>
}

const props = defineProps<Props>()
const { t } = useI18n()

const handleUndo = async () => {
  await props.onUndo()
}

// Format message to keep date ranges on the same line
const formatMessage = (message: string): string => {
  // Match date ranges in format: (DD.MM.YYYY - DD.MM.YYYY) or (DD.MM.YYYY-DD.MM.YYYY)
  // Also handles various date formats
  const dateRangePattern = /\((\d{1,2}\.\d{1,2}\.\d{4})\s*-\s*(\d{1,2}\.\d{1,2}\.\d{4})\)/g
  return message.replace(dateRangePattern, '<span class="date-range">($1 - $2)</span>')
}
</script>

<style scoped>
.undo-toast-content {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
}

.undo-toast-message {
  flex: 1;
  font-size: 0.875rem;
  line-height: 1.5;
  color: inherit;
}

.undo-toast-message :deep(.date-range) {
  white-space: nowrap;
}

.undo-button {
  background: linear-gradient(135deg, var(--tt-green-from) 0%, var(--tt-green-to) 100%);
  border: none;
  box-shadow: var(--tt-shadow-sm);
  border-radius: 6px;
  padding: 0.25rem 0.5rem;
  height: 1.75rem;
  min-width: 1.75rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: var(--tt-transition);
  flex-shrink: 0;
}

.undo-button:hover {
  box-shadow: var(--tt-shadow-hover);
  transform: translateY(-1px);
}

.undo-button:active {
  transform: translateY(0);
  box-shadow: var(--tt-shadow-sm);
}

.undo-button i {
  color: white;
  font-size: 0.75rem;
  line-height: 1;
}

/* Override PrimeVue Toast message padding for better fit */
:deep(.p-toast-message-content) {
  padding: 0.75rem 1rem;
}

:deep(.p-toast-message) {
  padding: 0;
}
</style>
