/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepVariableCreator extends GenericStepVariableCreator<WaitStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.WAIT_STEP);
  }
  @Override
  public Class<WaitStepNode> getFieldClass() {
    return WaitStepNode.class;
  }
}
