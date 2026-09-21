#pragma once
#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

constexpr size_t MAX_STORE_WORDS = 4096;
constexpr size_t MAX_STORE_WORD_LEN = 48;

struct DynamicEntry {
    char word[MAX_STORE_WORD_LEN];
    uint32_t base_freq;
    uint32_t hit_count;
    uint64_t last_used_timestamp; // Unix timestamp in seconds
    uint8_t is_deleted;
};

class DynamicStore {
public:
    DynamicStore();
    ~DynamicStore();

    bool init(const char* filePath);
    bool save();

    bool addWord(const char* word, uint32_t freq, uint64_t nowSec);
    bool recordUsage(const char* word, uint64_t nowSec);
    bool removeWord(const char* word);
    void reset();

    struct DynamicMatch {
        char word[MAX_STORE_WORD_LEN];
        uint8_t length;
        uint32_t effective_freq;
        uint32_t hit_count;
        uint64_t last_used_timestamp;
        float decay_factor;
        bool is_terminal;
    };

    uint32_t getEffectiveFrequency(const char* word, uint64_t nowSec, uint32_t halfLifeDays) const;
    uint32_t getHitCount(const char* word) const;
    bool isCustomWord(const char* word) const;
    bool isDeleted(const char* word) const;

    int findMatchingWords(const int* digits, int length, uint64_t nowSec, uint32_t halfLifeDays,
                          DynamicMatch* outMatches, int maxMatches) const;

    size_t getWordCount() const;
    int serializeAllWords(uint8_t* outBuffer, int maxBytes) const;

private:
    std::string dbPath;
    std::vector<DynamicEntry> entries;

    int findEntryIndex(const char* word) const;
};
