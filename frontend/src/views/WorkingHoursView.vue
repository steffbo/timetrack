<template>
  <div class="working-hours">
    <Card>
      <template #title>
        {{ t('workingHours.title') }}
      </template>
      <template #content>
        <DataTable :value="workingDays" :loading="isLoading">
          <Column field="weekday" header="Tag">
            <template #body="{ data }">
              {{ getWeekdayName(data.weekday) }}
            </template>
          </Column>

          <Column field="isWorkingDay" :header="t('workingHours.isWorkingDay')">
            <template #body="{ data }">
              <Checkbox v-model="data.isWorkingDay" :binary="true" />
            </template>
          </Column>

          <Column field="startTime" :header="t('workingHours.startTime')">
            <template #body="{ data }">
              <InputText
                v-model="data.startTime"
                type="time"
                :disabled="!data.isWorkingDay"
                @change="handleTimeChange(data)"
                fluid
              />
            </template>
          </Column>

          <Column field="endTime" :header="t('workingHours.endTime')">
            <template #body="{ data }">
              <InputText
                v-model="data.endTime"
                type="time"
                :disabled="!data.isWorkingDay"
                @change="handleTimeChange(data)"
                fluid
              />
            </template>
          </Column>

          <Column field="hours" :header="t('workingHours.hours')">
            <template #body="{ data }">
              <InputNumber
                v-model="data.hours"
                :min="0"
                :max="24"
                :disabled="!data.isWorkingDay || hasTimeValues(data)"
                showButtons
                fluid
              />
            </template>
          </Column>
        </DataTable>

        <div class="button-group">
          <Button
            :label="t('workingHours.save')"
            :loading="isSaving"
            @click="handleSave"
          />
        </div>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Checkbox from 'primevue/checkbox'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import apiClient from '@/api/client'
import { useAuth } from '@/composables/useAuth'
import type { WorkingDayConfig } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()
const { currentUser } = useAuth()

const isLoading = ref(false)
const isSaving = ref(false)
const workingDays = ref<WorkingDayConfig[]>([])

// Map weekday number (1-7) to day name
const weekdayMap = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

function getWeekdayName(weekday: number): string {
  return t(`workingHours.weekdays.${weekdayMap[weekday - 1]}`)
}

function hasTimeValues(data: WorkingDayConfig): boolean {
  return !!(data.startTime && data.endTime)
}

function handleTimeChange(data: WorkingDayConfig) {
  // Calculate hours from start and end time if both are set
  if (data.startTime && data.endTime) {
    const start = parseTime(data.startTime)
    const end = parseTime(data.endTime)

    if (start && end && end > start) {
      const diffMinutes = (end - start) / (1000 * 60)
      data.hours = Math.round((diffMinutes / 60) * 100) / 100
    }
  }
}

function parseTime(timeStr: string): number | null {
  if (!timeStr) return null
  const [hours, minutes] = timeStr.split(':').map(Number)
  if (isNaN(hours) || isNaN(minutes) || hours === undefined || minutes === undefined) return null
  return new Date(1970, 0, 1, hours, minutes).getTime()
}

onMounted(async () => {
  await loadWorkingHours()
})

async function loadWorkingHours() {
  isLoading.value = true
  try {
    const response = await apiClient.get(
      `/api/working-hours/${currentUser.value?.id}`
    )
    workingDays.value = response.data.workingDays
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('workingHours.saveError'),
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

async function handleSave() {
  isSaving.value = true
  try {
    await apiClient.put(
      `/api/working-hours`,
      { workingDays: workingDays.value }
    )

    toast.add({
      severity: 'success',
      summary: t('workingHours.saveSuccess'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('workingHours.saveError'),
      life: 3000
    })
  } finally {
    isSaving.value = false
  }
}
</script>

<style scoped>
.working-hours {
  padding: 0;
}

.button-group {
  display: flex;
  gap: 1rem;
  margin-top: 2rem;
}
</style>
