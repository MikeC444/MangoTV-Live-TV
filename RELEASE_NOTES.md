# Release notes

User-facing changes to MangoTV, newest first. This file *is* the release
notes shown in the app's own update banner (tap the "i" info button) --
whatever sits under a version heading here becomes that release's
description, so there's no need to visit GitHub to see what changed.

(For the backend/engineering build log, see CHANGELOG.md instead -- this
file is only ever end-user-facing release notes.)

When cutting a new release: rename `## Unreleased` below to the new
version (e.g. `## 0.1.2`), add a fresh empty `## Unreleased` above it, then
run the release workflow for that version.

## Unreleased

- Movie and TV show detail pages' three-dot menu, and long-pressing a poster anywhere in the app, both now have a working "Mark as watched" option, which switches to a filled checkmark once used
- Movies you finish watching (past ~85%) now get a green checkmark on their poster everywhere it appears (Home, Movies, TV Shows, Genres, Search, and Detail), and are automatically added to My List under a new "Watched" filter -- My List's default view still mixes watched titles in with everything you added yourself. This also runs once against your existing watch history, so movies you'd already finished before this update get picked up too, not just ones you finish from now on
- Trailers now open in the app of your choice (the YouTube app, a browser, whatever you have installed) instead of playing inside MangoTV, which fixes trailers playing sound over a black screen
- Movie detail pages now show the real release date when it's available, instead of just the year
- Settings is now a two-pane layout: pick a category (Account, Addons, Home Rows, Sounds, Subtitles) on the left, its settings show on the right
- Settings tabs are more compact and each one now scrolls as a whole, so long lists like Addons or Subtitle languages show more per screen
- Settings' focus highlight is now white instead of amber
- Settings > Home Rows loads faster with multiple addons installed
- Select a Source now shows results as soon as each addon responds, instead of waiting for the slowest one before showing anything
- The Home page hero now rotates through 10 random movies/TV shows from your whole catalog, picked fresh each time you launch the app, instead of always the same first row's top 10
- Fixed needing to press BACK twice to hide the player controls while the timeline was selected
- Update banner's Update button is now white instead of amber
- Fixed the Close/Open Settings buttons on the update overlays being unselectable
- Allow-installing-updates overlay's Open Settings button is now white instead of orange

## 0.1.1

- Redesigned the Genres tab: genres are now a grid of colorful cards with
  an icon per genre, instead of a plain list

## 0.1.0

- First public release
- Added in-app updates: the app now checks for new versions itself and
  lets you download and install them from a banner, without needing to
  re-sideload from scratch
- Various stability and UI fixes
