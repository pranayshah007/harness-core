#!/usr/bin/env bash
# Copyright 2018 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -e

IMAGE_NAME=harness/onprem-install-builder
IMAGE_TAG=helper-help

docker push "$IMAGE_NAME:$IMAGE_TAG"
