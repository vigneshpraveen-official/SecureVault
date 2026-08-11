import { lazy, Suspense } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import ProtectedRoute from '../components/ProtectedRoute';
import AdminRoute from '../components/AdminRoute';
import AppLayout from '../components/AppLayout';
import Spinner from '../components/Spinner';

// Route-level code splitting (S6.8) — each page ships in its own chunk, fetched on first
// navigation rather than bundled into the initial load. LoginPage/RegisterPage are NOT lazy —
// they're the very first thing an unauthenticated visitor needs, so splitting them would just
// add a network round trip before anything renders.
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
const DashboardPage = lazy(() => import('../pages/DashboardPage'));
const VaultPage = lazy(() => import('../pages/VaultPage'));
const TrashPage = lazy(() => import('../pages/TrashPage'));
const SharingPage = lazy(() => import('../pages/SharingPage'));
const AdminPage = lazy(() => import('../pages/AdminPage'));
const NotFoundPage = lazy(() => import('../pages/NotFoundPage'));

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <DashboardPage /> },
          { path: '/vault', element: <VaultPage /> },
          { path: '/vault/trash', element: <TrashPage /> },
          { path: '/sharing', element: <SharingPage /> },
          {
            element: <AdminRoute />,
            children: [{ path: '/admin', element: <AdminPage /> }],
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: (
      <Suspense
        fallback={
          <div className="flex min-h-screen items-center justify-center">
            <Spinner />
          </div>
        }
      >
        <NotFoundPage />
      </Suspense>
    ),
  },
]);
