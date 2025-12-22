<template>
  <Toast :position="position">
    <template #closeicon>
      <i class="pi pi-times"></i>
    </template>
    <template #message="slotProps">
      <div class="undo-toast-content">
        <i class="undo-toast-icon" :class="getIconClass(slotProps.message.severity)"></i>
        <div class="undo-toast-text">
          <span class="undo-toast-summary">
            <template
              v-for="(part, index) in formatMessageParts(slotProps.message.summary || '')"
              :key="index"
            >
              <span v-if="part.isDateRange" class="date-range">{{ part.text }}</span>
              <span v-else>{{ part.text }}</span>
            </template>
          </span>
          <div v-if="getDetailText(slotProps.message.detail)" class="undo-toast-detail">
            {{ getDetailText(slotProps.message.detail) }}
          </div>
        </div>
        <button
          v-if="getActionData(slotProps.message)?.onAction"
          class="undo-toast-action"
          :aria-label="getActionData(slotProps.message)?.label || t('timeEntries.undo')"
          v-tooltip="{ value: getActionData(slotProps.message)?.label || t('timeEntries.undo'), pt: { text: { style: 'white-space: nowrap' } } }"
          @click="getActionData(slotProps.message)?.onAction?.()"
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
import type { ToastMessageOptions } from 'primevue/toast'

type UndoToastData = {
  label?: string
  onAction?: () => void | Promise<void>
}

interface Props {
  position?: 'top-right' | 'top-left' | 'top-center' | 'bottom-left' | 'bottom-center' | 'bottom-right' | 'center'
}

const { position } = withDefaults(defineProps<Props>(), {
  position: 'top-right'
})

const { t } = useI18n()

const getActionData = (message: ToastMessageOptions & { data?: UndoToastData }): UndoToastData | null => {
  return (message as { data?: UndoToastData })?.data ?? null
}

const getDetailText = (detail: unknown): string | null => {
  return typeof detail === 'string' ? detail : null
}

const getIconClass = (severity?: ToastMessageOptions['severity']): string => {
  switch (severity) {
    case 'success':
      return 'pi pi-check'
    case 'warn':
      return 'pi pi-exclamation-triangle'
    case 'error':
      return 'pi pi-times-circle'
    default:
      return 'pi pi-info-circle'
  }
}

type MessagePart = {
  text: string
  isDateRange: boolean
}

// Format message to keep date ranges on the same line
const formatMessageParts = (message: string): MessagePart[] => {
  // Match date ranges in format: (DD.MM.YYYY - DD.MM.YYYY) or (DD.MM.YYYY-DD.MM.YYYY)
  const dateRangePattern = /\((\d{1,2}\.\d{1,2}\.\d{4})\s*-\s*(\d{1,2}\.\d{1,2}\.\d{4})\)/g
  const parts: MessagePart[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = dateRangePattern.exec(message)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ text: message.slice(lastIndex, match.index), isDateRange: false })
    }

    parts.push({ text: `(${match[1]} - ${match[2]})`, isDateRange: true })
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < message.length) {
    parts.push({ text: message.slice(lastIndex), isDateRange: false })
  }

  return parts
}
</script>

<style scoped>
.undo-toast-content {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
}

.undo-toast-icon {
  font-size: 1.1rem;
  line-height: 1;
  color: currentColor;
  flex-shrink: 0;
}

.undo-toast-text {
  flex: 1;
  min-width: 0;
}

.undo-toast-summary {
  display: block;
  font-size: 0.9rem;
  line-height: 1.4;
  font-weight: 600;
  color: inherit;
}

.undo-toast-detail {
  font-size: 0.85rem;
  line-height: 1.4;
  color: inherit;
  opacity: 0.85;
}

.undo-toast-summary :deep(.date-range) {
  white-space: nowrap;
}

.undo-toast-action {
  background: transparent;
  border: 1px solid var(--p-surface-border);
  border-radius: 6px;
  padding: 0.375rem 0.5rem;
  height: 2rem;
  min-width: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: inherit;
  transition: var(--tt-transition);
  flex-shrink: 0;
}

.undo-toast-action:hover {
  background: var(--p-surface-100);
}

.undo-toast-action i {
  font-size: 0.85rem;
  line-height: 1;
}

/* Align with default toast padding */
:deep(.p-toast-message-content) {
  padding: 0.75rem 1rem;
}

:deep(.p-toast-message) {
  padding: 0;
}
</style>
