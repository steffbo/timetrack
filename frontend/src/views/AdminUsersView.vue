<template>
  <div class="admin-users">
    <Card>
      <template #title>
        {{ t('users.title') }}
      </template>
      <template #content>
        <div class="table-header">
          <Button
            :label="t('users.createUser')"
            icon="pi pi-plus"
            @click="openCreateDialog"
          />
        </div>

        <DataTable :value="users" :loading="isLoading">
          <Column field="email" header="Email" />
          <Column field="firstName" :header="t('profile.firstName')" />
          <Column field="lastName" :header="t('profile.lastName')" />
          <Column field="role" :header="t('profile.role')" />
          <Column field="active" :header="t('profile.active')">
            <template #body="{ data }">
              <i :class="data.active ? 'pi pi-check' : 'pi pi-times'" />
            </template>
          </Column>
          <Column :header="t('common.edit')">
            <template #body="{ data }">
              <div class="action-buttons">
                <Button
                  icon="pi pi-user-edit"
                  text
                  severity="info"
                  @click="impersonateUser(data)"
                  :disabled="data.role === 'ADMIN'"
                  v-tooltip.top="t('users.impersonate')"
                />
                <Button
                  icon="pi pi-pencil"
                  text
                  @click="openEditDialog(data)"
                />
                <Button
                  icon="pi pi-trash"
                  text
                  severity="danger"
                  @click="deleteUser(data)"
                />
              </div>
            </template>
          </Column>
        </DataTable>
      </template>
    </Card>

    <Dialog
      v-model:visible="dialogVisible"
      :header="isEdit ? t('users.editUser') : t('users.createUser')"
      modal
      :style="{ width: '90vw', maxWidth: '550px' }"
      :breakpoints="{ '960px': '75vw', '640px': '90vw' }"
    >
      <form @submit.prevent="handleSave">
        <div class="field">
          <label for="user-email">Email</label>
          <InputText
            id="user-email"
            v-model="formData.email"
            type="email"
            required
            fluid
          />
        </div>

        <div class="field">
          <label for="user-firstName">{{ t('profile.firstName') }}</label>
          <InputText
            id="user-firstName"
            v-model="formData.firstName"
            required
            fluid
          />
        </div>

        <div class="field">
          <label for="user-lastName">{{ t('profile.lastName') }}</label>
          <InputText
            id="user-lastName"
            v-model="formData.lastName"
            required
            fluid
          />
        </div>

        <div class="field">
          <label for="user-password">{{ t('profile.password') }}</label>
          <Password
            id="user-password"
            v-model="formData.password"
            :feedback="false"
            toggle-mask
            :required="!isEdit"
            fluid
            :placeholder="isEdit ? t('profile.passwordHint') : ''"
          />
        </div>

        <div class="field">
          <label for="user-role">{{ t('profile.role') }}</label>
          <Select
            id="user-role"
            v-model="formData.role"
            :options="['USER', 'ADMIN']"
            fluid
          />
        </div>

        <div class="field">
          <label for="user-state">{{ t('profile.state') }}</label>
          <Select
            id="user-state"
            v-model="formData.state"
            :options="stateOptions"
            option-label="label"
            option-value="value"
            fluid
          />
        </div>

        <div class="field-checkbox">
          <Checkbox
            id="user-active"
            v-model="formData.active"
            :binary="true"
          />
          <label for="user-active">{{ t('profile.active') }}</label>
        </div>

        <div class="field-checkbox">
          <Checkbox
            id="user-halfDayHolidays"
            v-model="formData.halfDayHolidaysEnabled"
            :binary="true"
          />
          <label for="user-halfDayHolidays">
            {{ t('profile.halfDayHolidays') }}
            <i
              v-tooltip="t('profile.halfDayHolidaysTooltip')"
              class="pi pi-info-circle ml-1"
              style="font-size: 0.875rem; cursor: help;"
            ></i>
          </label>
        </div>

        <div class="dialog-footer">
          <Button
            :label="t('common.cancel')"
            text
            @click="dialogVisible = false"
          />
          <Button
            type="submit"
            :label="t('common.save')"
            :loading="isSaving"
          />
        </div>
      </form>
    </Dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Checkbox from 'primevue/checkbox'
import apiClient from '@/api/client'
import type { UserResponse, CreateUserRequest, UpdateUserRequest, AuthResponse } from '@/api/generated'
import { UsersService } from '@/api/generated'
import { useUndoDelete } from '@/composables/useUndoDelete'
import { getLocalizedErrorMessage } from '@/utils/errorLocalization'

const { t } = useI18n()
const toast = useToast()

const { deleteWithUndo } = useUndoDelete()

