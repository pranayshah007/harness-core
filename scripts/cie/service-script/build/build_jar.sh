#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

if [ "${PLATFORM}" == "jenkins" ]; then
  bazelrc=--bazelrc=bazelrc.remote
  bash scripts/bazel/testDistribute.sh
fi

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="--show_timestamps --announce_rc --experimental_convenience_symlinks=normal --symlink_prefix=${BAZEL_DIRS}/"
if [ ${MODULE} == "332-ci-manager" ];then
  SERVICE_MODULE="//${MODULE}/app:module //${MODULE}/app:module_deploy.jar"
else
  SERVICE_MODULE="//${MODULE}/service:module //${MODULE}/service:module_deploy.jar"
fi
bazel ${bazelrc} build $SERVICE_MODULE ${BAZEL_ARGUMENTS}

if [ $MODULE == "pipeline-service" ];
then
  bazel query "deps(//${MODULE}/service:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
  cp scripts/interface-hash/module-deps.sh .
  sh module-deps.sh //${MODULE}/service:module > /tmp/ProtoDeps.text
  bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${MODULE}-protocol.info
  rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
elif [ ${MODULE} == "332-ci-manager" ];then
  if [ "${PLATFORM}" == "jenkins" ]; then
    module=332-ci-manager
    moduleName=ci-manager

    bazel query "deps(//${module}/app:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
    cp scripts/interface-hash/module-deps.sh .
    sh module-deps.sh //${module}/service:module > /tmp/ProtoDeps.text
    bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${moduleName}-protocol.info
    rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
  fi
fi