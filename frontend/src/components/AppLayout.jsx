import { Suspense } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { LayoutDashboard, KeyRound, Share2, ShieldCheck, LogOut, ShieldAlert } from 'lucide-react';
import { logoutUser } from '../features/auth/authSlice';
import SessionExpiryWarning from '../features/auth/SessionExpiryWarning';
import Button from './Button';
import Spinner from './Spinner';

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/vault', label: 'Vault', icon: KeyRound },
  { to: '/sharing', label: 'Sharing', icon: Share2 },
];

function navClass({ isActive }) {
  return `flex items-center gap-1.5 rounded-sv px-3 py-1.5 text-sm font-medium ${
    isActive ? 'bg-accent-100 text-accent-700' : 'text-neutral-600 hover:bg-neutral-100'
  }`;
}

export default function AppLayout() {
  const dispatch = useDispatch();
  const user = useSelector((state) => state.auth.user);
  const items = user?.role === 'ADMIN' ? [...NAV_ITEMS, { to: '/admin', label: 'Admin', icon: ShieldAlert }] : NAV_ITEMS;

  return (
    <div className="min-h-screen bg-neutral-50">
      <SessionExpiryWarning />
      <header className="border-b border-neutral-200 bg-neutral-0">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-2 text-lg font-semibold text-neutral-900">
            <ShieldCheck className="h-5 w-5 text-accent-600" aria-hidden="true" />
            SecureVault
          </div>
          <nav className="hidden gap-1 sm:flex" aria-label="Primary">
            {items.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} className={navClass}>
                <Icon className="h-4 w-4" aria-hidden="true" />
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-neutral-600 sm:inline">{user?.fullName}</span>
            <Button variant="ghost" size="sm" onClick={() => dispatch(logoutUser())}>
              <LogOut className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Log out</span>
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 pb-20 sm:pb-6">
        <Suspense
          fallback={
            <div className="flex justify-center py-16">
              <Spinner />
            </div>
          }
        >
          <Outlet />
        </Suspense>
      </main>

      {/* Bottom tab bar below sm — stays reachable with one thumb, unlike a hamburger that
          hides navigation behind an extra tap on the smallest screens (S6.8). */}
      <nav
        aria-label="Primary"
        className="fixed inset-x-0 bottom-0 z-40 flex border-t border-neutral-200 bg-neutral-0 sm:hidden"
      >
        {items.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2 text-xs font-medium ${
                isActive ? 'text-accent-700' : 'text-neutral-500'
              }`
            }
          >
            <Icon className="h-5 w-5" aria-hidden="true" />
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
