#include "dawg_engine.hpp"
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>
#include <ctime>

namespace {

constexpr float WEIGHT_CORPUS = 1.0f;
constexpr float WEIGHT_USER = 1.15f;
constexpr float WEIGHT_CTX = 1.0f;
constexpr float WEIGHT_SPATIAL = 0.65f;
constexpr float EDIT_PENALTY_PER_SWITCH = 1.35f;
constexpr float SLANG_LEXICON_BOOST = 0.35f;
constexpr uint8_t MAX_SPATIAL_EDITS = 1;
constexpr size_t MAX_POOL_SIZE = MAX_BEAM_SIZE * 4;
constexpr size_t MAX_TERMINAL_POOL = MAX_BEAM_SIZE * 4;

inline uint32_t approxRawFreq(uint16_t quantizedLogFreq) {
    double lf = static_cast<double>(quantizedLogFreq) / static_cast<double>(LOG_FREQ_SCALE);
    double approx = std::pow(10.0, lf) - 1.0;
    if (approx < 0.0) return 0;
    if (approx > 4294967295.0) return UINT32_MAX;
    return static_cast<uint32_t>(std::llround(approx));
}

inline float computeScoreFromLogFreq(
    float corpusLogFreq,
    uint32_t hitCount,
    float decayFactor,
    bool slangBoostEnabled,
    bool isSlangWord,
    float contextBoost,
    float spatialScoreSum,
    uint8_t editCount
) {
    float score = WEIGHT_CORPUS * corpusLogFreq;
    if (isSlangWord && slangBoostEnabled) {
        score += SLANG_LEXICON_BOOST;
    }
    if (hitCount > 0) {
        float slangScale = slangBoostEnabled ? 1.25f : 1.0f;
        score += WEIGHT_USER * slangScale * std::log10(1.0f + static_cast<float>(hitCount)) * decayFactor;
    }
    score += WEIGHT_CTX * contextBoost;
    score += WEIGHT_SPATIAL * spatialScoreSum;
    score -= EDIT_PENALTY_PER_SWITCH * static_cast<float>(editCount);
    return score;
}

bool equalsCanonical(const char* a, const char* b) {
    size_t i = 0;
    size_t j = 0;
    while (true) {
        while (a[i] == '\'' || a[i] == '-') i++;
        while (b[j] == '\'' || b[j] == '-') j++;
        char ca = static_cast<char>(std::tolower(static_cast<unsigned char>(a[i])));
        char cb = static_cast<char>(std::tolower(static_cast<unsigned char>(b[j])));
        if (ca != cb) return false;
        if (ca == '\0') return true;
        i++;
        j++;
    }
}

bool containsWord(const CandidateWord* cands, uint8_t count, const char* word) {
    for (uint8_t i = 0; i < count; ++i) {
        if (std::strcmp(cands[i].word, word) == 0) {
            return true;
        }
    }
    return false;
}

int serializeCandidateArray(const CandidateWord* cands, uint8_t count, uint8_t* outBuffer, int maxBytes) {
    if (!outBuffer || maxBytes < 1) return 0;
    int offset = 0;
    outBuffer[offset++] = count;
    for (uint8_t i = 0; i < count; ++i) {
        const CandidateWord& cw = cands[i];
        if (offset + 1 + cw.length > maxBytes) break;
        outBuffer[offset++] = cw.length;
        std::memcpy(outBuffer + offset, cw.word, cw.length);
        offset += cw.length;
    }
    return offset;
}

uint8_t mergeCandidateLists(
    const CandidateWord* primaryCands, uint8_t primaryCount,
    const CandidateWord* secondaryCands, uint8_t secondaryCount,
    float secondaryPenalty,
    CandidateWord* outMerged, uint8_t maxOut
) {
    CandidateWord pool[MAX_CANDIDATES * 2];
    uint8_t poolCount = 0;

    auto addWithScore = [&](const CandidateWord& src, float adjustedScore) {
        for (uint8_t i = 0; i < poolCount; ++i) {
            if (std::strcmp(pool[i].word, src.word) == 0) {
                if (adjustedScore > pool[i].score) {
                    pool[i].score = adjustedScore;
                    pool[i].frequency = std::max(pool[i].frequency, src.frequency);
                }
                return;
            }
        }
        if (poolCount < MAX_CANDIDATES * 2) {
            pool[poolCount] = src;
            pool[poolCount].score = adjustedScore;
            poolCount++;
        }
    };

    for (uint8_t i = 0; i < primaryCount; ++i) {
        addWithScore(primaryCands[i], primaryCands[i].score);
    }
    for (uint8_t i = 0; i < secondaryCount; ++i) {
        addWithScore(secondaryCands[i], secondaryCands[i].score - secondaryPenalty);
    }

    if (poolCount > 1) {
        std::sort(pool, pool + poolCount, [](const CandidateWord& a, const CandidateWord& b) {
            if (a.score != b.score) return a.score > b.score;
            return a.frequency > b.frequency;
        });
    }

    uint8_t finalCount = std::min(poolCount, maxOut);
    for (uint8_t i = 0; i < finalCount; ++i) {
        outMerged[i] = pool[i];
    }
    return finalCount;
}

} // namespace

