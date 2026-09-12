import { mutate } from './client'
import type { CheckoutInput, CheckoutSummary } from './contracts'

export const checkoutApi = {
  place(input: CheckoutInput, idempotencyKey: string) {
    return mutate<CheckoutSummary>('/checkout', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(input),
    })
  },
}
