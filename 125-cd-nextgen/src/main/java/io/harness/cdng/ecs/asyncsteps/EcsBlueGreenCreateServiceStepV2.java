/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs.asyncsteps;

import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStep;
import io.harness.cdng.executables.CdAsyncChainExecutable;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

public class EcsBlueGreenCreateServiceStepV2 extends CdAsyncChainExecutable<EcsBlueGreenCreateServiceStep> {
    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.ECS_BLUE_GREEN_CREATE_SERVICE_V2.getName())
            .setStepCategory(StepCategory.STEP)
            .build();
}
