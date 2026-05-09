QT = core network

CONFIG += c++17 cmdline

# You can make your code fail to compile if it uses deprecated APIs.
# In order to do so, uncomment the following line.
#DEFINES += QT_DISABLE_DEPRECATED_BEFORE=0x060000    # disables all the APIs deprecated before Qt 6.0.0

SOURCES += \
        main.cpp

macx {
    isEmpty(OPENSSL_PREFIX) {
        exists(/opt/homebrew/opt/openssl@3/include/openssl/evp.h) {
            OPENSSL_PREFIX = /opt/homebrew/opt/openssl@3
        } else: exists(/opt/homebrew/opt/openssl/include/openssl/evp.h) {
            OPENSSL_PREFIX = /opt/homebrew/opt/openssl
        } else: exists(/usr/local/opt/openssl@3/include/openssl/evp.h) {
            OPENSSL_PREFIX = /usr/local/opt/openssl@3
        } else: exists(/usr/local/opt/openssl/include/openssl/evp.h) {
            OPENSSL_PREFIX = /usr/local/opt/openssl
        } else {
            error("OpenSSL development package not found. Install openssl@3 or set OPENSSL_PREFIX.")
        }
    }

    INCLUDEPATH += $$OPENSSL_PREFIX/include
    LIBS += -L$$OPENSSL_PREFIX/lib -lssl -lcrypto
}

unix:!macx {
    CONFIG += link_pkgconfig
    PKGCONFIG += openssl
}

win32 {
    LIBS += -lssl -lcrypto
}

# Default rules for deployment.
qnx: target.path = /tmp/$${TARGET}/bin
else: unix:!android: target.path = /opt/$${TARGET}/bin
!isEmpty(target.path): INSTALLS += target
