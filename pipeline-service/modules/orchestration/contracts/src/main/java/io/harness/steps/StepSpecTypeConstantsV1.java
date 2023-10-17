/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

@OwnedBy(PIPELINE)
public interface StepSpecTypeConstantsV1 {
  String SHELL_SCRIPT = "shell-script";
  String BARRIER = "barrier";
  String HTTP = "http";
  String CUSTOM_APPROVAL = "custom-approval";
  String HARNESS_APPROVAL = "harness-approval";
  String JIRA_APPROVAL = "jira-approval";
  String JIRA_CREATE = "jira-create";
  String JIRA_UPDATE = "jira-update";
  String RESOURCE_CONSTRAINT = "resource-constraint";
  String QUEUE = "queue";
  String FLAG_CONFIGURATION = "flag-configuration";
  String SERVICENOW_APPROVAL = "service-now-approval";
  String SERVICENOW_CREATE = "service-now-create";
  String SERVICENOW_UPDATE = "service-now-update";
  String SERVICENOW_IMPORT_SET = "service-now-import-set";
  String APPROVAL_STAGE = "approval";
  String PIPELINE_STAGE = "pipeline";
  String PIPELINE_ROLLBACK_STAGE = "pipeline-rollback";
  String CUSTOM_STAGE = "custom";
  String FEATURE_FLAG_STAGE = "deature-flag";
  String POLICY_STEP = "policy";
  String EMAIL = "email";
  String WAIT_STEP = "wait";
  String INIT_CONTAINER_STEP = "init-container";
  String RUN_CONTAINER_STEP = "run-container";
  String INIT_CONTAINER_STEP_V2 = "initialize-container";
  //  String INIT_CONTAINER_STEP_V2 = "InitContainer";

  String APPROVAL_FACILITATOR = "APPROVAL_FACILITATOR";
  String RESOURCE_RESTRAINT_FACILITATOR_TYPE = "RESOURCE_RESTRAINT";

  StepType BARRIER_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.BARRIER).setStepCategory(StepCategory.STEP).build();
  StepType HTTP_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.HTTP).setStepCategory(StepCategory.STEP).build();
  StepType FLAG_CONFIGURATION_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstantsV1.FLAG_CONFIGURATION)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType QUEUE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.QUEUE).setStepCategory(StepCategory.STEP).build();
  StepType RESOURCE_CONSTRAINT_STEP_TYPE = StepType.newBuilder()
                                               .setType(StepSpecTypeConstantsV1.RESOURCE_CONSTRAINT)
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  StepType CUSTOM_APPROVAL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.CUSTOM_APPROVAL).setStepCategory(StepCategory.STEP).build();
  StepType POLICY_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.POLICY_STEP).setStepCategory(StepCategory.STEP).build();
  StepType HARNESS_APPROVAL_STEP_TYPE = StepType.newBuilder()
                                            .setType(StepSpecTypeConstantsV1.HARNESS_APPROVAL)
                                            .setStepCategory(StepCategory.STEP)
                                            .build();
  StepType JIRA_APPROVAL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.JIRA_APPROVAL).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_APPROVAL_STEP_TYPE = StepType.newBuilder()
                                                .setType(StepSpecTypeConstantsV1.SERVICENOW_APPROVAL)
                                                .setStepCategory(StepCategory.STEP)
                                                .build();
  StepType JIRA_CREATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.JIRA_CREATE).setStepCategory(StepCategory.STEP).build();
  StepType JIRA_UPDATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.JIRA_UPDATE).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_CREATE_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstantsV1.SERVICENOW_CREATE)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType SERVICE_NOW_UPDATE_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstantsV1.SERVICENOW_UPDATE)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType SHELL_SCRIPT_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.SHELL_SCRIPT).setStepCategory(StepCategory.STEP).build();
  StepType EMAIL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.EMAIL).setStepCategory(StepCategory.STEP).build();
  StepType WAIT_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstantsV1.WAIT_STEP).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_IMPORT_SET_STEP_TYPE = StepType.newBuilder()
                                                  .setType(StepSpecTypeConstantsV1.SERVICENOW_IMPORT_SET)
                                                  .setStepCategory(StepCategory.STEP)
                                                  .build();

  StepType INIT_CONTAINER_STEP_TYPE = StepType.newBuilder()
                                          .setType(StepSpecTypeConstantsV1.INIT_CONTAINER_STEP)
                                          .setStepCategory(StepCategory.STEP)
                                          .build();

  StepType RUN_CONTAINER_STEP_TYPE = StepType.newBuilder()
                                         .setType(StepSpecTypeConstantsV1.RUN_CONTAINER_STEP)
                                         .setStepCategory(StepCategory.STEP)
                                         .build();

  StepType INIT_CONTAINER_V2_STEP_TYPE = StepType.newBuilder()
                                             .setType(StepSpecTypeConstantsV1.INIT_CONTAINER_STEP_V2)
                                             .setStepCategory(StepCategory.STEP)
                                             .build();
}
