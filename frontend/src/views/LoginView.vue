<template>
  <div class="login-container">
    <Card style="width: 400px">
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
              autocomplete="current-password"
              :disabled="isLoading"
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

async function handleLogin() {
  errorMessage.value = ''
  const success = await login(credentials.value)

  if (success) {
    router.push('/dashboard')
  } else {
    errorMessage.value = t('login.error')
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--p-surface-50);
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
</style>
