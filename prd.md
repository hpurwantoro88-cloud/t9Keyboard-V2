# Product Requirements Document (PRD): OpenT9 Core Native Keyboard (v3.0)

**Document Identifier:** `prd.md`  
**Package Identifier:** `com.opent9.keyboard` (`versionCode = 2`, `versionName = "3.0.1"`)  
**Architecture Class:** Native Hybrid Systems Architecture (Thin Kotlin IME Wrapper + C++20 Core via NDK + Zero-Allocation Hardware Canvas)  
**Target Benchmarks:** Microsoft SwiftKey, Gboard (3×4 Mode), Samsung Keyboard (3×4 Mode)

---

## 1. Executive Summary & Core Value Proposition

OpenT9 Core delivers an ultra-lightweight, low-latency Android T9 predictive keyboard that optimizes memory footprint, cold-start latency, frame rendering consistency, and typing disambiguation speed.

### Core Differentiators

- **Sub-1ms Predictive Disambiguation:** C++20 T9-digit-indexed minimal DAFSA (`OPENT9D2`) traversal + multi-hypothesis spatial beam search with pre-cached prefix completions and adjacent-key/overtype fallback.
- **Deterministic Fallback (Multi-Tap ABC Mode):** Instant toggling between predictive T9 and classic lowercase-first multi-tap for unlisted acronyms, passwords, and custom codes.
- **Retrospective Word Correction & Orthography Post-Processing:** Cursor/selection inspection (`WordCorrectionController`) for post-commit correction, English contraction restoration (`EnglishOrthography`), Indonesian suffix compounding & homograph promotion (`IndonesianOrthography`), and phonotactic fallback (`SuggestionGuard`).
- **Dynamic Auto-Fit Swipeable Suggestion Strip:** Horizontally scrollable 40dp candidate bar eliminating rigid candidate pagination.
- **Polymorphic System Keys:** State-machine-driven implementations for Key ↵ (ENTER), Key ⇧ (SHIFT), and Key ⌫ (DEL) adapting across `EditorInfo` configurations.
- **Single-Tag Language Toggle with Active T9 Glow Bar:** Minimalist visual display showing active language code (`EN` or `ID`) with an illuminated neon indicator bar when T9 prediction is active.
- **Continuous Spacebar Trackpad Scrubbing & Customizable Utility Layout:** Horizontal cursor positioning from Key 0 (`SpaceScrubTracker`), plus interactive puzzle-style rearrangement of Row 4 & Column 4 (`RearrangeLayoutActivity`).
- **Adaptive Ergonomics & Themes:** Follow-System / Light / Dark themes (`KeyboardTheme`), One-Handed mode, landscape height clamping, and tablet centering (`KeyboardDimensionCalculator`).
- **100% Offline Privacy:** Zero network permissions (`android.permission.INTERNET` strictly omitted).

---

## 2. Technical Stack & Architectural Constraints

> **Architectural Invariant:** Declarative UI frameworks (Jetpack Compose, Flutter) and nested XML view hierarchies are strictly prohibited in the core typing loop.

