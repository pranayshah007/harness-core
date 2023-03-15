# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

FROM redhat/ubi8-minimal:8.7

LABEL name="harnessdev/delegate-runner" \
      vendor="Harness" \
      maintainer="Harness"

RUN microdnf update --nodocs \
  && microdnf install --nodocs \
    procps \
    hostname \
    lsof \
    findutils \
    shadow-utils \
  && useradd -u 1001 -g 0 harness \
  && microdnf remove shadow-utils \
  && microdnf clean all \
  && rm -rf /var/cache/yum \
  && mkdir -p /opt/harness/

COPY scripts /opt/harness/

RUN chmod -R 775 /opt/harness \
  && chgrp -R 0 /opt/harness \
  && chown -R 1001 /opt/harness

WORKDIR /opt/harness

COPY --from=adoptopenjdk/openjdk11:jre-11.0.14_9-ubi-minimal /opt/java/openjdk/ /opt/java/openjdk/

ENV HOME=/opt/harness
ENV JAVA_HOME=/opt/java/openjdk/
ENV PATH="$JAVA_HOME/bin:${PATH}"

COPY ./TaskApp_deploy.jar ./task.jar
COPY ./taskfile /etc/config/taskfile

USER 1001

CMD [ "./start.sh" ]