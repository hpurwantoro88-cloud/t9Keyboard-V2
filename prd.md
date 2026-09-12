
Gemini

Chat

Spark
beta
New chat
Search chats
Images
Videos
Library
Gems
New notebook
SoftwareDeveloper
InginTau
All notebooks
Berbagi Langganan Gemini Pro Keluarga
OpenT9 v3.0 PRD Markdown Generation
SwiftKey and Gboard PRD Review
Why Oil Prices Are Rising
Troubleshooting Gemini Freezing on Web Development Research
Fungsi Rescue Tripod Ruang Terbatas
Optimasi Email Request Approval Baterai Laptop
Bahasa Pemrograman Populer Pengembangan Software
Kuota Gemini dan Antigravity Pro
Analisis Keaslian Foto Sosok Transparan
Ticket Resolution: OTP and MFA Setup
Koreksi Istilah MFA Gmail
Desain Ikon Aplikasi Remote Touchpad
Solusi Error 500121 Microsoft MFA
Touchpad Eksternal Windows dan Linux
Perbedaan Notebook Gemini dan NotebookLM
Menyelidiki Reputasi Toko Tokopedia
Analisis Keamanan Toko Preorder Tokopedia
Koreksi Draf Email Approval PR
Analisis Foto Erupsi Gunung Kelud
Browser Android Address Bar di Bawah
Analisis Keamanan Tautan Google OAuth
Dilema IT: Pensiun dan Data Sensitif
Cleanup Broken Oracle Installation on Windows 11
Rekomendasi Harga Pembelian Saat Ini
Fixing Corrupted MFT Record in Windows 11
Produk Google untuk Desain Web
Connecting Instagram to Gemini
Analisis Peningkatan Performa Apple M-Series
Cara List Folder di Windows
Legalitas Backup Data Pribadi Karyawan
Kondisi Terkini Perekonomian Indonesia
Pemberlakuan PPN 11 Persen
Perbaikan Diagram Siklus Aset
Conversation with Gemini
Product Requirements Document (PRD): OpenT9 Core Native Keyboard (v3.0)

Document Identifier: prd3.md

Package Identifier: com.opent9.keyboard


Architecture Class: Native Hybrid Systems Architecture (Thin Kotlin IME Wrapper + C++20 Core via NDK + Zero-Allocation Hardware Canvas)

Target Benchmarks: Microsoft SwiftKey, Gboard (3×4 Mode), Samsung Keyboard (3×4 Mode)

1. Executive Summary & Core Value Proposition

OpenT9 Core delivers an ultra-lightweight, zero-latency Android T9 predictive keyboard that decisively outperforms OEM solutions across memory footprint, cold-start latency, frame rendering consistency, and typing disambiguation speed.

Core Differentiators

Sub-1ms Predictive Disambiguation: C++20 beam search traversing pre-indexed Directed Acyclic Word Graphs (DAWG) without JVM pauses.

Deterministic Fallback (Multi-Tap ABC Mode): Instant toggling between predictive T9 and classic lowercase-first multi-tap for unlisted acronyms, passwords, and custom codes.

Spatial-Symmetric Flick Selection: Dual-symbol keys map physical left/right flick directions directly to on-key visual glyph positions.

Dynamic Auto-Fit Swipeable Suggestion Strip: Horizontally scrollable 40dp candidate bar eliminating rigid candidate pagination and secondary grid overlays.

Polymorphic System Keys: State-machine-driven implementations for Key ↵ (ENTER), Key ⇧ (SHIFT), and Key ⌫ (DEL) adapting across EditorInfo configurations.

Single-Tag Language Toggle with Active T9 Glow Bar: Minimalist visual display showing active language code (EN or ID) with an illuminated neon indicator bar when T9 prediction is active.

Continuous Spacebar Trackpad Scrubbing: Horizontal cursor positioning directly from Key 0.

Dedicated Native Settings Architecture: SharedPreferences persistence coupled with zero-allocation C++ memory synchronization.

100% Offline Privacy: Zero network permissions (android.permission.INTERNET strictly omitted).

2. Technical Stack & Architectural Constraints

Declarative UI frameworks (Jetpack Compose, Flutter) and nested XML view hierarchies are strictly prohibited in the core typing loop.

LayerTechnology SelectionArchitectural InvariantIME Service WrapperKotlin (InputMethodService)

Minimal OS wrapper for window lifecycle, focus handling, and InputConnection transactions. Free of dictionary search or parsing logic.

Prediction & Scoring CoreC++20 (Android NDK via CMake)

Executes beam search, Gaussian spatial scoring, candidate ranking, and state rollbacks. Zero dynamic heap allocations in hot paths.

Static LexiconsMemory-Mapped Files (mmap) + DAWG


English and Indonesian binary DAWGs mapped directly from storage into Linux page cache.

Dynamic VocabularyEmbedded LMDB / Native SQLite


Key-value store for user slang, contact names, and frequency overrides with exponential half-life decay.

UI & Render PipelineSingle Custom View (Canvas.onDraw)


Hardware-accelerated canvas backed by pre-allocated primitive arrays (FloatArray, IntArray). 0 bytes allocated in onTouchEvent() and onDraw().

JNI BridgeDirect ByteBuffer & Primitives


Touch coordinates and keycodes sent as primitives; candidate offsets returned via direct memory buffers to prevent java.lang.String churn.

Audio EngineGoogle Oboe C++ (AAudio / OpenSL ES)

Direct hardware audio driver access delivering click feedback in $<10\text{ms}$.

Configuration SyncDirect JNI Primitive StructAsynchronous SharedPreferences observer synchronizing runtime parameters directly to native C++ global state.

3. UI Layout & Keypad Specifications

The entire keyboard surface is fixed at a 260dp overall height budget (40dp suggestion strip + 220dp key grid across 4 rows of 55dp each) on standard 1080×2400 displays. Key columns occupy exactly 25% of the screen width (~90–100dp).

+-------------------------------------------------------------------------+

| [T9] | 1. selamat | 2. selalu | 3. selain | 4. seluler | 5. ... | <-- Swipeable Strip (40dp)

+-------------------------------------------------------------------------+

