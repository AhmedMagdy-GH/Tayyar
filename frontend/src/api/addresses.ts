import { mutate, request } from './client'
import type { Address, AddressProfile, Page } from './contracts'

export const addressApi = {
  list() { return request<Page<Address>>('/users/me/addresses?page=0&size=100') },
  create(profile: AddressProfile) { return mutate<Address>('/users/me/addresses', { method: 'POST', body: JSON.stringify(profile) }) },
  edit(id: string, profile: AddressProfile, version: number) { return mutate<Address>(`/users/me/addresses/${id}`, { method: 'PUT', body: JSON.stringify({ profile, version }) }) },
  remove(id: string, version: number) { return mutate<void>(`/users/me/addresses/${id}`, { method: 'DELETE', body: JSON.stringify({ version }) }) },
  setDefault(id: string, version: number) { return mutate<Address>(`/users/me/addresses/${id}/default`, { method: 'PUT', body: JSON.stringify({ version }) }) },
  setZone(id: string, deliveryZoneId: string | null, version: number) { return mutate<Address>(`/users/me/addresses/${id}/delivery-zone`, { method: 'PUT', body: JSON.stringify({ deliveryZoneId, version }) }) },
}
