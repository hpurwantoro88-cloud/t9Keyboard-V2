#include <algorithm>
#include <cctype>
#include <chrono>
#include <cmath>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <random>
#include <sstream>
#include <string>
#include <vector>

#include "../app/src/main/cpp/include/dawg_engine.hpp"
#include "../app/src/main/cpp/include/lexicon_manager.hpp"
#include "../app/src/main/cpp/include/native_config.hpp"
#include "../app/src/main/cpp/include/ngram_engine.hpp"
#include "../app/src/main/cpp/include/spatial_scoring.hpp"

#ifdef OPENT9_EVAL_TUNING
extern float g_evalWeightSpatial;
extern float g_evalEditPenalty;
extern float g_evalExactRankOffset;
extern int g_evalSpatialNormMode;
extern float g_evalMinNeighborLog10Posterior;
#endif

namespace {

int charToT9(char c) {
    c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    if (c >= 'a' && c <= 'c') return 2;
    if (c >= 'd' && c <= 'f') return 3;
    if (c >= 'g' && c <= 'i') return 4;
    if (c >= 'j' && c <= 'l') return 5;
    if (c >= 'm' && c <= 'o') return 6;
    if (c >= 'p' && c <= 's') return 7;
    if (c >= 't' && c <= 'v') return 8;
    if (c >= 'w' && c <= 'z') return 9;
    return 0;
}

std::vector<int> wordToDigits(const std::string& w) {
    std::vector<int> digits;
    for (unsigned char uc : w) {
        char lc = static_cast<char>(std::tolower(uc));
        if (lc == '\'' || lc == '-') continue;
        int d = charToT9(lc);
        if (d >= 2 && d <= 9) digits.push_back(d);
    }
    return digits;
}

bool equalsCaseInsensitive(const char* a, const std::string& b) {
    size_t i = 0;
    while (a[i] != '\0' && i < b.size()) {
        if (std::tolower(static_cast<unsigned char>(a[i])) !=
            std::tolower(static_cast<unsigned char>(b[i]))) {
            return false;
        }
        ++i;
    }
    return a[i] == '\0' && i == b.size();
}

std::vector<std::vector<std::string>> loadSentences(const std::string& path) {
    std::ifstream in(path);
    std::vector<std::vector<std::string>> sentences;
    if (!in.is_open()) {
        std::cerr << "Failed to open dataset: " << path << "\n";
        return sentences;
    }
    std::string line;
    while (std::getline(in, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::istringstream iss(line);
        std::vector<std::string> tokens;
        std::string tok;
        while (iss >> tok) {
            if (!wordToDigits(tok).empty()) {
                tokens.push_back(tok);
            }
        }
        if (!tokens.empty()) {
            sentences.push_back(std::move(tokens));
        }
    }
    return sentences;
}

bool loadBinaryFile(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream f(path, std::ios::binary);
    if (!f.is_open()) return false;
    f.seekg(0, std::ios::end);
    size_t sz = static_cast<size_t>(f.tellg());
    f.seekg(0, std::ios::beg);
    out.resize(sz);
    f.read(reinterpret_cast<char*>(out.data()), static_cast<std::streamsize>(sz));
    return static_cast<bool>(f);
}

struct KeyGrid {
    static constexpr float KEY_W = 270.0f;  // 90dp @ 3x density
    static constexpr float KEY_H = 165.0f;  // 55dp @ 3x density
    float cx[10]{};
    float cy[10]{};

    KeyGrid() {
        // 3x4 T9 grid: col 0..2, row 0..3
        const int layout[4][3] = {
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9},
            {-1, 0, -1}
        };
        for (int r = 0; r < 4; ++r) {
            for (int c = 0; c < 3; ++c) {
                int d = layout[r][c];
                if (d >= 0) {
                    cx[d] = (c + 0.5f) * KEY_W;
                    cy[d] = (r + 0.5f) * KEY_H;
                }
            }
        }
    }

    void populateScorer(SpatialScorer& scorer) const {
        for (int d = 2; d <= 9; ++d) {
            scorer.updateKeyGeometry(d, cx[d], cy[d]);
        }
    }

    // Maps touch coordinates to the nearest T9 letter key 2..9 (matching active letter keypad hit-testing)
    int hitDigit2to9(float x, float y) const {
        int bestD = 2;
        float bestDistSq = 1e30f;
        for (int d = 2; d <= 9; ++d) {
            float dx = x - cx[d];
            float dy = y - cy[d];
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestD = d;
            }
        }
        return bestD;
    }

