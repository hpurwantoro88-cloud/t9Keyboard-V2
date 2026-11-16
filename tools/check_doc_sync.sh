#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODE="full"
STRICT=0

for arg in "$@"; do
    case "$arg" in
        --staged) MODE="staged" ;;
        --strict) STRICT=1 ;;
        -h|--help)
            echo "Usage: $0 [--staged] [--strict]"
            echo "  --staged  Check staged Git contract files against prd.md (for pre-commit hook)"
            echo "  --strict  Exit non-zero if prd.md is missing from staged contract changes"
            exit 0
            ;;
    esac
done

CONTRACT_FILES=(
    "app/build.gradle.kts"
    "app/src/main/AndroidManifest.xml"
    "app/src/main/res/xml/method.xml"
    "app/src/main/res/xml/preferences.xml"
    "app/src/main/java/com/opent9/keyboard/settings/SettingsObserver.kt"
    "app/src/main/java/com/opent9/keyboard/settings/KeyboardLayoutConfig.kt"
    "app/src/main/java/com/opent9/keyboard/ui/KeyAtlas.kt"
    "app/src/main/java/com/opent9/keyboard/ui/PageController.kt"
    "app/src/main/java/com/opent9/keyboard/ui/FlickDisambiguator.kt"
    "app/src/main/java/com/opent9/keyboard/ui/EmojiAtlas.kt"
    "app/src/main/java/com/opent9/keyboard/KeyboardActionDispatcher.kt"
    "app/src/main/java/com/opent9/keyboard/jni/NativeEngineBridge.kt"
    "app/src/main/cpp/include/native_config.hpp"
    "app/src/main/cpp/include/dawg_engine.hpp"
    "app/src/main/cpp/src/jni_bridge.cpp"
)

warn() {
    echo -e "\033[33m[doc-sync WARN]\033[0m $*" >&2
}

err() {
    echo -e "\033[31m[doc-sync FAIL]\033[0m $*" >&2
}

ok() {
    echo -e "\033[32m[doc-sync OK]\033[0m $*"
}

if [[ "$MODE" == "staged" ]]; then
    STAGED_FILES="$(git diff --cached --name-only --diff-filter=ACMR || true)"
    if [[ -z "$STAGED_FILES" ]]; then
        exit 0
    fi

    CHANGED_CONTRACTS=()
    for cf in "${CONTRACT_FILES[@]}"; do
        if echo "$STAGED_FILES" | grep -qxF "$cf"; then
            CHANGED_CONTRACTS+=("$cf")
        fi
    done

    if [[ ${#CHANGED_CONTRACTS[@]} -gt 0 ]]; then
        if ! echo "$STAGED_FILES" | grep -qxF "prd.md"; then
            warn "File kontrak berikut berubah di staging, tetapi prd.md belum di-update/stage:"
            for f in "${CHANGED_CONTRACTS[@]}"; do
                echo "  - $f" >&2
            done
            echo "  -> Pastikan perubahan arsitektur/layout/settings/ABI sudah tercermin di prd.md." >&2
            echo "  -> Jalankan: ./tools/check_doc_sync.sh untuk cek konsistensi kontrak." >&2
            if [[ "$STRICT" -eq 1 && "${SKIP_DOC_CHECK:-0}" != "1" ]]; then
                err "Commit diblokir (--strict). Stage prd.md atau set SKIP_DOC_CHECK=1 jika memang tidak perlu."
                exit 1
            fi
        else
            ok "File kontrak berubah dan prd.md ikut di-stage."
        fi
    fi
    exit 0
fi

# Full structural contract check
FAILED=0

if [[ ! -s "prd.md" ]]; then
    err "prd.md tidak ditemukan atau kosong."
    FAILED=1
else
    ok "prd.md tersedia."
fi

# 1. Verify no INTERNET permission in AndroidManifest.xml
if grep -q "android.permission.INTERNET" "app/src/main/AndroidManifest.xml"; then
    err "AndroidManifest.xml mendeklarasikan android.permission.INTERNET (melanggar kontrak offline prd.md)."
    FAILED=1
else
    ok "Privacy check: android.permission.INTERNET tidak ditemukan."
fi

# 2. Verify all preferences.xml keys are referenced in SettingsObserver.kt or SettingsActivity.kt
PREF_KEYS="$(grep -oE 'app:key="[^"]+"' app/src/main/res/xml/preferences.xml | sed -E 's/app:key="([^"]+)"/\1/')"
MISSING_PREF_REFS=()
for key in $PREF_KEYS; do
    if ! grep -q "\"$key\"" app/src/main/java/com/opent9/keyboard/settings/SettingsObserver.kt && \
       ! grep -q "\"$key\"" app/src/main/java/com/opent9/keyboard/settings/SettingsActivity.kt; then
        MISSING_PREF_REFS+=("$key")
    fi
done

if [[ ${#MISSING_PREF_REFS[@]} -gt 0 ]]; then
    err "Preference key di preferences.xml belum terhubung ke SettingsObserver/SettingsActivity: ${MISSING_PREF_REFS[*]}"
    FAILED=1
else
    ok "Semua preference key di preferences.xml terhubung ke SettingsObserver/SettingsActivity."
fi

# 3. Verify Page 0 key 15 legacy dead handlers are absent
if grep -q "EMOJI_DOT" app/src/main/java/com/opent9/keyboard/KeyboardActionDispatcher.kt || \
   grep -q "EMOJI_DOT" app/src/main/java/com/opent9/keyboard/ui/KeyAtlas.kt; then
    warn "Legacy KeyType.EMOJI_DOT masih ada di kode."
fi

# 4. Verify zero-copy mmap requirement (noCompress for lexicon & ngram assets) & report asset sizes
if grep -qE 'noCompress.*dawg' "app/build.gradle.kts" && grep -qE 'noCompress.*bin' "app/build.gradle.kts"; then
    ok "app/build.gradle.kts mengaktifkan noCompress untuk aset .dawg dan .bin (zero-copy mmap)."
else
    err "app/build.gradle.kts harus mengaktifkan noCompress untuk .dawg dan .bin (berisiko menyalin file saat load)."
    FAILED=1
fi

if [[ -d "app/src/main/assets/dictionaries" ]]; then
    EN_BYTES=$(stat -c%s "app/src/main/assets/dictionaries/en_lexicon.dawg" 2>/dev/null || echo 0)
    ID_BYTES=$(stat -c%s "app/src/main/assets/dictionaries/id_lexicon.dawg" 2>/dev/null || echo 0)
    EN_BG_BYTES=$(stat -c%s "app/src/main/assets/dictionaries/en_bigrams.bin" 2>/dev/null || echo 0)
    ID_BG_BYTES=$(stat -c%s "app/src/main/assets/dictionaries/id_bigrams.bin" 2>/dev/null || echo 0)
    TOTAL_MB=$(awk "BEGIN { printf \"%.2f\", ($EN_BYTES + $ID_BYTES + $EN_BG_BYTES + $ID_BG_BYTES) / (1024 * 1024) }")
    ok "Total ukuran aset kamus + N-gram saat ini ${TOTAL_MB} MB (tanpa batas MB ketat; prioritas zero-copy mmap & latensi)."
fi

if [[ "$FAILED" -ne 0 ]]; then
    exit 1
fi
ok "Semua pengecekan kontrak doc-sync lolos."