| Layer | Technology Selection (Current Codebase) | Notes / Evolution Target |
| :--- | :--- | :--- |
| **IME Service Wrapper** | Kotlin (`InputMethodService`) + modular coordinators | Handles window lifecycle, focus, `InputConnection` transactions, and orthography post-processing (`OpenT9InputMethodService`, `T9ComposingCoordinator`, `KeyboardActionDispatcher`). |
| **Prediction & Scoring Core** | C++20 (Android NDK 26.1 via CMake) | Executes multi-hypothesis spatial beam search, Unified Log-Linear scoring, prefix completion, overtype/typo fallback, and $O(1)$ stroke stack rollback (`DawgEngine`, `SpatialScorer`) with zero heap allocation on hot path. |
| **Static Lexicons** | Memory-Mapped Files (`mmap`) + T9-Digit Minimal DAFSA (`OPENT9D2`) + Bigram Tables (`OPENT9N1`) | `en_lexicon.dawg` (`9.1 MB`), `id_lexicon.dawg` (`10.9 MB`), `en_bigrams.bin`, and `id_bigrams.bin` mapped read-only directly via `openFd` + `mmap` (`noCompress`), naturally aligned, supporting sub-ms T9 disambiguation and contextual/idle Next-Word Prediction. |
| **Dynamic Vocabulary** | Flat Binary Store (`opent9_vocab.dat` + `.bigrams` via `DynamicStore`) + `LearnedWordsRepository` | Thread-safe C++ unigram & personal bigram store (max 4096 unigrams + 4096 bigrams) with in-memory T9 prefix index, exponential half-life decay, suppression blacklist, and debounced off-UI-thread `flushIfDirty()` persistence. |
| **UI & Render Pipeline** | Single Custom View (`Canvas.onDraw`) | Hardware-accelerated canvas backed by pre-allocated `RectF`, `Path`, `Paint`, and primitive arrays in `T9KeyboardView` and dedicated page renderers. |
| **JNI Bridge** | Direct `ByteBuffer` & Primitives (`NativeEngineBridge`) | Touch coordinates, keycodes, and direct `ByteBuffer` (`2048B` candidates, `16B` multi-tap) with JVM fallback state for unit tests. |
| **Audio Engine** | Google Oboe `1.9.0` C++ (`AudioEngine`) | Pre-synthesized 48kHz PCM click waveforms (`Mechanical`, `Modern Soft`, `Minimalist Click`) via `LowLatency` shared output stream. |
| **Configuration Sync** | `PreferenceFragmentCompat` + `SettingsObserver` -> JNI | `SharedPreferences` observer synchronizing `NativeConfig` primitives and audio settings to C++ global state. |

---

## 3. UI Layout & Keypad Specifications

The keyboard height is user-configurable (**220dp–320dp**, default **260dp** in portrait) and dynamically constrained in landscape (`<= 48%` of screen height) and large-screen portrait (`<= 480dp` centered ergonomic width, or 85% width in One-Handed mode).

### 3.0. Page 0: Primary T9 Text & Prediction Layer (Default Layout)

Row 4 & Column 4 keys are customizable via `RearrangeLayoutActivity` (`KeyboardLayoutConfig`). In the default order (`DEL, SHIFT, ENTER, LANG, SPACE, PAGE`), `SPACE` occupies **2 blocks** and Key 15 (`EMOJI_DOT`) is hidden (`bounds` zeroed):

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

#### Suggestion & Candidate Strip Layout (~40dp)
- **Fixed Left Pill (12% Width):** Displays `[ T9 ]` or `[ ABC ]`. Tapping toggles Predictive T9 vs. Literal Multi-Tap.
- **Scrollable Candidate Viewport (88% Width):**
  - Renders up to 16 candidates left-to-right; #1 candidate highlighted with `candidatePrefixPaint`.
  - Candidate widths measured via `candidateTextPaint.measureText(word) + 24dp` horizontal padding (minimum `64dp`).
  - Horizontal dragging pans candidates; tapping commits word; long-pressing opens removal/blacklist confirmation dialog (`CandidateDialogHelper`).

#### Language & T9 Glow Indicator Specification (`LANG_SWITCH` Key)
- **Display Text:** Active language code (`EN` or `ID`) with `😊` sub-label in the upper-right corner.
- **Visual Glow Bar:** Horizontal bar beneath the text (`width = min(28dp, 60% key width)`, `height = 3dp`, `radius = 1.5dp`) drawn with `indicatorGlowPaint` when `isT9Mode == true`, omitted when in `ABC` Multi-Tap mode.
- **Interactions (Implemented in `PageController` & `KeyboardActionDispatcher`):**
  - **Primary Tap / Flick Up:** Hot-swaps language (`EN` $\leftrightarrow$ `ID`) and re-evaluates active composing strokes.
  - **Flick Down:** Opens Emoji Picker (`PAGE_4_EMOJI`).
  - **Long Press:** Toggles T9 / ABC mode.

