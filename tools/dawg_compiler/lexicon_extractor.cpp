#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#pragma pack(push, 1)
struct LegacyDawgHeader {
    char magic[8];
    uint32_t version;
    uint32_t node_count;
    uint32_t edge_count;
    uint32_t word_count;
    uint32_t max_word_len;
    uint32_t root_first_edge;
    uint32_t reserved;
};

struct LegacyDawgEdge {
    char letter;
    uint8_t is_terminal;
    uint8_t has_next_sibling;
    uint8_t digit;
    uint32_t target_first_edge;
    uint32_t frequency;
    uint32_t max_frequency;
};
#pragma pack(pop)

static void dumpDfs(
    const std::vector<LegacyDawgEdge>& edges,
    uint32_t edgeIdx,
    std::string& buffer,
    std::ostream& out
) {
    while (edgeIdx > 0 && edgeIdx < edges.size()) {
        const LegacyDawgEdge& e = edges[edgeIdx];
        buffer.push_back(e.letter);
        if (e.is_terminal) {
            out << buffer << '\t' << e.frequency << '\n';
        }
        if (e.target_first_edge != 0) {
            dumpDfs(edges, e.target_first_edge, buffer, out);
        }
        buffer.pop_back();
        if (!e.has_next_sibling) break;
        edgeIdx++;
    }
}

int main(int argc, char** argv) {
    if (argc != 3) {
        std::cerr << "Usage: lexicon_extractor <input_v1.dawg> <output.tsv>\n";
        return 1;
    }

    std::ifstream in(argv[1], std::ios::binary);
    if (!in.is_open()) {
        std::cerr << "Failed to open input: " << argv[1] << "\n";
        return 1;
    }

    LegacyDawgHeader hdr;
    in.read(reinterpret_cast<char*>(&hdr), sizeof(hdr));
    if (!in || std::memcmp(hdr.magic, "OPENT9D1", 8) != 0 || hdr.version != 1) {
        std::cerr << "Invalid OPENT9D1 header in " << argv[1] << "\n";
        return 1;
    }

    std::vector<LegacyDawgEdge> edges(hdr.edge_count);
    in.read(reinterpret_cast<char*>(edges.data()), static_cast<std::streamsize>(hdr.edge_count * sizeof(LegacyDawgEdge)));
    if (!in) {
        std::cerr << "Failed to read edges from " << argv[1] << "\n";
        return 1;
    }

    std::ofstream out(argv[2], std::ios::trunc);
    if (!out.is_open()) {
        std::cerr << "Failed to open output: " << argv[2] << "\n";
        return 1;
    }

    std::string buf;
    buf.reserve(64);
    dumpDfs(edges, hdr.root_first_edge, buf, out);
    std::cout << "Extracted from " << argv[1] << " -> " << argv[2] << "\n";
    return 0;
}
