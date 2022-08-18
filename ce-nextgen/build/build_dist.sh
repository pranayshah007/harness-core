# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

@@ -0,0 +1,34 @@
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

mkdir -p dist/ce-nextgen
cd dist/ce-nextgen

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/ce-nextgen/service/module_deploy.jar ce-nextgen-capsule.jar
cp ../../ce-nextgen/config/keystore.jks .
cp ../../ce-nextgen/config/config.yml .
cp ../../dockerization/ce-nextgen/Dockerfile-ce-nextgen-cie-jdk Dockerfile-cie-jdk
cp ../../dockerization/ce-nextgen/Dockerfile-ce-nextgen-jenkins-k8-openjdk Dockerfile
cp ../../dockerization/ce-nextgen/Dockerfile-ce-nextgen-jenkins-k8-gcr-openjdk Dockerfile-gcr
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../dockerization/ce-nextgen/scripts/ .

cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
	  echo ${PURPOSE} > purpose.txt
fi

cd ../..
