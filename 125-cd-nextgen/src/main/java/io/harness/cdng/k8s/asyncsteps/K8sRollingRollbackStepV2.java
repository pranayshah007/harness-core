/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s.asyncsteps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.executables.CdAsyncExecutable;
import io.harness.cdng.k8s.K8sRollingRollbackStep;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

@OwnedBy(CDP)
public class K8sRollingRollbackStepV2 extends CdAsyncExecutable<K8sDeployResponse, K8sRollingRollbackStep> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_ROLLBACK_ROLLING_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
}
