import { ref, type Ref } from 'vue'
import { formatDateISO } from '@/utils/dateTimeUtils'

/**
 * Generic cache composable for storing and retrieving cached data
 * Useful for API responses, computed data, etc.
 */
export function useCache<T>() {
  const cache = ref<Map<string, T>>(new Map())
  const cacheStartDate = ref<string>('')
  const cacheEndDate = ref<string>('')

  /**
   * Get a value from cache by key
   */
  const getFromCache = (key: string): T | undefined => {
    return cache.value.get(key)
  }

  /**
   * Set a value in cache
   */
  const setCache = (key: string, value: T) => {
    cache.value.set(key, value)
  }

  /**
   * Check if a key exists in cache
   */
  const hasCache = (key: string): boolean => {
    return cache.value.has(key)
  }

  /**
   * Remove a specific key from cache
   */
  const removeFromCache = (key: string) => {
    cache.value.delete(key)
  }

  /**
   * Clear all cache
   */
  const clearCache = () => {
    cache.value.clear()
    cacheStartDate.value = ''
    cacheEndDate.value = ''
  }

  /**
   * Get all cached keys
   */
  const getCacheKeys = (): string[] => {
    return Array.from(cache.value.keys())
  }

  /**
   * Get cache size
   */
  const getCacheSize = (): number => {
    return cache.value.size
  }

  /**
   * Check if a date range is fully covered by cache
   */
  const isRangeCached = (startDate: string, endDate: string): boolean => {
    if (!cacheStartDate.value || !cacheEndDate.value) {
      return false
    }
    return startDate >= cacheStartDate.value && endDate <= cacheEndDate.value
  }

  /**
   * Update cache range metadata
   */
  const updateCacheRange = (startDate: string, endDate: string) => {
    if (!cacheStartDate.value || startDate < cacheStartDate.value) {
      cacheStartDate.value = startDate
    }
    if (!cacheEndDate.value || endDate > cacheEndDate.value) {
      cacheEndDate.value = endDate
    }
  }

  /**
   * Get all cached values within a date range
   * Returns all available cached data for the range, even if range isn't fully cached
   */
  const getCachedRange = (startDate: string, endDate: string): T[] => {
    const result: T[] = []
    const currentDate = new Date(startDate)
    const end = new Date(endDate)

    while (currentDate <= end) {
      const dateStr = formatDateISO(currentDate)
      const cached = cache.value.get(dateStr)
      if (cached) {
        result.push(cached)
      }
      currentDate.setDate(currentDate.getDate() + 1)
    }

    return result
  }

  return {
    cache: cache as Readonly<Ref<Map<string, T>>>,
    cacheStartDate,
    cacheEndDate,
    getFromCache,
    setCache,
    hasCache,
    removeFromCache,
    clearCache,
    getCacheKeys,
    getCacheSize,
    isRangeCached,
    updateCacheRange,
    getCachedRange
  }
}
