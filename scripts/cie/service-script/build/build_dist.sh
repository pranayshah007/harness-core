#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex
mkdir -p dist/${MODULE}
cd dist/${MODULE}
curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/${MODULE}/service/module_deploy.jar ${MODULE}-capsule.jar
cp ../../../${MODULE}/config/config.yml .
cp ../../../${MODULE}/config/keystore.jks .
cp ../../../${MODULE}/config/key.pem .
cp ../../../${MODULE}/config/cert.pem .
cp ../../../${MODULE}/service/src/main/resources/redisson-jcache.yaml .
cp ../../../${MODULE}/service/src/main/resources/enterprise-redisson-jcache.yaml .
cp ../../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp ../../../${MODULE}/build/container/Dockerfile-${MODULE}-cie-jdk ./Dockerfile-cie-jdk
cp -r ../../../${MODULE}/build/container/scripts/ .
cp ../../../${MODULE}/config/jfr/default.jfc .
cp ../../../${MODULE}/config/jfr/profile.jfc .

java -jar ${MODULE}-capsule.jar scan-classpath-metadata
cd ../..
echo ${IMAGE_TAG}
