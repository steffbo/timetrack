<template>
  <Toast :position="position" :group="group">
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
  position?: string
}

const props = withDefaults(defineProps<Props>(), {
  position: 'top-right'
})
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
  background: linear-gradient(135deg, var(--tt-lime-from) 0%, var(--tt-lime-to) 100%);
  border: 2px solid white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  padding: 0.5rem 0.75rem;
  height: 2.25rem;
  min-width: 2.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: var(--tt-transition);
  flex-shrink: 0;
}

.undo-button:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
  transform: translateY(-2px) scale(1.05);
  border-color: rgba(255, 255, 255, 0.9);
}

.undo-button:active {
  transform: translateY(0) scale(1);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
}

.undo-button i {
  color: white;
  font-size: 1rem;
  line-height: 1;
  font-weight: 700;
}

/* Override PrimeVue Toast message padding for better fit */
:deep(.p-toast-message-content) {
  padding: 0.75rem 1rem;
}

:deep(.p-toast-message) {
  padding: 0;
}
</style>
