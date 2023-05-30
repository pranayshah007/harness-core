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

service_map["manager"]="360-cg-manager"
service_map["ng-manager"]="120-ng-manager"
service_map["access-control"]="access-control/service"
service_map["migrator"]="100-migrator"
service_map["change-data-capture"]="110-change-data-capture"
service_map["iacm-manager"]="310-iacm-manager/app"
service_map["sto-manager"]="315-sto-manager/app"
service_map["ci-manager"]="332-ci-manager/app"
service_map["batch-processing"]="batch-processing/service"
service_map["audit-event-streaming"]="audit-event-streaming/service"
service_map["ce-nextgen"]="ce-nextgen/service"
service_map["debezium-service"]="debezium-service/service"
service_map["idp-service"]="idp-service/src/main/java/io/harness/idp/app"
service_map["pipeline-service"]="pipeline-service/service"
service_map["platform-service"]="platform-service/service"
service_map["srm-service"]="srm-service/modules/cv-nextgen-service/service"
service_map["template-service"]="template-service/service"


key="${SERVICE_NAME}"
echo $key
for key in "${!service_map[@]}"; do
  bazel ${bazelrc} build //${service_map[$key]}":module_deploy.jar" ${BAZEL_ARGUMENTS}
done

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