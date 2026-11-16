#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <map>
#include <queue>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>
#include "../../app/src/main/cpp/include/dawg_engine.hpp"

namespace {

inline uint8_t charToT9Digit(char c) {
    if (c >= 'A' && c <= 'Z') c = static_cast<char>(c + ('a' - 'A'));
    switch (c) {
        case 'a': case 'b': case 'c': return 2;
        case 'd': case 'e': case 'f': return 3;
        case 'g': case 'h': case 'i': return 4;
        case 'j': case 'k': case 'l': return 5;
        case 'm': case 'n': case 'o': return 6;
        case 'p': case 'q': case 'r': case 's': return 7;
        case 't': case 'u': case 'v': return 8;
        case 'w': case 'x': case 'y': case 'z': return 9;
        default: return 0;
    }
}

std::string toCanonicalKey(const std::string& display) {
    std::string out;
    out.reserve(display.size());
    for (unsigned char uc : display) {
        char c = static_cast<char>(std::tolower(uc));
        if (c >= 'a' && c <= 'z') {
            out.push_back(c);
        }
    }
    return out;
}

std::string toDigitString(const std::string& display) {
    std::string digits;
    digits.reserve(display.size());
    for (unsigned char uc : display) {
        char c = static_cast<char>(std::tolower(uc));
        if (c == '\'' || c == '-') continue;
        uint8_t d = charToT9Digit(c);
        if (d < 2 || d > 9) {
            return ""; // Invalid character
        }
        digits.push_back(static_cast<char>('0' + d));
    }
    return digits;
}

uint16_t quantizeFreq(uint64_t freq) {
    double lf = std::log10(1.0 + static_cast<double>(freq)) * static_cast<double>(LOG_FREQ_SCALE);
    if (lf < 0.0) lf = 0.0;
    if (lf > 65535.0) lf = 65535.0;
    return static_cast<uint16_t>(std::llround(lf));
}

struct InputEntry {
    std::string display;
    std::string canonical;
    std::string digits;
    uint64_t freq = 1;
    uint8_t flags = 0;
};

struct TrieNode {
    int depth = 0;
    std::vector<uint32_t> terminalWordIds;
    std::vector<uint32_t> completionWordIds;
    std::map<uint8_t, TrieNode*> children;
    uint16_t maxLogFreq = 0;
    uint32_t outNodeIdx = UINT32_MAX;
};

struct ChildSig {
    uint8_t digit;
    uint32_t targetOutId;
};

struct NodeSignature {
    std::vector<ChildSig> children;
    bool operator==(const NodeSignature& o) const {
        if (children.size() != o.children.size()) return false;
        for (size_t i = 0; i < children.size(); ++i) {
            if (children[i].digit != o.children[i].digit ||
                children[i].targetOutId != o.children[i].targetOutId) {
                return false;
            }
        }
        return true;
    }
};

struct NodeSignatureHash {
    size_t operator()(const NodeSignature& s) const {
        size_t h = s.children.size() * 0x9e3779b97f4a7c15ULL;
        for (const auto& c : s.children) {
            h ^= (static_cast<size_t>(c.digit) << 32) ^ static_cast<size_t>(c.targetOutId);
            h *= 0x9e3779b97f4a7c15ULL;
        }
        return h;
    }
};

class DawgV2Compiler {
public:
    DawgV2Compiler() {
        root = new TrieNode();
    }

    ~DawgV2Compiler() {
        for (TrieNode* n : allocatedNodes) {
            delete n;
        }
        delete root;
    }

