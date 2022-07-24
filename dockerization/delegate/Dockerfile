FROM ubuntu:20.04

RUN apt-get update && \
    apt-get -y install curl unzip gnupg2 libgcrypt20 locales && \
    locale-gen en_US.UTF-8 && \
    export LANG=en_US.UTF-8 && \
    apt-get clean

ARG TARGETPLATFORM

RUN curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -

COPY apt-source-list /etc/apt/sources.list.d/kubernetes.list

RUN apt-get update && apt-get install -y kubectl

COPY scripts /opt/harness-delegate/

RUN chmod +x /opt/harness-delegate/*.sh

WORKDIR /opt/harness-delegate

RUN chmod -R 755 /opt/harness-delegate

ARG disable_client_tools

ENV CLIENT_TOOLS_DOWNLOAD_DISABLED=$disable_client_tools

RUN if [ "$TARGETPLATFORM" = "linux/arm64" ]; then curl -s https://app.harness.io/public/shared/jre/openjdk-11.0.14_9/jdk-11.0.14_linux-aarch64_bin.tar.gz | tar -xz; mkdir jdk-11.0.14+9-jre; mv jdk-11.0.14/* jdk-11.0.14+9-jre; rm -rf jdk-11.0.14; elif [ "$TARGETPLATFORM" = "linux/amd64" ]; then curl -s https://app.harness.io/public/shared/jre/openjdk-11.0.14_9/OpenJDK11U-jre_x64_linux_hotspot_11.0.14_9.tar.gz | tar -xz; else echo "Unsupported architecture type"; exit; fi

ARG watcher_version
RUN curl -#k https://app.harness.io/public/shared/watchers/builds/openjdk-8u242/$watcher_version/watcher.jar -o watcher.jar

CMD ./entrypoint.sh && bash -c ' \
    while [[ ! -e watcher.log ]]; do sleep 1s; done; tail -F watcher.log & \
    while [[ ! -e delegate.log ]]; do sleep 1s; done; tail -F delegate.log'