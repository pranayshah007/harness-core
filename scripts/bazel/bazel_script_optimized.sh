#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

function build_bazel_module() {
  module=$1
  BAZEL_MODULE="//${module}:module"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

function build_bazel_tests() {
  module=$1
  BAZEL_MODULE="//${module}:supporter-test"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

function build_bazel_application() {
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

function build_bazel_application_module() {
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

function build_java_proto_module() {
  module=$1
  modulePath=$module/src/main/proto

  build_proto_module $module $modulePath
}

function build_proto_module() {
  module=$1
  modulePath=$2

  BAZEL_MODULE="//${modulePath}:all"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi

  bazel_library=$(echo ${module} | tr '-' '_')
}

function build_protocol_info(){
  module=$1
  moduleName=$2

  bazel query "deps(//${module}:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
  cp scripts/interface-hash/module-deps.sh .
  sh module-deps.sh //${module}:module > /tmp/ProtoDeps.text
  bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${moduleName}-protocol.info
  rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
}

### Main Code Starts here ###
BAZEL_ARGUMENTS=$1

bazelrc='--bazelrc=bazelrc.remote'
BAZEL_DIRS="${HOME}/.bazel-dirs"
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --show_timestamps --announce_rc --experimental_convenience_symlinks=normal --symlink_prefix=${BAZEL_DIRS}/"

BAZEL_MODULES="\
  //100-migrator:module \
  //270-verification:module \
  //290-dashboard-service:module \
  //295-cdng-contracts:module \
  //300-cv-nextgen:module \
  //323-sto-utilities:module \
  //325-sto-beans:module \
  //332-ci-manager/app:module \
  //332-ci-manager/contracts:module \
  //332-ci-manager/service:module \
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
  //pipeline-service/service:module \
  //pipeline-service/modules/ng-triggers:module \
  //pipeline-service/modules/orchestration-steps:module \
  //pipeline-service/modules/orchestration-steps/contracts:module \
  //pipeline-service/modules/orchestration-visualization:module \
  //pipeline-service/modules/orchestration:module \
  //pipeline-service/modules/orchestration/contracts:module \
  //pipeline-service/modules/orchestration-beans:module \
  //pipeline-service/modules/pms-contracts:module \
  //clients/pipeline-service/pms-client:module \
  //clients/pipeline-service/pms-sdk-core:module \
  //clients/pipeline-service/pms-sdk:module \
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
  //865-cg-events:module \
  //867-polling-contracts:module \
  //870-cg-orchestration:module \
  //874-orchestration-delay:module \
  //877-filestore:module \
  //878-ng-common-utilities:module \
  //880-pipeline-cd-commons:module \
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
  //933-ci-commons:module \
  //935-analyser-service:module \
  //937-persistence-tracer:module \
  //940-feature-flag:module \
  //clients/notification:module \
  //clients/notification:module_deploy.jar \
  //940-secret-manager-client:module \
  //941-filestore-client:module \
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
  //debezium-service/service:module \
  //952-debezium-engine:module \
  //959-debezium-beans:module \
  //950-ng-authentication-service:module \
  //950-ng-core:module \
  //950-ng-project-n-orgs:module \
  //950-ng-signup-beans:module \
  //950-telemetry:module \
  //950-wait-engine:module \
  //951-opa-contracts:all \
  //952-remote-observers:module \
  //952-scm-java-client:module \
  //953-events-api/src/main/proto:all \
  //953-events-api:module \
  //953-git-sync-commons/src/main/proto:all \
  //953-git-sync-commons:module \
  //953-yaml-commons:module \
  //954-connector-beans:module \
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

build_bazel_modules=(100-migrator 323-sto-utilities 325-sto-beans 380-cg-graphql 400-rest 410-cg-rest 420-delegate-agent 420-delegate-service \
425-verification-commons 430-cv-nextgen-commons 440-connector-nextgen 440-secret-management-service 445-cg-connectors 450-ce-views 460-capability 490-ce-commons 815-cg-triggers \
865-cg-events 867-polling-contracts 870-cg-orchestration 874-orchestration-delay 878-ng-common-utilities 880-pipeline-cd-commons 884-pms-commons 890-sm-core \
900-git-sync-sdk 910-delegate-service-driver 910-delegate-task-grpc-service 920-delegate-agent-beans 920-delegate-service-beans 930-delegate-tasks 930-ng-core-clients \
932-connector-task 933-ci-commons 940-feature-flag 940-secret-manager-client 947-scim-core 948-cv-nextgen-beans 950-command-library-common 959-common-entities 950-delegate-tasks-beans \
950-events-framework 950-log-client 950-ng-core 950-ng-project-n-orgs 950-wait-engine 951-cg-git-sync 952-remote-observers 952-scm-java-client 953-events-api 953-git-sync-commons \
953-yaml-commons 954-connector-beans 955-cg-yaml 955-delegate-beans 955-filters-sdk 955-outbox-sdk 955-setup-usage-sdk 956-feature-flag-beans 957-cg-beans 958-migration-sdk \
959-file-service-commons 959-psql-database-models 959-timeout-engine 960-api-services 960-continuous-features 960-expression-service 960-ng-core-beans 960-persistence \
960-yaml-sdk 967-walktree-visitor 970-api-services-beans 970-grpc 970-ng-commons 970-rbac-core 970-watcher-beans 979-recaster 980-commons 990-commons-test 999-annotations)

build_bazel_tests=(400-rest 960-persistence)

if [ "${PLATFORM}" == "jenkins" ] && [ ! -z "${DISTRIBUTE_TESTING_WORKER}" ]; then
    bash scripts/bazel/testDistribute.sh
fi

# Enable caching by default. Turn it off by exporting CACHE_TEST_RESULTS=no
# to generate full call-graph for Test Intelligence
if [[ ! -z "${CACHE_TEST_RESULTS}" ]]; then
  export CACHE_TEST_RESULTS_ARG=--cache_test_results=${CACHE_TEST_RESULTS}
fi

echo "INFO: BAZEL_ARGUMENTS: ${BAZEL_ARGUMENTS}"
bazel ${bazelrc} build ${BAZEL_ARGUMENTS} --remote_download_outputs=all -- //:resource $BAZEL_MODULES `bazel query "//...:*" | grep "module_deploy.jar"`

cat ${BAZEL_DIRS}/out/stable-status.txt
cat ${BAZEL_DIRS}/out/volatile-status.txt

for module in ${build_bazel_modules[@]}
  do
    echo "INFO: build_bazel_module ${module}"
    build_bazel_module ${module}
  done

for module in ${build_bazel_tests[@]}
  do
    echo "INFO: build_bazel_tests ${module}"
    build_bazel_tests ${module}
  done

build_proto_module ciengine product/ci/engine/proto
build_proto_module ciscm product/ci/scm/proto

bazel ${bazelrc} run ${BAZEL_ARGUMENTS} //001-microservice-intfc-tool:module | grep "Codebase Hash:" > protocol.info

if [ "${PLATFORM}" == "jenkins" ]; then
 build_protocol_info pipeline-service pipeline-service
 build_protocol_info 332-ci-manager ci-manager
fi

if [ "${RUN_BAZEL_TESTS}" == "true" ]; then
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -- //... -//product/... -//commons/... \
  && bazel ${bazelrc} test ${BAZEL_ARGUMENTS} ${CACHE_TEST_RESULTS_ARG} --keep_going --define=HARNESS_ARGS=${HARNESS_ARGS} -- \
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
