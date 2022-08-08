/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

/**
 * Created by rishi on 12/22/16.
 */

@OwnedBy(CDC)
public enum PhaseStepType {
  SELECT_NODE,
  INFRASTRUCTURE_NODE,
  @Deprecated PROVISION_NODE,
  DISABLE_SERVICE,
  DEPLOY_SERVICE,
  ENABLE_SERVICE,
  VERIFY_SERVICE,
  WRAP_UP,
  PRE_DEPLOYMENT("Pre-Deployment"),
  ROLLBACK_PROVISIONERS,
  POST_DEPLOYMENT("Post-Deployment"),
  STOP_SERVICE,
  @Deprecated DE_PROVISION_NODE,
  CLUSTER_SETUP,
  CONTAINER_SETUP,
  CONTAINER_DEPLOY,
  PCF_SETUP,
  PCF_RESIZE,
  PCF_ROUTE_UPDATE,
  PCF_SWICH_ROUTES,
  START_SERVICE,
  DEPLOY_AWSCODEDEPLOY,
  PREPARE_STEPS,
  DEPLOY_AWS_LAMBDA,
  COLLECT_ARTIFACT,
  AMI_AUTOSCALING_GROUP_SETUP,
  AMI_DEPLOY_AUTOSCALING_GROUP,
  AMI_SWITCH_AUTOSCALING_GROUP_ROUTES,
  ECS_UPDATE_LISTENER_BG,
  ECS_UPDATE_ROUTE_53_DNS_WEIGHT,
  HELM_DEPLOY,
  ROUTE_UPDATE,
  K8S_PHASE_STEP,
  PROVISION_INFRASTRUCTURE,
  ROLLBACK_PROVISION_INFRASTRUCTURE,
  SPOTINST_SETUP,
  SPOTINST_DEPLOY,
  SPOTINST_ROLLBACK,
  SPOTINST_LISTENER_UPDATE_ROLLBACK,
  SPOTINST_LISTENER_UPDATE,
  STAGE_EXECUTION,
  AZURE_VMSS_SETUP,
  AZURE_VMSS_DEPLOY,
  AZURE_VMSS_ROLLBACK,
  AZURE_VMSS_SWITCH_ROUTES,
  AZURE_VMSS_SWITCH_ROLLBACK,
  CUSTOM_DEPLOYMENT_PHASE_STEP,
  AZURE_WEBAPP_SLOT_SETUP,
  AZURE_WEBAPP_SLOT_SWAP,
  AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT,
  AZURE_WEBAPP_SLOT_ROLLBACK,
  COLLECT_INSTANCES;

  PhaseStepType() {}

  PhaseStepType(String defaultName) {
    this.defaultName = defaultName;
  }

  @Getter private String defaultName;
}