| [ 1 .,?!' ] | [ 2 ABC ] | [ 3 DEF ] | [ ⌫ DEL ] | <-- Row 1 (55dp)

+---------------+---------------+---------------+---------------+

| [ 4 GHI ] | [ 5 JKL ] | [ 6 MNO ] | [ ⇧ SHIFT ] | <-- Row 2 (55dp)

+---------------+---------------+---------------+---------------+

| [ 7 PQRS ] | [ 8 TUV ] | [ 9 WXYZ ] | [ ↵ ENTER ] | <-- Row 3 (55dp)

+---------------+---------------+---------------+---------------+

| [ ?123 ] | [ EN ] | [ 0 ␣ SPACE ] | [ 😊 / . ] | <-- Row 4 (55dp)

| | [ ========= ] | | |

+-------------------------------------------------------------------------+

3.1. Suggestion & Candidate Strip Layout (40dp)

The suggestion strip replaces rigid slots with a Horizontally Scrollable Dynamic-Width Canvas Strip:

Fixed Left Pill (12% Width): Displays active predictive mode: [ T9 ] or [ ABC ]. Tapping this pill toggles between Predictive T9 and Literal Multi-Tap.

Scrollable Candidate Viewport (88% Width):

Renders candidates continuously from left to right.

Candidate item widths are dynamically measured based on word character count via primaryTextPaint.measureText(candidate) + paddingHorizontal (minimum width: 64dp).

Smooth finger dragging pans candidates left and right across the entire beam search result list (up to 16 candidates).

Vertical separator bars (dividerPaint) are drawn between adjacent candidate words.

Tapping any candidate immediately commits it to the host input field.

3.2. Page 0: Primary T9 Text & Prediction Layer

Row 4 Key 2: Language & T9 Glow Indicator Specification

Display Text: Renders only the active language code: EN or ID (never dual labels like EN / ID).

Visual Glow Bar:

Rendered as an illuminated horizontal indicator bar directly beneath the language text ($y + 14\text{dp}$, height $3\text{dp}$, width $28\text{dp}$, corner radius $1.5\text{dp}$).

When T9 is Active: Rendered using indicatorGlowPaint (Primary Accent Color, #00E5FF or #4CAF50, with anti-aliased fill).

When T9 is Inactive (Multi-Tap ABC Mode): The indicator bar is completely omitted (or rendered fully transparent), providing instantaneous visual verification of the active typing engine.

Interactions:

Primary Tap: Instant language hot-swap (EN $\leftrightarrow$ ID). Performs zero-allocation pointer swap in C++ core and re-evaluates current composing buffer.

Flick Up: Toggles typing mode between T9 and ABC (instantly turns the indicator glow bar on or off).

Long Press: Launches Keyboard Settings Activity.

Complete Page 0 Key Interaction & Gesture Matrix

KeyPrimary TapFlick UpFlick DownFlick LeftFlick RightLong Press (>350ms)[ 1 .,?!' ]


Contextual separator (. / , / ?)

Insert literal 1


Symbol popup bar

Insert ,Insert .Lock literal 1


[ 2 ABC ] to [ 9 WXYZ ]


Push digit to T9 / Multi-tap char

Insert literal digit

Diacritic picker

——Lock literal digit

[ ⌫ DEL ]


Delete char / DAWG rollback

Delete preceding word

Clear entire field

Delete word—Continuous accelerated delete

[ ⇧ SHIFT ]


Cycle Lower $\to$ Title $\to$ UpperToggle Caps Lock

———Lock UPPERCASE

[ ↵ ENTER ]


Polymorphic EditorInfo Action

Force submitInsert Tab (\t)

——Insert newline (\n)

[ ?123 ]


Switch to Page 1 (Symbols)

—Switch to Page 2

——Open Settings

[ EN ] / [ ID ]Hot-swap Language Pointer

Toggle T9 / ABC mode———Open Settings

[ 0 ␣ SPACE ]


Commit Candidate 1 + Space

Insert literal 0


Non-breaking space

Scrub Cursor LeftScrub Cursor RightSpace repeat / Scrub[ 😊 / . ]


Switch to Page 3 (Emoji Canvas)

Commit . + Space

Commit , + Space

Reduplication (-)

Insert .Recent emojis bar

3.3. Page 1: Numeric & Direct Symbols (?123)

Activated via [ ?123 ]. Bypasses T9 predictive traversal; all alphanumeric keys output literal characters.

+-------------------------------------------------------------------------+

| [SYM] | @ | # | _ | / | \ | & | % |

+-------------------------------------------------------------------------+

| [ 1 ] | [ 2 ] | [ 3 ] | [ ⌫ DEL ] | <-- Row 1 (55dp)

+---------------+---------------+---------------+---------------+

| [ 4 ] | [ 5 ] | [ 6 ] | [ + - ] | <-- Row 2 (55dp)

+---------------+---------------+---------------+---------------+

| [ 7 ] | [ 8 ] | [ 9 ] | [ ↵ ENTER ] | <-- Row 3 (55dp)

+---------------+---------------+---------------+---------------+

| [ ABC ] | [ =\< ] | [ 0 ␣ SPACE ] | [ . , ] | <-- Row 4 (55dp)

+-------------------------------------------------------------------------+

Dual-Key Flick Mappings (Page 1)

[ + - ]:

Visual Layout: + on left, - on right.

Tap: Inserts +.

Flick Left: Inserts +.

Flick Right: Inserts -.

Flick Down: Inserts _.

Long Press: Inserts ±.

[ . , ]:

Visual Layout: . on left, , on right.

Tap: Inserts ..

Flick Left: Inserts ..

Flick Right: Inserts ,.

Flick Down: Inserts :.

Long Press: Inserts ;.

3.4. Page 2: Extended Symbols & Math (=\<)

Activated via [ =\< ] on Page 1. Dual-symbol keys strictly align physical gesture directions with on-screen visual positions.

+-------------------------------------------------------------------------+

| [EXT] | ~ | ` | | | ^ | ° | § | = |

+-------------------------------------------------------------------------+

| [ ~ ` ] | [ \ | ] | [ { } ] | [ ⌫ DEL ] | <-- Row 1 (55dp)

+---------------+---------------+---------------+---------------+

| [ [ ] ] | [ < > ] | [ ( ) ] | [ × ÷ ] | <-- Row 2 (55dp)

+---------------+---------------+---------------+---------------+

| [ % ‰ ] | [ Rp $ ] | [ € £ ] | [ ↵ ENTER ] | <-- Row 3 (55dp)

+---------------+---------------+---------------+---------------+

| [ ABC ] | [ 123 ] | [ 0 ␣ SPACE ] | [ " ' ] | <-- Row 4 (55dp)

+-------------------------------------------------------------------------+

Spatial-Symmetric Flick Mapping Matrix (Page 2)

Key DisplayPrimary TapFlick Left (Left Glyph)Flick Right (Right Glyph)Flick Down (Paired / Secondary)[ ~ ]`


Inserts ~Inserts ~Inserts `Inserts ^[ \ | ]


Inserts \Inserts \Inserts |Inserts /[ { } ]


Inserts {


Inserts {Inserts }


Inserts {} + cursor inside

[ [ ] ]


Inserts [


Inserts [Inserts ]


Inserts [] + cursor inside

[ < > ]


Inserts <Inserts <Inserts >Inserts <> + cursor inside[ ( ) ]


Inserts (


Inserts (Inserts )


Inserts () + cursor inside

[ × ÷ ]


Inserts ×Inserts ×Inserts ÷Inserts *[ % ‰ ]


Inserts %Inserts %Inserts ‰Inserts /[ Rp $ ]


Inserts active currency

Inserts Rp Inserts $Inserts ¢[ € £ ]


Inserts €Inserts €Inserts £Inserts ¥[ " ' ]


Inserts "Inserts "Inserts 'Inserts "" + cursor inside

3.5. Page 3: In-Canvas Native Emoji Picker

Direct in-canvas rendering avoiding Android RecyclerView overhead.

+-------------------------------------------------------------------------+

| [🕒] | [😀] | [👍] | [🐱] | [🍔] | [⚽] | [🚗] | [💡] | [❤️] | <-- Category Tabs (40dp)

+-------------------------------------------------------------------------+

| 😀 | 😃 | 😄 | 😁 | 😆 | 😅 | 😂 | <-- Row 1 (45dp)

+------+------+------+------+------+------+------+

| 🤣 | 🥲 | 🥹 | 😊 | 😇 | 🙂 | 🙃 | <-- Row 2 (45dp)

+------+------+------+------+------+------+------+

| 😉 | 😌 | 😍 | 🥰 | 😘 | 😗 | 😙 | <-- Row 3 (45dp)

+------+------+------+------+------+------+------+

| 😚 | 😋 | 😛 | 😝 | 😜 | 🤪 | 🤨 | <-- Row 4 (45dp)

+------+------+------+------+------+------+------+

| [ ABC ] | [ 🕒 Recents ] | [ ␣ Space ] | [ ⌫ DEL ] | <-- Control Row (40dp)

+-----------+------------------+----------------+---------------+

Rendered from a pre-compiled flat table of 32-bit Unicode codepoints using a shared 2-element CharArray scratch buffer.

Long-press opens an in-canvas Fitzpatrick skin-tone modifier strip.

4. Touch Handling, Multi-Touch & Swipeable Strip State Machine

To eliminate event collisions between candidate horizontal scrolling and keypad vertical flicks, touch handling is partitioned by physical $Y$ bounds:

[MotionEvent.ACTION_DOWN]

│

┌──────────────────────────┴──────────────────────────┐

▼ ▼

Y < 40dp (Candidate Strip) Y >= 40dp (Keypad Area)

│ │

Store startX, startScrollX Store (x0, y0), t0

│ Start Long-Press Timer (350ms)

[ACTION_MOVE] │

Compute dx = x - startX ┌────────┼────────┐

Update strip scrollOffset ▼ ▼ ▼

Clamp to [maxScroll, 0] [MOVE] [TIMEOUT] [UP]

Invalidate strip region dx/dy>12dp >350ms <350ms

4.1. Gesture Detection Constants

Kotlin



private const val TOUCH_SLOP_PX = 36f // ~10dp threshold for tap stationary boundsprivate const val FLICK_DISTANCE_MIN_PX = 54f // Minimum travel to fire a directional flickprivate const val FLICK_TIME_WINDOW_MS = 180L // Max duration for a high-velocity flickprivate const val LONG_PRESS_TIMEOUT_MS = 350L // Hold duration for repeat / alternate modeprivate const val REPEAT_TICK_INTERVAL_MS = 50L // Repeat rate for continuous deletionprivate const val SCRUB_STEP_PX = 32f // Trackpad cursor sensitivity

4.2. Flick Vector Evaluation (4-Quadrant Disambiguation)

When $\vert{}dx\vert{} \ge \text{FLICK\_DISTANCE\_MIN\_PX}$ or $\vert{}dy\vert{} \ge \text{FLICK\_DISTANCE\_MIN\_PX}$ within $\text{FLICK\_TIME\_WINDOW\_MS}$, evaluate gesture angle $\theta = \operatorname{atan2}(-dy, dx)$:

Flick Right: $-45^\circ \le \theta < 45^\circ$

Flick Up: $45^\circ \le \theta < 135^\circ$

Flick Left: $135^\circ \le \theta < 225^\circ$ (or $\theta < -135^\circ$)

Flick Down: $-135^\circ \le \theta < -45^\circ$

4.3. Spacebar Cursor Scrubbing (Trackpad Mode)

Triggered when ACTION_MOVE originates on Key 0 ([ 0 ␣ SPACE ]) and $\vert{}dx\vert{} \ge \text{TOUCH\_SLOP\_PX}$.

Dispatches KEYCODE_DPAD_RIGHT ($dx > 0$) or KEYCODE_DPAD_LEFT ($dx < 0$) for every accumulated $\pm \text{SCRUB\_STEP\_PX}$.

Fires native low-latency mechanical audio/haptic click for every text position traversed.

5. Core System Button State Machines

5.1. Key ↵ (ENTER): Polymorphic Action Dispatcher

[Tap Key ↵ ENTER]

│

Is Composing Buffer Active (> 0)?

│

┌───────────────┴───────────────┐

Yes No

▼ ▼

Commit active Candidate 1 Read EditorInfo.imeOptions

Call finishComposingText() │

│ ┌───────────┴───────────┐

└──────────────────►▼ ▼

Has Action Flag? Multi-Line / No Action

│ │

▼ ▼

ic.performEditorAction() ic.commitText("\n", 1)

Icon Rendering: Canvas dynamically draws vector path based on EditorInfo.imeOptions:

IME_ACTION_SEARCH $\to$ Magnifying Glass.

IME_ACTION_GO / IME_ACTION_SEND $\to$ Rightward Arrow / Paper Plane.

IME_ACTION_NEXT $\to$ Tab Right Arrow.

IME_ACTION_DONE $\to$ Checkmark.

Default / Multi-line $\to$ Return Carriage Arrow.

5.2. Key ⇧ (SHIFT): 3-State Cycling Machine

┌───────── Tap Key ⇧ [SHIFT] ─────────┐

▼ │

[STATE: LOWERCASE] │

│ (Single Tap) │

▼ │

[STATE: TITLECASE] ──(Auto-resets to Lower) │ (Double-Tap or Flick-Up)

│ (Single Tap) │

▼ ▼

[STATE: UPPERCASE] <────────────────── [CAPS LOCK ACTIVE]

LOWERCASE: Standard lowercase candidate generation and multi-tap output. Key glyph: Hollow upward arrow outline (⇧).

TITLECASE: First letter capitalized; automatically reverts to LOWERCASE following the first character commit. Key glyph: Solid filled arrow (⬆).

UPPERCASE / CAPS LOCK: All characters capitalized. Key glyph: Solid filled arrow with horizontal base bar (⇪). Remains locked until [ ⇧ SHIFT ] is tapped again.

5.3. Key ⌫ (DEL): Unified Deletion Engine

┌─────────── [ Press ⌫ DEL ] ───────────┐

│ │

[Composing Buffer > 0] [Composing Buffer == 0]

│ │

Pop last digit from stack Inspect getTextBeforeCursor(2, 0)

C++ Engine pop_stroke() │

│ ┌──────────────┴──────────────┐

Re-score candidates & update ▼ ▼

Direct ByteBuffer buffer UTF-16 Surrogate? Standard Character

│ │ │

ic.setComposingText() deleteSurroundingText(2, 0) deleteSurroundingText(1, 0)

Retroactive Un-Commit: If ⌫ DEL is pressed immediately following a word commit via [ SPACE ], the trailing space and word are removed, and the raw digit sequence is re-hydrated into the active composing buffer.

Continuous Accelerated Delete:

$0–350\text{ms}$: Single character deletion.

$350–1200\text{ms}$: Repeating deletion (every $50\text{ms}$).

$>1200\text{ms}$: High-speed tokenized whole-word deletion.

6. Typing Engines & Predictive Pipelines

6.1. Multi-Tap (ABC) Cycling Engine (No-Caps Cycle)

When T9 prediction is toggled off, keys cycle strictly through lowercase characters and numbers. Capitalization is governed exclusively by the Shift Key State.

Deterministic Multi-Tap Mapping Matrix

KeyPrimary Cycle Sequence (Shift: Lowercase)Capitalized Cycle Sequence (Shift: Title/Upper)[ 1 ]. $\to$ , $\to$ ? $\to$ ! $\to$ ' $\to$ - $\to$ @ $\to$ 1. $\to$ , $\to$ ? $\to$ ! $\to$ ' $\to$ - $\to$ @ $\to$ 1[ 2 ]a $\to$ b $\to$ c $\to$ 2A $\to$ B $\to$ C $\to$ 2[ 3 ]d $\to$ e $\to$ f $\to$ 3D $\to$ E $\to$ F $\to$ 3[ 4 ]g $\to$ h $\to$ i $\to$ 4G $\to$ H $\to$ I $\to$ 4[ 5 ]j $\to$ k $\to$ l $\to$ 5J $\to$ K $\to$ L $\to$ 5[ 6 ]m $\to$ n $\to$ o $\to$ 6M $\to$ N $\to$ O $\to$ 6[ 7 ]p $\to$ q $\to$ r $\to$ s $\to$ 7P $\to$ Q $\to$ R $\to$ S $\to$ 7[ 8 ]t $\to$ u $\to$ v $\to$ 8T $\to$ U $\to$ V $\to$ 8[ 9 ]w $\to$ x $\to$ y $\to$ z $\to$ 9W $\to$ X $\to$ Y $\to$ Z $\to$ 9[ 0 ] (space) $\to$ 0 $\to$ \n (space) $\to$ 0 $\to$ \n

Cycle Expiration: $600\text{ms}$ timeout or tapping a different key commits the active character.

Auto-Reset: If Shift is TITLECASE, committing the first character automatically resets Shift to LOWERCASE.

6.2. Predictive T9 Engine (Beam Search & DAWG)

Traverses pre-compiled binary DAWG nodes in C++20.

2D Gaussian Touch Scoring:

$$P(\text{key}_k \mid x, y) = \exp\left( - \frac{(x - x_k)^2 + (y - y_k)^2}{2\sigma^2} \right)$$

Candidate strings are decoded into a Direct ByteBuffer with zero JVM object allocation.

7. EditorInfo & InputType Dynamic Routing Engine

Evaluated in InputMethodService.onStartInputView(info: EditorInfo, restarting: Boolean):

Password Fields (TYPE_TEXT_VARIATION_PASSWORD, TYPE_NUMBER_VARIATION_PASSWORD):

Beam search engine: DISABLED.

Suggestion Strip: Hidden.

LMDB learning: DISABLED (zero writes).

Switches automatically to literal Multi-Tap or numeric layout.

Numeric Fields (TYPE_CLASS_NUMBER, TYPE_CLASS_PHONE):

Displays Page 1 (Numeric) by default.

Incognito Mode (IME_FLAG_NO_PERSONALIZED_LEARNING):

Bypasses user dictionary logging and frequency updates.

8. Keyboard Settings Architecture & Dynamic Config Sync

8.1. SettingsActivity Hierarchy & Preferences

A native Android settings interface structured via PreferenceFragmentCompat:

Haptics & Audio Feedback:

Haptic Feedback: Toggle (On/Off).

Vibration Intensity: Slider ($0\text{ms}$ to $100\text{ms}$, default: $25\text{ms}$).

Audio Click Feedback: Toggle (On/Off).

Sound Volume: Slider ($0\%$ to $100\%$, default: $60\%$).

Sound Style: Dropdown (Mechanical Classic, Modern Soft, Minimalist Click).

Layout & Display:

Keyboard Overall Height: Slider ($220\text{dp}$ to $320\text{dp}$, default: $260\text{dp}$).

Candidate Font Size: Slider ($12\text{sp}$ to $18\text{sp}$, default: $14\text{sp}$).

One-Handed Mode: Dropdown (Disabled, Left-Handed, Right-Handed).

Typing Behaviors:

Default Input Mode: Dropdown (Predictive T9, Multi-Tap ABC).

Long-Press Delay: Slider ($200\text{ms}$ to $600\text{ms}$, default: $350\text{ms}$).

Multi-Tap Commit Timeout: Slider ($400\text{ms}$ to $1000\text{ms}$, default: $600\text{ms}$).

Auto-Capitalization: Toggle (On/Off).

Auto-Space After Commit: Toggle (On/Off).

Double-Space Period: Toggle (On/Off).

Spacebar Cursor Scrubbing: Toggle (On/Off).

Dictionary & Language Management:

Primary Startup Language: Dropdown (Indonesian, English).

Slang / Bahasa Gaul Priority: Toggle (On/Off).

Dynamic Decay Half-Life: Dropdown (14 Days, 30 Days, Disabled).

User Dictionary Editor: Search, add, or purge learned custom words.

Reset User Dictionary: Hard reset clearing the dynamic LMDB database.

8.2. Zero-Allocation C++ Configuration Synchronization

Whenever preferences are updated, SettingsObserver flushes a flat C primitive struct across JNI into the native core:

C++



// native_config.hppstruct NativeConfig {

float touch_variance_sigma; // Default: 42.0f

uint32_t long_press_timeout_ms; // Default: 350

uint32_t multi_tap_timeout_ms; // Default: 600

uint8_t auto_space_enabled; // 0 or 1

uint8_t slang_boost_enabled; // 0 or 1

uint32_t decay_half_life_days; // Default: 30

};extern "C" JNIEXPORT void JNICALLJava_com_opent9_keyboard_jni_NativeEngineBridge_nativeSyncConfig(

JNIEnv* env, jobject thiz,

jfloat sigma, jint longPressMs, jint multiTapMs,

jboolean autoSpace, jboolean slangBoost, jint decayDays) {

g_config.touch_variance_sigma = sigma;

g_config.long_press_timeout_ms = longPressMs;

g_config.multi_tap_timeout_ms = multiTapMs;

g_config.auto_space_enabled = autoSpace ? 1 : 0;

g_config.slang_boost_enabled = slangBoost ? 1 : 0;

g_config.decay_half_life_days = decayDays;

}

9. Performance Benchmarks & Non-Functional Verification

Target MetricBenchmark (SwiftKey / Gboard)OpenT9 Target SpecVerification HarnessDisambiguation Latency


10–25ms< 0.5ms (C++20 Beam Search)Google Benchmark / NDK CI

Rollback Execution Time


Full re-traversal< 0.01ms ($O(1)$ Stack Pop)

Nanosecond Timing Test

Cold-Start Launch Time


200–400ms< 30msAndroid Macrobenchmark

RAM Footprint (Resident)


85 MB – 190 MB< 25 MB (mmap page cache)

Android Profiler Memory Track

Frame Render Latency


Janks on GC cycles8.3ms (Locked 120 FPS)


dumpsys gfxinfo framestats


Garbage Collector Churn


Variable allocations0 bytes / frame


ART Allocation Tracker

Audio Feedback Latency


35–60ms (SoundPool)

< 10ms (Oboe / AAudio)

Low-Latency Mic TraceAPK Binary Size


45 MB – 80 MB< 10 MB (Lexicons included)apkanalyzer CLI

10. Repository Layout

Plaintext



opent9-android/

├── app/

│ ├── build.gradle.kts

│ └── src/main/

│ ├── AndroidManifest.xml # No INTERNET permission

│ ├── java/com/opent9/keyboard/

│ │ ├── OpenT9InputMethodService.kt # Thin IME lifecycle wrapper

│ │ ├── settings/

│ │ │ ├── SettingsActivity.kt # Preferences Activity

│ │ │ └── SettingsFragment.kt # PreferenceFragmentCompat bindings

│ │ ├── ui/

│ │ │ ├── T9KeyboardView.kt # Zero-allocation custom Canvas View

│ │ │ ├── TouchGestureTracker.kt # Keypad & swipeable strip state machine

│ │ │ ├── KeyAtlas.kt # Pre-computed key bounds & glyph coordinates

│ │ │ └── EmojiAtlas.kt # UTF-32 emoji codepoint tables

│ │ └── jni/

│ │ └── NativeEngineBridge.kt # Direct ByteBuffer JNI wrappers

│ ├── cpp/

│ │ ├── CMakeLists.txt

│ │ ├── include/

│ │ │ ├── dawg_engine.hpp # DAWG parser & O(1) history stack

│ │ │ ├── spatial_scoring.hpp # 2D Gaussian touch scoring

│ │ │ ├── multi_tap_engine.hpp # Deterministic lowercase multi-tap

│ │ │ ├── lexicon_manager.hpp # Dual mmap pointer hot-swapper

│ │ │ ├── audio_engine.hpp # Google Oboe AAudio sound driver

│ │ │ ├── dynamic_store.hpp # LMDB decay storage

│ │ │ └── native_config.hpp # Flat config sync struct

│ │ └── src/

│ │ ├── dawg_engine.cpp

│ │ ├── spatial_scoring.cpp

│ │ ├── multi_tap_engine.cpp

│ │ ├── lexicon_manager.cpp

│ │ ├── audio_engine.cpp

│ │ └── jni_bridge.cpp

│ └── assets/dictionaries/

│ ├── en_lexicon.dawg # Static binary DAWG (~4.1MB)

│ └── id_lexicon.dawg # Static binary DAWG (~3.8MB)

11. Phased TDD Implementation Roadmap

Phase 1: Native Core & DAWG Engine (C++20)

└──> Phase 2: Touch Tracker & Swipeable Suggestion Strip

└──> Phase 3: System Buttons (ENTER, SHIFT, DEL) & Multi-Tap

└──> Phase 4: Settings Activity & Native Config Sync

└──> Phase 5: Audio Engine, Haptics & Packaging

Phase 1: Native Core & DAWG Engine (C++20)

Unit tests verifying sequence 4663 yields ["good", "home", "gone", "hood"] in $<0.2\text{ms}$.

Unit tests confirming pop_stroke() restores prior depth in $<0.01\text{ms}$.

Pointer swap benchmarking confirming $<0.05\text{ms}$ lexicon switching.

Phase 2: Touch Tracker & Swipeable Suggestion Strip

Implement TouchGestureTracker.kt with partitioned $Y < 40\text{dp}$ candidate strip scrolling.

Unit tests verifying spatial flick evaluation (left, right, up, down) matching dual-symbol layout.

Implement horizontal scroll and auto-fit width measurement in T9KeyboardView.kt.

Phase 3: System Keys & Multi-Tap Engine

Implement ENTER polymorphic action mapping for all EditorInfo action flags.

Implement SHIFT 3-state cycling machine (LOWER, TITLE, UPPER).

Implement multi_tap_engine.hpp executing lowercase-first cycling without capital letter phases.

Phase 4: Settings Activity & Native Config Sync

Implement SettingsActivity.kt and SettingsFragment.kt.

Connect asynchronous preference observer flushing NativeConfig primitives across JNI.

Implement user dictionary management and half-life decay timers.

Phase 5: Audio Engine, Haptics & Packaging

Connect Google Oboe C++ sound driver to key events ($<10\text{ms}$ latency).

Connect Android Vibrator / VibrationEffect pipeline.

Inspect APK ensuring android.permission.INTERNET is omitted and package size $<10\text{MB}$.

Audit & Comparison: Gaps Closed from prd2.md

Language Button & Visual Indicator:

Fixed ambiguous dual-text label (EN / ID) to display solely the active language code (EN or ID).

Added the illuminated neon indicator bar under the text to clearly verify T9 predictive state versus Multi-Tap ABC mode.

Spatial-Symmetric Flick Selection:

Standardized all dual-symbol keys (e.g., [ Rp $ ], [ + - ], [ { } ]). Gesturing left selects the left glyph; gesturing right selects the right glyph.

Suggestion Strip Evolution:

Eliminated the unnecessary Page 4 full-keyboard overlay.

Transformed the 40dp strip into an auto-fitting, horizontally swipeable candidate bar supporting up to 16 beam search candidates without layout distortion.

Streamlined Multi-Tap Cycling:

Removed uppercase characters from the multi-tap loop, cutting rotation depth in half (a $\to$ b $\to$ c $\to$ 2). Capitalization is decoupled and handled by the Shift key state machine.

System Buttons & Settings Activity:

Formally detailed the state machines for ENTER (polymorphic EditorInfo actions), SHIFT (3-state cycle with glyph feedback), and DEL (surrogate-aware deletion and un-commit).

Fully designed the SettingsActivity hierarchy, persistence model, and zero-allocation C++ synchronization bridge.



create md files from this

3.1. Suggestion & Candidate Strip Layout (40dp)
The suggestion strip replaces rigid slots with a Horizontally Scrollable Dynamic-Width Canvas Strip:

Fixed Left Pill (12% Width): Displays active predictive mode: [ T9 ] or [ ABC ]. Tapping this pill toggles between Predictive T9 and Literal Multi-Tap.

Scrollable Candidate Viewport (88% Width):

Renders candidates continuously from left to right.

Candidate item widths are dynamically measured based on word character count via primaryTextPaint.measureText(candidate) + paddingHorizontal (minimum width: 64dp).

Smooth finger dragging pans candidates left and right across the entire beam search result list (up to 16 candidates).

Vertical separator bars (dividerPaint) are drawn between adjacent candidate words.

Tapping any candidate immediately commits it to the host input field.

3.2. Page 0: Primary T9 Text & Prediction Layer
Row 4 Key 2: Language & T9 Glow Indicator Specification
Display Text: Renders only the active language code: EN or ID (never dual labels like EN / ID).

Visual Glow Bar:

Rendered as an illuminated horizontal indicator bar directly beneath the language text (y+14dp, height 3dp, width 28dp, corner radius 1.5dp).

When T9 is Active: Rendered using indicatorGlowPaint (Primary Accent Color, #00E5FF or #4CAF50, with anti-aliased fill).

When T9 is Inactive (Multi-Tap ABC Mode): The indicator bar is completely omitted (or rendered fully transparent), providing instantaneous visual verification of the active typing engine.

Interactions:

Primary Tap: Instant language hot-swap (EN ↔ ID). Performs zero-allocation pointer swap in C++ core and re-evaluates current composing buffer.

Flick Up: Toggles typing mode between T9 and ABC (instantly turns the indicator glow bar on or off).

Long Press: Launches Keyboard Settings Activity.

Complete Page 0 Key Interaction & Gesture Matrix
Key	Primary Tap	Flick Up	Flick Down	Flick Left	Flick Right	Long Press (>350ms)
[ 1 .,?!' ]	Contextual separator (. / , / ?)	Insert literal 1	Symbol popup bar	Insert ,	Insert .	Lock literal 1
[ 2 ABC ] to [ 9 WXYZ ]	Push digit to T9 / Multi-tap char	Insert literal digit	Diacritic picker	—	—	Lock literal digit
[ ⌫ DEL ]	Delete char / DAWG rollback	Delete preceding word	Clear entire field	Delete word	—	Continuous accelerated delete
[ ⇧ SHIFT ]	Cycle Lower → Title → Upper	Toggle Caps Lock	—	—	—	Lock UPPERCASE
[ ↵ ENTER ]	Polymorphic EditorInfo Action	Force submit	Insert Tab (\\t)	—	—	Insert newline (\\n)
[ ?123 ]	Switch to Page 1 (Symbols)	—	Switch to Page 2	—	—	Open Settings
[ EN ] / [ ID ]	Hot-swap Language Pointer	Toggle T9 / ABC mode	—	—	—	Open Settings
[ 0 ␣ SPACE ]	Commit Candidate 1 + Space	Insert literal 0	Non-breaking space	Scrub Cursor Left	Scrub Cursor Right	Space repeat / Scrub
[ 😊 / . ]	Switch to Page 3 (Emoji Canvas)	Commit . + Space	Commit , + Space	Reduplication (-)	Insert .	Recent emojis bar
3.3. Page 1: Numeric & Direct Symbols (?123)
Activated via [ ?123 ]. Bypasses T9 predictive traversal; all alphanumeric keys output literal characters.

Plaintext
+-------------------------------------------------------------------------+
| [SYM] |   @    |   #    |   _    |   /    |   \    |   &    |   %    |
+-------------------------------------------------------------------------+
| [    1      ] | [    2      ] | [    3      ] | [   ⌫ DEL   ] |  <-- Row 1 (55dp)
+---------------+---------------+---------------+---------------+
| [    4      ] | [    5      ] | [    6      ] | [   +   -   ] |  <-- Row 2 (55dp)
+---------------+---------------+---------------+---------------+
| [    7      ] | [    8      ] | [    9      ] | [  ↵ ENTER  ] |  <-- Row 3 (55dp)
+---------------+---------------+---------------+---------------+
| [   ABC     ] | [   =\<     ] | [ 0 ␣ SPACE ] | [   .   ,   ] |  <-- Row 4 (55dp)
+-------------------------------------------------------------------------+
Dual-Key Flick Mappings (Page 1)
[ +  - ]:

Visual Layout: + on left, - on right.

Tap: Inserts +.

Flick Left: Inserts +.

Flick Right: Inserts -.

Flick Down: Inserts _.

Long Press: Inserts ±.

[ .  , ]:

Visual Layout: . on left, , on right.

Tap: Inserts ..

Flick Left: Inserts ..

Flick Right: Inserts ,.

Flick Down: Inserts :.

Long Press: Inserts ;.

3.4. Page 2: Extended Symbols & Math (=<)
Activated via [ =\\< ] on Page 1. Dual-symbol keys strictly align physical gesture directions with on-screen visual positions.

Plaintext
+-------------------------------------------------------------------------+
| [EXT] |   ~    |   `    |   |    |   ^    |   °    |   §    |   =    |
+-------------------------------------------------------------------------+
| [   ~   `   ] | [   \   |   ] | [   {   }   ] | [   ⌫ DEL   ] |  <-- Row 1 (55dp)
+---------------+---------------+---------------+---------------+
| [   [   ]   ] | [   <   >   ] | [   (   )   ] | [   ×   ÷   ] |  <-- Row 2 (55dp)
+---------------+---------------+---------------+---------------+
| [   %   ‰   ] | [  Rp   $   ] | [   €   £   ] | [  ↵ ENTER  ] |  <-- Row 3 (55dp)
+---------------+---------------+---------------+---------------+
| [   ABC     ] | [   123     ] | [ 0 ␣ SPACE ] | [   "   '   ] |  <-- Row 4 (55dp)
+-------------------------------------------------------------------------+
Spatial-Symmetric Flick Mapping Matrix (Page 2)
Key Display	Primary Tap	Flick Left (Left Glyph)	Flick Right (Right Glyph)	Flick Down (Paired / Secondary)
[ ~ ]`	Inserts ~	Inserts ~	Inserts `	Inserts ^
[ \ | ]	Inserts \	Inserts \	Inserts |	Inserts /
[ { } ]	Inserts {	Inserts {	Inserts }	Inserts {} + cursor inside
[ [ ] ]	Inserts [	Inserts [	Inserts ]	Inserts [] + cursor inside
[ < > ]	Inserts <	Inserts <	Inserts >	Inserts <> + cursor inside
[ ( ) ]	Inserts (	Inserts (	Inserts )	Inserts () + cursor inside
[ × ÷ ]	Inserts ×	Inserts ×	Inserts ÷	Inserts *
[ % ‰ ]	Inserts %	Inserts %	Inserts ‰	Inserts /
[ Rp $ ]	Inserts active currency	Inserts Rp	Inserts $	Inserts ¢
[ € £ ]	Inserts €	Inserts €	Inserts £	Inserts ¥
[ " ' ]	Inserts "	Inserts "	Inserts '	Inserts "" + cursor inside
3.5. Page 3: In-Canvas Native Emoji Picker
Direct in-canvas rendering avoiding Android RecyclerView overhead.

Plaintext
+-------------------------------------------------------------------------+
| [🕒] | [😀] | [👍] | [🐱] | [🍔] | [⚽] | [🚗] | [💡] | [❤️] |  <-- Category Tabs (40dp)
+-------------------------------------------------------------------------+
|  😀  |  😃  |  😄  |  😁  |  😆  |  😅  |  😂  |  <-- Row 1 (45dp)
+------+------+------+------+------+------+------+
|  🤣  |  🥲  |  🥹  |  😊  |  😇  |  🙂  |  🙃  |  <-- Row 2 (45dp)
+------+------+------+------+------+------+------+
|  😉  |  😌  |  😍  |  🥰  |  😘  |  😗  |  😙  |  <-- Row 3 (45dp)
+------+------+------+------+------+------+------+
|  😚  |  😋  |  😛  |  😝  |  😜  |  🤪  |  🤨  |  <-- Row 4 (45dp)
+------+------+------+------+------+------+------+
| [ ABC ]   | [ 🕒 Recents ]   | [ ␣ Space ]    | [ ⌫ DEL ]     |  <-- Control Row (40dp)
+-----------+------------------+----------------+---------------+
Rendered from a pre-compiled flat table of 32-bit Unicode codepoints using a shared 2-element CharArray scratch buffer.

Long-press opens an in-canvas Fitzpatrick skin-tone modifier strip.

4. Touch Handling, Multi-Touch & Swipeable Strip State Machine
   To eliminate event collisions between candidate horizontal scrolling and keypad vertical flicks, touch handling is partitioned by physical Y bounds:

Plaintext
[MotionEvent.ACTION_DOWN]
│
┌──────────────────────────┴──────────────────────────┐
▼                                                     ▼
Y < 40dp (Candidate Strip)                            Y >= 40dp (Keypad Area)
│                                                     │
Store startX, startScrollX                            Store (x0, y0), t0
│                                            Start Long-Press Timer (350ms)
[ACTION_MOVE]                                                  │
Compute dx = x - startX                               ┌────────┼────────┐
Update strip scrollOffset                             ▼        ▼        ▼
Clamp to [maxScroll, 0]                            [MOVE]   [TIMEOUT] [UP]
Invalidate strip region                            dx/dy>12dp  >350ms   <350ms
4.1. Gesture Detection Constants
Kotlin
private const val TOUCH_SLOP_PX = 36f           // ~10dp threshold for tap stationary bounds
private const val FLICK_DISTANCE_MIN_PX = 54f   // Minimum travel to fire a directional flick
private const val FLICK_TIME_WINDOW_MS = 180L   // Max duration for a high-velocity flick
private const val LONG_PRESS_TIMEOUT_MS = 350L  // Hold duration for repeat / alternate mode
private const val REPEAT_TICK_INTERVAL_MS = 50L // Repeat rate for continuous deletion
private const val SCRUB_STEP_PX = 32f           // Trackpad cursor sensitivity
4.2. Flick Vector Evaluation (4-Quadrant Disambiguation)
When ∣dx∣≥FLICK_DISTANCE_MIN_PX or ∣dy∣≥FLICK_DISTANCE_MIN_PX within FLICK_TIME_WINDOW_MS, evaluate gesture angle θ=atan2(−dy,dx):

Flick Right: −45
∘
≤θ<45
∘


Flick Up: 45
∘
≤θ<135
∘


Flick Left: 135
∘
≤θ<225
∘
(or θ<−135
∘
)

Flick Down: −135
∘
≤θ<−45
∘


4.3. Spacebar Cursor Scrubbing (Trackpad Mode)
Triggered when ACTION_MOVE originates on Key 0 ([ 0 ␣ SPACE ]) and ∣dx∣≥TOUCH_SLOP_PX.

Dispatches KEYCODE_DPAD_RIGHT (dx>0) or KEYCODE_DPAD_LEFT (dx<0) for every accumulated ±SCRUB_STEP_PX.

Fires native low-latency mechanical audio/haptic click for every text position traversed.

5. Core System Button State Machines
   5.1. Key ↵ (ENTER): Polymorphic Action Dispatcher
   Plaintext
   [Tap Key ↵ ENTER]
   │
   Is Composing Buffer Active (> 0)?
   │
   ┌───────────────┴───────────────┐
   Yes                              No
   ▼                               ▼
   Commit active Candidate 1            Read EditorInfo.imeOptions
   Call finishComposingText()                    │
   │                   ┌───────────┴───────────┐
   └──────────────────►▼                       ▼
   Has Action Flag?        Multi-Line / No Action
   │                       │
   ▼                       ▼
   ic.performEditorAction()       ic.commitText("\n", 1)
   Icon Rendering
   Canvas dynamically draws vector path based on EditorInfo.imeOptions:

IME_ACTION_SEARCH → Magnifying Glass.

IME_ACTION_GO / IME_ACTION_SEND → Rightward Arrow / Paper Plane.

IME_ACTION_NEXT → Tab Right Arrow.

IME_ACTION_DONE → Checkmark.

Default / Multi-line → Return Carriage Arrow.

5.2. Key ⇧ (SHIFT): 3-State Cycling Machine
Plaintext
┌───────── Tap Key ⇧ [SHIFT] ─────────┐
▼                                     │
[STATE: LOWERCASE]                            │
│ (Single Tap)                        │
▼                                     │
[STATE: TITLECASE] ──(Auto-resets to Lower)   │ (Double-Tap or Flick-Up)
│ (Single Tap)                        │
▼                                     ▼
[STATE: UPPERCASE] <────────────────── [CAPS LOCK ACTIVE]
LOWERCASE: Standard lowercase candidate generation and multi-tap output. Key glyph: Hollow upward arrow outline (⇧).

TITLECASE: First letter capitalized; automatically reverts to LOWERCASE following the first character commit. Key glyph: Solid filled arrow (⬆).

UPPERCASE / CAPS LOCK: All characters capitalized. Key glyph: Solid filled arrow with horizontal base bar (⇪). Remains locked until [ ⇧ SHIFT ] is tapped again.

5.3. Key ⌫ (DEL): Unified Deletion Engine
Plaintext
┌─────────── [ Press ⌫ DEL ] ───────────┐
│                                       │
[Composing Buffer > 0]                 [Composing Buffer == 0]
│                                       │
Pop last digit from stack              Inspect getTextBeforeCursor(2, 0)
C++ Engine pop_stroke()                           │
│                        ┌──────────────┴──────────────┐
Re-score candidates & update       ▼                             ▼
Direct ByteBuffer buffer     UTF-16 Surrogate?            Standard Character
│                        │                             │
ic.setComposingText()        deleteSurroundingText(2, 0)   deleteSurroundingText(1, 0)
Retroactive Un-Commit: If ⌫ DEL is pressed immediately following a word commit via [ SPACE ], the trailing space and word are removed, and the raw digit sequence is re-hydrated into the active composing buffer.

Continuous Accelerated Delete:

0–350ms: Single character deletion.

350–1200ms: Repeating deletion (every 50ms).

>1200ms: High-speed tokenized whole-word deletion.

6. Typing Engines & Predictive Pipelines
   6.1. Multi-Tap (ABC) Cycling Engine (No-Caps Cycle)
   When T9 prediction is toggled off, keys cycle strictly through lowercase characters and numbers. Capitalization is governed exclusively by the Shift Key State.

Deterministic Multi-Tap Mapping Matrix
Key	Primary Cycle Sequence (Shift: Lowercase)	Capitalized Cycle Sequence (Shift: Title/Upper)
[ 1 ]	. → , → ? → ! → ' → - → @ → 1	. → , → ? → ! → ' → - → @ → 1
[ 2 ]	a → b → c → 2	A → B → C → 2
[ 3 ]	d → e → f → 3	D → E → F → 3
[ 4 ]	g → h → i → 4	G → H → I → 4
[ 5 ]	j → k → l → 5	J → K → L → 5
[ 6 ]	m → n → o → 6	M → N → O → 6
[ 7 ]	p → q → r → s → 7	P → Q → R → S → 7
[ 8 ]	t → u → v → 8	T → U → V → 8
[ 9 ]	w → x → y → z → 9	W → X → Y → Z → 9
[ 0 ]	 (space) → 0 → \n	 (space) → 0 → \n
Cycle Expiration: 600ms timeout or tapping a different key commits the active character.

Auto-Reset: If Shift is TITLECASE, committing the first character automatically resets Shift to LOWERCASE.

6.2. Predictive T9 Engine (Beam Search & DAWG)
Traverses pre-compiled binary DAWG nodes in C++20.

2D Gaussian Touch Scoring:
P(key
k
​
∣x,y)=exp(−
2σ
2

(x−x
k
​
)
2
+(y−y
k
​
)
2

​
)
Candidate strings are decoded into a Direct ByteBuffer with zero JVM object allocation.

7. EditorInfo & InputType Dynamic Routing Engine
   Evaluated in InputMethodService.onStartInputView(info: EditorInfo, restarting: Boolean):

Password Fields (TYPE_TEXT_VARIATION_PASSWORD, TYPE_NUMBER_VARIATION_PASSWORD):

Beam search engine: DISABLED.

Suggestion Strip: Hidden.

LMDB learning: DISABLED (zero writes).

Switches automatically to literal Multi-Tap or numeric layout.

Numeric Fields (TYPE_CLASS_NUMBER, TYPE_CLASS_PHONE):

Displays Page 1 (Numeric) by default.

Incognito Mode (IME_FLAG_NO_PERSONALIZED_LEARNING):

Bypasses user dictionary logging and frequency updates.

8. Keyboard Settings Architecture & Dynamic Config Sync
   8.1. SettingsActivity Hierarchy & Preferences
   A native Android settings interface structured via PreferenceFragmentCompat:

Haptics & Audio Feedback:

Haptic Feedback: Toggle (On/Off).

Vibration Intensity: Slider (0ms to 100ms, default: 25ms).

Audio Click Feedback: Toggle (On/Off).

Sound Volume: Slider (0
to 100
, default: 60
).

Sound Style: Dropdown (Mechanical Classic, Modern Soft, Minimalist Click).

Layout & Display:

Keyboard Overall Height: Slider (220dp to 320dp, default: 260dp).

Candidate Font Size: Slider (12sp to 18sp, default: 14sp).

One-Handed Mode: Dropdown (Disabled, Left-Handed, Right-Handed).

Typing Behaviors:

Default Input Mode: Dropdown (Predictive T9, Multi-Tap ABC).

Long-Press Delay: Slider (200ms to 600ms, default: 350ms).

Multi-Tap Commit Timeout: Slider (400ms to 1000ms, default: 600ms).

Auto-Capitalization: Toggle (On/Off).

Auto-Space After Commit: Toggle (On/Off).

Double-Space Period: Toggle (On/Off).

Spacebar Cursor Scrubbing: Toggle (On/Off).

Dictionary & Language Management:

Primary Startup Language: Dropdown (Indonesian, English).

Slang / Bahasa Gaul Priority: Toggle (On/Off).

Dynamic Decay Half-Life: Dropdown (14 Days, 30 Days, Disabled).

User Dictionary Editor: Search, add, or purge learned custom words.

Reset User Dictionary: Hard reset clearing the dynamic LMDB database.

8.2. Zero-Allocation C++ Configuration Synchronization
Whenever preferences are updated, SettingsObserver flushes a flat C primitive struct across JNI into the native core:

C++
// native_config.hpp
struct NativeConfig {
float touch_variance_sigma;     // Default: 42.0f
uint32_t long_press_timeout_ms; // Default: 350
uint32_t multi_tap_timeout_ms;  // Default: 600
uint8_t auto_space_enabled;     // 0 or 1
uint8_t slang_boost_enabled;    // 0 or 1
uint32_t decay_half_life_days;  // Default: 30
};

extern "C" JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeSyncConfig(
JNIEnv* env, jobject thiz,
jfloat sigma, jint longPressMs, jint multiTapMs,
jboolean autoSpace, jboolean slangBoost, jint decayDays) {
g_config.touch_variance_sigma = sigma;
g_config.long_press_timeout_ms = longPressMs;
g_config.multi_tap_timeout_ms = multiTapMs;
g_config.auto_space_enabled = autoSpace ? 1 : 0;
g_config.slang_boost_enabled = slangBoost ? 1 : 0;
g_config.decay_half_life_days = decayDays;
}
9. Performance Benchmarks & Non-Functional Verification
   Target Metric	Benchmark (SwiftKey / Gboard)	OpenT9 Target Spec	Verification Harness
   Disambiguation Latency	10–25ms	< 0.5ms (C++20 Beam Search)	Google Benchmark / NDK CI
   Rollback Execution Time	Full re-traversal	< 0.01ms (O(1) Stack Pop)	Nanosecond Timing Test
   Cold-Start Launch Time	200–400ms	< 30ms	Android Macrobenchmark
   RAM Footprint (Resident)	85 MB – 190 MB	< 25 MB (mmap page cache)	Android Profiler Memory Track
   Frame Render Latency	Janks on GC cycles	8.3ms (Locked 120 FPS)	dumpsys gfxinfo framestats
   Garbage Collector Churn	Variable allocations	0 bytes / frame	ART Allocation Tracker
   Audio Feedback Latency	35–60ms (SoundPool)	< 10ms (Oboe / AAudio)	Low-Latency Mic Trace
   APK Binary Size	45 MB – 80 MB	< 10 MB (Lexicons included)	apkanalyzer CLI
10. Repository Layout
    Plaintext
    opent9-android/
    ├── app/
    │   ├── build.gradle.kts
    │   └── src/main/
    │       ├── AndroidManifest.xml             # No INTERNET permission
    │       ├── java/com/opent9/keyboard/
    │       │   ├── OpenT9InputMethodService.kt # Thin IME lifecycle wrapper
    │       │   ├── settings/
    │       │   │   ├── SettingsActivity.kt     # Preferences Activity
    │       │   │   └── SettingsFragment.kt     # PreferenceFragmentCompat bindings
    │       │   ├── ui/
    │       │   │   ├── T9KeyboardView.kt       # Zero-allocation custom Canvas View
    │       │   │   ├── TouchGestureTracker.kt  # Keypad & swipeable strip state machine
    │       │   │   ├── KeyAtlas.kt             # Pre-computed key bounds & glyph coordinates
    │       │   │   └── EmojiAtlas.kt           # UTF-32 emoji codepoint tables
    │       │   └── jni/
    │       │       └── NativeEngineBridge.kt   # Direct ByteBuffer JNI wrappers
    │       ├── cpp/
    │       │   ├── CMakeLists.txt
    │       │   ├── include/
    │       │   │   ├── dawg_engine.hpp         # DAWG parser & O(1) history stack
    │       │   │   ├── spatial_scoring.hpp     # 2D Gaussian touch scoring
    │       │   │   ├── multi_tap_engine.hpp    # Deterministic lowercase multi-tap
    │       │   │   ├── lexicon_manager.hpp     # Dual mmap pointer hot-swapper
    │       │   │   ├── audio_engine.hpp        # Google Oboe AAudio sound driver
    │       │   │   ├── dynamic_store.hpp       # LMDB decay storage
    │       │   │   └── native_config.hpp       # Flat config sync struct
    │       │   └── src/
    │       │       ├── dawg_engine.cpp
    │       │       ├── spatial_scoring.cpp
    │       │       ├── multi_tap_engine.cpp
    │       │       ├── lexicon_manager.cpp
    │       │       ├── audio_engine.cpp
    │       │       └── jni_bridge.cpp
    │       └── assets/dictionaries/
    │           ├── en_lexicon.dawg             # Static binary DAWG (~4.1MB)
    │           └── id_lexicon.dawg             # Static binary DAWG (~3.8MB)
11. Phased TDD Implementation Roadmap
    Plaintext
    Phase 1: Native Core & DAWG Engine (C++20)
    └──> Phase 2: Touch Tracker & Swipeable Suggestion Strip
    └──> Phase 3: System Buttons (ENTER, SHIFT, DEL) & Multi-Tap
    └──> Phase 4: Settings Activity & Native Config Sync
    └──> Phase 5: Audio Engine, Haptics & Packaging
    Phase 1: Native Core & DAWG Engine (C++20)
    Unit tests verifying sequence 4663 yields ["good", "home", "gone", "hood"] in <0.2ms.

Unit tests confirming pop_stroke() restores prior depth in <0.01ms.

Pointer swap benchmarking confirming <0.05ms lexicon switching.

Phase 2: Touch Tracker & Swipeable Suggestion Strip
Implement TouchGestureTracker.kt with partitioned Y<40dp candidate strip scrolling.

Unit tests verifying spatial flick evaluation (left, right, up, down) matching dual-symbol layout.

Implement horizontal scroll and auto-fit width measurement in T9KeyboardView.kt.

Phase 3: System Keys & Multi-Tap Engine
Implement ENTER polymorphic action mapping for all EditorInfo action flags.

Implement SHIFT 3-state cycling machine (LOWER, TITLE, UPPER).

Implement multi_tap_engine.hpp executing lowercase-first cycling without capital letter phases.

Phase 4: Settings Activity & Native Config Sync
Implement SettingsActivity.kt and SettingsFragment.kt.

Connect asynchronous preference observer flushing NativeConfig primitives across JNI.

Implement user dictionary management and half-life decay timers.

Phase 5: Audio Engine, Haptics & Packaging
Connect Google Oboe C++ sound driver to key events (<10ms latency).

Connect Android Vibrator / VibrationEffect pipeline.

Inspect APK ensuring android.permission.INTERNET is omitted and package size <10MB.

Audit & Comparison: Gaps Closed from prd2.md
Language Button & Visual Indicator:

Fixed ambiguous dual-text label (EN / ID) to display solely the active language code (EN or ID).

Added the illuminated neon indicator bar under the text to clearly verify T9 predictive state versus Multi-Tap ABC mode.

Spatial-Symmetric Flick Selection:

Standardized all dual-symbol keys (e.g., [ Rp  $ ], [ +  - ], [ {  } ]). Gesturing left selects the left glyph; gesturing right selects the right glyph.

Suggestion Strip Evolution:

Eliminated the unnecessary Page 4 full-keyboard overlay.

Transformed the 40dp strip into an auto-fitting, horizontally swipeable candidate bar supporting up to 16 beam search candidates without layout distortion.

Streamlined Multi-Tap Cycling:

Removed uppercase characters from the multi-tap loop, cutting rotation depth in half (a → b → c → 2). Capitalization is decoupled and handled by the Shift key state machine.

System Buttons & Settings Activity:

Formally detailed the state machines for ENTER (polymorphic EditorInfo actions), SHIFT (3-state cycle with glyph feedback), and DEL (surrogate-aware deletion and un-commit).

Fully designed the SettingsActivity hierarchy, persistence model, and zero-allocation C++ synchronization bridge.
"""

file_path = "prd3.md"
with open(file_path, "w", encoding="utf-8") as f:
f.write(prd_content.strip() + "\n")

print(f"File successfully created: {file_path}")


```text?code_stdout&code_event_index=1
File successfully created: prd3.md

Your Markdown (MD) file is ready

MD icon
prd3
 MD 
Document Summary: prd3.md
The Product Requirements Document (PRD) for OpenT9 Core Native Keyboard (v3.0) has been generated and structured into Markdown:

Document Identifier: prd3.md

Package Identifier: com.opent9.keyboard

Key Architectural Principles:

Thin Kotlin IME Wrapper + C++20 Core via NDK: Sub-millisecond beam search traversing pre-indexed binary DAWG files via memory mapping (mmap).

Zero-Allocation UI Pipeline: Canvas rendering backed by pre-allocated primitive arrays with 0 bytes allocated during onTouchEvent() and onDraw().

Dynamic Swipeable Candidate Strip (40dp): Continuous horizontal viewport supporting up to 16 beam search candidates with measured item widths.

Refined Language Indicator: Single active code (EN or ID) with an active neon T9 glow bar beneath the label.

Spatial-Symmetric Flick Selection: Direct physical mapping for dual-glyph keys (flick left for left symbol, flick right for right symbol).

Streamlined Multi-Tap Cycling: Decoupled lower-case sequence (a → b → c → 2) with capitalization handled by the Shift key state machine.

Native Hardware Audio Engine: Sub-10ms feedback using Google Oboe (AAudio / OpenSL ES).

Settings & Config Synchronization: Direct JNI primitive struct bridge updating runtime configurations without heap allocations.

Offline Security: android.permission.INTERNET strictly omitted.




Gemini is AI and can make mistakes.

Mapping Out Detail
# Product Requirements Document (PRD): OpenT9 Core Native Keyboard (v3.0)

**Document Identifier:** `prd3.md`  
**Package Identifier:** `com.opent9.keyboard`  
**Architecture Class:** Native Hybrid Systems Architecture (Thin Kotlin IME Wrapper + C++20 Core via NDK + Zero-Allocation Hardware Canvas)  
**Target Benchmarks:** Microsoft SwiftKey, Gboard (3×4 Mode), Samsung Keyboard (3×4 Mode)

---

## 1. Executive Summary & Core Value Proposition

OpenT9 Core delivers an ultra-lightweight, zero-latency Android T9 predictive keyboard that decisively outperforms OEM solutions across memory footprint, cold-start latency, frame rendering consistency, and typing disambiguation speed.

### Core Differentiators

- **Sub-1ms Predictive Disambiguation:** C++20 beam search traversing pre-indexed Directed Acyclic Word Graphs (DAWG) without JVM pauses.
- **Deterministic Fallback (Multi-Tap ABC Mode):** Instant toggling between predictive T9 and classic lowercase-first multi-tap for unlisted acronyms, passwords, and custom codes.
- **Spatial-Symmetric Flick Selection:** Dual-symbol keys map physical left/right flick directions directly to on-key visual glyph positions.
- **Dynamic Auto-Fit Swipeable Suggestion Strip:** Horizontally scrollable 40dp candidate bar eliminating rigid candidate pagination and secondary grid overlays.
- **Polymorphic System Keys:** State-machine-driven implementations for Key ↵ (ENTER), Key ⇧ (SHIFT), and Key ⌫ (DEL) adapting across EditorInfo configurations.
- **Single-Tag Language Toggle with Active T9 Glow Bar:** Minimalist visual display showing active language code (`EN` or `ID`) with an illuminated neon indicator bar when T9 prediction is active.
- **Continuous Spacebar Trackpad Scrubbing:** Horizontal cursor positioning directly from Key 0.
- **Dedicated Native Settings Architecture:** `SharedPreferences` persistence coupled with zero-allocation C++ memory synchronization.
- **100% Offline Privacy:** Zero network permissions (`android.permission.INTERNET` strictly omitted).

---

## 2. Technical Stack & Architectural Constraints

> **Architectural Invariant:** Declarative UI frameworks (Jetpack Compose, Flutter) and nested XML view hierarchies are strictly prohibited in the core typing loop.

| Layer | Technology Selection | Architectural Invariant |
| :--- | :--- | :--- |
| **IME Service Wrapper** | Kotlin (`InputMethodService`) | Minimal OS wrapper for window lifecycle, focus handling, and `InputConnection` transactions. Free of dictionary search or parsing logic. |
| **Prediction & Scoring Core** | C++20 (Android NDK via CMake) | Executes beam search, Gaussian spatial scoring, candidate ranking, and state rollbacks. Zero dynamic heap allocations in hot paths. |
| **Static Lexicons** | Memory-Mapped Files (`mmap`) + DAWG | English and Indonesian binary DAWGs mapped directly from storage into Linux page cache. |
| **Dynamic Vocabulary** | Embedded LMDB / Native SQLite | Key-value store for user slang, contact names, and frequency overrides with exponential half-life decay. |
| **UI & Render Pipeline** | Single Custom View (`Canvas.onDraw`) | Hardware-accelerated canvas backed by pre-allocated primitive arrays (`FloatArray`, `IntArray`). 0 bytes allocated in `onTouchEvent()` and `onDraw()`. |
| **JNI Bridge** | Direct `ByteBuffer` & Primitives | Touch coordinates and keycodes sent as primitives; candidate offsets returned via direct memory buffers to prevent `java.lang.String` churn. |
| **Audio Engine** | Google Oboe C++ (AAudio / OpenSL ES) | Direct hardware audio driver access delivering click feedback in $< 10\text{ms}$. |
| **Configuration Sync** | Direct JNI Primitive Struct | Asynchronous `SharedPreferences` observer synchronizing runtime parameters directly to native C++ global state. |

---

## 3. UI Layout & Keypad Specifications

The entire keyboard surface is fixed at a **260dp overall height budget** (40dp suggestion strip + 220dp key grid across 4 rows of 55dp each) on standard 1080×2400 displays. Key columns occupy exactly 25% of the screen width (~90–100dp).

```text
+-------------------------------------------------------------------------+
| [T9] | 1. selamat   | 2. selalu   | 3. selain   | 4. seluler  | 5. ...  |  <-- Swipeable Strip (40dp)
+-------------------------------------------------------------------------+
| [  1 .,?!'  ] | [   2 ABC   ] | [   3 DEF   ] | [   ⌫ DEL   ] |  <-- Row 1 (55dp)
+---------------+---------------+---------------+---------------+
| [   4 GHI   ] | [   5 JKL   ] | [   6 MNO   ] | [  ⇧ SHIFT  ] |  <-- Row 2 (55dp)
+---------------+---------------+---------------+---------------+
| [  7 PQRS   ] | [   8 TUV   ] | [  9 WXYZ   ] | [  ↵ ENTER  ] |  <-- Row 3 (55dp)
+---------------+---------------+---------------+---------------+
| [   ?123    ] | [    EN     ] | [ 0 ␣ SPACE ] | [   😊 / .   ] |  <-- Row 4 (55dp)
|               | [ ========= ] |               |               |
+-------------------------------------------------------------------------+
```

---

### 3.1. Suggestion & Candidate Strip Layout (40dp)

The suggestion strip replaces rigid slots with a **Horizontally Scrollable Dynamic-Width Canvas Strip**:

- **Fixed Left Pill (12% Width):** Displays active predictive mode: `[ T9 ]` or `[ ABC ]`. Tapping this pill toggles between Predictive T9 and Literal Multi-Tap.
- **Scrollable Candidate Viewport (88% Width):**
  - Renders candidates continuously from left to right.
  - Candidate item widths are dynamically measured based on word character count via `primaryTextPaint.measureText(candidate) + paddingHorizontal` (minimum width: 64dp).
  - Smooth finger dragging pans candidates left and right across the entire beam search result list (up to 16 candidates).
  - Vertical separator bars (`dividerPaint`) are drawn between adjacent candidate words.
  - Tapping any candidate immediately commits it to the host input field.

---

### 3.2. Page 0: Primary T9 Text & Prediction Layer

#### Row 4 Key 2: Language & T9 Glow Indicator Specification
- **Display Text:** Renders only the active language code: `EN` or `ID` (never dual labels like `EN / ID`).
- **Visual Glow Bar:**
  - Rendered as an illuminated horizontal indicator bar directly beneath the language text ($y + 14\text{dp}$, height $3\text{dp}$, width $28\text{dp}$, corner radius $1.5\text{dp}$).
  - **When T9 is Active:** Rendered using `indicatorGlowPaint` (Primary Accent Color, `#00E5FF` or `#4CAF50`, with anti-aliased fill).
  - **When T9 is Inactive (Multi-Tap ABC Mode):** The indicator bar is completely omitted (or rendered fully transparent), providing instantaneous visual verification of the active typing engine.
- **Interactions:**
  - **Primary Tap:** Instant language hot-swap (`EN` $\leftrightarrow$ `ID`). Performs zero-allocation pointer swap in C++ core and re-evaluates current composing buffer.
  - **Flick Up:** Toggles typing mode between T9 and ABC (instantly turns the indicator glow bar on or off).
  - **Long Press:** Launches Keyboard Settings Activity.

#### Complete Page 0 Key Interaction & Gesture Matrix

| Key | Primary Tap | Flick Up | Flick Down | Flick Left | Flick Right | Long Press (>350ms) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ 1 .,?!' ]** | Contextual separator (`.` / `,` / `?`) | Insert literal `1` | Symbol popup bar | Insert `,` | Insert `.` | Lock literal `1` |
| **[ 2 ABC ] to [ 9 WXYZ ]** | Push digit to T9 / Multi-tap char | Insert literal digit | Diacritic picker | — | — | Lock literal digit |
| **[ ⌫ DEL ]** | Delete char / DAWG rollback | Delete preceding word | Clear entire field | Delete word | — | Continuous accelerated delete |
| **[ ⇧ SHIFT ]** | Cycle Lower $\to$ Title $\to$ Upper | Toggle Caps Lock | — | — | — | Lock UPPERCASE |
| **[ ↵ ENTER ]** | Polymorphic EditorInfo Action | Force submit | Insert Tab (`\t`) | — | — | Insert newline (`\n`) |
| **[ ?123 ]** | Switch to Page 1 (Symbols) | — | Switch to Page 2 | — | — | Open Settings |
| **[ EN ] / [ ID ]** | Hot-swap Language Pointer | Toggle T9 / ABC mode | — | — | — | Open Settings |
| **[ 0 ␣ SPACE ]** | Commit Candidate 1 + Space | Insert literal `0` | Non-breaking space | Scrub Cursor Left | Scrub Cursor Right | Space repeat / Scrub |
| **[ 😊 / . ]** | Switch to Page 3 (Emoji Canvas) | Commit `. ` + Space | Commit `, ` + Space | Reduplication (`-`) | Insert `.` | Recent emojis bar |

---

### 3.3. Page 1: Numeric & Direct Symbols (?123)

Activated via `[ ?123 ]`. Bypasses T9 predictive traversal; all alphanumeric keys output literal characters.

```text
+-------------------------------------------------------------------------+
| [SYM] |   @    |   #    |   _    |   /    |   \    |   &    |   %    |
+-------------------------------------------------------------------------+
| [    1      ] | [    2      ] | [    3      ] | [   ⌫ DEL   ] |  <-- Row 1 (55dp)
+---------------+---------------+---------------+---------------+
| [    4      ] | [    5      ] | [    6      ] | [   +   -   ] |  <-- Row 2 (55dp)
+---------------+---------------+---------------+---------------+
| [    7      ] | [    8      ] | [    9      ] | [  ↵ ENTER  ] |  <-- Row 3 (55dp)
+---------------+---------------+---------------+---------------+
| [   ABC     ] | [   =\<     ] | [ 0 ␣ SPACE ] | [   .   ,   ] |  <-- Row 4 (55dp)
+-------------------------------------------------------------------------+
```

#### Dual-Key Flick Mappings (Page 1)

- **`[ +  - ]`:**
  - **Visual Layout:** `+` on left, `-` on right.
  - **Tap:** Inserts `+`.
  - **Flick Left:** Inserts `+`.
  - **Flick Right:** Inserts `-`.
  - **Flick Down:** Inserts `_`.
  - **Long Press:** Inserts `±`.
- **`[ .  , ]`:**
  - **Visual Layout:** `.` on left, `,` on right.
  - **Tap:** Inserts `.`.
  - **Flick Left:** Inserts `.`.
  - **Flick Right:** Inserts `,`.
  - **Flick Down:** Inserts `:`.
  - **Long Press:** Inserts `;`.

---

### 3.4. Page 2: Extended Symbols & Math (=\<)

Activated via `[ =\< ]` on Page 1. Dual-symbol keys strictly align physical gesture directions with on-screen visual positions.

```text
+-------------------------------------------------------------------------+
| [EXT] |   ~    |   `    |   |    |   ^    |   °    |   §    |   =    |
+-------------------------------------------------------------------------+
| [   ~   `   ] | [   \   |   ] | [   {   }   ] | [   ⌫ DEL   ] |  <-- Row 1 (55dp)
+---------------+---------------+---------------+---------------+
| [   [   ]   ] | [   <   >   ] | [   (   )   ] | [   ×   ÷   ] |  <-- Row 2 (55dp)
+---------------+---------------+---------------+---------------+
| [   %   ‰   ] | [  Rp   $   ] | [   €   £   ] | [  ↵ ENTER  ] |  <-- Row 3 (55dp)
+---------------+---------------+---------------+---------------+
| [   ABC     ] | [   123     ] | [ 0 ␣ SPACE ] | [   "   '   ] |  <-- Row 4 (55dp)
+-------------------------------------------------------------------------+
```

#### Spatial-Symmetric Flick Mapping Matrix (Page 2)

| Key Display | Primary Tap | Flick Left (Left Glyph) | Flick Right (Right Glyph) | Flick Down (Paired / Secondary) |
| :--- | :--- | :--- | :--- | :--- |
| **`[ ~   ` ]`** | Inserts `~` | Inserts `~` | Inserts `` ` `` | Inserts `^` |
| **`[ \   \| ]`** | Inserts `\` | Inserts `\` | Inserts `\|` | Inserts `/` |
| **`[ {   } ]`** | Inserts `{` | Inserts `{` | Inserts `}` | Inserts `{}` + cursor inside |
| **`[ [   ] ]`** | Inserts `[` | Inserts `[` | Inserts `]` | Inserts `[]` + cursor inside |
| **`[ <   > ]`** | Inserts `<` | Inserts `<` | Inserts `>` | Inserts `<>` + cursor inside |
| **`[ (   ) ]`** | Inserts `(` | Inserts `(` | Inserts `)` | Inserts `()` + cursor inside |
| **`[ ×   ÷ ]`** | Inserts `×` | Inserts `×` | Inserts `÷` | Inserts `*` |
| **`[ %   ‰ ]`** | Inserts `%` | Inserts `%` | Inserts `‰` | Inserts `/` |
| **`[ Rp  $ ]`** | Inserts active currency | Inserts `Rp ` | Inserts `$` | Inserts `¢` |
| **`[ €   £ ]`** | Inserts `€` | Inserts `€` | Inserts `£` | Inserts `¥` |
| **`[ "   ' ]`** | Inserts `"` | Inserts `"` | Inserts `'` | Inserts `""` + cursor inside |

---

### 3.5. Page 3: In-Canvas Native Emoji Picker

Direct in-canvas rendering avoiding Android `RecyclerView` overhead.

```text
+-------------------------------------------------------------------------+
| [🕒] | [😀] | [👍] | [🐱] | [🍔] | [⚽] | [🚗] | [💡] | [❤️] |  <-- Category Tabs (40dp)
+-------------------------------------------------------------------------+
|  😀  |  😃  |  😄  |  😁  |  😆  |  😅  |  😂  |  <-- Row 1 (45dp)
+------+------+------+------+------+------+------+
|  🤣  |  🥲  |  🥹  |  😊  |  😇  |  🙂  |  🙃  |  <-- Row 2 (45dp)
+------+------+------+------+------+------+------+
|  😉  |  😌  |  😍  |  🥰  |  😘  |  😗  |  😙  |  <-- Row 3 (45dp)
+------+------+------+------+------+------+------+
|  😚  |  😋  |  😛  |  😝  |  😜  |  🤪  |  🤨  |  <-- Row 4 (45dp)
+------+------+------+------+------+------+------+
| [ ABC ]   | [ 🕒 Recents ]   | [ ␣ Space ]    | [ ⌫ DEL ]     |  <-- Control Row (40dp)
+-----------+------------------+----------------+---------------+
```

- Rendered from a pre-compiled flat table of 32-bit Unicode codepoints using a shared 2-element `CharArray` scratch buffer.
- Long-press opens an in-canvas Fitzpatrick skin-tone modifier strip.

---

## 4. Touch Handling, Multi-Touch & Swipeable Strip State Machine

To eliminate event collisions between candidate horizontal scrolling and keypad vertical flicks, touch handling is partitioned by physical $Y$ bounds:

```text
                            [MotionEvent.ACTION_DOWN]
                                        │
             ┌──────────────────────────┴──────────────────────────┐
             ▼                                                     ▼
     Y < 40dp (Candidate Strip)                            Y >= 40dp (Keypad Area)
             │                                                     │
    Store startX, startScrollX                            Store (x0, y0), t0
             │                                            Start Long-Press Timer (350ms)
    [ACTION_MOVE]                                                  │
    Compute dx = x - startX                               ┌────────┼────────┐
    Update strip scrollOffset                             ▼        ▼        ▼
    Clamp to [maxScroll, 0]                            [MOVE]   [TIMEOUT] [UP]
    Invalidate strip region                            dx/dy>12dp  >350ms   <350ms
```

### 4.1. Gesture Detection Constants

```kotlin
private const val TOUCH_SLOP_PX = 36f           // ~10dp threshold for tap stationary bounds
private const val FLICK_DISTANCE_MIN_PX = 54f   // Minimum travel to fire a directional flick
private const val FLICK_TIME_WINDOW_MS = 180L   // Max duration for a high-velocity flick
private const val LONG_PRESS_TIMEOUT_MS = 350L  // Hold duration for repeat / alternate mode
private const val REPEAT_TICK_INTERVAL_MS = 50L // Repeat rate for continuous deletion
private const val SCRUB_STEP_PX = 32f           // Trackpad cursor sensitivity
```

### 4.2. Flick Vector Evaluation (4-Quadrant Disambiguation)

When $|dx| \ge 	ext{FLICK\_DISTANCE\_MIN\_PX}$ or $|dy| \ge 	ext{FLICK\_DISTANCE\_MIN\_PX}$ within $	ext{FLICK\_TIME\_WINDOW\_MS}$, evaluate gesture angle $	heta = \operatorname{atan2}(-dy, dx)$:

- **Flick Right:** $-45^\circ \le 	heta < 45^\circ$
- **Flick Up:** $45^\circ \le 	heta < 135^\circ$
- **Flick Left:** $135^\circ \le 	heta < 225^\circ$ (or $	heta < -135^\circ$)
- **Flick Down:** $-135^\circ \le 	heta < -45^\circ$

### 4.3. Spacebar Cursor Scrubbing (Trackpad Mode)

- Triggered when `ACTION_MOVE` originates on Key 0 (`[ 0 ␣ SPACE ]`) and $|dx| \ge 	ext{TOUCH\_SLOP\_PX}$.
- Dispatches `KEYCODE_DPAD_RIGHT` ($dx > 0$) or `KEYCODE_DPAD_LEFT` ($dx < 0$) for every accumulated $\pm 	ext{SCRUB\_STEP\_PX}$.
- Fires native low-latency mechanical audio/haptic click for every text position traversed.

---

## 5. Core System Button State Machines

### 5.1. Key ↵ (ENTER): Polymorphic Action Dispatcher

```text
                            [Tap Key ↵ ENTER]
                                    │
                    Is Composing Buffer Active (> 0)?
                                    │
                    ┌───────────────┴───────────────┐
                   Yes                              No
                    ▼                               ▼
      Commit active Candidate 1            Read EditorInfo.imeOptions
      Call finishComposingText()                    │
                    │                   ┌───────────┴───────────┐
                    └──────────────────►▼                       ▼
                                Has Action Flag?        Multi-Line / No Action
                                        │                       │
                                        ▼                       ▼
                           ic.performEditorAction()       ic.commitText("
", 1)
```

#### Icon Rendering
Canvas dynamically draws vector path based on `EditorInfo.imeOptions`:
- `IME_ACTION_SEARCH` $	o$ Magnifying Glass.
- `IME_ACTION_GO` / `IME_ACTION_SEND` $	o$ Rightward Arrow / Paper Plane.
- `IME_ACTION_NEXT` $	o$ Tab Right Arrow.
- `IME_ACTION_DONE` $	o$ Checkmark.
- Default / Multi-line $	o$ Return Carriage Arrow.

---

### 5.2. Key ⇧ (SHIFT): 3-State Cycling Machine

```text
              ┌───────── Tap Key ⇧ [SHIFT] ─────────┐
              ▼                                     │
      [STATE: LOWERCASE]                            │
              │ (Single Tap)                        │
              ▼                                     │
      [STATE: TITLECASE] ──(Auto-resets to Lower)   │ (Double-Tap or Flick-Up)
              │ (Single Tap)                        │
              ▼                                     ▼
      [STATE: UPPERCASE] <────────────────── [CAPS LOCK ACTIVE]
```

- **LOWERCASE:** Standard lowercase candidate generation and multi-tap output. Key glyph: Hollow upward arrow outline (`⇧`).
- **TITLECASE:** First letter capitalized; automatically reverts to `LOWERCASE` following the first character commit. Key glyph: Solid filled arrow (`⬆`).
- **UPPERCASE / CAPS LOCK:** All characters capitalized. Key glyph: Solid filled arrow with horizontal base bar (`⇪`). Remains locked until `[ ⇧ SHIFT ]` is tapped again.

---

### 5.3. Key ⌫ (DEL): Unified Deletion Engine

```text
                      ┌─────────── [ Press ⌫ DEL ] ───────────┐
                      │                                       │
            [Composing Buffer > 0]                 [Composing Buffer == 0]
                      │                                       │
            Pop last digit from stack              Inspect getTextBeforeCursor(2, 0)
            C++ Engine pop_stroke()                           │
                      │                        ┌──────────────┴──────────────┐
            Re-score candidates & update       ▼                             ▼
            Direct ByteBuffer buffer     UTF-16 Surrogate?            Standard Character
                      │                        │                             │
            ic.setComposingText()        deleteSurroundingText(2, 0)   deleteSurroundingText(1, 0)
```

- **Retroactive Un-Commit:** If `⌫ DEL` is pressed immediately following a word commit via `[ SPACE ]`, the trailing space and word are removed, and the raw digit sequence is re-hydrated into the active composing buffer.
- **Continuous Accelerated Delete:**
  - $0–350	ext{ms}$: Single character deletion.
  - $350–1200	ext{ms}$: Repeating deletion (every $50	ext{ms}$).
  - $>1200	ext{ms}$: High-speed tokenized whole-word deletion.

---

## 6. Typing Engines & Predictive Pipelines

### 6.1. Multi-Tap (ABC) Cycling Engine (No-Caps Cycle)

When T9 prediction is toggled off, keys cycle strictly through lowercase characters and numbers. Capitalization is governed exclusively by the Shift Key State.

#### Deterministic Multi-Tap Mapping Matrix

| Key | Primary Cycle Sequence (Shift: Lowercase) | Capitalized Cycle Sequence (Shift: Title/Upper) |
| :--- | :--- | :--- |
| **[ 1 ]** | `. ` $	o$ `, ` $	o$ `? ` $	o$ `! ` $	o$ `' ` $	o$ `- ` $	o$ `@ ` $	o$ `1` | `. ` $	o$ `, ` $	o$ `? ` $	o$ `! ` $	o$ `' ` $	o$ `- ` $	o$ `@ ` $	o$ `1` |
| **[ 2 ]** | `a` $	o$ `b` $	o$ `c` $	o$ `2` | `A` $	o$ `B` $	o$ `C` $	o$ `2` |
| **[ 3 ]** | `d` $	o$ `e` $	o$ `f` $	o$ `3` | `D` $	o$ `E` $	o$ `F` $	o$ `3` |
| **[ 4 ]** | `g` $	o$ `h` $	o$ `i` $	o$ `4` | `G` $	o$ `H` $	o$ `I` $	o$ `4` |
| **[ 5 ]** | `j` $	o$ `k` $	o$ `l` $	o$ `5` | `J` $	o$ `K` $	o$ `L` $	o$ `5` |
| **[ 6 ]** | `m` $	o$ `n` $	o$ `o` $	o$ `6` | `M` $	o$ `N` $	o$ `O` $	o$ `6` |
| **[ 7 ]** | `p` $	o$ `q` $	o$ `r` $	o$ `s` $	o$ `7` | `P` $	o$ `Q` $	o$ `R` $	o$ `S` $	o$ `7` |
| **[ 8 ]** | `t` $	o$ `u` $	o$ `v` $	o$ `8` | `T` $	o$ `U` $	o$ `V` $	o$ `8` |
| **[ 9 ]** | `w` $	o$ `x` $	o$ `y` $	o$ `z` $	o$ `9` | `W` $	o$ `X` $	o$ `Y` $	o$ `Z` $	o$ `9` |
| **[ 0 ]** | ` ` (space) $	o$ `0` $	o$ `
` | ` ` (space) $	o$ `0` $	o$ `
` |

- **Cycle Expiration:** $600	ext{ms}$ timeout or tapping a different key commits the active character.
- **Auto-Reset:** If Shift is `TITLECASE`, committing the first character automatically resets Shift to `LOWERCASE`.

---

### 6.2. Predictive T9 Engine (Beam Search & DAWG)

Traverses pre-compiled binary DAWG nodes in C++20.

#### 2D Gaussian Touch Scoring:
$$P(\text{key}_k \mid x, y) = \exp\left( - \frac{(x - x_k)^2 + (y - y_k)^2}{2\sigma^2} \right)$$

Candidate strings are decoded into a Direct `ByteBuffer` with zero JVM object allocation.

---

## 7. EditorInfo & InputType Dynamic Routing Engine

Evaluated in `InputMethodService.onStartInputView(info: EditorInfo, restarting: Boolean)`:

- **Password Fields (`TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`):**
  - Beam search engine: **DISABLED**.
  - Suggestion Strip: **Hidden**.
  - LMDB learning: **DISABLED** (zero writes).
  - Switches automatically to literal Multi-Tap or numeric layout.
- **Numeric Fields (`TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`):**
  - Displays Page 1 (Numeric) by default.
- **Incognito Mode (`IME_FLAG_NO_PERSONALIZED_LEARNING`):**
  - Bypasses user dictionary logging and frequency updates.

---

## 8. Keyboard Settings Architecture & Dynamic Config Sync

### 8.1. SettingsActivity Hierarchy & Preferences

A native Android settings interface structured via `PreferenceFragmentCompat`:

- **Haptics & Audio Feedback:**
  - **Haptic Feedback:** Toggle (On/Off).
  - **Vibration Intensity:** Slider ($0\text{ms}$ to $100\text{ms}$, default: $25\text{ms}$).
  - **Audio Click Feedback:** Toggle (On/Off).
  - **Sound Volume:** Slider ($0\%$ to $100\%$, default: $60\%$).
  - **Sound Style:** Dropdown (Mechanical Classic, Modern Soft, Minimalist Click).
- **Layout & Display:**
  - **Keyboard Overall Height:** Slider ($220\text{dp}$ to $320\text{dp}$, default: $260\text{dp}$).
  - **Candidate Font Size:** Slider ($12\text{sp}$ to $18\text{sp}$, default: $14\text{sp}$).
  - **One-Handed Mode:** Dropdown (Disabled, Left-Handed, Right-Handed).
- **Typing Behaviors:**
  - **Default Input Mode:** Dropdown (Predictive T9, Multi-Tap ABC).
  - **Long-Press Delay:** Slider ($200\text{ms}$ to $600\text{ms}$, default: $350\text{ms}$).
  - **Multi-Tap Commit Timeout:** Slider ($400\text{ms}$ to $1000\text{ms}$, default: $600\text{ms}$).
  - **Auto-Capitalization:** Toggle (On/Off).
  - **Auto-Space After Commit:** Toggle (On/Off).
  - **Double-Space Period:** Toggle (On/Off).
  - **Spacebar Cursor Scrubbing:** Toggle (On/Off).
- **Dictionary & Language Management:**
  - **Primary Startup Language:** Dropdown (Indonesian, English).
  - **Slang / Bahasa Gaul Priority:** Toggle (On/Off).
  - **Dynamic Decay Half-Life:** Dropdown (14 Days, 30 Days, Disabled).
  - **User Dictionary Editor:** Search, add, or purge learned custom words.
  - **Reset User Dictionary:** Hard reset clearing the dynamic LMDB database.

---

### 8.2. Zero-Allocation C++ Configuration Synchronization

Whenever preferences are updated, `SettingsObserver` flushes a flat C primitive struct across JNI into the native core:

```cpp
// native_config.hpp
struct NativeConfig {
    float touch_variance_sigma;     // Default: 42.0f
    uint32_t long_press_timeout_ms; // Default: 350
    uint32_t multi_tap_timeout_ms;  // Default: 600
    uint8_t auto_space_enabled;     // 0 or 1
    uint8_t slang_boost_enabled;    // 0 or 1
    uint32_t decay_half_life_days;  // Default: 30
};

extern "C" JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeSyncConfig(
    JNIEnv* env, jobject thiz,
    jfloat sigma, jint longPressMs, jint multiTapMs,
    jboolean autoSpace, jboolean slangBoost, jint decayDays) {
    g_config.touch_variance_sigma = sigma;
    g_config.long_press_timeout_ms = longPressMs;
    g_config.multi_tap_timeout_ms = multiTapMs;
    g_config.auto_space_enabled = autoSpace ? 1 : 0;
    g_config.slang_boost_enabled = slangBoost ? 1 : 0;
    g_config.decay_half_life_days = decayDays;
}
```

---

## 9. Performance Benchmarks & Non-Functional Verification

| Target Metric | Benchmark (SwiftKey / Gboard) | OpenT9 Target Spec | Verification Harness |
| :--- | :--- | :--- | :--- |
| **Disambiguation Latency** | 10–25ms | < 0.5ms (C++20 Beam Search) | Google Benchmark / NDK CI |
| **Rollback Execution Time** | Full re-traversal | < 0.01ms ($O(1)$ Stack Pop) | Nanosecond Timing Test |
| **Cold-Start Launch Time** | 200–400ms | < 30ms | Android Macrobenchmark |
| **RAM Footprint (Resident)** | 85 MB – 190 MB | < 25 MB (`mmap` page cache) | Android Profiler Memory Track |
| **Frame Render Latency** | Janks on GC cycles | 8.3ms (Locked 120 FPS) | `dumpsys gfxinfo framestats` |
| **Garbage Collector Churn** | Variable allocations | 0 bytes / frame | ART Allocation Tracker |
| **Audio Feedback Latency** | 35–60ms (`SoundPool`) | < 10ms (Oboe / AAudio) | Low-Latency Mic Trace |
| **APK Binary Size** | 45 MB – 80 MB | < 10 MB (Lexicons included) | `apkanalyzer` CLI |

---

## 10. Repository Layout

```text
opent9-android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml             # No INTERNET permission
│       ├── java/com/opent9/keyboard/
│       │   ├── OpenT9InputMethodService.kt # Thin IME lifecycle wrapper
│       │   ├── settings/
│       │   │   ├── SettingsActivity.kt     # Preferences Activity
│       │   │   └── SettingsFragment.kt     # PreferenceFragmentCompat bindings
│       │   ├── ui/
│       │   │   ├── T9KeyboardView.kt       # Zero-allocation custom Canvas View
│       │   │   ├── TouchGestureTracker.kt  # Keypad & swipeable strip state machine
│       │   │   ├── KeyAtlas.kt             # Pre-computed key bounds & glyph coordinates
│       │   │   └── EmojiAtlas.kt           # UTF-32 emoji codepoint tables
│       │   └── jni/
│       │       └── NativeEngineBridge.kt   # Direct ByteBuffer JNI wrappers
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── include/
│       │   │   ├── dawg_engine.hpp         # DAWG parser & O(1) history stack
│       │   │   ├── spatial_scoring.hpp     # 2D Gaussian touch scoring
│       │   │   ├── multi_tap_engine.hpp    # Deterministic lowercase multi-tap
│       │   │   ├── lexicon_manager.hpp     # Dual mmap pointer hot-swapper
│       │   │   ├── audio_engine.hpp        # Google Oboe AAudio sound driver
│       │   │   ├── dynamic_store.hpp       # LMDB decay storage
│       │   │   └── native_config.hpp       # Flat config sync struct
│       │   └── src/
│       │       ├── dawg_engine.cpp
│       │       ├── spatial_scoring.cpp
│       │       ├── multi_tap_engine.cpp
│       │       ├── lexicon_manager.cpp
│       │       ├── audio_engine.cpp
│       │       └── jni_bridge.cpp
│       └── assets/dictionaries/
│           ├── en_lexicon.dawg             # Static binary DAWG (~4.1MB)
│           └── id_lexicon.dawg             # Static binary DAWG (~3.8MB)
```

---

## 11. Phased TDD Implementation Roadmap

```text
Phase 1: Native Core & DAWG Engine (C++20)
    └──> Phase 2: Touch Tracker & Swipeable Suggestion Strip
        └──> Phase 3: System Buttons (ENTER, SHIFT, DEL) & Multi-Tap
            └──> Phase 4: Settings Activity & Native Config Sync
                └──> Phase 5: Audio Engine, Haptics & Packaging
```

### Phase 1: Native Core & DAWG Engine (C++20)
- Unit tests verifying sequence `4663` yields `["good", "home", "gone", "hood"]` in $< 0.2\text{ms}$.
- Unit tests confirming `pop_stroke()` restores prior depth in $< 0.01\text{ms}$.
- Pointer swap benchmarking confirming $< 0.05\text{ms}$ lexicon switching.

### Phase 2: Touch Tracker & Swipeable Suggestion Strip
- Implement `TouchGestureTracker.kt` with partitioned $Y < 40\text{dp}$ candidate strip scrolling.
- Unit tests verifying spatial flick evaluation (left, right, up, down) matching dual-symbol layout.
- Implement horizontal scroll and auto-fit width measurement in `T9KeyboardView.kt`.

### Phase 3: System Keys & Multi-Tap Engine
- Implement `ENTER` polymorphic action mapping for all `EditorInfo` action flags.
- Implement `SHIFT` 3-state cycling machine (`LOWER`, `TITLE`, `UPPER`).
- Implement `multi_tap_engine.hpp` executing lowercase-first cycling without capital letter phases.

### Phase 4: Settings Activity & Native Config Sync
- Implement `SettingsActivity.kt` and `SettingsFragment.kt`.
- Connect asynchronous preference observer flushing `NativeConfig` primitives across JNI.
- Implement user dictionary management and half-life decay timers.

### Phase 5: Audio Engine, Haptics & Packaging
- Connect Google Oboe C++ sound driver to key events ($< 10\text{ms}$ latency).
- Connect Android `Vibrator` / `VibrationEffect` pipeline.
- Inspect APK ensuring `android.permission.INTERNET` is omitted and package size $< 10\text{MB}$.

---

## Audit & Comparison: Gaps Closed from prd2.md

1. **Language Button & Visual Indicator:**
  - Fixed ambiguous dual-text label (`EN / ID`) to display solely the active language code (`EN` or `ID`).
  - Added the illuminated neon indicator bar under the text to clearly verify T9 predictive state versus Multi-Tap ABC mode.
2. **Spatial-Symmetric Flick Selection:**
  - Standardized all dual-symbol keys (e.g., `[ Rp  $ ]`, `[ +  - ]`, `[ {  } ]`). Gesturing left selects the left glyph; gesturing right selects the right glyph.
3. **Suggestion Strip Evolution:**
  - Eliminated the unnecessary Page 4 full-keyboard overlay.
  - Transformed the 40dp strip into an auto-fitting, horizontally swipeable candidate bar supporting up to 16 beam search candidates without layout distortion.
4. **Streamlined Multi-Tap Cycling:**
  - Removed uppercase characters from the multi-tap loop, cutting rotation depth in half (`a` $	o$ `b` $	o$ `c` $	o$ `2`). Capitalization is decoupled and handled by the Shift key state machine.
5. **System Buttons & Settings Activity:**
  - Formally detailed the state machines for `ENTER` (polymorphic `EditorInfo` actions), `SHIFT` (3-state cycle with glyph feedback), and `DEL` (surrogate-aware deletion and un-commit).
  - Fully designed the `SettingsActivity` hierarchy, persistence model, and zero-allocation C++ synchronization bridge.
    prd3.md
    Displaying prd3.md.