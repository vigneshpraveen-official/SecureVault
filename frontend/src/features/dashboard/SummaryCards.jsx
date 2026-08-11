import { Link } from 'react-router-dom';
import { KeyRound, Star, ArrowDownToLine, ArrowUpFromLine, Trash2 } from 'lucide-react';
import Card from '../../components/Card';
import Skeleton from '../../components/Skeleton';

const CARDS = [
  { key: 'totalCredentials', label: 'Credentials', icon: KeyRound, to: '/vault' },
  { key: 'favoritesCount', label: 'Favorites', icon: Star },
  { key: 'sharedInCount', label: 'Shared with me', icon: ArrowDownToLine, to: '/sharing' },
  { key: 'sharedOutCount', label: 'Shared by me', icon: ArrowUpFromLine, to: '/sharing' },
  { key: 'trashCount', label: 'In trash', icon: Trash2, to: '/vault/trash' },
];

function CardBody({ icon: Icon, label, value }) {
  return (
    <>
      <div className="flex items-center gap-2 text-neutral-500">
        <Icon className="h-4 w-4" aria-hidden="true" />
        <span className="text-sm">{label}</span>
      </div>
      <p className="mt-1 text-2xl font-semibold text-neutral-900">{value}</p>
    </>
  );
}

export default function SummaryCards({ summary, loading }) {
  if (loading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {CARDS.map((c) => (
          <Card key={c.key} className="p-4">
            <Skeleton className="h-4 w-20" />
            <Skeleton className="mt-2 h-7 w-10" />
          </Card>
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
      {CARDS.map(({ key, label, icon, to }) => {
        const value = summary?.[key] ?? 0;
        const body = <CardBody icon={icon} label={label} value={value} />;
        return to ? (
          <Link key={key} to={to}>
            <Card className="p-4 transition-shadow hover:shadow-md">{body}</Card>
          </Link>
        ) : (
          <Card key={key} className="p-4">
            {body}
          </Card>
        );
      })}
    </div>
  );
}
