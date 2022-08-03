#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -e

SCRIPT_DIR=$(dirname $0)

cd "$SCRIPT_DIR/.."
BAZEL_BIN=`bazel info bazel-bin`

bazel build `bazel query 'attr(tags, "oss", //...:*)'`

bazel run //360-cg-manager/container:manager

bazel run //120-ng-manager/container:ng_manager

bazel run //pipeline-service/container:pipeline_service

bazel run //platform-service/build/container:platform_service

export VERSION=$(grep 'build.number=' build.properties | cut -d= -f2)
echo $VERSION

mkdir -p destination/dist/delegate/
mkdir -p destination/dist/watcher/
cp -f ${BAZEL_BIN}/260-delegate/module_deploy.jar destination/dist/delegate/delegate-capsule.jar
cp -f ${BAZEL_BIN}/960-watcher/module_deploy.jar destination/dist/watcher/watcher-capsule.jar
chmod 744 dockerization/delegate-proxy/setup.sh
./dockerization/delegate-proxy/setup.sh
bazel run //dockerization/delegate-proxy:delegate_proxy
