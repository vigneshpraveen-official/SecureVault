import { useEffect, useState } from 'react';
import { Search, X, ArrowUpDown } from 'lucide-react';
import Input from '../../components/Input';
import Button from '../../components/Button';
import useDebouncedValue from '../../hooks/useDebouncedValue';
import { CATEGORIES, SORT_FIELDS } from './categories';

// The one visible search box maps to the AND-filtered, paginated GET /api/vault?title= (S4.5) —
// not the separate GET /api/vault/search?q= OR-endpoint (M-21), which returns a flat
// non-paginated list. Mixing an unpaginated result set into this paginated table would make the
// "N results" / page controls lie, so that endpoint is deliberately not wired in here.
export default function VaultFilters({ query, onChange }) {
  const [searchInput, setSearchInput] = useState(query.title);
  const debouncedSearch = useDebouncedValue(searchInput, 300);

  useEffect(() => {
    if (debouncedSearch !== query.title) {
      onChange({ title: debouncedSearch, page: 0 });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch]);

  useEffect(() => {
    setSearchInput(query.title);
  }, [query.title]);

  const hasFilters = query.title || query.username || query.website || query.category;

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-end">
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" aria-hidden="true" />
          <Input
            label="Search"
            placeholder="Title..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="vault-category" className="text-sm font-medium text-neutral-700">
            Category
          </label>
          <select
            id="vault-category"
            value={query.category}
            onChange={(e) => onChange({ category: e.target.value, page: 0 })}
            className="rounded-sv border border-neutral-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent-500"
          >
            <option value="">All categories</option>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="vault-sort" className="text-sm font-medium text-neutral-700">
            Sort by
          </label>
          <div className="flex gap-1">
            <select
              id="vault-sort"
              value={query.sortBy}
              onChange={(e) => onChange({ sortBy: e.target.value })}
              className="rounded-sv border border-neutral-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent-500"
            >
              {SORT_FIELDS.map((f) => (
                <option key={f.value} value={f.value}>
                  {f.label}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => onChange({ direction: query.direction === 'asc' ? 'desc' : 'asc' })}
              aria-label={`Sort ${query.direction === 'asc' ? 'descending' : 'ascending'}`}
              title={query.direction === 'asc' ? 'Ascending' : 'Descending'}
              className="rounded-sv border border-neutral-300 px-2 text-neutral-600 hover:bg-neutral-50"
            >
              <ArrowUpDown className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
      {hasFilters && (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => {
            setSearchInput('');
            onChange({ title: '', username: '', website: '', category: '', page: 0 });
          }}
        >
          <X className="h-4 w-4" aria-hidden="true" />
          Clear filters
        </Button>
      )}
    </div>
  );
}
