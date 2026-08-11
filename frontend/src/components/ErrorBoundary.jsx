import { Component } from 'react';
import { AlertOctagon } from 'lucide-react';
import Button from './Button';

export default class ErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('Unhandled UI error:', error, info.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-neutral-50 px-4 text-center">
        <AlertOctagon className="h-10 w-10 text-danger-500" aria-hidden="true" />
        <h1 className="text-lg font-semibold text-neutral-900">Something went wrong</h1>
        <p className="max-w-sm text-sm text-neutral-500">
          An unexpected error occurred. Your vault data is untouched — reloading usually fixes this.
        </p>
        <Button onClick={() => window.location.reload()}>Reload</Button>
      </div>
    );
  }
}
