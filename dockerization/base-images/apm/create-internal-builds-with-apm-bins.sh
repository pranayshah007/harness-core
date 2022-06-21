#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function download_apm_binaries(){

	curl  ${ET_AGENT} --output ${ET_AGENT##*/}; STATUS1=$?
	echo "INFO: Download Status: ${ET_AGENT##*/}: $STATUS1"

	curl ${APPD_AGENT} --output ${APPD_AGENT##*/}; STATUS3=$?
	echo "INFO: Download Status: ${APPD_AGENT##*/}: $STATUS3"

	curl -L ${OCELET_AGENT} --output ${OCELET_AGENT##*/}; STATUS4=$?
	echo "INFO: Download Status: ${OCELET_AGENT##*/}: $STATUS4"

	if [ "${STATUS1}" -eq 0 ] && [ "${STATUS2}" -eq 0 ] && [ "${STATUS3}" -eq 0 ] && [ "${STATUS4}" -eq 0 ]; then
		echo "Download Finished..."
	else
		echo "Failed to Download APM Binaries. Exiting..."
		exit 1
	fi
}

function create_and_push_docker_build(){
	local_service_name="$1"
	local_tag="$2"
  local_image_path="${REGISTRY_PATH}/${REPO_PATH}/${BUILD_TYPE}-${local_service_name}:${local_tag}"

  echo "INFO: Pulling Non APM IMAGE...."
	docker pull "${local_image_path}"; STATUS=$?
	if [ "$STATUS" -eq 0 ]; then
		echo "Successfully pulled NON APM IMAGE: ${local_image_path} from GCR"
	else
		echo "Failed to pull NON APM IMAGE: ${local_image_path} from GCR. Exiting..."
		exit 1
	fi

   echo "INFO: Bulding APM IMAGE...."
	 docker build -t "${local_image_path}" \
	 --build-arg BUILD_TAG="${local_tag}" --build-arg REGISTRY_PATH="${REGISTRY_PATH}" \
   --build-arg REPO_PATH="${REPO_PATH}" --build-arg BUILD_TYPE="${BUILD_TYPE}" \
   --build-arg SERVICE_NAME="${local_service_name}" --build-arg APPD_AGENT="${APPD_AGENT##*/}" \
   --build-arg OCELET_AGENT="${OCELET_AGENT##*/}" --build-arg ET_AGENT="${ET_AGENT##*/}" \
   -f internalBuilds.dockerfile .; STATUS1=$?

  echo "INFO: Pushing APM IMAGE...."
	docker push "${local_image_path}"; STATUS2=$?

	if [ "${STATUS1}" -eq 0 ] && [ "${STATUS2}" -eq 0 ]; then
		echo "INFO: Successfully created and pushed apm build for SERVICE: ${local_service_name} with TAG:${local_tag}"
	else
		echo "ERROR: Failed to create and push apm build for SERVICE: ${local_service_name} with TAG:${local_tag}"
		exit 1
	fi

}

export APPD_AGENT='https://harness.jfrog.io/artifactory/BuildsTools/docker/apm/appd/AppServerAgent-1.8-21.11.2.33305.zip'
export ET_AGENT='https://get.et.harness.io/releases/latest/nix/harness-et-agent.tar.gz'
export OCELET_AGENT='https://github.com/inspectIT/inspectit-ocelot/releases/download/1.16.0/inspectit-ocelot-agent-1.16.0.jar'

export REGISTRY_PATH='us.gcr.io/platform-205701'
export REPO_PATH=${REPO_PATH}
export BUILD_TYPE=${BUILD_TYPE}
export VERSION=${VERSION}

IMAGES_LIST=(manager-openjdk-8u242 ng-manager-openjdk-8u242 verification-service-openjdk-8u242 \
pipeline-service-openjdk-8u242 cv-nextgen-openjdk-8u242 ce-nextgen-openjdk-8u242 \
template-service-openjdk-8u242 ci-manager-openjdk-8u242 command-library-server-openjdk-8u242 \
change-data-capture-openjdk-8u242 eventsapi-monitor-openjdk-8u242 dms-openjdk-8u242 \
event-server-openjdk-8u242 batch-processing-openjdk-8u242 migrator-openjdk-8u242)

#<+steps.build.output.outputVariables.VERSION>
if [ -z "${VERSION}" ] && [ -z "${REPO_PATH}" ] && [ -z "${BUILD_TYPE}" ]; then
    echo "ERROR: VERSION, REPO_PATH and BUILD_TYPE are not defined. Exiting..."
    exit 1
fi

echo "STEP 1: INFO: Downloading APM Binaries Locally..."
download_apm_binaries

echo "STEP 2: INFO: Creating Docker Builds with apm binaries."
for IMAGE in "${IMAGES_LIST[@]}";
do
  create_and_push_docker_build $IMAGE $VERSION
done