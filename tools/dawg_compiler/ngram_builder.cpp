#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <map>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>
#include "../../app/src/main/cpp/include/dawg_engine.hpp"
#include "../../app/src/main/cpp/include/ngram_engine.hpp"

namespace {

std::string trim(const std::string& s) {
    size_t start = 0;
    while (start < s.size() && std::isspace(static_cast<unsigned char>(s[start]))) start++;
    size_t end = s.size();
    while (end > start && std::isspace(static_cast<unsigned char>(s[end - 1]))) end--;
    return s.substr(start, end - start);
}

std::string toLowerString(const std::string& s) {
    std::string out = s;
    for (char& c : out) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    return out;
}

std::string stripPunct(const std::string& s) {
    std::string out;
    out.reserve(s.size());
    for (char c : s) {
        char lc = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
        if (lc == '\'' || lc == '-') continue;
        out.push_back(lc);
    }
    return out;
}

struct LexiconLookup {
    std::vector<uint8_t> buffer;
    std::unordered_map<std::string, uint32_t> exactLowerToId;
    std::unordered_map<std::string, uint32_t> canonicalToId;
    std::vector<std::string> idToWord;

    bool load(const std::string& dawgPath) {
        std::ifstream in(dawgPath, std::ios::binary);
        if (!in.is_open()) {
            std::cerr << "Failed to open lexicon: " << dawgPath << "\n";
            return false;
        }
        in.seekg(0, std::ios::end);
        size_t sz = static_cast<size_t>(in.tellg());
        in.seekg(0, std::ios::beg);
        buffer.resize(sz);
        in.read(reinterpret_cast<char*>(buffer.data()), static_cast<std::streamsize>(sz));
        if (!in || sz < sizeof(DawgHeaderV2)) return false;

        const auto* hdr = reinterpret_cast<const DawgHeaderV2*>(buffer.data());
        if (std::memcmp(hdr->magic, "OPENT9D2", 8) != 0 || hdr->version != 2) {
            std::cerr << "Expected OPENT9D2 lexicon: " << dawgPath << "\n";
            return false;
        }

        size_t offset = sizeof(DawgHeaderV2)
            + static_cast<size_t>(hdr->node_count) * sizeof(DigitNodeV2)
            + static_cast<size_t>(hdr->edge_count) * sizeof(DigitEdgeV2);
        const auto* words = reinterpret_cast<const LexiconWordV2*>(buffer.data() + offset);
        offset += static_cast<size_t>(hdr->word_count) * sizeof(LexiconWordV2)
                + static_cast<size_t>(hdr->bucket_count) * sizeof(uint32_t);
        const char* strPool = reinterpret_cast<const char*>(buffer.data() + offset);

        idToWord.resize(hdr->word_count);
        for (uint32_t wid = 0; wid < hdr->word_count; ++wid) {
            std::string w(strPool + words[wid].str_offset);
            idToWord[wid] = w;
            std::string low = toLowerString(w);
            if (exactLowerToId.find(low) == exactLowerToId.end()) {
                exactLowerToId[low] = wid;
            }
            std::string canon = stripPunct(w);
            if (canonicalToId.find(canon) == canonicalToId.end()) {
                canonicalToId[canon] = wid;
            }
        }
        return true;
    }

    uint32_t findWordId(const std::string& word) const {
        std::string low = toLowerString(word);
        auto it = exactLowerToId.find(low);
        if (it != exactLowerToId.end()) return it->second;
        std::string canon = stripPunct(word);
        auto it2 = canonicalToId.find(canon);
        if (it2 != canonicalToId.end()) return it2->second;
        return UINT32_MAX;
    }
};

} // namespace

