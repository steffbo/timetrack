import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { OpenAPI } from './api/generated'

// PrimeIcons CSS
import 'primeicons/primeicons.css'
import './style.css'

// Storage keys
const ACCESS_TOKEN_KEY = 'timetrack_access_token'

// Configure OpenAPI client to use authentication token
OpenAPI.BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
OpenAPI.TOKEN = () => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  return token || ''
}

const app = createApp(App)

app.use(PrimeVue, {
  theme: {
    preset: Aura,
    options: {
      darkModeSelector: false
    }
  }
})
app.use(ToastService)
app.use(ConfirmationService)
app.use(router)
app.use(i18n)

app.mount('#app')
