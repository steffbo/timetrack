<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Knob from 'primevue/knob'
import { useAuth } from '@/composables/useAuth'
import { VacationBalanceService } from '@/api/generated'
import type { VacationBalanceResponse, UpdateVacationBalanceRequest } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()
const { user } = useAuth()

const balance = ref<VacationBalanceResponse | null>(null)
const loading = ref(false)
const editDialogVisible = ref(false)
const selectedYear = ref(new Date().getFullYear())
const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() + i - 2)

const editForm = ref<UpdateVacationBalanceRequest>({
  annualAllowanceDays: 30.0,
  carriedOverDays: 0.0,
  adjustmentDays: 0.0
})

const remainingDays = computed(() => {
  if (!balance.value) return 0
  return balance.value.remainingDays
})

const totalAvailableDays = computed(() => {
  if (!balance.value) return 0
  return balance.value.annualAllowanceDays + balance.value.carriedOverDays + balance.value.adjustmentDays
})

const usedPercentage = computed(() => {
  if (!balance.value || totalAvailableDays.value === 0) return 0
  return Math.round((balance.value.usedDays / totalAvailableDays.value) * 100)
})

const loadBalance = async () => {
  loading.value = true
  try {
    const response = await VacationBalanceService.getVacationBalance(selectedYear.value)
    balance.value = response
  } catch (error: any) {
    if (error?.status === 404) {
      balance.value = null
      toast.add({
        severity: 'info',
        summary: t('info'),
        detail: t('vacationBalance.noDataForYear'),
        life: 3000
      })
    } else {
      toast.add({
        severity: 'error',
        summary: t('error'),
        detail: t('vacationBalance.loadError'),
        life: 3000
      })
    }
  } finally {
    loading.value = false
  }
}

const openEditDialog = () => {
  if (!balance.value) return

  editForm.value = {
    annualAllowanceDays: balance.value.annualAllowanceDays,
    carriedOverDays: balance.value.carriedOverDays,
    adjustmentDays: balance.value.adjustmentDays
  }
  editDialogVisible.value = true
}

const saveBalance = async () => {
  try {
    await VacationBalanceService.updateVacationBalance(selectedYear.value, editForm.value)
    toast.add({
      severity: 'success',
      summary: t('success'),
      detail: t('vacationBalance.updateSuccess'),
      life: 3000
    })
    editDialogVisible.value = false
    await loadBalance()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: t('vacationBalance.updateError'),
      life: 3000
    })
  }
}

const getSeverity = () => {
  const percentage = usedPercentage.value
  if (percentage < 50) return 'success'
  if (percentage < 80) return 'warning'
  return 'danger'
}

onMounted(() => {
  loadBalance()
})
</script>

