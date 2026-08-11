import { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import toast from 'react-hot-toast';
import { dashboardApi } from '../api/dashboard';
import SummaryCards from '../features/dashboard/SummaryCards';
import PasswordHealthCard from '../features/dashboard/PasswordHealthCard';
import RecentActivity from '../features/dashboard/RecentActivity';
import SecurityAlerts from '../features/dashboard/SecurityAlerts';
import CategoryChart from '../features/dashboard/CategoryChart';
import StrengthChart from '../features/dashboard/StrengthChart';

// Every number here comes straight from a dashboard endpoint response — no client-side
// aggregation, recomputation, or caching beyond what the browser naturally does for the
// component's lifetime (S5.7/S6.6: "the server's answer is the one that counts").
export default function DashboardPage() {
  const user = useSelector((state) => state.auth.user);
  const [summary, setSummary] = useState(null);
  const [health, setHealth] = useState(null);
  const [activity, setActivity] = useState(null);
  const [alerts, setAlerts] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      dashboardApi.summary(),
      dashboardApi.passwordHealth(),
      dashboardApi.recentActivity(),
      dashboardApi.alerts(),
    ]).then(([summaryRes, healthRes, activityRes, alertsRes]) => {
      if (cancelled) return;
      if (summaryRes.status === 'fulfilled') setSummary(summaryRes.value);
      if (healthRes.status === 'fulfilled') setHealth(healthRes.value);
      if (activityRes.status === 'fulfilled') setActivity(activityRes.value);
      if (alertsRes.status === 'fulfilled') setAlerts(alertsRes.value);
      setLoading(false);

      // Each panel below already renders a blank/empty-looking state for null data — that's
      // indistinguishable from "genuinely nothing here" unless something also says a request
      // actually failed (S6.8: no async surface may fail silently).
      const failed = [summaryRes, healthRes, activityRes, alertsRes].some((r) => r.status === 'rejected');
      if (failed) toast.error('Some dashboard data could not be loaded.');
    });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold text-neutral-900">Welcome back, {user?.fullName}</h1>
        <p className="text-sm text-neutral-500">Here's what's happening in your vault.</p>
      </div>

      <SummaryCards summary={summary} loading={loading} />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <PasswordHealthCard health={health} loading={loading} />
        <SecurityAlerts alerts={alerts} loading={loading} />
        <CategoryChart byCategory={summary?.byCategory} loading={loading} />
        <StrengthChart health={health} loading={loading} />
      </div>

      <RecentActivity items={activity} loading={loading} />
    </div>
  );
}
