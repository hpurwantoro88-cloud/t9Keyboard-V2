#pragma once
#include <cstdint>
#include <cstddef>
#include <cstring>
#include <vector>
#include <string>
#include "spatial_scoring.hpp"
#include "native_config.hpp"
#include "dynamic_store.hpp"

#pragma pack(push, 1)
struct DawgHeader {
    char magic[8];             // "OPENT9D1"
    uint32_t version;          // 1
    uint32_t node_count;
    uint32_t edge_count;
    uint32_t word_count;
    uint32_t max_word_len;
    uint32_t root_first_edge;
    uint32_t reserved;
};

struct DawgEdge {
    char letter;               // 'a' - 'z'
    uint8_t is_terminal;       // 1 if word ends here
    uint8_t has_next_sibling;  // 1 if next sibling exists
    uint8_t digit;             // T9 digit (2..9)
    uint32_t target_first_edge;// Index of first edge of target node (0 = leaf)
    uint32_t frequency;        // Unigram corpus frequency
    uint32_t max_frequency;    // Maximum frequency in subtree
};
#pragma pack(pop)

static_assert(sizeof(DawgHeader) == 36, "DawgHeader must be 36 bytes");
static_assert(sizeof(DawgEdge) == 16, "DawgEdge must be 16 bytes");

constexpr size_t MAX_BEAM_SIZE = 32;
constexpr size_t MAX_CANDIDATES = 16;
constexpr size_t MAX_WORD_CHARS = 48;
constexpr size_t MAX_STROKE_DEPTH = 48;

struct BeamPath {
    uint32_t edge_index;
    float spatial_score_sum;
    float score;
    char word[MAX_WORD_CHARS];
    uint8_t word_len;
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

class DawgEngine {
public:
    DawgEngine();

    // Set active lexicon memory
    bool setLexiconData(const uint8_t* data, size_t size);

    // Reset current typing session / stroke stack
    void reset();

    // Push new T9 stroke (digit 2-9 and touch coordinates)
    bool pushStroke(int digit, float touchX, float touchY, const SpatialScorer& scorer, const NativeConfig& config);

    // Pop last stroke in O(1)
    bool popStroke();

    // Get current candidate count and details
    uint8_t getCandidateCount() const;
    const CandidateWord* getCandidates() const;

    // Direct candidate copy to pre-allocated buffer:
    // Format: [uint8_t count][for each: uint8_t len, chars...]
    int serializeCandidates(uint8_t* outBuffer, int maxBytes) const;

    // Set dynamic user vocabulary store
    void setDynamicStore(const DynamicStore* store) { dynamicStore = store; }

    int getDepth() const { return currentDepth; }

private:
    const DawgHeader* header = nullptr;
    const DawgEdge* edges = nullptr;
    size_t totalEdges = 0;
    const DynamicStore* dynamicStore = nullptr;

    int currentDepth = 0;
    StrokeState history[MAX_STROKE_DEPTH];
};
