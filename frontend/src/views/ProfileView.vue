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
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import apiClient from '@/api/client'
import { useAuth } from '@/composables/useAuth'
import type { UpdateUserRequest } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()
const { currentUser } = useAuth()

const isLoading = ref(false)
const formData = ref<UpdateUserRequest & { password?: string }>({
  firstName: '',
  lastName: '',
  email: ''
})

onMounted(async () => {
  if (currentUser.value) {
    formData.value = {
      firstName: currentUser.value.firstName!,
      lastName: currentUser.value.lastName!,
      email: currentUser.value.email!
    }
  }
})

async function handleSave() {
  isLoading.value = true
  try {
    const updateData: UpdateUserRequest = {
      firstName: formData.value.firstName,
      lastName: formData.value.lastName,
      email: formData.value.email
    }

    if (formData.value.password) {
      (updateData as any).password = formData.value.password
    }

    await apiClient.put(`/api/users/${currentUser.value?.id}`, updateData)

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
  max-width: 600px;
  margin: 0 auto;
}

.field {
  margin-bottom: 1.5rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.button-group {
  display: flex;
  gap: 1rem;
  margin-top: 2rem;
}
</style>
