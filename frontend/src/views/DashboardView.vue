<template>
  <div class="dashboard">
    <div class="dashboard-layout">
      <!-- Calendar -->
      <div class="calendar-section">
        <MonthlyCalendar
          :current-month="currentMonth"
          :daily-summaries="dailySummaries"
          :working-hours="workingHours"
          :half-day-holidays-enabled="currentUser?.halfDayHolidaysEnabled || false"
          @month-change="handleMonthChange"
          @quick-entry="handleQuickEntryFromCalendar"
          @manual-entry="handleManualEntryFromCalendar"
          @add-time-off="handleTimeOffFromCalendar"
          @edit-all="handleEditAllFromCalendar"
          @quick-delete="handleQuickDeleteFromCalendar"
        />
      </div>

      <!-- Today Card (moves to top on mobile via CSS order) -->
      <div class="today-card-wrapper">
        <TodayStatusCard
          :today-summary="todaySummary"
          :working-hours="workingHours"
          :active-entry="activeEntry"
          :has-today-working-hours="hasTodayWorkingHours"
          @clock-in="clockInNow"
          @clock-out="clockOutNow"
          @cancel-entry="cancelEntry"
          @quick-entry="createQuickWorkEntry"
          @quick-clock-out="quickClockOutNow"
          @create-exemption="openExemptionDialog"
        />
      </div>

      <!-- Tomorrow Preview Card (moves to top on mobile, after Today) -->
      <div class="tomorrow-card-wrapper">
        <TomorrowPreviewCard
          :tomorrow-summary="tomorrowSummary"
          :working-hours="workingHours"
        />
      </div>

      <!-- Other Info Cards -->
      <div class="other-cards-wrapper">
        <!-- Weekly Overview (always shows current week, independent of calendar month) -->
        <WeeklyOverviewCard
          :daily-summaries="currentWeekSummaries"
          :working-hours="workingHours"
        />
        
        <!-- Monthly Overview with Overtime -->
        <MonthlyOverviewCard
          :current-month="currentMonth"
          :daily-summaries="dailySummaries"
          :working-hours="workingHours"
          :overtime-selected-month="overtimeSelectedMonth"
        />
        
        <!-- Vacation Balance -->
        <VacationBalanceCard />
      </div>
    </div>

    <!-- Time Off Form Dialog -->
    <TimeOffQuickForm
      v-model:visible="showTimeOffDialog"
      :selected-date="selectedDate || ''"
      @saved="handleFormSaved"
    />

    <!-- Edit Dialog (combines entries and time-off) -->
    <DayEntriesEditor
      v-model:visible="showEditDialog"
      :selected-date="selectedDate || ''"
      :entries="selectedEntries"
      :time-off-entries="selectedTimeOffEntries"
      @saved="handleFormSaved"
    />

    <!-- Manual Entry Dialog -->
    <Dialog
      v-model:visible="showManualEntryDialog"
      :header="t('timeEntries.manualEntry')"
      :modal="true"
      :style="{ width: '90vw', maxWidth: '550px' }"
      :breakpoints="{ '960px': '75vw', '640px': '80vw', '480px': '90vw' }"
    >
      <div class="manual-entry-form">
        <div class="datetime-fields">
          <div class="field">
            <label for="manualEntryDate">{{ t('timeEntries.day') }}</label>
            <DatePicker
              id="manualEntryDate"
              v-model="manualEntryDate"
              show-icon
              @date-select="onManualEntryDateChange"
            />
          </div>

          <div class="time-fields">
            <div class="field">
              <label for="manualStartTime">{{ t('timeEntries.startTime') }}</label>
              <DatePicker
                id="manualStartTime"
                v-model="manualEntryStartTime"
                time-only
                :manual-input="true"
              />
            </div>

            <div class="field">
              <label for="manualEndTime">{{ t('timeEntries.endTime') }}</label>
              <DatePicker
                id="manualEndTime"
                v-model="manualEntryEndTime"
                time-only
                :manual-input="true"
              />
            </div>
          </div>
        </div>

        <div class="field">
          <label for="manualBreakMinutes">{{ t('timeEntries.breakMinutes') }}</label>
          <InputNumber
            id="manualBreakMinutes"
            v-model="manualEntryBreakMinutes"
            :min="0"
            :max="480"
            suffix=" min"
            class="manual-entry-break"
          />
        </div>
        <div class="field">
          <label for="manualNotes">{{ t('timeEntries.notes') }}</label>
          <Textarea
            id="manualNotes"
            v-model="manualEntryNotes"
            rows="2"
            class="w-full"
          />
        </div>
      </div>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="showManualEntryDialog = false" />
        <Button
          :label="t('save')"
          severity="primary"
          @click="createManualEntry"
        />
      </template>
    </Dialog>

    <!-- Exemption Dialog -->
    <Dialog
      v-model:visible="showExemptionDialog"
      :header="t('dashboard.createExemption')"
      :modal="true"
      :style="{ width: '90vw', maxWidth: '400px' }"
    >
      <div class="exemption-form">
        <p class="exemption-info">
          {{ t('recurringOffDays.exemptionInfo', { date: todayDateString, description: todayRecurringOffDay?.description || '' }) }}
        </p>
        <div class="field">
          <label for="exemptionReason">{{ t('recurringOffDays.exemptionReason') }}</label>
          <InputText
            id="exemptionReason"
            v-model="exemptionReason"
            :placeholder="t('recurringOffDays.exemptionReasonPlaceholder')"
            class="w-full"
          />
        </div>
      </div>
      <template #footer>
        <Button :label="t('cancel')" severity="secondary" @click="showExemptionDialog = false" />
        <Button
          :label="t('dashboard.createExemption')"
          severity="primary"
          @click="createExemptionForToday"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import { useAuth } from '@/composables/useAuth'