<template>
  <div class="vacation-balance-view">
    <div class="card">
      <div class="flex justify-content-between align-items-center mb-4">
        <h1>{{ t('vacationBalance.title') }}</h1>
        <div class="flex gap-2">
          <Select
            v-model="selectedYear"
            :options="years"
            @change="loadBalance"
            class="w-auto"
          />
          <Button
            v-if="balance && user?.role === 'ADMIN'"
            :label="t('edit')"
            icon="pi pi-pencil"
            @click="openEditDialog"
          />
        </div>
      </div>

      <div v-if="loading" class="flex justify-content-center py-4">
        <ProgressSpinner />
      </div>

      <div v-else-if="balance" class="balance-content">
        <div class="grid">
          <!-- Summary Card -->
          <div class="col-12 lg:col-6">
            <Card>
              <template #title>
                <div class="flex justify-content-between align-items-center">
                  <span>{{ t('vacationBalance.summary') }}</span>
                </div>
              </template>
              <template #content>
                <div class="balance-summary">
                  <div class="summary-item">
                    <span class="summary-label">{{ t('vacationBalance.annualAllowanceDays') }}</span>
                    <span class="summary-value">{{ balance.annualAllowanceDays.toFixed(1) }} {{ t('vacationBalance.days') }}</span>
                  </div>
                  <div class="summary-item">
                    <span class="summary-label">{{ t('vacationBalance.carriedOverDays') }}</span>
                    <span class="summary-value">{{ balance.carriedOverDays.toFixed(1) }} {{ t('vacationBalance.days') }}</span>
                  </div>
                  <div class="summary-item">
                    <span class="summary-label">{{ t('vacationBalance.adjustmentDays') }}</span>
                    <span class="summary-value">{{ balance.adjustmentDays.toFixed(1) }} {{ t('vacationBalance.days') }}</span>
                  </div>
                  <Divider />
                  <div class="summary-item total">
                    <span class="summary-label font-bold">{{ t('vacationBalance.totalAvailable') }}</span>
                    <span class="summary-value font-bold">{{ totalAvailableDays.toFixed(1) }} {{ t('vacationBalance.days') }}</span>
                  </div>
                  <div class="summary-item">
                    <span class="summary-label">{{ t('vacationBalance.usedDays') }}</span>
                    <span class="summary-value text-red-500">-{{ balance.usedDays.toFixed(1) }} {{ t('vacationBalance.days') }}</span>
                  </div>
                  <Divider />
                  <div class="summary-item remaining">
                    <span class="summary-label font-bold text-xl">{{ t('vacationBalance.remainingDays') }}</span>
                    <span class="summary-value font-bold text-2xl text-primary">{{ remainingDays.toFixed(1) }} {{ t('vacationBalance.days') }}</span>
                  </div>
                </div>
              </template>
            </Card>
          </div>

          <!-- Progress Chart -->
          <div class="col-12 lg:col-6">
            <Card>
              <template #title>{{ t('vacationBalance.usage') }}</template>
              <template #content>
                <div class="text-center">
                  <div class="progress-circle mb-3">
                    <Knob
                      v-model="usedPercentage"
                      :size="200"
                      :readonly="true"
                      :value-color="getSeverity() === 'success' ? '#22c55e' : getSeverity() === 'warning' ? '#f59e0b' : '#ef4444'"
                    />
                  </div>
                  <div class="progress-stats">
                    <p class="text-lg font-semibold">
                      {{ balance.usedDays.toFixed(1) }} / {{ totalAvailableDays.toFixed(1) }} {{ t('vacationBalance.days') }}
                    </p>
                    <p class="text-gray-600">
                      {{ usedPercentage }}% {{ t('vacationBalance.used') }}
                    </p>
                  </div>
                </div>
              </template>
            </Card>
          </div>
        </div>

        <!-- Info Message -->
        <Message v-if="user?.role !== 'ADMIN'" severity="info" :closable="false" class="mt-3">
          {{ t('vacationBalance.userInfo') }}
        </Message>
      </div>

      <div v-else class="text-center py-4 text-gray-500">
        <i class="pi pi-info-circle text-4xl mb-3" />
        <p>{{ t('vacationBalance.noData') }}</p>
      </div>
    </div>

    <!-- Edit Dialog (Admin only) -->
    <Dialog
      v-model:visible="editDialogVisible"
      :header="t('vacationBalance.edit')"
      :modal="true"
      :style="{ width: '500px' }"
    >
      <div class="p-fluid">
        <div class="field">
          <label for="annualAllowanceDays">{{ t('vacationBalance.annualAllowanceDays') }} *</label>
          <InputNumber
            id="annualAllowanceDays"
            v-model="editForm.annualAllowanceDays"
            :min="0"
            :max="365"
            :max-fraction-digits="1"
            suffix=" days"
          />
        </div>

        <div class="field">
          <label for="carriedOverDays">{{ t('vacationBalance.carriedOverDays') }}</label>
          <InputNumber
            id="carriedOverDays"
            v-model="editForm.carriedOverDays"
            :min="0"
            :max="365"
            :max-fraction-digits="1"
            suffix=" days"
          />
          <small>{{ t('vacationBalance.carriedOverDaysHint') }}</small>
        </div>

        <div class="field">
          <label for="adjustmentDays">{{ t('vacationBalance.adjustmentDays') }}</label>
          <InputNumber
            id="adjustmentDays"
            v-model="editForm.adjustmentDays"
            :min="-365"
            :max="365"
            :max-fraction-digits="1"
            suffix=" days"
          />
          <small>{{ t('vacationBalance.adjustmentDaysHint') }}</small>
        </div>

        <Message severity="info" :closable="false">
          {{ t('vacationBalance.usedDaysAutoCalculated') }}
        </Message>
      </div>

      <template #footer>
        <Button
          :label="t('cancel')"
          icon="pi pi-times"
          class="p-button-text"
          @click="editDialogVisible = false"
        />
        <Button
          :label="t('save')"
          icon="pi pi-check"
          @click="saveBalance"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.vacation-balance-view {
  padding: 2rem;
}

h1 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.balance-summary {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary-label {
  font-size: 0.95rem;
  color: #6c757d;
}

.summary-value {
  font-size: 1.1rem;
  font-weight: 600;
}

.summary-item.total,
.summary-item.remaining {
  padding: 0.5rem 0;
}

.progress-circle {
  display: flex;
  justify-content: center;
}

.field {
  margin-bottom: 1.5rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.field small {
  display: block;
  margin-top: 0.25rem;
  color: #6c757d;
}
</style>