T9SingleEngine::T9SingleEngine() {
    reset();
}

void T9SingleEngine::refreshContextBucket() {
    if (!ngramEngine || !ngramEngine->isLoaded() || !words || !stringPool || activeContextWord[0] == '\0') {
        activeContextBucket = NgramEngine::INVALID_BUCKET;
        return;
    }
    activeContextBucket = ngramEngine->resolvePrevBucket(
        activeContextWord, words, totalWords, stringPool, totalStringBytes
    );
}

void T9SingleEngine::setNgramEngine(const NgramEngine* ngram) {
    ngramEngine = ngram;
    refreshContextBucket();
}

void T9SingleEngine::setContextWord(const char* prevWord) {
    if (!prevWord || prevWord[0] == '\0') {
        activeContextWord[0] = '\0';
        activeContextBucket = NgramEngine::INVALID_BUCKET;
        return;
    }
    std::strncpy(activeContextWord, prevWord, MAX_WORD_CHARS - 1);
    activeContextWord[MAX_WORD_CHARS - 1] = '\0';
    refreshContextBucket();
}

bool T9SingleEngine::setLexiconData(const uint8_t* data, size_t size) {
    auto clearAll = [&]() {
        header = nullptr;
        nodes = nullptr;
        edges = nullptr;
        words = nullptr;
        bucketWords = nullptr;
        stringPool = nullptr;
        totalNodes = 0;
        totalEdges = 0;
        totalWords = 0;
        totalBucketWords = 0;
        totalStringBytes = 0;
        activeContextBucket = NgramEngine::INVALID_BUCKET;
    };

    if (!data || size < sizeof(DawgHeaderV2)) {
        clearAll();
        return false;
    }

    const DawgHeaderV2* hdr = reinterpret_cast<const DawgHeaderV2*>(data);
    if (std::memcmp(hdr->magic, "OPENT9D2", 8) != 0 || hdr->version != 2) {
        clearAll();
        return false;
    }

    size_t nodesBytes = static_cast<size_t>(hdr->node_count) * sizeof(DigitNodeV2);
    size_t edgesBytes = static_cast<size_t>(hdr->edge_count) * sizeof(DigitEdgeV2);
    size_t wordsBytes = static_cast<size_t>(hdr->word_count) * sizeof(LexiconWordV2);
    size_t bucketBytes = static_cast<size_t>(hdr->bucket_count) * sizeof(uint32_t);
    size_t stringBytes = static_cast<size_t>(hdr->string_pool_bytes);

    size_t expectedSize = sizeof(DawgHeaderV2) + nodesBytes + edgesBytes + wordsBytes + bucketBytes + stringBytes;
    if (size < expectedSize || hdr->root_node_index >= hdr->node_count) {
        clearAll();
        return false;
    }

    size_t offset = sizeof(DawgHeaderV2);
    nodes = reinterpret_cast<const DigitNodeV2*>(data + offset);
    offset += nodesBytes;
    edges = reinterpret_cast<const DigitEdgeV2*>(data + offset);
    offset += edgesBytes;
    words = reinterpret_cast<const LexiconWordV2*>(data + offset);
    offset += wordsBytes;
    bucketWords = reinterpret_cast<const uint32_t*>(data + offset);
    offset += bucketBytes;
    stringPool = reinterpret_cast<const char*>(data + offset);

    header = hdr;
    totalNodes = hdr->node_count;
    totalEdges = hdr->edge_count;
    totalWords = hdr->word_count;
    totalBucketWords = hdr->bucket_count;
    totalStringBytes = hdr->string_pool_bytes;
    reset();
    refreshContextBucket();
    return true;
}

void T9SingleEngine::reset() {
    currentDepth = 0;
    std::memset(history, 0, sizeof(history));
}

