<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Dialog from 'primevue/dialog'
import Textarea from 'primevue/textarea'
import InputNumber from 'primevue/inputnumber'
import DateTimePicker from '@/components/common/DateTimePicker.vue'
import TimeEntriesFilters from '@/components/time-entries/TimeEntriesFilters.vue'
import TimeEntriesTable from '@/components/time-entries/TimeEntriesTable.vue'
import { useTimeEntries } from '@/composables/useTimeEntries'
import { getLocalizedErrorMessage } from '@/utils/errorLocalization'
import { TimeEntriesService } from '@/api/generated'
import type { TimeEntryResponse, UpdateTimeEntryRequest } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()

const {
  activeEntry,
  displayEntries,
  loading,
  showTimeOff,
  startDateFilter,
  endDateFilter,
  workingHours,
  loadTimeEntries,
  toggleTimeOff,
  deleteTimeEntry
} = useTimeEntries()

const editDialogVisible = ref(false)
const currentTimeEntry = ref<Partial<UpdateTimeEntryRequest>>({})
const timeEntryToEdit = ref<TimeEntryResponse | null>(null)

const openEditDialog = (entry: TimeEntryResponse) => {
  currentTimeEntry.value = {
    clockIn: new Date(entry.clockIn) as any,
    clockOut: entry.clockOut ? new Date(entry.clockOut) as any : undefined,
    breakMinutes: entry.breakMinutes || 0,
    entryType: entry.entryType,
    notes: entry.notes
  }
  timeEntryToEdit.value = entry
  editDialogVisible.value = true
}

const saveTimeEntry = async () => {
  try {
    if (timeEntryToEdit.value?.id) {
      // Convert Date objects back to ISO strings
      const request: UpdateTimeEntryRequest = {
        clockIn: currentTimeEntry.value.clockIn instanceof Date
          ? currentTimeEntry.value.clockIn.toISOString()
          : currentTimeEntry.value.clockIn as string,
        clockOut: currentTimeEntry.value.clockOut instanceof Date
          ? currentTimeEntry.value.clockOut.toISOString()
          : currentTimeEntry.value.clockOut as string | undefined,
        breakMinutes: currentTimeEntry.value.breakMinutes || 0,
        entryType: currentTimeEntry.value.entryType!,
        notes: currentTimeEntry.value.notes
      }

      await TimeEntriesService.updateTimeEntry(
        timeEntryToEdit.value.id,
        request
      )

      toast.add({
        severity: 'success',
        summary: t('success'),
        detail: t('timeEntries.updateSuccess'),
        life: 3000
      })

      editDialogVisible.value = false
      await loadTimeEntries()
    }
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: getLocalizedErrorMessage(error, t, t('timeEntries.updateError')),
      life: 3000
    })
  }
}

const formatDateTime = (dateTime: string | undefined) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleTimeString(t('locale'), {
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  loadTimeEntries()
})
</script>

<template>
  <div class="time-entries-view">
    <h1 class="page-title">{{ t('timeEntries.title') }}</h1>

    <Card class="section-card">
      <template #content>
        <!-- Active Entry Card -->
        <div v-if="activeEntry" class="active-entry-card mb-4 p-3 border-round" style="background: var(--green-50); border: 1px solid var(--green-200)">
          <div class="flex align-items-center gap-3">
            <i class="pi pi-clock text-3xl text-green-600"></i>
            <div class="flex-1">
              <div class="font-semibold text-lg">{{ t('timeEntries.activeSession') }}</div>
              <div class="text-sm">
                {{ t('timeEntries.clockedInAt') }}: {{ formatDateTime(activeEntry.clockIn) }}
              </div>
              <div v-if="activeEntry.notes" class="text-sm mt-1">
                {{ activeEntry.notes }}
              </div>
            </div>
          </div>
        </div>

        <!-- Filters Section -->
        <div class="filters-section mb-4">
          <TimeEntriesFilters
            v-model:start-date="startDateFilter"
            v-model:end-date="endDateFilter"
            @filter="loadTimeEntries"
            :show-time-off="showTimeOff"
            @toggle-time-off="toggleTimeOff"
          />
        </div>

        <!-- Combined Entries Table -->
        <TimeEntriesTable
          :entries="displayEntries"
          :loading="loading"
          :working-hours="workingHours"
          @edit="openEditDialog"
          @delete="deleteTimeEntry"
        />
      </template>
    </Card>

    <!-- Edit Dialog -->
    <Dialog
      v-model:visible="editDialogVisible"
      :header="t('timeEntries.edit')"
      :modal="true"
      :style="{ width: '90vw', maxWidth: '550px' }"
      :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
    >
      <div class="time-entry-form">
        <!-- Type field removed - only WORK supported. -->
        <div class="field">
          <label for="editClockIn">{{ t('timeEntries.clockIn') }}</label>
          <DateTimePicker
            id="editClockIn"
            v-model="currentTimeEntry.clockIn"
            class="w-full"
          />
        </div>
        <div class="field">
          <label for="editClockOut">{{ t('timeEntries.clockOut') }}</label>
          <DateTimePicker
            id="editClockOut"
            v-model="currentTimeEntry.clockOut"
            class="w-full"
          />
        </div>
        <div class="field">
          <label for="editBreakMinutes">{{ t('timeEntries.breakMinutes') }}</label>
          <InputNumber
            id="editBreakMinutes"
            v-model="currentTimeEntry.breakMinutes"
            :min="0"
            :max="480"
            suffix=" min"
            class="w-full"
          />
        </div>
        <div class="field">
          <label for="editNotes">{{ t('timeEntries.notes') }}</label>
          <Textarea
            id="editNotes"
            v-model="currentTimeEntry.notes"
            rows="3"
            class="w-full"
          />
        </div>
      </div>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="editDialogVisible = false" />
        <Button :label="t('save')" severity="primary" @click="saveTimeEntry" />
      </template>
    </Dialog>

  </div>
</template>

<style scoped>
/* Using shared layout styles from layouts.css */
.time-entries-view {
  padding: var(--tt-view-padding);
}
</style>