#### Complete Page 0 Key Interaction & Gesture Matrix (Implemented)

| Key | Primary Tap | Flick Up | Flick Down | Flick Left | Flick Right | Long Press |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ 1 .,?!'@# ]** | Multi-tap cycle (`.`, `,`, `?`, `!`, `'`, `@`, `#`, `1`) | — | Switch to Page 1 (`PAGE_1_NUM_SYM`) | Insert `,` | Insert `.` | Insert literal `1` |
| **[ 2 ABC ] to [ 9 WXYZ ]** | Push digit to T9 / Multi-tap char | — | — *(Diacritic picker planned)* | — | — | Insert literal digit |
| **[ ⌫ DEL ]** | Delete char / UTF-16 surrogate / pop T9 stroke | Delete preceding word / abort composing | Clear entire field | Delete preceding word / abort composing | — | Continuous repeat delete (whole-word after 1200ms) |
| **[ ⇧ SHIFT ]** | Cycle Lower $\to$ Title $\to$ Upper (Double-tap $\le 350\text{ms}$ locks Caps) | Lock UPPERCASE (Caps Lock) | — | — | — | Lock UPPERCASE (Caps Lock) |
| **[ ↵ ENTER ]** | Polymorphic `EditorInfo` action / newline / `KEYCODE_ENTER` | Force submit (`performEditorAction` / `DONE`) | Insert Tab (`\t`) | — | — | Insert newline (`\n`) |
| **[ ?123 ]** | Switch to Page 2 (`PAGE_2_EXT_SYM`) | — | Switch to Page 2 (`PAGE_2_EXT_SYM`) | — | — | Open Settings (`SettingsActivity`) |
| **[ EN / ID ]** | Hot-swap Language (`EN` $\leftrightarrow$ `ID`) | Hot-swap Language | Switch to Emoji (`PAGE_4_EMOJI`) | — | — | Toggle T9 / ABC mode |
| **[ 0 ␣ SPACE ]** | Commit Candidate 1 + Space / Double-space period | — | Non-breaking space (`\u00A0`) | Single DPAD Left / Drag: Trackpad Scrub Left | Single DPAD Right / Drag: Trackpad Scrub Right | Insert literal `0` |

> *Note:* Retroactive Un-Commit is implemented in `DeleteController.kt` behind `enableRetroactiveUncommit: Boolean = false`.

---

### 3.1. Page 1: Numeric & Calculator Numpad (`PAGE_1_NUM_SYM`)

Activated via `[ 12 34 ]` on Page 2/3, Flick Down on Key `1` (Page 0), or automatically on `TYPE_CLASS_NUMBER` / `TYPE_CLASS_PHONE` / `TYPE_CLASS_DATETIME` inputs. Bypasses T9 prediction and hides the suggestion strip.

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

- **Left Column (14% Width, Rows 0–2):** Vertically scrollable operator container (`+`, `-`, `*`, `/`, `(`, `)` — 4 visible slots at a time).
- **Center Grid (72% Width):** Standard `1–9` numpad, `0`, and split utility keys (`[` `,` `|` `!?#` `]` and `[` `=` `|` `.` `]`).
- **Right Column (14% Width):** `%`, `␣`, `⌫ DEL`, `↵ ENTER`.

---

### 3.2. Page 2 & Page 3: Primary & Extended Symbols (10-Column Gboard-Style)

Activated via `[ ?123 ]` from Page 0 (`PAGE_2_EXT_SYM`) and toggled with `[ =\< ]` / `[ ?123 ]` to `PAGE_3_EXT_SYM_ALT`:

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

Activated via Flick Down on `[ EN / ID ]`. Direct in-canvas rendering (`EmojiPageRenderer`) avoiding `RecyclerView` overhead.

