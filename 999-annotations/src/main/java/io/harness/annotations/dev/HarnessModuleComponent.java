/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.annotations.dev;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_AMI_ASG, HarnessModuleComponent.CDS_K8S})
public enum HarnessModuleComponent {
  CDS_AMI_ASG,
  CDS_APPROVALS,
  CDS_ARTIFACTS,
  CDS_AWS_LAMBDA,
  CDS_COMMON_STEPS,
  CDS_DASHBOARD,
  CDS_DEPLOYMENT_FREEZE,
  CDS_DEPLOYMENT_TEMPLATES,
  CDS_ECS,
  CDS_EXPRESSION_ENGINE,
  CDS_FIRST_GEN,
  CDS_GITX,
  CDS_GITOPS,
  CDS_INFRA_PROVISIONERS,
  CDS_K8S,
  CDS_MIGRATOR,
  CDS_PCF,
  CDS_PIPELINE,
  CDS_PLG_LICENSING,
  CDS_SERVERLESS,
  CDS_SERVICE_ENVIRONMENT,
  CDS_TEMPLATE_LIBRARY,
  CDS_TRADITIONAL,
  CDS_TRIGGERS,
  CI_PLG_LICENSING,
  CDS_GIT_CLIENTS,
  CDS_AZURE_WEBAPP,
  CCM_PERSPECTIVE,
  DEBEZIUM
}
