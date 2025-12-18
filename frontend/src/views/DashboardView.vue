<template>
  <div class="dashboard">
    <div class="dashboard-layout">
      <!-- Left Side: Calendar -->
      <div class="calendar-section">
        <MonthlyCalendar
          :current-month="currentMonth"
          :daily-summaries="dailySummaries"
          :working-hours="workingHours"
          :half-day-holidays-enabled="currentUser?.halfDayHolidaysEnabled || false"
          @month-change="handleMonthChange"
          @quick-entry="handleQuickEntryFromCalendar"
          @add-time-off="handleTimeOffFromCalendar"
          @edit-all="handleEditAllFromCalendar"
        />
      </div>

      <!-- Right Side: Actions & Stats -->
      <div class="sidebar-section">
        <!-- Quick Actions & Overview Card -->
        <div class="actions-stats-card">
          <div class="action-cards">
            <!-- Clock In/Out Card -->
            <div
              v-if="!activeEntry"
              class="action-card action-clock-in"
              :class="{ disabled: !hasTodayWorkingHours }"
              @click="hasTodayWorkingHours && clockInNow()"
            >
              <i class="pi pi-play-circle action-icon"></i>
              <div class="action-label">{{ t('dashboard.clockInNow') }}</div>
              <small v-if="!hasTodayWorkingHours" class="action-hint">
                {{ t('dashboard.noWorkingHoursToday') }}
              </small>
            </div>
            <div v-else class="active-entry-cards">
              <div class="action-card action-clock-out" @click="clockOutNow">
                <i class="pi pi-stop-circle action-icon"></i>
                <div class="action-label">{{ t('dashboard.clockOutNow') }}</div>
              </div>
              <div class="action-card action-cancel" @click="cancelEntry">
                <i class="pi pi-times action-icon"></i>
                <div class="action-label">{{ t('dashboard.cancelEntry') }}</div>
              </div>
            </div>

            <!-- Quick Work Entry Card -->
            <div
              class="action-card action-quick-entry"
              :class="{ disabled: !hasTodayWorkingHours }"
              @click="hasTodayWorkingHours && createQuickWorkEntry()"
            >
              <i class="pi pi-bolt action-icon"></i>
              <div class="action-label">{{ t('dashboard.quickWorkEntry') }}</div>
              <small v-if="!hasTodayWorkingHours" class="action-hint">
                {{ t('dashboard.noWorkingHoursToday') }}
              </small>
            </div>
          </div>

          <!-- Statistics -->
          <div class="stats-grid">
            <div class="stat-card stat-vacation">
              <div class="stat-label">{{ t('dashboard.nextVacation') }}</div>
              <div class="stat-value">{{ nextVacationText }}</div>
            </div>
            <div class="stat-card stat-current">
              <div class="stat-label">{{ t('dashboard.overtimeSelectedMonth') }}</div>
              <div class="stat-value">{{ formatOvertime(overtimeSelectedMonth) }}</div>
            </div>
          </div>
        </div>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/composables/useAuth'
import { useDashboard } from '@/composables/useDashboard'
import MonthlyCalendar from '@/components/dashboard/MonthlyCalendar.vue'
import TimeOffQuickForm from '@/components/dashboard/TimeOffQuickForm.vue'
import DayEntriesEditor from '@/components/dashboard/DayEntriesEditor.vue'

const { t } = useI18n()
const { currentUser } = useAuth()

// Use dashboard composable for all dashboard logic
const {
  currentMonth,
  dailySummaries,
  workingHours,
  activeEntry,
  nextVacation,
  overtimeSelectedMonth,
  selectedDate,
  selectedEntries,
  selectedTimeOffEntries,
  showTimeOffDialog,
  showEditDialog,
  hasTodayWorkingHours,
  nextVacationText,
  loadInitialData,
  loadDailySummaries,
  handleMonthChange,
  loadActiveEntry,
  loadNextVacation,
  calculateOvertime,
  formatOvertime,
  clockInNow,
  clockOutNow,
  cancelEntry,
  createQuickWorkEntry,
  handleQuickEntryFromCalendar,
  handleTimeOffFromCalendar,
  handleEditAllFromCalendar,
  handleFormSaved
} = useDashboard()


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
}

/* Main Layout: Calendar Left, Sidebar Right */
.dashboard-layout {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 2rem;
  align-items: start;
  max-width: 100%;
}

/* Calendar Section */
.calendar-section {
  min-height: 600px;
  max-width: 100%;
  overflow-x: auto;
}

/* Sidebar Section */
.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-lg);
}

.sidebar-section h3 {
  margin: 0 0 var(--tt-spacing-md) 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
}

/* Actions & Stats Combined Card */
.actions-stats-card {
  background: var(--p-card-background);
  border-radius: var(--tt-radius-md);
  padding: var(--tt-card-padding);
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-md);
}

/* Quick Actions Section */
.quick-actions-section {
  background: #f8f9fa;
  border-radius: var(--tt-radius-md);
  padding: var(--tt-card-padding);
}

.action-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--tt-spacing-sm);
}

.active-entry-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--tt-spacing-sm);
  grid-column: 1 / -1;
}

/* Styles for action cards are now in shared CSS */

/* Statistics Section */
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--tt-spacing-sm);
}

/* Responsive layout */
@media (max-width: 1200px) {
  .dashboard-layout {
    grid-template-columns: 1fr 350px;
  }
}

@media (max-width: 1024px) {
  .dashboard-layout {
    grid-template-columns: 1fr;
    gap: var(--tt-spacing-lg);
  }

  .calendar-section {
    min-height: auto;
  }

  .sidebar-section {
    order: 2; /* Show sidebar after calendar on mobile */
  }
}

@media (max-width: 768px) {
  .dashboard {
    padding: var(--tt-view-padding-mobile);
  }

  .dashboard-layout {
    gap: var(--tt-spacing-md);
  }

  .quick-actions-section,
  .stats-section {
    padding: var(--tt-spacing-md);
  }

  .sidebar-section h3 {
    font-size: 1.1rem;
  }
}

@media (max-width: 480px) {
  .dashboard {
    padding: var(--tt-view-padding-xs);
  }

  .dashboard-layout {
    gap: var(--tt-spacing-sm);
  }

  .quick-actions-section,
  .stats-section {
    padding: var(--tt-spacing-sm);
    border-radius: var(--tt-radius-sm);
  }

  .action-card {
    padding: var(--tt-spacing-sm);
  }

  .stat-card {
    padding: var(--tt-spacing-sm);
  }
}
</style>
