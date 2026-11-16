#include "lexicon_manager.hpp"
#include <sys/mman.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>

namespace {

void releaseSlot(MmapLexicon& slot) {
    if (slot.is_mmap && slot.mmap_addr && slot.mmap_addr != MAP_FAILED) {
        munmap(slot.mmap_addr, slot.map_length);
    }
    slot.mmap_addr = nullptr;
    slot.map_length = 0;
    slot.data = nullptr;
    slot.data_length = 0;
    slot.is_mmap = false;
    slot.is_loaded = false;
}

bool mapFdIntoSlot(MmapLexicon& slot, int fd, off_t offset, size_t length) {
    if (fd < 0 || length == 0) return false;

    long pageSize = sysconf(_SC_PAGE_SIZE);
    off_t pageOffset = (offset / pageSize) * pageSize;
    off_t diff = offset - pageOffset;
    size_t mapLength = length + static_cast<size_t>(diff);

    void* mapped = mmap(nullptr, mapLength, PROT_READ, MAP_SHARED, fd, pageOffset);
    if (mapped == MAP_FAILED) {
        return false;
    }

    releaseSlot(slot);
    slot.mmap_addr = mapped;
    slot.map_length = mapLength;
    slot.data = static_cast<uint8_t*>(mapped) + diff;
    slot.data_length = length;
    slot.is_mmap = true;
    slot.is_loaded = true;
    return true;
}

bool setBufferIntoSlot(MmapLexicon& slot, const uint8_t* buffer, size_t length) {
    if (!buffer || length == 0) return false;
    releaseSlot(slot);
    slot.data = buffer;
    slot.data_length = length;
    slot.is_mmap = false;
    slot.is_loaded = true;
    return true;
}

} // namespace

LexiconManager::~LexiconManager() {
    release();
}

void LexiconManager::release() {
    releaseSlot(enLexicon);
    releaseSlot(idLexicon);
    releaseSlot(enNgram);
    releaseSlot(idNgram);
    activeData = nullptr;
    activeSize = 0;
    activeNgramData = nullptr;
    activeNgramSize = 0;
}

bool LexiconManager::loadFromFd(const char* langCode, int fd, off_t offset, size_t length) {
    if (!langCode) return false;
    MmapLexicon& slot = (std::strcmp(langCode, "EN") == 0) ? enLexicon : idLexicon;
    if (!mapFdIntoSlot(slot, fd, offset, length)) return false;

    if (std::strcmp(activeLanguage, langCode) == 0 || activeData == nullptr) {
        activeData = slot.data;
        activeSize = slot.data_length;
        std::strncpy(activeLanguage, langCode, sizeof(activeLanguage) - 1);
    }
    return true;
}

bool LexiconManager::loadFromBuffer(const char* langCode, const uint8_t* buffer, size_t length) {
    if (!langCode) return false;
    MmapLexicon& slot = (std::strcmp(langCode, "EN") == 0) ? enLexicon : idLexicon;
    if (!setBufferIntoSlot(slot, buffer, length)) return false;

    if (std::strcmp(activeLanguage, langCode) == 0 || activeData == nullptr) {
        activeData = slot.data;
        activeSize = slot.data_length;
        std::strncpy(activeLanguage, langCode, sizeof(activeLanguage) - 1);
    }
    return true;
}

bool LexiconManager::loadNgramFromFd(const char* langCode, int fd, off_t offset, size_t length) {
    if (!langCode) return false;
    MmapLexicon& slot = (std::strcmp(langCode, "EN") == 0) ? enNgram : idNgram;
    if (!mapFdIntoSlot(slot, fd, offset, length)) return false;

    if (std::strcmp(activeLanguage, langCode) == 0 || activeNgramData == nullptr) {
        activeNgramData = slot.data;
        activeNgramSize = slot.data_length;
    }
    return true;
}

bool LexiconManager::loadNgramFromBuffer(const char* langCode, const uint8_t* buffer, size_t length) {
    if (!langCode) return false;
    MmapLexicon& slot = (std::strcmp(langCode, "EN") == 0) ? enNgram : idNgram;
    if (!setBufferIntoSlot(slot, buffer, length)) return false;

    if (std::strcmp(activeLanguage, langCode) == 0 || activeNgramData == nullptr) {
        activeNgramData = slot.data;
        activeNgramSize = slot.data_length;
    }
    return true;
}

bool LexiconManager::switchLanguage(const char* langCode) {
    if (!langCode) return false;

    if (std::strcmp(langCode, "EN") == 0) {
        if (!enLexicon.is_loaded || !enLexicon.data) return false;
        activeData = enLexicon.data;
        activeSize = enLexicon.data_length;
        activeNgramData = enNgram.is_loaded ? enNgram.data : nullptr;
        activeNgramSize = enNgram.is_loaded ? enNgram.data_length : 0;
        std::strncpy(activeLanguage, "EN", sizeof(activeLanguage) - 1);
        return true;
    } else if (std::strcmp(langCode, "ID") == 0) {
        if (!idLexicon.is_loaded || !idLexicon.data) return false;
        activeData = idLexicon.data;
        activeSize = idLexicon.data_length;
        activeNgramData = idNgram.is_loaded ? idNgram.data : nullptr;
        activeNgramSize = idNgram.is_loaded ? idNgram.data_length : 0;
        std::strncpy(activeLanguage, "ID", sizeof(activeLanguage) - 1);
        return true;
    }
    return false;
}

const uint8_t* LexiconManager::getInactiveData() const {
    if (std::strcmp(activeLanguage, "EN") == 0) {
        return idLexicon.is_loaded ? idLexicon.data : nullptr;
    }
    return enLexicon.is_loaded ? enLexicon.data : nullptr;
}

size_t LexiconManager::getInactiveSize() const {
    if (std::strcmp(activeLanguage, "EN") == 0) {
        return idLexicon.is_loaded ? idLexicon.data_length : 0;
    }
    return enLexicon.is_loaded ? enLexicon.data_length : 0;
}

const uint8_t* LexiconManager::getInactiveNgramData() const {
    if (std::strcmp(activeLanguage, "EN") == 0) {
        return idNgram.is_loaded ? idNgram.data : nullptr;
    }
    return enNgram.is_loaded ? enNgram.data : nullptr;
}

size_t LexiconManager::getInactiveNgramSize() const {
    if (std::strcmp(activeLanguage, "EN") == 0) {
        return idNgram.is_loaded ? idNgram.data_length : 0;
    }
    return enNgram.is_loaded ? enNgram.data_length : 0;
}
