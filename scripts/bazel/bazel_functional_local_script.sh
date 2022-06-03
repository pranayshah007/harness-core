#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

BAZEL_ARGUMENTS=
BAZEL_TEST_ARGUMENTS=
GCP=

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --experimental_convenience_symlinks=normal --symlink_prefix=${BAZEL_DIRS}/"
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --spawn_strategy=standalone"
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --test_timeout=180"
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --test_output=all"
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --cache_test_results=no"
BAZEL_TEST_ARGUMENTS="${BAZEL_TEST_ARGUMENTS} --test_verbose_timeout_warnings"


  bazel build ${GCP} ${BAZEL_ARGUMENTS} -- //200-functional-test/...

  bazel run ${GCP} ${BAZEL_ARGUMENTS} 230-model-test:app
  MANAGER_PID=`ps ax | grep 'java' | grep '360-cg-manager/modified_config' |  awk '{print $1}'`

  bazel test --keep_going ${GCP} ${BAZEL_ARGUMENTS} --jobs=3 ${BAZEL_TEST_ARGUMENTS} -- //200-functional-test:io.harness.functional.DummyFirstFunctionalTest || true

  java -Xmx4096m -XX:+HeapDumpOnOutOfMemoryError -Xloggc:mygclogfilename.gc -XX:+UseParallelGC \
    -XX:MaxGCPauseMillis=500 -jar /harness/bazel-out/k8-fastbuild/bin/260-delegate/module_deploy.jar /harness/260-delegate/config-delegate.yml &> /tmp/delegate.log &
  DELEGATE_PID=$!

#  #TODO: https://harness.atlassian.net/browse/BT-434
  bazel test --keep_going ${GCP} ${BAZEL_ARGUMENTS} --jobs=3 ${BAZEL_TEST_ARGUMENTS} -- //200-functional-test/... \
    -//200-functional-test:io.harness.functional.nas.NASBuildWorkflowExecutionTest \
    -//200-functional-test:io.harness.functional.nas.NASWorkflowExecutionTest || true

  echo "INFO: MANAGER_PID = $MANAGER_PID"
  echo "INFO: DELEGATE_PID = $DELEGATE_PID"

  kill -9 $MANAGER_PID || true
  kill -9 $DELEGATE_PID || true
