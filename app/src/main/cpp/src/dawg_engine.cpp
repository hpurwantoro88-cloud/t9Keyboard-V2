#include "dawg_engine.hpp"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <ctime>

DawgEngine::DawgEngine() {
    reset();
}

bool DawgEngine::setLexiconData(const uint8_t* data, size_t size) {
    if (!data || size < sizeof(DawgHeader)) {
        header = nullptr;
        edges = nullptr;
        totalEdges = 0;
        return false;
    }

    const DawgHeader* hdr = reinterpret_cast<const DawgHeader*>(data);
    if (std::memcmp(hdr->magic, "OPENT9D1", 8) != 0 || hdr->version != 1) {
        header = nullptr;
        edges = nullptr;
        totalEdges = 0;
        return false;
    }

    size_t expectedSize = sizeof(DawgHeader) + (hdr->edge_count * sizeof(DawgEdge));
    if (size < expectedSize) {
        header = nullptr;
        edges = nullptr;
        totalEdges = 0;
        return false;
    }

    header = hdr;
    edges = reinterpret_cast<const DawgEdge*>(data + sizeof(DawgHeader));
    totalEdges = hdr->edge_count;
    reset();
    return true;
}

void DawgEngine::reset() {
    currentDepth = 0;
    std::memset(history, 0, sizeof(history));
}