import { useDashboard } from '@/composables/useDashboard'
import MonthlyCalendar from '@/components/dashboard/MonthlyCalendar.vue'
import TimeOffQuickForm from '@/components/dashboard/TimeOffQuickForm.vue'
import DayEntriesEditor from '@/components/dashboard/DayEntriesEditor.vue'
import TodayStatusCard from '@/components/dashboard/TodayStatusCard.vue'
import TomorrowPreviewCard from '@/components/dashboard/TomorrowPreviewCard.vue'
import WeeklyOverviewCard from '@/components/dashboard/WeeklyOverviewCard.vue'
import MonthlyOverviewCard from '@/components/dashboard/MonthlyOverviewCard.vue'
import VacationBalanceCard from '@/components/dashboard/VacationBalanceCard.vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import DatePicker from '@/components/common/DatePicker.vue'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { RecurringOffDayExemptionsService } from '@/api/generated'
import type { CreateRecurringOffDayExemptionRequest } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()
const { currentUser } = useAuth()

// Use dashboard composable for all dashboard logic
const {
  currentMonth,
  dailySummaries,
  currentWeekSummaries,
  workingHours,
  activeEntry,
  overtimeSelectedMonth,
  todaySummary,
  tomorrowSummary,
  selectedDate,
  selectedEntries,
  selectedTimeOffEntries,
  showTimeOffDialog,
  showEditDialog,
  showManualEntryDialog,
  manualEntryDate,
  manualEntryStartTime,
  manualEntryEndTime,
  manualEntryBreakMinutes,
  manualEntryNotes,
  hasTodayWorkingHours,
  loadInitialData,
  handleMonthChange,
  loadActiveEntry,
  loadNextVacation,
  calculateOvertime,
  clockInNow,
  clockOutNow,
  quickClockOutNow,
  cancelEntry,
  createQuickWorkEntry,
  handleQuickEntryFromCalendar,
  handleManualEntryFromCalendar,
  handleTimeOffFromCalendar,
  handleEditAllFromCalendar,
  handleQuickDeleteFromCalendar,
  handleFormSaved,
  onManualEntryDateChange,
  createManualEntry,
  loadCurrentWeekSummaries,
  invalidateCacheAndReload
} = useDashboard()

// Exemption dialog state
const showExemptionDialog = ref(false)
const exemptionReason = ref('')

// Get today's recurring off-day (if any) for creating exemption
const todayRecurringOffDay = computed(() => {
  return todaySummary.value?.recurringOffDays?.[0] || null
})

