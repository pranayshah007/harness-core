// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

export BUILD_PURPOSE=RELEASE
touch bazel-credentials.bzl
touch test-util.bzl
echo "JFROG_USERNAME=\"foo\"" >> bazel-credentials.bzl
echo "JFROG_PASSWORD=\"bar\"" >> bazel-credentials.bzl
echo "DISTRIBUTE_TESTING_WORKER=0" >> test-util.bzl
echo "DISTRIBUTE_TESTING_WORKERS=1" >> test-util.bzl
echo "OPTIMIZED_PACKAGE_TESTS=0" >> test-util.bzl

apt-get update -y
apt install curl -y
curl -O https://dl.google.com/go/go1.19.8.linux-amd64.tar.gz
tar -xvf go1.19.8.linux-amd64.tar.gz
mv go/ /usr/local/
export GOROOT=/usr/local/go
export GOPATH=/usr/local
export PATH=$PATH:/usr/local/go/bin
apt-get install patch git gcc openjdk-11-jdk -y
go install github.com/bazelbuild/bazelisk@latest
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export BAZEL_BIN=$(bazelisk info bazel-bin)/product/log-service
apt-get install build-essential -y
apt-get install zlib1g-dev -y

bazelisk build //product/log-service/... --define=ABSOLUTE_JAVABASE=$JAVA_HOME --javabase=@bazel_tools//tools/jdk:absolute_javabase --host_javabase=@bazel_tools//tools/jdk:absolute_javabase --java_toolchain=@bazel_tools//tools/jdk:toolchain_vanilla --host_java_toolchain=@bazel_tools//tools/jdk:toolchain_vanilla
bazelisk test //product/log-service/... --define=ABSOLUTE_JAVABASE=$JAVA_HOME --javabase=@bazel_tools//tools/jdk:absolute_javabase --host_javabase=@bazel_tools//tools/jdk:absolute_javabase --java_toolchain=@bazel_tools//tools/jdk:toolchain_vanilla --host_java_toolchain=@bazel_tools//tools/jdk:toolchain_vanilla
bazelisk build --platforms=@io_bazel_rules_go//go/toolchain:linux_amd64 --define=ABSOLUTE_JAVABASE=$JAVA_HOME --javabase=@bazel_tools//tools/jdk:absolute_javabase --host_javabase=@bazel_tools//tools/jdk:absolute_javabase --java_toolchain=@bazel_tools//tools/jdk:toolchain_vanilla --host_java_toolchain=@bazel_tools//tools/jdk:toolchain_vanilla //product/log-service/...
cp $(bazelisk info bazel-bin)/product/log-service/log-service_/log-service product/log-service/log-service

echo $BAZEL_BIN
