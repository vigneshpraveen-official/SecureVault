import { useState } from 'react';
import { Globe } from 'lucide-react';

// Deliberately does NOT use a third-party favicon aggregator (e.g. Google's s2/favicons) —
// that would leak every saved site's domain to a third party on every render, which is an odd
// privacy trade to bake into a password manager. Instead requests {origin}/favicon.ico directly
// from the site itself; many sites don't serve one there, so a broken load silently falls back
// to a generic globe glyph rather than a broken-image icon.
export default function FaviconIcon({ websiteUrl }) {
  const [failed, setFailed] = useState(false);
  let origin = null;
  try {
    origin = websiteUrl ? new URL(websiteUrl).origin : null;
  } catch {
    origin = null;
  }

  if (!origin || failed) {
    return <Globe className="h-4 w-4 text-neutral-400" aria-hidden="true" />;
  }

  return (
    <img
      src={`${origin}/favicon.ico`}
      alt=""
      className="h-4 w-4 rounded-sm"
      onError={() => setFailed(true)}
    />
  );
}
