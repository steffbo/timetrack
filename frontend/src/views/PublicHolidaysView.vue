<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import HolidayCard from '@/components/holidays/HolidayCard.vue'
import { PublicHolidaysService } from '@/api/generated'
import type { PublicHolidayResponse, PublicHolidaysResponse } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()

// Cache to store all holidays data (years 2023-2027, all states)
const holidaysCache = ref<PublicHolidaysResponse | null>(null)
const loading = ref(false)
const selectedYear = ref(new Date().getFullYear())
const selectedState = ref<'BERLIN' | 'BRANDENBURG'>('BERLIN')

const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() + i - 2)
const states = [
  { label: 'state.BERLIN', value: 'BERLIN' as const },
  { label: 'state.BRANDENBURG', value: 'BRANDENBURG' as const }
]

// Computed property to get holidays for selected year and state from cache
const holidays = computed<PublicHolidayResponse[]>(() => {
  if (!holidaysCache.value) {
    return []
  }

  const yearData = holidaysCache.value.holidaysByYearAndState[selectedYear.value.toString()]
  if (!yearData) {
    return []
  }

  return yearData[selectedState.value] || []
})

const loadHolidays = async () => {
  // Only load if not already cached
  if (holidaysCache.value) {
    return
  }

  loading.value = true
  try {
    const response = await PublicHolidaysService.getPublicHolidays()
    holidaysCache.value = response
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('publicHolidays.loadError'),
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const selectYear = (year: number) => {
  selectedYear.value = year
  // No need to reload - data is already cached
}

const selectState = (state: 'BERLIN' | 'BRANDENBURG') => {
  selectedState.value = state
  // No need to reload - data is already cached
}

onMounted(() => {
  loadHolidays()
})
</script>

<template>
  <div class="public-holidays-view">
    <h1 class="page-title">{{ t('publicHolidays.title') }}</h1>

    <div class="filters-container">
      <div class="filter-card">
        <label class="filter-label">{{ t('publicHolidays.year') }}</label>
        <div class="filter-buttons">
          <button
            v-for="year in years"
            :key="year"
            :class="['filter-btn', { active: selectedYear === year }]"
            @click="selectYear(year)"
          >
            {{ year }}
          </button>
        </div>
      </div>

      <div class="filter-card">
        <label class="filter-label">{{ t('publicHolidays.state') }}</label>
        <div class="filter-buttons">
          <button
            v-for="state in states"
            :key="state.value"
            :class="['filter-btn', { active: selectedState === state.value }]"
            @click="selectState(state.value)"
          >
            {{ t(state.label) }}
          </button>
        </div>
      </div>
    </div>

    <Card class="section-card">
      <template #content>

        <div v-if="loading" class="loading-state">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>

        <div v-else-if="holidays.length === 0" class="empty-state">
          {{ t('publicHolidays.noData') }}
        </div>

        <div v-else class="holidays-grid">
          <HolidayCard
            v-for="holiday in holidays"
            :key="holiday.date"
            :date="holiday.date"
            :name="holiday.name"
            :is-state-specific="holiday.isStateSpecific"
          />
        </div>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.public-holidays-view {
  padding: var(--tt-view-padding);
}

.filters-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.filter-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.25rem;
  background: var(--p-surface-0);
  border: 1px solid var(--p-surface-border);
  border-radius: 0.75rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
}

.filter-label {
  font-weight: 600;
  color: var(--p-text-color);
  font-size: 0.875rem;
}

.filter-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.filter-btn {
  padding: 0.5rem 1rem;
  background: var(--p-surface-50);
  border: 1px solid var(--p-surface-border);
  border-radius: 0.5rem;
  color: var(--p-text-color);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn:hover {
  background: var(--p-surface-100);
  border-color: var(--p-primary-color);
}

.filter-btn.active {
  background: var(--p-primary-color);
  border-color: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
  font-weight: 600;
}

.holidays-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.25rem;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 3rem 1rem;
  color: var(--p-text-muted-color);
  font-size: 1rem;
}

.loading-state {
  color: var(--p-primary-color);
}
</style>
