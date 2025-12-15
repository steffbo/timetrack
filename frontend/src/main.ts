import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import Tooltip from 'primevue/tooltip'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { OpenAPI } from './api/generated'

// PrimeIcons CSS
import 'primeicons/primeicons.css'
import './style.css'

// Shared styles
import './styles/variables.css'
import './styles/utilities.css'
import './styles/layouts.css'
import './styles/components/action-cards.css'
import './styles/components/data-tables.css'
import './styles/components/forms.css'
import './styles/components/stat-cards.css'

// Storage keys
const ACCESS_TOKEN_KEY = 'timetrack_access_token'

// Configure OpenAPI client to use authentication token
OpenAPI.BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
OpenAPI.TOKEN = () => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  return token || ''
}

const app = createApp(App)

// German locale configuration with Monday as first day of week
const deLocale = {
  startsWith: 'Beginnt mit',
  contains: 'Enthält',
  notContains: 'Enthält nicht',
  endsWith: 'Endet mit',
  equals: 'Gleich',
  notEquals: 'Nicht gleich',
  noFilter: 'Kein Filter',
  lt: 'Kleiner als',
  lte: 'Kleiner oder gleich',
  gt: 'Größer als',
  gte: 'Größer oder gleich',
  dateIs: 'Datum ist',
  dateIsNot: 'Datum ist nicht',
  dateBefore: 'Datum ist vor',
  dateAfter: 'Datum ist nach',
  clear: 'Löschen',
  apply: 'Anwenden',
  matchAll: 'Alles entsprechen',
  matchAny: 'Beliebig entsprechen',
  addRule: 'Regel hinzufügen',
  removeRule: 'Regel entfernen',
  accept: 'Ja',
  reject: 'Nein',
  choose: 'Wählen',
  upload: 'Hochladen',
  cancel: 'Abbrechen',
  dayNames: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],
  dayNamesShort: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
  dayNamesMin: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
  monthNames: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
  monthNamesShort: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
  today: 'Heute',
  weekHeader: 'Woche',
  firstDayOfWeek: 1,
  dateFormat: 'dd.mm.yy',
  weak: 'Schwach',
  medium: 'Mittel',
  strong: 'Stark',
  passwordPrompt: 'Passwort eingeben'
}

app.use(PrimeVue, {
  theme: {
    preset: Aura,
    options: {
      darkModeSelector: false
    }
  },
  locale: deLocale
})
app.use(ToastService)
app.use(ConfirmationService)
app.use(router)
app.use(i18n)
app.directive('tooltip', Tooltip)

app.mount('#app')
