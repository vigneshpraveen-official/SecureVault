export default function Card({ className = '', children, ...props }) {
  return (
    <div
      className={`rounded-sv border border-neutral-200 bg-neutral-0 shadow-sm ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
