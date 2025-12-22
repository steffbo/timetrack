<template>
  <Transition name="christmas-fade">
    <div v-if="showGreeting" class="christmas-banner" @click="dismiss">
      <img 
        :src="christmasImage" 
        alt="Frohe Weihnachten, Franzi. Hab dich lieb" 
        class="christmas-image"
      />
    </div>
  </Transition>
</template>

<!-- Regular script block - runs once when module loads, persists across component mounts -->
<script lang="ts">
import { ref } from 'vue'

// Module-level state: persists during SPA navigation, resets on full page reload
const dismissed = ref(false)

export function dismissChristmasGreeting() {
  dismissed.value = true
}

export { dismissed }
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuth } from '@/composables/useAuth'
import christmasImage from '@/assets/xmasegg.jpg'

const { currentUser } = useAuth()

// Christmas time range: December 24, 2025 6pm until January 2, 2026
// Only show for user ID <= 3
const showGreeting = computed(() => {
  if (dismissed.value) return false
  if (!currentUser.value?.id || currentUser.value.id > 3) return false
  
  const now = new Date()
  const start = new Date('2025-12-24T18:00:00')
  const end = new Date('2026-01-02T23:59:59')
  
  return now >= start && now <= end
})

const dismiss = () => {
  dismissed.value = true
}
</script>

<style scoped>
.christmas-banner {
  display: flex;
  justify-content: center;
  margin-bottom: var(--tt-spacing-lg);
  cursor: pointer;
  animation: christmas-appear 0.8s ease-out;
}

.christmas-image {
  max-width: 100%;
  max-height: 300px;
  border-radius: 12px;
  box-shadow: 
    0 0 30px rgba(255, 215, 0, 0.2),
    0 0 60px rgba(255, 100, 100, 0.15),
    0 8px 24px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.christmas-image:hover {
  transform: scale(1.01);
  box-shadow: 
    0 0 40px rgba(255, 215, 0, 0.3),
    0 0 80px rgba(255, 100, 100, 0.2),
    0 12px 32px rgba(0, 0, 0, 0.25);
}

@keyframes christmas-appear {
  0% {
    opacity: 0;
    transform: translateY(-20px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Transition */
.christmas-fade-enter-active {
  transition: opacity 0.5s ease, transform 0.5s ease;
}

.christmas-fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.christmas-fade-enter-from,
.christmas-fade-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
</style>
