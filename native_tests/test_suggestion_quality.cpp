#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <iomanip>
#include "../app/src/main/cpp/include/dawg_engine.hpp"
#include "../app/src/main/cpp/include/spatial_scoring.hpp"
#include "../app/src/main/cpp/include/native_config.hpp"

static int charToT9(char c) {
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

static void testWordList(DawgEngine& engine, const SpatialScorer& scorer, const NativeConfig& config,
                         const std::string& langTitle, const std::vector<std::string>& words) {
    std::cout << "\n========================================================" << std::endl;
    std::cout << "  " << langTitle << " Suggestion Quality Evaluation" << std::endl;
    std::cout << "========================================================" << std::endl;

    int rank1 = 0, top3 = 0, found = 0, missing = 0;

    for (const auto& w : words) {
        engine.reset();
        for (char c : w) {
            int d = charToT9(c);
            if (d >= 2 && d <= 9) {
                engine.pushStroke(d, 0, 0, scorer, config);
            }
        }
        uint8_t count = engine.getCandidateCount();
        const CandidateWord* cands = engine.getCandidates();
        int r = -1;
        for (uint8_t i = 0; i < count; ++i) {
            if (cands[i].word == w) {
                r = i + 1;
                break;
            }
        }
        if (r == 1) rank1++;
        if (r >= 1 && r <= 3) top3++;
        if (r >= 1) found++;
        else missing++;

        std::cout << "  " << std::setw(18) << std::left << w
                  << " | Rank: " << std::setw(2) << r
                  << " | Cands: " << std::setw(2) << (int)count;
        if (count > 0) {
            std::cout << " | Top: '" << cands[0].word << "'";
            if (r > 1) {
                std::cout << " | Alt 2nd: '" << (count > 1 ? cands[1].word : "") << "'";
            }
        } else {
            std::cout << " | [NO CANDIDATES]";
        }
        if (r == -1) {
            std::cout << " <-- [NEEDS IMPROVEMENT: Not in lexicon!]";
        }
        std::cout << std::endl;
    }

    std::cout << "\n" << langTitle << " Results:" << std::endl;
    std::cout << "  Total Words: " << words.size() << std::endl;
    std::cout << "  Rank 1:      " << rank1 << " (" << (100.0 * rank1 / words.size()) << "%)" << std::endl;
    std::cout << "  Top 3:       " << top3 << " (" << (100.0 * top3 / words.size()) << "%)" << std::endl;
    std::cout << "  Found:       " << found << " (" << (100.0 * found / words.size()) << "%)" << std::endl;
    std::cout << "  Missing:     " << missing << " (" << (100.0 * missing / words.size()) << "%)" << std::endl;
}

int main() {
    // Load Lexicons
    std::ifstream enFile("app/src/main/assets/dictionaries/en_lexicon.dawg", std::ios::binary);
    enFile.seekg(0, std::ios::end);
    size_t enSize = enFile.tellg();
    enFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> enBuffer(enSize);
    enFile.read(reinterpret_cast<char*>(enBuffer.data()), enSize);

    std::ifstream idFile("app/src/main/assets/dictionaries/id_lexicon.dawg", std::ios::binary);
    idFile.seekg(0, std::ios::end);
    size_t idSize = idFile.tellg();
    idFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> idBuffer(idSize);
    idFile.read(reinterpret_cast<char*>(idBuffer.data()), idSize);

    NativeConfig config;
    SpatialScorer scorer;
    DawgEngine engine;

    // 1. Indonesian Informal & Common Daily Words
    engine.setLexiconData(idBuffer.data(), idBuffer.size());
    std::vector<std::string> idInformalWords = {
        "gak", "nggak", "ngga", "banget", "bgt", "udah", "udh", "gimana", "gmn", "kalo",
        "klo", "bener", "ngapain", "aja", "cuma", "dong", "deh", "sih", "nih", "tuh",
        "lu", "gue", "gw", "bro", "sis", "makasih", "makasi", "makaci", "beneran",
        "capek", "cape", "baper", "mager", "kepo", "curhat", "nongkrong", "santai",
        "asik", "keren", "mantap", "mantul", "wkwk", "wkwkwk", "haha", "hahaha"
    };
    testWordList(engine, scorer, config, "Indonesian Slang & Informal (Bahasa Gaul)", idInformalWords);

    // 2. Indonesian Affixed Forms (Grammar forms)
    std::vector<std::string> idAffixedWords = {
        "rumahnya", "bukunya", "mobilnya", "anaknya", "temannya", "orangnya",
        "pekerjaannya", "perjalanannya", "pembicaraannya", "perkembangannya",
        "kemandiriannya", "keberadaannya", "memberikan", "mengatakan", "mendapatkan",
        "menemukan", "melakukan", "menggunakan", "mempunyai", "menunjukkan",
        "merupakan", "memerlukan", "memperhatikan", "mempercepat", "memperbaiki"
    };
    testWordList(engine, scorer, config, "Indonesian Affixed & Suffix Forms (-nya, -kan, me-)", idAffixedWords);

    // 3. English Informal & Contractions / Abbreviations
    engine.setLexiconData(enBuffer.data(), enBuffer.size());
    std::vector<std::string> enInformalWords = {
        "dont", "doesnt", "didnt", "wont", "cant", "couldnt", "shouldnt", "wouldnt",
        "isnt", "arent", "wasnt", "werent", "havent", "hasnt", "hadnt",
        "im", "youre", "hes", "shes", "theyre", "weve", "youve", "theyve",
        "gonna", "wanna", "gotta", "kinda", "sorta", "lemme", "gimme",
        "yeah", "yep", "nope", "okay", "ok", "hey", "cool", "awesome",
        "pls", "thx", "idk", "tbh", "omg", "lol", "btw", "fyi", "brb", "imo"
    };
    testWordList(engine, scorer, config, "English Informal & Contractions", enInformalWords);

    // 4. Prefix Completions (Typing prefix -> check if full word appears)
    std::cout << "\n========================================================" << std::endl;
    std::cout << "  Prefix Completion Evaluation (Typing prefix digits)   " << std::endl;
    std::cout << "========================================================" << std::endl;
    struct PrefixTest {
        std::string lang;
        std::string prefixWord;
        std::string targetFullWord;
    };
    std::vector<PrefixTest> prefixTests = {
        {"EN", "hell", "hello"},
        {"EN", "prog", "program"},
        {"EN", "comp", "computer"},
        {"EN", "appl", "application"},
        {"EN", "devel", "development"},
        {"ID", "sela", "selamat"},
        {"ID", "teri", "terima"},
        {"ID", "kasi", "kasih"},
        {"ID", "peme", "pemerintah"},
        {"ID", "perk", "perkembangan"}
    };

    for (const auto& pt : prefixTests) {
        if (pt.lang == "EN") engine.setLexiconData(enBuffer.data(), enBuffer.size());
        else engine.setLexiconData(idBuffer.data(), idBuffer.size());

        engine.reset();
        for (char c : pt.prefixWord) {
            engine.pushStroke(charToT9(c), 0, 0, scorer, config);
        }
        uint8_t count = engine.getCandidateCount();
        const CandidateWord* cands = engine.getCandidates();
        bool foundTarget = false;
        int rank = -1;
        for (uint8_t i = 0; i < count; ++i) {
            if (cands[i].word == pt.targetFullWord) {
                foundTarget = true;
                rank = i + 1;
                break;
            }
        }
        std::cout << "  [" << pt.lang << "] Prefix '" << pt.prefixWord << "' -> Looking for '"
                  << pt.targetFullWord << "': " << (foundTarget ? "[FOUND rank " + std::to_string(rank) + "]" : "[NOT IN TOP 16]")
                  << " (Top: '" << (count > 0 ? cands[0].word : "none") << "')" << std::endl;
    }

    return 0;
}
