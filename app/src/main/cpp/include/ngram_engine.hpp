#pragma once
#include <cctype>
#include <cstddef>
#include <cstdint>

struct NgramHeaderV1 {
    char magic[8];              // "OPENT9N1"
    uint32_t version;           // 1
    uint32_t prev_entry_count;  // Number of indexed previous words
    uint32_t bigram_count;      // Total NgramEdgeV1 entries
    uint32_t flags;             // Reserved (0)
    uint32_t reserved[2];       // Pad to 32 bytes (multiple of 16)
};

struct WordHashKeyV1 {
    uint64_t hash;              // FNV-1a 64-bit lowercase hash of prev_word
    uint32_t word_id;           // word_id in paired OPENT9D2 lexicon
    uint32_t bucket_idx;        // Index into PrevWordBucketV1[]
};

struct PrevWordBucketV1 {
    uint32_t top_offset;        // Offset in NgramEdgeV1[] (top-K sorted by prob DESC)
    uint32_t sorted_offset;     // Offset in NgramEdgeV1[] (all sorted by next_word_id ASC)
    uint16_t top_count;         // Up to 16 entries for O(1) idle NWP
    uint16_t sorted_count;      // Total bigrams for this prev_word
    uint32_t reserved;          // Pad to 16 bytes
};

struct NgramEdgeV1 {
    uint32_t next_word_id;      // word_id in paired OPENT9D2 lexicon
    uint8_t quantized_log_prob; // Scaled [0..255] -> [0.0 .. 4.0]
    uint8_t flags;
    uint16_t reserved;
};

static_assert(sizeof(NgramHeaderV1) == 32, "NgramHeaderV1 must be 32 bytes");
static_assert(sizeof(WordHashKeyV1) == 16, "WordHashKeyV1 must be 16 bytes");
static_assert(sizeof(PrevWordBucketV1) == 16, "PrevWordBucketV1 must be 16 bytes");
static_assert(sizeof(NgramEdgeV1) == 8, "NgramEdgeV1 must be 8 bytes");

constexpr float NGRAM_PROB_SCALE = 64.0f; // 255 / 64.0 = 3.984

inline uint64_t fnv1aHash64Lower(const char* str) {
    if (!str) return 0;
    uint64_t hash = 14695981039346656037ULL;
    for (size_t i = 0; str[i] != '\0'; ++i) {
        unsigned char c = static_cast<unsigned char>(std::tolower(static_cast<unsigned char>(str[i])));
        hash ^= static_cast<uint64_t>(c);
        hash *= 1099511628211ULL;
    }
    return hash;
}

struct LexiconWordV2;

class NgramEngine {
public:
    static constexpr uint32_t INVALID_BUCKET = UINT32_MAX;

    NgramEngine() = default;

    bool setNgramData(const uint8_t* data, size_t size);
    void clear();
    bool isLoaded() const { return header != nullptr; }

    /**
     * Resolves a previous word string to its bucket index in O(log V) using FNV-1a 64-bit hash
     * verified against the paired OPENT9D2 string pool. Returns INVALID_BUCKET if not found.
     */
    uint32_t resolvePrevBucket(
        const char* prevWord,
        const LexiconWordV2* lexWords,
        size_t lexWordCount,
        const char* lexStrings,
        size_t lexStringBytes
    ) const;

    /**
     * O(log K) contextual bigram boost for (prevBucketIdx -> nextWordId).
     * Returns 0.0f if no bigram entry exists.
     */
    float getBigramBoost(uint32_t prevBucketIdx, uint32_t nextWordId) const;

    /**
     * O(1) retrieval of top-K next word IDs and their quantized boosts for idle NWP.
     */
    int getTopNextWordIds(
        uint32_t prevBucketIdx,
        uint32_t* outWordIds,
        float* outBoosts,
        int maxCount
    ) const;

private:
    const NgramHeaderV1* header = nullptr;
    const WordHashKeyV1* hashKeys = nullptr;
    const PrevWordBucketV1* buckets = nullptr;
    const NgramEdgeV1* edges = nullptr;

    size_t totalPrevEntries = 0;
    size_t totalBigrams = 0;
};
