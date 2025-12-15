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
              <Button
                icon="pi pi-pencil"
                text
                @click="openEditDialog(data)"
              />
              <Button
                icon="pi pi-trash"
                text
                severity="danger"
                @click="confirmDelete(data)"
              />
            </template>
          </Column>
        </DataTable>
      </template>
    </Card>

    <Dialog
      v-model:visible="dialogVisible"
      :header="isEdit ? t('users.editUser') : t('users.createUser')"
      modal
      style="width: 500px"
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

        <div class="field-checkbox">
          <Checkbox
            id="user-active"
            v-model="formData.active"
            :binary="true"
          />
          <label for="user-active">{{ t('profile.active') }}</label>
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
import { useConfirm } from 'primevue/useconfirm'
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
import type { UserResponse, CreateUserRequest, UpdateUserRequest } from '@/api/generated'

const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()

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
  active: true
})

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
    active: true
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
    active: user.active!
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
        email: formData.value.email
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

function confirmDelete(user: UserResponse) {
  confirm.require({
    message: t('users.confirmDelete'),
    header: t('users.deleteUser'),
    icon: 'pi pi-exclamation-triangle',
    accept: () => handleDelete(user.id!)
  })
}

async function handleDelete(userId: number) {
  try {
    await apiClient.delete(`/api/users/${userId}`)
    toast.add({
      severity: 'success',
      summary: t('users.deleteSuccess'),
      life: 3000
    })
    await loadUsers()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('users.error'),
      life: 3000
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--tt-spacing-md);
  margin-top: var(--tt-card-gap);
}
</style>
