# Product Requirements Document (PRD): OpenT9 Core Native Keyboard (v3.0)

**Document Identifier:** `prd.md`  
**Package Identifier:** `com.opent9.keyboard` (`versionCode = 2`, `versionName = "3.0.2"`, `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`)  
**Architecture Class:** Native Hybrid Systems Architecture (Thin Kotlin IME Wrapper + C++20 Core via NDK + Zero-Allocation Hardware Canvas)  
**Target Benchmarks:** Microsoft SwiftKey, Gboard (3×4 Mode), Samsung Keyboard (3×4 Mode)

---

## 1. Executive Summary & Core Value Proposition

OpenT9 Core delivers an ultra-lightweight, low-latency Android T9 predictive keyboard that optimizes memory footprint, cold-start latency, frame rendering consistency, and typing disambiguation speed.

### Core Differentiators

- **Sub-1ms Predictive Disambiguation:** C++20 T9-digit-indexed minimal DAFSA (`OPENT9D2`) traversal + multi-hypothesis spatial beam search (`T9SingleEngine` / bilingual `DawgEngine`) with pre-cached prefix completions (`depth <= 6`), best-first subtree search, and overtype/adjacent-key fallback.
- **Deterministic Fallback (Multi-Tap ABC Mode):** Instant toggling between predictive T9 and classic lowercase-first multi-tap (`MultiTapEngine` + `MultiTapController`) for unlisted acronyms, passwords, and custom codes.
- **Retrospective Word Correction & Orthography Post-Processing:** Cursor/selection inspection (`WordCorrectionController` + `CursorSelectionCoordinator`) for post-commit correction, English contraction restoration (`EnglishOrthography`), Indonesian productive suffix compounding (`IndonesianOrthography`), and phonotactic character-bigram fallback (`SuggestionGuard`).
- **Contextual & Idle Next-Word Prediction (NWP):** Bigram-backed predictions (`NgramEngine` + `OPENT9N1` + `DynamicStore` personal bigrams + `PredictionContextHelper`) driving both contextual T9 disambiguation and idle next-word chips.
- **Bilingual Code-Switching:** Optional simultaneous `ID + EN` scoring (`DawgEngine` primary/secondary merge with `SECONDARY_LANG_PENALTY = 0.55f`) without switching languages.
- **Dynamic Auto-Fit Swipeable Suggestion Strip:** Horizontally scrollable ~40dp candidate bar eliminating rigid candidate pagination, with long-press suggestion removal/blacklisting (`CandidateDialogHelper`).
- **Polymorphic System Keys:** State-machine-driven implementations for Key ↵ (ENTER), Key ⇧ (SHIFT), and Key ⌫ (DEL) adapting across `EditorInfo` configurations.
- **Single-Tag Language Toggle with Active T9 Glow Bar:** Minimalist visual display showing active language code (`EN` or `ID`) with an illuminated indicator bar when T9 prediction is active.
- **Continuous Spacebar Trackpad Scrubbing & Customizable Utility Layout:** Horizontal cursor positioning from Key 0 (`SpaceScrubTracker`), plus interactive sliding-tile rearrangement of Row 4 & Column 4 (`RearrangeLayoutActivity` + `RearrangeKeyboardView`).
- **Adaptive Ergonomics & Themes:** Follow-System / Light / Dark themes (`KeyboardTheme`), One-Handed mode (Left/Right 85% width), landscape height clamping, and tablet portrait centering (`KeyboardDimensionCalculator`).
- **100% Offline Privacy:** Zero network permissions (`android.permission.INTERNET` strictly omitted; only `android.permission.VIBRATE` declared).

---

## 2. Technical Stack & Architectural Constraints

> **Architectural Invariant:** Declarative UI frameworks (Jetpack Compose, Flutter) and nested XML view hierarchies are strictly prohibited in the core typing loop (`T9KeyboardView` uses a single hardware-accelerated `Canvas`).

| Layer | Technology Selection (Current Codebase) | Notes & Implementation Details |
| :--- | :--- | :--- |
| **IME Service Wrapper** | Kotlin (`InputMethodService`) + modular coordinators | `OpenT9InputMethodService` delegates lifecycle & actions to `T9ComposingCoordinator`, `KeyboardActionDispatcher`, `CursorSelectionCoordinator`, `ImeLifecycleConfigurator`, `LanguageCycleHelper`, `CandidateRemovalHelper`, and `EmojiActionDispatcher`. |
| **Prediction & Scoring Core** | C++20 (Android NDK `26.1.10909125` via CMake `3.22.1`) | `DawgEngine` coordinates `T9SingleEngine` (`primary` + optional `secondary`) with `SpatialScorer`, Unified Log-Linear scoring, pre-cached/best-first completions, overtype/adjacent fallback, and $O(1)$ stroke stack rollback (`popStroke()`). |
| **Static Lexicons & Bigrams** | Memory-Mapped Files (`mmap`) + `OPENT9D2` DAFSA + `OPENT9N1` Bigram Tables | `en_lexicon.dawg` (`9,565,384 B` / ~9.12 MiB), `id_lexicon.dawg` (`11,395,327 B` / ~10.87 MiB), `en_bigrams.bin` (`9,200 B`), and `id_bigrams.bin` (`9,728 B`) mapped read-only via `openFd` + `mmap(PROT_READ, MAP_SHARED)` (`noCompress` in `app/build.gradle.kts`), with fallback extraction to `filesDir` in `LexiconLoader`. |
| **Dynamic Vocabulary** | Flat Binary Store (`opent9_vocab.dat` + `opent9_vocab.dat.bigrams` via `DynamicStore`) + `LearnedWordsRepository` | Thread-safe (`std::shared_mutex` + `ioMutex`) unigram & personal bigram store (max `4096` unigrams + `4096` bigrams, `MAX_STORE_WORD_LEN = 48`) with in-memory T9 prefix index (`MAX_INDEXED_PREFIX_LEN = 12`), exponential half-life decay, suppression blacklist (`is_deleted`), atomic `.tmp` + `rename` writes, and `1500ms` debounced off-UI-thread `OpenT9-StoreFlush` persistence. |
| **UI & Render Pipeline** | Single Custom View (`Canvas.onDraw`) | Hardware-accelerated canvas backed by pre-allocated `RectF`, `Path`, `Paint`, and primitive arrays across `T9KeyboardView` and dedicated renderers (`SuggestionStripRenderer`, `Page0KeypadRenderer`, `Page1NumSymRenderer`, `Page23SymbolsRenderer`, `EmojiPageRenderer`). |
| **JNI Bridge** | Direct `ByteBuffer` & Primitives (`NativeEngineBridge` ↔ `jni_bridge.cpp`) | Guarded by `std::shared_mutex g_engineMutex`; uses direct `ByteBuffer` (`2048 B` `candidateBuffer`, `16 B` `multiTapBuffer`) and maintains JVM in-memory fallback state when native library is not loaded (for JVM unit tests). |
| **Audio & Haptic Feedback** | Google Oboe `1.9.0` C++ (`AudioEngine`) + `KeyboardFeedbackManager` | Pre-synthesized 48kHz Mono Float PCM click waveforms (`Mechanical Classic` ~12ms, `Modern Soft` ~10ms, `Minimalist Click` ~5ms) over `LowLatency` shared Oboe stream (with `AudioManager.FX_KEY_CLICK` fallback if native is unloaded) + `Vibrator`/`VibratorManager` one-shot haptics (`1–100ms`). |
| **Configuration Sync** | `PreferenceFragmentCompat` + `SettingsObserver` → JNI | `SharedPreferences` listener synchronizing `NativeConfig` primitives, audio config, bilingual mode, layout order, and theme changes in real time. |