```text
+-------------------------------------------------------------------------+
| [🕒] | [😀] | [👋] | [🐶] | [🍔] | [⚽] | [🚗] | [💡] | [🔣] | [🚩] |  <-- 10 Tabs
+-------------------------------------------------------------------------+
|  😀  |  😃  |  😄  |  😁  |  😆  |  😅  |  🤣  |  <-- 7-Col Scrollable Grid
|  ...                                            |
+-----------+------------------+----------------+---------------+
| [ ABC ]   | [ 🕒 Recents ]   | [ ␣ Space ]    | [ ⌫ DEL ]     |  <-- Control Row
+-----------+------------------+----------------+---------------+
```

- **Categories (10 Tabs):** Recents, Smileys, People, Animals, Food, Activities, Travel, Objects, Symbols, Flags (`EmojiAtlas`).
- **Interactions:** Vertical scroll inside grid; horizontal swipe left/right switches category tabs; long-press on `⌫ DEL` triggers accelerated repeat delete (whole-word after 1200ms) with `UP`/`LEFT`/`DOWN` flick support; `␣ Space` tap supports double-space period & auto-caps.
- **Pending Gaps:** `recentEmojis` (28 items) is currently in-memory only (not persisted), Fitzpatrick skin-tone long-press modifier strip is not yet implemented, and `EmojiCompat` / `Paint.hasGlyph()` filtering is not yet applied.

---

## 4. Touch Handling, Multi-Touch & Swipeable Strip State Machine

Touch handling is dispatched by `TouchGestureTracker` into specialized sub-trackers (`CandidateStripGestureTracker`, `EmojiGestureTracker`, `SymbolPagesGestureTracker`, `SpaceScrubTracker`, `RepeatDeleteHandler`).

### 4.1. Gesture Detection Constants (`TouchGestureTracker.kt`)

- `touchSlopPx`: `10f * density` (fallback `30f`)
- `flickDistanceMinPx`: `12f * density` (fallback `36f`)
- `FLICK_TIME_WINDOW_MS`: `180L`
- `LONG_PRESS_TIMEOUT_MS`: `350L` (configurable `200–600ms`)
- `REPEAT_TICK_INTERVAL_MS`: `50L`
- `ACCELERATED_DELETE_THRESHOLD_MS`: `1200L`
- `scrubStepPx`: `(32f / 3f) * density` (fallback `32f`)
- `spaceScrubActivationPx`: configurable via `space_scrubbing_sensitivity` (`14dp` high, `22dp` normal, `30dp` low), with optional `150ms` hold (`space_scrubbing_hold`) and `1.3f` horizontal aspect-ratio deadband.

### 4.2. Flick Vector Evaluation (4-Quadrant Disambiguation — `FlickDisambiguator.kt`)
Evaluates $\theta = \operatorname{atan2}(-dy, dx)$ in degrees:
- **Flick Right:** $[-45^\circ, 45^\circ]$
- **Flick Up:** $(45^\circ, 135^\circ]$
- **Flick Down:** $[-135^\circ, -45^\circ)$
- **Flick Left:** Remaining angles ($> 135^\circ$ or $< -135^\circ$)

---

## 5. Core System Button State Machines

- **Key ↵ (ENTER) (`EnterKeyHandler.kt`):** If composing, commits Candidate 1 and calls `finishComposingText()`. Resolves effective action respecting `IME_FLAG_NO_ENTER_ACTION` and custom `actionId`; executes `performEditorAction()`, commits `"\n"` if multi-line, or dispatches `KEYCODE_ENTER` down/up on single-line fields without explicit action.
- **Key ⇧ (SHIFT) (`ShiftController.kt` + `AutoCapsController.kt`):** Cycles `LOWERCASE` $\to$ `TITLECASE` $\to$ `UPPERCASE` $\to$ `LOWERCASE`. Double-tap ($\le 350\text{ms}$), Flick Up, or Long Press locks Caps Lock. `TITLECASE` auto-resets to `LOWERCASE` upon character/word commit. `AutoCapsController` inspects `InputConnection.getCursorCapsMode()` unless excluded (passwords, email, URI, filter).
- **Key ⌫ (DEL) (`DeleteController.kt`):** Deletes active selection first; if composing, pops last stroke from `DawgEngine` in $O(1)$; otherwise deletes UTF-16 surrogate pair (`Character.isSurrogatePair`) or single char (with `KEYCODE_DEL` fallback when buffer is empty). *(Retroactive Un-Commit logic is implemented in `DeleteController` behind `enableRetroactiveUncommit = false`).*

