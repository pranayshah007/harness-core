/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.eks;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
public class EksConstants {
  private EksConstants() {}
  public static final String REGION_DELIMITER = "/";
  public static final String EKS_KUBECFG_ARGS_TOKEN = "token";
  public static final String EKS_KUBECFG_ARGS_EXTERNAL_ID = "--external-id";
  public static final String EKS_KUBECFG_ARGS_ROLE = "--role";
  public static final String EKS_KUBECFG_ENV_VARS_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
  public static final String EKS_KUBECFG_ENV_VARS_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
  public static final String AWS_STS_REGIONAL_ENDPOINTS = "AWS_STS_REGIONAL_ENDPOINTS";
  public static final String AWS_STS_REGIONAL = "regional";
  public static final String EKS_KUBECFG_ARGS_I = "-i";
}
