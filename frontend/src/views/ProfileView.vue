<template>
  <div class="profile">
    <Card>
      <template #title>
        {{ t('profile.title') }}
      </template>
      <template #content>
        <form @submit.prevent="handleSave">
          <div class="field">
            <label for="firstName">{{ t('profile.firstName') }}</label>
            <InputText
              id="firstName"
              v-model="formData.firstName"
              :disabled="isLoading"
              fluid
            />
          </div>

          <div class="field">
            <label for="lastName">{{ t('profile.lastName') }}</label>
            <InputText
              id="lastName"
              v-model="formData.lastName"
              :disabled="isLoading"
              fluid
            />
          </div>

          <div class="field">
            <label for="email">{{ t('profile.email') }}</label>
            <InputText
              id="email"
              v-model="formData.email"
              type="email"
              :disabled="isLoading"
              fluid
            />
          </div>

          <div class="field">
            <label for="password">{{ t('profile.password') }}</label>
            <Password
              id="password"
              v-model="formData.password"
              :feedback="false"
              toggle-mask
              :disabled="isLoading"
              fluid
              :placeholder="t('profile.passwordHint')"
            />
          </div>

          <div class="field">
            <label for="state">{{ t('profile.state') }}</label>
            <Select
              id="state"
              v-model="formData.state"
              :options="stateOptions"
              option-label="label"
              option-value="value"
              :disabled="isLoading"
              fluid
            />
          </div>

          <div class="checkbox-field">
            <Checkbox
              input-id="halfDayHolidays"
              v-model="formData.halfDayHolidaysEnabled"
              :binary="true"
              :disabled="isLoading"
            />
            <label for="halfDayHolidays" class="checkbox-label">
              {{ t('profile.halfDayHolidays') }}
              <i
                v-tooltip.right="t('profile.halfDayHolidaysTooltip')"
                class="pi pi-info-circle info-icon"
              ></i>
            </label>
          </div>

          <div class="button-group">
            <Button
              type="submit"
              :label="t('profile.save')"
              :loading="isLoading"
            />
          </div>
        </form>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import apiClient from '@/api/client'
import { useAuth } from '@/composables/useAuth'
import type { UpdateUserRequest } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()
const { currentUser, refreshCurrentUser } = useAuth()
const { handleError } = useErrorHandler()

const isLoading = ref(false)
const formData = ref<UpdateUserRequest & { password?: string }>({
  firstName: '',
  lastName: '',
  email: '',
  state: 'BERLIN',
  halfDayHolidaysEnabled: false
})

const stateOptions = computed(() => [
  { value: 'BERLIN', label: t('states.BERLIN') },
  { value: 'BRANDENBURG', label: t('states.BRANDENBURG') }
])

onMounted(async () => {
  try {
    // Fetch fresh user data from API
    const response = await apiClient.get('/api/users/me')
    const userData = response.data

    formData.value = {
      firstName: userData.firstName || '',
      lastName: userData.lastName || '',
      email: userData.email || '',
      state: userData.state || 'BERLIN',
      halfDayHolidaysEnabled: userData.halfDayHolidaysEnabled || false
    }
  } catch (error) {
    handleError(error, t('profile.loadError'))
  }
})

async function handleSave() {
  isLoading.value = true
  try {
    const updateData: UpdateUserRequest = {
      firstName: formData.value.firstName,
      lastName: formData.value.lastName,
      email: formData.value.email,
      state: formData.value.state as any,
      halfDayHolidaysEnabled: formData.value.halfDayHolidaysEnabled
    }

    if (formData.value.password) {
      (updateData as any).password = formData.value.password
    }

    await apiClient.put(`/api/users/${currentUser.value?.id}`, updateData)

    // Refresh user data in localStorage
    const updatedUserResponse = await apiClient.get('/api/users/me')
    localStorage.setItem('timetrack_user', JSON.stringify(updatedUserResponse.data))

    // Refresh the reactive currentUser ref
    refreshCurrentUser()

    toast.add({
      severity: 'success',
      summary: t('profile.saveSuccess'),
      life: 3000
    })

    // Clear password field
    formData.value.password = ''
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('profile.saveError'),
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}
</script>

<style scoped>
.profile {
  padding: 0;
}

/* Compact form spacing */
.field {
  margin-bottom: var(--tt-spacing-md);
}

/* Checkbox field styling */
.checkbox-field {
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-sm);
  padding: var(--tt-spacing-sm);
  border-radius: var(--tt-radius-sm);
  background-color: var(--surface-50);
  border: 1px solid var(--surface-200);
  transition: var(--tt-transition);
  margin-bottom: var(--tt-spacing-md);
}

.checkbox-field:hover {
  background-color: var(--surface-100);
  border-color: var(--primary-color);
}

.checkbox-label {
  margin: 0;
  cursor: pointer;
  font-weight: 500;
  font-size: 0.9375rem;
  color: var(--text-color);
  display: flex;
  align-items: center;
  gap: var(--tt-spacing-xs);
}

.info-icon {
  font-size: 0.875rem;
  color: var(--primary-color);
  cursor: help;
  opacity: 0.7;
  transition: opacity 0.2s ease;
}

.info-icon:hover {
  opacity: 1;
}

.button-group {
  margin-top: var(--tt-spacing-lg);
  display: flex;
  gap: var(--tt-spacing-sm);
}
</style>
