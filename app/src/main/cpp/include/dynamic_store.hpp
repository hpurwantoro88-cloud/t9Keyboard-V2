#pragma once
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <shared_mutex>
#include <string>
#include <unordered_map>
#include <vector>

constexpr size_t MAX_STORE_WORDS = 4096;
constexpr size_t MAX_STORE_BIGRAMS = 4096;
constexpr size_t MAX_STORE_WORD_LEN = 48;

struct DynamicEntry {
    char word[MAX_STORE_WORD_LEN];
    uint32_t base_freq;
    uint32_t hit_count;
    uint64_t last_used_timestamp; // Unix timestamp in seconds
    uint8_t is_deleted;
};

struct DynamicBigramEntry {
    char prev_word[MAX_STORE_WORD_LEN];
    char next_word[MAX_STORE_WORD_LEN];
    uint32_t hit_count;
    uint64_t last_used_timestamp;
};

/**
 * Thread-safe dynamic user vocabulary and personal bigram store with in-memory
 * T9 prefix indexing and non-blocking snapshot persistence via flushIfDirty().
 */
class DynamicStore {
public:
    DynamicStore();
    ~DynamicStore();

    bool init(const char* filePath);
    bool save();
    bool flushIfDirty();

    bool addWord(const char* word, uint32_t freq, uint64_t nowSec);
    bool recordUsage(const char* word, uint64_t nowSec, uint32_t weight = 1);
    bool removeWord(const char* word);
    void reset();

    bool recordBigram(const char* prevWord, const char* nextWord, uint64_t nowSec);

    struct DynamicMatch {
        char word[MAX_STORE_WORD_LEN];
        uint8_t length;
        uint32_t effective_freq;
        uint32_t hit_count;
        uint64_t last_used_timestamp;
        float decay_factor;
        bool is_terminal;
    };

    struct DynamicBigramMatch {
        char word[MAX_STORE_WORD_LEN];
        uint8_t length;
        float score;
        uint32_t hit_count;
    };

    uint32_t getEffectiveFrequency(const char* word, uint64_t nowSec, uint32_t halfLifeDays) const;
    uint32_t getHitCount(const char* word) const;
    bool isCustomWord(const char* word) const;
    bool isDeleted(const char* word) const;

    int findMatchingWords(const int* digits, int length, uint64_t nowSec, uint32_t halfLifeDays,
                          DynamicMatch* outMatches, int maxMatches) const;

    int findNextWords(const char* prevWord, uint64_t nowSec, uint32_t halfLifeDays,
                      DynamicBigramMatch* outMatches, int maxMatches) const;

    float getBigramBoost(const char* prevWord, const char* nextWord,
                         uint64_t nowSec, uint32_t halfLifeDays) const;

    size_t getWordCount() const;
    int serializeAllWords(uint8_t* outBuffer, int maxBytes) const;

private:
    static constexpr int MAX_INDEXED_PREFIX_LEN = 12;

    std::string dbPath;
    std::string bigramPath;
    std::vector<DynamicEntry> entries;
    std::vector<DynamicBigramEntry> bigrams;

    std::unordered_map<std::string, size_t> wordToIndex;
    std::unordered_map<uint64_t, std::vector<size_t>> prefixIndex;
    std::unordered_map<std::string, std::vector<size_t>> bigramByPrev;

    mutable std::shared_mutex storeMutex;
    std::mutex ioMutex;
    std::atomic<bool> dirty{false};

    int findEntryIndexLocked(const char* word) const;
    void indexEntryLocked(size_t entryIdx);
    void unindexEntryLocked(size_t entryIdx);
    void evictOneEntryLocked();
    bool upsertWordLocked(const char* canonical, uint32_t freq, uint64_t nowSec, uint32_t hitWeight);
    void rebuildIndicesLocked();
    void rebuildBigramIndexLocked();

    static bool writeUnigramsToDisk(const std::string& path, const std::vector<DynamicEntry>& snapshot);
    static bool writeBigramsToDisk(const std::string& path, const std::vector<DynamicBigramEntry>& snapshot);
};
