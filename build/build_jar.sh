#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
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

modify_service_name() {
  declare -A modified_service_name=(
    ["ng-manager"]="120-ng-manager"
    ["migrator"]="100-migrator"
    ["manager"]="360-cg-manager"
    ["change-data-capture"]="110-change-data-capture"
    ["iacm-manager"]="310-iacm-manager"
    ["sto-manager"]="315-sto-manager"
    ["ci-manager"]="332-ci-manager"
    ["verification-service"]="270-verification"
  )
  declare -A modified_service_name_with_app=(
    ["310-iacm-manager"]=1
    ["315-sto-manager"]=1
    ["332-ci-manager"]=1
  )
  declare -A modified_service_name_with_service=(
    ["debezium-service"]=1
    ["pipeline-service"]=1
    ["platform-service"]=1
    ["template-service"]=1
    ["access-control"]=1
    ["batch-processing"]=1
    ["audit-event-streaming"]=1
    ["ce-nextgen"]=1
    ["srm-service"]=1
  )

  local modified_service_name="${modified_service_name[$SERVICE_NAME]}"

  if [[ -z $modified_service_name ]]; then
    modified_service_name="$SERVICE_NAME"
  fi
  if [[ -n ${modified_service_name_with_app[$modified_service_name]} ]]; then
      modified_service_name+="/app"
  elif [[ -n ${modified_service_name_with_service[$modified_service_name]} ]]; then
      modified_service_name+="/service"
  fi

  if [[ $modified_service_name == *"srm-service"* ]]; then
    modified_service_name="srm-service/modules/cv-nextgen-service/service"
  fi
  echo "$modified_service_name"
}

# Call the function and pass the service name as an argument
modified_service_name=$(modify_service_name "$SERVICE_NAME")

bazel ${bazelrc} build //${modified_service_name}":module_deploy.jar" ${BAZEL_ARGUMENTS}


if [ "${SERVICE_NAME}" == "pipeline-service" ]; then
  module=pipeline-service
  moduleName=pipeline-service
  bazel query "deps(//${module}/service:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
  cp scripts/interface-hash/module-deps.sh .
  sh module-deps.sh //${module}/service:module > /tmp/ProtoDeps.text
  bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${moduleName}-protocol.info
  rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
fi

if [ "${PLATFORM}" == "jenkins" ] && [ "${SERVICE_NAME}" == "ci-manager" ]; then
  module=332-ci-manager
  moduleName=ci-manager

  bazel query "deps(//${module}/app:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
  cp scripts/interface-hash/module-deps.sh .
  sh module-deps.sh //${module}/service:module > /tmp/ProtoDeps.text
  bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${moduleName}-protocol.info
  rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
fi


service=$(echo "$modified_service_name" | cut -d'/' -f1)
if [[ $SERVICE_NAME == "manager" || $SERVICE_NAME == "migrator" || $SERVICE_NAME == "change-data-capture" || $SERVICE_NAME == "verification-service" ]]; then
    chmod +x build/build_dist.sh
    build/build_dist.sh || true
else
  chmod +x ${service}/build/build_dist.sh
  ${service}/build/build_dist.sh || true
fi