    std::vector<int> cardinalNeighbors(int d) const {
        static const int kCard[10][5] = {
            {-1}, {-1},
            {3, 5, -1},       // 2
            {2, 6, -1},       // 3
            {5, 7, -1},       // 4
            {2, 4, 6, 8, -1}, // 5
            {3, 5, 9, -1},    // 6
            {4, 8, -1},       // 7
            {5, 7, 9, -1},    // 8
            {6, 8, -1}        // 9
        };
        std::vector<int> out;
        if (d < 2 || d > 9) return out;
        for (int i = 0; kCard[d][i] != -1; ++i) {
            out.push_back(kCard[d][i]);
        }
        return out;
    }
};

struct EvalMetrics {
    int words = 0;
    int top1 = 0;
    int top3 = 0;
    int found16 = 0;
    double mrrSum = 0.0;
    long long keystrokes = 0;
    long long totalChars = 0;
    std::vector<double> latenciesUs;

    double top1Pct() const { return words > 0 ? (100.0 * top1 / words) : 0.0; }
    double top3Pct() const { return words > 0 ? (100.0 * top3 / words) : 0.0; }
    double foundPct() const { return words > 0 ? (100.0 * found16 / words) : 0.0; }
    double mrr() const { return words > 0 ? (mrrSum / words) : 0.0; }
    double kspc() const { return totalChars > 0 ? (static_cast<double>(keystrokes) / totalChars) : 0.0; }

    double percentileUs(double p) const {
        if (latenciesUs.empty()) return 0.0;
        std::vector<double> copy = latenciesUs;
        std::sort(copy.begin(), copy.end());
        size_t idx = static_cast<size_t>(std::min(copy.size() - 1, static_cast<size_t>(copy.size() * p)));
        return copy[idx];
    }
};

int findCandidateRank(const DawgEngine& engine, const std::string& target) {
    uint8_t cnt = engine.getCandidateCount();
    const CandidateWord* cands = engine.getCandidates();
    for (uint8_t i = 0; i < cnt; ++i) {
        if (equalsCaseInsensitive(cands[i].word, target)) {
            return static_cast<int>(i) + 1;
        }
    }
    return -1;
}

void recordWordRank(EvalMetrics& m, const std::string& word, int digitCount, int rank) {
    m.words++;
    int chars = static_cast<int>(word.size());
    m.totalChars += chars;
    if (rank == 1) {
        m.top1++;
        m.top3++;
        m.found16++;
        m.mrrSum += 1.0;
        m.keystrokes += digitCount;
    } else if (rank >= 2 && rank <= 3) {
        m.top3++;
        m.found16++;
        m.mrrSum += 1.0 / rank;
        m.keystrokes += digitCount + 1;
    } else if (rank >= 4 && rank <= 16) {
        m.found16++;
        m.mrrSum += 1.0 / rank;
        m.keystrokes += digitCount + 1;
    } else {
        m.keystrokes += digitCount + chars;
    }
}

struct SuiteResult {
    std::string label;
    EvalMetrics modeA;
    EvalMetrics modeB1;
    int b1CleanDigitBaseTop1 = 0;
    int b1CleanDigitFalseCorr = 0;
    int b1SingleSlipWords = 0;
    int b1SingleSlipTop1 = 0;
    int b1SingleSlipTop3 = 0;

    int b2SlipWords = 0;
    int b2SlipTop1 = 0;
    int b2SlipTop3 = 0;
    int b2NearBorderCleanBaseTop1 = 0;
    int b2NearBorderFalseCorr = 0;

    int modeCWords = 0;
    int nwpTop1 = 0;
    int nwpTop3 = 0;
    double nwpMrrSum = 0.0;
    int ctxT9Top1 = 0;
    int ctxT9Top3 = 0;
    double ctxT9MrrSum = 0.0;
    int noCtxT9Top1 = 0;
    int ctxImproved = 0;
    int ctxRegressed = 0;
};

