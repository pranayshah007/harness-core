# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

FROM redhat/ubi8-minimal:8.7

COPY --from=adoptopenjdk/openjdk11:jre-11.0.19_7-ubi-minimal /opt/java/openjdk/ /opt/java/openjdk/

WORKDIR /opt/harness

COPY scripts .

ENV HOME=/opt/harness
ENV JAVA_HOME=/opt/java/openjdk/
ENV PATH="$JAVA_HOME/bin:${PATH}"

COPY ./secret-decryption.jar ./secret-decryption.jar

USER 1001

CMD [ "./start.sh" ]