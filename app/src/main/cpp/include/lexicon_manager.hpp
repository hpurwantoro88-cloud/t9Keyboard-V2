#pragma once
#include <cstdint>
#include <cstddef>
#include <sys/types.h>

struct MmapLexicon {
    void* mmap_addr = nullptr;
    size_t length = 0;
    bool is_loaded = false;
};

class LexiconManager {
public:
    LexiconManager() = default;
    ~LexiconManager();

    bool loadFromFd(const char* langCode, int fd, off_t offset, size_t length);
    bool loadFromBuffer(const char* langCode, const uint8_t* buffer, size_t length);
    bool switchLanguage(const char* langCode);

    const uint8_t* getActiveData() const { return activeData; }
    size_t getActiveSize() const { return activeSize; }
    const char* getActiveLanguage() const { return activeLanguage; }

    void release();

private:
    MmapLexicon enLexicon;
    MmapLexicon idLexicon;

    const uint8_t* activeData = nullptr;
    size_t activeSize = 0;
    char activeLanguage[4] = "ID"; // default
};
