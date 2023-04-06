#!/bin/bash -e
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function append_config() {
  CONFIG_KEY=$1
  CONFIG_VALUE=$2
  if [ -n "$CONFIG_VALUE" ] ; then
    echo "$CONFIG_KEY: $CONFIG_VALUE" >> config.yml
  fi
}

if [ ! -w . ]; then
  echo "Missing required write permissions for running user $(id -run) or group $(id -rgn) on delegate home directory $PWD. Shutting down."
  exit 1
fi

# 0. Proxy setup
source ./proxy_setup.sh

# 1. Start the delegate
JAVA_OPTS=${JAVA_OPTS//UseCGroupMemoryLimitForHeap/UseContainerSupport}
exec java $JAVA_OPTS $PROXY_SYS_PROPS -XX:MaxRAMPercentage=70.0 -XX:MinRAMPercentage=40.0 -XX:+IgnoreUnrecognizedVMOptions -XX:+HeapDumpOnOutOfMemoryError -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 -Dcom.sun.jndi.ldap.object.disableEndpointIdentification=true -DLANG=en_US.UTF-8 -jar task.jar
