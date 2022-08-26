#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# From harness-core/scripts/bazel/bazel_script.sh

set -ex

if [ "${PLATFORM}" == "jenkins" ]; then
  bazelrc=--bazelrc=bazelrc.remote
  bash scripts/bazel/testDistribute.sh
fi

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="--show_timestamps --announce_rc --experimental_convenience_symlinks=normal --symlink_prefix=${BAZEL_DIRS}/"
CI_MANAGER_MODULE="//332-ci-manager/app:module //332-ci-manager/app:module_deploy.jar"
bazel ${bazelrc} build $CI_MANAGER_MODULE ${BAZEL_ARGUMENTS}

if [ "${PLATFORM}" == "jenkins" ]; then
  module=332-ci-manager
  moduleName=ci-manager

  bazel query "deps(//${module}/app:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
  cp scripts/interface-hash/module-deps.sh .
  sh module-deps.sh //${module}/service:module > /tmp/ProtoDeps.text
  bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${moduleName}-protocol.info
  rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
fi
