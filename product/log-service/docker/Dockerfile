# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# To be used when building in CIE

# Create image for log-service.
#
# Build the log-service image using:
# > bazel build --platforms=@io_bazel_rules_go//go/toolchain:linux_amd64 //product/log-service/...
#
# > docker build -t harness/log-service:valpha-0.1 -f product/log-service/docker/Dockerfile $(bazel info bazel-bin)/product/log-service/

FROM us.gcr.io/platform-205701/harness/ubi8/go1:1.20.5
# Copy go binary
COPY log-service_/log-service /usr/local/bin/log-service
USER root
RUN chown -R 65534:65534 /usr/local/bin
USER 65534
WORKDIR /usr/local/bin
CMD ["/usr/local/bin/log-service", "server"]
