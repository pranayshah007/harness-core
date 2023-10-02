#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist/idp-service
cd dist/idp-service

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/idp-service/src/main/java/io/harness/idp/app/module_deploy.jar idp-service-capsule.jar
cp ../../idp-service/config/config.yml .
cp ../../idp-service/config/keystore.jks .
cp ../../idp-service/config/key.pem .
cp ../../idp-service/config/cert.pem .
cp ../../idp-service/src/main/resources/redisson-jcache.yaml .
cp ../../idp-service/src/main/resources/enterprise-redisson-jcache.yaml .
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .

cp ../../idp-service/build/container/Dockerfile-idp-service-cie-jdk ./Dockerfile-cie-jdk

cp -r ../../idp-service/build/container/scripts/ .

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ../../idp-service/config/jfr/default.jfc .
cp ../../idp-service/config/jfr/profile.jfc .

java -jar idp-service-capsule.jar scan-classpath-metadata
echo $PWD

cd ../..

echo ${IMAGE_TAG}
