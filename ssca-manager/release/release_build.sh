#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

export PLATFORM="jenkins"

export VERSION_FILE=ssca-manager/build.properties
export MAJOR=`cat ${VERSION_FILE} | grep 'build.majorVersion=' | sed -e 's: *build.majorVersion=::g'`
export MINOR=`cat ${VERSION_FILE} | grep 'build.minorVersion=' | sed -e 's: *build.minorVersion=::g'`
export PATCHVERSION=`cat ${VERSION_FILE} | grep 'build.patchVersion=' | sed -e 's: *build.patchVersion=::g'`
export VERSION=${MAJOR}.${MINOR}.${PATCHVERSION}

chmod +x ssca-manager/build/feature_build.sh
ssca-manager/build/feature_build.sh