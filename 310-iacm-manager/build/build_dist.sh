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

mkdir -p dist/iacm-manager-service
cd dist/iacm-manager-service

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/310-iacm-manager/module_deploy.jar iacm-manager-capsule.jar
cp ../../310-iacm-manager/iacm-manager-config.yml .
cp ../../keystore.jks .
  cp ../../310-iacm-manager/key.pem .
cp ../../310-iacm-manager/cert.pem .
cp ../../310-iacm-manager/src/main/resources/redisson-jcache.yaml .
cp ../../310-iacm-manager/src/main/resources/enterprise-redisson-jcache.yaml .

cp ../../310-iacm-manager/build/container/Dockerfile-iacmmanager-service-jenkins-k8-openjdk ./Dockerfile
cp ../../310-iacm-manager/build/container/Dockerfile-iacmmanager-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp ../../310-iacm-manager/build/container/Dockerfile-iacmmanager-ubi ./Dockerfile-gcr-ubi
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../310-iacm-manager/build/container/scripts/ .
java -jar iacm-manager-capsule scan-classpath-metadata

cd ../..
