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

mkdir -p dist/sto-manager-service
cd dist/sto-manager-service

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/315-sto-manager/module_deploy.jar sto-manager-capsule.jar
cp ../../315-sto-manager/sto-manager-config.yml .
cp ../../keystore.jks .
cp ../../315-sto-manager/key.pem .
cp ../../315-sto-manager/cert.pem .
cp ../../315-sto-manager/src/main/resources/redisson-jcache.yaml .

cp ../../315-sto-manager/build/container/Dockerfile-stomanager-service-jenkins-k8-openjdk ./Dockerfile
cp ../../315-sto-manager/build/container/Dockerfile-stomanager-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../315-sto-manager/build/container/scripts/ .
java -jar sto-manager-capsule scan-classpath-metadata

cd ../..
