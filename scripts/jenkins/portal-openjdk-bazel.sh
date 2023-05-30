# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

### Dockerization of Manager ####### Doc
set -e

SCRIPT_DIR="$(dirname $0)"
source "${SCRIPT_DIR}/portal-openjdk-bazel-commons.sh"

prepare_to_copy_jars

copy_cg_manager_jars

copy_event_server_jars

copy_change_data_capture_jars

copy_ng_manager_jars

copy_ng_dashboard_jars

copy_dms_jars

mkdir -p dist/cv-nextgen ;
cd dist/cv-nextgen

cp ${HOME}/.bazel-dirs/bin/srm-service/modules/cv-nextgen-service/service/module_deploy.jar cv-nextgen-capsule.jar
cp ../../srm-service/config/keystore.jks .
cp ../../srm-service/config/cv-nextgen-config.yml .
cp ../../srm-service/modules/cv-nextgen-service/service/src/main/resources/redisson-jcache.yaml .
cp ../../srm-service/modules/cv-nextgen-service/service/src/main/resources/enterprise-redisson-jcache.yaml .

cp ../../srm-service/build/container/Dockerfile-cv-nextgen-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -R ../../srm-service/build/container/scripts/ .

cp ../../protocol.info .
cp ../../srm-service/config/jfr/default.jfc .
cp ../../srm-service/config/jfr/profile.jfc .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

java -jar cv-nextgen-capsule.jar scan-classpath-metadata

cd ../..

mkdir -p dist/verification-service ;
cd dist/verification-service

cp ${HOME}/.bazel-dirs/bin/270-verification/module_deploy.jar verification-capsule.jar
cp ../../270-verification/keystore.jks .
cp ../../270-verification/verification-config.yml .

cp ../../dockerization/verification/Dockerfile-verification-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -R ../../dockerization/verification/scripts/ .

cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/command-library-server ;
cd dist/command-library-server

cp ${HOME}/.bazel-dirs/bin/210-command-library-server/module_deploy.jar command-library-app-capsule.jar
cp ../../210-command-library-server/keystore.jks .
cp ../../210-command-library-server/command-library-server-config.yml .

cp ../../dockerization/command-library-server/Dockerfile-command-library-server-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -R ../../dockerization/command-library-server/scripts/ .

cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

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

mkdir -p dist/watcher
cp ${HOME}/.bazel-dirs/bin/960-watcher/module_deploy.jar dist/watcher/watcher-capsule.jar

jarsigner -tsa http://timestamp.digicert.com -storetype pkcs12 -keystore ${KEY_STORE} -storepass ${KEY_STORE_PASSWORD} dist/watcher/watcher-capsule.jar ${KEY_STORE_ALIAS}
cp dist/watcher/watcher-capsule.jar watcher-${VERSION}.jar
cp protocol.info dist/watcher/.

mkdir -p dist/disconnected_on_prem_pov
cd dist/disconnected_on_prem_pov
cp -r ../../on-prem/harness_disconnected_on_prem_pov_final .
cp -r ../../on-prem/disconnected_on_prem_pov_installer .
tar -zcvf disconnected_on_prem_pov_template.tar.gz *
cd ../..
cp dist/disconnected_on_prem_pov/disconnected_on_prem_pov_template.tar.gz disconnected_on_prem_pov_template.tar.gz

mkdir -p dist/disconnected_on_prem_k8s
cd dist/disconnected_on_prem_k8s
cp -r ../../on-prem/kubernetes_installer .
tar -zcvf disconnected_on_prem_k8s_installer_builder.tar.gz *
cd ../..
cp dist/disconnected_on_prem_k8s/disconnected_on_prem_k8s_installer_builder.tar.gz disconnected_on_prem_k8s_installer_builder.tar.gz

mkdir -p dist/test
cd dist/test
cp ${HOME}/.bazel-dirs/bin/160-model-gen-tool/module_deploy.jar model-gen-tool-capsule.jar
cp ../../160-model-gen-tool/config-datagen.yml .
cd ../..

mkdir -p dist/delegate-proxy
cd dist/delegate-proxy
cp ../../dockerization/delegate-proxy/setup.sh .
cp ../../dockerization/delegate-proxy/Dockerfile .
cp ../../dockerization/delegate-proxy/Dockerfile-gcr .
cp ../../dockerization/delegate-proxy/nginx.conf .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
cd ../..

mkdir -p dist/ci-manager
cd dist/ci-manager

