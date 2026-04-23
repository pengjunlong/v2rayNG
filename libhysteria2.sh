#!/bin/bash
# BUILD_ABI: space-separated list, e.g. "arm64-v8a" or "arm64-v8a armeabi-v7a"
# Defaults to arm64-v8a only.

all_targets=(
  "aarch64-linux-android21 arm64 arm64-v8a"
  "armv7a-linux-androideabi21 arm armeabi-v7a"
  "x86_64-linux-android21 amd64 x86_64"
  "i686-linux-android21 386 x86"
)

BUILD_ABI="${BUILD_ABI:-arm64-v8a}"

cd "hysteria" || exit

for entry in "${all_targets[@]}"; do
  IFS=' ' read -r ndk_target goarch abi <<< "$entry"

  # 跳过不在 BUILD_ABI 列表中的 ABI
  if [[ " ${BUILD_ABI} " != *" ${abi} "* ]]; then
    echo "Skipping ${abi} (not in BUILD_ABI='${BUILD_ABI}')"
    continue
  fi

  echo "Building for ${abi} with ${ndk_target} (${goarch})"
  CC="${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/${ndk_target}-clang" \
    CGO_ENABLED=1 GOOS=android GOARCH=$goarch \
    go build -o libs/$abi/libhysteria2.so -trimpath -ldflags "-s -w -buildid=" -buildvcs=false ./app
  echo "Built libhysteria2.so for ${abi}"
done
