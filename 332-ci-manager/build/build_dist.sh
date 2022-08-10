#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# From harness-core/scripts/jenkins/portal-openjdk-bazel.sh

mkdir -p dist/ci-manager
cd dist/ci-manager

cp ${HOME}/.bazel-dirs/bin/332-ci-manager/app/module_deploy.jar ci-manager-capsule.jar
cp ../../332-ci-manager/config/ci-manager-config.yml .
cp ../../keystore.jks .
cp ../../332-ci-manager/config/key.pem .
cp ../../332-ci-manager/config/cert.pem .
cp ../../332-ci-manager/service/src/main/resources/redisson-jcache.yaml .

cp ../../332-ci-manager/build/container/Dockerfile-ci-manager-jenkins-k8-openjdk ./Dockerfile
cp ../../332-ci-manager/build/container/Dockerfile-ci-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp ../../332-ci-manager/build/container/Dockerfile-ci-manager-jenkins-k8-gcr-openjdk-ubi ./Dockerfile-gcr-ubi
cp ../../332-ci-manager/build/container/Dockerfile-ci-manager-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../332-ci-manager/build/container/scripts/ .

cp ../../ci-manager-protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
java -jar ci-manager-capsule.jar scan-classpath-metadata

cd ../..