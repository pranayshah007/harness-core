#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

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
  TARGETS=$(bazel query 'attr(tags, "checkstyle", //...:*)')
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -k ${TARGETS}
  exit $?
fi

if [ "${RUN_PMDS}" == "true" ]; then
  TARGETS=$(bazel query 'attr(tags, "pmd", //...:*)')
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -k ${TARGETS}
  exit $?
fi

BAZEL_MODULES="\
  //100-migrator:module \
  //270-verification:module \
  //290-dashboard-service:module \
  //295-cdng-contracts:module \
  //300-cv-nextgen:module \
  //310-ci-manager:module \
  //320-ci-execution:module \
  //323-sto-utilities:module \
  //325-sto-beans:module \
  //330-ci-beans:module \
  //340-ce-nextgen:module \
  //350-event-server:module \
  //360-cg-manager:module \
  //380-cg-graphql:module \
  //400-rest:module \
  //400-rest:supporter-test \
  //410-cg-rest:module \
  //420-delegate-agent:module \
  //420-delegate-service:module \
  //425-verification-commons:module \
  //430-cv-nextgen-commons:module \
  //440-connector-nextgen:module \
  //440-secret-management-service:module \
  //441-cg-instance-sync:module \
  //445-cg-connectors:module \
  //450-ce-views:module \
  //460-capability:module \
  //490-ce-commons:module \
  //800-pipeline-service:module \
  //810-ng-triggers:module \
  //815-cg-triggers:module \
  //platform-service/service:module \
  //platform-service/service:module_deploy.jar \
  //platform-service/modules/audit-service/contracts:module \
  //platform-service/modules/notification-service/contracts:module \
  //platform-service/modules/notification-service/contracts/src/main/proto:all \
  //platform-service/modules/notification-service/delegate-tasks:module \
  //platform-service/modules/resource-group-service/contracts:module \
  //platform-service/modules/audit-service:module \
  //platform-service/modules/notification-service:module \
  //platform-service/modules/resource-group-service:module \
  //840-template-service:module \
  //860-orchestration-steps:module \
  //860-orchestration-visualization:module \
  //865-cg-events:module \
  //867-polling-contracts:module \
  //870-cg-orchestration:module \
  //870-orchestration:module \
  //874-orchestration-delay:module \
  //876-orchestration-beans:module \
  //877-filestore:module \
  //878-ng-common-utilities:module \
  //879-pms-sdk:module \
  //882-pms-sdk-core:module \
  //884-pms-commons:module \
  //890-sm-core:module \
  //900-git-sync-sdk:module \
  //910-delegate-service-driver:module \
  //910-delegate-task-grpc-service/src/main/proto:all \
  //910-delegate-task-grpc-service:module \
  //920-delegate-agent-beans/src/main/proto:all \
  //920-delegate-agent-beans:module \
  //920-delegate-service-beans/src/main/proto:all \
  //920-delegate-service-beans:module \
  //920-ng-signup:module \
  //925-enforcement-service:module \
  //930-delegate-tasks:module \
  //930-ng-core-clients:module \
  //932-connector-task:module \
  //935-analyser-service:module \
  //937-persistence-tracer:module \
  //940-feature-flag:module \
  //clients/notification:module \
  //clients/notification:module_deploy.jar \
  //940-secret-manager-client:module \
  //942-enforcement-sdk:module \
  //943-enforcement-beans:module \
  //945-account-mgmt:module \
  //945-license-usage-sdk:module \
  //clients/audit:module \
  //947-scim-core:module \
  //948-cv-nextgen-beans:module \
  //950-command-library-common:module \
  //959-common-entities:module \
  //950-delegate-tasks-beans/src/main/proto:all \
  //950-delegate-tasks-beans:module \
  //950-events-framework:module \
  //950-events-framework-monitor:module \
  //950-log-client:module \
  //951-cg-git-sync:module \
  //951-debezium-service:module \
  //952-debezium-engine:module \
  //959-debezium-beans:module \
  //950-ng-authentication-service:module \
  //950-ng-core:module \
  //950-ng-project-n-orgs:module \
  //950-ng-signup-beans:module \
  //950-telemetry:module \
  //950-wait-engine:module \
  //952-remote-observers:module \
  //952-scm-java-client:module \
  //953-events-api/src/main/proto:all \
  //953-events-api:module \
  //953-git-sync-commons/src/main/proto:all \
  //953-git-sync-commons:module \
  //953-yaml-commons:module \
  //954-connector-beans:module \
  //954-pms-contracts:module \
  //955-cg-yaml:module \
  //955-delegate-beans/src/main/proto:all \
  //955-delegate-beans:module \
  //955-filters-sdk:module \
  //955-outbox-sdk:module \
  //955-setup-usage-sdk:module \
  //956-feature-flag-beans:module \
  //957-cg-beans:module \
  //958-migration-sdk:module \
  //959-file-service-commons:module \
  //959-psql-database-models:module \
  //959-timeout-engine:module \
  //960-api-services:module \
  //960-continuous-features:module \
  //960-expression-service/src/main/proto/io/harness/expression/service:all \
  //960-expression-service:module \
  //960-ng-core-beans:module \
  //960-ng-license-beans:module \
  //960-ng-license-usage-beans:module \
  //960-persistence:module \
  //960-persistence:supporter-test \
  //960-yaml-sdk:module \
  //967-walktree-visitor:module \
  //970-api-services-beans:module \
  //970-grpc:module \
  //970-ng-commons:module \
  //970-rbac-core:module \
  //970-telemetry-beans:module \
  //970-watcher-beans:module \
  //980-commons:module \
  //979-recaster:module \
  //990-commons-test:module \
  //999-annotations:module \
  //access-control/service:module \
  //access-control/contracts:module \
  //product/ci/engine/proto:all \
  //product/ci/scm/proto:all \
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

