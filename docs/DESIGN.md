# Whammy — Design System

_Brand + UI source of truth. Read before touching `colors.xml`, `themes.xml`, `dimens.xml`, or any drawable. Everything here is buildable with plain Android SDK (minSdk 26): XML themes/drawables, bundled fonts, and one or two small custom `View`s. Anything genuinely expensive is flagged with a cheap approximation._

---

## 0. What Whammy is

Whammy searches the Encore/Chorus chart database and drops Clone Hero songs (`.sng`) straight into the game's folder. It is essentially one screen — a search bar and a scrolling list of results, each with a download button — plus an empty state, a permission screen, and lightweight feedback. It should feel like it belongs on the same home-screen row as Clone Hero: **flagship-grade, confident, fast.**

---

## 1. Aesthetic direction

**The guiding metaphor is the note highway.** In Clone Hero, notes stream down a dark, receding highway toward a glowing strike line, colored in the five frets. Whammy borrows that frame exactly: **the results list is the highway, and each song is a note traveling down it.** You search, results cascade in from the strike line, and you "strum" a note to grab it.

The name earns its own gesture. A **whammy bar** dives the pitch of a note downward — so the brand's signature motion and its logo are a **pitch-dive**: a luminous line that runs level, then bends and dives. It appears in the wordmark, the app icon, and the download completion.

The five fret colors are the accent language, but they are given a **job** rather than sprinkled around — this single rule is what keeps intense color from becoming noise:

| Role | Colors | Where it lives |
|---|---|---|
| **Identity** (resting) | Fret rainbow — green/red/yellow/blue/orange | The **app icon** and brand mark. Result rows lead with **album art** instead — cover art is the per-row identity; the fret rainbow is the app's signature, not a per-row stripe. |
| **Action** (in-flight) | Star-power cyan → blue | The one interactive accent: search focus, the idle + downloading download button, the strike line, the snackbar edge. |
| **Resolved** (outcome) | Green (done) / red (error) | Reuses the green & red frets, so the palette stays tight. |

So the eye reads a calm, dark highway of album-art rows; exactly one cyan thing is "happening" at a time (action); finished rows settle into green. **Bold in one place, quiet everywhere else.**

**What makes it flagship, not indie:** light that reads as *emitted* (layered surfaces, three-layer glow with soft falloff, a whisper of film grain so the dark is never a flat void), generous negative space, one clear hero moment per screen, and tactile, rewarding components. Restraint is the luxury.

---

## 2. Brand: logo, wordmark, mark

### 2.1 The pitch-dive
The brand's one memorable device. A single luminous line (star-power cyan `#35E6E1`) that travels level, then bends and **dives** downward — guitar-tab notation for a whammy bend. Rendered with the three-layer glow so it reads as neon light, not a drawn stroke. It is the through-line across wordmark, icon, empty state, and the download-complete moment.

### 2.2 Wordmark
`WHAMMY` set in **Archivo Expanded Black**, all caps, tracking **−1.5%** (tight, confident), `text_hi` white. Beneath it, the **pitch-dive line** underscores the word: level under `WHAMM`, then diving under `Y`, with a soft cyan bloom.

```
   W H A M M Y
   ─────────╮
            ╰──▽      ← the pitch-dive: level, then bends down under the final letters
```

- **Lockup clear-space:** minimum padding on all sides equals the cap-height of the `W` (1 cap-height). Nothing intrudes.
- **Minimum size:** 18px cap-height on screen. Below that, use the mark alone.
- **Variants:** (a) _Primary_ — white word + cyan dive on dark. (b) _Mono_ — all `text_hi` white, dive included, no color. (c) _Mark only_ — the W-dive glyph (see 2.3) for tight spaces (avatar, notification, favicon).
- **Don'ts:** don't recolor the word in fret colors, don't add a second accent, don't set it in the normal (non-expanded) width, don't remove the dive.

