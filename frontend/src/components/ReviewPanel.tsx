import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Star } from 'lucide-react'
import { ApiError } from '../api/client'
import { safeErrorMessage } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import { reviewApi } from '../api/reviews'

export function ReviewPanel({ orderId }: { orderId: string }) {
  const client = useQueryClient()
  const review = useQuery({ queryKey: queryKeys.review(orderId), queryFn: () => reviewApi.byOrder(orderId), retry: false })
  const missing = review.error instanceof ApiError && review.error.status === 404
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [message, setMessage] = useState('')
  useEffect(() => { if (review.data) { setRating(review.data.rating); setComment(review.data.comment ?? '') } }, [review.data])
  const save = useMutation({
    mutationFn: () => review.data ? reviewApi.update(orderId, rating, comment, review.data.version) : reviewApi.create(orderId, rating, comment),
    onSuccess: (next) => { client.setQueryData(queryKeys.review(orderId), next); setMessage(review.data ? 'Your review was updated.' : 'Thanks — your review was submitted.') },
    onError: async (error) => { if (error instanceof ApiError && error.status === 409 && review.data) { await review.refetch(); setMessage('Your review changed elsewhere. We reloaded the latest version; please check it before saving again.') } else setMessage(safeErrorMessage(error, 'Your review could not be saved.')) },
  })
  if (review.isPending) return <section className="review-panel"><p role="status">Loading your review…</p></section>
  if (review.isError && !missing) return <section className="review-panel"><p role="alert">Your review could not be loaded.</p><button type="button" onClick={() => void review.refetch()}>Try again</button></section>
  return <section className="review-panel" aria-labelledby="review-heading"><p className="eyebrow">Your experience</p><h2 id="review-heading">{review.data ? 'Your review' : 'How was your order?'}</h2><p>Rate your delivered order from 1 to 5 stars. A comment is optional.</p>
    <fieldset className="star-fieldset"><legend>Rating</legend><div className="star-picker">{[1, 2, 3, 4, 5].map((value) => <button className={value <= rating ? 'is-selected' : ''} key={value} type="button" aria-label={`${value} star${value === 1 ? '' : 's'}`} aria-pressed={rating === value} onClick={() => setRating(value)}><Star size={24} fill={value <= rating ? 'currentColor' : 'none'} /></button>)}</div></fieldset>
    <label className="review-comment">Comment <span>{comment.length}/2000</span><textarea value={comment} maxLength={2000} onChange={(event) => setComment(event.target.value)} placeholder="Tell us what stood out (optional)" /></label>
    {message && <p className={save.isError ? 'form-error' : 'success-banner'} role="status" aria-live="polite">{message}</p>}
    <button className="primary-button" type="button" disabled={rating < 1 || save.isPending} onClick={() => save.mutate()}>{save.isPending ? 'Saving…' : review.data ? 'Update review' : 'Submit review'}</button>
  </section>
}
