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
        uint32_t hit_count;
        uint64_t last_used_timestamp;
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
        p.hit_count = 0;
        p.last_used_timestamp = 0;
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
            float baseBoost = config.slang_boost_enabled ? 2.5f : 1.2f;
            float usageBoost = std::min(static_cast<float>(dm.hit_count) * 0.5f, 10.0f);
            float totalBoost = (baseBoost + usageBoost) * dm.decay_factor;

            bool foundInTerminal = false;
            for (auto& tc : terminalCandidates) {
                if (std::strcmp(tc.word, dm.word) == 0) {
                    foundInTerminal = true;
                    tc.freq = std::max(tc.freq, dm.effective_freq);
                    tc.cand_score += totalBoost;
                    tc.hit_count = dm.hit_count;
                    tc.last_used_timestamp = dm.last_used_timestamp;
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
                p.hit_count = dm.hit_count;
                p.last_used_timestamp = dm.last_used_timestamp;
                float prevSpatial = (currentDepth > 0 && history[currentDepth - 1].beam_count > 0)
                                    ? history[currentDepth - 1].beam[0].spatial_score_sum : 0.0f;
                p.spatial_sum = prevSpatial + spatialScore;
                p.cand_score = p.spatial_sum + std::log10(1.0f + static_cast<float>(dm.effective_freq)) + (dm.is_terminal ? totalBoost : 0.0f);
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
        // Priority 1: Exact terminal length match over forward prefix completion
        if (a.is_terminal != b.is_terminal) return a.is_terminal > b.is_terminal;

        // Priority 2: Most recently used/picked candidate (last picked is #1!)
        if (a.last_used_timestamp != b.last_used_timestamp) {
            return a.last_used_timestamp > b.last_used_timestamp;
        }

        // Priority 3: Candidate hit count
        if (a.hit_count != b.hit_count) return a.hit_count > b.hit_count;

        // Priority 4: Candidate score and corpus frequency
        if (a.cand_score != b.cand_score) return a.cand_score > b.cand_score;
        return a.freq > b.freq;
    });

    // Populate exact terminal candidates (matching current stroke depth)
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

    // Forward prefix completion: extend current beam paths to complete longer words
    if (candCount < MAX_CANDIDATES && nextState.beam_count > 0) {
        uint8_t completionStart = candCount;
        int visitBudget = 96;
        for (uint8_t b = 0; b < nextState.beam_count && candCount < MAX_CANDIDATES && visitBudget > 0; ++b) {
            const BeamPath& bp = nextState.beam[b];
            if (bp.edge_index < totalEdges) {
                uint32_t targetFirst = edges[bp.edge_index].target_first_edge;
                if (targetFirst != 0 && targetFirst < totalEdges) {
                    collectCompletions(targetFirst, bp.word, bp.word_len, bp.spatial_score_sum,
                                       nextState.candidates, candCount, MAX_CANDIDATES, 8, visitBudget);
                }
            }
        }
        // Sort forward completions among themselves
        if (candCount > completionStart) {
            std::sort(nextState.candidates + completionStart, nextState.candidates + candCount,
                      [](const CandidateWord& a, const CandidateWord& b) {
                if (a.score != b.score) return a.score > b.score;
                return a.frequency > b.frequency;
            });
        }
    }

    // If 0 candidates found (typo, overtype, or out-of-lexicon), search closest valid words
    if (candCount == 0) {
        findClosestWords(digit, touchX, touchY, scorer, config, nextState.candidates, candCount, MAX_CANDIDATES);
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

void DawgEngine::collectCompletions(
    uint32_t edgeIdx, const char* prefix, uint8_t prefixLen,
    float baseSpatialScore, CandidateWord* outCands, uint8_t& candCount,
    uint8_t maxCands, int maxDepthRemaining, int& visitBudget
) const {
    if (edgeIdx == 0 || edgeIdx >= totalEdges || candCount >= maxCands || maxDepthRemaining <= 0 || visitBudget <= 0) {
        return;
    }

    uint32_t eIdx = edgeIdx;
    struct SiblingEdge {
        uint32_t index;
        uint32_t max_freq;
    };
    SiblingEdge siblings[16];
    size_t sibCount = 0;

    while (eIdx < totalEdges && sibCount < 16) {
        siblings[sibCount++] = { eIdx, edges[eIdx].max_frequency };
        if (!edges[eIdx].has_next_sibling) break;
        eIdx++;
    }

    std::sort(siblings, siblings + sibCount, [](const SiblingEdge& a, const SiblingEdge& b) {
        return a.max_freq > b.max_freq;
    });

    for (size_t s = 0; s < sibCount && candCount < maxCands && visitBudget > 0; ++s) {
        visitBudget--;
        const DawgEdge& e = edges[siblings[s].index];
        if (prefixLen + 1 >= MAX_WORD_CHARS) continue;

        char newWord[MAX_WORD_CHARS];
        std::memcpy(newWord, prefix, prefixLen);
        newWord[prefixLen] = e.letter;
        newWord[prefixLen + 1] = '\0';
        uint8_t newLen = prefixLen + 1;

        if (e.is_terminal) {
            if (!dynamicStore || !dynamicStore->isDeleted(newWord)) {
                bool exists = false;
                for (uint8_t c = 0; c < candCount; ++c) {
                    if (std::strcmp(outCands[c].word, newWord) == 0) {
                        exists = true;
                        break;
                    }
                }
                if (!exists && candCount < maxCands) {
                    CandidateWord& cw = outCands[candCount++];
                    cw.length = newLen;
                    float lenDelta = static_cast<float>(newLen > currentDepth ? (newLen - currentDepth) : 0);
                    cw.score = baseSpatialScore + std::log10(1.0f + static_cast<float>(e.frequency)) - (0.25f * lenDelta);
                    cw.frequency = e.frequency;
                    std::memcpy(cw.word, newWord, newLen + 1);
                }
            }
        }

        if (e.target_first_edge != 0 && candCount < maxCands && maxDepthRemaining > 1 && visitBudget > 0) {
            collectCompletions(e.target_first_edge, newWord, newLen, baseSpatialScore,
                               outCands, candCount, maxCands, maxDepthRemaining - 1, visitBudget);
        }
    }
}

static const int ADJACENT_T9_KEYS[10][6] = {
    {-1},                      // 0
    {-1},                      // 1
    {1, 3, 4, 5, -1},          // 2
    {2, 5, 6, -1},             // 3
    {1, 2, 5, 7, -1},          // 4
    {2, 4, 6, 8, -1},          // 5
    {3, 5, 9, 8, -1},          // 6
    {4, 5, 8, -1},             // 7
    {5, 7, 9, 0, -1},          // 8
    {6, 8, 5, -1}              // 9
};

void DawgEngine::findClosestWords(
    int digit, float touchX, float touchY, const SpatialScorer& scorer,
    const NativeConfig& config, CandidateWord* outCands, uint8_t& candCount,
    uint8_t maxCands
) {
    if (candCount >= maxCands) return;

    // 1. Accidental Overtype: current stroke has 0 matches, find most recent valid word
    if (currentDepth > 0) {
        int searchDepth = currentDepth - 1;
        while (searchDepth >= 0 && history[searchDepth].candidate_count == 0) {
            searchDepth--;
        }
        if (searchDepth >= 0 && history[searchDepth].candidate_count > 0) {
            const StrokeState& prevState = history[searchDepth];
            for (uint8_t c = 0; c < prevState.candidate_count && candCount < maxCands; ++c) {
                const CandidateWord& prevCw = prevState.candidates[c];
                if (dynamicStore && dynamicStore->isDeleted(prevCw.word)) continue;
                bool exists = false;
                for (uint8_t i = 0; i < candCount; ++i) {
                    if (std::strcmp(outCands[i].word, prevCw.word) == 0) { exists = true; break; }
                }
                if (!exists) {
                    CandidateWord& cw = outCands[candCount++];
                    cw.length = prevCw.length;
                    cw.frequency = prevCw.frequency;
                    int overtypeLen = currentDepth - searchDepth;
                    cw.score = prevCw.score - (0.5f * overtypeLen);
                    std::memcpy(cw.word, prevCw.word, prevCw.length + 1);
                }
            }
        }
    }

    // 2. Adjacent-key typo substitution on the current digit
    if (digit >= 2 && digit <= 9 && candCount < maxCands) {
        const int* adjList = ADJACENT_T9_KEYS[digit];

        if (currentDepth == 0) {
            for (int a = 0; adjList[a] != -1 && candCount < maxCands; ++a) {
                int adj = adjList[a];
                if (adj < 2 || adj > 9) continue;
                uint32_t eIdx = header->root_first_edge;
                while (eIdx < totalEdges && candCount < maxCands) {
                    const DawgEdge& e = edges[eIdx];
                    if (e.digit == adj) {
                        char candWord[2] = { e.letter, '\0' };
                        if (e.is_terminal && (!dynamicStore || !dynamicStore->isDeleted(candWord))) {
                            bool exists = false;
                            for (uint8_t i = 0; i < candCount; ++i) {
                                if (std::strcmp(outCands[i].word, candWord) == 0) { exists = true; break; }
                            }
                            if (!exists) {
                                CandidateWord& cw = outCands[candCount++];
                                cw.length = 1;
                                cw.frequency = e.frequency;
                                cw.score = std::log10(1.0f + static_cast<float>(e.frequency)) - 1.5f;
                                std::memcpy(cw.word, candWord, 2);
                            }
                        }
                        if (e.target_first_edge != 0 && candCount < maxCands) {
                            int budget = 32;
                            collectCompletions(e.target_first_edge, candWord, 1, -1.5f,
                                               outCands, candCount, maxCands, 5, budget);
                        }
                    }
                    if (!edges[eIdx].has_next_sibling) break;
                    eIdx++;
                }
            }
        } else {
            const StrokeState& prevState = history[currentDepth - 1];
            for (int a = 0; adjList[a] != -1 && candCount < maxCands; ++a) {
                int adj = adjList[a];
                if (adj < 2 || adj > 9) continue;

                for (uint8_t b = 0; b < prevState.beam_count && candCount < maxCands; ++b) {
                    const BeamPath& bp = prevState.beam[b];
                    uint32_t prevEdgeIdx = bp.edge_index;
                    if (prevEdgeIdx >= totalEdges) continue;

                    uint32_t targetFirst = edges[prevEdgeIdx].target_first_edge;
                    if (targetFirst == 0 || targetFirst >= totalEdges) continue;

                    uint32_t eIdx = targetFirst;
                    while (eIdx < totalEdges && candCount < maxCands) {
                        const DawgEdge& e = edges[eIdx];
                        if (e.digit == adj) {
                            if (bp.word_len + 1 < MAX_WORD_CHARS) {
                                char candWord[MAX_WORD_CHARS];
                                std::memcpy(candWord, bp.word, bp.word_len);
                                candWord[bp.word_len] = e.letter;
                                candWord[bp.word_len + 1] = '\0';
                                uint8_t candLen = bp.word_len + 1;

                                if (e.is_terminal && (!dynamicStore || !dynamicStore->isDeleted(candWord))) {
                                    bool exists = false;
                                    for (uint8_t i = 0; i < candCount; ++i) {
                                        if (std::strcmp(outCands[i].word, candWord) == 0) { exists = true; break; }
                                    }
                                    if (!exists) {
                                        CandidateWord& cw = outCands[candCount++];
                                        cw.length = candLen;
                                        cw.frequency = e.frequency;
                                        float spatialAdj = scorer.calculateLogLikelihood(adj, touchX, touchY, config.touch_variance_sigma);
                                        cw.score = bp.spatial_score_sum + spatialAdj + std::log10(1.0f + static_cast<float>(e.frequency)) - 1.5f;
                                        std::memcpy(cw.word, candWord, candLen + 1);
                                    }
                                }

                                if (e.target_first_edge != 0 && candCount < maxCands) {
                                    int budget = 24;
                                    collectCompletions(e.target_first_edge, candWord, candLen,
                                                       bp.spatial_score_sum - 1.5f, outCands, candCount, maxCands, 4, budget);
                                }
                            }
                        }
                        if (!edges[eIdx].has_next_sibling) break;
                        eIdx++;
                    }
                }
            }
        }
    }

    // 3. Typo substitution on previous stroke (stroke depth >= 2)
    if (candCount < maxCands && currentDepth >= 2) {
        int prevDigit = history[currentDepth - 1].digit;
        if (prevDigit >= 2 && prevDigit <= 9) {
            const int* prevAdjList = ADJACENT_T9_KEYS[prevDigit];
            const StrokeState& stateBeforePrev = (currentDepth >= 3) ? history[currentDepth - 2] : history[0];

            for (int pa = 0; prevAdjList[pa] != -1 && candCount < maxCands; ++pa) {
                int adjPrev = prevAdjList[pa];
                if (adjPrev < 2 || adjPrev > 9) continue;

                uint8_t beamLimit = (currentDepth >= 3) ? stateBeforePrev.beam_count : 1;
                for (uint8_t b = 0; b < beamLimit && candCount < maxCands; ++b) {
                    uint32_t firstE = 0;
                    if (currentDepth >= 3) {
                        if (b >= stateBeforePrev.beam_count) continue;
                        uint32_t prevE = stateBeforePrev.beam[b].edge_index;
                        if (prevE >= totalEdges) continue;
                        firstE = edges[prevE].target_first_edge;
                    } else {
                        if (!header) continue;
                        firstE = header->root_first_edge;
                    }
                    if (firstE == 0 || firstE >= totalEdges) continue;

                    uint32_t eIdx1 = firstE;
                    while (eIdx1 < totalEdges && candCount < maxCands) {
                        const DawgEdge& e1 = edges[eIdx1];
                        if (e1.digit == adjPrev && e1.target_first_edge != 0 && e1.target_first_edge < totalEdges) {
                            uint32_t eIdx2 = e1.target_first_edge;
                            while (eIdx2 < totalEdges && candCount < maxCands) {
                                const DawgEdge& e2 = edges[eIdx2];
                                if (e2.digit == digit) {
                                    char candWord[MAX_WORD_CHARS];
                                    size_t baseLen = 0;
                                    if (currentDepth >= 3) {
                                        baseLen = stateBeforePrev.beam[b].word_len;
                                        std::memcpy(candWord, stateBeforePrev.beam[b].word, baseLen);
                                    }
                                    candWord[baseLen] = e1.letter;
                                    candWord[baseLen + 1] = e2.letter;
                                    candWord[baseLen + 2] = '\0';
                                    uint8_t cLen = static_cast<uint8_t>(baseLen + 2);

                                    if (e2.is_terminal && (!dynamicStore || !dynamicStore->isDeleted(candWord))) {
                                        bool exists = false;
                                        for (uint8_t i = 0; i < candCount; ++i) {
                                            if (std::strcmp(outCands[i].word, candWord) == 0) { exists = true; break; }
                                        }
                                        if (!exists) {
                                            CandidateWord& cw = outCands[candCount++];
                                            cw.length = cLen;
                                            cw.frequency = e2.frequency;
                                            cw.score = std::log10(1.0f + static_cast<float>(e2.frequency)) - 2.5f;
                                            std::memcpy(cw.word, candWord, cLen + 1);
                                        }
                                    }
                                    if (e2.target_first_edge != 0 && candCount < maxCands) {
                                        int budget = 20;
                                        collectCompletions(e2.target_first_edge, candWord, cLen, -2.5f,
                                                           outCands, candCount, maxCands, 4, budget);
                                    }
                                }
                                if (!edges[eIdx2].has_next_sibling) break;
                                eIdx2++;
                            }
                        }
                        if (!edges[eIdx1].has_next_sibling) break;
                        eIdx1++;
                    }
                }
            }
        }
    }

    if (candCount > 0) {
        std::sort(outCands, outCands + candCount, [](const CandidateWord& a, const CandidateWord& b) {
            if (a.score != b.score) return a.score > b.score;
            return a.frequency > b.frequency;
        });
    }
}
