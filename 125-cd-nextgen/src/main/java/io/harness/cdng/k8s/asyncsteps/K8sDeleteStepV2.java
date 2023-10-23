/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.asyncsteps;

import io.harness.cdng.executables.CdAsyncChainExecutable;
import io.harness.cdng.k8s.K8sDeleteStep;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

public class K8sDeleteStepV2 extends CdAsyncChainExecutable<K8sDeleteStep> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_DELETE_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
}