bool T9SingleEngine::pushStroke(int digit, float touchX, float touchY, const SpatialScorer& scorer, const NativeConfig& config) {
    if (!header || !nodes || !edges || !words || digit < 2 || digit > 9) {
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

    DigitHypothesis hyps[SpatialScorer::MAX_HYPOTHESES];
    int hypCount = scorer.collectDigitHypotheses(
        digit, touchX, touchY, config.touch_variance_sigma, hyps, SpatialScorer::MAX_HYPOTHESES
    );
    if (hypCount <= 0) {
        return false;
    }

    uint64_t nowSec = static_cast<uint64_t>(std::time(nullptr));

    struct TempBeamNode {
        uint32_t node_index;
        float spatial_sum;
        float beam_score;
        uint8_t edit_count;
        uint16_t max_log_freq;
    };

    TempBeamNode candidatesPool[MAX_POOL_SIZE];
    size_t poolSize = 0;

    auto addTargetNode = [&](uint32_t parentNodeIdx, float prevSpatialSum, uint8_t prevEdits, int maxAllowedHyps) {
        if (parentNodeIdx >= totalNodes) return;
        uint32_t eIdx = nodes[parentNodeIdx].first_child_edge;
        while (eIdx > 0 && eIdx < totalEdges) {
            const DigitEdgeV2& e = edges[eIdx];
            for (int h = 0; h < maxAllowedHyps; ++h) {
                if (e.digit != hyps[h].digit) continue;
                uint8_t nextEdits = prevEdits + (hyps[h].is_neighbor ? 1 : 0);
                if (nextEdits > MAX_SPATIAL_EDITS) continue;
                if (e.target_node >= totalNodes) continue;

                float nextSpatial = prevSpatialSum + hyps[h].log_likelihood;
                float maxLf = static_cast<float>(e.max_log_freq) / LOG_FREQ_SCALE;
                float bScore = (WEIGHT_CORPUS * maxLf) + (WEIGHT_SPATIAL * nextSpatial)
                             - (EDIT_PENALTY_PER_SWITCH * static_cast<float>(nextEdits));

                bool updated = false;
                for (size_t i = 0; i < poolSize; ++i) {
                    if (candidatesPool[i].node_index == e.target_node) {
                        if (bScore > candidatesPool[i].beam_score) {
                            candidatesPool[i].beam_score = bScore;
                            candidatesPool[i].spatial_sum = nextSpatial;
                            candidatesPool[i].edit_count = nextEdits;
                        }
                        updated = true;
                        break;
                    }
                }
                if (!updated && poolSize < MAX_POOL_SIZE) {
                    candidatesPool[poolSize++] = {
                        e.target_node, nextSpatial, bScore, nextEdits, e.max_log_freq
                    };
                }
                break;
            }
            if (!e.has_next_sibling) break;
            eIdx++;
        }
    };

    if (currentDepth == 0) {
        addTargetNode(header->root_node_index, 0.0f, 0, hypCount);
    } else {
        const StrokeState& prevState = history[currentDepth - 1];
        for (uint8_t b = 0; b < prevState.beam_count; ++b) {
            const BeamPath& bp = prevState.beam[b];
            int allowedHyps = (bp.edit_count < MAX_SPATIAL_EDITS) ? hypCount : 1;
            addTargetNode(bp.node_index, bp.spatial_score_sum, bp.edit_count, allowedHyps);
        }
    }

    if (poolSize > 0) {
        std::sort(candidatesPool, candidatesPool + poolSize, [](const TempBeamNode& a, const TempBeamNode& b) {
            if (a.beam_score != b.beam_score) return a.beam_score > b.beam_score;
            return a.max_log_freq > b.max_log_freq;
        });

        uint8_t beamCount = static_cast<uint8_t>(std::min(poolSize, MAX_BEAM_SIZE));
        nextState.beam_count = beamCount;
        for (uint8_t i = 0; i < beamCount; ++i) {
            nextState.beam[i].node_index = candidatesPool[i].node_index;
            nextState.beam[i].spatial_score_sum = candidatesPool[i].spatial_sum;
            nextState.beam[i].score = candidatesPool[i].beam_score;
            nextState.beam[i].edit_count = candidatesPool[i].edit_count;
        }
    } else {
        nextState.beam_count = 0;
    }

    struct TempTerminal {
        char word[MAX_WORD_CHARS];
        uint8_t word_len;
        uint8_t is_terminal;
        float cand_score;
        uint32_t frequency;
    };
    TempTerminal terminalPool[MAX_TERMINAL_POOL];
    size_t terminalCount = 0;

    bool hasStaticCtx = (ngramEngine && activeContextBucket != NgramEngine::INVALID_BUCKET);
    bool hasDynamicCtx = (dynamicStore && activeContextWord[0] != '\0');

    for (uint8_t b = 0; b < nextState.beam_count; ++b) {
        const BeamPath& bp = nextState.beam[b];
        const DigitNodeV2& dn = nodes[bp.node_index];
        uint16_t takeCount = static_cast<uint16_t>(std::min<uint32_t>(dn.terminal_count, MAX_CANDIDATES * 2));
        for (uint16_t t = 0; t < takeCount; ++t) {
            uint32_t bIdx = dn.terminal_offset + t;
            if (bIdx >= totalBucketWords) break;
            uint32_t wid = bucketWords[bIdx];
            if (wid >= totalWords) continue;

            const LexiconWordV2& lw = words[wid];
            if (lw.str_offset >= totalStringBytes || lw.length >= MAX_WORD_CHARS) continue;
            const char* wordStr = stringPool + lw.str_offset;

            if (dynamicStore && dynamicStore->isDeleted(wordStr)) continue;

            float ctxBoost = 0.0f;
            if (hasStaticCtx) {
                ctxBoost += ngramEngine->getBigramBoost(activeContextBucket, wid);
            }
            if (hasDynamicCtx) {
                ctxBoost += dynamicStore->getBigramBoost(activeContextWord, wordStr, nowSec, config.decay_half_life_days);
            }

            float corpusLog = static_cast<float>(lw.log_freq) / LOG_FREQ_SCALE;
            bool isSlang = (lw.flags & LEX_FLAG_SLANG) != 0;
            float score = computeScoreFromLogFreq(
                corpusLog, 0, 1.0f, config.slang_boost_enabled != 0, isSlang,
                ctxBoost, bp.spatial_score_sum, bp.edit_count
            );

            bool updated = false;
            for (size_t i = 0; i < terminalCount; ++i) {
                if (std::strcmp(terminalPool[i].word, wordStr) == 0) {
                    if (score > terminalPool[i].cand_score) {
                        terminalPool[i].cand_score = score;
                    }
                    updated = true;
                    break;
                }
            }
            if (!updated && terminalCount < MAX_TERMINAL_POOL) {
                TempTerminal& tt = terminalPool[terminalCount++];
                std::memcpy(tt.word, wordStr, lw.length + 1);
                tt.word_len = lw.length;
                tt.is_terminal = 1;
                tt.cand_score = score;
                tt.frequency = approxRawFreq(lw.log_freq);
            }
        }
    }

    if (dynamicStore) {
        int strokeDigits[MAX_STROKE_DEPTH];
        for (int d = 0; d < currentDepth; ++d) {
            strokeDigits[d] = history[d].digit;
        }
        strokeDigits[currentDepth] = digit;
        int strokeLen = currentDepth + 1;

        DynamicStore::DynamicMatch dynamicMatches[MAX_CANDIDATES];
        int dynCount = dynamicStore->findMatchingWords(
            strokeDigits, strokeLen, nowSec, config.decay_half_life_days, dynamicMatches, MAX_CANDIDATES
        );

        float primarySpatialSum = (currentDepth > 0 && history[currentDepth - 1].beam_count > 0)
            ? (history[currentDepth - 1].beam[0].spatial_score_sum + hyps[0].log_likelihood)
            : hyps[0].log_likelihood;

        for (int d = 0; d < dynCount; ++d) {
            const auto& dm = dynamicMatches[d];
            if (dynamicStore->isDeleted(dm.word)) continue;

            bool foundInTerminal = false;
            for (size_t t = 0; t < terminalCount; ++t) {
                if (equalsCanonical(terminalPool[t].word, dm.word)) {
                    foundInTerminal = true;
                    float slangScale = config.slang_boost_enabled ? 1.25f : 1.0f;
                    terminalPool[t].cand_score += WEIGHT_USER * slangScale
                        * std::log10(1.0f + static_cast<float>(dm.hit_count)) * dm.decay_factor;
                    terminalPool[t].frequency = std::max(terminalPool[t].frequency, dm.effective_freq);
                    break;
                }
            }
            if (!foundInTerminal && terminalCount < MAX_TERMINAL_POOL) {
                float ctxBoost = hasDynamicCtx
                    ? dynamicStore->getBigramBoost(activeContextWord, dm.word, nowSec, config.decay_half_life_days)
                    : 0.0f;

                TempTerminal& tt = terminalPool[terminalCount++];
                std::memcpy(tt.word, dm.word, dm.length + 1);
                tt.word_len = dm.length;
                tt.is_terminal = dm.is_terminal ? 1 : 0;
                tt.frequency = dm.effective_freq;
                float corpusLog = std::log10(1.0f + static_cast<float>(dm.effective_freq));
                tt.cand_score = computeScoreFromLogFreq(
                    corpusLog, dm.hit_count, dm.decay_factor,
                    config.slang_boost_enabled != 0, false,
                    ctxBoost, primarySpatialSum, 0
                );
                if (!dm.is_terminal) {
                    float lenDelta = static_cast<float>(dm.length > strokeLen ? (dm.length - strokeLen) : 0);
                    tt.cand_score -= 0.25f * lenDelta;
                }
            }
        }
    }

    std::sort(terminalPool, terminalPool + terminalCount, [](const TempTerminal& a, const TempTerminal& b) {
        if (a.is_terminal != b.is_terminal) return a.is_terminal > b.is_terminal;
        if (a.cand_score != b.cand_score) return a.cand_score > b.cand_score;
        return a.frequency > b.frequency;
    });

    uint8_t candCount = 0;
    for (size_t i = 0; i < terminalCount && candCount < MAX_CANDIDATES; ++i) {
        if (containsWord(nextState.candidates, candCount, terminalPool[i].word)) continue;
        CandidateWord& cw = nextState.candidates[candCount++];
        cw.length = terminalPool[i].word_len;
        cw.score = terminalPool[i].cand_score;
        cw.frequency = terminalPool[i].frequency;
        std::memcpy(cw.word, terminalPool[i].word, terminalPool[i].word_len + 1);
    }

    if (candCount < MAX_CANDIDATES && nextState.beam_count > 0) {
        uint8_t completionStart = candCount;
        int strokeLen = currentDepth + 1;

        for (uint8_t b = 0; b < nextState.beam_count && candCount < MAX_CANDIDATES; ++b) {
            const BeamPath& bp = nextState.beam[b];
            const DigitNodeV2& dn = nodes[bp.node_index];
            float baseOffset = (WEIGHT_SPATIAL * bp.spatial_score_sum)
                             - (EDIT_PENALTY_PER_SWITCH * static_cast<float>(bp.edit_count));

            if (dn.completion_count > 0) {
                for (uint16_t c = 0; c < dn.completion_count && candCount < MAX_CANDIDATES; ++c) {
                    uint32_t bIdx = dn.completion_offset + c;
                    if (bIdx >= totalBucketWords) break;
                    uint32_t wid = bucketWords[bIdx];
                    if (wid >= totalWords) continue;

                    const LexiconWordV2& lw = words[wid];
                    if (lw.str_offset >= totalStringBytes || lw.length >= MAX_WORD_CHARS) continue;
                    const char* wordStr = stringPool + lw.str_offset;

                    if (dynamicStore && dynamicStore->isDeleted(wordStr)) continue;
                    if (containsWord(nextState.candidates, candCount, wordStr)) continue;

                    float ctxBoost = hasStaticCtx ? ngramEngine->getBigramBoost(activeContextBucket, wid) : 0.0f;
                    float corpusLog = static_cast<float>(lw.log_freq) / LOG_FREQ_SCALE;
                    bool isSlang = (lw.flags & LEX_FLAG_SLANG) != 0;
                    float slangBoost = (isSlang && config.slang_boost_enabled) ? SLANG_LEXICON_BOOST : 0.0f;
                    float lenDelta = static_cast<float>(lw.length > strokeLen ? (lw.length - strokeLen) : 0);

                    CandidateWord& cw = nextState.candidates[candCount++];
                    cw.length = lw.length;
                    cw.score = baseOffset + corpusLog + slangBoost + (WEIGHT_CTX * ctxBoost) - (0.08f * lenDelta);
                    cw.frequency = approxRawFreq(lw.log_freq);
                    std::memcpy(cw.word, wordStr, lw.length + 1);
                }
            } else {
                collectBestCompletions(bp.node_index, baseOffset, nextState.candidates, candCount, MAX_CANDIDATES);
            }
        }

        if (candCount > completionStart) {
            std::sort(nextState.candidates + completionStart, nextState.candidates + candCount,
                      [](const CandidateWord& a, const CandidateWord& b) {
                if (a.score != b.score) return a.score > b.score;
                return a.frequency > b.frequency;
            });
        }
    }

    if (candCount == 0) {
        findClosestWords(digit, touchX, touchY, scorer, config, nextState.candidates, candCount, MAX_CANDIDATES);
    }

    nextState.candidate_count = candCount;
    currentDepth++;
    return (candCount > 0 || poolSize > 0);
}

bool T9SingleEngine::popStroke() {
    if (currentDepth > 0) {
        currentDepth--;
        return true;
    }
    return false;
}

uint8_t T9SingleEngine::getCandidateCount() const {
    if (currentDepth == 0) return 0;
    return history[currentDepth - 1].candidate_count;
}

const CandidateWord* T9SingleEngine::getCandidates() const {
    if (currentDepth == 0) return nullptr;
    return history[currentDepth - 1].candidates;
}

int T9SingleEngine::getNextWordCandidates(const char* prevWord, const NativeConfig& config,
                                            CandidateWord* outCands, int maxCands) const {
    if (!prevWord || prevWord[0] == '\0' || !outCands || maxCands <= 0) return 0;

    int limit = std::min<int>(maxCands, static_cast<int>(MAX_CANDIDATES));
    uint8_t candCount = 0;

    if (ngramEngine && ngramEngine->isLoaded() && words && stringPool) {
        uint32_t bucketIdx = ngramEngine->resolvePrevBucket(
            prevWord, words, totalWords, stringPool, totalStringBytes
        );
        if (bucketIdx != NgramEngine::INVALID_BUCKET) {
            uint32_t topIds[MAX_CANDIDATES];
            float topBoosts[MAX_CANDIDATES];
            int k = ngramEngine->getTopNextWordIds(bucketIdx, topIds, topBoosts, limit);
            for (int i = 0; i < k && candCount < limit; ++i) {
                uint32_t wid = topIds[i];
                if (wid >= totalWords) continue;
                const LexiconWordV2& lw = words[wid];
                if (lw.str_offset >= totalStringBytes || lw.length >= MAX_WORD_CHARS) continue;
                const char* wordStr = stringPool + lw.str_offset;
                if (dynamicStore && dynamicStore->isDeleted(wordStr)) continue;
                if (containsWord(outCands, candCount, wordStr)) continue;

                float corpusLog = static_cast<float>(lw.log_freq) / LOG_FREQ_SCALE;
                bool isSlang = (lw.flags & LEX_FLAG_SLANG) != 0;
                float slangBoost = (isSlang && config.slang_boost_enabled) ? 0.08f : 0.0f;

                CandidateWord& cw = outCands[candCount++];
                cw.length = lw.length;
                cw.score = topBoosts[i] + (0.02f * corpusLog) + slangBoost;
                cw.frequency = approxRawFreq(lw.log_freq);
                std::memcpy(cw.word, wordStr, lw.length + 1);
            }
        }
    }

    if (dynamicStore) {
        DynamicStore::DynamicBigramMatch dynNext[MAX_CANDIDATES];
        uint64_t nowSec = static_cast<uint64_t>(std::time(nullptr));
        int dynCount = dynamicStore->findNextWords(
            prevWord, nowSec, config.decay_half_life_days, dynNext, limit
        );

        for (int i = 0; i < dynCount; ++i) {
            const auto& dm = dynNext[i];
            if (dynamicStore->isDeleted(dm.word)) continue;

            bool merged = false;
            for (uint8_t c = 0; c < candCount; ++c) {
                if (equalsCanonical(outCands[c].word, dm.word)) {
                    outCands[c].score += dm.score;
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                if (candCount < limit) {
                    CandidateWord& cw = outCands[candCount++];
                    cw.length = dm.length;
                    cw.score = dm.score + 0.4f;
                    cw.frequency = dm.hit_count;
                    std::memcpy(cw.word, dm.word, dm.length + 1);
                } else {
                    uint8_t minIdx = 0;
                    for (uint8_t c = 1; c < candCount; ++c) {
                        if (outCands[c].score < outCands[minIdx].score) minIdx = c;
                    }
                    float candScore = dm.score + 0.4f;
                    if (candScore > outCands[minIdx].score) {
                        outCands[minIdx].length = dm.length;
                        outCands[minIdx].score = candScore;
                        outCands[minIdx].frequency = dm.hit_count;
                        std::memcpy(outCands[minIdx].word, dm.word, dm.length + 1);
                    }
                }
            }
        }
    }

    if (candCount > 1) {
        std::sort(outCands, outCands + candCount, [](const CandidateWord& a, const CandidateWord& b) {
            if (a.score != b.score) return a.score > b.score;
            return a.frequency > b.frequency;
        });
    }
    return static_cast<int>(candCount);
}

void T9SingleEngine::collectBestCompletions(
    uint32_t startNodeIdx, float baseScoreOffset,
    CandidateWord* outCands, uint8_t& candCount, uint8_t maxCands
) const {
    if (startNodeIdx >= totalNodes || candCount >= maxCands) return;

    struct QueueEntry {
        uint32_t node_index;
        uint16_t priority_log_freq;
        uint8_t extra_depth;
    };

    constexpr size_t MAX_QUEUE = 64;
    QueueEntry queue[MAX_QUEUE];
    size_t qSize = 0;

    auto pushChildren = [&](uint32_t nodeIdx, uint8_t curExtraDepth) {
        if (curExtraDepth >= 10) return;
        uint32_t eIdx = nodes[nodeIdx].first_child_edge;
        while (eIdx > 0 && eIdx < totalEdges) {
            const DigitEdgeV2& e = edges[eIdx];
            if (e.target_node < totalNodes && qSize < MAX_QUEUE) {
                queue[qSize++] = { e.target_node, e.max_log_freq, static_cast<uint8_t>(curExtraDepth + 1) };
            }
            if (!e.has_next_sibling) break;
            eIdx++;
        }
    };

    pushChildren(startNodeIdx, 0);

    int expandBudget = 48;
    while (qSize > 0 && candCount < maxCands && expandBudget-- > 0) {
        size_t bestIdx = 0;
        for (size_t i = 1; i < qSize; ++i) {
            if (queue[i].priority_log_freq > queue[bestIdx].priority_log_freq) {
                bestIdx = i;
            }
        }
        QueueEntry cur = queue[bestIdx];
        queue[bestIdx] = queue[--qSize];

        const DigitNodeV2& dn = nodes[cur.node_index];
        for (uint16_t t = 0; t < dn.terminal_count && candCount < maxCands; ++t) {
            uint32_t bIdx = dn.terminal_offset + t;
            if (bIdx >= totalBucketWords) break;
            uint32_t wid = bucketWords[bIdx];
            if (wid >= totalWords) continue;

            const LexiconWordV2& lw = words[wid];
            if (lw.str_offset >= totalStringBytes || lw.length >= MAX_WORD_CHARS) continue;
            const char* wordStr = stringPool + lw.str_offset;

            if (dynamicStore && dynamicStore->isDeleted(wordStr)) continue;
            if (containsWord(outCands, candCount, wordStr)) continue;

            CandidateWord& cw = outCands[candCount++];
            cw.length = lw.length;
            float corpusLog = static_cast<float>(lw.log_freq) / LOG_FREQ_SCALE;
            cw.score = baseScoreOffset + corpusLog - (0.08f * static_cast<float>(cur.extra_depth));
            cw.frequency = approxRawFreq(lw.log_freq);
            std::memcpy(cw.word, wordStr, lw.length + 1);
        }

        pushChildren(cur.node_index, cur.extra_depth);
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

void T9SingleEngine::findClosestWords(
    int digit, float touchX, float touchY, const SpatialScorer& scorer,
    const NativeConfig& config, CandidateWord* outCands, uint8_t& candCount,
    uint8_t maxCands
) {
    if (candCount >= maxCands || !header || !nodes || !edges) return;

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
                if (!containsWord(outCands, candCount, prevCw.word)) {
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

    if (digit >= 2 && digit <= 9 && candCount < maxCands) {
        const int* adjList = ADJACENT_T9_KEYS[digit];
        uint32_t parentNode = (currentDepth == 0) ? header->root_node_index : UINT32_MAX;

        auto probeNodeForDigit = [&](uint32_t pNode, int targetDigit, float basePenalty) {
            if (pNode >= totalNodes) return;
            uint32_t eIdx = nodes[pNode].first_child_edge;
            while (eIdx > 0 && eIdx < totalEdges && candCount < maxCands) {
                const DigitEdgeV2& e = edges[eIdx];
                if (e.digit == targetDigit && e.target_node < totalNodes) {
                    const DigitNodeV2& tn = nodes[e.target_node];
                    for (uint16_t t = 0; t < tn.terminal_count && candCount < maxCands; ++t) {
                        uint32_t bIdx = tn.terminal_offset + t;
                        if (bIdx >= totalBucketWords) break;
                        uint32_t wid = bucketWords[bIdx];
                        if (wid >= totalWords) continue;

                        const LexiconWordV2& lw = words[wid];
                        if (lw.str_offset >= totalStringBytes || lw.length >= MAX_WORD_CHARS) continue;
                        const char* wordStr = stringPool + lw.str_offset;
                        if (dynamicStore && dynamicStore->isDeleted(wordStr)) continue;
                        if (containsWord(outCands, candCount, wordStr)) continue;

                        CandidateWord& cw = outCands[candCount++];
                        cw.length = lw.length;
                        cw.frequency = approxRawFreq(lw.log_freq);
                        float spatialAdj = scorer.calculateLogLikelihood(targetDigit, touchX, touchY, config.touch_variance_sigma);
                        cw.score = (WEIGHT_SPATIAL * spatialAdj) + (static_cast<float>(lw.log_freq) / LOG_FREQ_SCALE) - basePenalty;
                        std::memcpy(cw.word, wordStr, lw.length + 1);
                    }
                    collectBestCompletions(e.target_node, -basePenalty, outCands, candCount, maxCands);
                }
                if (!e.has_next_sibling) break;
                eIdx++;
            }
        };

        for (int a = 0; adjList[a] != -1 && candCount < maxCands; ++a) {
            int adj = adjList[a];
            if (adj < 2 || adj > 9) continue;
            if (currentDepth == 0) {
                probeNodeForDigit(parentNode, adj, 1.5f);
            } else {
                const StrokeState& prevState = history[currentDepth - 1];
                for (uint8_t b = 0; b < prevState.beam_count && candCount < maxCands; ++b) {
                    probeNodeForDigit(prevState.beam[b].node_index, adj, 1.5f);
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

// ============================================================================
// DawgEngine (Bilingual Coordinator)
// ============================================================================

DawgEngine::DawgEngine() {
    reset();
}

bool DawgEngine::setLexiconData(const uint8_t* data, size_t size) {
    std::memset(mergedHistory, 0, sizeof(mergedHistory));
    return primary.setLexiconData(data, size);
}

bool DawgEngine::setSecondaryLexiconData(const uint8_t* data, size_t size) {
    std::memset(mergedHistory, 0, sizeof(mergedHistory));
    return secondary.setLexiconData(data, size);
}

void DawgEngine::reset() {
    primary.reset();
    secondary.reset();
    std::memset(mergedHistory, 0, sizeof(mergedHistory));
}

void DawgEngine::setDynamicStore(const DynamicStore* store) {
    primary.setDynamicStore(store);
    secondary.setDynamicStore(store);
}

void DawgEngine::setNgramEngine(const NgramEngine* ngram) {
    primary.setNgramEngine(ngram);
}

void DawgEngine::setSecondaryNgramEngine(const NgramEngine* ngram) {
    secondary.setNgramEngine(ngram);
}

void DawgEngine::setContextWord(const char* prevWord) {
    primary.setContextWord(prevWord);
    secondary.setContextWord(prevWord);
}

bool DawgEngine::pushStroke(int digit, float touchX, float touchY, const SpatialScorer& scorer, const NativeConfig& config) {
    int depthIdx = primary.getDepth();
    if (depthIdx >= static_cast<int>(MAX_STROKE_DEPTH) - 1) return false;

    bool okPrimary = primary.pushStroke(digit, touchX, touchY, scorer, config);
    bool okSecondary = false;

    if (config.bilingual_enabled != 0 && secondary.isLoaded() && secondary.getDepth() == depthIdx) {
        okSecondary = secondary.pushStroke(digit, touchX, touchY, scorer, config);
        MergedStrokeState& ms = mergedHistory[depthIdx];
        ms.active = true;
        ms.count = mergeCandidateLists(
            primary.getCandidates(), primary.getCandidateCount(),
            secondary.getCandidates(), secondary.getCandidateCount(),
            SECONDARY_LANG_PENALTY,
            ms.candidates, static_cast<uint8_t>(MAX_CANDIDATES)
        );
    } else {
        mergedHistory[depthIdx].active = false;
        mergedHistory[depthIdx].count = 0;
    }

    return okPrimary || okSecondary;
}

bool DawgEngine::popStroke() {
    int pDepth = primary.getDepth();
    if (pDepth <= 0) return false;

    if (secondary.getDepth() == pDepth) {
        secondary.popStroke();
    }
    mergedHistory[pDepth - 1].active = false;
    mergedHistory[pDepth - 1].count = 0;
    return primary.popStroke();
}

uint8_t DawgEngine::getCandidateCount() const {
    int depth = primary.getDepth();
    if (depth <= 0) return 0;
    const MergedStrokeState& ms = mergedHistory[depth - 1];
    return ms.active ? ms.count : primary.getCandidateCount();
}

const CandidateWord* DawgEngine::getCandidates() const {
    int depth = primary.getDepth();
    if (depth <= 0) return nullptr;
    const MergedStrokeState& ms = mergedHistory[depth - 1];
    return ms.active ? ms.candidates : primary.getCandidates();
}

int DawgEngine::serializeCandidates(uint8_t* outBuffer, int maxBytes) const {
    if (!outBuffer || maxBytes < 1) return 0;
    int depth = primary.getDepth();
    if (depth <= 0) {
        outBuffer[0] = 0;
        return 1;
    }
    return serializeCandidateArray(getCandidates(), getCandidateCount(), outBuffer, maxBytes);
}

int DawgEngine::getNextWordCandidates(const char* prevWord, const NativeConfig& config,
                                      CandidateWord* outCands, int maxCands) const {
    if (!prevWord || prevWord[0] == '\0' || !outCands || maxCands <= 0) return 0;

    if (config.bilingual_enabled == 0 || !secondary.isLoaded()) {
        return primary.getNextWordCandidates(prevWord, config, outCands, maxCands);
    }

    CandidateWord priCands[MAX_CANDIDATES];
    CandidateWord secCands[MAX_CANDIDATES];
    int priCount = primary.getNextWordCandidates(prevWord, config, priCands, static_cast<int>(MAX_CANDIDATES));
    int secCount = secondary.getNextWordCandidates(prevWord, config, secCands, static_cast<int>(MAX_CANDIDATES));

    uint8_t limit = static_cast<uint8_t>(std::min<int>(maxCands, static_cast<int>(MAX_CANDIDATES)));
    return static_cast<int>(mergeCandidateLists(
        priCands, static_cast<uint8_t>(priCount),
        secCands, static_cast<uint8_t>(secCount),
        SECONDARY_LANG_PENALTY,
        outCands, limit
    ));
}

int DawgEngine::serializeNextWordCandidates(const char* prevWord, const NativeConfig& config,
                                            uint8_t* outBuffer, int maxBytes) const {
    if (!outBuffer || maxBytes < 1) return 0;
    CandidateWord cands[MAX_CANDIDATES];
    int count = getNextWordCandidates(prevWord, config, cands, static_cast<int>(MAX_CANDIDATES));
    return serializeCandidateArray(cands, static_cast<uint8_t>(count), outBuffer, maxBytes);
}
