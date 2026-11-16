#include "ngram_engine.hpp"
#include "dawg_engine.hpp"
#include <algorithm>
#include <cctype>
#include <cstring>

namespace {

bool equalsIgnoreCase(const char* a, const char* b) {
    if (!a || !b) return false;
    size_t i = 0;
    while (true) {
        char ca = static_cast<char>(std::tolower(static_cast<unsigned char>(a[i])));
        char cb = static_cast<char>(std::tolower(static_cast<unsigned char>(b[i])));
        if (ca != cb) return false;
        if (ca == '\0') return true;
        i++;
    }
}

} // namespace

void NgramEngine::clear() {
    header = nullptr;
    hashKeys = nullptr;
    buckets = nullptr;
    edges = nullptr;
    totalPrevEntries = 0;
    totalBigrams = 0;
}

bool NgramEngine::setNgramData(const uint8_t* data, size_t size) {
    if (!data || size < sizeof(NgramHeaderV1)) {
        clear();
        return false;
    }

    const NgramHeaderV1* hdr = reinterpret_cast<const NgramHeaderV1*>(data);
    if (std::memcmp(hdr->magic, "OPENT9N1", 8) != 0 || hdr->version != 1) {
        clear();
        return false;
    }

    size_t hashBytes = static_cast<size_t>(hdr->prev_entry_count) * sizeof(WordHashKeyV1);
    size_t bucketBytes = static_cast<size_t>(hdr->prev_entry_count) * sizeof(PrevWordBucketV1);
    size_t edgeBytes = static_cast<size_t>(hdr->bigram_count) * sizeof(NgramEdgeV1);
    size_t expectedSize = sizeof(NgramHeaderV1) + hashBytes + bucketBytes + edgeBytes;

    if (size < expectedSize) {
        clear();
        return false;
    }

    size_t offset = sizeof(NgramHeaderV1);
    hashKeys = reinterpret_cast<const WordHashKeyV1*>(data + offset);
    offset += hashBytes;
    buckets = reinterpret_cast<const PrevWordBucketV1*>(data + offset);
    offset += bucketBytes;
    edges = reinterpret_cast<const NgramEdgeV1*>(data + offset);

    header = hdr;
    totalPrevEntries = hdr->prev_entry_count;
    totalBigrams = hdr->bigram_count;
    return true;
}

uint32_t NgramEngine::resolvePrevBucket(
    const char* prevWord,
    const LexiconWordV2* lexWords,
    size_t lexWordCount,
    const char* lexStrings,
    size_t lexStringBytes
) const {
    if (!header || !prevWord || prevWord[0] == '\0' || totalPrevEntries == 0 ||
        !lexWords || !lexStrings) {
        return INVALID_BUCKET;
    }

    uint64_t targetHash = fnv1aHash64Lower(prevWord);
    const WordHashKeyV1* begin = hashKeys;
    const WordHashKeyV1* end = hashKeys + totalPrevEntries;

    const WordHashKeyV1* it = std::lower_bound(begin, end, targetHash,
        [](const WordHashKeyV1& entry, uint64_t h) {
            return entry.hash < h;
        });

    while (it < end && it->hash == targetHash) {
        uint32_t wid = it->word_id;
        if (wid < lexWordCount && lexWords[wid].str_offset < lexStringBytes) {
            const char* candidate = lexStrings + lexWords[wid].str_offset;
            if (equalsIgnoreCase(candidate, prevWord)) {
                return (it->bucket_idx < totalPrevEntries) ? it->bucket_idx : INVALID_BUCKET;
            }
        }
        it++;
    }
    return INVALID_BUCKET;
}

float NgramEngine::getBigramBoost(uint32_t prevBucketIdx, uint32_t nextWordId) const {
    if (!header || prevBucketIdx >= totalPrevEntries) {
        return 0.0f;
    }

    const PrevWordBucketV1& bucket = buckets[prevBucketIdx];
    if (bucket.sorted_count == 0 || bucket.sorted_offset + bucket.sorted_count > totalBigrams) {
        return 0.0f;
    }

    const NgramEdgeV1* begin = edges + bucket.sorted_offset;
    const NgramEdgeV1* end = begin + bucket.sorted_count;
    const NgramEdgeV1* it = std::lower_bound(begin, end, nextWordId,
        [](const NgramEdgeV1& e, uint32_t targetId) {
            return e.next_word_id < targetId;
        });

    if (it < end && it->next_word_id == nextWordId) {
        return static_cast<float>(it->quantized_log_prob) / NGRAM_PROB_SCALE;
    }
    return 0.0f;
}

int NgramEngine::getTopNextWordIds(
    uint32_t prevBucketIdx,
    uint32_t* outWordIds,
    float* outBoosts,
    int maxCount
) const {
    if (!header || prevBucketIdx >= totalPrevEntries || !outWordIds || !outBoosts || maxCount <= 0) {
        return 0;
    }

    const PrevWordBucketV1& bucket = buckets[prevBucketIdx];
    if (bucket.top_count == 0 || bucket.top_offset + bucket.top_count > totalBigrams) {
        return 0;
    }

    int count = std::min<int>(bucket.top_count, maxCount);
    for (int i = 0; i < count; ++i) {
        const NgramEdgeV1& e = edges[bucket.top_offset + i];
        outWordIds[i] = e.next_word_id;
        outBoosts[i] = static_cast<float>(e.quantized_log_prob) / NGRAM_PROB_SCALE;
    }
    return count;
}
