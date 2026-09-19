#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <chrono>
#include <cassert>
#include <cstring>
#include <iomanip>
#include <unistd.h>
#include <sys/resource.h>
#include "../app/src/main/cpp/include/dawg_engine.hpp"
#include "../app/src/main/cpp/include/spatial_scoring.hpp"
#include "../app/src/main/cpp/include/lexicon_manager.hpp"
#include "../app/src/main/cpp/include/dynamic_store.hpp"
#include "../app/src/main/cpp/include/native_config.hpp"

// Read current Resident Set Size (RSS) in kilobytes from /proc/self/statm
static long getProcessRssKb() {
    long rssPages = 0;
    std::ifstream statm("/proc/self/statm");
    if (statm.is_open()) {
        long dummy;
        statm >> dummy >> rssPages;
        statm.close();
    }
    long pageSizeKb = sysconf(_SC_PAGE_SIZE) / 1024;
    return rssPages * pageSizeKb;
}

// Convert character to T9 digit
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

static std::vector<int> wordToDigits(const std::string& word) {
    std::vector<int> digits;
    for (char c : word) {
        int d = charToT9(c);
        if (d >= 2 && d <= 9) {
            digits.push_back(d);
        }
    }
    return digits;
}

struct WordQualityReport {
    std::string word;
    std::vector<int> digits;
    int rankFound; // 1-based, or -1 if not found
    int totalCandidates;
    std::string topCandidate;
    std::vector<std::string> allCandidates;
};

static WordQualityReport evaluateWord(DawgEngine& engine, const SpatialScorer& scorer, const NativeConfig& config, const std::string& word) {
    WordQualityReport rep;
    rep.word = word;
    rep.digits = wordToDigits(word);
    rep.rankFound = -1;
    rep.totalCandidates = 0;

    engine.reset();
    for (int d : rep.digits) {
        engine.pushStroke(d, 0.0f, 0.0f, scorer, config);
    }

    uint8_t count = engine.getCandidateCount();
    const CandidateWord* cands = engine.getCandidates();
    rep.totalCandidates = count;

    for (uint8_t i = 0; i < count; ++i) {
        std::string candStr = cands[i].word;
        rep.allCandidates.push_back(candStr);
        if (candStr == word && rep.rankFound == -1) {
            rep.rankFound = i + 1;
        }
    }
    if (count > 0) {
        rep.topCandidate = cands[0].word;
    }
    return rep;
}

