/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.pms.contracts.steps.StepType;

public class RunTestsStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = RunTestsStepInfo.STEP_TYPE;
}