// Format today's date as YYYY-MM-DD
const todayDateString = computed(() => {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
})

// Handle create exemption button click
const openExemptionDialog = () => {
  exemptionReason.value = ''
  showExemptionDialog.value = true
}

// Create exemption for today
const createExemptionForToday = async () => {
  if (!todayRecurringOffDay.value?.id) return
  
  try {
    const request: CreateRecurringOffDayExemptionRequest = {
      exemptionDate: todayDateString.value,
      reason: exemptionReason.value || undefined
    }
    
    await RecurringOffDayExemptionsService.createExemption(
      todayRecurringOffDay.value.id,
      request
    )
    
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('recurringOffDays.exemptionCreated'),
      life: 3000
    })
    
    showExemptionDialog.value = false
    
    // Reload daily summaries and current week to reflect the change
    await invalidateCacheAndReload()
    await loadCurrentWeekSummaries()
  } catch (error: any) {
    const errorMessage = error?.body?.message || t('recurringOffDays.exemptionCreateError')
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: errorMessage,
      life: 3000
    })
  }
}


onMounted(async () => {
  // Load initial data (includes daily summaries, working hours, and public holidays)
  await loadInitialData()

  // Load other data in parallel
  await Promise.all([
    loadActiveEntry(),
    loadNextVacation(),
    calculateOvertime()
  ])
})
</script>

<style scoped>
.dashboard {
  padding: var(--tt-view-padding);
  max-width: 100%;
  overflow-x: hidden;
  min-height: 0;
}

/* Main Layout: Calendar Left, Sidebar Right */
.dashboard-layout {
  display: grid;
  grid-template-columns: 1fr 400px;
  grid-template-rows: auto auto 1fr;
  grid-template-areas:
    "calendar today"
    "calendar tomorrow"
    "calendar other";
  gap: var(--tt-spacing-xl);
  row-gap: var(--tt-spacing-md);
  align-items: start;
  max-width: 100%;
}

/* Calendar Section */
.calendar-section {
  grid-area: calendar;
  min-height: 600px;
  max-width: 100%;
  overflow-x: auto;
}

/* Today Card Wrapper */
.today-card-wrapper {
  grid-area: today;
}

/* Tomorrow Card Wrapper */
.tomorrow-card-wrapper {
  grid-area: tomorrow;
}

/* Other Cards Wrapper */
.other-cards-wrapper {
  grid-area: other;
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-md);
}

/* Responsive layout */
@media (max-width: 1200px) {
  .dashboard-layout {
    grid-template-columns: 1fr 350px;
  }
}

@media (max-width: 1024px) {
  .dashboard-layout {
    grid-template-columns: 1fr 1fr;
    grid-template-areas:
      "today tomorrow"
      "calendar calendar"
      "other other";
    gap: var(--tt-spacing-lg);
  }

  .calendar-section {
    min-height: auto;
  }
}

@media (max-width: 768px) {
  .dashboard {
    padding: var(--tt-view-padding-mobile);
  }

  .dashboard-layout {
    gap: var(--tt-spacing-md);
  }
}

@media (max-width: 560px) {
  .dashboard-layout {
    grid-template-columns: 1fr;
    grid-template-areas:
      "today"
      "tomorrow"
      "calendar"
      "other";
  }
}

@media (max-width: 480px) {
  .dashboard {
    padding: 0.5rem;
  }

  .dashboard-layout {
    gap: var(--tt-spacing-sm);
  }
}

/* Manual entry form - date and time fields alignment using CSS Grid */
.manual-entry-form .datetime-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--tt-spacing-md);  /* 16px - aligned to grid */
  max-width: 300px;
}

.manual-entry-form .datetime-fields > .field:first-child {
  grid-column: 1 / -1;
}

.manual-entry-form .datetime-fields .time-fields {
  display: contents;
}

.manual-entry-form .datetime-fields :deep(.p-datepicker) {
  width: 100%;
}

/* Exemption form */
.exemption-form {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-md);
}

.exemption-form .exemption-info {
  margin: 0;
  color: var(--p-text-muted-color);
  font-size: 0.9rem;
}
</style>