---

## 6. Typing Engines & Predictive Pipelines

### 6.1. Multi-Tap (ABC) Cycling Engine (`MultiTapEngine` + `MultiTapController`)
Lowercase-first cycling decoupled from Shift state:
- **`[ 1 ]`**: `.` $\to$ `,` $\to$ `?` $\to$ `!` $\to$ `'` $\to$ `@` $\to$ `#` $\to$ `1`
- **`[ 2 ]`–`[ 9 ]`**: `abc2`, `def3`, `ghi4`, `jkl5`, `mno6`, `pqrs7`, `tuv8`, `wxyz9`
- **`[ 0 ]`**: `' '` $\to$ `'0'` $\to$ `'\n'`
- **Timeout:** Configurable (`400–1000ms`, default `600ms`). Flushes completed letters (`len >= 2`) to `LearnedWordsRepository`.

### 6.2. Predictive T9 & Post-Processing Pipeline
1. **Native C++ T9 Engine (`DawgEngine::pushStroke`):**
   - Traverses `OPENT9D2` T9-digit-indexed minimal DAFSA (`MAX_BEAM_SIZE = 32`, `MAX_CANDIDATES = 16`, `MAX_SPATIAL_EDITS = 1`).
   - Evaluates touched digit plus spatially nearby neighbor hypotheses (`SpatialScorer`, `sigma = 16.0f * density`) with Unified Log-Linear scoring (corpus `log_freq` + `LEX_FLAG_SLANG` boost + decayed user hits + spatial likelihood - edit penalty).
   - Merges matching canonical words from `DynamicStore` (in-memory T9 prefix index, exponential half-life decay) and filters `isDeleted()` blacklisted words before `MAX_CANDIDATES` truncation.
   - Reads O(1) pre-cached top-16 subtree completions (`depth <= 6`) or runs fixed-stack best-first completion (`collectBestCompletions`) for deeper prefixes.
   - Runs `findClosestWords()` (overtype retention + 1-digit adjacent-key substitution) when `candCount == 0`.
2. **Kotlin Prediction Layer (`prediction/`):**
   - `EnglishOrthography`: Restores fallback contractions (`dont` $\to$ `don't`, `im` $\to$ `I'm`), standalone `i` $\to$ `I`, and expands ambiguous pairs (`its`/`it's`) when not already supplied by `OPENT9D2`.
   - `IndonesianOrthography`: Synthesizes productive suffixes (`-nya`, `-kan`, `-lah`, `-kah`, `-pun`, `-ku`, `-mu`) when typing beyond a known root word.
   - `LearnedWordsRepository`: Delegates usage and removal calls to native `DynamicStore` with debounced background `flushIfDirty()` persistence.
   - `SuggestionGuard`: Holds last valid candidate/prefix state or projects OOV fallback words using language-specific character-bigram transition tables (`EN` and `ID`).
   - `WordCorrectionController`: Scans word under cursor or selection during `onUpdateSelection` and displays T9 replacement candidates in the suggestion strip.
   - `PredictionContextHelper`: Extracts preceding word tokens from `InputConnection` (respecting sentence boundaries and password/numeric exclusions) to power **Idle NWP** (non-highlighted chips after space/commit) and **Contextual T9 Disambiguation** (`OPENT9N1` + personal bigrams).

---

## 7. EditorInfo & InputType Dynamic Routing (`EditorContextHelper.kt`)

