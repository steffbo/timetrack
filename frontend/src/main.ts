import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { OpenAPI } from './api/generated'
import { getAccessToken } from './api/client'

// PrimeIcons CSS
import 'primeicons/primeicons.css'
import './style.css'

// Configure OpenAPI client to use authentication token
OpenAPI.BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
OpenAPI.TOKEN = () => {
  const token = getAccessToken()
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
