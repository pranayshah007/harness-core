#!/bin/bash -e
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p logs
(
echo
echo "` date +%d/%m/%Y%t%H:%M:%S `    ###########################"

if [ ! -e start.sh ]; then
  echo
  echo "Delegate must not be run from a different directory"
  echo
  exit 1
fi

<#noparse>
JRE=${3:-"11.0.19+7"}
</#noparse>
JRE_DIR=jdk-$JRE-jre
JRE_BINARY=$JRE_DIR/bin/java
case "$OSTYPE" in
  solaris*)
    OS=solaris
    ;;
  darwin*)
    OS=macosx
    JRE_BINARY=$JRE_DIR/Contents/Home/bin/java
    ;;
  linux*)
    OS=linux
    ;;
  bsd*)
    echo "freebsd not supported."
    exit 1;
    ;;
  msys*)
    echo "For windows execute run.bat"
    exit 1;
    ;;
  cygwin*)
    echo "For windows execute run.bat"
    exit 1;
    ;;
  *)
    echo "unknown: $OSTYPE"
    ;;
esac

case "$(uname -m)" in
  x86_64*)
    ARCH=x64
    ;;
  amd64*)
    ARCH=x64
    ;;
  aarch64*)
    ARCH=arm64
    ;;
  arm64*)
    ARCH=arm64
    ;;
  *)
    echo "unknown architecture $(uname -m). Proceeding as amd64 arch"
    ARCH=x64
    ;;
esac

DELEGATE_STORAGE_URL=${delegateStorageUrl}

<#if useCdn == "true">
  <#noparse>
    JVM_URL=$DELEGATE_STORAGE_URL/public/shared/jre/openjdk-$JRE/OpenJDK11U-jre_${ARCH}_${OS}_hotspot_$JRE.tar.gz
  </#noparse>
<#else>
  <#noparse>
    JVM_URL=$DELEGATE_STORAGE_URL/jre/openjdk-$JRE/OpenJDK11U-jre_${ARCH}_${OS}_hotspot_$JRE.tar.gz
  </#noparse>
</#if>

<#noparse>
SOURCE="${BASH_SOURCE[0]}"
</#noparse>
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
