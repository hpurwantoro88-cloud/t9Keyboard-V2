#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>
#include "../../app/src/main/cpp/include/dawg_engine.hpp"

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
        std::cerr << "Usage: lexicon_extractor <input.dawg> <output.tsv>\n";
        return 1;
    }

    std::ifstream in(argv[1], std::ios::binary);
    if (!in.is_open()) {
        std::cerr << "Failed to open input: " << argv[1] << "\n";
        return 1;
    }

    char magic[8];
    in.read(magic, 8);
    in.seekg(0, std::ios::beg);

    std::ofstream out(argv[2], std::ios::trunc);
    if (!out.is_open()) {
        std::cerr << "Failed to open output: " << argv[2] << "\n";
        return 1;
    }

    if (std::memcmp(magic, "OPENT9D2", 8) == 0) {
        in.seekg(0, std::ios::end);
        size_t sz = static_cast<size_t>(in.tellg());
        in.seekg(0, std::ios::beg);
        std::vector<uint8_t> buf(sz);
        in.read(reinterpret_cast<char*>(buf.data()), static_cast<std::streamsize>(sz));
        const auto* hdr = reinterpret_cast<const DawgHeaderV2*>(buf.data());
        size_t wordsOffset = sizeof(DawgHeaderV2)
            + static_cast<size_t>(hdr->node_count) * sizeof(DigitNodeV2)
            + static_cast<size_t>(hdr->edge_count) * sizeof(DigitEdgeV2);
        const auto* words = reinterpret_cast<const LexiconWordV2*>(buf.data() + wordsOffset);
        size_t strOffset = wordsOffset
            + static_cast<size_t>(hdr->word_count) * sizeof(LexiconWordV2)
            + static_cast<size_t>(hdr->bucket_count) * sizeof(uint32_t);
        const char* strPool = reinterpret_cast<const char*>(buf.data() + strOffset);

        for (uint32_t wid = 0; wid < hdr->word_count; ++wid) {
            const LexiconWordV2& lw = words[wid];
            const char* w = strPool + lw.str_offset;
            double lf = static_cast<double>(lw.log_freq) / static_cast<double>(LOG_FREQ_SCALE);
            uint64_t approxFreq = static_cast<uint64_t>(std::max<long long>(1LL, std::llround(std::pow(10.0, lf) - 1.0)));
            std::string flagStr;
            if (lw.flags & LEX_FLAG_SLANG) flagStr += "slang ";
            if (lw.flags & LEX_FLAG_CONTRACTION) flagStr += "contraction";
            out << w << '\t' << approxFreq;
            if (!flagStr.empty()) out << '\t' << flagStr;
            out << '\n';
        }
        std::cout << "Extracted OPENT9D2 (" << hdr->word_count << " words) from " << argv[1] << " -> " << argv[2] << "\n";
        return 0;
    }

    LegacyDawgHeader hdr;
    in.read(reinterpret_cast<char*>(&hdr), sizeof(hdr));
    if (!in || std::memcmp(hdr.magic, "OPENT9D1", 8) != 0 || hdr.version != 1) {
        std::cerr << "Invalid DAWG header in " << argv[1] << "\n";
        return 1;
    }

    std::vector<LegacyDawgEdge> edges(hdr.edge_count);
    in.read(reinterpret_cast<char*>(edges.data()), static_cast<std::streamsize>(hdr.edge_count * sizeof(LegacyDawgEdge)));
    if (!in) {
        std::cerr << "Failed to read edges from " << argv[1] << "\n";
        return 1;
    }

    std::string buf;
    buf.reserve(64);
    dumpDfs(edges, hdr.root_first_edge, buf, out);
    std::cout << "Extracted from " << argv[1] << " -> " << argv[2] << "\n";
    return 0;
}
