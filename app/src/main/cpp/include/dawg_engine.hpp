#pragma once
#include <cstddef>
#include <cstdint>
#include "dynamic_store.hpp"
#include "native_config.hpp"
#include "ngram_engine.hpp"
#include "spatial_scoring.hpp"

// Naturally aligned (4-byte/16-byte) OPENT9D2 T9-Digit-Indexed Minimal DAFSA structures
struct DawgHeaderV2 {
    char magic[8];              // "OPENT9D2"
    uint32_t version;           // 2
    uint32_t node_count;
    uint32_t edge_count;
    uint32_t word_count;
    uint32_t bucket_count;      // Number of uint32_t word IDs in bucketWords[]
    uint32_t string_pool_bytes;
    uint32_t max_word_len;
    uint32_t root_node_index;   // 0
    uint32_t flags;             // Reserved (0)
    uint32_t reserved;          // Pad header to 48 bytes (multiple of 16)
};

struct DigitNodeV2 {
    uint32_t first_child_edge;  // Index into DigitEdgeV2[] (0 = no children / invalid for root)
    uint32_t terminal_offset;   // Start index in bucketWords[]
    uint32_t completion_offset; // Start index in bucketWords[]
    uint16_t terminal_count;    // Exact matches at this digit path
    uint16_t completion_count;  // Cached top-K completions (depth <= MAX_CACHED_COMPLETION_DEPTH)
};

struct DigitEdgeV2 {
    uint32_t target_node;       // Index into DigitNodeV2[]
    uint8_t digit;              // T9 digit (2..9)
    uint8_t has_next_sibling;   // 1 if next sibling exists at edgeIdx + 1
    uint16_t max_log_freq;      // Maximum quantized log10(1+freq) * 4096 in target subtree
};

struct LexiconWordV2 {
    uint32_t str_offset;        // Null-terminated display string offset in stringPool
    uint16_t log_freq;          // Quantized round(log10(1 + freq) * 4096)
    uint8_t length;             // Character length of display string
    uint8_t flags;              // Bit 0: FLAG_SLANG, Bit 1: FLAG_CONTRACTION
};

static_assert(sizeof(DawgHeaderV2) == 48, "DawgHeaderV2 must be 48 bytes");
static_assert(sizeof(DigitNodeV2) == 16, "DigitNodeV2 must be 16 bytes");
static_assert(sizeof(DigitEdgeV2) == 8, "DigitEdgeV2 must be 8 bytes");
static_assert(sizeof(LexiconWordV2) == 8, "LexiconWordV2 must be 8 bytes");

constexpr uint8_t LEX_FLAG_SLANG = 0x01;
constexpr uint8_t LEX_FLAG_CONTRACTION = 0x02;
constexpr float LOG_FREQ_SCALE = 4096.0f;
constexpr int MAX_CACHED_COMPLETION_DEPTH = 6;
constexpr size_t MAX_CACHED_COMPLETIONS_PER_NODE = 16;

constexpr size_t MAX_BEAM_SIZE = 32;
constexpr size_t MAX_CANDIDATES = 16;
constexpr size_t MAX_WORD_CHARS = 48;
constexpr size_t MAX_STROKE_DEPTH = 48;

struct BeamPath {
    uint32_t node_index;
    float spatial_score_sum;
    float score;
    uint8_t edit_count;
};

struct CandidateWord {
    char word[MAX_WORD_CHARS];
    uint8_t length;
    float score;
    uint32_t frequency;
};

struct StrokeState {
    int digit;
    float touch_x;
    float touch_y;
    uint8_t beam_count;
    BeamPath beam[MAX_BEAM_SIZE];
    uint8_t candidate_count;
    CandidateWord candidates[MAX_CANDIDATES];
};

class T9SingleEngine {
public:
    T9SingleEngine();

    bool setLexiconData(const uint8_t* data, size_t size);
    bool isLoaded() const { return header != nullptr; }
    void reset();

    bool pushStroke(int digit, float touchX, float touchY, const SpatialScorer& scorer, const NativeConfig& config);
    bool popStroke();

    uint8_t getCandidateCount() const;
    const CandidateWord* getCandidates() const;
    int getDepth() const { return currentDepth; }

    void setDynamicStore(const DynamicStore* store) { dynamicStore = store; }
    void setNgramEngine(const NgramEngine* ngram);
    void setContextWord(const char* prevWord);

    int getNextWordCandidates(const char* prevWord, const NativeConfig& config,
                              CandidateWord* outCands, int maxCands) const;

private:
    const DawgHeaderV2* header = nullptr;
    const DigitNodeV2* nodes = nullptr;
    const DigitEdgeV2* edges = nullptr;
    const LexiconWordV2* words = nullptr;
    const uint32_t* bucketWords = nullptr;
    const char* stringPool = nullptr;

    size_t totalNodes = 0;
    size_t totalEdges = 0;
    size_t totalWords = 0;
    size_t totalBucketWords = 0;
    size_t totalStringBytes = 0;

    const DynamicStore* dynamicStore = nullptr;
    const NgramEngine* ngramEngine = nullptr;
    char activeContextWord[MAX_WORD_CHARS]{};
    uint32_t activeContextBucket = NgramEngine::INVALID_BUCKET;

    int currentDepth = 0;
    StrokeState history[MAX_STROKE_DEPTH];

    void refreshContextBucket();

    void collectBestCompletions(uint32_t startNodeIdx, float baseScoreOffset,
                                CandidateWord* outCands, uint8_t& candCount,
                                uint8_t maxCands) const;

    void findClosestWords(int digit, float touchX, float touchY, const SpatialScorer& scorer,
                          const NativeConfig& config, CandidateWord* outCands, uint8_t& candCount,
                          uint8_t maxCands);
};

/**
 * Bilingual-capable T9 prediction coordinator managing primary (active language)
 * and optional secondary (code-switching) lexicon engines with unified scoring.
 */
class DawgEngine {
public:
    static constexpr float SECONDARY_LANG_PENALTY = 0.55f;

    DawgEngine();

    bool setLexiconData(const uint8_t* data, size_t size);
    bool setSecondaryLexiconData(const uint8_t* data, size_t size);

    void reset();

    bool pushStroke(int digit, float touchX, float touchY, const SpatialScorer& scorer, const NativeConfig& config);
    bool popStroke();

    uint8_t getCandidateCount() const;
    const CandidateWord* getCandidates() const;
    int serializeCandidates(uint8_t* outBuffer, int maxBytes) const;

    void setDynamicStore(const DynamicStore* store);
    void setNgramEngine(const NgramEngine* ngram);
    void setSecondaryNgramEngine(const NgramEngine* ngram);
    void setContextWord(const char* prevWord);

    int getNextWordCandidates(const char* prevWord, const NativeConfig& config,
                              CandidateWord* outCands, int maxCands) const;
    int serializeNextWordCandidates(const char* prevWord, const NativeConfig& config,
                                    uint8_t* outBuffer, int maxBytes) const;

    int getDepth() const { return primary.getDepth(); }

private:
    struct MergedStrokeState {
        bool active = false;
        uint8_t count = 0;
        CandidateWord candidates[MAX_CANDIDATES];
    };

    T9SingleEngine primary;
    T9SingleEngine secondary;
    MergedStrokeState mergedHistory[MAX_STROKE_DEPTH];
};
