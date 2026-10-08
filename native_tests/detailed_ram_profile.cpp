#include <iostream>
#include <fcntl.h>
#include <fstream>
#include <vector>
#include <string>
#include <chrono>
#include <cassert>
#include <cstring>
#include <iomanip>
#include <unistd.h>
#include <malloc.h>
#include "../app/src/main/cpp/include/dawg_engine.hpp"
#include "../app/src/main/cpp/include/spatial_scoring.hpp"
#include "../app/src/main/cpp/include/lexicon_manager.hpp"
#include "../app/src/main/cpp/include/dynamic_store.hpp"
#include "../app/src/main/cpp/include/native_config.hpp"

struct MemorySnapshot {
    long vmRssKb = 0;
    long rssAnonKb = 0;
    long rssFileKb = 0;
    long rssShmemKb = 0;
    long vmSizeKb = 0;
    long vmDataKb = 0;
    size_t heapAllocatedBytes = 0;
};

static MemorySnapshot getMemorySnapshot() {
    MemorySnapshot s;
    std::ifstream status("/proc/self/status");
    std::string line;
    while (std::getline(status, line)) {
        if (line.rfind("VmRSS:", 0) == 0) {
            sscanf(line.c_str(), "VmRSS: %ld kB", &s.vmRssKb);
        } else if (line.rfind("RssAnon:", 0) == 0) {
            sscanf(line.c_str(), "RssAnon: %ld kB", &s.rssAnonKb);
        } else if (line.rfind("RssFile:", 0) == 0) {
            sscanf(line.c_str(), "RssFile: %ld kB", &s.rssFileKb);
        } else if (line.rfind("RssShmem:", 0) == 0) {
            sscanf(line.c_str(), "RssShmem: %ld kB", &s.rssShmemKb);
        } else if (line.rfind("VmSize:", 0) == 0) {
            sscanf(line.c_str(), "VmSize: %ld kB", &s.vmSizeKb);
        } else if (line.rfind("VmData:", 0) == 0) {
            sscanf(line.c_str(), "VmData: %ld kB", &s.vmDataKb);
        }
    }
#if defined(__GLIBC__) && (__GLIBC__ > 2 || (__GLIBC__ == 2 && __GLIBC_MINOR__ >= 33))
    struct mallinfo2 mi = mallinfo2();
    s.heapAllocatedBytes = mi.uordblks;
#endif
    return s;
}

static void printSnapshot(const std::string& label, const MemorySnapshot& s, const MemorySnapshot& base) {
    std::cout << std::left << std::setw(32) << label
              << " | VmRSS: " << std::setw(7) << s.vmRssKb << " KB (Δ " << std::setw(6) << (s.vmRssKb - base.vmRssKb) << " KB)"
              << " | Anon (Dirty): " << std::setw(6) << s.rssAnonKb << " KB"
              << " | File (mmap): " << std::setw(6) << s.rssFileKb << " KB"
              << " | Heap: " << std::setw(6) << (s.heapAllocatedBytes / 1024) << " KB"
              << std::endl;
}

