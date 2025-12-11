<template>
  <div class="app-layout">
    <AppNavbar />
    <main class="app-content">
      <RouterView />
    </main>
    <Toast />
    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import AppNavbar from './AppNavbar.vue'
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-content {
  flex: 1;
  padding: 2rem;
  background: var(--p-surface-50);
}
</style>
