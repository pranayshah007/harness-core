/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
public interface StepSpecTypeConstants {
  String SHELL_SCRIPT = "ShellScript";
  String BARRIER = "Barrier";
  String HTTP = "Http";
  String CUSTOM_APPROVAL = "CustomApproval";
  String HARNESS_APPROVAL = "HarnessApproval";
  String JIRA_APPROVAL = "JiraApproval";
  String JIRA_CREATE = "JiraCreate";
  String JIRA_UPDATE = "JiraUpdate";
  String RESOURCE_CONSTRAINT = "ResourceConstraint";
  String QUEUE = "Queue";
  String FLAG_CONFIGURATION = "FlagConfiguration";
  String SERVICENOW_APPROVAL = "ServiceNowApproval";
  String SERVICENOW_CREATE = "ServiceNowCreate";
  String SERVICENOW_UPDATE = "ServiceNowUpdate";
  String SERVICENOW_IMPORT_SET = "ServiceNowImportSet";
  String APPROVAL_STAGE = "Approval";
  String PIPELINE_STAGE = "Pipeline";
  String PIPELINE_ROLLBACK_STAGE = "PipelineRollback";
  String CUSTOM_STAGE = "Custom";
  String FEATURE_FLAG_STAGE = "FeatureFlag";
  String POLICY_STEP = "Policy";
  String EMAIL = "Email";
  String WAIT_STEP = "Wait";
  String INIT_CONTAINER_STEP = "InitContainer";
  String RUN_CONTAINER_STEP = "RunContainer";
  String INIT_CONTAINER_STEP_V2 = "InitializeContainer";
  //  String INIT_CONTAINER_STEP_V2 = "InitContainer";

  String APPROVAL_FACILITATOR = "APPROVAL_FACILITATOR";
  String RESOURCE_RESTRAINT_FACILITATOR_TYPE = "RESOURCE_RESTRAINT";

  StepType BARRIER_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.BARRIER).setStepCategory(StepCategory.STEP).build();
  StepType HTTP_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.HTTP).setStepCategory(StepCategory.STEP).build();
  StepType FLAG_CONFIGURATION_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.FLAG_CONFIGURATION)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType QUEUE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.QUEUE).setStepCategory(StepCategory.STEP).build();
  StepType RESOURCE_CONSTRAINT_STEP_TYPE = StepType.newBuilder()
                                               .setType(StepSpecTypeConstants.RESOURCE_CONSTRAINT)
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  StepType CUSTOM_APPROVAL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CUSTOM_APPROVAL).setStepCategory(StepCategory.STEP).build();
  StepType POLICY_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.POLICY_STEP).setStepCategory(StepCategory.STEP).build();
  StepType HARNESS_APPROVAL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.HARNESS_APPROVAL).setStepCategory(StepCategory.STEP).build();
  StepType JIRA_APPROVAL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.JIRA_APPROVAL).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_APPROVAL_STEP_TYPE = StepType.newBuilder()
                                                .setType(StepSpecTypeConstants.SERVICENOW_APPROVAL)
                                                .setStepCategory(StepCategory.STEP)
                                                .build();
  StepType JIRA_CREATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.JIRA_CREATE).setStepCategory(StepCategory.STEP).build();
  StepType JIRA_UPDATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.JIRA_UPDATE).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_CREATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.SERVICENOW_CREATE).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_UPDATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.SERVICENOW_UPDATE).setStepCategory(StepCategory.STEP).build();
  StepType SHELL_SCRIPT_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.SHELL_SCRIPT).setStepCategory(StepCategory.STEP).build();
  StepType EMAIL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.EMAIL).setStepCategory(StepCategory.STEP).build();
  StepType WAIT_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.WAIT_STEP).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_IMPORT_SET_STEP_TYPE = StepType.newBuilder()
                                                  .setType(StepSpecTypeConstants.SERVICENOW_IMPORT_SET)
                                                  .setStepCategory(StepCategory.STEP)
                                                  .build();

  StepType INIT_CONTAINER_STEP_TYPE = StepType.newBuilder()
                                          .setType(StepSpecTypeConstants.INIT_CONTAINER_STEP)
                                          .setStepCategory(StepCategory.STEP)
                                          .build();

  StepType RUN_CONTAINER_STEP_TYPE = StepType.newBuilder()
                                         .setType(StepSpecTypeConstants.RUN_CONTAINER_STEP)
                                         .setStepCategory(StepCategory.STEP)
                                         .build();

  StepType INIT_CONTAINER_V2_STEP_TYPE = StepType.newBuilder()
                                             .setType(StepSpecTypeConstants.INIT_CONTAINER_STEP_V2)
                                             .setStepCategory(StepCategory.STEP)
                                             .build();
}