bool DawgEngine::pushStroke(int digit, float touchX, float touchY, const SpatialScorer& scorer, const NativeConfig& config) {
    if (!header || !edges || digit < 2 || digit > 9) {
        return false;
    }
    if (currentDepth >= static_cast<int>(MAX_STROKE_DEPTH) - 1) {
        return false;
    }

    StrokeState& nextState = history[currentDepth];
    std::memset(&nextState, 0, sizeof(StrokeState));
    nextState.digit = digit;
    nextState.touch_x = touchX;
    nextState.touch_y = touchY;

    float spatialScore = scorer.calculateLogLikelihood(digit, touchX, touchY, config.touch_variance_sigma);

    struct TempPath {
        uint32_t edge_index;
        float spatial_sum;
        float beam_score;
        float cand_score;
        char word[MAX_WORD_CHARS];
        uint8_t word_len;
        uint8_t is_terminal;
        uint32_t freq;
        uint32_t max_freq;
    };

    TempPath candidatesPool[MAX_BEAM_SIZE * 4];
    size_t poolSize = 0;

    auto addCandidateEdge = [&](uint32_t eIdx, const char* prefix, uint8_t prefixLen, float prevSpatialSum) {
        if (eIdx >= totalEdges) return;
        const DawgEdge& e = edges[eIdx];
        if (e.digit != digit) return;
        if (prefixLen + 1 >= MAX_WORD_CHARS) return;
        if (poolSize >= sizeof(candidatesPool) / sizeof(candidatesPool[0])) return;

        TempPath& p = candidatesPool[poolSize++];
        p.edge_index = eIdx;
        p.is_terminal = e.is_terminal;
        p.freq = e.frequency;
        p.max_freq = e.max_frequency;
        p.word_len = prefixLen + 1;
        if (prefixLen > 0) {
            std::memcpy(p.word, prefix, prefixLen);
        }
        p.word[prefixLen] = e.letter;
        p.word[prefixLen + 1] = '\0';

        p.spatial_sum = prevSpatialSum + spatialScore;
        p.beam_score = p.spatial_sum + std::log10(1.0f + static_cast<float>(e.max_frequency));
        p.cand_score = p.spatial_sum + std::log10(1.0f + static_cast<float>(e.frequency));
    };

    if (currentDepth == 0) {
        // Search root outgoing edges
        uint32_t eIdx = header->root_first_edge;
        while (eIdx < totalEdges) {
            addCandidateEdge(eIdx, nullptr, 0, 0.0f);
            if (!edges[eIdx].has_next_sibling) break;
            eIdx++;
        }
    } else {
        const StrokeState& prevState = history[currentDepth - 1];
        for (uint8_t b = 0; b < prevState.beam_count; ++b) {
            const BeamPath& bp = prevState.beam[b];
            uint32_t prevEdgeIdx = bp.edge_index;
            if (prevEdgeIdx >= totalEdges) continue;

            uint32_t targetFirst = edges[prevEdgeIdx].target_first_edge;
            if (targetFirst == 0 || targetFirst >= totalEdges) continue;

            uint32_t eIdx = targetFirst;
            while (eIdx < totalEdges) {
                addCandidateEdge(eIdx, bp.word, bp.word_len, bp.spatial_score_sum);
                if (!edges[eIdx].has_next_sibling) break;
                eIdx++;
            }
        }
    }

    // Extract terminal candidates from candidatesPool
    std::vector<TempPath> terminalCandidates;
    terminalCandidates.reserve(poolSize + 8);
    for (size_t i = 0; i < poolSize; ++i) {
        if (candidatesPool[i].is_terminal) {
            terminalCandidates.push_back(candidatesPool[i]);
        }
    }

    // Inject matching dynamic / learned words from DynamicStore
    if (dynamicStore) {
        int strokeDigits[MAX_STROKE_DEPTH];
        for (int d = 0; d < currentDepth; ++d) {
            strokeDigits[d] = history[d].digit;
        }
        strokeDigits[currentDepth] = digit;
        int strokeLen = currentDepth + 1;

        DynamicStore::DynamicMatch dynamicMatches[MAX_CANDIDATES];
        uint64_t nowSec = static_cast<uint64_t>(std::time(nullptr));
        int dynCount = dynamicStore->findMatchingWords(strokeDigits, strokeLen, nowSec, config.decay_half_life_days, dynamicMatches, MAX_CANDIDATES);

        for (int d = 0; d < dynCount; ++d) {
            const auto& dm = dynamicMatches[d];
            bool foundInTerminal = false;
            for (auto& tc : terminalCandidates) {
                if (std::strcmp(tc.word, dm.word) == 0) {
                    foundInTerminal = true;
                    tc.freq = std::max(tc.freq, dm.effective_freq);
                    float boost = config.slang_boost_enabled ? 3.0f : 1.5f;
                    tc.cand_score += boost;
                    break;
                }
            }
            if (!foundInTerminal) {
                TempPath p;
                std::memset(&p, 0, sizeof(p));
                std::memcpy(p.word, dm.word, dm.length + 1);
                p.word_len = dm.length;
                p.is_terminal = dm.is_terminal ? 1 : 0;
                p.freq = dm.effective_freq;
                p.max_freq = dm.effective_freq;
                float prevSpatial = (currentDepth > 0 && history[currentDepth - 1].beam_count > 0)
                                    ? history[currentDepth - 1].beam[0].spatial_score_sum : 0.0f;
                p.spatial_sum = prevSpatial + spatialScore;
                float boost = config.slang_boost_enabled ? 3.0f : 1.5f;
                p.cand_score = p.spatial_sum + std::log10(1.0f + static_cast<float>(dm.effective_freq)) + (dm.is_terminal ? boost : 0.0f);
                p.beam_score = p.cand_score;
                terminalCandidates.push_back(p);
            }
        }
    }

    // Populate next beam: top paths by beam_score
    if (poolSize > 0) {
        std::sort(candidatesPool, candidatesPool + poolSize, [](const TempPath& a, const TempPath& b) {
            if (a.beam_score != b.beam_score) return a.beam_score > b.beam_score;
            return a.max_freq > b.max_freq;
        });

        uint8_t beamCount = static_cast<uint8_t>(std::min(poolSize, MAX_BEAM_SIZE));
        nextState.beam_count = beamCount;
        for (uint8_t i = 0; i < beamCount; ++i) {
            nextState.beam[i].edge_index = candidatesPool[i].edge_index;
            nextState.beam[i].spatial_score_sum = candidatesPool[i].spatial_sum;
            nextState.beam[i].score = candidatesPool[i].beam_score;
            nextState.beam[i].word_len = candidatesPool[i].word_len;
            std::memcpy(nextState.beam[i].word, candidatesPool[i].word, candidatesPool[i].word_len + 1);
        }
    } else if (!terminalCandidates.empty()) {
        uint8_t beamCount = static_cast<uint8_t>(std::min(terminalCandidates.size(), MAX_BEAM_SIZE));
        nextState.beam_count = beamCount;
        for (uint8_t i = 0; i < beamCount; ++i) {
            nextState.beam[i].edge_index = UINT32_MAX;
            nextState.beam[i].spatial_score_sum = terminalCandidates[i].spatial_sum;
            nextState.beam[i].score = terminalCandidates[i].beam_score;
            nextState.beam[i].word_len = terminalCandidates[i].word_len;
            std::memcpy(nextState.beam[i].word, terminalCandidates[i].word, terminalCandidates[i].word_len + 1);
        }
    } else {
        nextState.beam_count = 0;
    }

    std::sort(terminalCandidates.begin(), terminalCandidates.end(), [](const TempPath& a, const TempPath& b) {
        if (a.cand_score != b.cand_score) return a.cand_score > b.cand_score;
        return a.freq > b.freq;
    });

    // Populate candidate list (suppressing any deleted / blacklisted words)
    uint8_t candCount = 0;
    for (const auto& tc : terminalCandidates) {
        if (candCount >= MAX_CANDIDATES) break;
        if (dynamicStore && dynamicStore->isDeleted(tc.word)) continue;

        CandidateWord& cw = nextState.candidates[candCount++];
        cw.length = tc.word_len;
        cw.score = tc.cand_score;
        cw.frequency = tc.freq;
        std::memcpy(cw.word, tc.word, tc.word_len + 1);
    }

    // If still have slots, add non-terminal prefixes (unless deleted/blacklisted)
    for (size_t i = 0; i < poolSize && candCount < MAX_CANDIDATES; ++i) {
        if (!candidatesPool[i].is_terminal) {
            if (dynamicStore && dynamicStore->isDeleted(candidatesPool[i].word)) continue;
            bool exists = false;
            for (uint8_t c = 0; c < candCount; ++c) {
                if (std::strcmp(nextState.candidates[c].word, candidatesPool[i].word) == 0) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                CandidateWord& cw = nextState.candidates[candCount++];
                cw.length = candidatesPool[i].word_len;
                cw.score = candidatesPool[i].beam_score;
                cw.frequency = candidatesPool[i].freq;
                std::memcpy(cw.word, candidatesPool[i].word, candidatesPool[i].word_len + 1);
            }
        }
    }
    nextState.candidate_count = candCount;
    currentDepth++;
    return (candCount > 0 || poolSize > 0);
}

bool DawgEngine::popStroke() {
    if (currentDepth > 0) {
        currentDepth--;
        return true;
    }
    return false;
}

uint8_t DawgEngine::getCandidateCount() const {
    if (currentDepth == 0) return 0;
    return history[currentDepth - 1].candidate_count;
}

const CandidateWord* DawgEngine::getCandidates() const {
    if (currentDepth == 0) return nullptr;
    return history[currentDepth - 1].candidates;
}

int DawgEngine::serializeCandidates(uint8_t* outBuffer, int maxBytes) const {
    if (!outBuffer || maxBytes < 1) return 0;
    if (currentDepth == 0) {
        outBuffer[0] = 0;
        return 1;
    }

    const StrokeState& cur = history[currentDepth - 1];
    uint8_t count = cur.candidate_count;
    int offset = 0;

    outBuffer[offset++] = count;
    for (uint8_t i = 0; i < count; ++i) {
        const CandidateWord& cw = cur.candidates[i];
        if (offset + 1 + cw.length > maxBytes) break;
        outBuffer[offset++] = cw.length;
        std::memcpy(outBuffer + offset, cw.word, cw.length);
        offset += cw.length;
    }
    return offset;
}
