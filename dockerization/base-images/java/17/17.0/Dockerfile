# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# UBI BASE IMAGE + JDK

# Usage: Application runtime base image for java based application, CIE agent base image
# Test image locally by running this command:
#
# $ docker build \
#     -f Dockerfile-ubi-java-base" \
#     -t <tag> \
#     .

FROM us.gcr.io/platform-205701/harness/ubi8:8.8
RUN curl -OL https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.7%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.7_7.tar.gz && mkdir -p /opt/harness/OpenJDK17 && tar -xzf OpenJDK17U-jdk_x64_linux_hotspot_17.0.7_7.tar.gz -C /opt/harness/OpenJDK17 && ls -al /opt/harness/OpenJDK17/jdk-17.0.7+7 && /opt/harness/OpenJDK17/jdk-17.0.7+7/bin/java -version &&  rm -rf OpenJDK17U-jdk_x64_linux_hotspot_17.0.7_7.tar.gz
ENV JAVA_HOME=/opt/harness/OpenJDK17/jdk-17.0.7+7
ENV PATH=$JAVA_HOME/bin:$PATH
