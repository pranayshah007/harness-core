#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set +ex
mkdir -p dist/${MODULE}
cd dist/${MODULE}
curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${BUILD_PURPOSE} ]
then
    echo ${BUILD_PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/${MODULE}/service/module_deploy.jar ${MODULE}-capsule.jar
cp ${HOME}/.bazel-dirs/bin/${MODULE}/app/module_deploy.jar ${MODULE}-capsule.jar
cp /harness/${MODULE}/config/keystore.jks .
cp /harness/${MODULE}/config/key.pem .
cp /harness/${MODULE}/config/cert.pem .

if [[ ${MODULE} == 332-ci-manager ]];then
  cp /harness/${MODULE}/config/ci-manager-config.yml ./config.yml
else
  cp /harness/${MODULE}/config/config.yml .
fi

cp /harness/${MODULE}/service/src/main/resources/redisson-jcache.yaml .
cp /harness/${MODULE}/service/src/main/resources/enterprise-redisson-jcache.yaml .

cp /harness/dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp /harness/dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp /harness/scripts/cie/service-script/build/container/Dockerfile-${MODULE}-cie-jdk ./Dockerfile-cie-jdk
cp -r /harness/${MODULE}/build/container/scripts/ .
cp /harness/protocol.info .
cp /harness/${MODULE}/config/jfr/default.jfc .
cp /harness/${MODULE}/config/jfr/profile.jfc .
cp ../../${MODULE}/service/src/main/resources/jfr/default.jfc .
cp ../../${MODULE}/service/src/main/resources/jfr/profile.jfc .
cp ../../${MODULE}/service/src/main/resources/application.yml .
cp ../../${MODULE}/service/src/main/resources/keystore.jks .
cp ../../${MODULE}/service/src/main/resources/key.pem .
cp ../../${MODULE}/service/src/main/resources/cert.pem .

java -jar ${MODULE}-capsule.jar scan-classpath-metadata

if [ -f access-control-capsule.jar ]; then
  mv ./access-control-capsule.jar  ./accesscontrol-service-capsule.jar
fi

cd ../..
echo ${IMAGE_TAG}