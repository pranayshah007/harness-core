#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script is to build only the secret-decryption.jar. It doesn't build any other services

if [ "${PLATFORM}" == "harness-ci" ]; then
  bazelrc=--bazelrc=bazelrc.remote
fi

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="--show_timestamps --announce_rc --experimental_convenience_symlinks=normal --remote_download_outputs=all --symlink_prefix=${BAZEL_DIRS}/"

echo "building secret decryption"
bazel ${bazelrc} build //931-delegate-decryption-service/src/java/io/harness/decryption/delegate:SecretsHandler_deploy.jar ${BAZEL_ARGUMENTS}
cp ${BAZEL_DIRS}/bin/931-delegate-decryption-service/src/java/io/harness/decryption/delegate/SecretsHandler_deploy.jar ./931-delegate-decryption-service/src/dockerization/secret-decryption.jar
