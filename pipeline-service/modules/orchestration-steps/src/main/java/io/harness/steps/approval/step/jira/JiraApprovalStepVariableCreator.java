/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.steps.StepSpecTypeConstants;

import java.util.HashSet;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class JiraApprovalStepVariableCreator extends GenericStepVariableCreator<JiraApprovalStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    Set<String> strings = new HashSet<>();
    strings.add(StepSpecTypeConstants.JIRA_APPROVAL);
    return strings;
  }

  @Override
  public Class<JiraApprovalStepNode> getFieldClass() {
    return JiraApprovalStepNode.class;
  }
}