Evaluated in `OpenT9InputMethodService.onStartInputView(info, restarting)`:
- **Password Fields (`TYPE_TEXT_VARIATION_PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `NUMBER_VARIATION_PASSWORD`):** Forces `PAGE_0_TEXT` with `isT9Mode = false` (Multi-Tap ABC), disables word correction & learning writes.
- **Numeric / Phone / Datetime Fields (`TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, `TYPE_CLASS_DATETIME`):** Opens `PAGE_1_NUM_SYM` with `isT9Mode = false`.
- **Incognito Mode (`IME_FLAG_NO_PERSONALIZED_LEARNING` or password):** Suppresses all `LearnedWordsRepository` / `DynamicStore` recording.

---

## 8. Keyboard Settings Architecture & Dynamic Config Sync

### 8.1. `SettingsActivity` (`preferences.xml`)
- **Appearance & Theme:** `app_theme` (`system`, `light`, `dark`).
- **Audio & Haptics:** `haptic_enabled`, `vibration_intensity` (`5–100ms`, default `25ms`, manual input dialog), `audio_enabled`, `audio_volume` (`0–100%`, default `60%`), `audio_style` (`Mechanical Classic`, `Modern Soft`, `Minimalist Click`).
- **Layout & Display:** `keyboard_height` (`220–320dp`, default `260dp`), `candidate_font_size` (`14–22sp`, default `16sp`), `one_handed_mode` (`Disabled`, `Left-Handed`, `Right-Handed`), `rearrange_keyboard_layout` (launches `RearrangeLayoutActivity`).
- **Typing Behaviors:** `default_input_mode` (`T9` / `ABC`), `long_press_delay` (`200–600ms`, default `350ms`), `multi_tap_timeout` (`400–1000ms`, default `600ms`), `auto_caps`, `auto_space`, `double_space_period`, `space_scrubbing`, `space_scrubbing_sensitivity` (`normal`, `low`, `high`), `space_scrubbing_hold` (`150ms` hold toggle).
- **Dictionary & Language:** `startup_language` (`ID` / `EN`), `bilingual_prediction` (default `false`), `slang_boost`, `decay_half_life` (`14 Days`, `30 Days`, `Disabled`), `reset_user_dictionary`. *(Pending: Individual User Dictionary Editor UI).*

### 8.2. C++ `NativeConfig` Synchronization (`native_config.hpp`)
```cpp
struct NativeConfig {
    float touch_variance_sigma = 42.0f;
    uint32_t long_press_timeout_ms = 350;
    uint32_t multi_tap_timeout_ms = 600;
    uint8_t auto_space_enabled = 1;
    uint8_t slang_boost_enabled = 1;
    uint32_t decay_half_life_days = 30;
    uint8_t audio_enabled = 1;
    float audio_volume = 0.6f;
    uint32_t audio_style = 0; // 0=Classic, 1=Soft, 2=Click
    uint8_t bilingual_enabled = 0; // 0 or 1
};
```

---

## 9. Performance Targets & Current Reality

| Metric | Target Spec | Current Codebase Status |
| :--- | :--- | :--- |
| **Disambiguation Latency** | `< 0.5 ms` | Verified in `native_tests/test_native_core.cpp` (`< 200 µs` target for `4663`). |
| **Rollback Execution Time** | `< 0.01 ms` | $O(1)$ stack index decrement in `DawgEngine::popStroke()`. |
| **Lexicon Pointer Swap** | `< 0.05 ms` | Verified in `test_native_core.cpp` via `LexiconManager::switchLanguage()`. |
| **Audio Feedback Latency** | `< 10 ms` | Google Oboe `LowLatency` shared float stream (`AudioEngine`). |
| **Lexicon Loading & Working Set** | Zero-copy `mmap` (`noCompress`), no strict MB cap | `en_lexicon.dawg` (`9.1 MB`) + `id_lexicon.dawg` (`10.9 MB`) = `20.0 MB` (`OPENT9D2`) mapped via `openFd` (`noCompress` in `app/build.gradle.kts`); strict `< 10 MB` APK cap lifted so lexicon richness, cached completions, and N-gram tables are prioritized while keeping active working-set RSS compact. |
| **Hot-Path Allocation / I/O** | 0 alloc / 0 disk I/O | **Done (Phase 1):** `DawgEngine::pushStroke` uses fixed stack pools (`terminalPool`/`candidatesPool`), `DynamicStore` uses in-memory T9 prefix index + debounced background `flushIfDirty()`. |

