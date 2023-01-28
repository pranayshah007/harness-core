#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p /opt/harness/logs
touch /opt/harness/logs/delegate-service.log

if [[ -v "{hostname}" ]]; then
   export HOSTNAME=$(hostname)
fi

export JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:MinRAMPercentage=40.0 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/heapdump -Xloggc:mygclogfilename.gc $GC_PARAMS $JAVA_ADVANCED_FLAGS"


if [[ "${ENABLE_APPDYNAMICS}" == "true" ]]; then
    mkdir /opt/harness/AppServerAgent-1.8-21.11.2.33305 && unzip AppServerAgent-1.8-21.11.2.33305.zip -d /opt/harness/AppServerAgent-1.8-21.11.2.33305
    node_name="-Dappdynamics.agent.nodeName=$(hostname)"
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/AppServerAgent-1.8-21.11.2.33305/javaagent.jar -Dappdynamics.jvm.shutdown.mark.node.as.historical=true"
    JAVA_OPTS="$JAVA_OPTS $node_name"
    echo "Using Appdynamics java agent"
fi

java $JAVA_OPTS -jar /opt/harness/delegate-service-capsule.jar server /opt/harness/delegate-service-config.yml