build_bazel_module 100-migrator
build_bazel_module 320-ci-execution
build_bazel_module 323-sto-utilities
build_bazel_module 325-sto-beans
build_bazel_module 330-ci-beans
build_bazel_module 380-cg-graphql
build_bazel_module 400-rest
build_bazel_module 410-cg-rest
build_bazel_module 420-delegate-agent
build_bazel_module 420-delegate-service
build_bazel_module 425-verification-commons
build_bazel_module 430-cv-nextgen-commons
build_bazel_module 440-connector-nextgen
build_bazel_module 440-secret-management-service
build_bazel_module 445-cg-connectors
build_bazel_module 450-ce-views
build_bazel_module 460-capability
build_bazel_module 490-ce-commons
build_bazel_module 810-ng-triggers
build_bazel_module 815-cg-triggers
build_bazel_module 865-cg-events
build_bazel_module 860-orchestration-steps
build_bazel_module 860-orchestration-visualization
build_bazel_module 867-polling-contracts
build_bazel_module 870-cg-orchestration
build_bazel_module 870-orchestration
build_bazel_module 874-orchestration-delay
build_bazel_module 876-orchestration-beans
build_bazel_module 878-ng-common-utilities
build_bazel_module 879-pms-sdk
build_bazel_module 882-pms-sdk-core
build_bazel_module 884-pms-commons
build_bazel_module 890-sm-core
build_bazel_module 900-git-sync-sdk
build_bazel_module 910-delegate-service-driver
build_bazel_module 910-delegate-task-grpc-service
build_bazel_module 920-delegate-agent-beans
build_bazel_module 920-delegate-service-beans
build_bazel_module 930-delegate-tasks
build_bazel_module 930-ng-core-clients
build_bazel_module 932-connector-task
build_bazel_module 940-feature-flag
build_bazel_module 940-secret-manager-client
build_bazel_module 947-scim-core
build_bazel_module 948-cv-nextgen-beans
build_bazel_module 950-command-library-common
build_bazel_module 959-common-entities
build_bazel_module 950-delegate-tasks-beans
build_bazel_module 950-events-framework
build_bazel_module 950-log-client
build_bazel_module 950-ng-core
build_bazel_module 950-ng-project-n-orgs
build_bazel_module 950-wait-engine
build_bazel_module 951-cg-git-sync
build_bazel_module 952-remote-observers
build_bazel_module 952-scm-java-client
build_bazel_module 953-events-api
build_bazel_module 953-git-sync-commons
build_bazel_module 953-yaml-commons
build_bazel_module 954-connector-beans
build_bazel_module 954-pms-contracts
build_bazel_module 955-cg-yaml
build_bazel_module 955-delegate-beans
build_bazel_module 955-filters-sdk
build_bazel_module 955-outbox-sdk
build_bazel_module 955-setup-usage-sdk
build_bazel_module 956-feature-flag-beans
build_bazel_module 957-cg-beans
build_bazel_module 958-migration-sdk
build_bazel_module 959-file-service-commons
build_bazel_module 959-psql-database-models
build_bazel_module 959-timeout-engine
build_bazel_module 960-api-services
build_bazel_module 960-continuous-features
build_bazel_module 960-expression-service
build_bazel_module 960-ng-core-beans
build_bazel_module 960-persistence
build_bazel_module 960-yaml-sdk
build_bazel_module 967-walktree-visitor
build_bazel_module 970-api-services-beans
build_bazel_module 970-grpc
build_bazel_module 970-ng-commons
build_bazel_module 970-rbac-core
build_bazel_module 970-watcher-beans
build_bazel_module 979-recaster
build_bazel_module 980-commons
build_bazel_module 990-commons-test
build_bazel_module 999-annotations

build_bazel_tests 400-rest
build_bazel_tests 960-persistence

build_proto_module ciengine product/ci/engine/proto
build_proto_module ciscm product/ci/scm/proto

bazel ${bazelrc} run ${BAZEL_ARGUMENTS} //001-microservice-intfc-tool:module | grep "Codebase Hash:" > protocol.info

if [ "${PLATFORM}" == "jenkins" ]; then
 build_protocol_info 800-pipeline-service pipeline-service
 build_protocol_info 310-ci-manager ci-manager
fi
