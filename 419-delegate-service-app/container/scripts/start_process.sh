#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p /opt/harness/logs

export JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:MinRAMPercentage=40.0 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/heapdump -Xloggc:mygclogfilename.gc $JAVA_ADVANCED_FLAGS $JAVA_17_FLAGS"


java $JAVA_OPTS -jar /opt/harness/delegate-service-capsule.jar server /opt/harness/config.yml
