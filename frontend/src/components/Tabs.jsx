// Shared by SharingPage and AdminPage (previously two hand-copied, near-identical tab-button
// implementations — consolidated here in the S6.8 consistency pass).
export default function Tabs({ tabs, active, onChange }) {
  return (
    <div className="flex gap-1 overflow-x-auto border-b border-neutral-200">
      {tabs.map((t) => (
        <button
          key={t.key}
          onClick={() => onChange(t.key)}
          className={`whitespace-nowrap px-4 py-2 text-sm font-medium ${
            active === t.key
              ? 'border-b-2 border-accent-600 text-accent-700'
              : 'text-neutral-500 hover:text-neutral-800'
          }`}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
