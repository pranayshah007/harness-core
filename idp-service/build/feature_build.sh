# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

echo $GCP_KEY | base64 -d > /tmp/storage_secret.json
echo $HARNESS_WILD_CERT | base64 -d > /harness/harness_wild.p12

export KEY_STORE="/harness/harness_wild.p12"
export KEY_STORE_PASSWORD=$KEYSTORE_PASS

export BUILD_NAME=$(git rev-parse --abbrev-ref HEAD)
export BUILD_BAZEL_DEPLOY_JAR=true

export IMAGE_TAG=$VERSION-$BUILD_NAME
export GCP_KEY="/tmp/storage_secret.json"

#export VERSION_FILE=build.properties
#export PATCH=`cat ${VERSION_FILE} | grep 'build.patch=' | sed -e 's: *build.patch=::g'`

echo "--------------------------------------"
echo $JDK $VERSION $PURPOSE $BUILD_NAME $IMAGE_TAG
echo "--------------------------------------"

scripts/bazel/generate_credentials.sh
yum install zlib-devel -y


export BAZEL_ARGUMENTS="--show_timestamps --announce_rc --experimental_convenience_symlinks=normal --symlink_prefix=${HOME}/.bazel-dirs/"
export bazelrc="--bazelrc=bazelrc.remote"

# Skipping upload result to remote-cache.
echo "" >> bazelrc.remote
echo "build --remote_upload_local_results=false" >> bazelrc.remote


bazel ${bazelrc} build //idp-service/src/main/java/io/harness/idp/app:module //idp-service/src/main/java/io/harness/idp/app:module_deploy.jar ${BAZEL_ARGUMENTS} --remote_download_outputs=all


#Download the apln jar
curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

mkdir -p dist/idp-service
cd dist/idp-service

cp ${HOME}/.bazel-dirs/bin/idp-service/src/main/java/io/harness/idp/app/module_deploy.jar idp-service-capsule.jar
cp ../../idp-service/config/config.yml .

cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../dockerization/idp-service/Dockerfile-idp-service-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../idp-service/container/scripts/ .
echo "" > protocol.info

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

echo "INFO: list the jars built"
find . -name "*.jar"