SuiteResult evaluateSuite(
    const std::string& label,
    const std::vector<std::vector<std::string>>& sentences,
    DawgEngine& engine,
    const SpatialScorer& scorer,
    const KeyGrid& grid,
    const NativeConfig& config
) {
    SuiteResult res;
    res.label = label;
    std::mt19937 rng(424242);
    // Realistic anisotropic smartphone typing jitter around key center (~24% key width, ~26% key height)
    std::normal_distribution<float> noiseX(0.0f, KeyGrid::KEY_W * 0.24f);
    std::normal_distribution<float> noiseY(0.0f, KeyGrid::KEY_H * 0.26f);

    std::vector<bool> cleanCenterTop1;
    cleanCenterTop1.reserve(2048);

    // ---------------- Mode A: Clean Tap ----------------
    engine.setContextWord(nullptr);
    for (const auto& sent : sentences) {
        for (const auto& w : sent) {
            std::vector<int> digits = wordToDigits(w);
            engine.reset();
            for (int d : digits) {
                auto t0 = std::chrono::high_resolution_clock::now();
                engine.pushStroke(d, grid.cx[d], grid.cy[d], scorer, config);
                auto t1 = std::chrono::high_resolution_clock::now();
                res.modeA.latenciesUs.push_back(
                    std::chrono::duration<double, std::micro>(t1 - t0).count()
                );
            }
            int rank = findCandidateRank(engine, w);
            recordWordRank(res.modeA, w, static_cast<int>(digits.size()), rank);
            cleanCenterTop1.push_back(rank == 1);
        }
    }

    // ---------------- Mode B1: Realistic Jitter ----------------
    size_t wordIdx = 0;
    for (const auto& sent : sentences) {
        for (const auto& w : sent) {
            std::vector<int> digits = wordToDigits(w);
            engine.reset();
            int slipCount = 0;
            for (int d : digits) {
                float tx = grid.cx[d] + noiseX(rng);
                float ty = grid.cy[d] + noiseY(rng);
                int hitD = grid.hitDigit2to9(tx, ty);
                if (hitD != d) slipCount++;
                engine.pushStroke(hitD, tx, ty, scorer, config);
            }
            int rank = findCandidateRank(engine, w);
            recordWordRank(res.modeB1, w, static_cast<int>(digits.size()), rank);

            bool wasCenterTop1 = cleanCenterTop1[wordIdx++];
            if (slipCount == 0 && wasCenterTop1) {
                res.b1CleanDigitBaseTop1++;
                if (rank != 1) res.b1CleanDigitFalseCorr++;
            }
            if (slipCount == 1) {
                res.b1SingleSlipWords++;
                if (rank == 1) res.b1SingleSlipTop1++;
                if (rank >= 1 && rank <= 3) res.b1SingleSlipTop3++;
            }
        }
    }

    // ---------------- Mode B2: Targeted Border Slip & Near-Border FCR ----------------
    wordIdx = 0;
    for (const auto& sent : sentences) {
        for (const auto& w : sent) {
            bool wasCenterTop1 = cleanCenterTop1[wordIdx++];
            std::vector<int> digits = wordToDigits(w);
            if (digits.size() < 2) continue;

            size_t targetPos = (wordIdx * 7 + 3) % digits.size();
            int origD = digits[targetPos];
            std::vector<int> nbrs = grid.cardinalNeighbors(origD);
            if (nbrs.empty()) continue;
            int slipD = nbrs[(wordIdx * 5) % nbrs.size()];

            // Unit direction from origD center to slipD center
            float vx = grid.cx[slipD] - grid.cx[origD];
            float vy = grid.cy[slipD] - grid.cy[origD];
            float borderX = 0.5f * (grid.cx[origD] + grid.cx[slipD]);
            float borderY = 0.5f * (grid.cy[origD] + grid.cy[slipD]);
            float norm = std::sqrt(vx * vx + vy * vy);
            float ux = vx / norm;
            float uy = vy / norm;

            // B2a: 12px across border inside slipD (hitDigit = slipD)
            {
                float sx = borderX + 12.0f * ux;
                float sy = borderY + 12.0f * uy;
                engine.reset();
                for (size_t i = 0; i < digits.size(); ++i) {
                    if (i == targetPos) {
                        engine.pushStroke(slipD, sx, sy, scorer, config);
                    } else {
                        int d = digits[i];
                        engine.pushStroke(d, grid.cx[d], grid.cy[d], scorer, config);
                    }
                }
                int rank = findCandidateRank(engine, w);
                res.b2SlipWords++;
                if (rank == 1) res.b2SlipTop1++;
                if (rank >= 1 && rank <= 3) res.b2SlipTop3++;
            }

            // B2b: 12px inside origD border toward slipD (hitDigit = origD)
            if (wasCenterTop1) {
                float nx = borderX - 12.0f * ux;
                float ny = borderY - 12.0f * uy;
                engine.reset();
                for (size_t i = 0; i < digits.size(); ++i) {
                    if (i == targetPos) {
                        engine.pushStroke(origD, nx, ny, scorer, config);
                    } else {
                        int d = digits[i];
                        engine.pushStroke(d, grid.cx[d], grid.cy[d], scorer, config);
                    }
                }
                int rank = findCandidateRank(engine, w);
                res.b2NearBorderCleanBaseTop1++;
                if (rank != 1) res.b2NearBorderFalseCorr++;
            }
        }
    }

    // ---------------- Mode C: Contextual NWP & Disambiguation ----------------
    CandidateWord nwpCands[MAX_CANDIDATES];
    for (const auto& sent : sentences) {
        for (size_t wi = 1; wi < sent.size(); ++wi) {
            const std::string& prev = sent[wi - 1];
            const std::string& cur = sent[wi];
            std::vector<int> digits = wordToDigits(cur);
            res.modeCWords++;

            // Idle NWP
            int nwpCount = engine.getNextWordCandidates(prev.c_str(), config, nwpCands, MAX_CANDIDATES);
            int nwpRank = -1;
            for (int i = 0; i < nwpCount; ++i) {
                if (equalsCaseInsensitive(nwpCands[i].word, cur)) {
                    nwpRank = i + 1;
                    break;
                }
            }
            if (nwpRank == 1) res.nwpTop1++;
            if (nwpRank >= 1 && nwpRank <= 3) res.nwpTop3++;
            if (nwpRank >= 1) res.nwpMrrSum += 1.0 / nwpRank;

            // T9 without context
            engine.setContextWord(nullptr);
            engine.reset();
            for (int d : digits) engine.pushStroke(d, grid.cx[d], grid.cy[d], scorer, config);
            int noCtxRank = findCandidateRank(engine, cur);
            if (noCtxRank == 1) res.noCtxT9Top1++;

            // T9 with context
            engine.setContextWord(prev.c_str());
            engine.reset();
            for (int d : digits) engine.pushStroke(d, grid.cx[d], grid.cy[d], scorer, config);
            int ctxRank = findCandidateRank(engine, cur);
            if (ctxRank == 1) res.ctxT9Top1++;
            if (ctxRank >= 1 && ctxRank <= 3) res.ctxT9Top3++;
            if (ctxRank >= 1) res.ctxT9MrrSum += 1.0 / ctxRank;

            if (noCtxRank != 1 && ctxRank == 1) res.ctxImproved++;
            if (noCtxRank == 1 && ctxRank != 1) res.ctxRegressed++;
        }
    }
    engine.setContextWord(nullptr);

    return res;
}

