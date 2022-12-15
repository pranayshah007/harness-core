#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

export GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
export GIT_COMMIT=$(git rev-parse HEAD)

echo "--------------------------------------"
echo $BUILD $VERSION $BUILD_PURPOSE $GIT_BRANCH $GIT_COMMIT $(date)
echo "--------------------------------------"

scripts/bazel/generate_credentials.sh

chmod +x queue-service/hsqs/build/build_jar.sh
queue-service/hsqs/build/build_jar.sh