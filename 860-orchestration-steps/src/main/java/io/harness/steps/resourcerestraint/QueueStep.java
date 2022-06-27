/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.steps.StepSpecTypeConstants;

/**
 * A dedicated step to represent a queue, even the queue be a kind of resource constraint.
 *
 * We decide create this one to avoid any side effect from changing the original resource restraint step type. While
 * {@link ResourceRestraintStep} is used for internal stuffs, the queue step is exposed to the customer.
 */
@OwnedBy(PIPELINE)
public class QueueStep extends ResourceRestraintStep {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.QUEUE).setStepCategory(StepCategory.STEP).build();
}
