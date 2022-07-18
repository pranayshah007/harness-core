#!/bin/sh
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#Build your java service and then pass in the path to that jar
if [[ $# = 0 ]] ; then printf "Error - You did not pass in the path the jar you want nativized!\n"; exit 1; else JAR_PATH="${1}"; fi

native-image --no-fallback \
-H:TraceClassInitialization=true \
-H:ConfigurationFileDirectories=target/config \
-H:+ReportExceptionStackTraces \
-H:+PrintClassInitialization \
-H:IncludeResources="org/joda/time/tz/data/ZoneInfoMap" \
-H:IncludeResources="org/joda/time/tz/data/America/New_York" \
--initialize-at-build-time=ch.qos.logback \
--initialize-at-build-time=org.hibernate.validator.internal.util.logging.Log \
--initialize-at-run-time=io.netty \
--initialize-at-run-time=io.netty.buffer.AbstractReferenceCountedByteBuf,io.netty.util.AbstractReferenceCounted \
--trace-class-initialization='org.codehaus.janino.Java$Literal' \
-jar $JAR_PATH
