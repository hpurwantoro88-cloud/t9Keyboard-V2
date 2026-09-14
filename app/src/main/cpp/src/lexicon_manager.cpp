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
        munmap(enLexicon.mmap_addr, enLexicon.map_length);
        enLexicon.mmap_addr = nullptr;
        enLexicon.map_length = 0;
        enLexicon.data = nullptr;
        enLexicon.data_length = 0;
        enLexicon.is_loaded = false;
    }
    if (idLexicon.mmap_addr && idLexicon.mmap_addr != MAP_FAILED) {
        munmap(idLexicon.mmap_addr, idLexicon.map_length);
        idLexicon.mmap_addr = nullptr;
        idLexicon.map_length = 0;
        idLexicon.data = nullptr;
        idLexicon.data_length = 0;
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

    MmapLexicon& lex = (std::strcmp(langCode, "EN") == 0) ? enLexicon : idLexicon;
    if (lex.is_loaded && lex.mmap_addr && lex.mmap_addr != MAP_FAILED) {
        munmap(lex.mmap_addr, lex.map_length);
    }
    lex.mmap_addr = mapped;
    lex.map_length = mapLength;
    lex.data = basePtr;
    lex.data_length = length;
    lex.is_loaded = true;

    if (std::strcmp(activeLanguage, langCode) == 0 || activeData == nullptr) {
        activeData = basePtr;
        activeSize = length;
        std::strncpy(activeLanguage, langCode, sizeof(activeLanguage) - 1);
    }
    return true;
}

bool LexiconManager::loadFromBuffer(const char* langCode, const uint8_t* buffer, size_t length) {
    if (!buffer || length == 0) return false;

    MmapLexicon& lex = (std::strcmp(langCode, "EN") == 0) ? enLexicon : idLexicon;
    lex.mmap_addr = const_cast<uint8_t*>(buffer);
    lex.map_length = length;
    lex.data = buffer;
    lex.data_length = length;
    lex.is_loaded = true;

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
        if (!enLexicon.is_loaded || !enLexicon.data) return false;
        activeData = enLexicon.data;
        activeSize = enLexicon.data_length;
        std::strncpy(activeLanguage, "EN", sizeof(activeLanguage) - 1);
        return true;
    } else if (std::strcmp(langCode, "ID") == 0) {
        if (!idLexicon.is_loaded || !idLexicon.data) return false;
        activeData = idLexicon.data;
        activeSize = idLexicon.data_length;
        std::strncpy(activeLanguage, "ID", sizeof(activeLanguage) - 1);
        return true;
    }
    return false;
}
