# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

scripts/bazel/testDistribute.sh

CACHE_FILE='bazelrc.test'

CONTENT="#Remote cache configuration\n
build --google_credentials=platform-bazel-cache-read.json\n
build --remote_upload_local_results=false\n
build --incompatible_remote_results_ignore_disk=true\n
build --experimental_guard_against_concurrent_changes\n
build --incompatible_strict_action_env\n"

if [ `uname -s` = 'Darwin' ]; then
  echo "$CONTENT" > ${CACHE_FILE}
  [ `uname -m` = 'arm64' ] \
  && echo "build:macos --remote_cache=https://storage.googleapis.com/harness-bazel-cache-blr-dev-m1" >> ${CACHE_FILE} \
  || echo "build:macos --remote_cache=https://storage.googleapis.com/harness-bazel-cache-blr-dev" >> ${CACHE_FILE}
fi
