# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#Copy the analyser-service jar for uploading on GCP then dockerization

set -e
curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

mkdir -p dist/analyser-service
cd dist/analyser-service

cp ${HOME}/.bazel-dirs/bin/935-analyser-service/module_deploy.jar analyser-service-capsule.jar
cp ../../935-analyser-service/config.yml .
cp ../../935-analyser-service/keystore.jks .
cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../dockerization/analyser-service/Dockerfile-analyser-service-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/analyser-service/Dockerfile-analyser-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/analyser-service/scripts/ .

cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
cd ../..