### 2.3 App mark — "the W-dive"
A **W constructed from a pitch-dive waveform** — two dives forming the W, drawn as one continuous glowing cyan line with a fine chrome bevel and a fret-spectrum rim-light along its lower edge. It reads simultaneously as **W** (whammy) and as a **pitch-dive/tremolo waveform**. This is the app icon centerpiece and the mark-only logo.

### 2.4 App icon (shipped-product quality)
Android adaptive icon, 108dp canvas / 72dp safe zone.
- **Foreground:** the W-dive mark, centered, star-power cyan with chrome bevel + fret-spectrum rim-light and a soft bloom.
- **Background:** the stage gradient (see §3) with a faint horizontal strike line and a barely-there converging note-highway perspective.
- **Monochrome/themed layer:** the W-dive as a single flat silhouette.
- **Alt concept** (if the W reads ambiguous at 48dp): a single note gem on the highway with a cyan pitch-dive tail curving down from it — "a note being whammied down."

---

## 3. Color

Dark theme only — Whammy is never light. Values are opaque unless an alpha is given.

### 3.1 Surface ramp (depth)
A cool near-black ramp with a faint blue tint. Elevation is expressed by stepping **up** this ramp plus a top-edge highlight — not by heavy drop shadows.

| Token | Hex | Role |
|---|---|---|
| `stage_black` | `#0A0B12` | Window base (the void beyond the highway). Status bar. |
| `surface_low` | `#0E1017` | Sunken areas, list background behind rows. |
| `surface` | `#14161F` | Result rows at rest. |
| `surface_hi` | `#1B1E29` | Search field, elevated/pressed row, dialogs. |
| `surface_top` | `#242835` | Menus, snackbar, top-most sheets. |

### 3.2 Edges & light
| Token | Value | Role |
|---|---|---|
| `edge_top` | `#FFFFFF` @ 8% (`#14FFFFFF`) | 1px inner highlight on the **top** edge of cards/fields — the "lit from above" cue. |
| `edge_hair` | `#FFFFFF` @ 5% (`#0DFFFFFF`) | Full card outline / hairline. |
| `edge_shadow` | `#000000` @ 45% (`#73000000`) | Soft ambient beneath elevated surfaces (short, wide, low-alpha). |
| `divider` | gradient `transparent → #FFFFFF14 → transparent` | List dividers that fade at both ends. |

### 3.3 Background (the stage)
The window is **not** a flat fill. Compose three layers, bottom to top:
1. Vertical gradient: `#14161F` (top) → `#0C0D14` (55%) → `stage_black` (bottom) — the highway receding into dark.
2. A soft radial **stage bloom** at top-center: cool light `#1C2A44` @ ~22% fading to transparent within ~60% of width.
3. A subtle **vignette** at the corners: `#000000` @ ~30%.
Plus **film grain** (see §5.3).

### 3.4 Text
| Token | Hex | Role | Contrast on `surface` |
|---|---|---|---|
| `text_hi` | `#F4F6FF` | Titles, primary labels | ~15:1 ✅ |
| `text` | `#AEB4C7` | Artist, body | ~7.9:1 ✅ |
| `text_lo` | `#7E86A0` | Charter credit, hints, meta | ~4.6:1 ✅ (small text) |

### 3.5 Frets — identity + accents
Refined neon: punchy but pulled back from pure primaries so they read on dark and never vibrate.

| Token | Hex | Fret | Doubles as |
|---|---|---|---|
| `fret_green` | `#3CE07B` | 1 · Green | **Success** / done |
| `fret_red` | `#FF4D6A` | 2 · Red | **Error** / failed |
| `fret_yellow` | `#FFD23F` | 3 · Yellow | — |
| `fret_blue` | `#3B9DFF` | 4 · Blue | Star-power gradient partner |
| `fret_orange` | `#FF8A3D` | 5 · Orange | — |

Row → color: `position % 5 → 0 green · 1 red · 2 yellow · 3 blue · 4 orange`.

