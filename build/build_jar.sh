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

declare -A service_map

#if [ "${SERVICE_NAME}" == "manager" ]; then
#  service_map["manager"]="360-cg-manager"
#elif [ "${SERVICE_NAME}" == "ng-manager" ]; then
#  service_map["ng-manager"]="120-ng-manager"
#elif [ "${SERVICE_NAME}" == "migrator" ]; then
#  service_map["migrator"]="100-migrator"
#elif [ "${SERVICE_NAME}" == "change-data-capture" ]; then
#  service_map["change-data-capture"]="110-change-data-capture"
#elif [ "${SERVICE_NAME}" == "iacm-manager" ]; then
#  service_map["iacm-manager"]="310-iacm-manager/app"
#elif [ "${SERVICE_NAME}" == "sto-manager" ]; then
#  service_map["sto-manager"]="315-sto-manager/app"
#elif [ "${SERVICE_NAME}" == "ci-manager" ]; then
#  service_map["ci-manager"]="332-ci-manager/app"
#elif [ "${SERVICE_NAME}" == "idp-service" ]; then
#  service_map["idp-service"]=${SERVICE_NAME}"/src/main/java/io/harness/idp/app"
#elif [ "${SERVICE_NAME}" == "srm-service" ]; then
#  service_map["srm-service"]=${SERVICE_NAME}"/modules/cv-nextgen-service/service"
#else
#  service_map[${SERVICE_NAME}]=${SERVICE_NAME}"/service"
#fi

case "${SERVICE_NAME}" in
  "manager")
    service_map["manager"]="360-cg-manager"
    ;;
  "ng-manager")
    service_map["ng-manager"]="120-ng-manager"
    ;;
  "migrator")
    service_map["migrator"]="100-migrator"
    ;;
  "change-data-capture")
    service_map["change-data-capture"]="110-change-data-capture"
    ;;
  "iacm-manager")
    service_map["iacm-manager"]="310-iacm-manager/app"
    ;;
  "sto-manager")
    service_map["sto-manager"]="315-sto-manager/app"
    ;;
  "ci-manager")
    service_map["ci-manager"]="332-ci-manager/app"
    ;;
  "idp-service")
    service_map["idp-service"]="${SERVICE_NAME}/src/main/java/io/harness/idp/app"
    ;;
  "srm-service")
    service_map["srm-service"]="${SERVICE_NAME}/modules/cv-nextgen-service/service"
    ;;
  *)
    service_map["${SERVICE_NAME}"]="${SERVICE_NAME}/service"
    ;;
esac

key="${SERVICE_NAME}"
bazel ${bazelrc} build //${service_map[$key]}":module_deploy.jar" ${BAZEL_ARGUMENTS}

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