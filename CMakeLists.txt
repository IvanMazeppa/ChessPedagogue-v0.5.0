cmake_minimum_required(VERSION 3.10.2)
project(my_native_lib)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Our wrapper and bridge files
set(WRAPPER_SOURCES
        "${CMAKE_CURRENT_SOURCE_DIR}/stockfish_wrapper.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/native_bridge.cpp"
)

# Go up one directory from CMAKE_SOURCE_DIR to reach the app directory
# Then navigate to the jniLibs folder
set(STOCKFISH_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Add the directory to the linker search path
link_directories(${STOCKFISH_LIB_DIR})

# Define the target library
add_library(my_native_lib SHARED
        stockfish_wrapper.cpp
        native_bridge.cpp
)

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Link against libraries using a simple name (option 1)
target_link_libraries(my_native_lib
        android
        log
        stockfish
)

# Alternative if the above doesn't work (option 2)
# target_link_libraries(my_native_lib
#         android
#         log
#         ${STOCKFISH_LIB_DIR}/libstockfish.so
# )