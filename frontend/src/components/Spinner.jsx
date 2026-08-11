export default function Spinner({ size = 24, className = '', label = 'Loading' }) {
  return (
    <span
      role="status"
      aria-label={label}
      className={`inline-block animate-spin rounded-full border-2 border-neutral-300 border-t-accent-600 ${className}`}
      style={{ width: size, height: size }}
    />
  );
}
