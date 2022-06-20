#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function download_apm_binaries(){

	curl  ${ET_AGENT} --output ${ET_AGENT##*/}; STATUS1=$?
	echo "INFO: Download Status: ${ET_AGENT##*/}: $STATUS1"

	curl ${TAKIPI_AGENT} --output ${TAKIPI_AGENT##*/}; STATUS2=$?
	echo "INFO: Download Status: ${TAKIPI_AGENT##*/}: $STATUS2"

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
  local_feature_image_path="${REGISTRY_PATH}/${REPO_PATH}/${local_service_name}:${local_tag}"

  echo "INFO: Pulling Non APM IMAGE...."
	docker pull "${local_feature_image_path}"; STATUS=$?
	if [ "$STATUS" -eq 0 ]; then
		echo "Successfully pulled NON APM IMAGE: ${local_feature_image_path} from GCR"
	else
		echo "Failed to pull NON APM IMAGE: ${local_feature_image_path} from GCR. Exiting..."
		exit 1
	fi

   echo "INFO: Bulding APM IMAGE...."
	 docker build -t "${local_feature_image_path}" \
	 --build-arg BUILD_TAG="${local_tag}" --build-arg REGISTRY_PATH="${REGISTRY_PATH}" \
   --build-arg REPO_PATH="${REPO_PATH}" --build-arg SERVICE_NAME="${local_service_name}" \
   --build-arg APPD_AGENT="${APPD_AGENT##*/}" --build-arg TAKIPI_AGENT="${TAKIPI_AGENT##*/}" \
   --build-arg OCELET_AGENT="${OCELET_AGENT##*/}" --build-arg ET_AGENT="${ET_AGENT##*/}" \
   -f featureBuilds.dockerfile .; STATUS1=$?

  echo "INFO: Pushing APM IMAGE...."
	docker push "${local_feature_image_path}"; STATUS2=$?

	if [ "${STATUS1}" -eq 0 ] && [ "${STATUS2}" -eq 0 ]; then
		echo "INFO: Successfully created and pushed apm build for SERVICE: ${local_service_name} with TAG:${local_tag}"
	else
		echo "ERROR: Failed to create and push apm build for SERVICE: ${local_service_name} with TAG:${local_tag}"
		exit 1
	fi

}

export APPD_AGENT='https://harness.jfrog.io/artifactory/BuildsTools/docker/apm/appd/AppServerAgent-1.8-21.11.2.33305.zip'
export TAKIPI_AGENT='https://harness.jfrog.io/artifactory/BuildsTools/docker/apm/overops/takipi-agent-latest.tar.gz'
export ET_AGENT='https://get.et.harness.io/releases/latest/nix/harness-et-agent.tar.gz'
export OCELET_AGENT='https://github.com/inspectIT/inspectit-ocelot/releases/download/1.16.0/inspectit-ocelot-agent-1.16.0.jar'

export REGISTRY_PATH='us.gcr.io/platform-205701'
export REPO_PATH=${REPO_PATH}
export VERSION=${VERSION}

IMAGES_LIST=(feature-manager-openjdk-8u242 feature-ng-manager-openjdk-8u242 feature-verification-service-openjdk-8u242 \
feature-pipeline-service-openjdk-8u242 feature-cv-nextgen-openjdk-8u242 feature-ce-nextgen-openjdk-8u242 \
feature-template-service-openjdk-8u242 feature-ci-manager-openjdk-8u242 feature-command-library-server-openjdk-8u242 \
feature-change-data-capture-openjdk-8u242 feature-eventsapi-monitor-openjdk-8u242 feature-dms-openjdk-8u242 \
feature-event-server-openjdk-8u242 feature-batch-processing-openjdk-8u242 feature-migrator-openjdk-8u242)

#<+steps.build.output.outputVariables.VERSION>
if [ -z "${VERSION}" ] && [ -z "${REPO_PATH}" ]; then
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