### 3.6 Action / interactive
| Token | Hex | Role |
|---|---|---|
| `star` | `#35E6E1` | THE interactive accent: focus, progress arc, strike line, snackbar edge, links, the pitch-dive. |
| `star_deep` | `#3B9DFF` | Gradient partner (`star → star_deep` = the star-power sweep). Reuses `fret_blue`. |
| `on_accent` | `#05222A` | Near-black text/icons on top of a filled `star` or `fret_green` surface. |

**Contrast:** all body/label text meets WCAG AA on its surface. Fret colors are fills/strokes/glows, never long-form text; where a fret color carries a short label (charter tag) it is ≥ 4.5:1 on `surface`.

---

## 4. Typography

**One superfamily, two widths** — the disciplined, "design-system" choice. Both are OFL and bundleable (~5 files). The personality comes from the brand system and the wordmark treatment; the type stays refined and legible.

> Upgrade note: this replaces the earlier Chakra Petch pick. Archivo Expanded delivers the wide, technical-premium, esports-flagship feel (think confident all-caps lockups) while normal-width Archivo keeps the dense song list crisp and readable.

### 4.1 Faces
- **Archivo Expanded** (Omnibus-Type, OFL) — display: wordmark, hero headlines, section eyebrows. Wide width = premium, technical, confident. Bundle: **Black (900)**, **SemiBold (600)**.
- **Archivo** (normal width, OFL) — everything else: song titles, artist, meta, buttons, numerals. Bundle: **Regular (400)**, **Medium (500)**, **SemiBold (600)**.
- Fallbacks: `Archivo Expanded → Archivo → sans-serif-condensed → sans-serif` (display); `Archivo → Roboto → sans-serif` (text).
- _Optional data face:_ a mono (e.g. **Azeret Mono**) for the `%` readout gives an instrument-panel precision. Not required — Archivo's tabular figures suffice. Flag: bundling a third family adds ~1 file; skip unless desired.

### 4.2 Scale
| Role | Face / weight | Size | Tracking | Case |
|---|---|---|---|---|
| Wordmark | Archivo Expanded Black | contextual | −1.5% | UPPER |
| Hero headline (empty/permission) | Archivo Expanded SemiBold | 26sp | −1% | Title |
| Section eyebrow | Archivo Expanded Medium | 12sp | +8% | UPPER |
| Song title | Archivo SemiBold | 17sp | −0.5% | Title |
| Artist | Archivo Regular | 14sp | 0 | As-is |
| Charter tag | Archivo Medium | 11sp | +6% | UPPER |
| Download % | Archivo SemiBold (tabular) | 13sp | +0.5% | — |
| Search input | Archivo Regular | 16sp | 0 | As-is |
| Body / hint | Archivo Regular | 14–15sp | 0 | As-is |
| Button label | Archivo SemiBold | 15sp | +2% | As-is |
| Snackbar | Archivo Medium | 14sp | +0.2% | As-is |

Line height: titles 1.15, body 1.35. Song titles never wrap (ellipsize end); artist wraps to 1 line then ellipsizes.

---

## 5. Depth, light & material

The single biggest lever from "indie neon" to "flagship." Three ingredients:

### 5.1 Elevation
Express elevation by (a) stepping up the surface ramp, (b) adding a 1px `edge_top` highlight, and (c) a short, wide, low-alpha `edge_shadow` beneath. Avoid tall hard shadows. Rows are nearly flat (rest on `surface`, +`edge_top`, +`edge_hair` outline); the search field and snackbar sit one ramp-step higher.

### 5.2 The glow system (soft falloff)
Real light has falloff. Every glowing element uses **three stacked layers**, not one ring. For a color `C`:

| Layer | Blur | Alpha | Purpose |
|---|---|---|---|
| Core | ~6px | ~55% | crisp edge |
| Bloom | ~16px | ~28% | body of the light |
| Ambient | ~40px | ~10% | halo bleeding into the dark |

