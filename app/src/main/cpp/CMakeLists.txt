cmake_minimum_required(VERSION 3.22.1)
project("opent9" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

include_directories(include)

add_library(opent9 SHARED
    src/dawg_engine.cpp
    src/spatial_scoring.cpp
    src/multi_tap_engine.cpp
    src/lexicon_manager.cpp
    src/audio_engine.cpp
    src/dynamic_store.cpp
    src/jni_bridge.cpp
)

target_link_libraries(opent9
    android
    log
    oboe::oboe
)