int main(int argc, char** argv) {
    if (argc < 4) {
        std::cerr << "Usage: ngram_builder <lexicon_v2.dawg> <output.bin> <bigrams_1.tsv> [bigrams_2.tsv ...]\n";
        return 1;
    }

    LexiconLookup lex;
    if (!lex.load(argv[1])) {
        return 1;
    }

    std::string outputPath = argv[2];

    // prev_wid -> (next_wid -> weight)
    std::map<uint32_t, std::unordered_map<uint32_t, double>> bigrams;
    size_t skippedPairs = 0;
    size_t loadedPairs = 0;

    for (int i = 3; i < argc; ++i) {
        std::ifstream in(argv[i]);
        if (!in.is_open()) {
            std::cerr << "Warning: Could not open " << argv[i] << "\n";
            continue;
        }
        std::string line;
        while (std::getline(in, line)) {
            std::string t = trim(line);
            if (t.empty() || t[0] == '#') continue;

            std::stringstream ss(t);
            std::string prevWord, nextWord, weightStr;
            std::getline(ss, prevWord, '\t');
            std::getline(ss, nextWord, '\t');
            std::getline(ss, weightStr, '\t');

            prevWord = trim(prevWord);
            nextWord = trim(nextWord);
            double weight = 100.0;
            if (!weightStr.empty()) {
                try {
                    weight = std::stod(trim(weightStr));
                } catch (...) {
                    weight = 100.0;
                }
            }

            uint32_t prevId = lex.findWordId(prevWord);
            uint32_t nextId = lex.findWordId(nextWord);
            if (prevId == UINT32_MAX || nextId == UINT32_MAX) {
                skippedPairs++;
                continue;
            }
            auto& inner = bigrams[prevId];
            inner[nextId] = std::max(inner[nextId], weight);
            loadedPairs++;
        }
    }

    std::vector<WordHashKeyV1> hashKeys;
    std::vector<PrevWordBucketV1> buckets;
    std::vector<NgramEdgeV1> allEdges;

    hashKeys.reserve(bigrams.size());
    buckets.reserve(bigrams.size());

    constexpr size_t MAX_TOP_K = 16;

    for (const auto& kv : bigrams) {
        uint32_t prevId = kv.first;
        const auto& nextMap = kv.second;
        if (nextMap.empty()) continue;

        double totalWeight = 0.0;
        for (const auto& nk : nextMap) {
            totalWeight += nk.second;
        }

        // Add-k smoothed conditional probability P(next | prev) mapped cleanly to [0.05, 1.50] log10 units.
        // Preserves strict monotonic ordering per prevWord while preventing aggressive over-steering.
        constexpr double SMOOTH_K = 200.0;
        constexpr double VIRTUAL_VOCAB = 18.0;
        constexpr double LOG10_P_FLOOR = -2.40;
        constexpr double BOOST_SCALE = 0.625; // Maps P=1.0 cleanly to 1.50 without top saturation
        constexpr double MIN_BOOST = 0.05;
        constexpr double MAX_BOOST = 1.50;

        double denom = totalWeight + SMOOTH_K * (static_cast<double>(nextMap.size()) + VIRTUAL_VOCAB);

        struct ScoredEdge {
            uint32_t nextId;
            uint8_t qProb;
            double weight;
        };
        std::vector<ScoredEdge> list;
        list.reserve(nextMap.size());

        for (const auto& nk : nextMap) {
            double pCond = (nk.second + SMOOTH_K) / denom;
            double boost = BOOST_SCALE * (std::log10(pCond) - LOG10_P_FLOOR);
            if (boost < MIN_BOOST) boost = MIN_BOOST;
            if (boost > MAX_BOOST) boost = MAX_BOOST;
            int q = static_cast<int>(std::llround(boost * NGRAM_PROB_SCALE));
            if (q < 3) q = 3;
            if (q > 255) q = 255;
            list.push_back({nk.first, static_cast<uint8_t>(q), nk.second});
        }

        // Top-K descending by qProb (then weight, then nextId ascending)
        std::sort(list.begin(), list.end(), [](const ScoredEdge& a, const ScoredEdge& b) {
            if (a.qProb != b.qProb) return a.qProb > b.qProb;
            if (a.weight != b.weight) return a.weight > b.weight;
            return a.nextId < b.nextId;
        });

        uint32_t bucketIdx = static_cast<uint32_t>(buckets.size());
        PrevWordBucketV1 bucket{};
        bucket.top_offset = static_cast<uint32_t>(allEdges.size());
        bucket.top_count = static_cast<uint16_t>(std::min(list.size(), MAX_TOP_K));

        for (uint16_t i = 0; i < bucket.top_count; ++i) {
            allEdges.push_back(NgramEdgeV1{list[i].nextId, list[i].qProb, 0, 0});
        }

        // All entries sorted ascending by next_word_id for O(log K) contextual binary search
        std::sort(list.begin(), list.end(), [](const ScoredEdge& a, const ScoredEdge& b) {
            return a.nextId < b.nextId;
        });
        bucket.sorted_offset = static_cast<uint32_t>(allEdges.size());
        bucket.sorted_count = static_cast<uint16_t>(std::min<size_t>(list.size(), 65535));
        for (uint16_t i = 0; i < bucket.sorted_count; ++i) {
            allEdges.push_back(NgramEdgeV1{list[i].nextId, list[i].qProb, 0, 0});
        }

        buckets.push_back(bucket);

        WordHashKeyV1 hk{};
        hk.hash = fnv1aHash64Lower(lex.idToWord[prevId].c_str());
        hk.word_id = prevId;
        hk.bucket_idx = bucketIdx;
        hashKeys.push_back(hk);
    }

    std::sort(hashKeys.begin(), hashKeys.end(), [](const WordHashKeyV1& a, const WordHashKeyV1& b) {
        if (a.hash != b.hash) return a.hash < b.hash;
        return a.word_id < b.word_id;
    });

    NgramHeaderV1 hdr{};
    std::memcpy(hdr.magic, "OPENT9N1", 8);
    hdr.version = 1;
    hdr.prev_entry_count = static_cast<uint32_t>(buckets.size());
    hdr.bigram_count = static_cast<uint32_t>(allEdges.size());
    hdr.flags = 0;

    std::ofstream out(outputPath, std::ios::binary | std::ios::trunc);
    if (!out.is_open()) {
        std::cerr << "Failed to open output: " << outputPath << "\n";
        return 1;
    }
    out.write(reinterpret_cast<const char*>(&hdr), sizeof(hdr));
    out.write(reinterpret_cast<const char*>(hashKeys.data()), hashKeys.size() * sizeof(WordHashKeyV1));
    out.write(reinterpret_cast<const char*>(buckets.data()), buckets.size() * sizeof(PrevWordBucketV1));
    out.write(reinterpret_cast<const char*>(allEdges.data()), allEdges.size() * sizeof(NgramEdgeV1));
    out.close();

    size_t totalBytes = sizeof(hdr)
        + hashKeys.size() * sizeof(WordHashKeyV1)
        + buckets.size() * sizeof(PrevWordBucketV1)
        + allEdges.size() * sizeof(NgramEdgeV1);

    std::cout << "Generated OPENT9N1 " << outputPath << "\n"
              << "  Loaded pairs:  " << loadedPairs << " (skipped OOV: " << skippedPairs << ")\n"
              << "  Prev words:    " << buckets.size() << "\n"
              << "  Edge entries:  " << allEdges.size() << "\n"
              << "  Total size:    " << totalBytes << " bytes (" << (totalBytes / 1024.0) << " KB)\n";
    return 0;
}
