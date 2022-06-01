#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

mkdir -p dist/manager
cd dist/manager

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
  echo ${PURPOSE} > purpose.txt
fi

BAZEL_BIN="${HOME}/.bazel-dirs/bin"

cp ${BAZEL_BIN}/360-cg-manager/module_deploy.jar rest-capsule.jar
cp ../../400-rest/src/main/resources/hazelcast.xml .
cp ../../keystore.jks .
cp ../../360-cg-manager/key.pem .
cp ../../360-cg-manager/cert.pem .
cp ../../360-cg-manager/newrelic.yml .
cp ../../360-cg-manager/config.yml .
cp ../../400-rest/src/main/resources/redisson-jcache.yaml .

cp ../../dockerization/manager/Dockerfile-manager-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/manager/Dockerfile-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/manager/scripts/ .
mv scripts/start_process_bazel.sh scripts/start_process.sh

cp ../../protocol.info .
java -jar rest-capsule.jar scan-classpath-metadata
cd ../..

mkdir -p dist/watcher
cp ${HOME}/.bazel-dirs/bin/960-watcher/module_deploy.jar dist/watcher/watcher-capsule.jar
jarsigner -tsa http://timestamp.digicert.com -storetype pkcs12 -keystore ${KEY_STORE} -storepass ${KEY_STORE_PASSWORD} dist/watcher/watcher-capsule.jar ${KEY_STORE_ALIAS}
cp dist/watcher/watcher-capsule.jar watcher-${VERSION}.jar
cp protocol.info dist/watcher/.

mkdir -p dist/test
cd dist/test
cp ${HOME}/.bazel-dirs/bin/160-model-gen-tool/module_deploy.jar model-gen-tool-capsule.jar
cp ../../160-model-gen-tool/config-datagen.yml .
cd ../..

mkdir -p dist/delegate
if [ ${USE_MAVEN_DELEGATE} == "true" ]; then
echo "building maven 260-delegate"
cp 260-delegate/target/delegate-capsule.jar dist/delegate/delegate-capsule.jar
else
echo "building bazel 260-delegate"
cp ${HOME}/.bazel-dirs/bin/260-delegate/module_deploy.jar dist/delegate/delegate-capsule.jar
fi

cp 260-delegate/config-delegate.yml dist/delegate/config-delegate.yml
jarsigner -tsa http://timestamp.digicert.com -storetype pkcs12 -keystore ${KEY_STORE} -storepass ${KEY_STORE_PASSWORD} dist/delegate/delegate-capsule.jar ${KEY_STORE_ALIAS}
cp dist/delegate/delegate-capsule.jar delegate-${VERSION}.jar
cp protocol.info dist/delegate/.
cd ../..

echo ${IMAGE_TAG}