    void addOrUpdateWord(const std::string& display, uint64_t freq, uint8_t flags) {
        if (display.empty() || display.size() >= MAX_WORD_CHARS) return;
        std::string digits = toDigitString(display);
        if (digits.empty()) return;

        std::string canonical = toCanonicalKey(display);
        if (canonical.empty()) return;

        // If flagged as contraction override, erase unpunctuated lowercase canonical duplicate
        if (flags & LEX_FLAG_CONTRACTION) {
            if (canonical != display) {
                wordsByDisplay.erase(canonical);
            }
            // Also erase plain lowercase version (e.g. "i" -> "I")
            std::string lowerDisplay = display;
            for (char& ch : lowerDisplay) ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
            if (lowerDisplay != display) {
                wordsByDisplay.erase(lowerDisplay);
            }
        }

        auto it = wordsByDisplay.find(display);
        if (it == wordsByDisplay.end()) {
            InputEntry e;
            e.display = display;
            e.canonical = canonical;
            e.digits = digits;
            e.freq = freq;
            e.flags = flags;
            wordsByDisplay.emplace(display, std::move(e));
        } else {
            it->second.freq = std::max(it->second.freq, freq);
            it->second.flags |= flags;
        }
    }

    bool buildAndWrite(const std::string& outputPath) {
        // 1. Sort all vocabulary words descending by frequency -> word_id 0 is hottest!
        std::vector<InputEntry> sortedEntries;
        sortedEntries.reserve(wordsByDisplay.size());
        for (auto& kv : wordsByDisplay) {
            sortedEntries.push_back(std::move(kv.second));
        }
        std::sort(sortedEntries.begin(), sortedEntries.end(), [](const InputEntry& a, const InputEntry& b) {
            if (a.freq != b.freq) return a.freq > b.freq;
            return a.display < b.display;
        });

        // 2. Build LexiconWordV2 table + StringPool in hot-first order
        std::vector<LexiconWordV2> wordTable(sortedEntries.size());
        std::vector<char> stringPool;
        stringPool.reserve(sortedEntries.size() * 9);
        uint32_t maxWordLen = 0;

        for (uint32_t wid = 0; wid < sortedEntries.size(); ++wid) {
            const InputEntry& e = sortedEntries[wid];
            LexiconWordV2& lw = wordTable[wid];
            lw.str_offset = static_cast<uint32_t>(stringPool.size());
            lw.log_freq = quantizeFreq(e.freq);
            lw.length = static_cast<uint8_t>(e.display.size());
            lw.flags = e.flags;

            stringPool.insert(stringPool.end(), e.display.begin(), e.display.end());
            stringPool.push_back('\0');
            maxWordLen = std::max<uint32_t>(maxWordLen, lw.length);

            // Insert into T9-digit Trie
            TrieNode* cur = root;
            for (size_t i = 0; i < e.digits.size(); ++i) {
                uint8_t d = static_cast<uint8_t>(e.digits[i] - '0');
                auto childIt = cur->children.find(d);
                if (childIt == cur->children.end()) {
                    TrieNode* child = new TrieNode();
                    child->depth = cur->depth + 1;
                    allocatedNodes.push_back(child);
                    cur->children[d] = child;
                    cur = child;
                } else {
                    cur = childIt->second;
                }
            }
            cur->terminalWordIds.push_back(wid);
        }

        // 3. Compute subtree maxLogFreq & pre-sorted completion caches for depth <= MAX_CACHED_COMPLETION_DEPTH
        computeSubtreeStats(root, wordTable);
        populateCompletionCaches(root, wordTable);

        // 4. Minimal DAFSA suffix merging for terminal-less nodes at depth > MAX_CACHED_COMPLETION_DEPTH
        std::unordered_map<NodeSignature, uint32_t, NodeSignatureHash> dafsaTable;
        std::vector<TrieNode*> uniqueNodes;
        std::vector<std::vector<std::pair<uint8_t, uint32_t>>> uniqueEdges;
        minimizeDafsaBottomUp(root, dafsaTable, uniqueNodes, uniqueEdges);

        uint32_t rootId = root->outNodeIdx;

        // 5. Reorder uniqueNodes into BFS order from root for optimal cache/page locality
        std::vector<int32_t> remap(uniqueNodes.size(), -1);
        std::vector<uint32_t> bfsOrder;
        bfsOrder.reserve(uniqueNodes.size());
        std::queue<uint32_t> q;
        remap[rootId] = 0;
        bfsOrder.push_back(rootId);
        q.push(rootId);

        while (!q.empty()) {
            uint32_t u = q.front();
            q.pop();
            for (const auto& edge : uniqueEdges[u]) {
                uint32_t v = edge.second;
                if (remap[v] == -1) {
                    remap[v] = static_cast<int32_t>(bfsOrder.size());
                    bfsOrder.push_back(v);
                    q.push(v);
                }
            }
        }

        // 6. Flatten DigitNodeV2[], DigitEdgeV2[], bucketWords[]
        std::vector<DigitNodeV2> outNodes(bfsOrder.size());
        std::vector<DigitEdgeV2> outEdges;
        // Edge 0 reserved as dummy NULL edge
        outEdges.push_back(DigitEdgeV2{0, 0, 0, 0});

        std::vector<uint32_t> bucketWords;
        bucketWords.reserve(wordTable.size() * 2);

        for (size_t newIdx = 0; newIdx < bfsOrder.size(); ++newIdx) {
            uint32_t oldIdx = bfsOrder[newIdx];
            TrieNode* tn = uniqueNodes[oldIdx];
            DigitNodeV2& dn = outNodes[newIdx];

            // Terminal words bucket (already sorted by wid ascending == freq descending)
            dn.terminal_count = static_cast<uint16_t>(std::min<size_t>(tn->terminalWordIds.size(), 65535));
            dn.terminal_offset = dn.terminal_count > 0 ? static_cast<uint32_t>(bucketWords.size()) : 0;
            for (uint16_t i = 0; i < dn.terminal_count; ++i) {
                bucketWords.push_back(tn->terminalWordIds[i]);
            }

            // Completion words bucket (depth <= MAX_CACHED_COMPLETION_DEPTH)
            dn.completion_count = static_cast<uint16_t>(std::min<size_t>(tn->completionWordIds.size(), 65535));
            dn.completion_offset = dn.completion_count > 0 ? static_cast<uint32_t>(bucketWords.size()) : 0;
            for (uint16_t i = 0; i < dn.completion_count; ++i) {
                bucketWords.push_back(tn->completionWordIds[i]);
            }

            const auto& edgesList = uniqueEdges[oldIdx];
            if (edgesList.empty()) {
                dn.first_child_edge = 0;
            } else {
                dn.first_child_edge = static_cast<uint32_t>(outEdges.size());
                for (size_t e = 0; e < edgesList.size(); ++e) {
                    DigitEdgeV2 de;
                    de.digit = edgesList[e].first;
                    de.target_node = static_cast<uint32_t>(remap[edgesList[e].second]);
                    de.has_next_sibling = (e + 1 < edgesList.size()) ? 1 : 0;
                    de.max_log_freq = uniqueNodes[edgesList[e].second]->maxLogFreq;
                    outEdges.push_back(de);
                }
            }
        }

        // 7. Write binary file
        DawgHeaderV2 hdr;
        std::memset(&hdr, 0, sizeof(hdr));
        std::memcpy(hdr.magic, "OPENT9D2", 8);
        hdr.version = 2;
        hdr.node_count = static_cast<uint32_t>(outNodes.size());
        hdr.edge_count = static_cast<uint32_t>(outEdges.size());
        hdr.word_count = static_cast<uint32_t>(wordTable.size());
        hdr.bucket_count = static_cast<uint32_t>(bucketWords.size());
        hdr.string_pool_bytes = static_cast<uint32_t>(stringPool.size());
        hdr.max_word_len = maxWordLen;
        hdr.root_node_index = 0;
        hdr.flags = 0;

        std::ofstream out(outputPath, std::ios::binary | std::ios::trunc);
        if (!out.is_open()) {
            std::cerr << "Failed to open output file: " << outputPath << "\n";
            return false;
        }

        out.write(reinterpret_cast<const char*>(&hdr), sizeof(hdr));
        out.write(reinterpret_cast<const char*>(outNodes.data()), outNodes.size() * sizeof(DigitNodeV2));
        out.write(reinterpret_cast<const char*>(outEdges.data()), outEdges.size() * sizeof(DigitEdgeV2));
        out.write(reinterpret_cast<const char*>(wordTable.data()), wordTable.size() * sizeof(LexiconWordV2));
        out.write(reinterpret_cast<const char*>(bucketWords.data()), bucketWords.size() * sizeof(uint32_t));
        out.write(stringPool.data(), stringPool.size());
        out.close();

        size_t totalBytes = sizeof(hdr)
            + outNodes.size() * sizeof(DigitNodeV2)
            + outEdges.size() * sizeof(DigitEdgeV2)
            + wordTable.size() * sizeof(LexiconWordV2)
            + bucketWords.size() * sizeof(uint32_t)
            + stringPool.size();

        std::cout << "Generated OPENT9D2 " << outputPath << "\n"
                  << "  Words:        " << wordTable.size() << "\n"
                  << "  Trie Nodes:   " << (allocatedNodes.size() + 1) << " -> Minimized DAFSA Nodes: " << outNodes.size() << "\n"
                  << "  Edges:        " << outEdges.size() << "\n"
                  << "  BucketWords:  " << bucketWords.size() << "\n"
                  << "  Total Size:   " << totalBytes << " bytes (" << (totalBytes / (1024.0 * 1024.0)) << " MB)\n";
        return true;
    }

private:
    TrieNode* root;
    std::vector<TrieNode*> allocatedNodes;
    std::unordered_map<std::string, InputEntry> wordsByDisplay;

