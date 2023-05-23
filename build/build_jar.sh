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

if [ "${SERVICE_NAME}" == "access-control" ]; then
  SERVICE_MODULE="access-control/service:module //access-control/service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "ng-manager" ]; then
    SERVICE_MODULE="120-ng-manager:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "migrator" ]; then
    SERVICE_MODULE="100-migrator:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "change-data-capture" ]; then
    SERVICE_MODULE="110-change-data-capture:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "iacm-manager" ]; then
    SERVICE_MODULE="310-iacm-manager/app:module //310-iacm-manager/app:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "sto-manager" ]; then
    SERVICE_MODULE="315-sto-manager/app:module //315-sto-manager/app:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "ci-manager" ]; then
    SERVICE_MODULE="332-ci-manager/app:module //332-ci-manager/app:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "batch-processing" ]; then
    SERVICE_MODULE="batch-processing/service:module //batch-processing/service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "audit-event-streaming" ]; then
    SERVICE_MODULE="audit-event-streaming/service:module //audit-event-streaming/service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "ce-nextgen" ]; then
    SERVICE_MODULE="ce-nextgen/service:module //ce-nextgen/service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "debezium-service" ]; then
    SERVICE_MODULE="debezium-service/service:module //debezium-service/service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "idp-service" ]; then
    SERVICE_MODULE="idp-service/src/main/java/io/harness/idp/app:module //idp-service/src/main/java/io/harness/idp/app:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "pipeline-service" ]; then
    SERVICE_MODULE="pipeline-service/service:module //pipeline-service/service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "platform-service" ]; then
    SERVICE_MODULE="platform-service/service:module //platform-service/service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "srm-service" ]; then
    SERVICE_MODULE="srm-service/modules/cv-nextgen-service/service:module //srm-service/modules/cv-nextgen-service/service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "template-service" ]; then
    SERVICE_MODULE="template-service:module_deploy.jar"
elif [ "${SERVICE_NAME}" == "cg-manager" ]; then
    SERVICE_MODULE="template-service:module_deploy.jar"
fi


bazel ${bazelrc} build //$SERVICE_MODULE ${BAZEL_ARGUMENTS}