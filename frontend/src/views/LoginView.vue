<template>
  <div class="login-container">
    <div class="login-header">
      <img src="/web-app-manifest-192x192.png" alt="TymeTrack" class="login-logo" />
      <h1 class="login-app-title">TymeTrack</h1>
    </div>
    <Card style="width: 400px" :class="{ 'shake': isShaking }">
      <template #title>
        {{ t('login.title') }}
      </template>
      <template #content>
        <form @submit.prevent="handleLogin">
          <div class="field">
            <label for="email">{{ t('login.email') }}</label>
            <InputText
              id="email"
              v-model="credentials.email"
              type="email"
              required
              autocomplete="username"
              :disabled="isLoading"
              :invalid="!!errorMessage"
              fluid
            />
          </div>

          <div class="field">
            <label for="password">{{ t('login.password') }}</label>
            <Password
              id="password"
              v-model="credentials.password"
              :feedback="false"
              toggle-mask
              required
              :input-props="{ autocomplete: 'current-password' }"
              :disabled="isLoading"
              :invalid="!!errorMessage"
              fluid
            />
          </div>

          <Message v-if="errorMessage" severity="error">
            {{ errorMessage }}
          </Message>

          <Button
            type="submit"
            :label="t('login.submit')"
            :loading="isLoading"
            fluid
          />
        </form>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const { t } = useI18n()
const { login, isLoading } = useAuth()

const credentials = ref({
  email: '',
  password: ''
})

const errorMessage = ref('')
const isShaking = ref(false)

async function handleLogin() {
  errorMessage.value = ''
  isShaking.value = false
  
  const success = await login(credentials.value)

  if (success) {
    router.push('/dashboard')
  } else {
    errorMessage.value = t('login.error')
    isShaking.value = true
    setTimeout(() => {
      isShaking.value = false
    }, 500)
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--p-surface-50);
  gap: 2rem;
}

.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.login-logo {
  height: 4rem;
  width: 4rem;
}

.login-app-title {
  font-size: 2rem;
  font-weight: 600;
  color: var(--p-text-color);
  margin: 0;
}

.field {
  margin-bottom: 1.5rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

form button {
  margin-top: 1rem;
}

.shake {
  animation: shake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97) both;
  transform: translate3d(0, 0, 0);
  backface-visibility: hidden;
  perspective: 1000px;
}

@keyframes shake {
  10%, 90% { transform: translate3d(-1px, 0, 0); }
  20%, 80% { transform: translate3d(2px, 0, 0); }
  30%, 50%, 70% { transform: translate3d(-4px, 0, 0); }
  40%, 60% { transform: translate3d(4px, 0, 0); }
}
</style>
