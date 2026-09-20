cmake_minimum_required(VERSION 3.22.1)

project(VaultShelfDroidFS)

set(DROIDFS_APP_DIR "${CMAKE_CURRENT_LIST_DIR}/../third_party/droidfs/app")

add_library(
    memfile
    SHARED
    "${DROIDFS_APP_DIR}/src/main/native/memfile.c"
)
target_link_libraries(memfile log)

add_library(gocryptfs SHARED IMPORTED)
set_target_properties(
    gocryptfs
    PROPERTIES IMPORTED_LOCATION
    "${DROIDFS_APP_DIR}/libgocryptfs/build/${ANDROID_ABI}/libgocryptfs.so"
)

add_library(
    gocryptfs_jni
    SHARED
    "${DROIDFS_APP_DIR}/src/main/native/gocryptfs_jni.c"
)
target_include_directories(
    gocryptfs_jni
    PRIVATE
    "${DROIDFS_APP_DIR}/libgocryptfs/build/${ANDROID_ABI}"
)
target_link_libraries(gocryptfs_jni gocryptfs)

# VaultShelf does not embed DroidFS' encrypted camera/video capture path.
# Omitting its FFmpeg mux bridge removes libavformat/libavcodec/libavutil/libmux from every ABI.
