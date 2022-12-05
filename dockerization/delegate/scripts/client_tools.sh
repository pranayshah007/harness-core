#!/bin/bash -xe
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

DISABLE_CLIENT_TOOLS=$1
ARCH=$2
if [[ ( -z "$DISABLE_CLIENT_TOOLS") || ("$DISABLE_CLIENT_TOOLS" = "false") ]]; then
  echo "Installing client tools for $ARCH"
  mkdir -m 777 -p client-tools/kubectl/v1.24.3 \
  && curl -s -L -o client-tools/kubectl/v1.24.3/kubectl https://qa.harness.io/public/shared/tools/kubectl/release/v1.24.3/bin/linux/$ARCH/kubectl \
  && mkdir -m 777 -p client-tools/go-template/v0.4.1 \
  && curl -s -L -o client-tools/go-template/v0.4.1/go-template https://qa.harness.io/public/shared/tools/go-template/release/v0.4.1/bin/linux/$ARCH/go-template \
  && mkdir -m 777 -p client-tools/harness-pywinrm/v0.4-dev \
  && curl -s -L -o client-tools/harness-pywinrm/v0.4-dev/harness-pywinrm https://qa.harness.io/public/shared/tools/harness-pywinrm/release/v0.4-dev/bin/linux/$ARCH/harness-pywinrm \
  && mkdir -m 777 -p client-tools/helm/v3.9.2 \
  && curl -s -L -o client-tools/helm/v3.9.2/helm https://qa.harness.io/public/shared/tools/helm/release/v3.9.2/bin/linux/$ARCH/helm \
  && mkdir -m 777 -p client-tools/chartmuseum/v0.15.0 \
  && curl -s -L -o client-tools/chartmuseum/v0.15.0/chartmuseum https://qa.harness.io/public/shared/tools/chartmuseum/release/v0.15.0/bin/linux/$ARCH/chartmuseum \
  && mkdir -m 777 -p client-tools/tf-config-inspect/v1.1 \
  && curl -s -L -o client-tools/tf-config-inspect/v1.1/terraform-config-inspect https://qa.harness.io/public/shared/tools/terraform-config-inspect/v1.1/linux/$ARCH/terraform-config-inspect \
  && mkdir -m 777 -p client-tools/oc/v4.2.16 \
  && curl -s -L -o client-tools/oc/v4.2.16/oc https://qa.harness.io/public/shared/tools/oc/release/v4.2.16/bin/linux/$ARCH/oc \
  && mkdir -m 777 -p client-tools/kustomize/v4.5.4 \
  && curl -s -L -o client-tools/kustomize/v4.5.4/kustomize https://qa.harness.io/public/shared/tools/kustomize/release/v4.5.4/bin/linux/$ARCH/kustomize \
  && mkdir -m 777 -p client-tools/scm/3920e509 \
  && curl -s -L -o client-tools/scm/3920e509/scm https://qa.harness.io/public/shared/tools/scm/release/3920e509/bin/linux/$ARCH/scm
else
  echo "Client tools are disabled"
fi
