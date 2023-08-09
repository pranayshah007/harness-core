#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# This script requires JDK, VERSION and PURPOSE as environment variables

BAZEL_BIN="${HOME}/.bazel-dirs/bin"

function prepare_to_copy_jars(){
  mkdir -p dist ;
  cd dist
  ls ../../scripts/jenkins/
  cp -R ../scripts/jenkins/ .
  cd ..

}

function copy_common_files(){
	cp ../../protocol.info .
	echo ${JDK} > jdk.txt
	echo ${VERSION} > version.txt
	if [ ! -z ${PURPOSE} ]
	then
	    echo ${PURPOSE} > purpose.txt
	fi
}

mkdir -p dist/$SERVICE_NAME
cd dist/$SERVICE_NAME

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

#BAZEL_BIN="${HOME}/.bazel-dirs/bin"

function copy_cg_manager_jars(){

	cp ${BAZEL_BIN}/360-cg-manager/module_deploy.jar rest-capsule.jar
	cp ../../keystore.jks .
	cp ../../360-cg-manager/key.pem .
	cp ../../360-cg-manager/cert.pem .
	cp ../../360-cg-manager/newrelic.yml .
	cp ../../360-cg-manager/config.yml .
	cp ../../400-rest/src/main/resources/redisson-jcache.yaml .
	cp ../../400-rest/src/main/resources/jfr/default.jfc .
  cp ../../400-rest/src/main/resources/jfr/profile.jfc .

	cp ../../dockerization/manager/Dockerfile-manager-cie-jdk ./Dockerfile-cie-jdk
	cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
	cp -r ../../dockerization/manager/scripts/ .

  for file in scripts/*.sh; do
      chmod 755 "$file"
  done
	mv scripts/start_process_bazel.sh scripts/start_process.sh

	copy_common_files

	java -jar rest-capsule.jar scan-classpath-metadata

	cd ../..
}

function copy_change_data_capture_jars(){

	cp ${BAZEL_BIN}/110-change-data-capture/module_deploy.jar change-data-capture.jar
	cp ../../110-change-data-capture/config.yml .
	cp ../../dockerization/change-data-capture/Dockerfile-change-data-capture-cie-jdk Dockerfile-cie-jdk
	cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
	cp -r ../../dockerization/change-data-capture/scripts/ .

	copy_common_files

	cd ../..
}

function copy_ng_dashboard_jars(){

	cp ${BAZEL_BIN}/290-dashboard-service/module_deploy.jar ng-dashboard-service.jar
	cp ../../290-dashboard-service/config.yml .
	cp ../../290-dashboard-service/src/main/resources/jfr/default.jfc .
	cp ../../290-dashboard-service/src/main/resources/jfr/profile.jfc .
	cp ../../dockerization/ng-dashboard-service/Dockerfile-ng-dashboard-cie-jdk Dockerfile-cie-jdk
	cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
	cp -r ../../dockerization/ng-dashboard-service/scripts/ .

	copy_common_files

	cd ../..
}

function copy_dms_jars(){

	cp ${HOME}/.bazel-dirs/bin/419-delegate-service-app/src/main/java/io/harness/dms/app/module_deploy.jar delegate-service-capsule.jar
  cp ../../419-delegate-service-app/config/config.yml .
  cp ../../419-delegate-service-app/config/redisson-jcache.yaml .

  cp ../../dockerization/delegate-service-app/Dockerfile-delegate-service-app-cie-jdk ./Dockerfile-cie-jdk
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -r ../../419-delegate-service-app/container/scripts/ .

	copy_common_files

	cd ../..
}

function copy_migrator_jars(){

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

}

function copy_eventsapi-monitor_jars(){
  cp ${HOME}/.bazel-dirs/bin/950-events-framework-monitor/module_deploy.jar eventsapi-monitor-capsule.jar
  cp ../../950-events-framework-monitor/config.yml .
  cp ../../950-events-framework-monitor/redis/* .
  cp ../../dockerization/eventsapi-monitor/Dockerfile-eventsapi-monitor-cie-jdk ./Dockerfile-cie-jdk
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -r ../../dockerization/eventsapi-monitor/scripts/ .

  copy_common_files

  cd ../..
}

function copy_delegate_proxy_jars(){

  cp ../../dockerization/delegate-proxy/setup.sh .
  cp ../../dockerization/delegate-proxy/Dockerfile .
  cp ../../dockerization/delegate-proxy/Dockerfile-gcr .
  cp ../../dockerization/delegate-proxy/nginx.conf .
  copy_common_files
  cd ../..

}

function copy_command_library_server_jars(){

  cp ${HOME}/.bazel-dirs/bin/210-command-library-server/module_deploy.jar command-library-app-capsule.jar
  cp ../../210-command-library-server/keystore.jks .
  cp ../../210-command-library-server/command-library-server-config.yml .
  cp ../../dockerization/command-library-server/Dockerfile-command-library-server-cie-jdk ./Dockerfile-cie-jdk
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -R ../../dockerization/command-library-server/scripts/ .
  copy_common_files
  cd ../..

}

function copy_event_server_jars(){

	cp ${BAZEL_BIN}/350-event-server/module_deploy.jar event-server-capsule.jar
	cp ../../350-event-server/keystore.jks .
	cp ../../350-event-server/key.pem .
	cp ../../350-event-server/cert.pem .
	cp ../../350-event-server/event-service-config.yml .
	cp ../../dockerization/event-server/Dockerfile-event-server-cie-jdk Dockerfile-cie-jdk
	cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
	cp -r ../../dockerization/event-server/scripts/ .

	copy_common_files

	cd ../..
}

function copy_verification_service_jars(){

  cp ${HOME}/.bazel-dirs/bin/270-verification/module_deploy.jar verification-capsule.jar
  cp ../../270-verification/keystore.jks .
  cp ../../270-verification/verification-config.yml .
  cp ../../dockerization/verification/Dockerfile-verification-cie-jdk ./Dockerfile-cie-jdk
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -R ../../dockerization/verification/scripts/ .
  copy_common_files
  cd ../..

}


#prepare_to_copy_jars
if [ "${SERVICE_NAME}" == "manager" ]; then
    copy_cg_manager_jars
elif [ "${SERVICE_NAME}" == "migrator" ]; then
    copy_migrator_jars
elif [ "${SERVICE_NAME}" == "change-data-capture" ]; then
    copy_change_data_capture_jars
elif [ "${SERVICE_NAME}" == "idp-service" ]; then
    copy_change_data_capture_jars
elif [ "${SERVICE_NAME}" == "verification-service" ]; then
    copy_verification_service_jars
elif [ "${SERVICE_NAME}" == "event-server" ]; then
    copy_event_server_jars
fi