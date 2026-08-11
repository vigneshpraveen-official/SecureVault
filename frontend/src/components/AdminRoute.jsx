import { useSelector } from 'react-redux';
import { Navigate, Outlet } from 'react-router-dom';

export default function AdminRoute() {
  const isAuthenticated = useSelector((state) => state.auth.isAuthenticated);
  const role = useSelector((state) => state.auth.user?.role);

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  // Route-hiding is UX only — the API's own @PreAuthorize (ADR-025) is the real boundary.
  if (role !== 'ADMIN') return <Navigate to="/" replace />;
  return <Outlet />;
}
