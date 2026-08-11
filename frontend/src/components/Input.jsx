import { useId } from 'react';

export default function Input({ label, error, hint, className = '', id, ...props }) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorId = error ? `${inputId}-error` : undefined;
  const hintId = hint ? `${inputId}-hint` : undefined;

  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-neutral-700">
          {label}
        </label>
      )}
      <input
        id={inputId}
        className={`rounded-sv border px-3 py-2 text-sm text-neutral-900 placeholder:text-neutral-400
          focus:outline-none focus:ring-2 focus:ring-accent-500 focus:border-accent-500
          ${error ? 'border-danger-500' : 'border-neutral-300'} ${className}`}
        aria-invalid={Boolean(error)}
        aria-describedby={[errorId, hintId].filter(Boolean).join(' ') || undefined}
        {...props}
      />
      {hint && !error && (
        <p id={hintId} className="text-xs text-neutral-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs text-danger-600">
          {error}
        </p>
      )}
    </div>
  );
}
