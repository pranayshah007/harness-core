#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function download_apm_binaries(){

	curl  ${APM_BINS} --output ${APM_BINS##*/}; STATUS1=$?
	echo "INFO: Download Status: ${ET_AGENT##*/}: $STATUS1"

	if [ "${STATUS1}" -eq 0 ]; then
		echo "Download Finished..."
	else
		echo "Failed to Download APM Binaries. Exiting..."
		exit 1
	fi

}

function create_and_push_docker_build(){
	local service_name="$1"
	local tag="$2"
  local image_path="${REGISTRY_PATH}/${REPO_PATH}/${BUILD_TYPE}-${service_name}:${tag}"

  echo "INFO: Pulling Non APM IMAGE...."
	docker pull "${image_path}"; STATUS=$?

	if [ "$STATUS" -eq 0 ]; then
		echo "Successfully pulled NON APM IMAGE: ${non_apm_image_path} from GCR"
	else
		echo "Failed to pull NON APM IMAGE: ${non_apm_image_path} from GCR. Exiting..."
		exit 1
	fi

   echo "INFO: Bulding APM IMAGE...."
	 docker build -t "${image_path}" \
	 --build-arg BUILD_TAG="${tag}" --build-arg REGISTRY_PATH="${REGISTRY_PATH}" \
   --build-arg REPO_PATH="${REPO_PATH}" --build-arg BUILD_TYPE="${BUILD_TYPE}" \
   --build-arg SERVICE_NAME="${service_name}" --build-arg APM_BINS="${APM_BINS##*/}" \
   -f internalBuilds.dockerfile .; STATUS1=$?

  echo "INFO: Pushing APM IMAGE...."
	docker push "${image_path}"; STATUS2=$?

  if [ "${STATUS1}" -eq 0 ] && [ "${STATUS2}" -eq 0 ]; then
		echo "INFO: Successfully created and pushed apm build for SERVICE: ${service_name} with TAG:${tag}"
	else
		echo "ERROR: Failed to create and push apm build for SERVICE: ${service_name} with TAG:${tag}"
		exit 1
	fi

}

export DOCKER_BUILDKIT=1

export APM_BINS='https://harness.jfrog.io/artifactory/BuildsTools/docker/apm/apm_bins.tar.gz'

export REGISTRY_PATH='us.gcr.io/platform-205701'
export REPO_PATH=${REPO_PATH}
export BUILD_TYPE=${BUILD_TYPE}
export VERSION=${VERSION}

IMAGES_LIST=(manager-openjdk-8u242 ng-manager-openjdk-8u242 verification-service-openjdk-8u242 \
pipeline-service-openjdk-8u242 cv-nextgen-openjdk-8u242 ce-nextgen-openjdk-8u242 \
template-service-openjdk-8u242 ci-manager-openjdk-8u242 command-library-server-openjdk-8u242 \
change-data-capture-openjdk-8u242 eventsapi-monitor-openjdk-8u242 dms-openjdk-8u242 \
event-server-openjdk-8u242 batch-processing-openjdk-8u242 migrator-openjdk-8u242)

if [ -z "${VERSION}" ] && [ -z "${REPO_PATH}" ] && [ -z "${BUILD_TYPE}" ]; then
    echo "ERROR: VERSION is not defined. Exiting..."
    exit 1
fi

echo "STEP 1: INFO: Downloading APM Binaries Locally..."
download_apm_binaries

echo "STEP 2: INFO: Creating Docker Builds with apm binaries."
for IMAGE in "${IMAGES_LIST[@]}";
do
  create_and_push_docker_build $IMAGE $VERSION
done