void printResult(std::ostream& os, const SuiteResult& r) {
    os << std::fixed << std::setprecision(2);
    os << "\n=== " << r.label << " (Words: " << r.modeA.words << ", Context pairs: " << r.modeCWords << ") ===\n";
    os << "  [Mode A: Clean Tap]\n"
       << "    Top-1: " << r.modeA.top1Pct() << "% (" << r.modeA.top1 << "/" << r.modeA.words << ")"
       << " | Top-3: " << r.modeA.top3Pct() << "%"
       << " | Found@16: " << r.modeA.foundPct() << "%"
       << " | MRR: " << std::setprecision(4) << r.modeA.mrr()
       << " | KSPC: " << r.modeA.kspc() << std::setprecision(2) << "\n"
       << "    Latency (us): p50=" << r.modeA.percentileUs(0.50)
       << " | p95=" << r.modeA.percentileUs(0.95)
       << " | p99=" << r.modeA.percentileUs(0.99) << "\n";

    double b1Fcr = r.b1CleanDigitBaseTop1 > 0 ? (100.0 * r.b1CleanDigitFalseCorr / r.b1CleanDigitBaseTop1) : 0.0;
    double b1Rec1 = r.b1SingleSlipWords > 0 ? (100.0 * r.b1SingleSlipTop1 / r.b1SingleSlipWords) : 0.0;
    double b1Rec3 = r.b1SingleSlipWords > 0 ? (100.0 * r.b1SingleSlipTop3 / r.b1SingleSlipWords) : 0.0;
    double b2Rec1 = r.b2SlipWords > 0 ? (100.0 * r.b2SlipTop1 / r.b2SlipWords) : 0.0;
    double b2Rec3 = r.b2SlipWords > 0 ? (100.0 * r.b2SlipTop3 / r.b2SlipWords) : 0.0;
    double b2Fcr = r.b2NearBorderCleanBaseTop1 > 0 ? (100.0 * r.b2NearBorderFalseCorr / r.b2NearBorderCleanBaseTop1) : 0.0;

    os << "  [Mode B1: Realistic Anisotropic Jitter]\n"
       << "    Overall Top-1: " << r.modeB1.top1Pct() << "% | Top-3: " << r.modeB1.top3Pct() << "%"
       << " | MRR: " << std::setprecision(4) << r.modeB1.mrr() << std::setprecision(2) << "\n"
       << "    Clean-Digit False-Correction Rate: " << b1Fcr << "% (" << r.b1CleanDigitFalseCorr << "/" << r.b1CleanDigitBaseTop1 << ")\n"
       << "    Natural 1-Slip Recovery (" << r.b1SingleSlipWords << " words): Top-1=" << b1Rec1 << "% | Top-3=" << b1Rec3 << "%\n";

    os << "  [Mode B2: Targeted 12px Border Slip & Near-Border FCR]\n"
       << "    Border-Slip Recovery (" << r.b2SlipWords << " words): Top-1=" << b2Rec1 << "% | Top-3=" << b2Rec3 << "%\n"
       << "    Near-Border False-Correction Rate: " << b2Fcr << "% (" << r.b2NearBorderFalseCorr << "/" << r.b2NearBorderCleanBaseTop1 << ")\n";

    double nwp1 = r.modeCWords > 0 ? (100.0 * r.nwpTop1 / r.modeCWords) : 0.0;
    double nwp3 = r.modeCWords > 0 ? (100.0 * r.nwpTop3 / r.modeCWords) : 0.0;
    double nwpMrr = r.modeCWords > 0 ? (r.nwpMrrSum / r.modeCWords) : 0.0;
    double noCtx1 = r.modeCWords > 0 ? (100.0 * r.noCtxT9Top1 / r.modeCWords) : 0.0;
    double ctx1 = r.modeCWords > 0 ? (100.0 * r.ctxT9Top1 / r.modeCWords) : 0.0;
    double ctx3 = r.modeCWords > 0 ? (100.0 * r.ctxT9Top3 / r.modeCWords) : 0.0;
    double ctxMrr = r.modeCWords > 0 ? (r.ctxT9MrrSum / r.modeCWords) : 0.0;

    os << "  [Mode C: Contextual NWP & Disambiguation]\n"
       << "    Idle NWP: Top-1=" << nwp1 << "% | Top-3=" << nwp3 << "% | MRR=" << std::setprecision(4) << nwpMrr << std::setprecision(2) << "\n"
       << "    T9 No-Ctx Top-1=" << noCtx1 << "% -> Ctx Top-1=" << ctx1 << "% | Ctx Top-3=" << ctx3 << "%"
       << " | MRR=" << std::setprecision(4) << ctxMrr << std::setprecision(2) << "\n"
       << "    Context Flips: improved=" << r.ctxImproved << " | regressed(over-steer)=" << r.ctxRegressed << "\n";
}

} // namespace

