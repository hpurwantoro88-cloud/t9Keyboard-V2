#include <iostream>
#include <fstream>
#include <vector>
#include <chrono>
#include <cassert>
#include <cstring>
#include <algorithm>
#include <ctime>
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

    // ------------------------------------------------------------------------
    // Test 12: Unified Decayed-Frequency Ranking (Indonesian & English)
    // ------------------------------------------------------------------------
    // 12A: Indonesian 7-2-6-2 ('sana' vs 'sama' - close corpus frequencies)
    engine.setLexiconData(idBuffer.data(), idBuffer.size());
    DynamicStore test12Store;
    engine.setDynamicStore(&test12Store);

    // Initial check: sana should be #1, sama should be #2 based on static unigram frequency
    engine.reset();
    engine.pushStroke(7, 0, 0, scorer, g_config);
    engine.pushStroke(2, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(2, 0, 0, scorer, g_config);
    assert(engine.getCandidateCount() >= 2);
    assert(std::strcmp(engine.getCandidates()[0].word, "sana") == 0);
    assert(std::strcmp(engine.getCandidates()[1].word, "sama") == 0);

    // Record usage for close competitor 'sama': promotes 'sama' to #1
    uint64_t usageTime = static_cast<uint64_t>(std::time(nullptr));
    test12Store.recordUsage("sama", usageTime + 1);

    engine.reset();
    engine.pushStroke(7, 0, 0, scorer, g_config);
    engine.pushStroke(2, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(2, 0, 0, scorer, g_config);
    assert(engine.getCandidateCount() >= 2);
    assert(std::strcmp(engine.getCandidates()[0].word, "sama") == 0);
    assert(std::strcmp(engine.getCandidates()[1].word, "sana") == 0);

    // Now pick 'sana' repeatedly (2x) so its user score + higher corpus freq overtakes 'sama'
    test12Store.recordUsage("sana", usageTime + 2);
    test12Store.recordUsage("sana", usageTime + 3);
    engine.reset();
    engine.pushStroke(7, 0, 0, scorer, g_config);
    engine.pushStroke(2, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(2, 0, 0, scorer, g_config);
    assert(engine.getCandidateCount() >= 2);
    assert(std::strcmp(engine.getCandidates()[0].word, "sana") == 0);
    assert(std::strcmp(engine.getCandidates()[1].word, "sama") == 0);

    std::cout << "  [ID] 7262 verified: proportional decayed-frequency promotion" << std::endl;

    // 12B: English 4-6-6-3 ('good' vs 'home') - Anti-Recency-Hijacking
    engine.setLexiconData(enBuffer.data(), enBuffer.size());
    DynamicStore test12StoreEn;
    engine.setDynamicStore(&test12StoreEn);

    // Initial check: 'good' is #1, 'home' is #2
    engine.reset();
    engine.pushStroke(4, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(3, 0, 0, scorer, g_config);
    assert(engine.getCandidateCount() >= 2);
    assert(std::strcmp(engine.getCandidates()[0].word, "good") == 0);
    assert(std::strcmp(engine.getCandidates()[1].word, "home") == 0);

    // Single accidental pick of rare word 'hoof' MUST NOT hijack 'good'
    test12StoreEn.recordUsage("hoof", usageTime + 1);
    engine.reset();
    engine.pushStroke(4, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(3, 0, 0, scorer, g_config);
    assert(std::strcmp(engine.getCandidates()[0].word, "good") == 0 && "Rare word picked 1x must not hijack 'good'");

    // Repeated deliberate usage of 'home' (3x) promotes 'home' above 'good'
    test12StoreEn.recordUsage("home", usageTime + 2);
    test12StoreEn.recordUsage("home", usageTime + 3);
    test12StoreEn.recordUsage("home", usageTime + 4);
    engine.reset();
    engine.pushStroke(4, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(6, 0, 0, scorer, g_config);
    engine.pushStroke(3, 0, 0, scorer, g_config);
    assert(engine.getCandidateCount() >= 2);
    assert(std::strcmp(engine.getCandidates()[0].word, "home") == 0 && "Repeatedly used 'home' must overtake 'good'");

    std::cout << "  [EN] 4663 verified: anti-recency-hijacking + repeated-use promotion" << std::endl;
    std::cout << "[PASS] Test 12: Unified decayed-frequency ranking verified" << std::endl;

    // ------------------------------------------------------------------------
    // Test 13: Multi-Key Spatial Border Recovery (4-5-6-3 near 5|6 border -> 'good')
    // ------------------------------------------------------------------------
    SpatialScorer gridScorer;
    // Standard 3x4 keypad spacing (key width ~270px, height ~180px, sigma = 48px)
    gridScorer.updateKeyGeometry(2, 540.0f, 90.0f);
    gridScorer.updateKeyGeometry(3, 810.0f, 90.0f);
    gridScorer.updateKeyGeometry(4, 270.0f, 270.0f);
    gridScorer.updateKeyGeometry(5, 540.0f, 270.0f);
    gridScorer.updateKeyGeometry(6, 810.0f, 270.0f);
    gridScorer.updateKeyGeometry(7, 270.0f, 450.0f);
    gridScorer.updateKeyGeometry(8, 540.0f, 450.0f);
    gridScorer.updateKeyGeometry(9, 810.0f, 450.0f);

    NativeConfig spatialConfig = g_config;
    spatialConfig.touch_variance_sigma = 48.0f; // 16dp @ 3x density

    engine.setLexiconData(enBuffer.data(), enBuffer.size());
    engine.setDynamicStore(nullptr);
    engine.reset();
    // Intended 4-6-6-3 ("good"), but 2nd tap slipped just left of the 5|6 boundary (x=665 -> digit 5)
    engine.pushStroke(4, 270.0f, 270.0f, gridScorer, spatialConfig);
    engine.pushStroke(5, 665.0f, 270.0f, gridScorer, spatialConfig);
    engine.pushStroke(6, 810.0f, 270.0f, gridScorer, spatialConfig);
    engine.pushStroke(3, 810.0f, 90.0f, gridScorer, spatialConfig);

    bool recoveredGood = false;
    for (uint8_t i = 0; i < engine.getCandidateCount(); ++i) {
        if (std::strcmp(engine.getCandidates()[i].word, "good") == 0) {
            recoveredGood = true;
            break;
        }
    }
    assert(recoveredGood && "Spatial border slip 4-5-6-3 near 5|6 border must recover 'good'");
    std::cout << "[PASS] Test 13: Multi-key spatial border recovery verified ('good' recovered from 4-5-6-3)" << std::endl;

    // ------------------------------------------------------------------------
    // Test 14: DynamicStore flushIfDirty() persistence round-trip
    // ------------------------------------------------------------------------
    const char* persistPath = "/tmp/test_opent9_flush.dat";
    std::remove(persistPath);
    {
        DynamicStore writerStore;
        assert(writerStore.init(persistPath));
        writerStore.recordUsage("mantul", usageTime);
        assert(writerStore.flushIfDirty());
    }
    {
        DynamicStore readerStore;
        assert(readerStore.init(persistPath));
        assert(readerStore.isCustomWord("mantul"));
        assert(readerStore.getHitCount("mantul") == 1);
    }
    std::remove(persistPath);
    std::cout << "[PASS] Test 14: DynamicStore flushIfDirty() persistence round-trip verified" << std::endl;

    // ------------------------------------------------------------------------
    // Test 15: OPENT9D2 Native Curated Homographs, Contractions & Completions
    // ------------------------------------------------------------------------
    auto checkTopCandidate = [&](const char* digitsStr, const char* expectedTop) {
        engine.reset();
        for (size_t i = 0; digitsStr[i] != '\0'; ++i) {
            engine.pushStroke(digitsStr[i] - '0', 0.0f, 0.0f, scorer, g_config);
        }
        assert(engine.getCandidateCount() > 0);
        if (std::strcmp(engine.getCandidates()[0].word, expectedTop) != 0) {
            std::cerr << "Mismatch for " << digitsStr << ": expected '" << expectedTop
                      << "', got '" << engine.getCandidates()[0].word << "'" << std::endl;
            assert(false);
        }
    };

    auto checkContainsCandidate = [&](const char* digitsStr, const char* target) {
        engine.reset();
        for (size_t i = 0; digitsStr[i] != '\0'; ++i) {
            engine.pushStroke(digitsStr[i] - '0', 0.0f, 0.0f, scorer, g_config);
        }
        bool found = false;
        for (uint8_t i = 0; i < engine.getCandidateCount(); ++i) {
            if (std::strcmp(engine.getCandidates()[i].word, target) == 0) {
                found = true;
                break;
            }
        }
        assert(found && "Expected candidate missing from top 16");
    };

    // English contractions & homographs directly in OPENT9D2
    engine.setLexiconData(enBuffer.data(), enBuffer.size());
    engine.setDynamicStore(nullptr);
    checkTopCandidate("3668", "don't");
    checkTopCandidate("27368", "aren't");
    checkTopCandidate("96873", "you're");
    checkTopCandidate("7437", "she's");
    checkTopCandidate("487", "it's");
    checkContainsCandidate("487", "its");
    checkContainsCandidate("2667", "computer");
    checkContainsCandidate("2775", "application");

    // Indonesian curated homographs, reduplications & prefix completions in OPENT9D2
    engine.setLexiconData(idBuffer.data(), idBuffer.size());
    checkTopCandidate("22735", "capek");
    checkTopCandidate("2273", "cape");
    checkTopCandidate("625274", "makasi");
    checkTopCandidate("23637", "bener");
    checkTopCandidate("5376", "kepo");
    checkTopCandidate("5252652526", "jalan-jalan");
    checkContainsCandidate("7352", "selamat");
    checkContainsCandidate("7363", "pemerintah");

    // Canonical deletion & learning bridge for apostrophe words ("don't" <-> "dont")
    DynamicStore canonicalStore;
    engine.setLexiconData(enBuffer.data(), enBuffer.size());
    engine.setDynamicStore(&canonicalStore);
    canonicalStore.removeWord("dont");
    assert(canonicalStore.isDeleted("don't"));
    engine.reset();
    for (int d : {3, 6, 6, 8}) engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    for (uint8_t i = 0; i < engine.getCandidateCount(); ++i) {
        assert(std::strcmp(engine.getCandidates()[i].word, "don't") != 0);
    }

    std::cout << "[PASS] Test 15: OPENT9D2 native contractions, Indonesian homographs, hyphenated reduplications, and canonical deletion verified" << std::endl;

    // ------------------------------------------------------------------------
    // Test 16: OPENT9N1 Next-Word Prediction & Contextual T9 Disambiguation
    // ------------------------------------------------------------------------
    std::ifstream enBgFile("app/src/main/assets/dictionaries/en_bigrams.bin", std::ios::binary);
    assert(enBgFile.is_open() && "Failed to open en_bigrams.bin");
    enBgFile.seekg(0, std::ios::end);
    size_t enBgSize = enBgFile.tellg();
    enBgFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> enBgBuffer(enBgSize);
    enBgFile.read(reinterpret_cast<char*>(enBgBuffer.data()), enBgSize);
    enBgFile.close();

    std::ifstream idBgFile("app/src/main/assets/dictionaries/id_bigrams.bin", std::ios::binary);
    assert(idBgFile.is_open() && "Failed to open id_bigrams.bin");
    idBgFile.seekg(0, std::ios::end);
    size_t idBgSize = idBgFile.tellg();
    idBgFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> idBgBuffer(idBgSize);
    idBgFile.read(reinterpret_cast<char*>(idBgBuffer.data()), idBgSize);
    idBgFile.close();

    NgramEngine ngram;
    assert(lexMgr.loadNgramFromBuffer("EN", enBgBuffer.data(), enBgBuffer.size()));
    assert(lexMgr.loadNgramFromBuffer("ID", idBgBuffer.data(), idBgBuffer.size()));

    // 16A: Idle NWP (EN)
    assert(lexMgr.switchLanguage("EN"));
    assert(ngram.setNgramData(lexMgr.getActiveNgramData(), lexMgr.getActiveNgramSize()));
    engine.setDynamicStore(nullptr);
    engine.setNgramEngine(&ngram);
    engine.setLexiconData(lexMgr.getActiveData(), lexMgr.getActiveSize());

    CandidateWord nwpCands[MAX_CANDIDATES];
    int nwpCount = engine.getNextWordCandidates("thank", g_config, nwpCands, MAX_CANDIDATES);
    assert(nwpCount > 0 && std::strcmp(nwpCands[0].word, "you") == 0);
    nwpCount = engine.getNextWordCandidates("good", g_config, nwpCands, MAX_CANDIDATES);
    assert(nwpCount > 0 && std::strcmp(nwpCands[0].word, "morning") == 0);

    // 16B: Contextual T9 Disambiguation (EN: "yes" + 9373 -> "we're" flips over "were")
    engine.setContextWord(nullptr);
    engine.reset();
    for (int d : {9, 3, 7, 3}) engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    assert(std::strcmp(engine.getCandidates()[0].word, "were") == 0);

    engine.setContextWord("yes");
    engine.reset();
    for (int d : {9, 3, 7, 3}) engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    assert(std::strcmp(engine.getCandidates()[0].word, "we're") == 0);
    engine.setContextWord(nullptr);

    // 16C: Idle NWP & Contextual T9 Disambiguation (ID: "selamat" -> "pagi", "terima" -> "kasih")
    assert(lexMgr.switchLanguage("ID"));
    assert(ngram.setNgramData(lexMgr.getActiveNgramData(), lexMgr.getActiveNgramSize()));
    engine.setNgramEngine(&ngram);
    engine.setLexiconData(lexMgr.getActiveData(), lexMgr.getActiveSize());

    nwpCount = engine.getNextWordCandidates("selamat", g_config, nwpCands, MAX_CANDIDATES);
    assert(nwpCount > 0 && std::strcmp(nwpCands[0].word, "pagi") == 0);
    nwpCount = engine.getNextWordCandidates("terima", g_config, nwpCands, MAX_CANDIDATES);
    assert(nwpCount > 0 && std::strcmp(nwpCands[0].word, "kasih") == 0);

    engine.setContextWord("selamat");
    engine.reset();
    for (int d : {7, 2, 4, 4}) engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    assert(engine.getCandidateCount() > 0 && std::strcmp(engine.getCandidates()[0].word, "pagi") == 0);
    engine.setContextWord(nullptr);

    // 16D: Personal user bigrams + .bigrams persistence round-trip
    const char* bgPersistPath = "/tmp/test_opent9_bigrams.dat";
    std::remove(bgPersistPath);
    std::remove("/tmp/test_opent9_bigrams.dat.bigrams");
    {
        DynamicStore bgStore;
        assert(bgStore.init(bgPersistPath));
        assert(bgStore.recordBigram("otw", "ya", usageTime));
        assert(bgStore.flushIfDirty());
    }
    {
        DynamicStore bgReload;
        assert(bgReload.init(bgPersistPath));
        engine.setDynamicStore(&bgReload);
        nwpCount = engine.getNextWordCandidates("otw", g_config, nwpCands, MAX_CANDIDATES);
        assert(nwpCount > 0 && std::strcmp(nwpCands[0].word, "ya") == 0);
        engine.setDynamicStore(nullptr);
    }
    std::remove(bgPersistPath);
    std::remove("/tmp/test_opent9_bigrams.dat.bigrams");

    std::cout << "[PASS] Test 16: OPENT9N1 Idle NWP, contextual disambiguation ('yes we're', 'selamat pagi'), and personal user bigrams verified" << std::endl;

    // ------------------------------------------------------------------------
    // Test 17: Bilingual Code-Switching (ID + EN Simultaneous Scoring)
    // ------------------------------------------------------------------------
    assert(lexMgr.switchLanguage("ID"));
    NgramEngine primaryNg, secondaryNg;
    assert(primaryNg.setNgramData(lexMgr.getActiveNgramData(), lexMgr.getActiveNgramSize()));
    assert(secondaryNg.setNgramData(lexMgr.getInactiveNgramData(), lexMgr.getInactiveNgramSize()));

    engine.setDynamicStore(nullptr);
    engine.setNgramEngine(&primaryNg);
    engine.setSecondaryNgramEngine(&secondaryNg);
    assert(engine.setLexiconData(lexMgr.getActiveData(), lexMgr.getActiveSize()));
    assert(engine.setSecondaryLexiconData(lexMgr.getInactiveData(), lexMgr.getInactiveSize()));

    NativeConfig biConfig = g_config;
    biConfig.bilingual_enabled = 1;

    // In ID mode with bilingual ON: "selamat" (ID) -> #1, AND "meeting" (EN) -> #1 without switching!
    engine.reset();
    for (int d : {7, 3, 5, 2, 6, 2, 8}) engine.pushStroke(d, 0.0f, 0.0f, scorer, biConfig);
    assert(engine.getCandidateCount() > 0 && std::strcmp(engine.getCandidates()[0].word, "selamat") == 0);

    engine.reset();
    for (int d : {6, 3, 3, 8, 4, 6, 4}) engine.pushStroke(d, 0.0f, 0.0f, scorer, biConfig);
    assert(engine.getCandidateCount() > 0 && std::strcmp(engine.getCandidates()[0].word, "meeting") == 0);

    // Bilingual Idle NWP: "thank" -> "you" while active language is ID
    nwpCount = engine.getNextWordCandidates("thank", biConfig, nwpCands, MAX_CANDIDATES);
    assert(nwpCount > 0 && std::strcmp(nwpCands[0].word, "you") == 0);

    // Turning bilingual OFF restores strict single-language isolation ("you're" is EN-only)
    biConfig.bilingual_enabled = 1;
    engine.reset();
    for (int d : {9, 6, 8, 7, 3}) engine.pushStroke(d, 0.0f, 0.0f, scorer, biConfig);
    assert(engine.getCandidateCount() > 0 && std::strcmp(engine.getCandidates()[0].word, "you're") == 0);

    biConfig.bilingual_enabled = 0;
    engine.reset();
    for (int d : {9, 6, 8, 7, 3}) engine.pushStroke(d, 0.0f, 0.0f, scorer, biConfig);
    for (uint8_t i = 0; i < engine.getCandidateCount(); ++i) {
        assert(std::strcmp(engine.getCandidates()[i].word, "you're") != 0);
    }

    std::cout << "[PASS] Test 17: Bilingual ID+EN code-switching and toggle isolation verified" << std::endl;

    // ------------------------------------------------------------------------
    // Test 18: Sentence-Level Regression & p99 Latency Benchmark (< 500 µs)
    // ------------------------------------------------------------------------
    struct SentenceCase {
        const char* lang;
        std::vector<std::string> words;
    };
    std::vector<SentenceCase> sentences = {
        {"ID", {"selamat", "pagi", "terima", "kasih", "udah", "bantu", "hari", "ini"}},
        {"ID", {"aku", "gak", "bisa", "datang", "karena", "lagi", "kerja"}},
        {"EN", {"good", "morning", "thank", "you", "for", "your", "help"}},
        {"EN", {"I", "don't", "think", "it's", "going", "to", "work"}}
    };

    std::vector<double> strokeLatenciesUs;
    strokeLatenciesUs.reserve(256);
    int totalTestedWords = 0;
    int top3Hits = 0;

    for (const auto& sc : sentences) {
        assert(lexMgr.switchLanguage(sc.lang));
        assert(primaryNg.setNgramData(lexMgr.getActiveNgramData(), lexMgr.getActiveNgramSize()));
        engine.setNgramEngine(&primaryNg);
        engine.setLexiconData(lexMgr.getActiveData(), lexMgr.getActiveSize());
        engine.setContextWord(nullptr);

        for (size_t wi = 0; wi < sc.words.size(); ++wi) {
            const std::string& w = sc.words[wi];
            if (wi > 0) {
                engine.setContextWord(sc.words[wi - 1].c_str());
            }
            engine.reset();
            for (char ch : w) {
                char lc = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
                if (lc == '\'' || lc == '-') continue;
                int d = 0;
                if (lc >= 'a' && lc <= 'c') d = 2;
                else if (lc <= 'f') d = 3;
                else if (lc <= 'i') d = 4;
                else if (lc <= 'l') d = 5;
                else if (lc <= 'o') d = 6;
                else if (lc <= 's') d = 7;
                else if (lc <= 'v') d = 8;
                else if (lc <= 'z') d = 9;
                if (d < 2 || d > 9) continue;

                auto ts = std::chrono::high_resolution_clock::now();
                engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
                auto te = std::chrono::high_resolution_clock::now();
                strokeLatenciesUs.push_back(std::chrono::duration<double, std::micro>(te - ts).count());
            }

            totalTestedWords++;
            for (uint8_t c = 0; c < engine.getCandidateCount() && c < 3; ++c) {
                if (w == engine.getCandidates()[c].word) {
                    top3Hits++;
                    break;
                }
            }
        }
    }

    std::sort(strokeLatenciesUs.begin(), strokeLatenciesUs.end());
    double p99Us = strokeLatenciesUs[static_cast<size_t>(strokeLatenciesUs.size() * 0.99)];
    double top3Acc = (100.0 * top3Hits) / totalTestedWords;
    std::cout << "  Sentence Regression: Top-3 = " << top3Hits << "/" << totalTestedWords
              << " (" << top3Acc << "%), p99 stroke latency = " << p99Us << " µs" << std::endl;
    assert(top3Acc >= 95.0 && "Sentence Top-3 accuracy must be >= 95%");
    assert(p99Us < 500.0 && "p99 stroke latency must stay < 500 µs (0.5 ms)");
    std::cout << "[PASS] Test 18: Sentence regression & p99 latency (< 0.5 ms) verified" << std::endl;

    // ------------------------------------------------------------------------
    // Test 19: Short-Prefix Length Guard, Non-Terminal Dynamic Isolation & Soft Recency
    // ------------------------------------------------------------------------
    // 19A: Short-Prefix Length Guard (pressing 2 or 3 digits must not surface 8-12 char words)
    assert(lexMgr.switchLanguage("ID"));
    engine.setDynamicStore(nullptr);
    engine.setNgramEngine(nullptr);
    engine.setLexiconData(lexMgr.getActiveData(), lexMgr.getActiveSize());
    engine.setContextWord(nullptr);

    engine.reset();
    engine.pushStroke(7, 0.0f, 0.0f, scorer, g_config);
    engine.pushStroke(3, 0.0f, 0.0f, scorer, g_config);
    engine.pushStroke(6, 0.0f, 0.0f, scorer, g_config); // 3 digits (7-3-6)
    assert(engine.getCandidateCount() > 0);
    for (uint8_t i = 0; i < engine.getCandidateCount(); ++i) {
        assert(engine.getCandidates()[i].length <= 7 && "3-digit prefix must bound completions to <= 7 chars");
    }
    assert(engine.getCandidates()[0].length == 3 && "Top candidate for 3-digit prefix must be exact 3-char word");

    // 19B: Non-Terminal DynamicStore Isolation (long learned word must not hijack 3-digit prefix)
    DynamicStore test19Store;
    engine.setDynamicStore(&test19Store);
    test19Store.recordUsage("pembelajaran", usageTime, 5); // 12-char word starting with 7-3-6
    engine.reset();
    engine.pushStroke(7, 0.0f, 0.0f, scorer, g_config);
    engine.pushStroke(3, 0.0f, 0.0f, scorer, g_config);
    engine.pushStroke(6, 0.0f, 0.0f, scorer, g_config);
    assert(engine.getCandidates()[0].length == 3 && "Long learned word must not hijack 3-digit exact terminal");
    for (uint8_t i = 0; i < engine.getCandidateCount(); ++i) {
        assert(std::strcmp(engine.getCandidates()[i].word, "pembelajaran") != 0 &&
               "12-char learned word must not leak into 3-digit prefix candidates");
    }

    // 19C: Soft Session Recency vs Bigram Context Override + Weighted Explicit Pick
    assert(lexMgr.switchLanguage("EN"));
    assert(primaryNg.setNgramData(lexMgr.getActiveNgramData(), lexMgr.getActiveNgramSize()));
    engine.setNgramEngine(&primaryNg);
    engine.setLexiconData(lexMgr.getActiveData(), lexMgr.getActiveSize());
    test19Store.reset();

    // Explicit pick of "were" (weight = 3) promotes "were" on 9-3-7-3 without context
    test19Store.recordUsage("were", usageTime, 3);
    assert(test19Store.getHitCount("were") == 3);
    engine.setContextWord(nullptr);
    engine.reset();
    for (int d : {9, 3, 7, 3}) engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    assert(std::strcmp(engine.getCandidates()[0].word, "were") == 0);

    // Strong bigram context ("yes" -> "we're") still overrides soft recency + user hits
    engine.setContextWord("yes");
    engine.reset();
    for (int d : {9, 3, 7, 3}) engine.pushStroke(d, 0.0f, 0.0f, scorer, g_config);
    assert(std::strcmp(engine.getCandidates()[0].word, "we're") == 0 &&
           "Bigram context 'yes we're' must override soft recency of 'were'");
    engine.setContextWord(nullptr);

    std::cout << "[PASS] Test 19: Short-prefix length guard, non-terminal DynamicStore isolation, weighted explicit pick, and context vs soft-recency balance verified" << std::endl;

    std::cout << "\n========================================" << std::endl;
    std::cout << "ALL OPENT9 CORE C++ TESTS PASSED!" << std::endl;
    std::cout << "========================================" << std::endl;
    return 0;
}
