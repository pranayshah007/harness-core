/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

@OwnedBy(PIPELINE)
@TargetModule(HarnessModule._860_ORCHESTRATION_STEPS)
public interface StepSpecTypeConstants {
  String SHELL_SCRIPT = "ShellScript";
  String BARRIER = "Barrier";
  String HTTP = "Http";
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
  String APPROVAL_STAGE = "Approval";
  String CUSTOM_STAGE = "Custom";
  String DEPLOYMENT_STAGE = "Deployment";
  String CI_STAGE = "CI";
  String SECURITY_STAGE = "SecurityTests";
  String FEATURE_FLAG_STAGE = "FeatureFlag";
  String POLICY_STEP = "Policy";
}
