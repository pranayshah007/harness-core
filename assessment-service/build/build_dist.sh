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

mkdir -p dist/assessment-service
cd dist/assessment-service

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/assessment/service/module_deploy.jar assessment-service-capsule.jar
cp ../../assessment/config/config.yml .
cp ../../assessment/config/keystore.jks .
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp ../../assessment/build/container/Dockerfile-assessment-service-cie-jdk ./Dockerfile-cie-jdk
cp -r ../../assessment/build/container/scripts/ .
cp ../../assessment/config/jfr/default.jfc .
cp ../../assessment/config/jfr/profile.jfc .
java -jar assessment-service-capsule.jar scan-classpath-metadata

cd ../..
