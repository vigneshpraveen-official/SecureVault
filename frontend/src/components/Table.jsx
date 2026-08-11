export default function Table({ columns, children, className = '' }) {
  return (
    <div className="overflow-x-auto rounded-sv border border-neutral-200">
      <table className={`w-full min-w-max text-left text-sm ${className}`}>
        <thead className="bg-neutral-50 text-xs uppercase text-neutral-500">
          <tr>
            {columns.map((col) => (
              <th key={col.key} scope="col" className="px-4 py-3 font-medium">
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-neutral-200">{children}</tbody>
      </table>
    </div>
  );
}
