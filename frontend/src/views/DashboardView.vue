<template>
  <div class="dashboard">
    <div class="welcome-section">
      <h2>{{ t('dashboard.welcome', { name: currentUser?.firstName || currentUser?.email }) }}</h2>
    </div>

    <div class="dashboard-grid">
      <div class="calendar-section">
        <MonthlyCalendar
          :current-month="currentMonth"
          :daily-summaries="dailySummaries"
          @month-change="handleMonthChange"
        />
      </div>

      <div class="stats-section">
        <Card>
          <template #title>
            {{ t('dashboard.statistics.title') }}
          </template>
          <template #content>
            <p>{{ t('dashboard.statistics.comingSoon') }}</p>
          </template>
        </Card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import { useAuth } from '@/composables/useAuth'
import MonthlyCalendar from '@/components/dashboard/MonthlyCalendar.vue'
import { TimeEntriesService, PublicHolidaysService } from '@/api/generated'
import type { DailySummaryResponse, PublicHolidayResponse, TimeOffResponse } from '@/api/generated'

const { t } = useI18n()
const { currentUser } = useAuth()
const toast = useToast()

const currentMonth = ref<Date>(new Date())
const dailySummaries = ref<DailySummaryResponse[]>([])
const loading = ref(false)

const loadDailySummaries = async () => {
  loading.value = true
  try {
    const year = currentMonth.value.getFullYear()
    const month = currentMonth.value.getMonth()

    // Get first and last day of the month
    const startDate = new Date(year, month, 1)
    const endDate = new Date(year, month + 1, 0)

    const startDateStr = startDate.toISOString().split('T')[0]
    const endDateStr = endDate.toISOString().split('T')[0]

    // Fetch daily summaries and public holidays in parallel
    const [summaries, publicHolidays] = await Promise.all([
      TimeEntriesService.getDailySummary(startDateStr, endDateStr),
      PublicHolidaysService.getPublicHolidays(year)
    ])

    // Merge public holidays into daily summaries
    const summariesWithHolidays = summaries.map(summary => {
      // Check if this date is a public holiday
      const holiday = publicHolidays.find(h => h.date === summary.date)

      if (holiday) {
        // Create a time-off entry for the public holiday
        const holidayTimeOff: TimeOffResponse = {
          id: 0, // Dummy ID for display purposes
          userId: currentUser.value?.id || 0,
          startDate: holiday.date!,
          endDate: holiday.date!,
          timeOffType: 'PUBLIC_HOLIDAY' as any,
          notes: holiday.name
        }

        // Add the holiday to the beginning of timeOffEntries (highest precedence)
        return {
          ...summary,
          timeOffEntries: [holidayTimeOff, ...(summary.timeOffEntries || [])]
        }
      }

      return summary
    })

    dailySummaries.value = summariesWithHolidays
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('dashboard.loadError'),
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const handleMonthChange = (newMonth: Date) => {
  currentMonth.value = newMonth
  loadDailySummaries()
}

onMounted(() => {
  loadDailySummaries()
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1rem;
}

.welcome-section {
  margin-bottom: 1.5rem;
}

.welcome-section h2 {
  margin: 0;
  color: var(--p-text-color);
  font-size: 1.75rem;
  font-weight: 600;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
  align-items: start;
}

.calendar-section {
  min-height: 500px;
}

.stats-section {
  min-height: 500px;
}

/* Responsive layout */
@media (max-width: 1024px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .calendar-section,
  .stats-section {
    min-height: auto;
  }
}
</style>
