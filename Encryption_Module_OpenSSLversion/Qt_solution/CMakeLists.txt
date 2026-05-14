cmake_minimum_required(VERSION 3.16)

project(FileSecurityTransferTool_CryptoQt LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(QT NAMES Qt6 Qt5 REQUIRED COMPONENTS Core Network)
find_package(Qt${QT_VERSION_MAJOR} REQUIRED COMPONENTS Core Network)
find_package(OpenSSL REQUIRED)

add_executable(FileSecurityTransferTool_CryptoQt
    main.cpp
)

target_link_libraries(FileSecurityTransferTool_CryptoQt
    PRIVATE
        Qt${QT_VERSION_MAJOR}::Core
        Qt${QT_VERSION_MAJOR}::Network
        OpenSSL::SSL
        OpenSSL::Crypto
)
