#!/bin/bash -xe
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

DISABLE_CLIENT_TOOLS=$1
ARCH=$2
if [[ ( -z "$DISABLE_CLIENT_TOOLS") || ("$DISABLE_CLIENT_TOOLS" = "false") ]]; then
  echo "Installing client tools for $ARCH"
  mkdir -m 777 -p client-tools/kubectl/v1.19.2 \
  && curl -s -L -o client-tools/kubectl/v1.19.2/kubectl https://dl.k8s.io/release/v1.24.3.0/bin/linux/amd64/kubectl \
  && mkdir -m 777 -p client-tools/go-template/v0.4.1 \
  && curl -s -L -o client-tools/go-template/v0.4.1/go-template https://github.com/SchwarzIT/go-template/releases/download/v0.4.3/gt-darwin-amd64 \
  && mkdir -m 777 -p client-tools/harness-pywinrm/v0.4-dev \
  && curl -s -L -o client-tools/harness-pywinrm/v0.4-dev/harness-pywinrm https://app.harness.io/public/shared/tools/harness-pywinrm/release/v0.4-dev/bin/linux/$ARCH/harness-pywinrm \
  && mkdir -m 777 -p client-tools/helm/v3.8.0 \
  && curl -s -L -o client-tools/helm/v3.8.0/helm https://get.helm.sh/helm-v3.9.2-linux-amd64.tar.gz \
  && mkdir -m 777 -p client-tools/chartmuseum/v0.12.0 \
  && curl -s -L -o client-tools/chartmuseum/v0.12.0/chartmuseum https://get.helm.sh/chartmuseum-v0.15.0-linux-arm.tar.gz \
  && mkdir -m 777 -p client-tools/tf-config-inspect/v1.1 \
  && curl -s -L -o client-tools/tf-config-inspect/v1.1/terraform-config-inspect https://app.harness.io/public/shared/tools/terraform-config-inspect/v1.1/linux/$ARCH/terraform-config-inspect \
  && mkdir -m 777 -p client-tools/oc/v4.2.16 \
  && curl -s -L -o client-tools/oc/v4.2.16/oc https://app.harness.io/public/shared/tools/oc/release/v4.2.16/bin/linux/$ARCH/oc \
  && mkdir -m 777 -p client-tools/kustomize/v4.0.0 \
  && curl -s -L -o client-tools/kustomize/v4.0.0/kustomize https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2Fv4.5.6/kustomize_v4.5.6_linux_amd64.tar.gz \
  && mkdir -m 777 -p client-tools/scm/91df8e76 \
  && curl -s -L -o client-tools/scm/91df8e76/scm https://app.harness.io/public/shared/tools/scm/release/91df8e76/bin/linux/$ARCH/scm
else
  echo "Client tools are disabled"
fi
