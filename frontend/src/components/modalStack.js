// Tracks nested open modals (e.g. the password generator opened from inside the credential
// form modal) so Escape closes only the top-most one instead of every open modal at once —
// each Modal instance's keydown listener checks "am I the top of the stack?" before acting.
let stack = [];

export function pushModal(id) {
  stack = [...stack.filter((x) => x !== id), id];
}

export function popModal(id) {
  stack = stack.filter((x) => x !== id);
}

export function isTopModal(id) {
  return stack[stack.length - 1] === id;
}