    uint16_t computeSubtreeStats(TrieNode* node, const std::vector<LexiconWordV2>& wordTable) {
        uint16_t best = 0;
        for (uint32_t wid : node->terminalWordIds) {
            best = std::max(best, wordTable[wid].log_freq);
        }
        for (auto& kv : node->children) {
            best = std::max(best, computeSubtreeStats(kv.second, wordTable));
        }
        node->maxLogFreq = best;
        return best;
    }

    struct CompletionCand {
        uint32_t wid;
        float score;
    };

    void collectSubtreeWords(TrieNode* node, int baseDepth, const std::vector<LexiconWordV2>& wordTable,
                             std::vector<CompletionCand>& out) {
        for (auto& kv : node->children) {
            TrieNode* child = kv.second;
            int delta = child->depth - baseDepth;
            for (uint32_t wid : child->terminalWordIds) {
                float lf = static_cast<float>(wordTable[wid].log_freq) / LOG_FREQ_SCALE;
                float s = lf - (0.08f * static_cast<float>(delta));
                out.push_back({wid, s});
            }
            if (delta < 10) {
                collectSubtreeWords(child, baseDepth, wordTable, out);
            }
        }
    }

    void populateCompletionCaches(TrieNode* node, const std::vector<LexiconWordV2>& wordTable) {
        if (node->depth >= 1 && node->depth <= MAX_CACHED_COMPLETION_DEPTH && !node->children.empty()) {
            std::vector<CompletionCand> cands;
            cands.reserve(64);
            collectSubtreeWords(node, node->depth, wordTable, cands);
            std::sort(cands.begin(), cands.end(), [](const CompletionCand& a, const CompletionCand& b) {
                if (a.score != b.score) return a.score > b.score;
                return a.wid < b.wid;
            });
            size_t keep = std::min(cands.size(), MAX_CACHED_COMPLETIONS_PER_NODE);
            node->completionWordIds.reserve(keep);
            for (size_t i = 0; i < keep; ++i) {
                node->completionWordIds.push_back(cands[i].wid);
            }
        }
        for (auto& kv : node->children) {
            populateCompletionCaches(kv.second, wordTable);
        }
    }

