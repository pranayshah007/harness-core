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

mkdir -p dist/accesscontrol-service
cd dist/accesscontrol-service

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/access-control/service/module_deploy.jar accesscontrol-service-capsule.jar
cp ../../access-control/config/config.yml .
cp ../../access-control/config/keystore.jks .
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp ../../access-control/build/container/Dockerfile-accesscontrol-service-jenkins-k8-openjdk ./Dockerfile
cp ../../access-control/build/container/Dockerfile-accesscontrol-service-cie-jdk ./Dockerfile-cie-jdk
cp ../../access-control/build/container/Dockerfile-accesscontrol-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../access-control/build/container/scripts/ .
java -jar accesscontrol-service-capsule.jar scan-classpath-metadata

cd ../..
