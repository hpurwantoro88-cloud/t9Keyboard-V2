#include <iostream>
#include <fstream>
#include <string>
#include <vector>
#include <map>
#include <queue>
#include <cctype>
#include <cstring>
#include <algorithm>
#include "../../app/src/main/cpp/include/dawg_engine.hpp"

inline uint8_t charToT9Digit(char c) {
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

struct TrieNode {
    bool is_terminal = false;
    uint32_t frequency = 0;
    uint32_t max_frequency = 0;
    std::map<char, TrieNode*> children;
    uint32_t first_edge_idx = 0;
};

class DawgBuilder {
public:
    DawgBuilder() {
        root = new TrieNode();
    }

    ~DawgBuilder() {
        deleteNode(root);
    }

    void insert(const std::string& word, uint32_t freq) {
        if (word.empty() || word.length() >= MAX_WORD_CHARS) return;
        for (char c : word) {
            if (c < 'a' || c > 'z') return;
        }

        TrieNode* curr = root;
        for (char c : word) {
            if (curr->children.find(c) == curr->children.end()) {
                curr->children[c] = new TrieNode();
            }
            curr = curr->children[c];
        }
        curr->is_terminal = true;
        curr->frequency = std::max(curr->frequency, freq);
        word_count++;
        if (word.length() > max_word_len) {
            max_word_len = static_cast<uint32_t>(word.length());
        }
    }

    uint32_t computeMaxFreq(TrieNode* node) {
        if (!node) return 0;
        uint32_t m = node->is_terminal ? node->frequency : 0;
        for (auto& pair : node->children) {
            m = std::max(m, computeMaxFreq(pair.second));
        }
        node->max_frequency = m;
        return m;
    }

    bool compileToFile(const std::string& outputPath) {
        computeMaxFreq(root);

        std::vector<DawgEdge> edges;
        // Edge 0 is reserved/dummy so that 0 means NULL
        DawgEdge dummy;
        std::memset(&dummy, 0, sizeof(dummy));
        edges.push_back(dummy);

        // BFS to layout edges
        std::queue<TrieNode*> q;
        q.push(root);

        uint32_t nodeCount = 0;

        while (!q.empty()) {
            TrieNode* parent = q.front();
            q.pop();
            nodeCount++;

            if (parent->children.empty()) {
                parent->first_edge_idx = 0;
                continue;
            }

            uint32_t firstIdx = static_cast<uint32_t>(edges.size());
            parent->first_edge_idx = firstIdx;

            size_t childCount = parent->children.size();
            size_t cIdx = 0;

            for (const auto& pair : parent->children) {
                char c = pair.first;
                TrieNode* child = pair.second;

                DawgEdge edge;
                edge.letter = c;
                edge.digit = charToT9Digit(c);
                edge.is_terminal = child->is_terminal ? 1 : 0;
                edge.has_next_sibling = (cIdx + 1 < childCount) ? 1 : 0;
                edge.target_first_edge = 0; // will update in second pass
                edge.frequency = child->frequency;
                edge.max_frequency = child->max_frequency;

                edges.push_back(edge);
                q.push(child);
                cIdx++;
            }
        }

        // Second pass: link target_first_edge
        std::queue<TrieNode*> q2;
        q2.push(root);

        while (!q2.empty()) {
            TrieNode* parent = q2.front();
            q2.pop();

            if (parent->children.empty()) continue;

            uint32_t currentEdgeIdx = parent->first_edge_idx;
            for (const auto& pair : parent->children) {
                TrieNode* child = pair.second;
                edges[currentEdgeIdx].target_first_edge = child->first_edge_idx;
                q2.push(child);
                currentEdgeIdx++;
            }
        }

        DawgHeader header;
        std::memset(&header, 0, sizeof(header));
        std::memcpy(header.magic, "OPENT9D1", 8);
        header.version = 1;
        header.node_count = nodeCount;
        header.edge_count = static_cast<uint32_t>(edges.size());
        header.word_count = word_count;
        header.max_word_len = max_word_len;
        header.root_first_edge = root->first_edge_idx;
        header.reserved = 0;

        std::ofstream out(outputPath, std::ios::binary | std::ios::trunc);
        if (!out.is_open()) {
            std::cerr << "Failed to open output file: " << outputPath << std::endl;
            return false;
        }

        out.write(reinterpret_cast<const char*>(&header), sizeof(header));
        out.write(reinterpret_cast<const char*>(edges.data()), edges.size() * sizeof(DawgEdge));
        out.close();

        std::cout << "Successfully generated " << outputPath << "\n"
                  << "  Words: " << word_count << "\n"
                  << "  Nodes: " << nodeCount << "\n"
                  << "  Edges: " << edges.size() << "\n"
                  << "  Total size: " << (sizeof(header) + edges.size() * sizeof(DawgEdge)) << " bytes\n";
        return true;
    }

private:
    TrieNode* root;
    uint32_t word_count = 0;
    uint32_t max_word_len = 0;

    void deleteNode(TrieNode* node) {
        if (!node) return;
        for (auto& pair : node->children) {
            deleteNode(pair.second);
        }
        delete node;
    }
};

int main(int argc, char** argv) {
    if (argc < 3) {
        std::cerr << "Usage: dawg_builder <output_path> <input_wordlist_1> [input_wordlist_2 ...]\n";
        return 1;
    }

    std::string outputPath = argv[1];
    DawgBuilder builder;

    for (int i = 2; i < argc; ++i) {
        std::string inputPath = argv[i];
        std::ifstream infile(inputPath);
        if (!infile.is_open()) {
            std::cerr << "Warning: Could not open " << inputPath << std::endl;
            continue;
        }

        std::string line;
        int count = 0;
        while (std::getline(infile, line)) {
            if (line.empty() || line[0] == '#') continue;

            size_t tabPos = line.find('\t');
            std::string word;
            uint32_t freq = 1;

            if (tabPos != std::string::npos) {
                word = line.substr(0, tabPos);
                try {
                    freq = static_cast<uint32_t>(std::stoul(line.substr(tabPos + 1)));
                } catch (...) {
                    freq = 1;
                }
            } else {
                word = line;
                freq = 1;
            }

            // Clean word: trim whitespace and lowercase
            while (!word.empty() && std::isspace(static_cast<unsigned char>(word.back()))) {
                word.pop_back();
            }
            while (!word.empty() && std::isspace(static_cast<unsigned char>(word.front()))) {
                word.erase(0, 1);
            }
            for (char& c : word) {
                c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
            }

            if (!word.empty()) {
                builder.insert(word, freq);
                count++;
            }
        }
        std::cout << "Loaded " << count << " entries from " << inputPath << std::endl;
    }

    if (!builder.compileToFile(outputPath)) {
        return 1;
    }

    return 0;
}
