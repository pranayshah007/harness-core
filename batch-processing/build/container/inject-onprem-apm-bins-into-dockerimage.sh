#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function download_onprem_apm_binaries(){
  curl -L ${OCELET_AGENT} --output ${OCELET_AGENT##*/}; STATUS1=$?
  echo "INFO: Download Status: ${OCELET_AGENT##*/}: $STATUS1"
  chmod 711 ${OCELET_AGENT##*/}

  if [ "${STATUS1}" -eq 0 ] ; then
		echo "Download Finished..."
	else
		echo "Failed to Download On-prem APM Binaries. Exiting..."
		exit 1
	fi
}

export OCELET_AGENT='https://github.com/inspectIT/inspectit-ocelot/releases/download/1.16.0/inspectit-ocelot-agent-1.16.0.jar'

echo "STEP 1: INFO: Downloading APM Binaries Locally..."
