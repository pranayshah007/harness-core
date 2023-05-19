#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

function check_cmd_status() {
  if [ $1 != 0 ]; then
      echo "ERROR: $LINENO: $2. Exiting..."; exit 1
  fi
}

function get_PR_Modules(){
  GIT_DIFF="git diff --name-only $COMMIT_SHA..$BASE_SHA"

  PR_MODULES=()
  PR_MODULES+=($($GIT_DIFF | awk -F/ '{print $1}' | sort -u | tr '\r\n' ' '))
  check_cmd_status "$?" "Failed to get modules from commits."
  echo "List of targets modules for your PR."
  echo "${PR_MODULES[@]}"
}

#local_repo=${HOME}/.m2/repository
BAZEL_ARGUMENTS=
if [ "${PLATFORM}" == "jenkins" ]; then
  bazelrc=--bazelrc=bazelrc.remote
  #local_repo=/root/.m2/repository
  if [ ! -z "${DISTRIBUTE_TESTING_WORKER}" ]; then
    bash scripts/bazel/testDistribute.sh
  fi
fi

BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --show_timestamps --announce_rc"

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --experimental_convenience_symlinks=normal --symlink_prefix=${BAZEL_DIRS}/"

#if [[ ! -z "${OVERRIDE_LOCAL_M2}" ]]; then
#  local_repo=${OVERRIDE_LOCAL_M2}
#fi

# Enable caching by default. Turn it off by exporting CACHE_TEST_RESULTS=no
# to generate full call-graph for Test Intelligence
if [[ ! -z "${CACHE_TEST_RESULTS}" ]]; then
  export CACHE_TEST_RESULTS_ARG=--cache_test_results=${CACHE_TEST_RESULTS}
fi

bazel ${bazelrc} build ${BAZEL_ARGUMENTS}  //:resource
cat ${BAZEL_DIRS}/out/stable-status.txt
cat ${BAZEL_DIRS}/out/volatile-status.txt

if [ "${RUN_BAZEL_TESTS}" == "true" ]; then
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -- //... -//product/... -//commons/... \
  && bazel ${bazelrc} test ${CACHE_TEST_RESULTS_ARG} --define=HARNESS_ARGS=${HARNESS_ARGS} --keep_going ${BAZEL_ARGUMENTS} -- \
  //... -//product/... -//commons/... -//200-functional-test/... -//190-deployment-functional-tests/...
  exit $?
fi

if [ "${RUN_CHECKS}" == "true" ]; then
  if [[ "${BUILD_PURPOSE}" == "PR_CHECK" ]];then
    get_PR_Modules
  else
    GIT_DIFF="git diff --name-only develop $(git branch --show-current)"
    PR_MODULES=()
    PR_MODULES+=($($GIT_DIFF | awk -F/ '{print $1}' | sort -u | tr '\r\n' ' '))
    echo "By default target branch is develop but if your target branch is something different then you can change the target branch in line no 65 and 90 to run it in local"
  fi

  TARGETS=()
  for module in "${PR_MODULES[@]}"
  do
    if [[ $(bazel query 'attr (tags,"checkstyle",//'"$module"'/...)') ]];then
      TARGETS+=($(bazel query 'attr (tags,"checkstyle",//'"$module"'/...)'))
    fi
  done

  echo "list of target to be build "
  echo "${TARGETS[@]}"

  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -k ${TARGETS[@]}
  exit $?
fi

if [ "${RUN_PMDS}" == "true" ]; then
   if [[ "${BUILD_PURPOSE}" == "PR_CHECK" ]];then
      get_PR_Modules
   else
      GIT_DIFF="git diff --name-only develop $(git branch --show-current)"
      PR_MODULES=()
      PR_MODULES+=($($GIT_DIFF | awk -F/ '{print $1}' | sort -u | tr '\r\n' ' '))
      echo "By default target branch is develop but if your target branch is something different then you can change the target branch in line no 65 and 90 to run it in local"
    fi
  TARGETS=()
  for module in "${PR_MODULES[@]}"
  do
    if [[ $(bazel query 'attr (tags,"pmd",//'"$module"':*)') ]];then
      TARGETS+=($(bazel query 'attr (tags,"pmd",//'"$module"':*)'))
    fi
  done

  echo "list of target to be build "
  echo "${TARGETS[@]}"

  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -k ${TARGETS[@]}
  exit $?
fi

BAZEL_MODULES="\
  //360-cg-manager:module \
"

bazel ${bazelrc} build $BAZEL_MODULES `bazel query "//...:*" | grep "module_deploy.jar"` ${BAZEL_ARGUMENTS} --remote_download_outputs=all

