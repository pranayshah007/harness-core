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
apt-get install jq -y

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


if [ "$APPD_ENABLED" == "true" ]; then
export TOKEN=$(curl -X POST -d '{"username": "'"$APPD_USERNAME"'", "password": "'"$APPD_PASSWORD"'", "scopes": ["download"]}' https://identity.msrv.saas.appdynamics.com/v2.0/oauth/token | jq -r '.access_token') && \
        curl -L -O \
        -H "Authorization: Bearer $TOKEN" \
        "https://download.appdynamics.com/download/prox/download-file/golang-sdk/4.5.2.0/golang-sdk-x64-linux-4.5.2.0.tbz2" && \
        tar xjf golang-sdk-x64-linux-4.5.2.0.tbz2 --no-same-owner && \
        mv appdynamics $GOROOT/src && \
        rm golang-sdk-x64-linux-4.5.2.0.tbz2
fi

cd queue-service/hsqs

go install appdynamics
go build -tags=appdynamics -buildvcs=false

echo BUILD_NO=$BUILD  >> build.properties

cp hsqs /root/.cache/bazel/hsqs
cp build.properties /root/.cache/bazel/build.properties

cp $GOROOT/src/appdynamics/lib/libappdynamics.so /root/.cache/bazel/libappdynamics.so