---

## 10. Repository Layout (Actual)

```text
t9Keyboard-V2/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml                   # No INTERNET permission
│       │   ├── assets/dictionaries/
│       │   │   ├── en_lexicon.dawg                   # 9.1 MB OPENT9D2 T9-digit DAFSA
│       │   │   ├── id_lexicon.dawg                   # 10.9 MB OPENT9D2 T9-digit DAFSA
│       │   │   ├── en_bigrams.bin                    # OPENT9N1 mmap bigram table (EN)
│       │   │   └── id_bigrams.bin                    # OPENT9N1 mmap bigram table (ID)
│       │   ├── cpp/
│       │   │   ├── CMakeLists.txt
│       │   │   ├── include/                          # dawg_engine, dynamic_store, lexicon_manager, spatial_scoring, multi_tap_engine, audio_engine, native_config
│       │   │   └── src/                              # Implementations + jni_bridge.cpp
│       │   ├── java/com/opent9/keyboard/
│       │   │   ├── OpenT9InputMethodService.kt       # Slim IME lifecycle wrapper
│       │   │   ├── T9ComposingCoordinator.kt         # Composing, commit, pop/rehydrate state
│       │   │   ├── KeyboardActionDispatcher.kt       # Tap/flick/long-press routing
│       │   │   ├── CursorSelectionCoordinator.kt     # onUpdateSelection disambiguation
│       │   │   ├── LanguageCycleHelper.kt
│       │   │   ├── CandidateRemovalHelper.kt
│       │   │   ├── EmojiActionDispatcher.kt
│       │   │   ├── ImeLifecycleConfigurator.kt
│       │   │   ├── ComposingStroke.kt
│       │   │   ├── jni/NativeEngineBridge.kt
│       │   │   ├── lexicon/LexiconLoader.kt
│       │   │   ├── prediction/                       # CandidateResolver, EnglishOrthography, IndonesianOrthography, LearnedWordsRepository, SuggestionGuard, WordCorrectionController, WordCorrectionHelper
│       │   │   ├── settings/                         # SettingsActivity (+SettingsFragment), SettingsObserver, RearrangeLayoutActivity, RearrangeKeyboardView, KeyboardLayoutConfig, ManualInputSeekBarPreference
│       │   │   └── ui/                               # T9KeyboardView, KeyAtlas, EmojiAtlas, TouchGestureTracker + sub-trackers, Page0/1/23/Emoji/Strip renderers, controllers, themes, icons
│       │   └── res/                                  # xml/preferences.xml, xml/method.xml, values/, values-in/, layout/, drawable/
│       └── test/java/com/opent9/keyboard/            # 19 JVM / Robolectric unit, integration & contract test suites
├── native_tests/                                     # C++ tests & profiling harnesses
└── tools/
    ├── check_doc_sync.sh                             # Contract & prd.md sync verification script
    └── dawg_compiler/                                # dawg_builder.cpp, lexicon_extractor.cpp, en_curated.tsv, id_curated.tsv
```

---

## 11. Implementation Status & Remaining Gaps