build_bazel_module() {
  module=$1
  BAZEL_MODULE="//${module}:module"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_tests() {
  module=$1
  BAZEL_MODULE="//${module}:supporter-test"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_application() {
  module=$1
  BAZEL_MODULE="//${module}:module"
  BAZEL_DEPLOY_MODULE="//${module}:module_deploy.jar"

  bazel ${bazelrc} build $BAZEL_MODULES ${BAZEL_ARGUMENTS}

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi

  if ! grep -q "$BAZEL_DEPLOY_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_DEPLOY_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_application_module() {
  module=$1
  BAZEL_MODULE="//${module}:module"
  BAZEL_DEPLOY_MODULE="//${module}:module_deploy.jar"

  if [ "${BUILD_BAZEL_DEPLOY_JAR}" == "true" ]; then
    bazel ${bazelrc} build $BAZEL_DEPLOY_MODULE ${BAZEL_ARGUMENTS}
  fi

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_java_proto_module() {
  module=$1
  modulePath=$module/src/main/proto

  build_proto_module $module $modulePath
}

build_proto_module() {
  module=$1
  modulePath=$2

  BAZEL_MODULE="//${modulePath}:all"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi

  bazel_library=$(echo ${module} | tr '-' '_')
}

build_protocol_info(){
  module=$1
  moduleName=$2

  bazel query "deps(//${module}:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
  cp scripts/interface-hash/module-deps.sh .
  sh module-deps.sh //${module}:module > /tmp/ProtoDeps.text
  bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${moduleName}-protocol.info
  rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
}

#build_bazel_module 100-migrator
#build_bazel_module 323-sto-utilities
#build_bazel_module 380-cg-graphql
#build_bazel_module 400-rest
#build_bazel_module 410-cg-rest
#build_bazel_module 420-delegate-agent
#build_bazel_module 419-delegate-service-app/src/main/java/io/harness/dms/app
#build_bazel_module 420-delegate-service
#build_bazel_module 425-verification-commons
#build_bazel_module 440-connector-nextgen
#build_bazel_module 440-secret-management-service
#build_bazel_module 445-cg-connectors
#build_bazel_module 450-ce-views
#build_bazel_module 490-ce-commons
#build_bazel_module 815-cg-triggers
#build_bazel_module 865-cg-events
#build_bazel_module 867-polling-contracts
#build_bazel_module 870-cg-orchestration
#build_bazel_module 874-orchestration-delay
#build_bazel_module 878-ng-common-utilities
#build_bazel_module 880-pipeline-cd-commons
#build_bazel_module 884-pms-commons
#build_bazel_module 890-sm-core
#build_bazel_module 900-git-sync-sdk
#build_bazel_module 910-delegate-service-driver
#build_bazel_module 910-delegate-task-grpc-service
#build_bazel_module 920-delegate-agent-beans
#build_bazel_module 920-delegate-service-beans
#build_bazel_module 930-delegate-tasks
#build_bazel_module 930-ng-core-clients
#build_bazel_module 932-connector-task
#build_bazel_module 933-ci-commons
#build_bazel_module 940-feature-flag
#build_bazel_module 940-secret-manager-client
#build_bazel_module 947-scim-core
#build_bazel_module 950-command-library-common
#build_bazel_module 959-common-entities
#build_bazel_module 950-delegate-tasks-beans
#build_bazel_module 950-events-framework
#build_bazel_module 950-log-client
#build_bazel_module 950-ng-core
#build_bazel_module 950-ng-project-n-orgs
#build_bazel_module 950-wait-engine
#build_bazel_module 951-cg-git-sync
#build_bazel_module 952-remote-observers
#build_bazel_module 952-scm-java-client
#build_bazel_module 953-events-api
#build_bazel_module 953-git-sync-commons
#build_bazel_module 953-yaml-commons
#build_bazel_module 954-connector-beans
#build_bazel_module 955-cg-yaml
#build_bazel_module 955-delegate-beans
#build_bazel_module 955-filters-sdk
#build_bazel_module 955-outbox-sdk
#build_bazel_module 955-setup-usage-sdk
#build_bazel_module 956-feature-flag-beans
#build_bazel_module 957-cg-beans
#build_bazel_module 958-migration-sdk
#build_bazel_module 959-file-service-commons
#build_bazel_module 959-psql-database-models
#build_bazel_module 959-timeout-engine
#build_bazel_module 960-api-services
#build_bazel_module 960-continuous-features
#build_bazel_module 960-expression-service
#build_bazel_module 960-ng-core-beans
#build_bazel_module 960-persistence
#build_bazel_module 960-yaml-sdk
#build_bazel_module 967-walktree-visitor
#build_bazel_module 970-api-services-beans
#build_bazel_module 970-grpc
#build_bazel_module 970-ng-commons
#build_bazel_module 970-rbac-core
#build_bazel_module 970-watcher-beans
#build_bazel_module 979-recaster
#build_bazel_module 980-commons
#build_bazel_module 990-commons-test
#build_bazel_module 999-annotations

#build_bazel_tests 400-rest
#build_bazel_tests 960-persistence

build_proto_module ciengine product/ci/engine/proto
build_proto_module ciscm product/ci/scm/proto

bazel ${bazelrc} run ${BAZEL_ARGUMENTS} //001-microservice-intfc-tool:module | grep "Codebase Hash:" > protocol.info

if [ "${PLATFORM}" == "jenkins" ]; then
 build_protocol_info pipeline-service pipeline-service
 build_protocol_info 332-ci-manager ci-manager
fi