    uint32_t minimizeDafsaBottomUp(
        TrieNode* node,
        std::unordered_map<NodeSignature, uint32_t, NodeSignatureHash>& dafsaTable,
        std::vector<TrieNode*>& uniqueNodes,
        std::vector<std::vector<std::pair<uint8_t, uint32_t>>>& uniqueEdges
    ) {
        std::vector<std::pair<uint8_t, uint32_t>> childEdges;
        childEdges.reserve(node->children.size());

        for (auto& kv : node->children) {
            uint32_t childId = minimizeDafsaBottomUp(kv.second, dafsaTable, uniqueNodes, uniqueEdges);
            childEdges.push_back({kv.first, childId});
        }

        // Order outgoing edges descending by subtree maxLogFreq for fast best-first traversal
        std::sort(childEdges.begin(), childEdges.end(), [&](const auto& a, const auto& b) {
            uint16_t fa = uniqueNodes[a.second]->maxLogFreq;
            uint16_t fb = uniqueNodes[b.second]->maxLogFreq;
            if (fa != fb) return fa > fb;
            return a.first < b.first;
        });

        // Merge identical terminal-less suffix subtrees beyond cached completion depth
        if (node->depth > MAX_CACHED_COMPLETION_DEPTH && node->terminalWordIds.empty() && node->completionWordIds.empty()) {
            NodeSignature sig;
            sig.children.reserve(childEdges.size());
            for (const auto& ce : childEdges) {
                sig.children.push_back({ce.first, ce.second});
            }
            auto it = dafsaTable.find(sig);
            if (it != dafsaTable.end()) {
                node->outNodeIdx = it->second;
                return it->second;
            }
            uint32_t id = static_cast<uint32_t>(uniqueNodes.size());
            uniqueNodes.push_back(node);
            uniqueEdges.push_back(std::move(childEdges));
            dafsaTable.emplace(std::move(sig), id);
            node->outNodeIdx = id;
            return id;
        }

        uint32_t id = static_cast<uint32_t>(uniqueNodes.size());
        uniqueNodes.push_back(node);
        uniqueEdges.push_back(std::move(childEdges));
        node->outNodeIdx = id;
        return id;
    }
};

std::string trim(const std::string& s) {
    size_t start = 0;
    while (start < s.size() && std::isspace(static_cast<unsigned char>(s[start]))) start++;
    size_t end = s.size();
    while (end > start && std::isspace(static_cast<unsigned char>(s[end - 1]))) end--;
    return s.substr(start, end - start);
}

uint8_t parseFlags(const std::string& flagStr) {
    uint8_t flags = 0;
    std::string f = trim(flagStr);
    if (f.find("slang") != std::string::npos) flags |= LEX_FLAG_SLANG;
    if (f.find("contraction") != std::string::npos) flags |= LEX_FLAG_CONTRACTION;
    return flags;
}

} // namespace