### Completed (Beyond Original PRD Scope)
- Modularized IME architecture (`T9ComposingCoordinator`, `KeyboardActionDispatcher`, `CursorSelectionCoordinator`, dedicated renderers & gesture sub-trackers).
- Retrospective word correction (`WordCorrectionController`) & language-specific orthography (`EnglishOrthography`, `IndonesianOrthography`, `SuggestionGuard`).
- Interactive Row 4 & Column 4 key rearrangement (`RearrangeLayoutActivity`, `RearrangeKeyboardView`).
- 5-column calculator-style Page 1 with scrollable operator column & 10-column Gboard-style Page 2/3 symbols.
- Light / Dark / System theme support (`KeyboardTheme`), One-Handed mode, landscape height clamping, tablet ergonomic centering, and `ManualInputSeekBarPreference`.
- Configurable Spacebar scrubbing sensitivity & 150ms hold option (`SpaceScrubTracker`).
- Legacy Page 0 Key 15 (`EMOJI_DOT`), old Page 1 flick IDs, and 3×3 `symbolLayers` dead code removed; `ContractConsistencyTest` + `tools/check_doc_sync.sh` added.
- **Prediction Phase 1:** Unified Log-Linear scoring (removing absolute `last_used_timestamp` & `opent9_last_picked` recency hijacking), multi-key `SpatialScorer` border hypotheses, DPI-normalized `touch_variance_sigma` (`16dp * density`), `DynamicStore` in-memory T9 prefix indexing + debounced async `flushIfDirty()`, fixed-stack `DawgEngine::pushStroke` pools, `LexiconManager` `is_mmap` safety, `std::shared_mutex` JNI guard, and complete `reset_user_dictionary` cleanup (`LearnedWordsRepository.clearAll()`).
- **Prediction Phase 2:** T9-digit-indexed minimal DAFSA (`OPENT9D2`, naturally 48-byte/16-byte aligned, quantized `log_freq`, `LEX_FLAG_SLANG`, cached depth `<= 6` prefix completions, `Word` display forms with apostrophes/hyphens), `DynamicStore` canonical normalization, removal of Kotlin hardcoded homograph promotions (`homographPromotions` / `promoteCandidate`), and character-bigram fallback tables in `SuggestionGuard`.
- **Prediction Phase 3:** Next-Word Prediction (`OPENT9N1` `mmap` bigram tables + `DynamicStore` personal user bigrams), `NgramEngine`, `PredictionContextHelper`, Idle NWP suggestion strip (`isIdlePrediction` non-highlighted chips; Spacebar preserved for normal spacing), and contextual T9 disambiguation (`setContextWord`).
- **Prediction Phase 4:** Bilingual code-switching (`bilingual_prediction` preference + `DawgEngine` `primary`/`secondary` coordinator with `SECONDARY_LANG_PENALTY`) and sentence-level regression + p99 latency benchmark (`test_native_core`).
- **Button & Gesture Consistency Hardening:** Incognito/password isolation in `MultiTapController`, preserved session `isT9Mode` and password/numeric guards across page switches, `FlickDisambiguator.isLongPressSupported` scoping (settings long-press restricted to Page 0 Key 12 `?123`), long-press vs flick & space-scrubbing guards in `TouchGestureTracker`, multi-tap commit unification on flick/long-press/repeat-delete, Emoji `DEL`/`SPACE` behavioural parity, Idle NWP `TITLECASE` key label fix, One-Handed Page 0 gutter hit-testing guard, and preserved Page 0 `digitValue` metadata on Page 1 reload.

### Pending / Known Gaps
1. **User Dictionary Editor:** C++ `serializeAllWords()` / `addWord()` lack JNI bindings and Settings UI.
2. **Emoji Picker Completeness:** Persist `recentEmojis` (frecency), implement long-press Fitzpatrick skin-tone strip, remove duplicate emojis in `EmojiAtlas`, add `Paint.hasGlyph()` / `EmojiCompat` filtering, bitmap glyph caching, and `OverScroller` fling physics.
4. **Settings Exposure:** Expose `Retroactive Un-Commit` (`DeleteController`) in `preferences.xml`.
5. **Audio, Accessibility & Localization:** Check `AudioManager.ringerMode` and auto-idle Oboe stream; add `ExploreByTouchHelper` for TalkBack; complete `values-in/strings.xml` translations and add `in_ID` subtype in `method.xml`; configure proper release signing in `app/build.gradle.kts`.
