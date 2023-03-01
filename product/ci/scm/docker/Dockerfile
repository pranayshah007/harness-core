# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# To be used when building in CIE

# Create image for ci-scm service
#
# Build the ci-scm docker image using:
# > bazel build --platforms=@io_bazel_rules_go//go/toolchain:linux_amd64 //product/ci/scm/...
# > docker build -t harness/ci-scm:<tag> -f product/ci/scm/docker/Dockerfile $(bazel info bazel-bin)/product/ci/scm/

# First stage
FROM alpine:3.12
RUN GRPC_HEALTH_PROBE_VERSION=v0.4.14 && \
    wget -qO/bin/grpc_health_probe https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-amd64 && \
    chmod +x /bin/grpc_health_probe

# Second stage
FROM us.gcr.io/platform-205701/ubi/ubi-go:8.7
COPY --from=0 /bin/grpc_health_probe /grpc_health_probe
# Copy ci-scm binary
COPY scm_/scm /usr/local/bin/scm
USER root
RUN chown -R 65534:65534 /usr/local/bin /grpc_health_probe
USER 65534:65534
WORKDIR /usr/local/bin
CMD ["/usr/local/bin/scm"]