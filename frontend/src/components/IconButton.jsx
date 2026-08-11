const VARIANTS = {
  default: 'text-neutral-500 hover:bg-neutral-100 hover:text-neutral-800',
  danger: 'text-neutral-500 hover:bg-danger-500/10 hover:text-danger-600',
};

// The one place every icon-only row action (reveal, copy, edit, delete, share, history,
// revoke...) gets its styling from — consolidates what used to be the same className string
// hand-copied across VaultRow, SharingPage and others (S6.8 consistency pass).
export default function IconButton({ icon: Icon, label, variant = 'default', className = '', ...props }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={`rounded-sv p-1.5 disabled:cursor-not-allowed disabled:opacity-40 ${VARIANTS[variant]} ${className}`}
      {...props}
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
    </button>
  );
}
