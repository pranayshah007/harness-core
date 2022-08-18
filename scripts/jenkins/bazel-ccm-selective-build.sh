#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

SCRIPT_DIR="$(dirname $0)"

local_repo=${HOME}/.m2/repository
BAZEL_ARGUMENTS=
if [ "${PLATFORM}" == "jenkins" ]; then
  bazelrc=--bazelrc=bazelrc.remote
  local_repo=/root/.m2/repository
  if [ ! -z "${DISTRIBUTE_TESTING_WORKER}" ]; then
    bash scripts/bazel/testDistribute.sh
  fi
fi

BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --show_timestamps --announce_rc"

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --experimental_convenience_symlinks=normal --symlink_prefix=${BAZEL_DIRS}/"

if [[ ! -z "${OVERRIDE_LOCAL_M2}" ]]; then
  local_repo=${OVERRIDE_LOCAL_M2}
fi


build_bazel_application() {
  module=$1
  BAZEL_MODULE="//${module}:module"
  BAZEL_DEPLOY_MODULE="//${module}:module_deploy.jar"

  bazel ${bazelrc} build $BAZEL_DEPLOY_MODULE ${BAZEL_ARGUMENTS}

  file "${BAZEL_DIRS}/bin/${module}/module_deploy.jar"
}

if [ "${BUILD_CG_MANAGER}" == "true" ]; then
  echo "##### Building Everything #####"
  #mvn -B -T 2C clean package -e -Dbuild.number=$VERSION -DgitBranch=$BUILD_NAME -DskipTests --also-make -U #This one is used in Jenkins.
  scripts/bazel/bazel_script.sh
  scripts/jenkins/portal-openjdk-bazel.sh || true
#  # build manager
#  build_bazel_application 360-cg-manager
#  copy_cg_manager_jars
#
#  # build delegate
#  # //TODO: Implement delegate build only
else
  echo "##### Using Selective Builds #####"
  source "${SCRIPT_DIR}/portal-openjdk-bazel-commons.sh"

  [ "$DOCKERIZE" == "true" ] && prepare_to_copy_jars

  # Workaround to get away from building entire module
  # For feature build we don't need to actually create checksum, since it's just copied but not used in docker image anyway.
  if [ "$DOCKERIZE" == "true" ]; then 
    RANDOM_CODEHASH=$(head -n 10000 /dev/urandom | sha256sum | cut -d' ' -f1);
    echo "Codebase Hash:${RANDOM_CODEHASH}" > protocol.info;
  fi

  if [ "${BUILD_NG_MANAGER}" == "true" ]; then
    build_bazel_application 120-ng-manager
    [ "$DOCKERIZE" == "true" ] && copy_ng_manager_jars
  fi

  if [ "${BUILD_EVENT_SERVER}" == "true" ]; then
    build_bazel_application 350-event-server
    [ "$DOCKERIZE" == "true" ] && copy_event_server_jars
  fi
fi


if [ "$DOCKERIZE" = "true" ] && [ "${BRANCH_IN_THE_TAG}" = "true" ] && [ "${BUILD_CG_MANAGER}" == "true" ];
then
  echo ${IMAGE_TAG} > dist/manager/image_tag.txt
fi
