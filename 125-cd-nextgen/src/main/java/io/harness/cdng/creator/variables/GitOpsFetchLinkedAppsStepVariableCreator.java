/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import io.harness.cdng.gitops.beans.FetchLinkedAppsStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

public class GitOpsFetchLinkedAppsStepVariableCreator extends GenericStepVariableCreator<FetchLinkedAppsStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS);
  }

  @Override
  public Class<FetchLinkedAppsStepNode> getFieldClass() {
    return FetchLinkedAppsStepNode.class;
  }
}
