#include <iostream>
#include <fstream>
#include <vector>
#include <chrono>
#include <cassert>
#include <cstring>
#include "../app/src/main/cpp/include/dawg_engine.hpp"
#include "../app/src/main/cpp/include/spatial_scoring.hpp"
#include "../app/src/main/cpp/include/lexicon_manager.hpp"
#include "../app/src/main/cpp/include/dynamic_store.hpp"
#include "../app/src/main/cpp/include/multi_tap_engine.hpp"
#include "../app/src/main/cpp/include/native_config.hpp"

// Global config instance
NativeConfig g_config;

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "RUNNING OPENT9 PHASE 1 C++ UNIT TESTS" << std::endl;
    std::cout << "========================================" << std::endl;

    // Load en_lexicon.dawg
    std::ifstream enFile("app/src/main/assets/dictionaries/en_lexicon.dawg", std::ios::binary);
    assert(enFile.is_open() && "Failed to open en_lexicon.dawg");
    enFile.seekg(0, std::ios::end);
    size_t enSize = enFile.tellg();
    enFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> enBuffer(enSize);
    enFile.read(reinterpret_cast<char*>(enBuffer.data()), enSize);
    enFile.close();

    // Load id_lexicon.dawg
    std::ifstream idFile("app/src/main/assets/dictionaries/id_lexicon.dawg", std::ios::binary);
    assert(idFile.is_open() && "Failed to open id_lexicon.dawg");
    idFile.seekg(0, std::ios::end);
    size_t idSize = idFile.tellg();
    idFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> idBuffer(idSize);
    idFile.read(reinterpret_cast<char*>(idBuffer.data()), idSize);
    idFile.close();

    // ------------------------------------------------------------------------
    // Test 1: LexiconManager loading & pointer swapping (< 0.05ms)
    // ------------------------------------------------------------------------
    LexiconManager lexMgr;
    bool loadedEn = lexMgr.loadFromBuffer("EN", enBuffer.data(), enBuffer.size());
    bool loadedId = lexMgr.loadFromBuffer("ID", idBuffer.data(), idBuffer.size());
    assert(loadedEn && "LexiconManager load EN failed");
    assert(loadedId && "LexiconManager load ID failed");

    auto tSwapStart = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < 1000; ++i) {
        lexMgr.switchLanguage("EN");
        lexMgr.switchLanguage("ID");
    }
    auto tSwapEnd = std::chrono::high_resolution_clock::now();
    double avgSwapMicros = std::chrono::duration<double, std::micro>(tSwapEnd - tSwapStart).count() / 2000.0;
    std::cout << "[PASS] Test 1: Lexicon swap average time: " << avgSwapMicros << " µs (Target: < 50 µs / 0.05ms)" << std::endl;
    assert(avgSwapMicros < 50.0 && "Lexicon swap exceeded 0.05ms target!");

    // ------------------------------------------------------------------------
    // Test 2: DAWG Disambiguation for 4663 (["good", "home", "gone", "hood"])
    // ------------------------------------------------------------------------
    DawgEngine engine;
    bool setLex = engine.setLexiconData(enBuffer.data(), enBuffer.size());
    assert(setLex && "DawgEngine setLexiconData failed");

    SpatialScorer scorer;
    // Set standard key centers: Key 4, 6, 6, 3
    scorer.updateKeyGeometry(4, 50.0f, 100.0f);
    scorer.updateKeyGeometry(6, 250.0f, 100.0f);
    scorer.updateKeyGeometry(3, 250.0f, 50.0f);

    engine.reset();

    auto tSeqStart = std::chrono::high_resolution_clock::now();
    bool p4 = engine.pushStroke(4, 50.0f, 100.0f, scorer, g_config);
    bool p6a = engine.pushStroke(6, 250.0f, 100.0f, scorer, g_config);
    bool p6b = engine.pushStroke(6, 250.0f, 100.0f, scorer, g_config);
    bool p3 = engine.pushStroke(3, 250.0f, 50.0f, scorer, g_config);
    auto tSeqEnd = std::chrono::high_resolution_clock::now();

    assert(p4 && p6a && p6b && p3 && "Push strokes 4-6-6-3 failed");
    double seqMicros = std::chrono::duration<double, std::micro>(tSeqEnd - tSeqStart).count();
    std::cout << "[PASS] Test 2: 4-6-6-3 beam search sequence time: " << seqMicros << " µs (Target: < 200 µs / 0.2ms)" << std::endl;
    assert(seqMicros < 200.0 && "Disambiguation latency exceeded 0.2ms target!");

    uint8_t candCount = engine.getCandidateCount();
    const CandidateWord* cands = engine.getCandidates();
    std::cout << "  Candidate count for 4663: " << static_cast<int>(candCount) << std::endl;
    for (uint8_t i = 0; i < candCount && i < 6; ++i) {
        std::cout << "    [" << (i + 1) << "] " << cands[i].word << " (freq=" << cands[i].frequency << ", score=" << cands[i].score << ")" << std::endl;
    }

    assert(candCount >= 4 && "Expected at least 4 candidates for 4663");
    assert(std::strcmp(cands[0].word, "good") == 0 && "Top candidate must be 'good'");
    assert(std::strcmp(cands[1].word, "home") == 0 && "Second candidate must be 'home'");
    assert(std::strcmp(cands[2].word, "gone") == 0 && "Third candidate must be 'gone'");
    assert(std::strcmp(cands[3].word, "hood") == 0 && "Fourth candidate must be 'hood'");
    std::cout << "[PASS] Test 2: Verified candidate order: good, home, gone, hood" << std::endl;

    // ------------------------------------------------------------------------
    // Test 3: O(1) Rollback pop_stroke() (< 0.01ms / 10 µs)
    // ------------------------------------------------------------------------
    auto tPopStart = std::chrono::high_resolution_clock::now();
    bool popped = engine.popStroke();
    auto tPopEnd = std::chrono::high_resolution_clock::now();
    assert(popped && "pop_stroke failed");
    double popMicros = std::chrono::duration<double, std::micro>(tPopEnd - tPopStart).count();
    std::cout << "[PASS] Test 3: pop_stroke() time: " << popMicros << " µs (Target: < 10 µs / 0.01ms)" << std::endl;
    assert(popMicros < 10.0 && "pop_stroke exceeded 0.01ms target!");
    assert(engine.getDepth() == 3 && "Depth must be restored to 3");

    // ------------------------------------------------------------------------
    // Test 4: Candidate serialization to pre-allocated Direct Buffer
    // ------------------------------------------------------------------------
    uint8_t buffer[512];
    int serializedBytes = engine.serializeCandidates(buffer, sizeof(buffer));
    assert(serializedBytes > 0 && "Candidate serialization failed");
    uint8_t countFromBuf = buffer[0];
    assert(countFromBuf == engine.getCandidateCount() && "Mismatch in serialized candidate count");
    std::cout << "[PASS] Test 4: Direct buffer serialization verified (" << serializedBytes << " bytes, zero heap allocations)" << std::endl;

    // ------------------------------------------------------------------------
    // Test 5: Indonesian Lexicon Disambiguation (e.g. "selamat", "selalu")
    // ------------------------------------------------------------------------
    engine.setLexiconData(idBuffer.data(), idBuffer.size());
    engine.reset();
    // 's'(7) 'e'(3) 'l'(5) 'a'(2) 'm'(6) 'a'(2) 't'(8)
    engine.pushStroke(7, 0, 0, scorer, g_config);
    engine.pushStroke(3, 0, 0, scorer, g_config);
    engine.pushStroke(5, 0, 0, scorer, g_config);
    engine.pushStroke(2, 0, 0, scorer, g_config);
    candCount = engine.getCandidateCount();
    cands = engine.getCandidates();
    std::cout << "  Indonesian prefix 7352 candidates: " << static_cast<int>(candCount) << std::endl;
    for (uint8_t i = 0; i < candCount && i < 4; ++i) {
        std::cout << "    [" << (i + 1) << "] " << cands[i].word << std::endl;
    }
    bool foundSelamatOrSelalu = false;
    for (uint8_t i = 0; i < candCount; ++i) {
        if (std::string(cands[i].word).rfind("sela", 0) == 0) {
            foundSelamatOrSelalu = true;
            break;
        }
    }
    assert(foundSelamatOrSelalu && "Expected 'sela...' words for prefix 7352");
    std::cout << "[PASS] Test 5: Indonesian lexicon search verified" << std::endl;

    // Test typing "kemandiriannya"
    engine.reset();
    int kemandiriannyaDigits[] = {5, 3, 6, 2, 6, 3, 4, 7, 4, 2, 6, 6, 9, 2};
    std::cout << "Testing typing kemandiriannya step-by-step:" << std::endl;
    for (size_t s = 0; s < sizeof(kemandiriannyaDigits)/sizeof(kemandiriannyaDigits[0]); ++s) {
        int d = kemandiriannyaDigits[s];
        std::cout << "  Pushing stroke " << (s + 1) << " (digit " << d << ")..." << std::flush;
        bool ok = engine.pushStroke(d, 0, 0, scorer, g_config);
        std::cout << " ok=" << ok << ", cands=" << static_cast<int>(engine.getCandidateCount());
        if (engine.getCandidateCount() > 0) {
            std::cout << " top=" << engine.getCandidates()[0].word;
        }
        std::cout << std::endl;
    }

    // ------------------------------------------------------------------------
    // Test 6: MultiTapEngine (Lowercase cycling + Shift decoupling)
    // ------------------------------------------------------------------------
    MultiTapEngine mt;
    bool committedPrev = false;
    char committedChar = 0;

    // Tap Key 2 once: 'a'
    char c1 = mt.onKeyPress(2, 1000, ShiftStateEnum::LOWERCASE, &committedPrev, &committedChar);
    assert(c1 == 'a');
    assert(!committedPrev);

    // Tap Key 2 twice: 'b'
    char c2 = mt.onKeyPress(2, 1200, ShiftStateEnum::LOWERCASE, &committedPrev, &committedChar);
    assert(c2 == 'b');

    // Tap Key 2 thrice: 'c'
    char c3 = mt.onKeyPress(2, 1300, ShiftStateEnum::LOWERCASE, &committedPrev, &committedChar);
    assert(c3 == 'c');

    // Tap Key 2 four times: '2'
    char c4 = mt.onKeyPress(2, 1400, ShiftStateEnum::LOWERCASE, &committedPrev, &committedChar);
    assert(c4 == '2');

    // Tap Key 3 (switch key): previous '2' is committed, active is 'd'
    char c5 = mt.onKeyPress(3, 1500, ShiftStateEnum::LOWERCASE, &committedPrev, &committedChar);
    assert(c5 == 'd');
    assert(committedPrev && committedChar == '2');

    // Shift TITLECASE test on Key 2: 'A'
    mt.reset();
    char cShift = mt.onKeyPress(2, 2000, ShiftStateEnum::TITLECASE, &committedPrev, &committedChar);
    assert(cShift == 'A');

    // Timeout test: 600ms
    char timedOutChar = 0;
    bool timedOut = mt.checkTimeout(2700, 600, &timedOutChar);
    assert(timedOut && timedOutChar == 'A');
    std::cout << "[PASS] Test 6: Multi-tap engine verified (cycle: a->b->c->2, switch commit, Shift titlecase, 600ms timeout)" << std::endl;

    // ------------------------------------------------------------------------
    // Test 7: DynamicStore with Half-life Decay
    // ------------------------------------------------------------------------
    DynamicStore store;
    store.init("/tmp/test_opent9_store.dat");
    store.reset();

    uint64_t t0 = 1700000000;
    store.addWord("kuy", 500, t0);
    assert(store.isCustomWord("kuy"));

    // Effective freq at t0 with 30-day half life
    uint32_t freq0 = store.getEffectiveFrequency("kuy", t0, 30);
    assert(freq0 >= 500);

    // Effective freq after 30 days (1 half-life = 30 * 86400 seconds)
    uint64_t t1 = t0 + (30 * 86400);
    uint32_t freq1 = store.getEffectiveFrequency("kuy", t1, 30);
    std::cout << "  DynamicStore frequency decay: Day 0 = " << freq0 << ", Day 30 = " << freq1 << std::endl;
    assert(freq1 < freq0 && freq1 >= (freq0 / 2 - 20));

    // Remove word
    bool rem = store.removeWord("kuy");
    assert(rem && !store.isCustomWord("kuy"));
    std::cout << "[PASS] Test 7: DynamicStore with exponential half-life decay verified" << std::endl;

    // ------------------------------------------------------------------------
    // Test 8: Dynamic Word Candidate Injection (purwantoro)
    // ------------------------------------------------------------------------
    engine.setLexiconData(idBuffer.data(), idBuffer.size());
    engine.setDynamicStore(&store);

    store.addWord("purwantoro", 500, t0);
    assert(store.isCustomWord("purwantoro"));

    engine.reset();
    int purwantoroDigits[] = {7, 8, 7, 9, 2, 6, 8, 6, 7, 6};
    for (int d : purwantoroDigits) {
        bool pushed = engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
        assert(pushed);
    }
    assert(engine.getCandidateCount() > 0);
    assert(std::strcmp(engine.getCandidates()[0].word, "purwantoro") == 0);
    std::cout << "[PASS] Test 8: Learned word 'purwantoro' dynamically injected into T9 candidates: "
              << engine.getCandidates()[0].word << " (score=" << engine.getCandidates()[0].score << ")" << std::endl;

    // ------------------------------------------------------------------------
    // Test 9: Blacklisting / Suggestion Removal
    // ------------------------------------------------------------------------
    // Without removal, 78792686 yields purwanto
    engine.reset();
    int purwantoDigits[] = {7, 8, 7, 9, 2, 6, 8, 6};
    for (int d : purwantoDigits) {
        engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    }
    bool foundPurwanto = false;
    for (uint8_t c = 0; c < engine.getCandidateCount(); ++c) {
        if (std::strcmp(engine.getCandidates()[c].word, "purwanto") == 0) {
            foundPurwanto = true;
            break;
        }
    }
    assert(foundPurwanto);

    // Now remove purwanto (blacklist it)
    store.removeWord("purwanto");
    assert(store.isDeleted("purwanto"));

    engine.reset();
    for (int d : purwantoDigits) {
        engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    }
    for (uint8_t c = 0; c < engine.getCandidateCount(); ++c) {
        assert(std::strcmp(engine.getCandidates()[c].word, "purwanto") != 0);
    }
    std::cout << "[PASS] Test 9: Static candidate 'purwanto' successfully suppressed via blacklist" << std::endl;

    // Re-learning un-blacklists the word
    store.recordUsage("purwanto", t0);
    assert(!store.isDeleted("purwanto"));
    engine.reset();
    for (int d : purwantoDigits) {
        engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    }
    bool restoredPurwanto = false;
    for (uint8_t c = 0; c < engine.getCandidateCount(); ++c) {
        if (std::strcmp(engine.getCandidates()[c].word, "purwanto") == 0) {
            restoredPurwanto = true;
            break;
        }
    }
    assert(restoredPurwanto);
    std::cout << "[PASS] Test 9: 'purwanto' successfully restored upon deliberate re-learning" << std::endl;

    // ------------------------------------------------------------------------
    // Test 10: Forward Prefix Completion (e.g. 4355 -> hello, help)
    // ------------------------------------------------------------------------
    engine.setLexiconData(enBuffer.data(), enBuffer.size());
    engine.setDynamicStore(nullptr);
    engine.reset();
    // 4(h) 3(e) 5(l) 5(l)
    engine.pushStroke(4, 0, 0, scorer, g_config);
    engine.pushStroke(3, 0, 0, scorer, g_config);
    engine.pushStroke(5, 0, 0, scorer, g_config);
    engine.pushStroke(5, 0, 0, scorer, g_config);

    candCount = engine.getCandidateCount();
    cands = engine.getCandidates();
    std::cout << "  Candidates for prefix 4355 (forward completions): " << static_cast<int>(candCount) << std::endl;
    bool foundHello = false;
    for (uint8_t i = 0; i < candCount; ++i) {
        std::cout << "    [" << (i + 1) << "] " << cands[i].word << " (score=" << cands[i].score << ")" << std::endl;
        if (std::strcmp(cands[i].word, "hello") == 0) {
            foundHello = true;
        }
    }
    assert(candCount > 0 && "Must return candidates for 4355");
    assert(foundHello && "Forward completion must include 'hello' for prefix 4355");
    std::cout << "[PASS] Test 10: Forward prefix completion verified ('hello' suggested for 4355)" << std::endl;

    // ------------------------------------------------------------------------
    // Test 11: Closest Word Typo Correction & Overtype
    // ------------------------------------------------------------------------
    // Case 11A: Overtype 4663 + 7 -> retains 'good' / 'goods'
    engine.reset();
    engine.pushStroke(4, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(3, 0, 0, scorer, g_config);
    // Extra digit 7
    engine.pushStroke(7, 0, 0, scorer, g_config);
    candCount = engine.getCandidateCount();
    cands = engine.getCandidates();
    std::cout << "  Overtyped 46637 candidates: " << static_cast<int>(candCount) << std::endl;
    for (uint8_t i = 0; i < candCount && i < 4; ++i) {
        std::cout << "    [" << (i + 1) << "] " << cands[i].word << " (score=" << cands[i].score << ")" << std::endl;
    }
    assert(candCount > 0 && "Overtyped stroke must suggest closest words");
    bool foundGoodOrGoods = false;
    for (uint8_t i = 0; i < candCount; ++i) {
        if (std::strcmp(cands[i].word, "good") == 0 || std::strcmp(cands[i].word, "goods") == 0) {
            foundGoodOrGoods = true;
            break;
        }
    }
    assert(foundGoodOrGoods && "Overtyped 46637 must suggest 'good' or 'goods'");

    // Case 11B: Overtype 4663 + 7 + 7 (466377) -> yields real English words 'goners', 'homers'
    engine.reset();
    engine.pushStroke(4, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(3, 0, 0, scorer, g_config);
    engine.pushStroke(7, 0, 0, scorer, g_config);
    engine.pushStroke(7, 0, 0, scorer, g_config); // 466377 spells 'goners', 'homers'
    candCount = engine.getCandidateCount();
    cands = engine.getCandidates();
    std::cout << "  Overtyped 466377 candidates: " << static_cast<int>(candCount) << std::endl;
    for (uint8_t i = 0; i < candCount && i < 4; ++i) {
        std::cout << "    [" << (i + 1) << "] " << cands[i].word << " (score=" << cands[i].score << ")" << std::endl;
    }
    assert(candCount > 0 && "466377 must suggest valid dictionary words");
    bool foundGonersOrHomers = false;
    for (uint8_t i = 0; i < candCount; ++i) {
        if (std::strcmp(cands[i].word, "goners") == 0 || std::strcmp(cands[i].word, "homers") == 0) {
            foundGonersOrHomers = true;
            break;
        }
    }
    assert(foundGonersOrHomers && "466377 must suggest real words 'goners' or 'homers' instead of gibberish 'goodss'");
    std::cout << "[PASS] Test 11: Closest word typo correction and overtype verified" << std::endl;

    std::cout << "\n========================================" << std::endl;
    std::cout << "ALL OPENT9 CORE C++ TESTS PASSED!" << std::endl;
    std::cout << "========================================" << std::endl;
    return 0;
}
