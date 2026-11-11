#!/usr/bin/env python3
"""POCO per Android arm64 con il clang dell'NDK attuale (per l'app Collabora Office, vedi office/app/EURISE.md).

Uso: python3 office/app/poco_android.py <cartella di poco>

Scrive build/config/AndroidEurise: come la configurazione Android di POCO con la correzione di Collabora
(scripts/build-poco-android.sh), ma solo arm64, API 26 come la suite, strumenti llvm dell'NDK e C++17.
"""
import sys
from pathlib import Path

config = """#
# Android arm64 per Eurise Office (clang dell'NDK, API 26)
#
LINKMODE           ?= STATIC
POCO_TARGET_OSNAME  = Android
POCO_TARGET_OSARCH  = arm64-v8a
TOOL      = aarch64-linux-android
ARCHFLAGS = -march=armv8-a
CC      = $(TOOL)26-clang
CXX     = $(TOOL)26-clang++
LINK    = $(CXX)
STRIP   = llvm-strip
LIB     = llvm-ar -cr
RANLIB  = llvm-ranlib
SHLIB   = $(CXX) -shared -Wl,-soname,$(notdir $@) -o $@
SHLIBLN = $(POCO_BASE)/build/script/shlibln
DEP     = $(POCO_BASE)/build/script/makedepend.gcc
SHELL   = sh
RM      = rm -rf
CP      = cp
MKDIR   = mkdir -p
SHAREDLIBEXT     = .so.$(target_version)
SHAREDLIBLINKEXT = .so
CFLAGS          = $(ARCHFLAGS) -fpic -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing
CFLAGS32        =
CFLAGS64        =
CXXFLAGS        = $(ARCHFLAGS) -fpic -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing -frtti -fexceptions -std=c++17
CXXFLAGS32      =
CXXFLAGS64      =
LINKFLAGS       =
LINKFLAGS32     =
LINKFLAGS64     =
STATICOPT_CC    =
STATICOPT_CXX   =
STATICOPT_LINK  = -static
SHAREDOPT_CC    = -fPIC
SHAREDOPT_CXX   = -fPIC
SHAREDOPT_LINK  = -Wl,-rpath,$(LIBPATH)
DEBUGOPT_CC     = -g -D_DEBUG=$(DEBUGLEVEL)
DEBUGOPT_CXX    = -g -D_DEBUG=$(DEBUGLEVEL)
DEBUGOPT_LINK   = -g
RELEASEOPT_CC   = -O2 -DNDEBUG
RELEASEOPT_CXX  = -O2 -DNDEBUG
RELEASEOPT_LINK = -O2
SYSFLAGS = -DPOCO_ANDROID -DPOCO_NO_FPENVIRONMENT -DPOCO_NO_WSTRING -DPOCO_NO_SHAREDMEMORY
SYSLIBS  = -static-libstdc++
"""

root = Path(sys.argv[1])
target = root / "build" / "config" / "AndroidEurise"
if not target.parent.is_dir():
    sys.exit(f"poco_android: non trovo {target.parent}: è la cartella di POCO?")
target.write_text(config)
print(f"poco_android: scritto {target}")
