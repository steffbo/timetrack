<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Select from 'primevue/select'
import { PublicHolidaysService } from '@/api/generated'
import type { PublicHolidayResponse } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()

const holidays = ref<PublicHolidayResponse[]>([])
const loading = ref(false)
const selectedYear = ref(new Date().getFullYear())
const selectedState = ref<'BERLIN' | 'BRANDENBURG'>('BERLIN')

const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() + i - 2)

const loadHolidays = async () => {
  loading.value = true
  try {
    const response = await PublicHolidaysService.getPublicHolidays(selectedYear.value, selectedState.value)
    holidays.value = response
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

onMounted(() => {
  loadHolidays()
})

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return new Intl.DateTimeFormat('de-DE', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric'
  }).format(date)
}
</script>

<template>
  <div class="public-holidays-view">
    <div class="card">
      <h1>{{ t('publicHolidays.title') }}</h1>

      <div class="filters">
        <div class="p-fluid">
          <div class="flex gap-3 mb-4">
            <div class="flex-1">
              <label for="year">{{ t('publicHolidays.year') }}</label>
              <Select
                id="year"
                v-model="selectedYear"
                :options="years"
                @change="loadHolidays"
              />
            </div>
            <div class="flex-1">
              <label for="state">{{ t('publicHolidays.state') }}</label>
              <Select
                id="state"
                v-model="selectedState"
                :options="[
                  { label: t('state.BERLIN'), value: 'BERLIN' },
                  { label: t('state.BRANDENBURG'), value: 'BRANDENBURG' }
                ]"
                option-label="label"
                option-value="value"
                @change="loadHolidays"
              />
            </div>
          </div>
        </div>
      </div>

      <DataTable
        :value="holidays"
        :loading="loading"
        striped-rows
        responsive-layout="scroll"
      >
        <Column field="date" :header="t('publicHolidays.date')">
          <template #body="{ data }">
            <span class="font-semibold">{{ formatDate(data.date) }}</span>
          </template>
        </Column>
        <Column field="name" :header="t('publicHolidays.name')">
          <template #body="{ data }">
            {{ data.name }}
          </template>
        </Column>
      </DataTable>

      <div v-if="holidays.length === 0 && !loading" class="text-center py-4 text-gray-500">
        {{ t('publicHolidays.noData') }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.public-holidays-view {
  padding: 2rem;
}

h1 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.filters {
  margin-bottom: 1.5rem;
}

label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}
</style>
