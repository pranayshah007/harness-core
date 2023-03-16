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
	mkdir -p dist/event-server ;
	cd dist/event-server

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
	mkdir -p dist/ng-manager
	cd dist/ng-manager

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
	mkdir -p dist/change-data-capture ;
	cd dist/change-data-capture
	
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
	mkdir -p dist/ng-dashboard-service ;
	cd dist/ng-dashboard-service

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
	mkdir -p dist/delegate-service-app ;
  cd dist/delegate-service-app

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



