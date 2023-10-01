#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist/ssca-manager ;
cd dist/ssca-manager

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi


cp ${HOME}/.bazel-dirs/bin/ssca-manager/service/module_deploy.jar ssca-manager-capsule.jar
cp ../../ssca-manager/config/ssca-manager-config.yml .
cp ../../ssca-manager/build/container/Dockerfile-ssca-manager-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp ../../ssca-manager/service/src/main/resources/jfr/default.jfc .
cp ../../ssca-manager/service/src/main/resources/jfr/profile.jfc .

cp ../../ssca-manager/build/container/Dockerfile-ssca-manager-cie-jdk ./Dockerfile-cie-jdk

cp -R ../../ssca-manager/build/container/scripts/ .
cp ../../protocol.info .

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

java -jar ssca-manager-capsule.jar scan-classpath-metadata
echo $PWD

cd ../..

echo ${IMAGE_TAG}