---

## 3. UI Layout & Keypad Specifications

Keyboard dimensions are computed dynamically in `KeyboardDimensionCalculator`:
- **Portrait Height:** User-configurable `220dp–320dp` (default `260dp`), clamped to `max(220dp, 45% of screenHeightDp)` when `screenHeightDp > 100`.
- **Landscape Height:** Scaled from `prefHeightDp * (maxLandscapeHeightDp / 260f)` where `maxLandscapeHeightDp = 48% of screenHeightDp` (or `180dp` fallback), clamped to `[140dp, max(160dp, maxLandscapeHeightDp)]`.
- **Ergonomic Width, Landscape Compact + Panel & Offset:**
  - `one_handed_mode == 1` (Left-Handed): `offsetX = 0`, `contentWidth = 85%` of view width.
  - `one_handed_mode == 2` (Right-Handed): `offsetX = 15%`, `contentWidth = 85%` of view width.
  - Landscape Compact + Utility Panel (`isLandscape && one_handed_mode == 0 && widthDp >= 520` when `landscape_layout_mode != "full"`): constrains Page 0 & Page 1 keypad to `compactWidth = clamp(min(0.56 * width, 440dp), 300dp, 0.62 * width)` and renders `LandscapePanelLayout` / `LandscapePanelRenderer` (Undo/Redo, Select All, 4-way Cursor D-Pad, Home/End, Copy/Cut/Paste, Swap Dock Side `⇄`, Emoji, Hide IME — or split Left/Right gutters in `"center"` mode), while Page 2/3 Symbols and Page 4 Emoji expand to full landscape width (`landscape_layout_mode`: `"left"`, `"right"`, `"center"`, `"full"`).
  - Large-screen portrait (`widthDp > 600` and not landscape): centered with `contentWidth = min(width, 480dp)`.
  - Default: full width (`offsetX = 0`, `contentWidth = width`).

### 3.0. Page 0: Primary T9 Text & Prediction Layer (`PAGE_0_TEXT`)

Row 4 & Column 4 utility keys are customizable via `RearrangeLayoutActivity` (`KeyboardLayoutConfig` using 7 track slots across Col 3 Rows 0–3 and Row 3 Cols 2–0). In the default order (`DEL, SHIFT, ENTER, LANG, SPACE, PAGE`), `SPACE` spans **2 blocks** and legacy Key 15 is hidden (`bounds` zeroed and skipped in rendering/touch):

```text
+-------------------------------------------------------------------------+
| [T9] | selamat   | selalu   | selain   | seluler   | ...                |  <-- Swipeable Strip (~40dp)
+-------------------------------------------------------------------------+
| [ .,?!'@# ¹ ] | [   ABC ²   ] | [   DEF ³   ] | [   ⌫ DEL   ] |  <-- Row 1
+---------------+---------------+---------------+---------------+
| [   GHI ⁴   ] | [   JKL ⁵   ] | [   MNO ⁶   ] | [  ⇧ SHIFT  ] |  <-- Row 2
+---------------+---------------+---------------+---------------+
| [  PQRS ⁷   ] | [   TUV ⁸   ] | [  WXYZ ⁹   ] | [  ↵ ENTER  ] |  <-- Row 3
+---------------+---------------+---------------+---------------+
| [   ?123    ] | [      0 ␣ SPACE (2 blk)    ] | [  EN / 😊  ] |  <-- Row 4
|               |                               | [ ========= ] |
+-----------------------------------------------+---------------+
```

#### Suggestion & Candidate Strip Layout
- **Strip Height:** `(height * 40 / 260).coerceIn(30dp, 48dp)`.
- **Fixed Left Pill (`12%` Content Width, clamped `[38dp, 56dp]`):** Displays `[ T9 ]` or `[ ABC ]`. Tapping toggles Predictive T9 vs. Literal Multi-Tap (disabled on password and numeric fields).
- **Scrollable Candidate Viewport (88% Content Width):**
  - Renders up to `16` candidates left-to-right; `#1` candidate is highlighted with `candidatePrefixPaint` **only** during active composing or word correction (`!isIdlePrediction`), while Idle NWP chips render uniformly in `candidateTextPaint`.
  - Candidate widths measured via `max(candidateTextPaint.measureText(word) + 24dp, 64dp)`.
  - Horizontal drag pans candidates (`CandidateStripGestureTracker`); tapping a candidate commits word correction (if active), idle NWP word (if `isIdlePrediction && !hasActiveComposing`), or composing candidate (via `commitCandidateIndex` with `auto_space` setting). Long-pressing opens removal/blacklist confirmation dialog (`CandidateDialogHelper`).
- **Key & Candidate Case Formatting (`CandidateMeasurementHelper`):**
  - `DIGIT_T9` key labels display uppercase when `shiftState == 2` (`UPPERCASE`), or when `shiftState == 1` (`TITLECASE`) and `(candidates.isEmpty() || isIdlePrediction)`.
  - Candidate text preserves case-matched output when `isWordCorrectionActive == true`, otherwise applies `shiftState` (`1` = Titlecase, `2` = Uppercase, `0` = lowercase preserving `"I"` and `"I'..."`).

#### Language & T9 Glow Indicator (`LANG_SWITCH` Key, `id = 13`)
- **Display Text:** Active language code (`EN` or `ID`) with `😊` sub-label in the upper-right corner.
- **Visual Glow Bar:** Horizontal bar beneath the language text (`width = min(28dp, 60% key width)`, `height = 3dp`, `radius = 1.5dp`) drawn with `indicatorGlowPaint` when `isT9Mode == true`, omitted in `ABC` Multi-Tap mode.
- **Interactions:**
  - **Primary Tap / Flick Up:** Commits/flushes active multi-tap, hot-swaps language (`EN` $\leftrightarrow$ `ID`) via `LanguageCycleHelper` (replaying `currentComposingStrokes` if actively composing, or refreshing idle NWP if idle).
  - **Flick Down:** Switches to Emoji Picker (`PAGE_4_EMOJI`).
  - **Long Press:** Toggles T9 / ABC mode (`toggleT9Mode()`).

#### Complete Page 0 Key Interaction & Gesture Matrix (Implemented)

