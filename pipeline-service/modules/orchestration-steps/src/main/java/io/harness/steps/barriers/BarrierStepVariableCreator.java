/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.barriers;

import io.harness.plancreator.steps.barrier.BarrierStepNode;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Set;

public class BarrierStepVariableCreator extends GenericStepVariableCreator<BarrierStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.BARRIER);
  }

  @Override
  public Class<BarrierStepNode> getFieldClass() {
    return BarrierStepNode.class;
  }
}
