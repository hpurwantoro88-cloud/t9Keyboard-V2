#include "dynamic_store.hpp"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <fstream>

namespace {

inline void toCanonicalWord(const char* src, char* dst, size_t maxLen) {
    if (!src || maxLen == 0) {
        if (dst && maxLen > 0) dst[0] = '\0';
        return;
    }
    size_t j = 0;
    for (size_t i = 0; src[i] != '\0' && j + 1 < maxLen; ++i) {
        char c = src[i];
        if (c >= 'A' && c <= 'Z') {
            c = static_cast<char>(c + ('a' - 'A'));
        }
        if (c == '\'' || c == '-') {
            continue;
        }
        // Skip UTF-8 curly apostrophe U+2019 (0xE2 0x80 0x99) and U+2018 (0xE2 0x80 0x98)
        if (static_cast<unsigned char>(c) == 0xE2 &&
            static_cast<unsigned char>(src[i + 1]) == 0x80 &&
            (static_cast<unsigned char>(src[i + 2]) == 0x99 || static_cast<unsigned char>(src[i + 2]) == 0x98)) {
            i += 2;
            continue;
        }
        dst[j++] = c;
    }
    dst[j] = '\0';
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

inline uint64_t packPrefixKey(uint64_t packedDigits, int len) {
    return (static_cast<uint64_t>(len) << 48) | packedDigits;
}

inline double computeDecayFactor(uint64_t nowSec, uint64_t lastUsedSec, uint32_t halfLifeDays) {
    if (halfLifeDays == 0 || nowSec <= lastUsedSec) {
        return 1.0;
    }
    uint64_t elapsedSec = nowSec - lastUsedSec;
    double halfLifeSec = static_cast<double>(halfLifeDays) * 86400.0;
    return std::pow(0.5, static_cast<double>(elapsedSec) / halfLifeSec);
}

inline uint32_t computeEffectiveFreq(const DynamicEntry& entry, double decayFactor) {
    double totalFreq = static_cast<double>(entry.base_freq + (entry.hit_count * 10));
    return static_cast<uint32_t>(totalFreq * decayFactor);
}

inline float computeBigramScore(uint32_t hitCount, double decayFactor) {
    return static_cast<float>(std::log10(1.0 + static_cast<double>(hitCount)) * decayFactor * 2.2);
}

template <typename T>
bool writeVectorFile(const std::string& path, const std::vector<T>& snapshot) {
    if (path.empty()) return false;
    std::string tmpPath = path + ".tmp";
    std::ofstream file(tmpPath, std::ios::binary | std::ios::trunc);
    if (!file.is_open()) return false;

    uint32_t count = static_cast<uint32_t>(snapshot.size());
    file.write(reinterpret_cast<const char*>(&count), sizeof(count));
    if (count > 0) {
        file.write(reinterpret_cast<const char*>(snapshot.data()), count * sizeof(T));
    }
    file.close();
    if (!file) return false;
    return std::rename(tmpPath.c_str(), path.c_str()) == 0;
}

} // namespace

DynamicStore::DynamicStore() {
    entries.reserve(256);
    bigrams.reserve(256);
}

DynamicStore::~DynamicStore() {
    flushIfDirty();
}

bool DynamicStore::init(const char* filePath) {
    if (!filePath) return false;
    std::unique_lock<std::shared_mutex> lock(storeMutex);
    dbPath = filePath;
    bigramPath = dbPath + ".bigrams";
    entries.clear();
    bigrams.clear();
    wordToIndex.clear();
    prefixIndex.clear();
    bigramByPrev.clear();
    dirty.store(false, std::memory_order_release);

    std::ifstream file(filePath, std::ios::binary);
    if (file.is_open()) {
        uint32_t count = 0;
        file.read(reinterpret_cast<char*>(&count), sizeof(count));
        if (file.gcount() != sizeof(count) || count > MAX_STORE_WORDS) {
            return false;
        }
        entries.resize(count);
        if (count > 0) {
            file.read(reinterpret_cast<char*>(entries.data()), count * sizeof(DynamicEntry));
            if (file.gcount() != static_cast<std::streamsize>(count * sizeof(DynamicEntry))) {
                entries.clear();
                return false;
            }
        }
        rebuildIndicesLocked();
    }

    std::ifstream bgFile(bigramPath, std::ios::binary);
    if (bgFile.is_open()) {
        uint32_t bgCount = 0;
        bgFile.read(reinterpret_cast<char*>(&bgCount), sizeof(bgCount));
        if (bgFile.gcount() == sizeof(bgCount) && bgCount <= MAX_STORE_BIGRAMS) {
            bigrams.resize(bgCount);
            if (bgCount > 0) {
                bgFile.read(reinterpret_cast<char*>(bigrams.data()), bgCount * sizeof(DynamicBigramEntry));
                if (bgFile.gcount() != static_cast<std::streamsize>(bgCount * sizeof(DynamicBigramEntry))) {
                    bigrams.clear();
                }
            }
            rebuildBigramIndexLocked();
        }
    }

    return true;
}

bool DynamicStore::writeUnigramsToDisk(const std::string& path, const std::vector<DynamicEntry>& snapshot) {
    return writeVectorFile(path, snapshot);
}

bool DynamicStore::writeBigramsToDisk(const std::string& path, const std::vector<DynamicBigramEntry>& snapshot) {
    return writeVectorFile(path, snapshot);
}

bool DynamicStore::save() {
    std::string uPath, bPath;
    std::vector<DynamicEntry> uSnap;
    std::vector<DynamicBigramEntry> bSnap;
    {
        std::shared_lock<std::shared_mutex> lock(storeMutex);
        if (dbPath.empty()) return false;
        uPath = dbPath;
        bPath = bigramPath;
        uSnap = entries;
        bSnap = bigrams;
    }

    std::lock_guard<std::mutex> ioLock(ioMutex);
    bool ok1 = writeUnigramsToDisk(uPath, uSnap);
    bool ok2 = bPath.empty() ? true : writeBigramsToDisk(bPath, bSnap);
    if (ok1 && ok2) {
        dirty.store(false, std::memory_order_release);
    }
    return ok1 && ok2;
}

bool DynamicStore::flushIfDirty() {
    if (!dirty.load(std::memory_order_acquire)) {
        return true;
    }

    std::string uPath, bPath;
    std::vector<DynamicEntry> uSnap;
    std::vector<DynamicBigramEntry> bSnap;
    {
        std::shared_lock<std::shared_mutex> lock(storeMutex);
        if (dbPath.empty()) return false;
        uPath = dbPath;
        bPath = bigramPath;
        uSnap = entries;
        bSnap = bigrams;
    }

    std::lock_guard<std::mutex> ioLock(ioMutex);
    dirty.store(false, std::memory_order_release);
    bool ok1 = writeUnigramsToDisk(uPath, uSnap);
    bool ok2 = bPath.empty() ? true : writeBigramsToDisk(bPath, bSnap);
    if (!ok1 || !ok2) {
        dirty.store(true, std::memory_order_release);
        return false;
    }
    return true;
}

void DynamicStore::indexEntryLocked(size_t entryIdx) {
    const DynamicEntry& entry = entries[entryIdx];
    wordToIndex[entry.word] = entryIdx;
    if (entry.is_deleted) {
        return;
    }

    uint64_t packed = 0;
    int indexedLen = 0;
    for (size_t i = 0; entry.word[i] != '\0' && indexedLen < MAX_INDEXED_PREFIX_LEN; ++i) {
        uint8_t d = charToT9Digit(entry.word[i]);
        if (d < 2 || d > 9) {
            break;
        }
        packed = (packed << 4) | static_cast<uint64_t>(d);
        indexedLen++;
        uint64_t key = packPrefixKey(packed, indexedLen);
        auto& bucket = prefixIndex[key];
        if (std::find(bucket.begin(), bucket.end(), entryIdx) == bucket.end()) {
            bucket.push_back(entryIdx);
        }
    }
}

void DynamicStore::unindexEntryLocked(size_t entryIdx) {
    const DynamicEntry& entry = entries[entryIdx];
    uint64_t packed = 0;
    int indexedLen = 0;
    for (size_t i = 0; entry.word[i] != '\0' && indexedLen < MAX_INDEXED_PREFIX_LEN; ++i) {
        uint8_t d = charToT9Digit(entry.word[i]);
        if (d < 2 || d > 9) {
            break;
        }
        packed = (packed << 4) | static_cast<uint64_t>(d);
        indexedLen++;
        uint64_t key = packPrefixKey(packed, indexedLen);
        auto it = prefixIndex.find(key);
        if (it != prefixIndex.end()) {
            auto& bucket = it->second;
            bucket.erase(std::remove(bucket.begin(), bucket.end(), entryIdx), bucket.end());
            if (bucket.empty()) {
                prefixIndex.erase(it);
            }
        }
    }
}

void DynamicStore::evictOneEntryLocked() {
    if (entries.empty()) return;
    size_t victimIdx = 0;
    bool foundActive = false;
    uint64_t oldestActiveTime = UINT64_MAX;
    uint64_t oldestAnyTime = UINT64_MAX;
    size_t oldestAnyIdx = 0;

    for (size_t i = 0; i < entries.size(); ++i) {
        if (entries[i].last_used_timestamp < oldestAnyTime) {
            oldestAnyTime = entries[i].last_used_timestamp;
            oldestAnyIdx = i;
        }
        if (!entries[i].is_deleted && entries[i].last_used_timestamp < oldestActiveTime) {
            oldestActiveTime = entries[i].last_used_timestamp;
            victimIdx = i;
            foundActive = true;
        }
    }
    if (!foundActive) {
        victimIdx = oldestAnyIdx;
    }
    entries.erase(entries.begin() + static_cast<std::ptrdiff_t>(victimIdx));
    rebuildIndicesLocked();
}

bool DynamicStore::upsertWordLocked(const char* canonical, uint32_t freq, uint64_t nowSec, uint32_t hitWeight) {
    auto it = wordToIndex.find(canonical);
    if (it != wordToIndex.end()) {
        size_t idx = it->second;
        bool wasDeleted = entries[idx].is_deleted != 0;
        entries[idx].hit_count += hitWeight;
        entries[idx].base_freq = std::max(entries[idx].base_freq, freq);
        entries[idx].last_used_timestamp = nowSec;
        entries[idx].is_deleted = 0;
        if (wasDeleted) {
            indexEntryLocked(idx);
        }
        dirty.store(true, std::memory_order_release);
        return true;
    }

    if (entries.size() >= MAX_STORE_WORDS) {
        evictOneEntryLocked();
    }

    DynamicEntry entry{};
    std::strncpy(entry.word, canonical, MAX_STORE_WORD_LEN - 1);
    entry.base_freq = freq;
    entry.hit_count = hitWeight;
    entry.last_used_timestamp = nowSec;
    entry.is_deleted = 0;

    size_t newIdx = entries.size();
    entries.push_back(entry);
    indexEntryLocked(newIdx);
    dirty.store(true, std::memory_order_release);
    return true;
}

void DynamicStore::rebuildIndicesLocked() {
    wordToIndex.clear();
    prefixIndex.clear();
    wordToIndex.reserve(entries.size());
    for (size_t i = 0; i < entries.size(); ++i) {
        indexEntryLocked(i);
    }
}

void DynamicStore::rebuildBigramIndexLocked() {
    bigramByPrev.clear();
    for (size_t i = 0; i < bigrams.size(); ++i) {
        bigramByPrev[bigrams[i].prev_word].push_back(i);
    }
}

int DynamicStore::findEntryIndexLocked(const char* word) const {
    if (!word) return -1;
    char canonical[MAX_STORE_WORD_LEN];
    toCanonicalWord(word, canonical, MAX_STORE_WORD_LEN);
    if (canonical[0] == '\0') return -1;

    auto it = wordToIndex.find(canonical);
    if (it == wordToIndex.end()) return -1;
    size_t idx = it->second;
    if (idx < entries.size() && !entries[idx].is_deleted) {
        return static_cast<int>(idx);
    }
    return -1;
}

bool DynamicStore::isDeleted(const char* word) const {
    if (!word) return false;
    char canonical[MAX_STORE_WORD_LEN];
    toCanonicalWord(word, canonical, MAX_STORE_WORD_LEN);
    if (canonical[0] == '\0') return false;

    std::shared_lock<std::shared_mutex> lock(storeMutex);
    auto it = wordToIndex.find(canonical);
    if (it == wordToIndex.end()) return false;
    return entries[it->second].is_deleted != 0;
}

bool DynamicStore::addWord(const char* word, uint32_t freq, uint64_t nowSec) {
    if (!word || word[0] == '\0') return false;
    char canonical[MAX_STORE_WORD_LEN];
    toCanonicalWord(word, canonical, MAX_STORE_WORD_LEN);
    if (canonical[0] == '\0') return false;

    std::unique_lock<std::shared_mutex> lock(storeMutex);
    return upsertWordLocked(canonical, freq, nowSec, 1);
}

bool DynamicStore::recordUsage(const char* word, uint64_t nowSec, uint32_t weight) {
    if (!word || word[0] == '\0') return false;
    char canonical[MAX_STORE_WORD_LEN];
    toCanonicalWord(word, canonical, MAX_STORE_WORD_LEN);
    if (canonical[0] == '\0') return false;
    uint32_t safeWeight = std::clamp<uint32_t>(weight, 1, 10);

    std::unique_lock<std::shared_mutex> lock(storeMutex);
    return upsertWordLocked(canonical, 100, nowSec, safeWeight);
}

bool DynamicStore::removeWord(const char* word) {
    if (!word || word[0] == '\0') return false;
    char canonical[MAX_STORE_WORD_LEN];
    toCanonicalWord(word, canonical, MAX_STORE_WORD_LEN);
    if (canonical[0] == '\0') return false;

    uint64_t nowSec = static_cast<uint64_t>(std::time(nullptr));
    std::unique_lock<std::shared_mutex> lock(storeMutex);
    auto it = wordToIndex.find(canonical);
    if (it != wordToIndex.end()) {
        size_t idx = it->second;
        entries[idx].is_deleted = 1;
        entries[idx].last_used_timestamp = nowSec;
        unindexEntryLocked(idx);
        dirty.store(true, std::memory_order_release);
        return true;
    }

    if (entries.size() >= MAX_STORE_WORDS) {
        evictOneEntryLocked();
    }

    DynamicEntry entry{};
    std::strncpy(entry.word, canonical, MAX_STORE_WORD_LEN - 1);
    entry.base_freq = 0;
    entry.hit_count = 0;
    entry.last_used_timestamp = nowSec;
    entry.is_deleted = 1;

    size_t newIdx = entries.size();
    entries.push_back(entry);
    wordToIndex[entry.word] = newIdx;
    dirty.store(true, std::memory_order_release);
    return true;
}

bool DynamicStore::recordBigram(const char* prevWord, const char* nextWord, uint64_t nowSec) {
    if (!prevWord || !nextWord || prevWord[0] == '\0' || nextWord[0] == '\0') return false;

    char prevCanon[MAX_STORE_WORD_LEN];
    toCanonicalWord(prevWord, prevCanon, MAX_STORE_WORD_LEN);
    if (prevCanon[0] == '\0') return false;

    // Preserve display form for nextWord (e.g. "don't", "I'm") while keeping length bounded
    size_t nextLen = std::strlen(nextWord);
    if (nextLen == 0 || nextLen >= MAX_STORE_WORD_LEN) return false;

    std::unique_lock<std::shared_mutex> lock(storeMutex);
    auto& bucket = bigramByPrev[prevCanon];
    for (size_t idx : bucket) {
        if (std::strcmp(bigrams[idx].next_word, nextWord) == 0) {
            bigrams[idx].hit_count++;
            bigrams[idx].last_used_timestamp = nowSec;
            dirty.store(true, std::memory_order_release);
            return true;
        }
    }

    if (bigrams.size() >= MAX_STORE_BIGRAMS) {
        size_t oldestIdx = 0;
        uint64_t oldestTime = UINT64_MAX;
        for (size_t i = 0; i < bigrams.size(); ++i) {
            if (bigrams[i].last_used_timestamp < oldestTime) {
                oldestTime = bigrams[i].last_used_timestamp;
                oldestIdx = i;
            }
        }
        bigrams.erase(bigrams.begin() + static_cast<std::ptrdiff_t>(oldestIdx));
        rebuildBigramIndexLocked();
    }

    DynamicBigramEntry bg{};
    std::strncpy(bg.prev_word, prevCanon, MAX_STORE_WORD_LEN - 1);
    std::strncpy(bg.next_word, nextWord, MAX_STORE_WORD_LEN - 1);
    bg.hit_count = 1;
    bg.last_used_timestamp = nowSec;

    size_t newIdx = bigrams.size();
    bigrams.push_back(bg);
    bigramByPrev[bg.prev_word].push_back(newIdx);
    dirty.store(true, std::memory_order_release);
    return true;
}

void DynamicStore::reset() {
    {
        std::unique_lock<std::shared_mutex> lock(storeMutex);
        entries.clear();
        bigrams.clear();
        wordToIndex.clear();
        prefixIndex.clear();
        bigramByPrev.clear();
        dirty.store(true, std::memory_order_release);
    }
    flushIfDirty();
}

int DynamicStore::findMatchingWords(const int* digits, int length, uint64_t nowSec, uint32_t halfLifeDays,
                                    DynamicMatch* outMatches, int maxMatches) const {
    if (!digits || length <= 0 || !outMatches || maxMatches <= 0) return 0;

    int lookupLen = std::min(length, MAX_INDEXED_PREFIX_LEN);
    uint64_t packed = 0;
    for (int i = 0; i < lookupLen; ++i) {
        if (digits[i] < 2 || digits[i] > 9) return 0;
        packed = (packed << 4) | static_cast<uint64_t>(digits[i]);
    }
    uint64_t key = packPrefixKey(packed, lookupLen);

    std::shared_lock<std::shared_mutex> lock(storeMutex);
    auto it = prefixIndex.find(key);
    if (it == prefixIndex.end() || it->second.empty()) {
        return 0;
    }

    std::vector<DynamicMatch> matches;
    matches.reserve(std::min<size_t>(it->second.size(), 32));

    for (size_t idx : it->second) {
        const DynamicEntry& entry = entries[idx];
        if (entry.is_deleted) continue;

        size_t len = std::strlen(entry.word);
        if (static_cast<int>(len) < length) continue;

        bool matched = true;
        for (int i = lookupLen; i < length; ++i) {
            if (charToT9Digit(entry.word[i]) != digits[i]) {
                matched = false;
                break;
            }
        }
        if (!matched) continue;

        double decayFactor = computeDecayFactor(nowSec, entry.last_used_timestamp, halfLifeDays);
        DynamicMatch m{};
        std::memcpy(m.word, entry.word, len + 1);
        m.length = static_cast<uint8_t>(len);
        m.effective_freq = computeEffectiveFreq(entry, decayFactor);
        m.hit_count = entry.hit_count;
        m.last_used_timestamp = entry.last_used_timestamp;
        m.decay_factor = static_cast<float>(decayFactor);
        m.is_terminal = (static_cast<int>(len) == length);
        matches.push_back(m);
    }

    std::sort(matches.begin(), matches.end(), [](const DynamicMatch& a, const DynamicMatch& b) {
        if (a.is_terminal != b.is_terminal) return a.is_terminal > b.is_terminal;
        if (a.effective_freq != b.effective_freq) return a.effective_freq > b.effective_freq;
        return a.hit_count > b.hit_count;
    });

    int count = std::min(static_cast<int>(matches.size()), maxMatches);
    for (int i = 0; i < count; ++i) {
        outMatches[i] = matches[i];
    }
    return count;
}

int DynamicStore::findNextWords(const char* prevWord, uint64_t nowSec, uint32_t halfLifeDays,
                                DynamicBigramMatch* outMatches, int maxMatches) const {
    if (!prevWord || prevWord[0] == '\0' || !outMatches || maxMatches <= 0) return 0;

    char prevCanon[MAX_STORE_WORD_LEN];
    toCanonicalWord(prevWord, prevCanon, MAX_STORE_WORD_LEN);
    if (prevCanon[0] == '\0') return 0;

    std::shared_lock<std::shared_mutex> lock(storeMutex);
    auto it = bigramByPrev.find(prevCanon);
    if (it == bigramByPrev.end() || it->second.empty()) return 0;

    std::vector<DynamicBigramMatch> list;
    list.reserve(it->second.size());

    for (size_t idx : it->second) {
        const DynamicBigramEntry& bg = bigrams[idx];
        char nextCanon[MAX_STORE_WORD_LEN];
        toCanonicalWord(bg.next_word, nextCanon, MAX_STORE_WORD_LEN);
        auto delIt = wordToIndex.find(nextCanon);
        if (delIt != wordToIndex.end() && entries[delIt->second].is_deleted) {
            continue;
        }

        double decayFactor = computeDecayFactor(nowSec, bg.last_used_timestamp, halfLifeDays);
        DynamicBigramMatch m{};
        size_t len = std::strlen(bg.next_word);
        std::memcpy(m.word, bg.next_word, len + 1);
        m.length = static_cast<uint8_t>(len);
        m.score = computeBigramScore(bg.hit_count, decayFactor);
        m.hit_count = bg.hit_count;
        list.push_back(m);
    }

    std::sort(list.begin(), list.end(), [](const DynamicBigramMatch& a, const DynamicBigramMatch& b) {
        if (a.score != b.score) return a.score > b.score;
        return a.hit_count > b.hit_count;
    });

    int count = std::min(static_cast<int>(list.size()), maxMatches);
    for (int i = 0; i < count; ++i) {
        outMatches[i] = list[i];
    }
    return count;
}

float DynamicStore::getBigramBoost(const char* prevWord, const char* nextWord,
                                   uint64_t nowSec, uint32_t halfLifeDays) const {
    if (!prevWord || !nextWord || prevWord[0] == '\0' || nextWord[0] == '\0') return 0.0f;

    char prevCanon[MAX_STORE_WORD_LEN];
    toCanonicalWord(prevWord, prevCanon, MAX_STORE_WORD_LEN);
    if (prevCanon[0] == '\0') return 0.0f;

    char nextCanon[MAX_STORE_WORD_LEN];
    toCanonicalWord(nextWord, nextCanon, MAX_STORE_WORD_LEN);

    std::shared_lock<std::shared_mutex> lock(storeMutex);
    auto it = bigramByPrev.find(prevCanon);
    if (it == bigramByPrev.end()) return 0.0f;

    for (size_t idx : it->second) {
        const DynamicBigramEntry& bg = bigrams[idx];
        char candidateCanon[MAX_STORE_WORD_LEN];
        toCanonicalWord(bg.next_word, candidateCanon, MAX_STORE_WORD_LEN);
        if (std::strcmp(candidateCanon, nextCanon) == 0) {
            double decayFactor = computeDecayFactor(nowSec, bg.last_used_timestamp, halfLifeDays);
            return computeBigramScore(bg.hit_count, decayFactor);
        }
    }
    return 0.0f;
}

uint32_t DynamicStore::getHitCount(const char* word) const {
    if (!word) return 0;
    std::shared_lock<std::shared_mutex> lock(storeMutex);
    int idx = findEntryIndexLocked(word);
    if (idx < 0) return 0;
    return entries[idx].hit_count;
}

uint32_t DynamicStore::getEffectiveFrequency(const char* word, uint64_t nowSec, uint32_t halfLifeDays) const {
    if (!word) return 0;
    std::shared_lock<std::shared_mutex> lock(storeMutex);
    int idx = findEntryIndexLocked(word);
    if (idx < 0) return 0;

    const auto& entry = entries[idx];
    double decayFactor = computeDecayFactor(nowSec, entry.last_used_timestamp, halfLifeDays);
    return computeEffectiveFreq(entry, decayFactor);
}

bool DynamicStore::isCustomWord(const char* word) const {
    std::shared_lock<std::shared_mutex> lock(storeMutex);
    return findEntryIndexLocked(word) >= 0;
}

size_t DynamicStore::getWordCount() const {
    std::shared_lock<std::shared_mutex> lock(storeMutex);
    size_t count = 0;
    for (const auto& entry : entries) {
        if (!entry.is_deleted) count++;
    }
    return count;
}

int DynamicStore::serializeAllWords(uint8_t* outBuffer, int maxBytes) const {
    if (!outBuffer || maxBytes < 4) return 0;
    std::shared_lock<std::shared_mutex> lock(storeMutex);

    int offset = 0;
    uint32_t count = 0;
    for (const auto& entry : entries) {
        if (!entry.is_deleted) count++;
    }
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