| Key | Primary Tap | Flick Up | Flick Down | Flick Left | Flick Right | Long Press |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ 1 .,?!'@# ]** (`id=0`) | Commit active T9 (no space), then multi-tap cycle (`.`, `,`, `?`, `!`, `'`, `@`, `#`, `1`) | — | Switch to Page 1 (`PAGE_1_NUM_SYM`) | Insert `,` | Insert `.` | Commit active composing/multi-tap + insert literal `1` |
| **[ 2 ABC ]–[ 9 WXYZ ]** | Push `(digit, touchX, touchY)` to T9 (if T9 & non-password) / Multi-tap char | — | — | — | — | Commit active composing/multi-tap + insert literal digit |
| **[ ⌫ DEL ]** (`id=3`) | Clear word correction + delete char, dismiss active multi-tap, pop T9 stroke, or delete char / UTF-16 surrogate (`KEYCODE_DEL` if empty) | Abort composing (if active) or delete preceding word | Abort composing (if active) + clear entire field | Abort composing (if active) or delete preceding word | — | Continuous repeat delete every `50ms` (switches to whole-word delete after `1200ms`) |
| **[ ⇧ SHIFT ]** (`id=7`) | Cycle Lower $\to$ Title $\to$ Upper (double-tap $\le 350\text{ms}$ locks Caps; unlocks Caps back to Lower); refreshes active composing display | Lock `UPPERCASE` (Caps Lock) | — | — | — | Lock `UPPERCASE` (Caps Lock) |
| **[ ↵ ENTER ]** (`id=11`) | Commit active multi-tap; commit active T9 (no space) + `EnterKeyHandler` (`performEditorAction` / `"\n"` / `KEYCODE_ENTER`) | Force submit (`effectiveAction` / `IME_ACTION_DONE` / `KEYCODE_ENTER`) | Insert Tab (`\t`) | — | — | Commit active composing/multi-tap + insert `"\n"` |
| **[ ?123 ]** (`id=12`) | Switch to Page 2 (`PAGE_2_EXT_SYM`) | — | Switch to Page 2 (`PAGE_2_EXT_SYM`) | — | — | Open Settings (`SettingsActivity`) |
| **[ EN / ID ]** (`id=13`) | Hot-swap Language (`EN` $\leftrightarrow$ `ID`) | Hot-swap Language (`EN` $\leftrightarrow$ `ID`) | Switch to Emoji (`PAGE_4_EMOJI`) | — | — | Toggle T9 / ABC mode |
| **[ 0 ␣ SPACE ]** (`id=14`) | Commit Candidate 1 + space (if T9 composing), else commit/flush multi-tap + `DoubleSpacePeriodHelper` + refresh Idle NWP | — | Non-breaking space (`\u00A0`) | Single `DPAD_LEFT` (or Trackpad Scrub Left on drag) | Single `DPAD_RIGHT` (or Trackpad Scrub Right on drag) | Commit active composing/multi-tap + insert literal `0` |

> **Page-Switching & Universal Key Notes:**
> - Calling `switchToPage()` commits any active T9 composing candidate (`addSpace = false`), commits any active multi-tap character, and flushes `multiTapWordBuffer`.
> - On `PAGE_0_TEXT`, keys `id == 12` (`?123`) and `LANG_SWITCH` include an `8dp` inner-edge hit tolerance favoring adjacent `SPACE_0` (`KeyAtlas.findKeyAt`).
> - In `FlickDisambiguator` and `PageController`, `DEL` (`!= RIGHT`), `ENTER` (`UP`/`DOWN`), and `SPACE_0` (`DOWN`/`LEFT`/`RIGHT`) flicks also work universally across Pages 1–3; `PAGE_SWITCH` long-press Settings is scoped exclusively to Page 0 `key.id == 12`, and `SPACE_0` long-press `0` is scoped to `digitValue >= 0` (Page 0 only).

---

### 3.1. Page 1: Numeric & Calculator Numpad (`PAGE_1_NUM_SYM`)

Activated via `[ 12 34 ]` on Page 2/3, Flick Down on Key `1` (Page 0), or automatically on `TYPE_CLASS_NUMBER` / `TYPE_CLASS_PHONE` / `TYPE_CLASS_DATETIME` inputs. Entering Page 1 sets `isT9Mode = false`, resets the Page 1 operator scroll offset, and clears the candidate strip while preserving Page 0 `keys[0..15].digitValue` metadata for `SpatialScorer` geometry.

```text
+-------------------------------------------------------------------------+
| [  +  ] | [     1     ] | [     2     ] | [     3     ] | [     %     ] |  <-- Row 0
| [  -  ] +---------------+---------------+---------------+---------------+
| [  *  ] | [     4     ] | [     5     ] | [     6     ] | [     ␣     ] |  <-- Row 1
| [  /  ] +---------------+---------------+---------------+---------------+
| [ ( ) ] | [     7     ] | [     8     ] | [     9     ] | [   ⌫ DEL   ] |  <-- Row 2
+---------+-------+-------+---------------+-------+-------+---------------+
| [ ABC ] | [ , ] | [!?#] | [     0     ] | [ = ] | [ . ] | [  ↵ ENTER  ] |  <-- Row 3
+---------+-------+-------+---------------+-------+-------+---------------+
```

- **Geometry (`computePage1Geometry`):** `topPadding = 12dp`, `bottomPadding = 6dp`, `keyMargin = 3dp`, `sideColWidth = 14%` each, `midColWidth = 72% / 3` (`24%` each).
- **Left Column (14% Width, Rows 0–2):** Vertically scrollable operator container (`+`, `-`, `*`, `/`, `(`, `)` — 4 visible slots at a time). Supports drag scroll and vertical flick snap to top/bottom (`SymbolPagesGestureTracker`).
- **Center Grid (72% Width):** Standard `1–9` numpad, `0`, and split Row 3 utility cells: Col 1 split `,` (42%) / `!?#` (58% → `PAGE_2_EXT_SYM`), Col 3 split `=` (58%) / `.` (42%).
- **Right Column (14% Width):** `%`, `␣`, `⌫ DEL`, `↵ ENTER`.

---

### 3.2. Page 2 & Page 3: Primary & Extended Symbols (10-Unit Gboard-Style)

Activated via `[ ?123 ]` (`PAGE_2_EXT_SYM`) and toggled via `[ =\< ]` / `[ ?123 ]` to `PAGE_3_EXT_SYM_ALT` (both set `isT9Mode = false` and clear candidates). Row widths are divided into 10 base units (`uWidth = contentWidth / 10f`); Row 2 uses `1.5u + 7×1.0u + 1.5u` and Row 3 uses `1.5u + 1.0u + 1.0u + 4.0u + 1.0u + 1.5u`, with `[ ␣ ]` displaying `"Indonesia"` or `"English"`.

