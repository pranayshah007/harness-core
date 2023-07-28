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

JRE=${3:-"11.0.19+7"}
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

DELEGATE_STORAGE_URL=http://localhost:8888

    JVM_URL=$DELEGATE_STORAGE_URL/jre/openjdk-$JRE/OpenJDK11U-jre_${ARCH}_${OS}_hotspot_$JRE.tar.gz

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
  i=0
  stopped=0
  while [ "$i" -le 30 ]; do
    if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
      pkill -f "\-Dwatchersourcedir=$DIR"
      if [ "$i" -gt 0 ]; then
        sleep 1
      fi
      i=$((i+1))
    else
      echo "Watcher stopped"
      stopped=1
      break
    fi
  done
  if [ "$stopped" -eq 0 ]; then
    echo "Unable to stop watcher in 30 seconds. Trying to force it ..."
    pkill -9 -f "\-Dwatchersourcedir=$DIR"
    sleep 10
    if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
      echo "Unable to stop watcher in 40 seconds. See process details ..."
      echo
      pgrep -f "\-Dwatchersourcedir=$DIR" | xargs ps up
      echo
    else
      echo "Watcher stopped"
    fi
  fi
else
  echo "Watcher not running"
fi

if `pgrep -f "\-Ddelegatesourcedir=$DIR"> /dev/null`; then
  i=0
  while [ "$i" -le 30 ]; do
    if `pgrep -f "\-Ddelegatesourcedir=$DIR"> /dev/null`; then
      pkill -f "\-Ddelegatesourcedir=$DIR"
      if [ "$i" -gt 0 ]; then
        sleep 1
      fi
      i=$((i+1))
    else
      echo "Delegate stopped"
      rm -rf msg
      exit 0
    fi
  done
  echo "Unable to stop delegate in 30 seconds. Trying to force it ..."
  pkill -9 -f "\-Ddelegatesourcedir=$DIR"
  sleep 10
  if `pgrep -f "\-Ddelegatesourcedir=$DIR"> /dev/null`; then
    echo "Unable to stop delegate in 40 seconds. See process details ..."
    echo
    pgrep -f "\-Ddelegatesourcedir=$DIR" | xargs ps up
    echo
    exit 1
  else
    echo "Delegate stopped"
    rm -rf msg
    exit 0
  fi
else
  echo "Delegate not running"
  rm -rf msg
  exit 0
fi ) 2>&1 | tee -a logs/stopscript.log
