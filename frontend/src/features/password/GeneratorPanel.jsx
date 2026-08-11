import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Copy, RefreshCw } from 'lucide-react';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import IconButton from '../../components/IconButton';
import StrengthMeter from '../../components/StrengthMeter';
import { passwordApi } from '../../api/password';

const DEFAULT_CONFIG = {
  length: 16,
  includeUppercase: true,
  includeLowercase: true,
  includeNumbers: true,
  includeSymbols: true,
  excludeAmbiguous: false,
};

const TOGGLES = [
  { key: 'includeUppercase', label: 'Uppercase (A-Z)' },
  { key: 'includeLowercase', label: 'Lowercase (a-z)' },
  { key: 'includeNumbers', label: 'Numbers (0-9)' },
  { key: 'includeSymbols', label: 'Symbols (!@#$...)' },
  { key: 'excludeAmbiguous', label: 'Exclude ambiguous (l, I, 1, O, 0)' },
];

export default function GeneratorPanel({ open, onClose, onUsePassword }) {
  const [config, setConfig] = useState(DEFAULT_CONFIG);
  const [result, setResult] = useState(null);
  const [generating, setGenerating] = useState(false);

  const generate = useCallback(async (cfg) => {
    setGenerating(true);
    try {
      const response = await passwordApi.generate(cfg);
      setResult(response);
    } catch (error) {
      // Object-level validation (e.g. "no character class enabled") comes back under a
      // synthetic field name, not error.message — surface the real reason, not the generic one.
      const detail = error.errors?.[0]?.message ?? error.message;
      toast.error(detail ?? 'Could not generate a password.');
    } finally {
      setGenerating(false);
    }
  }, []);

  useEffect(() => {
    if (open) generate(config);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  function updateConfig(partial) {
    const next = { ...config, ...partial };
    setConfig(next);
    generate(next);
  }

  async function handleCopy() {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.password);
      toast.success('Copied to clipboard.');
    } catch {
      toast.error('Could not copy to clipboard.');
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Password generator"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            onClick={() => {
              onUsePassword(result.password);
              onClose();
            }}
            disabled={!result}
          >
            Use this password
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-2 rounded-sv border border-neutral-200 bg-neutral-50 px-3 py-2">
          <code className="flex-1 overflow-x-auto font-mono text-sm text-neutral-900">
            {generating ? 'Generating…' : (result?.password ?? '')}
          </code>
          <IconButton icon={RefreshCw} label="Regenerate" onClick={() => generate(config)} />
          <IconButton icon={Copy} label="Copy" onClick={handleCopy} />
        </div>

        {result?.strength && <StrengthMeter {...result.strength} />}

        <div className="flex flex-col gap-1">
          <label htmlFor="gen-length" className="flex justify-between text-sm font-medium text-neutral-700">
            <span>Length</span>
            <span>{config.length}</span>
          </label>
          <input
            id="gen-length"
            type="range"
            min={8}
            max={64}
            value={config.length}
            onChange={(e) => updateConfig({ length: Number(e.target.value) })}
            className="accent-accent-600"
          />
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="sr-only">Character classes</legend>
          {TOGGLES.map(({ key, label }) => (
            <label key={key} className="flex items-center gap-2 text-sm text-neutral-700">
              <input
                type="checkbox"
                checked={config[key]}
                onChange={(e) => updateConfig({ [key]: e.target.checked })}
                className="h-4 w-4 rounded accent-accent-600"
              />
              {label}
            </label>
          ))}
        </fieldset>
      </div>
    </Modal>
  );
}
