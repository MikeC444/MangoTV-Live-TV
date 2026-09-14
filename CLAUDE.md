# Documentation upkeep

This repo keeps two logs. Update them as a normal part of finishing any
task that changes the app or backend -- don't wait to be asked, and don't
skip it because the change feels small.

## RELEASE_NOTES.md -- end-user facing

- One bullet per user-visible change, added under the `## Unreleased`
  heading at the top of the file.
- Write it for the person using the app, not for another engineer: plain
  language, no file names, no internal implementation detail.
- Cutting a release (rename `## Unreleased` to the version, e.g.
  `## 0.1.3`, and start a fresh empty `## Unreleased` above it) is a
  separate, explicit request -- only do it when actually asked to cut a
  release, never as a side effect of an ordinary change.

## CHANGELOG.md -- engineering / development log

- One entry per unit of work, appended at the end of the file as
  `## Post-Milestone-N -- <short title>` (increment N from whatever the
  last entry already there is).
- Follow the shape already used throughout the file: **Status**,
  **Context**, **Changes**, **Tests performed**, **Issues discovered**,
  **Issues fixed**.
- This sandbox typically has no route to `dl.google.com`, so a real
  Gradle/Android build usually isn't possible here. Say that plainly in
  "Tests performed" rather than implying a build was verified when it
  wasn't -- state exactly what actually was checked (a manual re-read, a
  structural brace/paren balance check, etc.) alongside what wasn't.

## Don't log your own mid-work corrections as separate entries

If getting a request right takes more than one commit -- a bug in your own
just-written code, a "actually also do X" follow-up refinement to the same
still-unshipped thing, a rename because the name you picked minutes ago
turned out misleading -- that is not a second, separate change. It's still
the one change the user asked for, still in progress.

- Before adding a new RELEASE_NOTES.md bullet or CHANGELOG.md entry, check
  whether the current `## Unreleased` section (RELEASE_NOTES) or the most
  recent Post-Milestone entry (CHANGELOG) already describes the exact
  feature/area you're touching right now, from earlier in this same task
  or a very recent one that hasn't shipped yet.
- If yes: **update that existing bullet/entry in place** so it describes
  the final, complete behavior. Don't leave the earlier, now-superseded
  wording sitting next to a new bullet that describes the fix on top of
  it.
- If no (a genuinely distinct request, not a refinement of something
  already logged): add a new bullet/entry as normal.
- Rule of thumb: someone reading RELEASE_NOTES.md later should see what a
  feature does now, not a play-by-play of how you arrived there.

**Worked example, from this repo's actual history:** a user asked for a
"mark as watched" button; it got built and a release-notes bullet written
for it. In the same conversation, the user then asked to also let people
undo that, and to also add the same action to a poster's long-press menu.
Both are refinements of the exact same not-yet-released feature, so the
right move was updating the one existing bullet to describe the final,
complete behavior (works from two places in the app, and is a toggle) --
not appending "also added a way to undo it" and "also added it to the
long-press menu" as two more bullets.

A genuinely new, separate request in that same conversation (e.g. "also
make My List a grid instead of a row") still got its own bullet. This rule
is about not fragmenting one requested change into a trail of your own
fix-up commits -- it isn't about batching everything a user asks for into
a single line.
