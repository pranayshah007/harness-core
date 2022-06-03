#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

echo "REPORT_UNUSED = False" > project/flags/report_unused.bzl

bazel build \
  --strict_java_deps=error \
  --host_java_toolchain=@bazel_tools//tools/jdk:toolchain_java11 \
  --java_toolchain=@bazel_tools//tools/jdk:toolchain_java11 \
  $(bazel query 'attr(tags, "harness", //...:*)')

if [ $? -ne 0 ]; then
  exit 1
fi

echo "REPORT_UNUSED = True" > project/flags/report_unused.bzl

echo "# start analyzing ..."
TARGETS=$(bazel query 'attr(tags, "unused_dependency", //...:*)')

bazel build -k \
  --strict_java_deps=error \
  --host_java_toolchain=@bazel_tools//tools/jdk:toolchain_java11 \
  --java_toolchain=@bazel_tools//tools/jdk:toolchain_java11 \
  ${TARGETS} \
  2>&1 | grep "^buildozer 'remove deps\|^# "

echo "REPORT_UNUSED = False" > project/flags/report_unused.bzl
