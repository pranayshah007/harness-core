/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container;

import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
public class ContainerStepSpecTypeConstants {
  public static final String CONTAINER_STEP = "Container";
  public static final StepType CONTAINER_STEP_TYPE =
      StepType.newBuilder().setType(CONTAINER_STEP).setStepCategory(StepCategory.STEP).build();
}
