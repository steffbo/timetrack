<template>
  <div class="app-layout">
    <AppNavbar />
    <main class="app-content">
      <RouterView />
    </main>
    <UndoDeleteToast />
    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'
import ConfirmDialog from 'primevue/confirmdialog'
import UndoDeleteToast from '@/components/common/UndoDeleteToast.vue'
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
  width: calc(100vw - 4rem); /* Full viewport width minus padding */
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem;
  background: var(--p-surface-50);
  box-sizing: border-box;
}

/* Tablet and smaller - reduce padding */
@media (max-width: 768px) {
  .app-content {
    width: calc(100vw - 2rem);
    padding: 1rem;
  }
}

/* Mobile - minimal padding */
@media (max-width: 480px) {
  .app-content {
    width: 100vw;
    padding: 0.5rem;
  }
}
</style>