int main() {
    std::cout << "========================================================================================\n";
    std::cout << "   OPENT9 DEEP MEMORY & SPEED TYPING PROFILING ANALYSIS (C++ NATIVE CORE)               \n";
    std::cout << "========================================================================================\n";

    MemorySnapshot initSnap = getMemorySnapshot();
    printSnapshot("1. Process Startup Baseline", initSnap, initSnap);

    LexiconManager lexMgr;
    int enFd = open("app/src/main/assets/dictionaries/en_lexicon.dawg", O_RDONLY);
    assert(enFd >= 0);
    off_t enLen = lseek(enFd, 0, SEEK_END);
    lseek(enFd, 0, SEEK_SET);
    lexMgr.loadFromFd("EN", enFd, 0, enLen);

    int idFd = open("app/src/main/assets/dictionaries/id_lexicon.dawg", O_RDONLY);
    assert(idFd >= 0);
    off_t idLen = lseek(idFd, 0, SEEK_END);
    lseek(idFd, 0, SEEK_SET);
    lexMgr.loadFromFd("ID", idFd, 0, idLen);

    MemorySnapshot postMmap = getMemorySnapshot();
    printSnapshot("2. After mmap EN & ID Lexicons", postMmap, initSnap);

    NativeConfig config;
    SpatialScorer scorer;
    for (int d = 1; d <= 9; ++d) {
        float row = static_cast<float>((d - 1) / 3);
        float col = static_cast<float>((d - 1) % 3);
        scorer.updateKeyGeometry(d, col * 100.0f + 50.0f, row * 100.0f + 50.0f);
    }

    DynamicStore store;
    store.init("/tmp/test_deep_dynamic_store.dat");
    store.reset();

    DawgEngine engine;
    engine.setDynamicStore(&store);
    engine.setLexiconData(lexMgr.getActiveData(), lexMgr.getActiveSize());

    MemorySnapshot postEngineInit = getMemorySnapshot();
    printSnapshot("3. Engine & Store Initialized", postEngineInit, initSnap);

    std::vector<std::pair<std::string, std::vector<int>>> englishCorpus = {
        {"quick", {7, 8, 4, 2, 5}},
        {"brown", {2, 7, 6, 9, 6}},
        {"fox", {3, 6, 9}},
        {"jumps", {5, 8, 6, 7, 7}},
        {"keyboard", {5, 3, 9, 2, 6, 2, 7, 3}},
        {"prediction", {7, 7, 3, 3, 4, 2, 8, 4, 6, 6}},
        {"performance", {7, 3, 7, 3, 6, 7, 6, 2, 6, 2, 3}},
        {"allocation", {2, 5, 5, 6, 2, 2, 8, 4, 6, 6}}
    };

    std::vector<std::pair<std::string, std::vector<int>>> indonesianCorpus = {
        {"selamat", {7, 3, 5, 2, 6, 2, 8}},
        {"malam", {6, 2, 5, 2, 6}},
        {"kemandirian", {5, 3, 6, 2, 6, 3, 4, 7, 4, 2, 6}},
        {"kebudayaan", {5, 3, 2, 8, 3, 2, 9, 2, 2, 6}},
        {"pemerintah", {7, 3, 6, 3, 7, 4, 6, 8, 2, 4}},
        {"kebijakan", {5, 3, 2, 4, 5, 2, 5, 2, 6}},
        {"masyarakat", {6, 2, 7, 9, 2, 7, 2, 5, 2, 8}}
    };

    std::cout << "\n--- SPEED TYPING SIMULATION (100,000 WORDS TYPED) ---\n";
    const int totalWords = 100000;
    uint8_t outBuf[2048];
    uint64_t fakeTime = 1700000000;

    auto tStart = std::chrono::high_resolution_clock::now();

    for (int w = 0; w < totalWords; ++w) {
        if (w % 2000 == 0) {
            bool useId = ((w / 2000) % 2 == 1);
            lexMgr.switchLanguage(useId ? "ID" : "EN");
            engine.setLexiconData(lexMgr.getActiveData(), lexMgr.getActiveSize());
        }

        bool isId = (std::strcmp(lexMgr.getActiveLanguage(), "ID") == 0);
        const auto& wordData = isId ? indonesianCorpus[w % indonesianCorpus.size()]
                                    : englishCorpus[w % englishCorpus.size()];

        engine.reset();
        for (size_t s = 0; s < wordData.second.size(); ++s) {
            int digit = wordData.second[s];
            float cx = static_cast<float>(((digit - 1) % 3) * 100 + 50);
            float cy = static_cast<float>(((digit - 1) / 3) * 100 + 50);
            engine.pushStroke(digit, cx + (w % 5), cy - (w % 3), scorer, config);

            // Periodically backspace
            if (s == 2 && (w % 7 == 0)) {
                engine.popStroke();
                engine.pushStroke(digit, cx, cy, scorer, config);
            }
        }

        // Serialize candidates into direct buffer
        engine.serializeCandidates(outBuf, sizeof(outBuf));

        // Periodically learn word into dynamic store
        if (w % 10 == 0) {
            store.recordUsage(wordData.first.c_str(), fakeTime + w);
        }

        // Memory checkpoint every 25,000 words
        if ((w + 1) % 25000 == 0) {
            std::string label = "   > Typed " + std::to_string(w + 1) + " words";
            MemorySnapshot snap = getMemorySnapshot();
            printSnapshot(label, snap, initSnap);
        }
    }

    auto tEnd = std::chrono::high_resolution_clock::now();
    double totalMs = std::chrono::duration<double, std::milli>(tEnd - tStart).count();

    MemorySnapshot finalSnap = getMemorySnapshot();
    std::cout << "\n--- FINAL MEMORY AUDIT & LATENCY BENCHMARK ---\n";
    printSnapshot("4. Final Test State", finalSnap, initSnap);
    std::cout << "\nKey Metrics:\n";
    std::cout << "  - Total Duration:             " << totalMs << " ms\n";
    std::cout << "  - Throughput:                 " << (totalWords * 1000.0 / totalMs) << " words/sec\n";
    std::cout << "  - Total Words Processed:      " << totalWords << "\n";
    std::cout << "  - Anonymous/Dirty RAM Delta:  " << (finalSnap.rssAnonKb - postEngineInit.rssAnonKb) << " KB\n";
    std::cout << "  - Resident Set Size Delta:    " << (finalSnap.vmRssKb - postEngineInit.vmRssKb) << " KB\n";
    std::cout << "  - Heap Memory Delta:          " << ((long)finalSnap.heapAllocatedBytes - (long)postEngineInit.heapAllocatedBytes) / 1024 << " KB\n";

    close(enFd);
    close(idFd);
    return 0;
}
