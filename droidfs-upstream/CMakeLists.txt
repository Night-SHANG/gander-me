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

# DroidFS' original encrypted camera/video path depends on its small FFmpeg mux bridge.
add_library(avformat SHARED IMPORTED)
set_target_properties(
    avformat
    PROPERTIES IMPORTED_LOCATION
    "${DROIDFS_APP_DIR}/ffmpeg/build/${ANDROID_ABI}/libavformat/libavformat.so"
)
add_library(avcodec SHARED IMPORTED)
set_target_properties(
    avcodec
    PROPERTIES IMPORTED_LOCATION
    "${DROIDFS_APP_DIR}/ffmpeg/build/${ANDROID_ABI}/libavcodec/libavcodec.so"
)
add_library(avutil SHARED IMPORTED)
set_target_properties(
    avutil
    PROPERTIES IMPORTED_LOCATION
    "${DROIDFS_APP_DIR}/ffmpeg/build/${ANDROID_ABI}/libavutil/libavutil.so"
)

add_library(
    mux
    SHARED
    "${DROIDFS_APP_DIR}/src/main/native/libmux.c"
)
target_include_directories(
    mux
    PRIVATE
    "${DROIDFS_APP_DIR}/ffmpeg/build/${ANDROID_ABI}"
)
target_link_libraries(mux avformat avcodec avutil log)
