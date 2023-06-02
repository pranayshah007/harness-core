#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script is to build only the TaskApp_deploy.jar. It doesn't build any other services

if [ "${PLATFORM}" == "harness-ci" ]; then
  bazelrc=--bazelrc=bazelrc.remote
fi

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="--show_timestamps --announce_rc --experimental_convenience_symlinks=normal --remote_download_outputs=all --symlink_prefix=${BAZEL_DIRS}/"

BAZEL_MODULES="\
  //delegate/src/main/java/io/harness/taskapps/shell:ShellNgApplication_deploy.jar \
"

echo "building tasks"
bazel ${bazelrc} build ${BAZEL_MODULES} ${BAZEL_ARGUMENTS}