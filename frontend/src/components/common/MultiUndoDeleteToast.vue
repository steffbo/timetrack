<template>
  <Toast :position="toastPosition" :group="toastGroup">
    <template #message>
      <div class="multi-undo-toast">
        <button
          class="close-all-button"
          :aria-label="t('close')"
          @click="handleCloseAll"
        >
          <i class="pi pi-times"></i>
        </button>
        <div class="deleted-items-list">
          <div
            v-for="item in deletedItemsStack"
            :key="item.id"
            class="deleted-item"
          >
            <span class="item-icon">{{ getTypeIcon(item.type) }}</span>
            <span class="item-message" v-html="formatMessage(item.message)"></span>
            <button
              class="undo-button"
              :aria-label="t('timeEntries.undo')"
              v-tooltip.left="t('timeEntries.undo')"
              @click="handleUndo(item.id)"
            >
              <i class="pi pi-undo"></i>
            </button>
          </div>
        </div>
      </div>
    </template>
  </Toast>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Toast from 'primevue/toast'
import { useMultiUndoDelete, type DeletedItemType } from '@/composables/useMultiUndoDelete'

interface Props {
  position?: 'top-right' | 'top-left' | 'top-center' | 'bottom-left' | 'bottom-center' | 'bottom-right' | 'center'
}

const props = withDefaults(defineProps<Props>(), {
  position: 'top-right'
})

const { t } = useI18n()
const { deletedItemsStack, toastGroup, undoDelete, clearAll } = useMultiUndoDelete()

const toastPosition = props.position

const handleUndo = async (itemId: string) => {
  await undoDelete(itemId)
}

const handleCloseAll = () => {
  clearAll()
}

// Get icon/emoji based on deletion type
const getTypeIcon = (type: DeletedItemType): string => {
  switch (type) {
    case 'time-entry':
      return '⏱️'
    case 'time-off':
      return '�️'
    default:
      return '🗑️'
  }
}

// Format message to keep date ranges on the same line
const formatMessage = (message: string): string => {
  // Match date ranges in format: (DD.MM.YYYY - DD.MM.YYYY) or (DD.MM.YYYY-DD.MM.YYYY)
  const dateRangePattern = /\((\d{1,2}\.\d{1,2}\.\d{4})\s*-\s*(\d{1,2}\.\d{1,2}\.\d{4})\)/g
  return message.replace(dateRangePattern, '<span class="date-range">($1 - $2)</span>')
}
</script>

<style scoped>
.multi-undo-toast {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
  min-width: 280px;
  position: relative;
  padding-top: 0.25rem;
}

.close-all-button {
  position: absolute;
  top: -0.5rem;
  right: -0.5rem;
  background: rgba(0, 0, 0, 0.3);
  border: 2px solid white;
  border-radius: 50%;
  width: 1.5rem;
  height: 1.5rem;
  padding: 0;
  cursor: pointer;
  color: white;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.close-all-button:hover {
  background: rgba(0, 0, 0, 0.5);
  transform: scale(1.1);
}

.close-all-button i {
  font-size: 0.75rem;
}

.deleted-items-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding-right: 1rem;
}

.deleted-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.625rem;
  padding: 0.5rem 0.625rem;
  background: rgba(255, 255, 255, 0.15);
  border-left: 3px solid #ef4444;
  border-radius: 0 6px 6px 0;
}

.item-icon {
  font-size: 1rem;
  line-height: 1;
  flex-shrink: 0;
}

.item-message {
  flex: 1;
  font-size: 0.8125rem;
  line-height: 1.4;
  color: white;
}

.item-message :deep(.date-range) {
  white-space: nowrap;
}

.undo-button {
  background: #3b82f6;
  border: none;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.4);
  border-radius: 6px;
  padding: 0.375rem 0.5rem;
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
  background: #2563eb;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.5);
  transform: translateY(-1px) scale(1.05);
}

.undo-button:active {
  transform: translateY(0) scale(1);
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.3);
}

.undo-button i {
  color: white;
  font-size: 0.75rem;
  line-height: 1;
  font-weight: 700;
}
</style>
