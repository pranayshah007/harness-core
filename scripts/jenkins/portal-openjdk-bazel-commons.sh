# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BAZEL_BIN="${HOME}/.bazel-dirs/bin"

function prepare_to_copy_jars(){
  mkdir -p dist ;
  cd dist

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

function copy_cg_manager_jars(){
	mkdir -p dist/manager ;
	cd dist/manager

	cp ${BAZEL_BIN}/360-cg-manager/module_deploy.jar rest-capsule.jar
	cp ../../400-rest/src/main/resources/hazelcast.xml .
	cp ../../keystore.jks .
	cp ../../360-cg-manager/key.pem .
	cp ../../360-cg-manager/cert.pem .
	cp ../../360-cg-manager/newrelic.yml .
	cp ../../360-cg-manager/config.yml .
	cp ../../400-rest/src/main/resources/redisson-jcache.yaml .

	cp ../../dockerization/manager/Dockerfile-manager-jenkins-k8-openjdk ./Dockerfile
	cp ../../dockerization/manager/Dockerfile-manager-cie-jdk ./Dockerfile-cie-jdk
	cp ../../dockerization/manager/Dockerfile-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
	cp -r ../../dockerization/manager/scripts/ .
	mv scripts/start_process_bazel.sh scripts/start_process.sh

	copy_common_files

	java -jar rest-capsule.jar scan-classpath-metadata

	cd ../..
}

function copy_event_server_jars(){
	mkdir -p dist/event-server ;
	cd dist/event-server

	cp ${BAZEL_BIN}/350-event-server/module_deploy.jar event-server-capsule.jar
	cp ../../350-event-server/key.pem .
	cp ../../350-event-server/cert.pem .
	cp ../../350-event-server/event-service-config.yml .
	cp ../../dockerization/event-server/Dockerfile-event-server-jenkins-k8-openjdk Dockerfile
	cp ../../dockerization/event-server/Dockerfile-event-server-cie-jdk Dockerfile-cie-jdk
	cp ../../dockerization/event-server/Dockerfile-event-server-jenkins-k8-gcr-openjdk Dockerfile-gcr
	cp -r ../../dockerization/event-server/scripts/ .
	
	copy_common_files

	cd ../..
}

function copy_ng_manager_jars(){
	mkdir -p dist/ng-manager
	cd dist/ng-manager

	cp ${BAZEL_BIN}/120-ng-manager/module_deploy.jar ng-manager-capsule.jar
	cp ../../120-ng-manager/config.yml .
	cp ../../keystore.jks .
	cp ../../120-ng-manager/key.pem .
	cp ../../120-ng-manager/cert.pem .
	cp ../../120-ng-manager/src/main/resources/redisson-jcache.yaml .

	cp ../../dockerization/ng-manager/Dockerfile-ng-manager-jenkins-k8-openjdk ./Dockerfile
	cp ../../dockerization/ng-manager/Dockerfile-ng-manager-cie-jdk ./Dockerfile-cie-jdk
	cp ../../dockerization/ng-manager/Dockerfile-ng-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
	cp -r ../../dockerization/ng-manager/scripts/ .

	copy_common_files

	java -jar ng-manager-capsule.jar scan-classpath-metadata

	cd ../..
}

function copy_ce_nextgen_jars(){
	MODULE_NAME="340-ce-nextgen";
	FOLDER_NAME="ce-nextgen";
	mkdir -p dist/${FOLDER_NAME} ;
	cd dist/${FOLDER_NAME}

	cp ${BAZEL_BIN}/${MODULE_NAME}/module_deploy.jar ce-nextgen-capsule.jar
	cp ../../${MODULE_NAME}/keystore.jks .
	cp ../../${MODULE_NAME}/config.yml .
	cp ../../dockerization/${FOLDER_NAME}/Dockerfile-ce-nextgen-cie-jdk Dockerfile-cie-jdk
	cp ../../dockerization/${FOLDER_NAME}/Dockerfile-ce-nextgen-jenkins-k8-openjdk Dockerfile
	cp ../../dockerization/${FOLDER_NAME}/Dockerfile-ce-nextgen-jenkins-k8-gcr-openjdk Dockerfile-gcr
	cp -r ../../dockerization/${FOLDER_NAME}/scripts/ .
	
	copy_common_files

	cd ../..
}

function copy_batch_processing_jars(){
	mkdir -p dist/batch-processing ;
	cd dist/batch-processing

	cp ${BAZEL_BIN}/280-batch-processing/module_deploy.jar batch-processing-capsule.jar
	cp ../../280-batch-processing/batch-processing-config.yml .
	cp ../../dockerization/batch-processing/Dockerfile-batch-processing-jenkins-k8-openjdk Dockerfile
	cp ../../dockerization/batch-processing/Dockerfile-batch-processing-cie-jdk Dockerfile-cie-jdk
	cp ../../dockerization/batch-processing/Dockerfile-batch-processing-jenkins-k8-gcr-openjdk Dockerfile-gcr
	cp -r ../../dockerization/batch-processing/scripts/ .
	
	copy_common_files

	cd ../..
}

function copy_change_data_capture_jars(){
	mkdir -p dist/change-data-capture ;
	cd dist/change-data-capture
	
	cp ${BAZEL_BIN}/110-change-data-capture/module_deploy.jar change-data-capture.jar
	cp ../../110-change-data-capture/config.yml .
	cp ../../dockerization/change-data-capture/Dockerfile-change-data-capture-jenkins-k8-openjdk Dockerfile
	cp ../../dockerization/change-data-capture/Dockerfile-change-data-capture-cie-jdk Dockerfile-cie-jdk
	cp ../../dockerization/change-data-capture/Dockerfile-change-data-capture-jenkins-k8-gcr-openjdk Dockerfile-gcr
	cp -r ../../dockerization/change-data-capture/scripts/ .
	
	copy_common_files

	cd ../..
}

function copy_ng_dashboard_jars(){
	mkdir -p dist/ng-dashboard-service ;
	cd dist/ng-dashboard-service

	cp ${BAZEL_BIN}/290-dashboard-service/module_deploy.jar ng-dashboard-service.jar
	cp ../../290-dashboard-service/config.yml .

	if [ -e ../../dockerization/ng-dashboard-service/Dockerfile-ng-dashboard-k8-openjdk ]
	then
	  cp ../../dockerization/ng-dashboard-service/Dockerfile-ng-dashboard-k8-openjdk Dockerfile
	fi
	  echo "Docker file for ng dashboard not found"

	if [ -e ../../dockerization/ng-dashboard-service/Dockerfile-ng-dashboard-k8-gcr-openjdk ]
	then
	  cp ../../dockerization/ng-dashboard-service/Dockerfile-ng-dashboard-cie-jdk Dockerfile-cie-jdk
	  cp ../../dockerization/ng-dashboard-service/Dockerfile-ng-dashboard-k8-gcr-openjdk Dockerfile-gcr
	fi
	  echo "Docker file for ng dashboard for gcr not found"
	cp -r ../../dockerization/ng-dashboard-service/scripts/ .

	copy_common_files

	cd ../..
}
