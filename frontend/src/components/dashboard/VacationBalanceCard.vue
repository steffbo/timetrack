<template>
  <div class="info-card vacation-balance-card">
    <div class="card-header">
      <span class="card-icon">🏝️</span>
      <h4>{{ t('dashboard.vacationBalance.title') }}</h4>
    </div>
    
    <div v-if="loading" class="card-loading">
      <i class="pi pi-spin pi-spinner"></i>
    </div>
    
    <div v-else-if="balance" class="card-content">
      <!-- Main remaining days display -->
      <div class="main-stat">
        <span class="stat-value">{{ formatDays(balance.remainingDays) }}</span>
        <span class="stat-label">{{ t('dashboard.vacationBalance.daysLeft') }}</span>
      </div>
      
      <!-- Progress bar -->
      <div class="progress-container">
        <div class="progress-bar">
          <div 
            class="progress-used" 
            :style="{ width: usedPercentage + '%' }"
            :title="t('dashboard.vacationBalance.used')"
          ></div>
          <div 
            class="progress-planned" 
            :style="{ width: plannedPercentage + '%', left: usedPercentage + '%' }"
            :title="t('dashboard.vacationBalance.planned')"
          ></div>
        </div>
        <div class="progress-labels">
          <span>{{ formatDays(balance.usedDays) }} {{ t('dashboard.vacationBalance.used') }}</span>
          <span>{{ formatDays(totalDays) }} {{ t('dashboard.vacationBalance.total') }}</span>
        </div>
      </div>
      
      <!-- Quick stats row -->
      <div class="quick-stats">
        <div class="quick-stat">
          <span class="quick-stat-value">{{ formatDays(balance.plannedDays) }}</span>
          <span class="quick-stat-label">{{ t('dashboard.vacationBalance.planned') }}</span>
        </div>
        <div class="quick-stat">
          <span class="quick-stat-value">{{ formatDays(balance.usedDays) }}</span>
          <span class="quick-stat-label">{{ t('dashboard.vacationBalance.taken') }}</span>
        </div>
      </div>
    </div>
    
    <div v-else class="card-empty">
      {{ t('dashboard.vacationBalance.noData') }}
    </div>
    
    <!-- Link to time-off view -->
    <router-link to="/time-off" class="card-link">
      {{ t('dashboard.vacationBalance.viewDetails') }}
      <i class="pi pi-angle-right"></i>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { VacationBalanceService } from '@/api/generated'
import type { VacationBalanceResponse } from '@/api/generated'

const { t } = useI18n()

const loading = ref(true)
const balance = ref<VacationBalanceResponse | null>(null)

// Computed values
const totalDays = computed(() => {
  if (!balance.value) return 0
  return balance.value.annualAllowanceDays + balance.value.carriedOverDays + balance.value.adjustmentDays
})

const usedPercentage = computed(() => {
  if (!balance.value || totalDays.value === 0) return 0
  return Math.min((balance.value.usedDays / totalDays.value) * 100, 100)
})

const plannedPercentage = computed(() => {
  if (!balance.value || totalDays.value === 0) return 0
  // Only show planned days that aren't already used
  const futurePlanned = balance.value.plannedDays - balance.value.usedDays
  return Math.min((futurePlanned / totalDays.value) * 100, 100 - usedPercentage.value)
})

// Format days with 0.5 precision
const formatDays = (days: number): string => {
  return days % 1 === 0 ? days.toString() : days.toFixed(1)
}

// Load vacation balance for current year
const loadBalance = async () => {
  try {
    loading.value = true
    const currentYear = new Date().getFullYear()
    balance.value = await VacationBalanceService.getVacationBalance(currentYear)
  } catch (error) {
    console.error('Failed to load vacation balance:', error)
    balance.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadBalance()
})
</script>

<style scoped>
.info-card {
  background: var(--p-card-background);
  border-radius: var(--tt-radius-md);
  padding: var(--tt-spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);
}

.card-icon {
  font-size: 1.25rem;
}

.card-header h4 {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--tt-text-primary);
}

.card-loading {
  display: flex;
  justify-content: center;
  padding: var(--tt-spacing-md);
  color: var(--tt-text-secondary);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-sm);
}

.main-stat {
  display: flex;
  align-items: baseline;
  gap: var(--tt-spacing-xs);
}

.main-stat .stat-value {
  font-size: 2rem;
  font-weight: 700;
  color: var(--tt-emerald-from);
  line-height: 1;
}

.main-stat .stat-label {
  font-size: 0.875rem;
  color: var(--tt-text-secondary);
}

/* Progress bar */
.progress-container {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-xs);
}

.progress-bar {
  position: relative;
  height: 8px;
  background: var(--tt-bg-light);
  border-radius: 4px;
  overflow: hidden;
}

.progress-used {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, var(--tt-emerald-from) 0%, var(--tt-emerald-to) 100%);
  border-radius: 4px 0 0 4px;
  transition: width 0.3s ease;
}

.progress-planned {
  position: absolute;
  top: 0;
  height: 100%;
  background: rgba(16, 185, 129, 0.3);
  transition: width 0.3s ease, left 0.3s ease;
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
}

/* Quick stats row */
.quick-stats {
  display: flex;
  gap: var(--tt-spacing-md);
  padding-top: var(--tt-spacing-xs);
  border-top: 1px solid var(--tt-bg-light);
}

.quick-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.quick-stat-value {
  font-size: 1rem;
  font-weight: 600;
  color: var(--tt-text-primary);
}

.quick-stat-label {
  font-size: 0.75rem;
  color: var(--tt-text-secondary);
}

.card-empty {
  color: var(--tt-text-secondary);
  font-size: 0.875rem;
  text-align: center;
  padding: var(--tt-spacing-sm);
}

.card-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--tt-spacing-xs);
  padding-top: var(--tt-spacing-xs);
  font-size: 0.75rem;
  color: var(--tt-emerald-from);
  text-decoration: none;
  transition: color 0.2s ease;
}

.card-link:hover {
  color: var(--tt-emerald-to);
}

.card-link i {
  font-size: 0.875rem;
}
</style>