This recipe is used for lane-lights, the focused search ring, the note button, the strike line, and the pitch-dive. In CSS it's a 3-stop `box-shadow`/`drop-shadow`; in Android see §9.

### 5.3 Film grain
A very subtle monochrome grain over `stage_black` keeps the dark from banding or feeling like a flat void.
- **CSS mockup:** a fixed full-screen SVG `feTurbulence` noise as a data-URI, `opacity ~0.045`, `mix-blend-mode: overlay`.
- **Android (cheap):** one small (~120×120) tiled noise PNG drawn over the window background at ~4% alpha (`ImageView` with `tileMode="repeat"`, or a `BitmapShader`). Negligible cost.

### 5.4 Glass / chrome accents
The search field and primary button carry a faint top-to-bottom **sheen**: a 1px `edge_top` highlight plus a subtle lightening in the top ~40% of the fill. Reads as stage light catching glass/chrome. Keep it whisper-quiet.

---

## 6. Spacing, radius, sizing

**4dp base.** Flagship polish = a consistent scale, optical alignment, and generous negative space.

- Scale: `4 · 8 · 12 · 16 · 20 · 24 · 32 · 40`.
- Screen horizontal padding: **20dp** (roomier than a stock list — buys premium air).
- Row: padding **16dp** vertical / **18dp** horizontal; **row gap 12dp**.
- Title → artist gap: **4dp**.
- Radii: `r_sm 12` (chips), `r_md 16` (search field, buttons, snackbar), `r_lg 20` (rows, dialogs), note button + gems = **full circle**. Keep radii unified — no stray values.
- Sizes: result row **min 78dp**; note button **48dp**; album-art thumbnail **56dp** (`r_sm` 12dp, `edge_hair` outline); search field **54dp**; primary button **54dp**.
- Optical alignment: the album thumbnail, title baseline, and note-button center form a clean vertical rhythm; icons are optically centered (not metric-centered) in their touch targets.

---

## 7. Components

### 7.1 App bar
- 56dp, transparent over the stage. A 1px `divider` appears at its base only once the list scrolls under it.
- **Left:** the **Whammy wordmark** (with pitch-dive) at ~20sp cap-height. Clean — no fret dots cluttering the bar (the fret language lives in the list and icon).
- **Right:** a single custom overflow/settings glyph (24-grid, see 7.7), `text_lo`.

### 7.2 Search field — the strike zone
- `surface_hi` fill, `r_md` 16dp, glass sheen (§5.4), `edge_top` + `edge_hair`.
- Leading **search** icon (custom, `text_lo`), placeholder `Search songs or artists` (Archivo Regular 16sp `text_lo`). Trailing **clear** (✕) icon appears when text is present.
- **Focus:** 1.5dp `star` stroke + the three-layer cyan glow (§5.2). Placeholder/label transitions with `ease_standard`.
- **Strike line:** directly beneath, a 2dp rule — a horizontal gradient `transparent → star → transparent` carrying a faint fret-spectrum tint at its center, with cyan bloom. On focus it brightens and a single cyan highlight **sweeps** left→right (`420ms`, `ease_emphasized`). This is where results "strike" and cascade from.

