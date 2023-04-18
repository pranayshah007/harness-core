#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist/debezium-service
cd dist/debezium-service

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/debezium-service/service/module_deploy.jar debezium-service-capsule.jar
cp ../../debezium-service/config/config.yml .
cp ../../debezium-service/service/src/main/resources/redisson-jcache.yaml .

cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .

cp ../../debezium-service/build/container/Dockerfile-debezium-service-cie-jdk ./Dockerfile-cie-jdk

cp -r ../../debezium-service/build/container/scripts/ .
cp ../../protocol.info .

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ../../debezium-service/config/jfr/default.jfc .
cp ../../debezium-service/config/jfr/profile.jfc .

java -jar debezium-service-capsule.jar scan-classpath-metadata
echo $PWD

cd ../..

echo ${IMAGE_TAG}
