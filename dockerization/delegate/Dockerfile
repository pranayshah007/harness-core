# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

FROM redhat/ubi8-minimal:8.7

LABEL name="harness/delegate" \
      vendor="Harness" \
      maintainer="Harness"

RUN microdnf update --nodocs \
  && microdnf install --nodocs \
    procps \
    hostname \
    lsof \
    findutils \
    tar \
    gzip \
    git \
    shadow-utils \
    glibc-langpack-en \
  && useradd -u 1001 -g 0 harness \
  && microdnf remove shadow-utils \
  && microdnf clean all \
  && rm -rf /var/cache/yum \
  && mkdir -p /opt/harness-delegate/

COPY immutable-scripts /opt/harness-delegate/

WORKDIR /opt/harness-delegate

COPY --from=adoptopenjdk/openjdk11:jre-11.0.19_7-ubi-minimal /opt/java/openjdk/ /opt/java/openjdk/

ENV LANG=en_US.UTF-8
ENV HOME=/opt/harness-delegate
ENV CLIENT_TOOLS_DOWNLOAD_DISABLED=true
ENV INSTALL_CLIENT_TOOLS_IN_BACKGROUND=true
ENV JAVA_HOME=/opt/java/openjdk/
ENV PATH="$JAVA_HOME/bin:${PATH}"

ARG TARGETARCH
ARG BASEURL=https://app.harness.io/public/shared/delegates
ARG DELEGATEVERSION

RUN mkdir -m 777 -p client-tools/kubectl/v1.24.3 \
  && curl -s -L -o client-tools/kubectl/v1.24.3/kubectl https://app.harness.io/public/shared/tools/kubectl/release/v1.24.3/bin/linux/$TARGETARCH/kubectl \
  && mkdir -m 777 -p client-tools/helm/v2.13.1 \
  && curl -s -L -o client-tools/helm/v2.13.1/helm https://app.harness.io/public/shared/tools/helm/release/v2.13.1/bin/linux/$TARGETARCH/helm \
  && mkdir -m 777 -p client-tools/helm/v3.1.2 \
  && curl -s -L -o client-tools/helm/v3.1.2/helm https://app.harness.io/public/shared/tools/helm/release/v3.1.2/bin/linux/$TARGETARCH/helm \
  && mkdir -m 777 -p client-tools/helm/v3.8.0 \
  && curl -s -L -o client-tools/helm/v3.8.0/helm https://app.harness.io/public/shared/tools/helm/release/v3.8.0/bin/linux/$TARGETARCH/helm \
  && mkdir -m 777 -p client-tools/go-template/v0.4.2 \
  && curl -s -L -o client-tools/go-template/v0.4.2/go-template https://app.harness.io/public/shared/tools/go-template/release/v0.4.2/bin/linux/$TARGETARCH/go-template \
  && mkdir -m 777 -p client-tools/harness-pywinrm/v0.4-dev \
  && curl -s -L -o client-tools/harness-pywinrm/v0.4-dev/harness-pywinrm https://app.harness.io/public/shared/tools/harness-pywinrm/release/v0.4-dev/bin/linux/$TARGETARCH/harness-pywinrm \
  && mkdir -m 777 -p client-tools/chartmuseum/v0.15.0 \
  && curl -s -L -o client-tools/chartmuseum/v0.15.0/chartmuseum https://app.harness.io/public/shared/tools/chartmuseum/release/v0.15.0/bin/linux/$TARGETARCH/chartmuseum \
  && mkdir -m 777 -p client-tools/tf-config-inspect/v1.2 \
  && curl -s -L -o client-tools/tf-config-inspect/v1.2/terraform-config-inspect https://app.harness.io/public/shared/tools/terraform-config-inspect/v1.2/linux/$TARGETARCH/terraform-config-inspect \
  && mkdir -m 777 -p client-tools/oc/v4.2.16 \
  && curl -s -L -o client-tools/oc/v4.2.16/oc https://app.harness.io/public/shared/tools/oc/release/v4.2.16/bin/linux/$TARGETARCH/oc \
  && mkdir -m 777 -p client-tools/kustomize/v4.5.4 \
  && curl -s -L -o client-tools/kustomize/v4.5.4/kustomize https://app.harness.io/public/shared/tools/kustomize/release/v4.5.4/bin/linux/$TARGETARCH/kustomize \
  && mkdir -m 777 -p client-tools/scm/9b34bea7 \
  && curl -s -L -o client-tools/scm/9b34bea7/scm https://app.harness.io/public/shared/tools/scm/release/9b34bea7/bin/linux/$TARGETARCH/scm \
  && chmod -R 775 /opt/harness-delegate \
  && chgrp -R 0 /opt/harness-delegate  \
  && chown -R 1001 /opt/harness-delegate

ENV PATH=/opt/harness-delegate/client-tools/kubectl/v1.24.3/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/go-template/v0.4.2/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/chartmuseum/v0.15.0/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/tf-config-inspect/v1.2/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/kustomize/v4.5.4/:$PATH

RUN curl -s -L -o delegate.jar $BASEURL/$DELEGATEVERSION/delegate.jar

USER 1001

CMD [ "./start.sh" ]