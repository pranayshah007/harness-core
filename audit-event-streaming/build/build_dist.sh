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

mkdir -p dist/audit-event-streaming
cd dist/audit-event-streaming

echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cp ${HOME}/.bazel-dirs/bin/audit-event-streaming/service/module_deploy.jar audit-event-streaming-capsule.jar
cp ../../audit-event-streaming/service/audit-event-streaming-config.yml .
cp ../../audit-event-streaming/build/container/Dockerfile-audit-event-streaming-cie-jdk ./Dockerfile-audit-event-streaming-cie-jdk
cp -r ../../audit-event-streaming/build/container/scripts/ .
cp ../../audit-event-streaming/build/container/inject-onprem-apm-bins-into-dockerimage.sh ./inject-onprem-apm-bins-into-dockerimage.sh
cp ../../audit-event-streaming/build/container/inject-saas-apm-bins-into-dockerimage.sh ./inject-saas-apm-bins-into-dockerimage.sh

cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
	  echo ${PURPOSE} > purpose.txt
fi

cd ../..