int main(int argc, char** argv) {
    if (argc < 3) {
        std::cerr << "Usage: dawg_builder <output_v2.dawg> <input_1.tsv> [input_2.tsv ...]\n";
        return 1;
    }

    std::string outputPath = argv[1];
    DawgV2Compiler compiler;

    for (int i = 2; i < argc; ++i) {
        std::ifstream infile(argv[i]);
        if (!infile.is_open()) {
            std::cerr << "Warning: Could not open " << argv[i] << "\n";
            continue;
        }

        std::string line;
        int count = 0;
        while (std::getline(infile, line)) {
            std::string trimmedLine = trim(line);
            if (trimmedLine.empty() || trimmedLine[0] == '#') continue;

            std::stringstream ss(trimmedLine);
            std::string word, freqStr, flagStr;
            std::getline(ss, word, '\t');
            std::getline(ss, freqStr, '\t');
            std::getline(ss, flagStr, '\t');

            word = trim(word);
            uint64_t freq = 1;
            if (!freqStr.empty()) {
                try {
                    freq = std::stoull(trim(freqStr));
                } catch (...) {
                    freq = 1;
                }
            }
            uint8_t flags = parseFlags(flagStr);

            if (!word.empty()) {
                compiler.addOrUpdateWord(word, freq, flags);
                count++;
            }
        }
        std::cout << "Loaded " << count << " entries from " << argv[i] << "\n";
    }

    return compiler.buildAndWrite(outputPath) ? 0 : 1;
}
