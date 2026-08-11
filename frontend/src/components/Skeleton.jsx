export default function Skeleton({ className = '' }) {
  return <div className={`animate-pulse rounded-sv bg-neutral-200 ${className}`} aria-hidden="true" />;
}
