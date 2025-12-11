import { createI18n } from 'vue-i18n'
import de from './locales/de.json'
import en from './locales/en.json'

const i18n = createI18n({
  legacy: false, // Use Composition API mode
  locale: 'de',  // Default language
  fallbackLocale: 'en',
  messages: {
    de,
    en
  }
})

export default i18n
