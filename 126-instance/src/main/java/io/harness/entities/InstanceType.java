/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.DX)
public enum InstanceType {
  PHYSICAL_HOST_INSTANCE,
  EC2_CLOUD_INSTANCE,
  GCP_CLOUD_INSTANCE,
  ECS_CONTAINER_INSTANCE,
  K8S_INSTANCE,
  PCF_INSTANCE,
  AZURE_VMSS_INSTANCE,
  AZURE_WEB_APP_INSTANCE,
  KUBERNETES_CONTAINER_INSTANCE,
  NATIVE_HELM_INSTANCE,
  SERVERLESS_AWS_LAMBDA_INSTANCE,
  AZURE_SSH_WINRM_INSTANCE,
  AWS_SSH_WINRM_INSTANCE,
  CUSTOM_DEPLOYMENT_INSTANCE,
  ECS_INSTANCE,
  TAS_INSTANCE,
  SPOT_INSTANCE,
  ASG_INSTANCE,
  GOOGLE_CLOUD_FUNCTIONS_INSTANCE,
  AWS_LAMBDA_INSTANCE
}
