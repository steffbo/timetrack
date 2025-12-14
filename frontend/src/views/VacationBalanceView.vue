<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import { useAuth } from '@/composables/useAuth'
import { VacationBalanceService } from '@/api/generated'
import type { VacationBalanceResponse, UpdateVacationBalanceRequest } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()
const { currentUser } = useAuth()

const balance = ref<VacationBalanceResponse | null>(null)
const loading = ref(false)
const editDialogVisible = ref(false)
const selectedYear = ref(new Date().getFullYear())
const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() + i - 2)

const editForm = ref<UpdateVacationBalanceRequest>({
  userId: currentUser.value?.id || 0,
  year: new Date().getFullYear(),
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
  if (!balance.value || !currentUser.value) return

  editForm.value = {
    userId: currentUser.value.id,
    year: selectedYear.value,
    annualAllowanceDays: balance.value.annualAllowanceDays,
    carriedOverDays: balance.value.carriedOverDays,
    adjustmentDays: balance.value.adjustmentDays
  }
  editDialogVisible.value = true
}

const saveBalance = async () => {
  try {
    await VacationBalanceService.updateVacationBalance(editForm.value)
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
            v-if="balance"
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
        <!-- Stat Cards -->
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-label">{{ t('vacationBalance.annualAllowanceDays') }}</div>
            <div class="stat-value">{{ balance.annualAllowanceDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card stat-planned">
            <div class="stat-label">{{ t('vacationBalance.plannedDays') }}</div>
            <div class="stat-value">{{ balance.plannedDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card stat-used">
            <div class="stat-label">{{ t('vacationBalance.usedDays') }}</div>
            <div class="stat-value">{{ balance.usedDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card stat-remaining">
            <div class="stat-label">{{ t('vacationBalance.leftForPlanning') }}</div>
            <div class="stat-value">{{ remainingDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card">
            <div class="stat-label">{{ t('vacationBalance.carriedOverDays') }}</div>
            <div class="stat-value">{{ balance.carriedOverDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>

          <div class="stat-card">
            <div class="stat-label">{{ t('vacationBalance.adjustmentDays') }}</div>
            <div class="stat-value">{{ balance.adjustmentDays.toFixed(1) }}</div>
            <div class="stat-unit">{{ t('vacationBalance.days') }}</div>
          </div>
        </div>
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
  padding: 1rem 2rem 2rem 2rem;
}

h1 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

/* Stat Cards Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
}

.stat-card {
  background: white;
  border-radius: 8px;
  padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
  text-align: center;
}

.stat-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.stat-label {
  font-size: 0.85rem;
  color: #6c757d;
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.stat-value {
  font-size: 2rem;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 0.25rem;
  line-height: 1;
}

.stat-unit {
  font-size: 0.875rem;
  color: #9ca3af;
  font-weight: 500;
}

.stat-card.stat-total {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-card.stat-total .stat-label,
.stat-card.stat-total .stat-value,
.stat-card.stat-total .stat-unit {
  color: white;
}

.stat-card.stat-used {
  background: linear-gradient(135deg, #f59e0b 0%, #ef4444 100%);
}

.stat-card.stat-used .stat-label,
.stat-card.stat-used .stat-value,
.stat-card.stat-used .stat-unit {
  color: white;
}

.stat-card.stat-planned {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-card.stat-planned .stat-label,
.stat-card.stat-planned .stat-value,
.stat-card.stat-planned .stat-unit {
  color: white;
}

.stat-card.stat-remaining {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.stat-card.stat-remaining .stat-label,
.stat-card.stat-remaining .stat-value,
.stat-card.stat-remaining .stat-unit {
  color: white;
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

/* Responsive adjustments */
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-value {
    font-size: 1.5rem;
  }
}

@media (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
