export function formatMoney(value: number | string, currency = 'EGP') {
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) return `— ${currency}`
  return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(numeric)} ${currency}`
}
