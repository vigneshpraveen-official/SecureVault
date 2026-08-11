// Converts the backend's `errors: [{ field, message }]` array (docs/ai/CONVENTIONS.md §API
// contract) into a { field: message } map so form components can look up errors by field name
// without inventing a second error shape.
export function fieldErrorsFrom(apiError) {
  const map = {};
  for (const { field, message } of apiError?.errors ?? []) {
    // A field can fail more than one constraint (e.g. password: length AND complexity) —
    // concatenate rather than overwrite, so the last error found doesn't hide the others.
    map[field] = map[field] ? `${map[field]} ${message}` : message;
  }
  return map;
}
