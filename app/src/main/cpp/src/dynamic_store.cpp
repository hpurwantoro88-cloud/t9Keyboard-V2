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

bool DynamicStore::save() {
    if (dbPath.empty()) return false;

    std::string tmpPath = dbPath + ".tmp";
    std::ofstream file(tmpPath, std::ios::binary | std::ios::trunc);
    if (!file.is_open()) {
        return false;
    }

    // Filter out deleted entries
    std::vector<DynamicEntry> validEntries;
    validEntries.reserve(entries.size());
    for (const auto& entry : entries) {
        if (!entry.is_deleted) {
            validEntries.push_back(entry);
        }
    }

    uint32_t count = static_cast<uint32_t>(validEntries.size());
    file.write(reinterpret_cast<const char*>(&count), sizeof(count));
    if (count > 0) {
        file.write(reinterpret_cast<const char*>(validEntries.data()), count * sizeof(DynamicEntry));
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

bool DynamicStore::addWord(const char* word, uint32_t freq, uint64_t nowSec) {
    if (!word || word[0] == '\0') return false;
    size_t len = std::strlen(word);
    if (len >= MAX_STORE_WORD_LEN) return false;

    int idx = findEntryIndex(word);
    if (idx >= 0) {
        entries[idx].hit_count++;
        entries[idx].base_freq = std::max(entries[idx].base_freq, freq);
        entries[idx].last_used_timestamp = nowSec;
        entries[idx].is_deleted = 0;
        return true;
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
    return true;
}

bool DynamicStore::recordUsage(const char* word, uint64_t nowSec) {
    int idx = findEntryIndex(word);
    if (idx >= 0) {
        entries[idx].hit_count++;
        entries[idx].last_used_timestamp = nowSec;
        return true;
    }
    return addWord(word, 100, nowSec);
}

bool DynamicStore::removeWord(const char* word) {
    int idx = findEntryIndex(word);
    if (idx >= 0) {
        entries[idx].is_deleted = 1;
        return true;
    }
    return false;
}

void DynamicStore::reset() {
    entries.clear();
    save();
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