#### Page 2 (`PAGE_2_EXT_SYM` — Primary Symbols)
```text
Row 0:  1   2   3   4   5   6   7   8   9   0
Row 1:  @   #   $   _   &   -   +   (   )   /
Row 2: [=\<]  *   "   '   :   ;   !   ?  [⌫ DEL]
Row 3: [ABC]  ,  [12 34] [     ␣ (Language)    ]  .  [↵ ENTER]
```

#### Page 3 (`PAGE_3_EXT_SYM_ALT` — Extended Symbols)
```text
Row 0:  ~   `   |   •   √   π   ÷   ×   §   Δ
Row 1:  £   ¢   €   ¥   ^   °   =   {   }   \
Row 2: [?123] %   ©   ®   ™   ✓   [   ]  [⌫ DEL]
Row 3: [ABC]  <  [12 34] [     ␣ (Language)    ]  >  [↵ ENTER]
```

---

### 3.3. Page 4: In-Canvas Native Emoji Picker (`PAGE_4_EMOJI` / alias `PAGE_3_EMOJI`)

Activated via Flick Down on `[ EN / ID ]`. Direct in-canvas rendering (`EmojiPageRenderer`) avoiding `RecyclerView` overhead, with dedicated enlarged `emojiGlyphPaint` (`24dp * rowScale`), `emoticonTextPaint` (`15.5dp * rowScale`), and `emojiTabTextPaint` (`15.5dp * rowScale`) for high legibility and wide 6-column touch targets.

```text
+-------------------------------------------------------------------------+
| [🕒]  | [😀]  | [👋]  | [🐶]  | [🍔]  | [💡]  | [❤️]  | [;-)] |  <-- 8 Tabs
+-------------------------------------------------------------------------+
|  😀   |  😃   |  😄   |  😁   |  😆   |  😅   |               |  <-- 6-Col Scrollable Grid
|  :-)  |  :-(  |  ;-)  |  :-P  |  :-D  |  :-|  |               |      (4 Visible Rows = 24 Items)
+-----------+------------------+----------------+---------------+
| [ ABC ]   | [ 🕒 Recents ]   | [ ␣ Space ]    | [ ⌫ DEL ]     |  <-- Control Row
+-----------+------------------+----------------+---------------+
```

- **Categories (8 Curated Tabs, default tab `1` = Smileys):** `Recents` (`🕒`), `Smileys` (`😀`), `People` (`👋`), `Animals` (`🐶`), `Food` (`🍔`), `Objects` (`💡`), `Symbols` (`❤️`), and classic SwiftKey-style text `Emoticons` (`;-)` — `:-)`, `:-(`, `;-)`, `:-P`, `:-D`, `:-|`, `:-*`, `:O`, `B-)`, `:-$`, `:3`, `:-/`, `:-[`, `:-\`, `O:-)`, `:'(`, `(^_-)`, `(-:`, `:-{`, `8-)`, `^O^`, `^_^`, `(^O^)`, `<3`, etc.); low-frequency groups (`Travel`, `Activities`, `Flags`) are omitted (`EmojiAtlas`, 6 columns × 4 visible rows).
- **Interactions (`EmojiGestureTracker`, `EmojiInteractionCoordinator`, `EmojiActionDispatcher`):**
  - Tapping an emoji or text emoticon records it in in-memory `recentEmojis` (max 24), commits any active composing candidate (without space), commits the emoji/emoticon string, resets `lastSpaceTapTime`, and updates auto-caps.
  - Vertical drag inside the grid scrolls rows; horizontal swipe (`LEFT` = next tab, `RIGHT` = previous tab) switches categories.
  - Bottom controls (25% width each): `[ ABC ]` returns to `PAGE_0_TEXT`; `[ 🕒 Recents ]` switches to tab `0` and resets scroll; `[ ␣ Space ]` commits composing + space or commits/flushes multi-tap + `DoubleSpacePeriodHelper` + refreshes Idle NWP; `[ ⌫ DEL ]` supports tap, `50ms` repeat delete (whole-word after `1200ms`), and `UP`/`LEFT` (abort composing or delete preceding word) and `DOWN` (clear field) flicks.
- **Pending Gaps:** `recentEmojis` is in-memory only (not persisted), Fitzpatrick skin-tone long-press modifier strip is not implemented, and `Paint.hasGlyph()` / `EmojiCompat` filtering, bitmap glyph caching, and `OverScroller` fling physics are not yet implemented.

---

## 4. Touch Handling, Multi-Touch & Gesture State Machine

Touch events in `T9KeyboardView.onTouchEvent()` are dispatched by `TouchGestureTracker`:
- **`ACTION_DOWN` routing priority:** `PAGE_4_EMOJI` (`EmojiGestureTracker`) $\to$ `PAGE_1_NUM_SYM` operator container (`SymbolPagesGestureTracker`) $\to$ `PAGE_0_TEXT` strip (`y < stripHeight`, `CandidateStripGestureTracker`) $\to$ Keypad (`KeyAtlas.findKeyAt(x, y)` + `FlickDisambiguator.isLongPressSupported` timer).
- **Multi-Touch Rollover (`ACTION_POINTER_DOWN` / `ACTION_POINTER_UP`):** When a second finger touches down while a keypad key is active (and `!longPressTriggered && !flickTriggered && !isScrubbing`), `TouchGestureTracker` immediately commits `onKeyTap(prevKey, touchDownX, touchDownY)` for the first finger and transfers `activePointerId` to the new finger (ignored during active Emoji page or Page 1 operator column touches). `ACTION_POINTER_UP` evaluates tap/flick when the active pointer lifts.

### 4.1. Gesture Detection Constants (`TouchGestureTracker.kt` & `RepeatDeleteHandler.kt`)

- `touchSlopPx`: `10f * density` (fallback `TOUCH_SLOP_PX = 30f`)
- `flickDistanceMinPx`: `12f * density` (fallback `FLICK_DISTANCE_MIN_PX = 36f`)
- `FLICK_TIME_WINDOW_MS`: `180L` *(defined as constant; runtime flick detection gates on `flickDistanceMinPx` and `!longPressTriggered && !flickTriggered && !isScrubbing`)*
- `longPressTimeoutMs`: default `LONG_PRESS_TIMEOUT_MS = 350L` (configurable `200–600ms`)
- `REPEAT_TICK_INTERVAL_MS`: `50L`
- `ACCELERATED_DELETE_THRESHOLD_MS`: `1200L`
- `scrubStepPx`: `(32f / 3f) * density` (fallback `SCRUB_STEP_PX = 32f`)
- `spaceScrubActivationPx`: configurable via `space_scrubbing_sensitivity` (`14dp` high, `22dp` normal, `30dp` low) $\times$ `density`, with `spaceScrubAspectRatio = 1.3f` (`absDx > absDy * 1.3f`) and optional `150ms` hold (`space_scrubbing_hold`). Once `SpaceScrubTracker` activates, long-press callbacks are cancelled and `ACTION_UP` suppresses tap/flick if `scrubStepsDispatched > 0`.

