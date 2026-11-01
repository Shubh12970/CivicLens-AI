import { api, type LocationSuggestion } from './client'

export type { LocationSuggestion } from './client'

/** Explicit, user-triggered worldwide search through the backend geocoding adapter. */
export async function searchWorldwideLocations(query: string): Promise<LocationSuggestion[]> {
  const clean = query.trim()
  if (clean.length < 3) throw new Error('Enter at least 3 characters to search for a location.')
  return api.searchLocations(clean)
}

/** Reverse-geocode GPS coordinates through the configured backend provider. */
export async function reverseWorldwideLocation(latitude: number, longitude: number): Promise<LocationSuggestion> {
  return api.reverseLocation(latitude, longitude)
}
