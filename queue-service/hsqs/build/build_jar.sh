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

touch bazel-credentials.bzl
echo "JFROG_USERNAME=\"foo\"" >> bazel-credentials.bzl
echo "JFROG_PASSWORD=\"bar\"" >> bazel-credentials.bzl

apt-get update -y
apt install curl -y
curl -O https://dl.google.com/go/go1.18.linux-amd64.tar.gz
tar -xvf go1.18.linux-amd64.tar.gz
mv go/ /usr/local/
rm -rf go1.18.linux-amd64.tar.gz
export GOROOT=/usr/local/go
export GOPATH=/usr/local
export PATH=$PATH:/usr/local/go/bin
apt-get install patch git gcc openjdk-11-jdk -y

go install github.com/bazelbuild/bazelisk@latest
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
apt-get install build-essential -y
apt-get install zlib1g-dev -y

BAZEL_ARGUMENTS="--platforms=@io_bazel_rules_go//go/toolchain:linux_amd64 --define=ABSOLUTE_JAVABASE=${JAVA_HOME} --javabase=@bazel_tools//tools/jdk:absolute_javabase --host_javabase=@bazel_tools//tools/jdk:absolute_javabase --java_toolchain=@bazel_tools//tools/jdk:toolchain_vanilla --host_java_toolchain=@bazel_tools//tools/jdk:toolchain_vanilla"
bazelisk build ${BAZEL_ARGUMENTS} //queue-service/hsqs/...

cp $(bazelisk info bazel-bin)/queue-service/hsqs/hsqs_/hsqs queue-service/hsqs/hsqs