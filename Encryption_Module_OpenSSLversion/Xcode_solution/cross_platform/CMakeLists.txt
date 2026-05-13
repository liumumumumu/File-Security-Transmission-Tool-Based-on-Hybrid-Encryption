cmake_minimum_required(VERSION 3.20)

project(FileSecurityTransferTool_CryptoTest LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(OpenSSL REQUIRED)
find_package(nlohmann_json CONFIG REQUIRED)

add_executable(FileSecurityTransferTool_CryptoTest
    ../FileSecurityTransferTool_CryptoTest/main.cpp
)

set_target_properties(FileSecurityTransferTool_CryptoTest PROPERTIES
    OUTPUT_NAME crypto-service
)

target_include_directories(FileSecurityTransferTool_CryptoTest PRIVATE
    ../FileSecurityTransferTool_CryptoTest
)

target_link_libraries(FileSecurityTransferTool_CryptoTest PRIVATE
    OpenSSL::SSL
    OpenSSL::Crypto
    nlohmann_json::nlohmann_json
)

if(WIN32)
    target_compile_definitions(FileSecurityTransferTool_CryptoTest PRIVATE
        WIN32_LEAN_AND_MEAN
        NOMINMAX
    )

    target_link_libraries(FileSecurityTransferTool_CryptoTest PRIVATE
        ws2_32
        crypt32
    )
endif()

if(MSVC)
    target_compile_options(FileSecurityTransferTool_CryptoTest PRIVATE
        /utf-8
        /permissive-
    )
else()
    target_compile_options(FileSecurityTransferTool_CryptoTest PRIVATE
        -Wall
        -Wextra
        -Wpedantic
    )
endif()

install(TARGETS FileSecurityTransferTool_CryptoTest
    RUNTIME DESTINATION .
)
