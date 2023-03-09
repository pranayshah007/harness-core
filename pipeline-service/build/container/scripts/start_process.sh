#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -x
if [[ -v "{hostname}" ]]; then
   export HOSTNAME=$(hostname)
fi

if [[ -z "$MEMORY" ]]; then
   export MEMORY=4096m
fi

if [[ -z "$COMMAND" ]]; then
   export COMMAND=server
fi

echo "Using memory " "$MEMORY"

if [[ -z "$CAPSULE_JAR" ]]; then
   export CAPSULE_JAR=/opt/harness/pipeline-service-capsule.jar
fi

if [[ "${ENABLE_SERIALGC}" == "true" ]]; then
    export GC_PARAMS=" -XX:+UseSerialGC -Dfile.encoding=UTF-8"
else
    export GC_PARAMS=" -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=40 -XX:MaxGCPauseMillis=1000 -Dfile.encoding=UTF-8"
fi

export JAVA_OPTS="-Xmx${MEMORY} -XX:+HeapDumpOnOutOfMemoryError -Xloggc:mygclogfilename.gc $GC_PARAMS $JAVA_ADVANCED_FLAGS"

if [[ "${ENABLE_REMOTE_DEBUG}" == "true" ]]; then
  export REMOTE_DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5005"
  export JAVA_OPTS="$REMOTE_DEBUG $JAVA_OPTS"
  echo "Enabled remote debug"
fi

if [[ "${ENABLE_APPDYNAMICS}" == "true" ]]; then
    mkdir /opt/harness/AppServerAgent && unzip AppServerAgent.zip -d /opt/harness/AppServerAgent
    node_name="-Dappdynamics.agent.nodeName=$(hostname)"
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/AppServerAgent/javaagent.jar -Dappdynamics.jvm.shutdown.mark.node.as.historical=true"
    JAVA_OPTS="$JAVA_OPTS $node_name"
    echo "Using Appdynamics java agent"
fi

if [[ "${ENABLE_ERROR_TRACKING}" == "true" ]] ; then
    echo "Error Tracking is enabled"
    JAVA_OPTS=$JAVA_OPTS" -Xshare:off -XX:-UseTypeSpeculation -XX:ReservedCodeCacheSize=512m -agentpath:/opt/harness/harness/lib/libETAgent.so"
    echo "Using Error Tracking Java Agent"
fi

if [[ "${ENABLE_MONITORING}" == "true" ]] ; then
    echo "Monitoring  is enabled"
    JAVA_OPTS="$JAVA_OPTS ${MONITORING_FLAGS}"
    echo "Using inspectIT Java Agent"
fi

if [[ "${ENABLE_OPENTELEMETRY}" == "true" ]] ; then
    echo "OpenTelemetry is enabled"
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/opentelemetry-javaagent.jar -Dotel.service.name=${OTEL_SERVICE_NAME:-pipeline-service}"

    if [ -n "$OTEL_EXPORTER_OTLP_ENDPOINT" ]; then
        JAVA_OPTS=$JAVA_OPTS" -Dotel.exporter.otlp.endpoint=$OTEL_EXPORTER_OTLP_ENDPOINT "
    else
        echo "OpenTelemetry export is disabled"
        JAVA_OPTS=$JAVA_OPTS" -Dotel.traces.exporter=none -Dotel.metrics.exporter=none "
    fi
    echo "Using OpenTelemetry Java Agent"
fi

if [[ "${DEPLOY_MODE}" == "KUBERNETES" || "${DEPLOY_MODE}" == "KUBERNETES_ONPREM" || "${DEPLOY_VERSION}" == "COMMUNITY" ]]; then
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/config.yml
else
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/config.yml > /opt/harness/logs/pipeline-service.log 2>&1
fi