### 4.2. Flick Vector Evaluation (4-Quadrant Disambiguation — `FlickDisambiguator.kt`)
Evaluates $\theta = \operatorname{atan2}(-dy, dx)$ in degrees (evaluated in order):
- **Flick Right:** $[-45^\circ, 45^\circ]$
- **Flick Up:** $(45^\circ, 135^\circ]$
- **Flick Down:** $[-135^\circ, -45^\circ)$
- **Flick Left:** Remaining angles ($> 135^\circ$ or $< -135^\circ$)

---

## 5. Core System Button State Machines

- **Key ↵ (ENTER) (`EnterKeyHandler.kt`):** If `hasActiveComposing`, commits Candidate 1 (`addSpace = false`) and calls `finishComposingText()`. `resolveEffectiveAction()` respects `IME_FLAG_NO_ENTER_ACTION` (returning `IME_ACTION_UNSPECIFIED`), non-zero `editorInfo.actionId`, and masked `IME_MASK_ACTION` (`GO`, `SEARCH`, `SEND`, `NEXT`, `DONE`, `PREVIOUS`); executes `performEditorAction()`, commits `"\n"` if `TYPE_TEXT_FLAG_MULTI_LINE`, or dispatches `KEYCODE_ENTER` down/up otherwise. Flick Up calls `handleForceSubmit()` (performs `effectiveAction` or `IME_ACTION_DONE`, falling back to `KEYCODE_ENTER`).
- **Key ⇧ (SHIFT) (`ShiftController.kt` + `AutoCapsController.kt`):** Cycles `LOWERCASE (0)` $\to$ `TITLECASE (1)` $\to$ `UPPERCASE (2)` $\to$ `LOWERCASE (0)`. Double-tap ($\le 350\text{ms}$), Flick Up, or Long Press locks `UPPERCASE` (Caps Lock); tapping while Caps Locked returns to `LOWERCASE`. Manual interactions set `isManualOverride = true`; `onCharacterCommitted()` clears `isManualOverride` and resets `TITLECASE` to `LOWERCASE` unless Caps Locked. `AutoCapsController` inspects `InputConnection.getCursorCapsMode()` on `TYPE_CLASS_TEXT` unless Caps Locked, manually overridden, or excluded (`PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `EMAIL_ADDRESS`, `WEB_EMAIL_ADDRESS`, `URI`, `FILTER`).
- **Key ⌫ (DEL) (`DeleteController.kt`):** Priority order: (1) delete active selection via `finishComposingText()` + `commitText("", 1)`, (2) if `hasActiveComposing`, pop last stroke from `DawgEngine` in $O(1)$, (3) optional Retroactive Un-Commit within `3000ms` (disabled by default via `enableRetroactiveUncommit = false`), (4) delete UTF-16 surrogate pair (`Character.isSurrogatePair`) or single char (with `KEYCODE_DEL` down/up fallback when buffer is empty).

---

## 6. IME Lifecycle, Typing Engines & Predictive Pipelines

### 6.0. IME Service Lifecycle & Selection Flow (`OpenT9InputMethodService`)
1. **`onCreate()`:** Starts `SettingsObserver`, initializes `LearnedWordsRepository`, `AutoCapsController`, `MultiTapController`, `WordCorrectionController`, and `T9ComposingCoordinator`, initializes native `DynamicStore` (`filesDir/opent9_vocab.dat`), loads `EN`/`ID` `.dawg` + `.bin` assets via `LexiconLoader`, and sets `sessionTextT9Mode = settingsObserver.isDefaultT9()`.
2. **`onCreateInputView()`:** Instantiates `T9KeyboardView` + `PageController`, binds `ImeLifecycleConfigurator` settings callbacks and view touch/action listeners.
3. **`onStartInputView(info, restarting)`:** Resets Page 1 / Emoji scroll, applies theme and layout configuration (syncing key center coordinates `keys[0..15]` with `digitValue >= 0` to `SpatialScorer` via `nativeUpdateKeyGeometry`), resets composing state, evaluates `EditorContextHelper` (`isPasswordMode`, `isIncognito`, `isNumeric`), sets starting page and T9 state, updates `imeAction` and `AutoCapsController`, and calls `refreshIdleNextWordSuggestions`.
4. **`onUpdateSelection(...)` → `CursorSelectionCoordinator`:** Ignores synthetic selection callbacks (`ignoreSelectionUpdateCount`), clears `lastSpaceTapTime` on cursor move, updates `composingAnchorPosition`, skips post-processing on `isBackspaceAction`, detects manual user cursor repositioning to finalize active T9 composing (`finalizeComposingOnCursorMove`) or ABC multi-tap (`commitMultiTapActive` + `flushMultiTapWord`), updates auto-caps, and triggers `WordCorrectionController.checkSuggestions()` when idle outside password fields.
5. **`onWindowShown()` / `onWindowHidden()` / `onFinishInputView()` / `onFinishInput()` / `onDestroy()`:** Starts/stops Oboe audio stream, cancels active gestures, finalizes active composing or multi-tap on finish, and flushes `DynamicStore` asynchronously (`flushAsync()`).

### 6.1. Multi-Tap (ABC) Cycling Engine (`MultiTapEngine` + `MultiTapController`)
Lowercase-first cycling with Shift applied to alphabetic characters:
- **`[ 1 ]`**: `.` $\to$ `,` $\to$ `?` $\to$ `!` $\to$ `'` $\to$ `@` $\to$ `#` $\to$ `1`
- **`[ 2 ]`–`[ 9 ]`**: `abc2`, `def3`, `ghi4`, `jkl5`, `mno6`, `pqrs7`, `tuv8`, `wxyz9`
- **`[ 0 ]`**: `' '` $\to$ `'0'` $\to$ `'\n'`
- **Key Switch & Timeout:** Switching to a different key commits the active character and resets `TITLECASE` to `LOWERCASE` for the new character. Timeout is configurable (`400–1000ms`, default `600ms`). Committed letters accumulate in `multiTapWordBuffer` and flush to `LearnedWordsRepository` when `word.length >= 2` (unless `isIncognito`).

### 6.2. Predictive T9 & Post-Processing Pipeline

1. **Native C++ T9 Engine (`DawgEngine` + `T9SingleEngine` + `SpatialScorer` + `NgramEngine` + `DynamicStore`):**
   - `T9SingleEngine::pushStroke(digit, touchX, touchY, scorer, config)` (`MAX_BEAM_SIZE = 32`, `MAX_CANDIDATES = 16`, `MAX_SPATIAL_EDITS = 1`, `MAX_STROKE_DEPTH = 48`):
     - Collects pressed digit + up to 2 adjacent T9 neighbor digits via `SpatialScorer::collectDigitHypotheses` (`MAX_HYPOTHESES = 3`, `MAX_NEIGHBOR_LL_GAP = 2.6f`, Gaussian variance $\sigma = \text{touch\_variance\_sigma} = 16.0\text{dp} \times \text{density}$).
     - Expands beam paths in `OPENT9D2` DAFSA and computes **Unified Log-Linear Score** with $\sqrt{\text{strokeLen}}$-normalized spatial log-likelihood, Exact-Digit Priority (`edit_count == 0` preferred over neighbor edits), progressive **Length-Ratio Penalty** for prefix completions (`maxExtraDelta`: `+2` at depth 1, `+3` at depth 2, `+4` at depth 3, `+7` at depth $\ge 4$), and **Soft Session Recency Bonus** (`0.35 * 0.5^(Δt / 6h)`) guarded strictly to exact-length (`wordLen == strokeLen`) terminal matches.
     - Contextual boost combines static `NgramEngine::getBigramBoost(activeContextBucket, wid)` (`OPENT9N1`, FNV-1a 64-bit hash + `OPENT9D2` string verification) and personal `DynamicStore::getBigramBoost()`.
     - Merges canonical matches from `DynamicStore::findMatchingWords()` (T9 prefix index up to 12 digits, exponential half-life decay $0.5^{\Delta t / \text{halfLife}}$, weighted explicit candidate pick boost `weight = 3` on `index > 0`), isolating exact-length matches in `terminalPool` and routing non-terminal prefix matches to `completionPool`, and filters `isDeleted()` blacklisted words.
     - Aggregates O(1) pre-cached top-16 subtree completions (`depth <= 6`) or fixed-stack best-first `collectBestCompletions()` (queue `64`, expand budget `48`, dynamic `maxExtraDepth`) across all active beam nodes into a unified `completionPool` with tiered near/mid ranking when `candCount < 16`.
     - Runs `findClosestWords()` (retaining previous non-empty stroke candidates at `-0.5 * overtypeLen` and probing `ADJACENT_T9_KEYS` at `-1.5` base penalty) when `candCount == 0`.
   - **Bilingual Coordination (`DawgEngine`):** Runs `primary` and (when `config.bilingual_enabled != 0 && secondary.isLoaded()`) `secondary` engines, merging candidates with `SECONDARY_LANG_PENALTY = 0.55f` for both `pushStroke` and `getNextWordCandidates`.
   - **Idle NWP (`T9SingleEngine::getNextWordCandidates`):** Combines `NgramEngine::getTopNextWordIds` (`topBoosts + 0.02 * corpusLog + slangBoost`) with `DynamicStore::findNextWords` (`score + 0.4` when unmerged).

2. **Kotlin Prediction & Composing Pipeline:**
   - `T9ComposingCoordinator`: Prepares preceding context word on first stroke (`prepareComposingContext` → `NativeEngineBridge.setContextWord`), commits words/candidates/NWP chips (`commitWord`, `commitNextWordCandidate`), records commit history (`DeleteController`) + unigram/bigram usage (`LearnedWordsRepository`), resets composing state, updates shift & auto-caps, and refreshes Idle NWP when `addSpace == true`.
   - `CandidateResolver`: Queries `NativeEngineBridge.getCandidates()`, applies active language orthography (`IndonesianOrthography` if `"ID"`, else `EnglishOrthography`), filters `NativeEngineBridge.isWordDeleted(...)`, invokes `SuggestionGuard` on empty results, and promotes `preferredWord` if restoring.
   - `EnglishOrthography`: Restores fallback contractions (`dont` $\to$ `don't`, `im` $\to$ `I'm`, etc.), standalone `i` $\to$ `I`, and expands ambiguous pairs (`its`/`it's`, `were`/`we're`, `well`/`we'll`, `ill`/`I'll`, `lets`/`let's`, `hell`/`he'll`, `shell`/`she'll`, `shed`/`she'd`, `wed`/`we'd`, `id`/`I'd`) when not already present in `OPENT9D2` results.
   - `IndonesianOrthography`: Synthesizes productive suffixes (`-nya`, `-kan`, `-lah`, `-kah`, `-pun`, `-ku`, `-mu`) when `digits.size >= 4` and a matching root exists.
   - `SuggestionGuard`: Holds last valid candidate/prefix state (absorbing 1-digit typos or phonotactically extending for 2+ extra digits) or projects OOV words using language-specific unigram priors + character-bigram bonus tables (`EN` / `ID`) + consonant-vowel alternation, with deterministic primary-letter fallback (`projectWordDeterministic`) if all projections are blacklisted.
   - `WordCorrectionController`: Scans active selection (`<= 32` chars, single token with letters) or word under cursor (`<= 32` chars), replays its letters as T9 digits through `NativeEngineBridge`, applies `matchCase`, and performs atomic `beginBatchEdit()` / `deleteSurroundingText()` / `commitText()` replacement.
   - `PredictionContextHelper`: Inspects up to `64` chars before cursor (`InputConnection.getTextBeforeCursor`); extracts preceding alphabetic token when trailing whitespace is present and no sentence terminator (`.`, `!`, `?`) precedes the whitespace.

---

## 7. EditorInfo & InputType Dynamic Routing (`EditorContextHelper.kt`)

Evaluated in `OpenT9InputMethodService.onStartInputView(info, restarting)`:
- **Password Fields (`TYPE_TEXT_VARIATION_PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `NUMBER_VARIATION_PASSWORD`):** Forces `PAGE_0_TEXT` with `isT9Mode = false` (Multi-Tap ABC), suppresses `WordCorrectionController`, `DoubleSpacePeriodHelper`, and `Idle NWP`.
- **Numeric / Phone / Datetime Fields (`TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, `TYPE_CLASS_DATETIME`):** Opens `PAGE_1_NUM_SYM` with `isT9Mode = false` (`sessionTextT9Mode = false`).
- **Incognito Mode (`IME_FLAG_NO_PERSONALIZED_LEARNING` or password):** Suppresses all `LearnedWordsRepository` unigram & bigram writes (`recordWordUsage`, `recordBigramUsage`).

---

## 8. Keyboard Settings Architecture & Dynamic Config Sync

### 8.1. `SettingsActivity` (`preferences.xml`)
- **Appearance & Theme:** `app_theme` (`system`, `light`, `dark`).
- **Audio & Haptics:** `haptic_enabled` (`true`), `vibration_intensity` (`5–100ms`, default `25ms`, `ManualInputSeekBarPreference`), `audio_enabled` (`true`), `audio_volume` (`0–100%`, default `60%`, `ManualInputSeekBarPreference`), `audio_style` (`0` = Mechanical Classic, `1` = Modern Soft, `2` = Minimalist Click).
- **Layout & Display:** `keyboard_height` (`220–320dp`, default `260dp`, `ManualInputSeekBarPreference`), `candidate_font_size` (`14–22sp`, default `16sp`, `ManualInputSeekBarPreference`), `one_handed_mode` (`0` = Disabled, `1` = Left-Handed, `2` = Right-Handed), `landscape_layout_mode` (`"left"` = Compact Left + Edit Panel, `"right"` = Edit Panel + Compact Right, `"center"` = Centered + Side Gutters, `"full"` = Full Width; default `"left"`), `rearrange_keyboard_layout` (launches `RearrangeLayoutActivity`, persisted under `custom_keyboard_layout`).
- **Typing Behaviors:** `default_input_mode` (`0` = Predictive T9, `1` = Multi-Tap ABC), `long_press_delay` (`200–600ms`, default `350ms`, `ManualInputSeekBarPreference`), `multi_tap_timeout` (`400–1000ms`, default `600ms`, `ManualInputSeekBarPreference`), `auto_caps` (`true`), `auto_space` (`true`), `double_space_period` (`true`), `space_scrubbing` (`true`), `space_scrubbing_sensitivity` (`normal`, `low`, `high`), `space_scrubbing_hold` (`false`, `150ms` hold toggle).
- **Dictionary & Language:** `startup_language` (`ID` / `EN`, default `ID`), `bilingual_prediction` (`false`), `slang_boost` (`true`), `decay_half_life` (`14`, `30`, `0` = Disabled, default `30`), `reset_user_dictionary` (AlertDialog confirmation → `LearnedWordsRepository.clearAll()`). *(Pending: Individual User Dictionary Editor UI).*

### 8.2. C++ `NativeConfig` Synchronization (`native_config.hpp` + `SettingsObserver.kt`)
`SettingsObserver.syncAll()` computes `touch_variance_sigma = 16.0f * density.coerceAtLeast(1.0f)` and pushes configuration changes via `NativeEngineBridge.syncConfig`, `syncBilingualMode`, and `syncAudioConfig`:
```cpp
struct NativeConfig {
    float touch_variance_sigma = 42.0f;     // Synced to 16.0dp * density
    uint32_t long_press_timeout_ms = 350;
    uint32_t multi_tap_timeout_ms = 600;
    uint8_t auto_space_enabled = 1;
    uint8_t slang_boost_enabled = 1;
    uint32_t decay_half_life_days = 30;
    uint8_t audio_enabled = 1;
    float audio_volume = 0.6f;
    uint32_t audio_style = 0;               // 0=Classic, 1=Soft, 2=Click
    uint8_t bilingual_enabled = 0;          // 0 or 1
};
```

---

## 9. Performance Targets & Current Reality

| Metric | Target Spec | Current Codebase Status |
| :--- | :--- | :--- |
| **Disambiguation Latency** | `< 0.5 ms` | Verified in `native_tests/test_native_core.cpp` (`< 200 µs` target for `4663` + p99 sentence-level benchmark). |
| **Rollback Execution Time** | `< 0.01 ms` | $O(1)$ stack index decrement in `T9SingleEngine::popStroke()` / `DawgEngine::popStroke()`. |
| **Lexicon Pointer Swap** | `< 0.05 ms` | Verified in `test_native_core.cpp` via `LexiconManager::switchLanguage()`. |
| **Audio Feedback Latency** | `< 10 ms` | Google Oboe `LowLatency` shared float stream (`AudioEngine`, 48kHz Mono). |
| **Lexicon & N-Gram Assets** | Zero-copy `mmap` (`noCompress`), no strict MB cap | `en_lexicon.dawg` (`9,557,040 B` / `9.11 MB`) + `id_lexicon.dawg` (`11,384,535 B` / `10.86 MB`) + `en_bigrams.bin` (`9,200 B`) + `id_bigrams.bin` (`9,728 B`) = `19.99 MB` total (`noCompress` for `dawg` & `bin` in `app/build.gradle.kts`). |
| **Hot-Path Allocation / I/O** | 0 alloc / 0 disk I/O | `T9SingleEngine::pushStroke` uses fixed stack pools (`terminalPool`/`completionPool`/`candidatesPool`), `DynamicStore` uses in-memory T9 prefix index + `1500ms` debounced background `flushIfDirty()`. |

---

## 10. Repository Layout (Actual)

```text
t9Keyboard-V2/
├── app/
│   ├── build.gradle.kts                              # minSdk=26, targetSdk=34, NDK 26.1, noCompress=["dawg","bin"]
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml                   # VIBRATE only, no INTERNET permission
│       │   ├── assets/dictionaries/
│       │   │   ├── en_lexicon.dawg                   # 9,557,040 B OPENT9D2 T9-digit DAFSA (EN)
│       │   │   ├── id_lexicon.dawg                   # 11,384,535 B OPENT9D2 T9-digit DAFSA (ID)
│       │   │   ├── en_bigrams.bin                    # 9,200 B OPENT9N1 mmap bigram table (EN)
│       │   │   └── id_bigrams.bin                    # 9,728 B OPENT9N1 mmap bigram table (ID)
│       │   ├── cpp/
│       │   │   ├── CMakeLists.txt
│       │   │   ├── include/                          # dawg_engine, ngram_engine, dynamic_store, lexicon_manager, spatial_scoring, multi_tap_engine, audio_engine, native_config
│       │   │   └── src/                              # Implementations + jni_bridge.cpp
│       │   ├── java/com/opent9/keyboard/
│       │   │   ├── OpenT9InputMethodService.kt       # Slim IME lifecycle wrapper
│       │   │   ├── T9ComposingCoordinator.kt         # Composing, commit, pop/rehydrate, NWP context
│       │   │   ├── KeyboardActionDispatcher.kt       # Tap/flick/long-press routing
│       │   │   ├── LandscapePanelActionHandler.kt    # Landscape edit/cursor/clipboard panel actions
│       │   │   ├── CursorSelectionCoordinator.kt     # onUpdateSelection disambiguation
│       │   │   ├── LanguageCycleHelper.kt
│       │   │   ├── CandidateRemovalHelper.kt
│       │   │   ├── EmojiActionDispatcher.kt
│       │   │   ├── ImeLifecycleConfigurator.kt
│       │   │   ├── ComposingStroke.kt
│       │   │   ├── jni/NativeEngineBridge.kt
│       │   │   ├── lexicon/LexiconLoader.kt
│       │   │   ├── prediction/                       # CandidateResolver, EnglishOrthography, IndonesianOrthography, LearnedWordsRepository, PredictionContextHelper, SuggestionGuard, WordCorrectionController, WordCorrectionHelper
│       │   │   ├── settings/                         # SettingsActivity (+SettingsFragment), SettingsObserver, RearrangeLayoutActivity, RearrangeKeyboardView, KeyboardLayoutConfig, ManualInputSeekBarPreference
│       │   │   └── ui/                               # T9KeyboardView, KeyAtlas, EmojiAtlas, LandscapePanelLayout, TouchGestureTracker + sub-trackers, Page0/1/23/Emoji/Strip/LandscapePanel renderers, controllers, themes, icons
│       │   └── res/                                  # xml/preferences.xml, xml/method.xml, values/, values-in/, layout/, drawable/
│       └── test/java/com/opent9/keyboard/            # 22 JVM / Robolectric unit, integration & contract test suites (17 root + 5 prediction/)
├── native_tests/                                     # test_native_core.cpp, test_suggestion_quality.cpp, simulated_speed_typing.cpp, detailed_ram_profile.cpp
└── tools/
    ├── check_doc_sync.sh                             # Contract & prd.md sync verification script
    └── dawg_compiler/                                # dawg_builder.cpp, ngram_builder.cpp, lexicon_extractor.cpp, en_curated.tsv, id_curated.tsv, en_bigrams.tsv, id_bigrams.tsv
```

---

## 11. Implementation Status & Remaining Gaps

### Completed (Beyond Original PRD Scope)
- Modularized IME architecture (`T9ComposingCoordinator`, `KeyboardActionDispatcher`, `CursorSelectionCoordinator`, `ImeLifecycleConfigurator`, dedicated renderers & gesture sub-trackers).
- Retrospective word correction (`WordCorrectionController`) & language-specific orthography (`EnglishOrthography`, `IndonesianOrthography`, `SuggestionGuard`).
- Interactive Row 4 & Column 4 key rearrangement (`RearrangeLayoutActivity`, `RearrangeKeyboardView`).
- 5-column calculator-style Page 1 with scrollable operator column & 10-unit Gboard-style Page 2/3 symbols.
- Light / Dark / System theme support (`KeyboardTheme`), One-Handed mode, landscape height clamping, tablet ergonomic centering, and `ManualInputSeekBarPreference`.
- Configurable Spacebar scrubbing sensitivity & 150ms hold option (`SpaceScrubTracker`).
- Legacy Page 0 Key 15 (`EMOJI_DOT`), old Page 1 flick IDs, and 3×3 `symbolLayers` dead code removed; `ContractConsistencyTest` + `tools/check_doc_sync.sh` added.
- **Prediction Phase 1:** Unified Log-Linear scoring, multi-key `SpatialScorer` border hypotheses, DPI-normalized `touch_variance_sigma` (`16dp * density`), `DynamicStore` in-memory T9 prefix indexing + debounced async `flushIfDirty()`, fixed-stack `T9SingleEngine::pushStroke` pools, `LexiconManager` `is_mmap` safety, `std::shared_mutex` JNI guard, and complete `reset_user_dictionary` cleanup (`LearnedWordsRepository.clearAll()`).
- **Prediction Phase 2:** T9-digit-indexed minimal DAFSA (`OPENT9D2`, naturally 48-byte/16-byte aligned, quantized `log_freq`, `LEX_FLAG_SLANG`, cached depth `<= 6` prefix completions, `Word` display forms with apostrophes/hyphens), `DynamicStore` canonical normalization, removal of Kotlin hardcoded homograph promotions, and character-bigram fallback tables in `SuggestionGuard`.
- **Prediction Phase 3:** Next-Word Prediction (`OPENT9N1` `mmap` bigram tables + `DynamicStore` personal user bigrams), `NgramEngine`, `PredictionContextHelper`, Idle NWP suggestion strip (`isIdlePrediction` non-highlighted chips), and contextual T9 disambiguation (`setContextWord`).
- **Prediction Phase 4:** Bilingual code-switching (`bilingual_prediction` preference + `DawgEngine` `primary`/`secondary` coordinator with `SECONDARY_LANG_PENALTY = 0.55f`) and sentence-level regression + p99 latency benchmark (`test_native_core`).
- **Prediction Phase 5 (Dynamic Length-Ratio & Soft-Recency Tuning):** Progressive length-ratio completion penalty & dynamic `maxExtraDelta` window in `dawg_builder` and `T9SingleEngine`, cross-beam tiered `completionPool`, non-terminal `DynamicStore` isolation from `terminalPool`, exact-digit priority (`edit_count == 0`), $\sqrt{\text{strokeLen}}$-normalized spatial scoring, 6-hour soft session recency bonus with exact-length guard, and weighted explicit candidate pick promotion (`weight = 3` for `index > 0`).
- **Button & Gesture Consistency Hardening:** Incognito/password isolation in `MultiTapController`, preserved session `isT9Mode` and password/numeric guards across page switches, `FlickDisambiguator.isLongPressSupported` scoping (settings long-press restricted to Page 0 Key 12 `?123`), long-press vs flick & space-scrubbing guards in `TouchGestureTracker`, multi-tap commit unification on flick/long-press/repeat-delete, Emoji `DEL`/`SPACE` behavioural parity, Idle NWP `TITLECASE` key label fix, One-Handed Page 0 gutter hit-testing guard, and preserved Page 0 `digitValue` metadata on Page 1 reload.
- **Landscape Compact + Utility Panel (`Opsi 2`):** Ergonomic compact Page 0/1 sizing (`<= 440dp`) paired with `LandscapePanelLayout` / `LandscapePanelRenderer` / `LandscapePanelActionHandler` (Undo/Redo, 4-way Cursor D-Pad, Home/End, Select All, Copy/Cut/Paste, Swap Dock Side, Emoji, Hide IME, plus centered split gutters), `landscape_layout_mode` preference, clamped `T9` pill width (`[38dp, 56dp]`), and readable landscape typography floor (`rowScale >= 0.78`).
- **Engine, Bridge, Orthography & Layout Reliability Hardening:** Strict weak ordering scalar sort keys in `DawgEngine`/`T9SingleEngine`, T9 digit-length `lenDelta` for apostrophe/hyphen words, context/slang preservation in `collectBestCompletions`, canonical bilingual deduplication, `DynamicStore` blacklist LRU protection + prefix unindexing + single-lock `recordUsage`, UTF-8 `ByteBuffer` decoding with synchronized JNI buffers, lock-free `AudioEngine::playClick` stream health guard, versioned `LexiconLoader` fallback cache, bilingual `CandidateResolver` + root-digit-verified `IndonesianOrthography` synthesis, `WordCorrectionController` digit-aware orthography + batch-edit selection sync, `BreakIterator` grapheme cluster deletion in `DeleteController`, `ACTION_DOWN` spatial coordinates on `TouchGestureTracker`, vertical `SPACE_0` margin tolerance in `KeyAtlas`, and zero-allocation `EmojiAtlas` recent tab reads.

### Pending / Known Gaps
1. **User Dictionary Editor:** C++ `DynamicStore::serializeAllWords()` / `addWord()` lack JNI bindings and Settings UI.
2. **Emoji Picker Completeness:** Persist `recentEmojis` (frecency), implement long-press Fitzpatrick skin-tone strip, add `Paint.hasGlyph()` / `EmojiCompat` filtering, bitmap glyph caching, and `OverScroller` fling physics.
3. **Settings Exposure:** Expose `Retroactive Un-Commit` (`DeleteController.handleDeleteTap(..., enableRetroactiveUncommit)`) in `preferences.xml`.
4. **Audio, Accessibility & Release Config:** Check `AudioManager.ringerMode` and auto-idle Oboe stream when unused; add `ExploreByTouchHelper` for TalkBack; complete `values-in/strings.xml` translations and add `in_ID` subtype in `method.xml`; configure proper release signing in `app/build.gradle.kts` (currently uses debug signingConfig).