int main(int argc, char** argv) {
    std::string outPath;
    bool runSweep = false;
    for (int i = 1; i < argc; ++i) {
        if (std::strcmp(argv[i], "--out") == 0 && i + 1 < argc) {
            outPath = argv[++i];
        } else if (std::strcmp(argv[i], "--sweep") == 0) {
            runSweep = true;
        }
    }

    std::vector<uint8_t> enLex, idLex, enBg, idBg;
    if (!loadBinaryFile("app/src/main/assets/dictionaries/en_lexicon.dawg", enLex) ||
        !loadBinaryFile("app/src/main/assets/dictionaries/id_lexicon.dawg", idLex) ||
        !loadBinaryFile("app/src/main/assets/dictionaries/en_bigrams.bin", enBg) ||
        !loadBinaryFile("app/src/main/assets/dictionaries/id_bigrams.bin", idBg)) {
        std::cerr << "Failed to load dictionary/bigram assets.\n";
        return 1;
    }

    auto idSents = loadSentences("native_tests/data/eval_id_sentences.txt");
    auto enSents = loadSentences("native_tests/data/eval_en_sentences.txt");
    auto biSents = loadSentences("native_tests/data/eval_bilingual_sentences.txt");
    if (idSents.empty() || enSents.empty() || biSents.empty()) {
        std::cerr << "Evaluation datasets missing or empty.\n";
        return 1;
    }

    KeyGrid grid;
    SpatialScorer scorer;
    grid.populateScorer(scorer);

    NativeConfig cfg;
    cfg.touch_variance_sigma = 48.0f; // 16dp @ 3x density

    NgramEngine idNg, enNg;
    idNg.setNgramData(idBg.data(), idBg.size());
    enNg.setNgramData(enBg.data(), enBg.size());

    DawgEngine engine;

#ifdef OPENT9_EVAL_TUNING
    if (runSweep) {
        std::cout << "norm,w_sp,edit_pen,exact_off,min_post | "
                  << "ID:B1_Top1,ID:B1_FCR,ID:B1_Slip1,ID:B2_Slip1,ID:B2_FCR | "
                  << "EN:B1_Top1,EN:B1_FCR,EN:B1_Slip1,EN:B2_Slip1,EN:B2_FCR\n";
        std::cout << std::fixed << std::setprecision(2);

        const int normModes[] = {0, 1};
        const float wSpatials[] = {1.00f, 1.25f};
        const float editPens[] = {0.45f, 0.55f, 0.65f};
        const float minPosts[] = {-1.55f, -1.70f};

        for (int nm : normModes) {
            for (float wsp : wSpatials) {
                for (float ep : editPens) {
                    for (float mp : minPosts) {
                        g_evalSpatialNormMode = nm;
                        g_evalWeightSpatial = wsp;
                        g_evalEditPenalty = ep;
                        g_evalExactRankOffset = 0.0f;
                        g_evalMinNeighborLog10Posterior = mp;

                        cfg.bilingual_enabled = 0;
                        engine.setLexiconData(idLex.data(), idLex.size());
                        engine.setNgramEngine(&idNg);
                        SuiteResult rId = evaluateSuite("ID", idSents, engine, scorer, grid, cfg);

                        engine.setLexiconData(enLex.data(), enLex.size());
                        engine.setNgramEngine(&enNg);
                        SuiteResult rEn = evaluateSuite("EN", enSents, engine, scorer, grid, cfg);

                        auto pct = [](int num, int den) { return den > 0 ? 100.0 * num / den : 0.0; };
                        std::cout << nm << "," << wsp << "," << ep << ",0.00," << mp << " | "
                                  << rId.modeB1.top1Pct() << "," << pct(rId.b1CleanDigitFalseCorr, rId.b1CleanDigitBaseTop1)
                                  << "," << pct(rId.b1SingleSlipTop1, rId.b1SingleSlipWords)
                                  << "," << pct(rId.b2SlipTop1, rId.b2SlipWords)
                                  << "," << pct(rId.b2NearBorderFalseCorr, rId.b2NearBorderCleanBaseTop1) << " | "
                                  << rEn.modeB1.top1Pct() << "," << pct(rEn.b1CleanDigitFalseCorr, rEn.b1CleanDigitBaseTop1)
                                  << "," << pct(rEn.b1SingleSlipTop1, rEn.b1SingleSlipWords)
                                  << "," << pct(rEn.b2SlipTop1, rEn.b2SlipWords)
                                  << "," << pct(rEn.b2NearBorderFalseCorr, rEn.b2NearBorderCleanBaseTop1) << "\n";
                    }
                }
            }
        }
        return 0;
    }
#else
    (void)runSweep;
#endif

    // 1. Indonesian
    cfg.bilingual_enabled = 0;
    engine.setLexiconData(idLex.data(), idLex.size());
    engine.setNgramEngine(&idNg);
    SuiteResult idRes = evaluateSuite("Indonesian (ID)", idSents, engine, scorer, grid, cfg);

    // 2. English
    engine.setLexiconData(enLex.data(), enLex.size());
    engine.setNgramEngine(&enNg);
    SuiteResult enRes = evaluateSuite("English (EN)", enSents, engine, scorer, grid, cfg);

    // 3. Bilingual (ID primary + EN secondary)
    cfg.bilingual_enabled = 1;
    engine.setLexiconData(idLex.data(), idLex.size());
    engine.setSecondaryLexiconData(enLex.data(), enLex.size());
    engine.setNgramEngine(&idNg);
    engine.setSecondaryNgramEngine(&enNg);
    SuiteResult biRes = evaluateSuite("Bilingual (ID+EN)", biSents, engine, scorer, grid, cfg);

    std::ostringstream report;
    report << "OpenT9 Prediction Evaluation Report\n";
    report << "===================================\n";
    printResult(report, idRes);
    printResult(report, enRes);
    printResult(report, biRes);

    std::cout << report.str();

    if (!outPath.empty()) {
        std::ofstream out(outPath);
        if (out.is_open()) {
            out << report.str();
            std::cout << "\nSaved metrics to " << outPath << "\n";
        }
    }
    return 0;
}
