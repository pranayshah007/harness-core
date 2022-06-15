ARG BUILD_TAG=?
ARG REGISTRY_PATH=?
ARG REPO_PATH=?
ARG SERVICE_NAME=?
FROM ${REGISTRY_PATH}/${REPO_PATH}/${SERVICE_NAME}:${BUILD_TAG}

ARG APPD_AGENT=?
ARG TAKIPI_AGENT=?
ARG OCELET_AGENT=?
ARG ET_AGENT=?

USER root

RUN mkdir -p /opt/harness/
COPY --chown=65534:65534 ./${APPD_AGENT} /opt/harness/
ADD --chown=65534:65534 ./${TAKIPI_AGENT} /opt/harness/
ADD --chown=65534:65534 ./${OCELET_AGENT} /opt/harness/
ADD --chown=65534:65534 ./${ET_AGENT} /opt/harness/

RUN chmod -R +x /opt/harness/ \
    && chmod -R +x /opt/harness/takipi \
    && chmod 700 -R /opt/harness/ \
    && chown -R 65534:65534 /opt/harness/

USER 65534