int main() {
    std::cout << "================================================================" << std::endl;
    std::cout << "  OPENT9 SPEED TYPING SIMULATION & RAM CONSUMPTION ANALYSIS    " << std::endl;
    std::cout << "================================================================" << std::endl;

    long initialRss = getProcessRssKb();
    std::cout << "Initial Process RSS: " << initialRss << " KB" << std::endl;

    // 1. Load Lexicons
    std::ifstream enFile("app/src/main/assets/dictionaries/en_lexicon.dawg", std::ios::binary);
    assert(enFile.is_open() && "Missing en_lexicon.dawg");
    enFile.seekg(0, std::ios::end);
    size_t enSize = enFile.tellg();
    enFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> enBuffer(enSize);
    enFile.read(reinterpret_cast<char*>(enBuffer.data()), enSize);
    enFile.close();

    std::ifstream idFile("app/src/main/assets/dictionaries/id_lexicon.dawg", std::ios::binary);
    assert(idFile.is_open() && "Missing id_lexicon.dawg");
    idFile.seekg(0, std::ios::end);
    size_t idSize = idFile.tellg();
    idFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> idBuffer(idSize);
    idFile.read(reinterpret_cast<char*>(idBuffer.data()), idSize);
    idFile.close();

    long rssAfterLexiconLoad = getProcessRssKb();
    std::cout << "RSS after loading DAWG buffers: " << rssAfterLexiconLoad << " KB (+ "
              << (rssAfterLexiconLoad - initialRss) << " KB)" << std::endl;

    NativeConfig config;
    SpatialScorer scorer;
    // Set standard 3x4 grid key centers (Row 0: 1,2,3; Row 1: 4,5,6; Row 2: 7,8,9; Row 3: *,0,#)
    for (int d = 1; d <= 9; ++d) {
        float row = static_cast<float>((d - 1) / 3);
        float col = static_cast<float>((d - 1) % 3);
        scorer.updateKeyGeometry(d, col * 100.0f + 50.0f, row * 100.0f + 50.0f);
    }

    DynamicStore store;
    store.init("/tmp/test_speed_dynamic_store.dat");
    store.reset();

    DawgEngine engine;
    engine.setDynamicStore(&store);

    // ========================================================================
    // PART A: HIGH SPEED TYPING STRESS TEST (20,000 WORDS, RAM LEAK ANALYSIS)
    // ========================================================================
    std::cout << "\n----------------------------------------------------------------" << std::endl;
    std::cout << "TEST 1: Simulating 20,000 Words Speed Typing (EN & ID interleaved)" << std::endl;
    std::cout << "----------------------------------------------------------------" << std::endl;

    std::vector<std::string> sampleEnglishWords = {
        "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog",
        "hello", "world", "information", "technology", "artificial", "intelligence",
        "communication", "keyboard", "predictive", "algorithm", "speed", "test",
        "performance", "memory", "allocation", "engineering", "development",
        "good", "home", "gone", "hood", "please", "thanks", "tomorrow", "yesterday",
        "because", "something", "different", "important", "question", "problem",
        "system", "program", "company", "government", "number", "people", "water"
    };

    std::vector<std::string> sampleIndonesianWords = {
        "selamat", "pagi", "semoga", "hari", "ini", "menyenangkan",
        "terima", "kasih", "banyak", "atas", "bantuan", "dan", "kerja", "sama",
        "pemerintah", "mengeluarkan", "kebijakan", "baru", "nasional",
        "perkembangan", "teknologi", "sangat", "cepat", "kemandirian",
        "kebudayaan", "pemberdayaan", "pertanggungjawaban", "masyarakat",
        "indonesia", "belajar", "bahasa", "kamu", "bisa", "akan", "ada",
        "harus", "sudah", "mereka", "kita", "orang", "rumah", "sekolah"
    };

    long rssStartTest = getProcessRssKb();
    auto startSimTime = std::chrono::high_resolution_clock::now();

    size_t totalStrokes = 0;
    size_t totalCompletions = 0;
    size_t totalWordsTyped = 20000;
    uint64_t fakeTimestamp = 1700000000;

    for (size_t w = 0; w < totalWordsTyped; ++w) {
        bool isEn = (w % 2 == 0);
        if (isEn) {
            engine.setLexiconData(enBuffer.data(), enBuffer.size());
        } else {
            engine.setLexiconData(idBuffer.data(), idBuffer.size());
        }

        const auto& wordList = isEn ? sampleEnglishWords : sampleIndonesianWords;
        const std::string& targetWord = wordList[w % wordList.size()];
        std::vector<int> digits = wordToDigits(targetWord);

        engine.reset();
        for (size_t i = 0; i < digits.size(); ++i) {
            int d = digits[i];
            // Simulate realistic touch coordinate with small jitter (+-5px)
            float cx = static_cast<float>(((d - 1) % 3) * 100 + 50);
            float cy = static_cast<float>(((d - 1) / 3) * 100 + 50);
            float jx = cx + static_cast<float>((w % 11) - 5);
            float jy = cy + static_cast<float>((w % 7) - 3);

            engine.pushStroke(d, jx, jy, scorer, config);
            totalStrokes++;

            // Every 5th stroke, simulate a typo and backspace (rollback)
            if (i == 2 && (w % 5 == 0)) {
                engine.popStroke();
                totalStrokes++;
                engine.pushStroke(d, cx, cy, scorer, config);
                totalStrokes++;
            }
        }

        // Simulate word commit: serialize candidates to pre-allocated buffer
        uint8_t outBuf[512];
        engine.serializeCandidates(outBuf, sizeof(outBuf));

        // Periodically record word usage into DynamicStore (1 out of every 4 words)
        if (w % 4 == 0) {
            store.recordUsage(targetWord.c_str(), fakeTimestamp + w);
        }

        totalCompletions++;

        // Periodic RSS check every 4,000 words
        if ((w + 1) % 4000 == 0) {
            long currentRss = getProcessRssKb();
            std::cout << "  [" << std::setw(5) << (w + 1) << " words] Current RSS: "
                      << currentRss << " KB (Delta from test start: "
                      << (currentRss - rssStartTest) << " KB)" << std::endl;
        }
    }

    auto endSimTime = std::chrono::high_resolution_clock::now();
    double totalMs = std::chrono::duration<double, std::milli>(endSimTime - startSimTime).count();
    long rssEndTest = getProcessRssKb();

    std::cout << "\nTyping Simulation Results:" << std::endl;
    std::cout << "  Total Words Typed:   " << totalWordsTyped << std::endl;
    std::cout << "  Total Strokes Done:   " << totalStrokes << std::endl;
    std::cout << "  Total Elapsed Time:   " << totalMs << " ms" << std::endl;
    std::cout << "  Average Stroke Time:  " << (totalMs * 1000.0 / static_cast<double>(totalStrokes)) << " µs / stroke" << std::endl;
    std::cout << "  Typing Speed Rate:    " << static_cast<int>(totalStrokes / (totalMs / 1000.0)) << " strokes/second" << std::endl;
    std::cout << "  Memory Delta (RSS):   " << (rssEndTest - rssStartTest) << " KB" << std::endl;
    std::cout << "  Final Process RSS:    " << rssEndTest << " KB" << std::endl;

    if (std::abs(rssEndTest - rssStartTest) > 2048) {
        std::cout << "  [WARNING] High memory delta detected! Delta = " << (rssEndTest - rssStartTest) << " KB" << std::endl;
    } else {
        std::cout << "  [PASS] Zero/Negligible memory growth detected! RAM remains strictly stable ("
                  << (rssEndTest - rssStartTest) << " KB delta over 20,000 words)." << std::endl;
    }

    // ========================================================================
    // PART B: ENGLISH SUGGESTION ACCURACY & QUALITY EVALUATION
    // ========================================================================
    std::cout << "\n----------------------------------------------------------------" << std::endl;
    std::cout << "TEST 2: English Vocabulary Suggestion Evaluation" << std::endl;
    std::cout << "----------------------------------------------------------------" << std::endl;

    engine.setLexiconData(enBuffer.data(), enBuffer.size());

    std::vector<std::string> testEnglishWords = {
        // High frequency basic words
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        // Common 4-key collisions
        "good", "home", "gone", "hood", "book", "cool", "look", "took",
        // Multi-syllable & longer words
        "information", "technology", "development", "performance", "communication",
        "intelligence", "artificial", "programming", "important", "yesterday",
        // Contraction stems / target words
        "dont", "cant", "wont", "didnt", "isnt", "arent"
    };

    int enRank1Count = 0;
    int enTop3Count = 0;
    int enFoundCount = 0;
    std::vector<WordQualityReport> enIssues;

    for (const auto& w : testEnglishWords) {
        WordQualityReport r = evaluateWord(engine, scorer, config, w);
        if (r.rankFound == 1) enRank1Count++;
        if (r.rankFound >= 1 && r.rankFound <= 3) enTop3Count++;
        if (r.rankFound >= 1) enFoundCount++;
        else enIssues.push_back(r);

        std::cout << "  EN '" << std::setw(15) << std::left << w << "': "
                  << "Rank=" << std::setw(2) << r.rankFound
                  << " | Cands=" << std::setw(2) << r.totalCandidates
                  << " | Top='" << r.topCandidate << "'";
        if (r.rankFound > 1) {
            std::cout << " [ALT RANK " << r.rankFound << "]";
        } else if (r.rankFound == -1) {
            std::cout << " [NOT FOUND!]";
        }
        std::cout << std::endl;
    }

    double enRank1Pct = (100.0 * enRank1Count) / testEnglishWords.size();
    double enTop3Pct = (100.0 * enTop3Count) / testEnglishWords.size();
    double enFoundPct = (100.0 * enFoundCount) / testEnglishWords.size();

    std::cout << "\nEnglish Suggestion Summary:" << std::endl;
    std::cout << "  Rank 1 Accuracy: " << enRank1Count << " / " << testEnglishWords.size() << " (" << enRank1Pct << "%)" << std::endl;
    std::cout << "  Top 3 Accuracy:  " << enTop3Count << " / " << testEnglishWords.size() << " (" << enTop3Pct << "%)" << std::endl;
    std::cout << "  Recall (Found):  " << enFoundCount << " / " << testEnglishWords.size() << " (" << enFoundPct << "%)" << std::endl;

    // ========================================================================
    // PART C: INDONESIAN SUGGESTION ACCURACY & QUALITY EVALUATION
    // ========================================================================
    std::cout << "\n----------------------------------------------------------------" << std::endl;
    std::cout << "TEST 3: Indonesian Vocabulary Suggestion Evaluation" << std::endl;
    std::cout << "----------------------------------------------------------------" << std::endl;

    engine.setLexiconData(idBuffer.data(), idBuffer.size());

    std::vector<std::string> testIndonesianWords = {
        // High frequency core words
        "yang", "dan", "di", "ini", "itu", "untuk", "dari", "dalam", "tidak", "dengan",
        "saya", "kamu", "bisa", "akan", "ada", "ke", "pada", "oleh", "saat", "harus",
        "sudah", "mereka", "kita", "orang", "semua", "banyak", "lebih", "dapat", "juga",
        "karena", "tentang", "hanya", "lain", "baru", "hari", "waktu", "tempat", "kerja",
        // Common conversational words
        "selamat", "pagi", "siang", "malam", "terima", "kasih", "kembali", "tolong",
        "maaf", "bagaimana", "dimana", "kapan", "siapa", "mengapa", "kenapa", "apakah",
        // Affixed and complex words
        "pemerintah", "mengeluarkan", "kebijakan", "perkembangan", "kemandirian",
        "kebudayaan", "pemberdayaan", "pertanggungjawaban", "masyarakat", "indonesia",
        // Words with affixes (me-, ber-, pe-, -an, -kan, -nya)
        "berjalan", "membaca", "menulis", "melihat", "makanan", "minuman", "kebaikan",
        "pendidikan", "kesehatan", "keamanan", "perubahan", "hubungan"
    };

    int idRank1Count = 0;
    int idTop3Count = 0;
    int idFoundCount = 0;
    std::vector<WordQualityReport> idIssues;

    for (const auto& w : testIndonesianWords) {
        WordQualityReport r = evaluateWord(engine, scorer, config, w);
        if (r.rankFound == 1) idRank1Count++;
        if (r.rankFound >= 1 && r.rankFound <= 3) idTop3Count++;
        if (r.rankFound >= 1) idFoundCount++;
        else idIssues.push_back(r);

        std::cout << "  ID '" << std::setw(20) << std::left << w << "': "
                  << "Rank=" << std::setw(2) << r.rankFound
                  << " | Cands=" << std::setw(2) << r.totalCandidates
                  << " | Top='" << r.topCandidate << "'";
        if (r.rankFound > 1) {
            std::cout << " [ALT RANK " << r.rankFound << "]";
        } else if (r.rankFound == -1) {
            std::cout << " [NOT FOUND!]";
        }
        std::cout << std::endl;
    }

    double idRank1Pct = (100.0 * idRank1Count) / testIndonesianWords.size();
    double idTop3Pct = (100.0 * idTop3Count) / testIndonesianWords.size();
    double idFoundPct = (100.0 * idFoundCount) / testIndonesianWords.size();

    std::cout << "\nIndonesian Suggestion Summary:" << std::endl;
    std::cout << "  Rank 1 Accuracy: " << idRank1Count << " / " << testIndonesianWords.size() << " (" << idRank1Pct << "%)" << std::endl;
    std::cout << "  Top 3 Accuracy:  " << idTop3Count << " / " << testIndonesianWords.size() << " (" << idTop3Pct << "%)" << std::endl;
    std::cout << "  Recall (Found):  " << idFoundCount << " / " << testIndonesianWords.size() << " (" << idFoundPct << "%)" << std::endl;

    // ========================================================================
    // PART D: OVERTYPED / OUT-OF-LEXICON / PREFIX COMPLETION ANALYSIS
    // ========================================================================
    std::cout << "\n----------------------------------------------------------------" << std::endl;
    std::cout << "TEST 4: Overtyping and Deep History Edge Cases" << std::endl;
    std::cout << "----------------------------------------------------------------" << std::endl;

    // Case 1: Typing beyond known Indonesian root (kemandirian -> kemandiriannya)
    engine.reset();
    int kemandiriannyaDigits[] = {5, 3, 6, 2, 6, 3, 4, 7, 4, 2, 6, 6, 9, 2}; // kemandiriannya
    for (size_t s = 0; s < 14; ++s) {
        engine.pushStroke(kemandiriannyaDigits[s], 0.0f, 0.0f, scorer, config);
    }
    std::cout << "  'kemandiriannya' (14 digits) candidate count: " << (int)engine.getCandidateCount() << std::endl;
    for (uint8_t i = 0; i < engine.getCandidateCount() && i < 3; ++i) {
        std::cout << "    [" << (i + 1) << "] " << engine.getCandidates()[i].word << std::endl;
    }

    // Case 2: Extreme overtyping (30 consecutive strokes beyond known words)
    engine.reset();
    for (int i = 0; i < 30; ++i) {
        engine.pushStroke(7, 0.0f, 0.0f, scorer, config);
    }
    std::cout << "  30x '7' consecutive overtype candidate count: " << (int)engine.getCandidateCount() << std::endl;
    for (uint8_t i = 0; i < engine.getCandidateCount() && i < 3; ++i) {
        std::cout << "    [" << (i + 1) << "] " << engine.getCandidates()[i].word << std::endl;
    }

    // Case 3: 1,000 repeated push/pop cycles (stressing beam allocation/rollback)
    engine.reset();
    long rssBeforeCycles = getProcessRssKb();
    for (int i = 0; i < 10000; ++i) {
        engine.pushStroke(4, 0, 0, scorer, config);
        engine.pushStroke(6, 0, 0, scorer, config);
        engine.pushStroke(6, 0, 0, scorer, config);
        engine.pushStroke(3, 0, 0, scorer, config);
        engine.popStroke();
        engine.popStroke();
        engine.popStroke();
        engine.popStroke();
    }
    long rssAfterCycles = getProcessRssKb();
    std::cout << "  10,000 Push/Pop Cycles RSS Delta: " << (rssAfterCycles - rssBeforeCycles) << " KB" << std::endl;

    std::cout << "\n================================================================" << std::endl;
    std::cout << "  SIMULATION AND ANALYSIS COMPLETED SUCCESSFULLY               " << std::endl;
    std::cout << "================================================================" << std::endl;
    return 0;
}
