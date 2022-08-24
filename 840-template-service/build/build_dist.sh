#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist/template-service
cd dist/template-service

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

BAZEL_BIN="${HOME}/.bazel-dirs/bin"

cp ${BAZEL_BIN}/840-template-service/module_deploy.jar template-service-capsule.jar
cp ../../840-template-service/config.yml .
cp ../../keystore.jks .
cp ../../840-template-service/key.pem .
cp ../../840-template-service/cert.pem .
cp ../../840-template-service/src/main/resources/redisson-jcache.yaml .

cp ../../dockerization/template-service/Dockerfile-template-service-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/template-service/Dockerfile-template-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/template-service/scripts/ .

cp ../../protocol.info .
java -jar template-service-capsule.jar scan-classpath-metadata

cd ../..