### 7.3 Result row — album art on the highway
```
┌──────────────────────────────────────────────────────────┐
│ ┌────┐  SULTANS OF SWING                          ( ↓ )    │
│ │ ▓▓ │  Dire Straits  ·  CHARTED BY HARMONIX               │
│ └────┘                                                     │
└──────────────────────────────────────────────────────────┘
   ▲                                                  ▲
   album art (rounded thumbnail)                      note button
```
- Background `surface`, `r_lg` 20dp, `edge_top` highlight + `edge_hair` outline, faint `edge_shadow` beneath. **The rounded background must clip cleanly — no square corner box may show behind the radius.**
- **Album art:** a **56dp rounded square** (`r_sm` 12dp) leading the row, `edge_hair` hairline outline. Source: `https://files.enchor.us/{albumArtMd5}.jpg` (from the search result's `albumArtMd5`), loaded async and memory-cached; center-cropped. **Placeholder** while loading / on missing art or fetch failure: a `surface_hi` tile bearing a small centered `text_lo` W-dive (or music-note) glyph — never a broken-image or empty box. This replaces the old fret lane-light: **rows lead with cover art; the fret palette now lives on the app icon and in the note-hit accents, not per-row.**
- **Title:** Archivo SemiBold 17sp `text_hi`, ellipsized.
- **Meta:** artist · charter tag rendered as **one line that ellipsizes as a unit** (artist Archivo Regular 14sp `text`, `·`, then `CHARTED BY XXX` Archivo Medium 11sp +6% `text_lo`) — a long artist name must ellipsize the whole meta line, never hard-clip or squeeze the charter tag to zero width.
- **Badges:** a third line of small badges (7.10) — instruments, video, duration — beneath the meta. Quiet and monochrome; they inform without competing with title/art.
- **Right:** the note button (7.4).
- **Press (whole row):** scale 0.985 + `surface_hi` lift + a soft `star`-tinted ripple, `dur_1` `ease_standard`.

### 7.4 Download button — the note you strum (all states)
A 48dp circle, the most tactile element. One consistent accent (no per-row fret color): idle uses the `star` action accent, resolving to green (done) / red (error).

| State | Look | Motion / easing |
|---|---|---|
| **Idle** | Hollow 2dp ring in **`star`** (the action accent), three-layer cyan glow, a down-chevron glyph. | Gentle idle glow breathe: ambient layer alpha 0.6↔1 over 3s `ease-in-out`. Reduced-motion: static. |
| **Press (strum)** | Ring compresses; glow intensifies. | Scale 1→0.92, `90ms` `ease_standard`; a radial **pluck** ripple in `star` expands out and fades (`240ms` `ease_emphasized`). |
| **Downloading** | Ring becomes a determinate **cyan→blue sweep arc** (`star → star_deep`) from 12 o'clock; faint `edge_hair` track ring behind. Center shows `%` (Archivo SemiBold tabular, `star`). A slow shimmer rotates on the arc. | Arc advances per tick with `ease_emphasized`; cyan bloom breathes softly. Indeterminate = a 90° arc rotating at constant speed. |
| **Done ✓** | Arc snaps to a **filled `fret_green` disc** with a check (`on_accent`). | **The note hit:** button overshoots 1→1.14→1 (`420ms`, spring/`ease_emphasized`); a green radial **bloom** scales 0.6→1.6 and fades (`420ms` `ease_emphasized`); a `star` ring **ripple** expands past the button and fades. Optional light haptic. |
| **Error** | Ring turns `fret_red`, glyph becomes a **retry** (circular arrow); meta line appends `Download failed · tap to retry` in `fret_red`. | Horizontal **shake** ±3px, 2 cycles, `110ms` `ease_exit`. Tap → back to Downloading. |

Build: idle/done/error rings are `<shape oval>` state drawables; the sweep arc is a small custom `View` (`Canvas.drawArc` + `SweepGradient`) — the one thing not expressible as a stock drawable (~40 lines). The hit-bloom is a radial-gradient drawable animated on alpha + scale.

### 7.5 Empty / first-launch state
The screen's hero moment.
- Centered: the large **W-dive mark** (the brand mark), softly bloomed, with a faint fret-spectrum rim. Behind it, a few fret-colored note gems drift slowly down the highway (subtle, respects reduced-motion).
- Headline (Archivo Expanded SemiBold 26sp `text_hi`): `Find your setlist`.
- Sub (Archivo Regular 15sp `text`): `Search the Encore database and drop songs straight into Clone Hero.`
- **No-results variant:** headline `No charts found`, sub `Try a different song or artist name.` — direction, not apology.

### 7.6 Permission screen — all-files access
- Stage background. Centered **folder-with-down-arrow** icon inside a fret-spectrum ring (the five frets sweep around the ring), softly bloomed.
- Headline (Archivo Expanded SemiBold 24sp): `Let Whammy reach your songs`.
- Body (Archivo Regular 15sp `text`): `Whammy needs all-files access to drop .sng charts into your Clone Hero songs folder. It only writes charts you download.`
- **Primary button:** filled `star` with glass sheen + cyan glow, `on_accent` label `Grant access` (Archivo SemiBold 15sp), `r_md`, full-width, 54dp. Press: scale 0.98, glow intensifies.
- **Secondary:** text button `Not now` (`text_lo`).
- Copy is plain and specific about what and why — never salesy.

### 7.7 Custom iconography
One cohesive set, **24×24 grid, 2dp stroke, round caps/joins, consistent optical weight.** No mixed styles.
- `search` — magnifier.
- `download` — down-chevron over a short tray line (the "note drop").
- `check` — done.
- `retry` — circular arrow.
- `alert` — used sparingly for hard errors.
- `clear` — ✕.
- `scan` — two-arrow refresh (the Clone Hero rescan hint).
- `folder-down` — permission.
- `library` — stacked/shelf glyph, the app-bar entry to the Library screen.
- `trash` — delete, on library rows.
- `w-dive` — the brand mark (empty state / logo).
- **Badge/instrument marks** (11sp, same 2dp-stroke family, monochrome): `guitar`, `bass`, `drums`, `keys`, `vocals`, `video` (film), and a `ghl` "6" mark. Simpler than the action icons — read as small chips, not buttons.
Ship as vector drawables; tint via `app:tint` to the contextual color.

### 7.8 Snackbar / toast
- `surface_top` fill, `r_md` 16dp, `edge_top` + `edge_hair`, soft `edge_shadow`. Faint glass sheen.
- **3dp `star` accent bar** on the left edge (the interactive-action rule).
- Label Archivo Medium 14sp `text_hi`; optional action (`UNDO`, `RETRY`) in `star`, Archivo SemiBold +2% UPPER.
- Success example: a small green ✓ + `Added: Sultans of Swing`.
- Enters: slide up 16dp + fade, `240ms` `ease_emphasized` (slight overshoot). Auto-dismiss 3s.

### 7.9 Scan-in-Clone-Hero hint
Slim inline pill (or reuse the snackbar) after a successful download: `scan` icon in `star` + `Scan your library in Clone Hero to see new charts.` (`Scan` emphasized `text_hi`). Auto-dismiss or a small `Got it`.

### 7.10 Song badges (metadata at a glance)
A compact, monochrome badge row on each result — enough to judge a chart without opening it, quiet enough not to fight the title or cover. All badges are `text_lo` on transparent (no fills), 11sp / 2dp-stroke icons, ~6dp apart. Data comes straight from the search result:
- **Instruments** (`notesData.instruments`): small icons for the charted instruments — `guitar`, `bass`, `drums`, `keys`, `vocals` (and `guitarghl` → a "6" GHL mark). Show up to ~4; the presence of the icon is the signal, no counts.
- **Video** (`hasVideoBackground` true): a `video` badge (film glyph) — the "has a background video" cue.
- **Duration** (`song_length` ms → `m:ss`): a text badge, e.g. `5:36`.
- **Pro drums** (`pro_drums`) and **Modchart** (`modchart`): tiny text tags `PRO` / `MOD` only when true, `text_lo` +6% UPPER — secondary, easy to skip.
Never render a badge for an absent/false attribute (no empty slots). On very narrow rows, drop trailing badges before wrapping — the row stays one visual line of chips.

### 7.11 Filters (search refinement)
A single horizontal, scrollable **filter chip** rail directly under the search field (hidden until there is a search context). Chips are `surface_hi`, `r_sm` 12dp, `edge_hair`; **selected** = `star` 1.5dp stroke + faint cyan glow + `text_hi` label; unselected = `text` label.
- **Instrument** (`guitar`/`bass`/`drums`/`keys`/`vocals`): maps to the API body's `instrument` param (server-side). Single-select (the API takes one instrument); a selected instrument also filters which difficulty is meaningful.
- **Has video** (`hasVideoBackground`): client-side filter on the returned page.
- Chips animate selection with `dur_2` `ease_standard`. A `clear` affordance appears when any filter is active. Keep the rail to the essential few — instrument + video — not every field the API exposes.

### 7.12 Library screen (manage downloaded charts)
Opened from the app bar `library` glyph (7.1); its own screen over the stage, with a back affordance returning to Search.
- **Header:** `Your library` (Archivo Expanded SemiBold 22sp `text_hi`) + a count sub (`N charts · 312 MB`, `text_lo`).
- **Rows:** the same album-row rhythm, but the right-side control is a **delete** affordance (a `trash` glyph in `text_lo`, turning `fret_red` on press) instead of the download note. Title = the chart folder/file name; meta = size. No cover fetch (local charts) — show the art placeholder tile, or the embedded `album.png` if trivially available (else placeholder).
- **Delete:** tapping delete asks for a light inline confirm (the row slides to reveal a `fret_red` `Delete` / `Cancel`, or a small confirm snackbar with `UNDO`) — never a blocking system dialog (see the alert-dialog guidance). On confirm, remove the file/folder from `/sdcard/Documents/Clone Hero/Songs` and animate the row out (`dur_3` `ease_exit`), updating the count.
- **Empty state:** `No charts yet` + `Songs you download land here.` with the W-dive mark — mirrors the search empty state.
- **Scan hint:** a persistent slim footer reminding to rescan in Clone Hero after changes (reuse 7.9).

### 7.1b App-bar library entry
The app bar (7.1) carries a single **`library`** glyph at the trailing edge (`text_lo`, 24-grid) that opens the Library screen (7.12). It replaces the generic overflow glyph as the one top-level action; settings, if ever needed, move elsewhere. Keep the bar otherwise clean.

---

## 8. Motion

Named tokens keep motion consistent and cheap. All motion respects `ANIMATOR_DURATION_SCALE` / reduced-motion: under reduced motion, cross-fade instead of translate and drop the breathe/bloom/shimmer.

### 8.1 Tokens
| Token | Value | Use |
|---|---|---|
| `dur_1` | 90ms | press / strum |
| `dur_2` | 160ms | small state changes |
| `dur_3` | 240ms | transitions, snackbar |
| `dur_4` | 420ms | note-hit, list entrance, sweep |
| `ease_emphasized` | `cubic-bezier(.16,1,.3,1)` (expo-out) | entrances, the note hit, sweeps — the "satisfying" curve |
| `ease_standard` | `cubic-bezier(.2,0,0,1)` | most UI moves |
| `ease_exit` | `cubic-bezier(.4,0,1,1)` | dismissals, shake |

### 8.2 Principles
- **One thing moves at a time** in the user's focus. The highway stays calm; the note you touched animates.
- **List entrance:** rows cascade from the strike line — `translateY −16dp → 0` + `alpha 0 → 1`, `dur_4` `ease_emphasized`, staggered ~40ms/row, capped after ~6 rows. (A blur-in is nicer but expensive pre-API-31 — flag; ship alpha+translate only.)
- **Note hit** (download done): the reward moment — overshoot + green bloom + cyan ripple (see 7.4).
- **Search focus:** strike-line brighten + single cyan sweep.
- **Everything else:** press feedback and the idle glow breathe.

---

## 9. Android mapping

Plain Android SDK, minSdk 26. No Compose, no third-party UI libs required (Material3 as the theme base is fine but nothing beyond it is needed).

### `res/values/colors.xml`
Every §3 token (`stage_black`, `surface`/`surface_low`/`surface_hi`/`surface_top`, `edge_top`=`#14FFFFFF` etc., text tiers, `fret_*`, `star`, `star_deep`, `on_accent`).

### `res/values/themes.xml`
- Dark-only theme (extend `Theme.Material3.Dark.NoActionBar` or a hand-rolled AppCompat dark base). **No `values-night` variant.**
- `colorPrimary`=`star`, `colorSurface`=`surface`, `android:colorBackground`=`stage_black`, `colorError`=`fret_red`.
- `android:statusBarColor`=`@android:color/transparent` (draw behind), `windowLightStatusBar=false`.
- `android:windowBackground` = the layered stage drawable.

### `res/font/`
`archivo_expanded_black.ttf`, `archivo_expanded_semibold.ttf`, `archivo_regular.ttf`, `archivo_medium.ttf`, `archivo_semibold.ttf`. Two family XMLs (`font_display`, `font_text`) or per-view `fontFamily`. Text appearances in `styles.xml` per §4.2.

### `res/values/dimens.xml`
Spacing scale, radii, row/button/lane/search sizes from §6.

### Drawables & techniques
| File | Technique |
|---|---|
| `bg_stage.xml` | `<layer-list>`: vertical `<gradient>` + a radial `<gradient type="radial">` bloom item (top-center) + a vignette item. Window background. |
| Grain | Small tiled noise PNG over the window at ~4% alpha (§5.3). |
| `bg_row.xml` | `<layer-list>`: `<shape r_lg>` solid `surface` + top inset 1px `edge_top` line + `<stroke>` `edge_hair`. |
| `lane_light.xml` | `<shape>` rounded 3dp with vertical `<gradient>` (fret→dimmer). Glow via colored elevation + a radial-halo layer. Tint per fret at runtime. |
| `bg_search.xml` | `<layer-list>`: `<shape r_md>` `surface_hi` + top sheen gradient item + `edge_top` + `<stroke>` swapped to `star` on focus via `selector`. |
| `strike_line.xml` | `<shape>` horizontal gradient `transparent→star→transparent`; 2dp View; colored elevation for bloom. |
| `note_ring_*.xml` / `note_done.xml` / `note_error.xml` | `<shape oval>` stroke/solid state drawables. |
| Sweep arc | **Custom `View`** — `Canvas.drawArc` + `SweepGradient(star, star_deep)`. ~40 lines. The only non-stock piece. |
| `note_bloom.xml` | `<shape oval><gradient type="radial">` color→transparent; animated alpha+scale for the hit. |
| `btn_primary.xml` | `<shape r_md>` `star` + sheen + colored elevation for glow. |
| `bg_snackbar.xml` | `<shape r_md>` `surface_top` + left `star` bar via `<layer-list>`. |
| Icons | Vector drawables on a 24 grid, 2dp stroke; `app:tint` contextual. |

### Glow without blur (cheap)
- **Preferred:** colored elevation shadows — set `android:outlineSpotShadowColor` / `outlineAmbientShadowColor` to the fret/`star` color on the note button, lane-light, strike line, and primary button (API 28+). A low-elevation colored spot shadow reads as the bloom for free.
- **Fallback (API 26+):** the radial-gradient halo drawables placed behind elements with padding (approximates the bloom + ambient layers).
- **Optional (API 31+):** `RenderEffect.createBlurEffect` for a truer bloom — an enhancement only, never required.

### Flagged as slightly more effort (still cheap)
- Sweep-arc custom `View` (~40 lines).
- Note-hit bloom (animate a radial drawable's alpha + scale).
- Staggered list entrance (per-holder start delay).
- Film grain (one tiled PNG).
Everything else is stock XML drawables, styles, `RecyclerView`.

---

## 10. Quick reference — the one rule

> **Fret colors = who a row is. Cyan = what's happening. Green/red = how it ended.**
> Keep the highway dark and calm; light should feel emitted, not painted; let only the note you touched glow.
