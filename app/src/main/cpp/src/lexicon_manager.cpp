#include "lexicon_manager.hpp"
#include <sys/mman.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>

LexiconManager::~LexiconManager() {
    release();
}

void LexiconManager::release() {
    if (enLexicon.mmap_addr && enLexicon.mmap_addr != MAP_FAILED) {
        munmap(enLexicon.mmap_addr, enLexicon.length);
        enLexicon.mmap_addr = nullptr;
        enLexicon.length = 0;
        enLexicon.is_loaded = false;
    }
    if (idLexicon.mmap_addr && idLexicon.mmap_addr != MAP_FAILED) {
        munmap(idLexicon.mmap_addr, idLexicon.length);
        idLexicon.mmap_addr = nullptr;
        idLexicon.length = 0;
        idLexicon.is_loaded = false;
    }
    activeData = nullptr;
    activeSize = 0;
}

bool LexiconManager::loadFromFd(const char* langCode, int fd, off_t offset, size_t length) {
    if (fd < 0 || length == 0) return false;

    // mmap offset must be page aligned
    long pageSize = sysconf(_SC_PAGE_SIZE);
    off_t pageOffset = (offset / pageSize) * pageSize;
    off_t diff = offset - pageOffset;
    size_t mapLength = length + diff;

    void* mapped = mmap(nullptr, mapLength, PROT_READ, MAP_SHARED, fd, pageOffset);
    if (mapped == MAP_FAILED) {
        return false;
    }

    uint8_t* basePtr = static_cast<uint8_t*>(mapped) + diff;

    if (std::strcmp(langCode, "EN") == 0) {
        if (enLexicon.is_loaded && enLexicon.mmap_addr) {
            munmap(enLexicon.mmap_addr, enLexicon.length);
        }
        enLexicon.mmap_addr = mapped;
        enLexicon.length = mapLength;
        enLexicon.is_loaded = true;
    } else {
        if (idLexicon.is_loaded && idLexicon.mmap_addr) {
            munmap(idLexicon.mmap_addr, idLexicon.length);
        }
        idLexicon.mmap_addr = mapped;
        idLexicon.length = mapLength;
        idLexicon.is_loaded = true;
    }

    if (std::strcmp(activeLanguage, langCode) == 0 || activeData == nullptr) {
        activeData = basePtr;
        activeSize = length;
        std::strncpy(activeLanguage, langCode, sizeof(activeLanguage) - 1);
    }
    return true;
}

bool LexiconManager::loadFromBuffer(const char* langCode, const uint8_t* buffer, size_t length) {
    if (!buffer || length == 0) return false;

    if (std::strcmp(langCode, "EN") == 0) {
        enLexicon.mmap_addr = const_cast<uint8_t*>(buffer);
        enLexicon.length = length;
        enLexicon.is_loaded = true;
    } else {
        idLexicon.mmap_addr = const_cast<uint8_t*>(buffer);
        idLexicon.length = length;
        idLexicon.is_loaded = true;
    }

    if (std::strcmp(activeLanguage, langCode) == 0 || activeData == nullptr) {
        activeData = buffer;
        activeSize = length;
        std::strncpy(activeLanguage, langCode, sizeof(activeLanguage) - 1);
    }
    return true;
}

bool LexiconManager::switchLanguage(const char* langCode) {
    if (!langCode) return false;

    if (std::strcmp(langCode, "EN") == 0) {
        if (!enLexicon.is_loaded) return false;
        activeData = static_cast<const uint8_t*>(enLexicon.mmap_addr);
        activeSize = enLexicon.length;
        std::strncpy(activeLanguage, "EN", sizeof(activeLanguage) - 1);
        return true;
    } else if (std::strcmp(langCode, "ID") == 0) {
        if (!idLexicon.is_loaded) return false;
        activeData = static_cast<const uint8_t*>(idLexicon.mmap_addr);
        activeSize = idLexicon.length;
        std::strncpy(activeLanguage, "ID", sizeof(activeLanguage) - 1);
        return true;
    }
    return false;
}
