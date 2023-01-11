#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist
cd dist

cd ..

mkdir -p dist/audit-event-streaming
cd dist/audit-event-streaming

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
cp ${HOME}/.bazel-dirs/bin/audit-event-streaming/service/module_deploy.jar audit-event-streaming-capsule.jar

cp ../../audit-event-streaming/service/src/main/resources/application.yml .
cp ../../audit-event-streaming/service/src/main/resources/keystore.jks .
cp ../../audit-event-streaming/service/src/main/resources/key.pem .
cp ../../audit-event-streaming/service/src/main/resources/cert.pem .
cp ../../audit-event-streaming/service/src/main/resources/jfr/default.jfc .
cp ../../audit-event-streaming/service/src/main/resources/jfr/profile.jfc .
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp ../../audit-event-streaming/build/container/Dockerfile-audit-event-streaming-cie-jdk ./Dockerfile-cie-jdk
cp -r ../../audit-event-streaming/build/container/scripts/ .

java -jar audit-event-streaming-capsule.jar scan-classpath-metadata

cd ../..
