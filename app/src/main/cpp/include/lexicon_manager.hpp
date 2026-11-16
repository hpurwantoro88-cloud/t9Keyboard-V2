#pragma once
#include <cstddef>
#include <cstdint>
#include <sys/types.h>

struct MmapLexicon {
    void* mmap_addr = nullptr;
    size_t map_length = 0;
    const uint8_t* data = nullptr;
    size_t data_length = 0;
    bool is_mmap = false;
    bool is_loaded = false;
};

class LexiconManager {
public:
    LexiconManager() = default;
    ~LexiconManager();

    bool loadFromFd(const char* langCode, int fd, off_t offset, size_t length);
    bool loadFromBuffer(const char* langCode, const uint8_t* buffer, size_t length);

    bool loadNgramFromFd(const char* langCode, int fd, off_t offset, size_t length);
    bool loadNgramFromBuffer(const char* langCode, const uint8_t* buffer, size_t length);

    bool switchLanguage(const char* langCode);

    const uint8_t* getActiveData() const { return activeData; }
    size_t getActiveSize() const { return activeSize; }
    const uint8_t* getActiveNgramData() const { return activeNgramData; }
    size_t getActiveNgramSize() const { return activeNgramSize; }

    const uint8_t* getInactiveData() const;
    size_t getInactiveSize() const;
    const uint8_t* getInactiveNgramData() const;
    size_t getInactiveNgramSize() const;

    const char* getActiveLanguage() const { return activeLanguage; }

    void release();

private:
    MmapLexicon enLexicon;
    MmapLexicon idLexicon;
    MmapLexicon enNgram;
    MmapLexicon idNgram;

    const uint8_t* activeData = nullptr;
    size_t activeSize = 0;
    const uint8_t* activeNgramData = nullptr;
    size_t activeNgramSize = 0;
    char activeLanguage[4] = "ID"; // default
};