cp ${HOME}/.bazel-dirs/bin/332-ci-manager/app/module_deploy.jar ci-manager-capsule.jar
cp ../../332-ci-manager/config/ci-manager-config.yml .
cp ../../keystore.jks .
cp ../../332-ci-manager/config/key.pem .
cp ../../332-ci-manager/config/cert.pem .
cp ../../332-ci-manager/service/src/main/resources/redisson-jcache.yaml .
cp ../../332-ci-manager/service/src/main/resources/enterprise-redisson-jcache.yaml .

cp ../../dockerization/ci-manager/Dockerfile-ci-manager-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../dockerization/ci-manager/scripts/ .

cp ../../ci-manager-protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
java -jar ci-manager-capsule.jar scan-classpath-metadata

cd ../..

mkdir -p dist/platform-service
cd dist/platform-service

cp ${HOME}/.bazel-dirs/bin/platform-service/service/module_deploy.jar platform-service-capsule.jar
cp ../../platform-service/config/config.yml .
cp ../../platform-service/config/keystore.jks .
cp ../../platform-service/config/key.pem .
cp ../../platform-service/config/cert.pem .
cp ../../dockerization/platform-service/Dockerfile-platform-service-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../dockerization/platform-service/scripts .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
java -jar platform-service-capsule.jar scan-classpath-metadata

cd ../..

mkdir -p dist/pipeline-service
cd dist/pipeline-service

cp ${HOME}/.bazel-dirs/bin/pipeline-service/service/module_deploy.jar pipeline-service-capsule.jar
cp ../../pipeline-service/config/config.yml .
cp ../../pipeline-service/config/keystore.jks .
cp ../../pipeline-service/config/key.pem .
cp ../../pipeline-service/config/cert.pem .
cp ../../pipeline-service/service/src/main/resources/redisson-jcache.yaml .
cp ../../pipeline-service/service/src/main/resources/enterprise-redisson-jcache.yaml .

cp ../../dockerization/pipeline-service/Dockerfile-pipeline-service-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../dockerization/pipeline-service/scripts/ .
cp ../../pipeline-service-protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
java -jar pipeline-service-capsule.jar scan-classpath-metadata

cd ../..

mkdir -p dist/debezium-service
cd dist/debezium-service

cp ${HOME}/.bazel-dirs/bin/debezium-service/service/module_deploy.jar debezium-service-capsule.jar
cp ../../debezium-service/config/config.yml .
cp ../../debezium-service/service/src/main/resources/redisson-jcache.yaml .

cp ../../dockerization/debezium-service/Dockerfile-debezium-service-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../dockerization/debezium-service/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/template-service
cd dist/template-service

cp ${HOME}/.bazel-dirs/bin/template-service/service/module_deploy.jar template-service-capsule.jar
cp ../../template-service/config/config.yml .
cp ../../template-service/config/keystore.jks .
cp ../../template-service/config/key.pem .
cp ../../template-service/config/cert.pem .
cp ../../template-service/service/src/main/resources/redisson-jcache.yaml .
cp ../../template-service/service/src/main/resources/jfr/default.jfc .
cp ../../template-service/service/src/main/resources/jfr/profile.jfc .

cp ../../dockerization/template-service/Dockerfile-template-service-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../dockerization/template-service/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/eventsapi-monitor
cd dist/eventsapi-monitor

cp ${HOME}/.bazel-dirs/bin/950-events-framework-monitor/module_deploy.jar eventsapi-monitor-capsule.jar
cp ../../950-events-framework-monitor/config.yml .
cp ../../950-events-framework-monitor/redis/* .
cp ../../dockerization/eventsapi-monitor/Dockerfile-eventsapi-monitor-cie-jdk ./Dockerfile-cie-jdk
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../dockerization/eventsapi-monitor/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/migrator ;
cd dist/migrator

cp ${BAZEL_BIN}/100-migrator/module_deploy.jar migrator-capsule.jar
cp ../../keystore.jks .
cp ../../360-cg-manager/key.pem .
cp ../../360-cg-manager/cert.pem .
cp ../../360-cg-manager/newrelic.yml .
cp ../../100-migrator/config.yml .
cp ../../400-rest/src/main/resources/redisson-jcache.yaml .
cp ../../400-rest/src/main/resources/jfr/default.jfc .
cp ../../400-rest/src/main/resources/jfr/profile.jfc .

cp ../../dockerization/migrator/Dockerfile-manager-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/migrator/Dockerfile-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
cp -r ../../dockerization/migrator/scripts/ .
mv scripts/start_process_bazel.sh scripts/start_process.sh

copy_common_files

java -jar migrator-capsule.jar scan-classpath-metadata

cd ../..