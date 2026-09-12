import { Link } from 'react-router-dom'

export function BrandLogo({ demo = false }: { demo?: boolean }) {
  return (
    <Link className="brand-logo" to={demo ? '/?demo=true' : '/'} aria-label="Tayyar home">
      <span className="brand-logo__mark" aria-hidden="true">
        <svg viewBox="0 0 32 32" role="img"><path d="M4 15.1 27.4 4.4c.8-.4 1.6.4 1.2 1.2L17.9 29c-.4.9-1.7.7-1.9-.2l-1.8-9-9-1.8c-.9-.2-1.1-1.5-.2-1.9Z" fill="currentColor"/><path d="m14.2 19.8 7.4-7.4" stroke="white" strokeWidth="2.2" strokeLinecap="round"/></svg>
      </span>
      <span className="brand-logo__word">tayyar</span>
      <span className="brand-logo__tag">Delivery</span>
    </Link>
  )
}
