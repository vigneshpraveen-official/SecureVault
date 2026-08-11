import { memo } from 'react';
import { Star, Eye, EyeOff, Copy, Pencil, Trash2, History, Share2 } from 'lucide-react';
import Badge from '../../components/Badge';
import IconButton from '../../components/IconButton';
import FaviconIcon from './FaviconIcon';
import { CATEGORY_VARIANT } from './categories';

function StrengthDot({ score }) {
  const color =
    score <= 1 ? 'bg-red-500' : score === 2 ? 'bg-orange-500' : score === 3 ? 'bg-amber-500' : score === 4 ? 'bg-lime-500' : 'bg-green-500';
  return (
    <span className="flex items-center gap-1">
      <span className={`h-2 w-2 rounded-full ${color}`} aria-hidden="true" />
      <span className="text-xs text-neutral-500">{score}/5</span>
    </span>
  );
}

// Memoized because a single row's reveal/hide toggle would otherwise re-render every row in
// the (up to 20-per-page) list — the parent passes stable useCallback handlers so this memo
// boundary actually skips work instead of comparing new function references every time.
function VaultRow({ cred, isRevealed, revealedPassword, onReveal, onCopy, onHistory, onShare, onEdit, onDelete }) {
  return (
    <tr className="hover:bg-neutral-50">
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <FaviconIcon websiteUrl={cred.websiteUrl} />
          <span className="font-medium text-neutral-900">{cred.title}</span>
          {cred.favorite && <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" aria-label="Favorite" />}
        </div>
      </td>
      <td className="px-4 py-3 text-neutral-600">{cred.username || '—'}</td>
      <td className="px-4 py-3">
        <Badge variant={CATEGORY_VARIANT[cred.category]}>{cred.category}</Badge>
      </td>
      <td className="px-4 py-3">
        <StrengthDot score={cred.strengthScore} />
      </td>
      <td className="px-4 py-3 text-neutral-500">{new Date(cred.updatedAt).toLocaleDateString()}</td>
      <td className="px-4 py-3">
        <div className="flex items-center justify-end gap-1">
          <IconButton
            icon={isRevealed ? EyeOff : Eye}
            label={isRevealed ? 'Hide password' : 'Reveal password (auto-hides in 20s)'}
            onClick={() => onReveal(cred.id)}
          />
          <IconButton icon={Copy} label="Copy password" onClick={() => onCopy(cred.id)} />
          <IconButton icon={History} label="Password history" onClick={() => onHistory(cred)} />
          <IconButton icon={Share2} label="Share credential" onClick={() => onShare(cred)} />
          <IconButton icon={Pencil} label="Edit credential" onClick={() => onEdit(cred)} />
          <IconButton icon={Trash2} label="Delete credential" variant="danger" onClick={() => onDelete(cred)} />
        </div>
        {isRevealed && (
          <div className="mt-1 rounded-sv bg-neutral-100 px-2 py-1 text-right font-mono text-xs text-neutral-800">
            {revealedPassword}
          </div>
        )}
      </td>
    </tr>
  );
}

export default memo(VaultRow);
