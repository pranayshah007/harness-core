#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p /opt/harness/logs
touch /opt/harness/logs/command-library-server.log

if [[ -v "{hostname}" ]]; then
  export HOSTNAME=$(hostname)
fi

if [[ -z "$MEMORY" ]]; then
  export MEMORY=4096
fi

echo "Using memory " $MEMORY

if [[ -z "$CAPSULE_JAR" ]]; then
  export CAPSULE_JAR=/opt/harness/command-library-app-capsule.jar
fi

export JAVA_OPTS="-Xms${MEMORY}m -Xmx${MEMORY}m -XX:+HeapDumpOnOutOfMemoryError -Xloggc:mygclogfilename.gc -XX:+UseG1GC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 $JAVA_ADVANCED_FLAGS $JAVA_17_FLAGS"

if [[ "${DEPLOY_MODE}" == "KUBERNETES" ]] || [[ "${DEPLOY_MODE}" == "KUBERNETES_ONPREM" ]]; then
  java $JAVA_OPTS -jar $CAPSULE_JAR /opt/harness/command-library-server-config.yml
else
  java $JAVA_OPTS -jar $CAPSULE_JAR /opt/harness/command-library-server-config.yml >/opt/harness/logs/command-library-server.log 2>&1
fi
