<template>
  <div class="register-container">
    <div class="register-header">
      <img src="/web-app-manifest-192x192.png" alt="TymeTrack" class="register-logo" />
      <h1 class="register-app-title">TymeTrack</h1>
    </div>

    <Card class="register-card">
      <template #title>
        {{ inviteInfo ? (inviteInfo.isPasswordReset ? t('register.resetTitle') : t('register.title')) : t('register.title') }}
      </template>
      <template #content>
        <div v-if="loadError" class="error-state">
          <Message severity="error">{{ loadError }}</Message>
          <Button :label="t('login.title')" text @click="router.push('/login')" class="mt-3" />
        </div>

        <div v-else-if="!inviteInfo" class="loading-state">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem;" />
        </div>

        <form v-else @submit.prevent="handleSubmit">
          <div class="field">
            <label>Email</label>
            <InputText :value="inviteInfo.email" readonly fluid class="readonly-field" />
          </div>

          <div class="field">
            <label for="reg-firstName">{{ t('profile.firstName') }}</label>
            <InputText
              id="reg-firstName"
              v-model="form.firstName"
              required
              fluid
            />
          </div>

          <div class="field">
            <label for="reg-lastName">{{ t('profile.lastName') }}</label>
            <InputText
              id="reg-lastName"
              v-model="form.lastName"
              required
              fluid
            />
          </div>

          <div class="field">
            <label for="reg-password">{{ t('profile.password') }}</label>
            <Password
              id="reg-password"
              v-model="form.password"
              :feedback="true"
              toggle-mask
              required
              fluid
            />
          </div>

          <div class="field">
            <label for="reg-confirm">{{ t('register.confirmPassword') }}</label>
            <Password
              id="reg-confirm"
              v-model="form.confirmPassword"
              :feedback="false"
              toggle-mask
              required
              fluid
              :invalid="passwordMismatch"
            />
            <small v-if="passwordMismatch" class="p-error">{{ t('register.passwordMismatch') }}</small>
          </div>

          <Message v-if="submitError" severity="error">{{ submitError }}</Message>

          <Button
            type="submit"
            :label="t('register.submit')"
            :loading="isSubmitting"
            :disabled="passwordMismatch"
            fluid
          />
        </form>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import apiClient, { setAccessToken } from '@/api/client'
import type { InviteInfoResponse, AuthResponse, UserResponse } from '@/api/generated'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const ACCESS_TOKEN_KEY = 'timetrack_access_token'
const REFRESH_TOKEN_KEY = 'timetrack_refresh_token'
const USER_KEY = 'timetrack_user'

const inviteInfo = ref<InviteInfoResponse | null>(null)
const loadError = ref('')
const isSubmitting = ref(false)
const submitError = ref('')

const form = ref({
  firstName: '',
  lastName: '',
  password: '',
  confirmPassword: ''
})

const passwordMismatch = computed(() =>
  form.value.confirmPassword.length > 0 && form.value.password !== form.value.confirmPassword
)

const token = computed(() => route.params.token as string)

onMounted(async () => {
  try {
    const response = await apiClient.get<InviteInfoResponse>(`/api/auth/invite/${token.value}`)
    inviteInfo.value = response.data
    form.value.firstName = response.data.firstName ?? ''
    form.value.lastName = response.data.lastName ?? ''
  } catch (error: any) {
    const status = error.response?.status
    if (status === 410) {
      loadError.value = t('register.expiredToken')
    } else {
      loadError.value = t('register.invalidToken')
    }
  }
})

async function handleSubmit() {
  if (passwordMismatch.value) return

  isSubmitting.value = true
  submitError.value = ''

  try {
    const response = await apiClient.post<AuthResponse>('/api/auth/register', {
      token: token.value,
      password: form.value.password,
      firstName: form.value.firstName,
      lastName: form.value.lastName
    })

    const authData = response.data

    // Store tokens
    localStorage.setItem(ACCESS_TOKEN_KEY, authData.accessToken!)
    localStorage.setItem(REFRESH_TOKEN_KEY, authData.refreshToken!)
    setAccessToken(authData.accessToken!)

    // Fetch full user profile
    const userResponse = await apiClient.get<UserResponse>('/api/users/me')
    localStorage.setItem(USER_KEY, JSON.stringify(userResponse.data))

    router.push('/dashboard')
  } catch (error: any) {
    const status = error.response?.status
    if (status === 410) {
      submitError.value = t('register.expiredToken')
    } else if (status === 404) {
      submitError.value = t('register.invalidToken')
    } else {
      submitError.value = t('register.error')
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--tt-spacing-lg);
  background: var(--p-surface-ground);
}

.register-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: var(--tt-spacing-lg);
}

.register-logo {
  width: 64px;
  height: 64px;
  margin-bottom: var(--tt-spacing-sm);
}

.register-app-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
}

.register-card {
  width: 100%;
  max-width: 460px;
}

.readonly-field {
  background: var(--p-surface-50);
  cursor: default;
}

.error-state,
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--tt-spacing-md);
  padding: var(--tt-spacing-lg) 0;
}

.mt-3 {
  margin-top: 0.75rem;
}
</style>
