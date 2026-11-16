---
trigger: always_on
description: Keep prd.md synchronized with contract, layout, gesture, settings, and native ABI changes via micro-updates.
---

# OpenT9 Documentation Sync Rule

Perform a targeted **micro-update** to `prd.md` in the same task (before commit) **only** when modifying any contract file or resolving/introducing items in `prd.md` Section 11:

1. **Triggering Contract Files:**
   - Layout & Gestures: `KeyAtlas.kt`, `PageController.kt`, `FlickDisambiguator.kt`, `EmojiAtlas.kt`, `KeyboardLayoutConfig.kt`, `KeyboardActionDispatcher.kt`
   - Settings & Manifest: `preferences.xml`, `SettingsObserver.kt`, `AndroidManifest.xml`, `method.xml`
   - Native / JNI / Build: `native_config.hpp`, `dawg_engine.hpp`, `jni_bridge.cpp`, `NativeEngineBridge.kt`, `app/build.gradle.kts`

2. **Micro-Update Scope:**
   - Update only the affected section(s) of `prd.md` (do not rewrite unrelated sections).
   - Move completed items or record new gaps/bugs in `prd.md` Section 11 (*Implementation Status & Remaining Gaps*).
   - Skip `prd.md` edits for pure internal refactors, bugfixes that preserve contracts, or test-only changes.

3. **Verification Before Reporting Done:**
   - Run `./tools/check_doc_sync.sh` whenever contract files or `prd.md` were modified.
   - Run `./gradlew testDebugUnitTest` (especially `ContractConsistencyTest`) after Kotlin/resource/test changes.
