# Top-level content state

Home, library and authorized-folder destinations render session-owned data. Switching
tabs must not recreate an empty data source or replace loaded content with a spinner.

## Ownership

- `RetainedContent` owns one immutable snapshot and its asynchronous refresh. A null
  value means not loaded; a loaded empty list is a real empty result. Refresh preserves
  the previous value. Cancellation, failure and session closure never publish a false
  empty result or a late result from an obsolete request.
- The external session owns the local repository and authorized-folder snapshots for
  the application lifetime, including newly created shell Activities.
- Each open vault has its own session, keyed by the open DroidFS volume object's identity. The
  DroidFS volume-close observer clears snapshots and cancels work. Reopening the same
  vault creates a new session; book snapshots are not saved into instance state.
- Pages receive loaded data and refresh callbacks. They do not own another book list.
  Mutations still use the existing repositories and request a fresh snapshot afterward.
- Compose `SaveableStateHolder` saves per-tab UI state such as scroll/search, while the
  session owns data. Reader, settings and explorer Activity navigation stays separate.
- In the vault shell, system Back always asks for exit confirmation, including when
  the shell was opened from the file explorer. `EXTRA_RETURN_TO_FILES` applies only
  to the explicit Files shortcut, which restores the retained explorer and directory.
- Confirming vault exit always selects external Home and resets the external shell's
  return-to-files flag. The exit destination does not depend on the entry tab.

## Rendering and refresh

Only a successful load may show an empty-library/folder/recent state. First load uses
a neutral static placeholder; subsequent refreshes keep the last loaded content visible.
Home and library derive from the same snapshot, so the recent area cannot temporarily
collapse simply because the user switched tabs. Activities refresh on resume to observe
imports, deletions, reader progress and permission changes made in other flows.

## Verification

- Unit-test unloaded vs loaded-empty, refresh preservation, failed refresh, stale request
  completion and close during load. Test UI-state decisions independently from source text.
- Run CI unit tests, lint and debug/release assembly. Local static checks do not prove
  Android compilation or device rendering.
- On device: switch home/library/settings repeatedly in both modes, switch file pages
  to library/home, verify real empty libraries, and return after import/delete/rename.
- Lock and reopen a vault, switch between two vaults, and verify no stale metadata appears.
- Check tab scroll/search restoration and confirm reader/settings return animations and
  fixed bottom bars retain their accepted behavior.
