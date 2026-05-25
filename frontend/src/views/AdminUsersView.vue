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
          <Column field="email" header="Email">
            <template #body="{ data }">
              <span>{{ data.email }}</span>
              <Tag
                v-if="data.pending"
                :value="t('users.pending')"
                severity="warn"
                class="ml-2"
                style="font-size: 0.7rem; padding: 0.1rem 0.4rem;"
              />
            </template>
          </Column>
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
                  icon="pi pi-link"
                  text
                  severity="secondary"
                  @click="generateInvite(data)"
                  v-tooltip.top="data.pending ? t('users.inviteUser') : t('users.resetPasswordLink')"
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

    <!-- Create/Edit user dialog -->
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
          <label for="user-password">
            {{ t('profile.password') }}
            <span v-if="!isEdit" class="optional-hint">{{ t('users.passwordOptionalHint') }}</span>
          </label>
          <Password
            id="user-password"
            v-model="formData.password"
            :feedback="false"
            toggle-mask
            :required="false"
            fluid
            :placeholder="isEdit ? t('profile.passwordHint') : t('users.passwordOptionalPlaceholder')"
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

    <!-- Invite link dialog -->
    <Dialog
      v-model:visible="inviteDialogVisible"
      :header="inviteDialogIsReset ? t('users.resetPasswordLink') : t('users.inviteLink')"
      modal
      :style="{ width: '90vw', maxWidth: '550px' }"
    >
      <div class="invite-dialog-content">
        <p v-if="inviteExpiresAt" class="invite-expires">
          {{ t('users.expiresOn') }}: {{ inviteExpiresAt }}
        </p>
        <div class="invite-url-row">
          <InputText :value="inviteUrl" readonly fluid class="invite-url-field" />
          <Button
            icon="pi pi-copy"
            @click="copyInviteUrl"
            v-tooltip.top="t('users.copyLink')"
          />
        </div>
      </div>
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
import Tag from 'primevue/tag'
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

// Invite dialog state
const inviteDialogVisible = ref(false)
const inviteUrl = ref('')
const inviteExpiresAt = ref('')
const inviteDialogIsReset = ref(false)

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

function showInviteDialog(url: string, expiresAt: string | null | undefined, isReset: boolean) {
  inviteUrl.value = url
  inviteExpiresAt.value = expiresAt ? new Date(expiresAt).toLocaleDateString() : ''
  inviteDialogIsReset.value = isReset
  inviteDialogVisible.value = true
}

async function copyInviteUrl() {
  try {
    await navigator.clipboard.writeText(inviteUrl.value)
    toast.add({
      severity: 'success',
      summary: t('users.inviteCopied'),
      life: 2000
    })
  } catch {
    toast.add({ severity: 'error', summary: t('users.error'), life: 3000 })
  }
}

async function generateInvite(user: UserResponse) {
  try {
    const response = await apiClient.post<{ inviteUrl: string; expiresAt: string }>(
      `/api/users/${user.id}/invite`
    )
    showInviteDialog(response.data.inviteUrl, response.data.expiresAt, !user.pending)
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: t('users.error'),
      detail: getLocalizedErrorMessage(error, t, t('users.error')),
      life: 5000
    })
  }
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
      const createData: CreateUserRequest = {
        email: formData.value.email,
        firstName: formData.value.firstName,
        lastName: formData.value.lastName,
        role: formData.value.role,
        active: formData.value.active,
        state: formData.value.state,
        halfDayHolidaysEnabled: formData.value.halfDayHolidaysEnabled
      }
      if (formData.value.password) {
        createData.password = formData.value.password
      }

      const response = await apiClient.post<UserResponse>('/api/users', createData)
      toast.add({
        severity: 'success',
        summary: t('users.createSuccess'),
        life: 3000
      })

      // If invite URL returned (pending user), open invite modal automatically
      if (response.data.inviteUrl) {
        dialogVisible.value = false
        await loadUsers()
        showInviteDialog(response.data.inviteUrl, null, false)
        return
      }
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
        password: 'TempPassword123!',
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

    const response = await UsersService.impersonateUser(user.id!)

    const currentToken = localStorage.getItem('timetrack_access_token')
    const currentRefreshToken = localStorage.getItem('timetrack_refresh_token')
    if (currentToken) {
      sessionStorage.setItem('admin_token', currentToken)
      if (currentRefreshToken) {
        sessionStorage.setItem('admin_refresh_token', currentRefreshToken)
      }
      sessionStorage.setItem('impersonated_email', user.email!)
    }

    localStorage.setItem('timetrack_access_token', response.accessToken)
    if (response.refreshToken) {
      localStorage.setItem('timetrack_refresh_token', response.refreshToken)
    }

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

.optional-hint {
  font-size: 0.8rem;
  color: var(--p-text-muted-color);
  margin-left: 0.5rem;
  font-weight: normal;
}

.invite-dialog-content {
  display: flex;
  flex-direction: column;
  gap: var(--tt-spacing-md);
}

.invite-expires {
  margin: 0;
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.invite-url-row {
  display: flex;
  gap: var(--tt-spacing-sm);
  align-items: center;
}

.invite-url-field {
  flex: 1;
  font-family: monospace;
  font-size: 0.8rem;
}
</style>
