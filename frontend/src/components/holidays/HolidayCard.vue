<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

interface Props {
  date: string
  name: string
  isStateSpecific?: boolean
}

const props = defineProps<Props>()
const { t } = useI18n()

const dateObj = computed(() => new Date(props.date))

const weekdayShort = computed(() => {
  return new Intl.DateTimeFormat(t('locale'), {
    weekday: 'short'
  }).format(dateObj.value)
})

const dayNumber = computed(() => {
  return dateObj.value.getDate()
})

const monthName = computed(() => {
  return new Intl.DateTimeFormat(t('locale'), {
    month: 'long'
  }).format(dateObj.value)
})
</script>

<template>
  <div class="holiday-card" :class="{ 'state-specific': isStateSpecific }">
    <div class="holiday-card-left">
      {{ weekdayShort }}
    </div>
    <div class="holiday-card-right">
      <div class="holiday-name">{{ name }}</div>
      <div class="holiday-date">{{ dayNumber }}. {{ monthName }}</div>
    </div>
  </div>
</template>

<style scoped>
.holiday-card {
  display: flex;
  background: var(--p-surface-0);
  border: 1px solid var(--p-surface-border);
  border-radius: 0.75rem;
  overflow: hidden;
  min-height: 80px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s, box-shadow 0.2s;
}

.holiday-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.holiday-card.state-specific {
  background: #f0fdf4;
  border-color: #86efac;
}

.holiday-card.state-specific .holiday-card-left {
  background: #dcfce7;
}

.holiday-card-left {
  flex: 0 0 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--p-text-color);
  border-right: 1px solid var(--p-surface-border);
  background: var(--p-surface-50);
}

.holiday-card-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 1rem 1.25rem;
  gap: 0.25rem;
}

.holiday-name {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--p-text-color);
  line-height: 1.2;
}

.holiday-date {
  font-size: 0.875rem;
  font-weight: 400;
  color: var(--p-text-secondary-color);
  line-height: 1.3;
}
</style>
