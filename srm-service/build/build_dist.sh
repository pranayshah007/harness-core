#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist/cv-nextgen ;
cd dist/cv-nextgen

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi


cp ${HOME}/.bazel-dirs/bin/srm-service/modules/cv-nextgen-service/service/module_deploy.jar cv-nextgen-capsule.jar
cp ../../srm-service/config/keystore.jks .
cp ../../srm-service/config/cv-nextgen-config.yml .
cp ../../srm-service/modules/cv-nextgen-service/service/src/main/resources/redisson-jcache.yaml .
cp ../../srm-service/modules/cv-nextgen-service/service/src/main/resources/enterprise-redisson-jcache.yaml .
cp ../../srm-service/build/container/Dockerfile-cv-nextgen-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .

cp ../../srm-service/build/container/Dockerfile-cv-nextgen-cie-jdk ./Dockerfile-cie-jdk

cp -R ../../srm-service/build/container/scripts/ .
cp ../../protocol.info .

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

java -jar cv-nextgen-capsule.jar scan-classpath-metadata
echo $PWD

cd ../..

echo ${IMAGE_TAG}