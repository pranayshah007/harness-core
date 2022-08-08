/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.servicenow.create.ServiceNowCreateStepNode;

import java.util.HashSet;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ServiceNowCreateStepVariableCreator extends GenericStepVariableCreator<ServiceNowCreateStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    Set<String> strings = new HashSet<>();
    strings.add(StepSpecTypeConstants.SERVICENOW_CREATE);
    return strings;
  }

  @Override
  public Class<ServiceNowCreateStepNode> getFieldClass() {
    return ServiceNowCreateStepNode.class;
  }
}
