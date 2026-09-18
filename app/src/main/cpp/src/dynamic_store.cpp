#include "dynamic_store.hpp"
#include <fstream>
#include <cmath>
#include <cstring>
#include <algorithm>

DynamicStore::DynamicStore() {
    entries.reserve(256);
}

DynamicStore::~DynamicStore() {
    save();
}

bool DynamicStore::init(const char* filePath) {
    if (!filePath) return false;
    dbPath = filePath;
    entries.clear();

    std::ifstream file(filePath, std::ios::binary);
    if (!file.is_open()) {
        return true; // Empty / new database
    }

    uint32_t count = 0;
    file.read(reinterpret_cast<char*>(&count), sizeof(count));
    if (file.gcount() != sizeof(count) || count > MAX_STORE_WORDS) {
        return false;
    }

    entries.resize(count);
    file.read(reinterpret_cast<char*>(entries.data()), count * sizeof(DynamicEntry));
    if (file.gcount() != static_cast<std::streamsize>(count * sizeof(DynamicEntry))) {
        entries.clear();
        return false;
    }

    return true;
}

inline uint8_t charToT9Digit(char c) {
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

bool DynamicStore::save() {
    if (dbPath.empty()) return false;

    std::string tmpPath = dbPath + ".tmp";
    std::ofstream file(tmpPath, std::ios::binary | std::ios::trunc);
    if (!file.is_open()) {
        return false;
    }

    uint32_t count = static_cast<uint32_t>(entries.size());
    file.write(reinterpret_cast<const char*>(&count), sizeof(count));
    if (count > 0) {
        file.write(reinterpret_cast<const char*>(entries.data()), count * sizeof(DynamicEntry));
    }
    file.close();

    std::rename(tmpPath.c_str(), dbPath.c_str());
    return true;
}

int DynamicStore::findEntryIndex(const char* word) const {
    if (!word) return -1;
    for (size_t i = 0; i < entries.size(); ++i) {
        if (!entries[i].is_deleted && std::strcmp(entries[i].word, word) == 0) {
            return static_cast<int>(i);
        }
    }
    return -1;
}

bool DynamicStore::isDeleted(const char* word) const {
    if (!word) return false;
    for (size_t i = 0; i < entries.size(); ++i) {
        if (std::strcmp(entries[i].word, word) == 0) {
            return entries[i].is_deleted != 0;
        }
    }
    return false;
}

bool DynamicStore::addWord(const char* word, uint32_t freq, uint64_t nowSec) {
    if (!word || word[0] == '\0') return false;
    size_t len = std::strlen(word);
    if (len >= MAX_STORE_WORD_LEN) return false;

    for (size_t i = 0; i < entries.size(); ++i) {
        if (std::strcmp(entries[i].word, word) == 0) {
            entries[i].hit_count++;
            entries[i].base_freq = std::max(entries[i].base_freq, freq);
            entries[i].last_used_timestamp = nowSec;
            entries[i].is_deleted = 0;
            save();
            return true;
        }
    }

    if (entries.size() >= MAX_STORE_WORDS) {
        // Purge oldest entry
        size_t oldestIdx = 0;
        uint64_t oldestTime = UINT64_MAX;
        for (size_t i = 0; i < entries.size(); ++i) {
            if (entries[i].last_used_timestamp < oldestTime) {
                oldestTime = entries[i].last_used_timestamp;
                oldestIdx = i;
            }
        }
        entries.erase(entries.begin() + oldestIdx);
    }

    DynamicEntry entry;
    std::memset(&entry, 0, sizeof(entry));
    std::strncpy(entry.word, word, MAX_STORE_WORD_LEN - 1);
    entry.base_freq = freq;
    entry.hit_count = 1;
    entry.last_used_timestamp = nowSec;
    entry.is_deleted = 0;
    entries.push_back(entry);
    save();
    return true;
}

bool DynamicStore::recordUsage(const char* word, uint64_t nowSec) {
    if (!word || word[0] == '\0') return false;
    for (size_t i = 0; i < entries.size(); ++i) {
        if (std::strcmp(entries[i].word, word) == 0) {
            entries[i].is_deleted = 0;
            entries[i].hit_count++;
            entries[i].last_used_timestamp = nowSec;
            save();
            return true;
        }
    }
    return addWord(word, 100, nowSec);
}

bool DynamicStore::removeWord(const char* word) {
    if (!word || word[0] == '\0') return false;
    for (size_t i = 0; i < entries.size(); ++i) {
        if (std::strcmp(entries[i].word, word) == 0) {
            entries[i].is_deleted = 1;
            save();
            return true;
        }
    }

    // If not in dynamic store yet (e.g. built-in static word from DAWG),
    // record it as deleted to serve as a persistent suppression blacklist.
    if (entries.size() >= MAX_STORE_WORDS) {
        entries.erase(entries.begin());
    }
    DynamicEntry entry;
    std::memset(&entry, 0, sizeof(entry));
    std::strncpy(entry.word, word, MAX_STORE_WORD_LEN - 1);
    entry.base_freq = 0;
    entry.hit_count = 0;
    entry.last_used_timestamp = 0;
    entry.is_deleted = 1;
    entries.push_back(entry);
    save();
    return true;
}

void DynamicStore::reset() {
    entries.clear();
    save();
}

int DynamicStore::findMatchingWords(const int* digits, int length, uint64_t nowSec, uint32_t halfLifeDays,
                                    DynamicMatch* outMatches, int maxMatches) const {
    if (!digits || length <= 0 || !outMatches || maxMatches <= 0) return 0;

    int matchCount = 0;
    for (const auto& entry : entries) {
        if (entry.is_deleted) continue;
        size_t len = std::strlen(entry.word);
        if (static_cast<int>(len) < length) continue;

        bool matched = true;
        for (int i = 0; i < length; ++i) {
            if (charToT9Digit(entry.word[i]) != digits[i]) {
                matched = false;
                break;
            }
        }
        if (matched) {
            DynamicMatch& m = outMatches[matchCount++];
            std::memcpy(m.word, entry.word, len + 1);
            m.length = static_cast<uint8_t>(len);
            m.effective_freq = getEffectiveFrequency(entry.word, nowSec, halfLifeDays);
            m.is_terminal = (static_cast<int>(len) == length);
            if (matchCount >= maxMatches) break;
        }
    }

    std::sort(outMatches, outMatches + matchCount, [](const DynamicMatch& a, const DynamicMatch& b) {
        if (a.is_terminal != b.is_terminal) return a.is_terminal > b.is_terminal;
        return a.effective_freq > b.effective_freq;
    });

    return matchCount;
}

uint32_t DynamicStore::getEffectiveFrequency(const char* word, uint64_t nowSec, uint32_t halfLifeDays) const {
    int idx = findEntryIndex(word);
    if (idx < 0) return 0;

    const auto& entry = entries[idx];
    if (halfLifeDays == 0) {
        return entry.base_freq + (entry.hit_count * 10);
    }

    uint64_t elapsedSec = (nowSec >= entry.last_used_timestamp) ? (nowSec - entry.last_used_timestamp) : 0;
    double halfLifeSec = static_cast<double>(halfLifeDays) * 86400.0;
    double decayFactor = std::pow(0.5, static_cast<double>(elapsedSec) / halfLifeSec);

    double totalFreq = static_cast<double>(entry.base_freq + (entry.hit_count * 10));
    return static_cast<uint32_t>(totalFreq * decayFactor);
}

bool DynamicStore::isCustomWord(const char* word) const {
    return findEntryIndex(word) >= 0;
}

size_t DynamicStore::getWordCount() const {
    size_t count = 0;
    for (const auto& entry : entries) {
        if (!entry.is_deleted) count++;
    }
    return count;
}

int DynamicStore::serializeAllWords(uint8_t* outBuffer, int maxBytes) const {
    if (!outBuffer || maxBytes < 4) return 0;

    int offset = 0;
    uint32_t count = static_cast<uint32_t>(getWordCount());
    std::memcpy(outBuffer + offset, &count, sizeof(count));
    offset += sizeof(count);

    for (const auto& entry : entries) {
        if (entry.is_deleted) continue;
        uint8_t len = static_cast<uint8_t>(std::strlen(entry.word));
        if (offset + 1 + len + sizeof(entry.base_freq) > static_cast<size_t>(maxBytes)) break;

        outBuffer[offset++] = len;
        std::memcpy(outBuffer + offset, entry.word, len);
        offset += len;
        std::memcpy(outBuffer + offset, &entry.base_freq, sizeof(entry.base_freq));
        offset += sizeof(entry.base_freq);
    }

    return offset;
}
