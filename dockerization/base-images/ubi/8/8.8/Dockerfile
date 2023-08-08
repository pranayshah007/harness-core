# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# BASE IMAGE - UBI

# Usage: Absolute base image for all images. Father of all!!
# Test image locally by running this command:
#
# $ docker build \
#     -f Dockerfile-ubi-base" \
#     -t <tag> \
#     .

FROM redhat/ubi8-minimal:8.8
CMD ["/bin/sh"]
RUN microdnf install curl libstdc++.i686 make bash unzip hostname tar gzip procps

RUN curl -L https://app.harness.io/public/shared/tools/yq/1.20.5/yq -o /usr/bin/yq && chmod +x /usr/bin/yq

RUN set -x \
    && mkdir -p /opt/harness/plugins \
    && mkdir -p /tmp \
    && chown -R 65534:65534 /opt/harness /tmp \
    && chmod -R 700 /opt/harness /tmp

WORKDIR /opt/harness

USER 65534
