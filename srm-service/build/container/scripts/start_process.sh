#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p /opt/harness/logs
touch /opt/harness/logs/cv-nextgen.log

if [[ -v "{hostname}" ]]; then
   export HOSTNAME=$(hostname)
fi

if [[ -z "$MEMORY" ]]; then
   export MEMORY=2048
fi

echo "Using memory " $MEMORY

if [[ -z "$COMMAND" ]]; then
   export COMMAND=server
fi


if [[ -z "$CAPSULE_JAR" ]]; then
   export CAPSULE_JAR=/opt/harness/cv-nextgen-capsule.jar
fi

export JAVA_OPTS="-Xms${MEMORY}m -Xmx${MEMORY}m -XX:+HeapDumpOnOutOfMemoryError -Xloggc:mygclogfilename.gc -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=40 -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 $JAVA_ADVANCED_FLAGS $JAVA_17_FLAGS"

#if [[ "${ENABLE_APPDYNAMICS}" == "true" ]]; then
#    mkdir /opt/harness/AppServerAgent && unzip AppServerAgent.zip -d /opt/harness/AppServerAgent
#    node_name="-Dappdynamics.agent.nodeName=$(hostname)"
#    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/AppServerAgent/javaagent.jar -Dappdynamics.jvm.shutdown.mark.node.as.historical=true"
#    JAVA_OPTS="$JAVA_OPTS $node_name"
#    echo "Using Appdynamics java agent"
#fi

if [[ "${ENABLE_MONITORING}" == "true" ]] ; then
    echo "Monitoring  is enabled"
    JAVA_OPTS="$JAVA_OPTS ${MONITORING_FLAGS}"
    echo "Using inspectIT Java Agent"
fi

#if [[ "${ENABLE_COVERAGE}" == "true" ]] ; then
#    echo "functional code coverage is enabled"
#    mkdir /opt/harness/jacoco-0.8.7 && unzip jacoco-0.8.7.zip -d /opt/harness/jacoco-0.8.7
#    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/jacoco-0.8.7/lib/jacocoagent.jar=port=6300,address=0.0.0.0,append=true,output=tcpserver,destfile=jacoco-remote.exec"
#    echo "Using Jacoco Java Agent"
#fi

#if [[ "${ENABLE_OPENTELEMETRY}" == "true" ]] ; then
#    echo "OpenTelemetry is enabled"
#    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/opentelemetry-javaagent.jar -Dotel.service.name=${OTEL_SERVICE_NAME:-cv-nextgen}"
#
#    if [ -n "$OTEL_EXPORTER_OTLP_ENDPOINT" ]; then
#        JAVA_OPTS=$JAVA_OPTS" -Dotel.exporter.otlp.endpoint=$OTEL_EXPORTER_OTLP_ENDPOINT "
#    else
#        echo "OpenTelemetry export is disabled"
#        JAVA_OPTS=$JAVA_OPTS" -Dotel.traces.exporter=none -Dotel.metrics.exporter=none "
#    fi
#    echo "Using OpenTelemetry Java Agent"
#fi

if [[ "${DEPLOY_MODE}" == "KUBERNETES" ]] || [[ "${DEPLOY_MODE}" == "KUBERNETES_ONPREM" ]]; then
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/cv-nextgen-config.yml
else
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/cv-nextgen-config.yml > /opt/harness/logs/cv-nextgen.log 2>&1
fi
