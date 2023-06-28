#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function download_saas_apm_binaries(){

	curl  ${ET_AGENT} --output ${ET_AGENT##*/}; STATUS1=$?
	echo "INFO: Download Status: ${ET_AGENT##*/}: $STATUS1"
	tar -xzf harness-et-agent.tar.gz -C /opt/harness
	chmod 711 /opt/harness/harness
	rm /opt/harness/harness-et-agent.tar.gz

	curl ${APPD_AGENT} --output AppServerAgent.zip; STATUS2=$?
	echo "INFO: Download Status: AppServerAgent.zip: $STATUS2"
	chmod 711 AppServerAgent.zip

	curl ${OT_AGENT} --output ${OT_AGENT##*/}; STATUS3=$?
	echo "INFO: Download Status: ${OT_AGENT##*/}: $STATUS3"
	chmod 711 ${OT_AGENT##*/}

	curl ${JACOCO_AGENT} --output ${JACOCO_AGENT##*/}; STATUS4=$?
  echo "INFO: Download Status: ${JACOCO_AGENT##*/}: $STATUS4"
  chmod 711 ${JACOCO_AGENT##*/}

	if [ "${STATUS1}" -eq 0 ] && [ "${STATUS2}" -eq 0 ] && [ "${STATUS3}" -eq 0 ] && [ "${STATUS4}" -eq 0 ] ; then
		echo "Download Finished..."
	else
		echo "Failed to Download Saas APM Binaries. Exiting..."
		exit 1
	fi
}

export APPD_AGENT='https://harness.jfrog.io/artifactory/BuildsTools/docker/apm/appd/AppServerAgent-1.8-23.2.0.34668.zip'
export ET_AGENT='https://get.et.harness.io/releases/latest/nix/harness-et-agent.tar.gz'
export OT_AGENT='https://harness.jfrog.io/artifactory/BuildsTools/docker/apm/opentelemetry/opentelemetry-javaagent.jar'
export JACOCO_AGENT='https://repo1.maven.org/maven2/org/jacoco/jacoco/0.8.7/jacoco-0.8.7.zip'

echo "STEP 1: INFO: Downloading APM Binaries Locally..."
download_saas_apm_binaries