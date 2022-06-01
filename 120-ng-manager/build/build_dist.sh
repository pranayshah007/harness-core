#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist/ng-manager
cd dist/ng-manager

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

BAZEL_BIN="${HOME}/.bazel-dirs/bin"

cp ${BAZEL_BIN}/120-ng-manager/module_deploy.jar ng-manager-capsule.jar
cp ../../120-ng-manager/config.yml .
cp ../../keystore.jks .
cp ../../120-ng-manager/key.pem .
cp ../../120-ng-manager/cert.pem .
cp ../../120-ng-manager/src/main/resources/redisson-jcache.yaml .

cp ../../dockerization/ng-manager/Dockerfile-ng-manager-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/ng-manager/Dockerfile-ng-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/ng-manager/scripts/ .

cp ../../protocol.info .
java -jar ng-manager-capsule.jar scan-classpath-metadata

cd ../..
