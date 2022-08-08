#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist/pipeline-service
cd dist/pipeline-service

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/pipeline-service/service/module_deploy.jar pipeline-service-capsule.jar
cp ../../pipeline-service/config/config.yml .
cp ../../pipeline-service/config/keystore.jks .
cp ../../pipeline-service/config/key.pem .
cp ../../pipeline-service/config/cert.pem .
cp ../../pipeline-service/service/src/main/resources/redisson-jcache.yaml .
cp ../../pipeline-service/service/src/main/resources/enterprise-redisson-jcache.yaml .

cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .

cp ../../pipeline-service/build/container/Dockerfile-pipeline-service-jenkins-k8-openjdk ./Dockerfile
cp ../../pipeline-service/build/container/Dockerfile-pipeline-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp ../../pipeline-service/build/container/Dockerfile-pipeline-service-cie-jdk ./Dockerfile-cie-jdk

cp -r ../../pipeline-service/build/container/scripts/ .
cp ../../pipeline-service-protocol.info .

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
java -jar pipeline-service-capsule.jar scan-classpath-metadata
echo $PWD

cd ../..

echo ${IMAGE_TAG}