const isLoading = ref(false)
const isSaving = ref(false)
const users = ref<UserResponse[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editUserId = ref<number | null>(null)

const formData = ref<CreateUserRequest & { password?: string }>({
  email: '',
  firstName: '',
  lastName: '',
  password: '',
  role: 'USER',
  active: true,
  state: 'BERLIN',
  halfDayHolidaysEnabled: false
})

const stateOptions = [
  { value: 'BERLIN', label: t('states.BERLIN') },
  { value: 'BRANDENBURG', label: t('states.BRANDENBURG') }
]

onMounted(async () => {
  await loadUsers()
})

async function loadUsers() {
  isLoading.value = true
  try {
    const response = await apiClient.get<UserResponse[]>('/api/users')
    users.value = response.data
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('users.error'),
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

function openCreateDialog() {
  isEdit.value = false
  editUserId.value = null
  formData.value = {
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    role: 'USER',
    active: true,
    state: 'BERLIN',
    halfDayHolidaysEnabled: false
  }
  dialogVisible.value = true
}

function openEditDialog(user: UserResponse) {
  isEdit.value = true
  editUserId.value = user.id!
  formData.value = {
    email: user.email!,
    firstName: user.firstName!,
    lastName: user.lastName!,
    password: '',
    role: user.role!,
    active: user.active!,
    state: user.state!,
    halfDayHolidaysEnabled: user.halfDayHolidaysEnabled || false
  }
  dialogVisible.value = true
}

async function handleSave() {
  isSaving.value = true
  try {
    if (isEdit.value && editUserId.value) {
      const updateData: UpdateUserRequest = {
        firstName: formData.value.firstName,
        lastName: formData.value.lastName,
        email: formData.value.email,
        role: formData.value.role,
        active: formData.value.active,
        state: formData.value.state,
        halfDayHolidaysEnabled: formData.value.halfDayHolidaysEnabled
      }

      if (formData.value.password) {
        (updateData as any).password = formData.value.password
      }

      await apiClient.put(`/api/users/${editUserId.value}`, updateData)
      toast.add({
        severity: 'success',
        summary: t('users.updateSuccess'),
        life: 3000
      })
    } else {
      await apiClient.post('/api/users', formData.value)
      toast.add({
        severity: 'success',
        summary: t('users.createSuccess'),
        life: 3000
      })
    }

    dialogVisible.value = false
    await loadUsers()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('users.error'),
      life: 3000
    })
  } finally {
    isSaving.value = false
  }
}

async function deleteUser(user: UserResponse) {
  await deleteWithUndo(
    user,
    async (id) => {
      await apiClient.delete(`/api/users/${id}`)
    },
    async () => {
      await loadUsers()
    },
    (item) => {
      return t('users.deleteSuccess') + `: ${item.email}`
    },
    async (item) => {
      // Recreate user with temporary password (user will need to reset password)
      const createRequest: CreateUserRequest = {
        email: item.email!,
        firstName: item.firstName!,
        lastName: item.lastName!,
        password: 'TempPassword123!', // Temporary password - user should reset
        role: item.role!,
        active: item.active!,
        state: item.state!,
        halfDayHolidaysEnabled: item.halfDayHolidaysEnabled || false
      }
      await apiClient.post('/api/users', createRequest)

      toast.add({
        severity: 'info',
        summary: t('info'),
        detail: t('users.undoPasswordReset'),
        life: 5000
      })
    },
    {
      showUndoSuccessToast: true
    }
  )
}

async function impersonateUser(user: UserResponse) {
  try {
    if (user.role === 'ADMIN') {
      toast.add({
        severity: 'warn',
        summary: t('warning'),
        detail: t('impersonation.cannotImpersonateAdmin'),
        life: 3000
      })
      return
    }

    // Use the generated API service
    const response = await UsersService.impersonateUser(user.id!)

    // Store current admin token using correct keys
    const currentToken = localStorage.getItem('timetrack_access_token')
    const currentRefreshToken = localStorage.getItem('timetrack_refresh_token')
    if (currentToken) {
      sessionStorage.setItem('admin_token', currentToken)
      if (currentRefreshToken) {
        sessionStorage.setItem('admin_refresh_token', currentRefreshToken)
      }
      sessionStorage.setItem('impersonated_email', user.email!)
    }

    // Set new token using correct keys
    localStorage.setItem('timetrack_access_token', response.accessToken)
    if (response.refreshToken) {
      localStorage.setItem('timetrack_refresh_token', response.refreshToken)
    }

    // Force full page reload to clear all cached state
    window.location.replace('/dashboard')
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('error'),
      detail: getLocalizedErrorMessage(error, t, t('users.error')),
      life: 5000
    })
  }
}
</script>

<style scoped>
/* Using shared form and utility styles */
.admin-users {
  padding: 0;
}

.table-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--tt-spacing-md);
}

.action-buttons {
  display: flex;
  gap: 0.25rem;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--tt-spacing-md);
  margin-top: var(--tt-card-gap);
}
</style>
