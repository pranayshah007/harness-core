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

cp ${BAZEL_BIN}/template-service/service/module_deploy.jar template-service-capsule.jar
cp ../../template-service/config/config.yml .
cp ../../template-service/config/keystore.jks .
cp ../../template-service/config/key.pem .
cp ../../template-service/config/cert.pem .
cp ../../template-service/service/src/main/resources/redisson-jcache.yaml .
cp ../../template-service/service/src/main/resources/jfr/default.jfc .
cp ../../template-service/service/src/main/resources/jfr/profile.jfc .

cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .

cp ../../template-service/build/container/Dockerfile-template-service-cie-jdk ./Dockerfile-cie-jdk
cp -r ../../template-service/build/container/scripts/ .

cp ../../protocol.info .
java -jar template-service-capsule.jar scan-classpath-metadata

cd ../..
