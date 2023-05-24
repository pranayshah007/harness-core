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

  pwd
  ls
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
	mv scripts/start_process_bazel.sh scripts/start_process.sh

	copy_common_files

	java -jar rest-capsule.jar scan-classpath-metadata

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

function copy_ng_manager_jars(){

	cp ${BAZEL_BIN}/120-ng-manager/module_deploy.jar ng-manager-capsule.jar
	cp ../../120-ng-manager/config.yml .
	cp ../../keystore.jks .
	cp ../../120-ng-manager/key.pem .
	cp ../../120-ng-manager/cert.pem .
	cp ../../120-ng-manager/src/main/resources/redisson-jcache.yaml .
	cp ../../120-ng-manager/src/main/resources/enterprise-redisson-jcache.yaml .
	cp ../../120-ng-manager/src/main/resources/jfr/default.jfc .
  cp ../../120-ng-manager/src/main/resources/jfr/profile.jfc .

	cp ../../dockerization/ng-manager/Dockerfile-ng-manager-cie-jdk ./Dockerfile-cie-jdk
	cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
	cp -r ../../dockerization/ng-manager/scripts/ .

	copy_common_files

	java -jar ng-manager-capsule.jar scan-classpath-metadata

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

function copy_template_service_jars(){

  cp ${BAZEL_BIN}/template-service/service/module_deploy.jar template-service-capsule.jar
  cp ../../template-service/config/config.yml .
  cp ../../template-service/config/keystore.jks .
  cp ../../template-service/config/key.pem .
  cp ../../template-service/config/cert.pem .
  cp ../../template-service/service/src/main/resources/redisson-jcache.yaml .
  cp ../../template-service/service/src/main/resources/jfr/default.jfc .
  cp ../../template-service/service/src/main/resources/jfr/profile.jfc .

  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .

  cp ../../template-service/build/container/Dockerfile-template-service-cie-jdk ./Dockerfile-cie-jdk
  cp -r ../../template-service/build/container/scripts/ .

  cp ../../protocol.info .
  java -jar template-service-capsule.jar scan-classpath-metadata

}

function copy_debezium_service_jars(){

  cp ${HOME}/.bazel-dirs/bin/debezium-service/service/module_deploy.jar debezium-service-capsule.jar
  cp ../../debezium-service/config/config.yml .
  cp ../../debezium-service/service/src/main/resources/redisson-jcache.yaml .

  cp ../../dockerization/debezium-service/Dockerfile-debezium-service-cie-jdk ./Dockerfile-cie-jdk
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -r ../../dockerization/debezium-service/scripts/ .
  copy_common_files
  cd ../..

}

function copy_pipeline_service_jars(){

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
  ls /harness/harness-core/dist/pipeline-service/
  ls /harness/harness-core/dist/
  ls /harness/harness-core/
  cp ../../pipeline-service-protocol.info .
  ls /harness/harness-core/dist/pipeline-service/
  copy_common_files
  cp ../../pipeline-service/config/jfr/default.jfc .
  cp ../../pipeline-service/config/jfr/profile.jfc .
  java -jar pipeline-service-capsule.jar scan-classpath-metadata
  cd ../..

}


function copy_platform_service_jars(){

  cp ${HOME}/.bazel-dirs/bin/platform-service/service/module_deploy.jar platform-service-capsule.jar
  cp ../../platform-service/config/config.yml .
  cp ../../platform-service/config/keystore.jks .
  cp ../../platform-service/config/key.pem .
  cp ../../platform-service/config/cert.pem .
  cp ../../dockerization/platform-service/Dockerfile-platform-service-cie-jdk ./Dockerfile-cie-jdk
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -r ../../dockerization/platform-service/scripts .
  copy_common_files
  java -jar platform-service-capsule.jar scan-classpath-metadata
  cd ../..

}


function copy_ci_manager_jars(){

  cp ${HOME}/.bazel-dirs/bin/332-ci-manager/app/module_deploy.jar ci-manager-capsule.jar
  cp ../../332-ci-manager/config/ci-manager-config.yml .
  cp ../../keystore.jks .
  cp ../../332-ci-manager/config/key.pem .
  cp ../../332-ci-manager/config/cert.pem .
  cp ../../332-ci-manager/service/src/main/resources/redisson-jcache.yaml .
  cp ../../332-ci-manager/service/src/main/resources/enterprise-redisson-jcache.yaml .
  cp ../../332-ci-manager/service/src/main/resources/jfr/default.jfc .
  cp ../../332-ci-manager/service/src/main/resources/jfr/profile.jfc .

  cp ../../332-ci-manager/build/container/Dockerfile-ci-manager-jenkins-k8-openjdk ./Dockerfile
  cp ../../332-ci-manager/build/container/Dockerfile-ci-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
  cp ../../332-ci-manager/build/container/Dockerfile-ci-manager-jenkins-k8-gcr-openjdk-ubi ./Dockerfile-gcr-ubi
  cp ../../332-ci-manager/build/container/Dockerfile-ci-manager-cie-jdk ./Dockerfile-cie-jdk
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -r ../../332-ci-manager/build/container/scripts/ .
  cp ../../ci-manager-protocol.info .
  copy_common_files
  java -jar ci-manager-capsule.jar scan-classpath-metadata
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

function copy_srm_service_jars(){

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
  cp ../../srm-service/config/jfr/default.jfc .
  cp ../../srm-service/config/jfr/profile.jfc .

  copy_common_files
  java -jar cv-nextgen-capsule.jar scan-classpath-metadata
  cd ../..
  
}

function copy_batch_processing_jars(){

  cp ${HOME}/.bazel-dirs/bin/batch-processing/service/module_deploy.jar batch-processing-capsule.jar
  cp ../../batch-processing/service/batch-processing-config.yml .
  cp ../../batch-processing/build/container/Dockerfile-batch-processing-cie-jdk ./Dockerfile-batch-processing-cie-jdk
  cp -r ../../batch-processing/build/container/scripts/ .
  cp ../../batch-processing/build/container/inject-onprem-apm-bins-into-dockerimage.sh ./inject-onprem-apm-bins-into-dockerimage.sh
  cp ../../batch-processing/build/container/inject-saas-apm-bins-into-dockerimage.sh ./inject-saas-apm-bins-into-dockerimage.sh
  copy_common_files
  cd ../..

}

function copy_access_control_jars(){

  cp ${HOME}/.bazel-dirs/bin/access-control/service/module_deploy.jar accesscontrol-service-capsule.jar
  cp ../../access-control/config/config.yml .
  cp ../../access-control/config/keystore.jks .
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp ../../access-control/build/container/Dockerfile-accesscontrol-service-cie-jdk ./Dockerfile-cie-jdk
  cp -r ../../access-control/build/container/scripts/ .
  cp ../../access-control/config/jfr/default.jfc .
  cp ../../access-control/config/jfr/profile.jfc .
  copy_common_files
  java -jar accesscontrol-service-capsule.jar scan-classpath-metadata
  cd ../..

}

function copy_audit_event_streaming_jars(){

  cp ${HOME}/.bazel-dirs/bin/audit-event-streaming/service/module_deploy.jar audit-event-streaming-capsule.jar
  cp ../../audit-event-streaming/service/src/main/resources/application.yml .
  cp ../../audit-event-streaming/service/src/main/resources/keystore.jks .
  cp ../../audit-event-streaming/service/src/main/resources/key.pem .
  cp ../../audit-event-streaming/service/src/main/resources/cert.pem .
  cp ../../audit-event-streaming/service/src/main/resources/jfr/default.jfc .
  cp ../../audit-event-streaming/service/src/main/resources/jfr/profile.jfc .
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp ../../audit-event-streaming/build/container/Dockerfile-audit-event-streaming-cie-jdk ./Dockerfile-cie-jdk
  cp -r ../../audit-event-streaming/build/container/scripts/ .
  copy_common_files
  java -jar audit-event-streaming-capsule.jar scan-classpath-metadata
  cd ../..

}

function copy_ce_nextgen_jars(){

  cp ${HOME}/.bazel-dirs/bin/ce-nextgen/service/module_deploy.jar ce-nextgen-capsule.jar
  cp ../../ce-nextgen/config/keystore.jks .
  cp ../../ce-nextgen/config/config.yml .
  cp ../../ce-nextgen/config/jfr/default.jfc .
  cp ../../ce-nextgen/config/jfr/profile.jfc .
  cp ../../ce-nextgen/build/container/Dockerfile-ce-nextgen-cie-jdk Dockerfile-ce-nextgen-cie-jdk
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -r ../../ce-nextgen/build/container/scripts/ .
  copy_common_files
  cd ../..

}

function copy_sto_manager_jars(){

  cp ${HOME}/.bazel-dirs/bin/315-sto-manager/app/module_deploy.jar sto-manager-capsule.jar
  # Copy CI manager config file and use it as is
  cp ../../332-ci-manager/config/ci-manager-config.yml .
  cp ../../keystore.jks .
  cp ../../315-sto-manager/config/key.pem .
  cp ../../315-sto-manager/config/cert.pem .
  cp ../../315-sto-manager/app/src/main/resources/redisson-jcache.yaml .
  cp ../../315-sto-manager/app/src/main/resources/enterprise-redisson-jcache.yaml .

  cp ../../315-sto-manager/build/container/Dockerfile-stomanager-service-jenkins-k8-openjdk ./Dockerfile
  cp ../../315-sto-manager/build/container/Dockerfile-stomanager-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
  cp ../../315-sto-manager/build/container/Dockerfile-stomanager-ubi ./Dockerfile-gcr-ubi
  cp ../../dockerization/base-images/apm/inject-onprem-apm-bins-into-dockerimage.sh .
  cp ../../dockerization/base-images/apm/inject-saas-apm-bins-into-dockerimage.sh .
  cp -r ../../315-sto-manager/build/container/scripts/ .

  # Use CI manager replace config logic as is
  cp ../../332-ci-manager/build/container/scripts/replace_configs.sh ./scripts/replace_configs.sh
  java -jar sto-manager-capsule scan-classpath-metadata

  cd ../..

}



#prepare_to_copy_jars

if [ "${SERVICE_NAME}" == "access-control" ]; then
    copy_access_control_jars
elif [ "${SERVICE_NAME}" == "ng-manager" ]; then
    copy_ng_manager_jars
elif [ "${SERVICE_NAME}" == "migrator" ]; then
    copy_migrator_jars
elif [ "${SERVICE_NAME}" == "change-data-capture" ]; then
    copy_change_data_capture_jars
elif [ "${SERVICE_NAME}" == "ci-manager" ]; then
    copy_ci_manager_jars
elif [ "${SERVICE_NAME}" == "batch-processing" ]; then
    copy_batch_processing_jars
elif [ "${SERVICE_NAME}" == "audit-event-streaming" ]; then
    copy_audit_event_streaming_jars
elif [ "${SERVICE_NAME}" == "ce-nextgen" ]; then
    copy_ce_nextgen_jars
elif [ "${SERVICE_NAME}" == "debezium-service" ]; then
    copy_debezium_service_jars
elif [ "${SERVICE_NAME}" == "pipeline-service" ]; then
    copy_pipeline_service_jars
elif [ "${SERVICE_NAME}" == "platform-service" ]; then
    copy_platform_service_jars
elif [ "${SERVICE_NAME}" == "srm-service" ]; then
    copy_srm_service_jars
elif [ "${SERVICE_NAME}" == "template-service" ]; then
    copy_template_service_jars
elif [ "${SERVICE_NAME}" == "sto-manager" ]; then
    copy_sto_manager_jars
fi