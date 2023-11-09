/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs.asyncsteps;

import io.harness.cdng.ecs.EcsUpgradeContainerStep;
import io.harness.cdng.executables.CdAsyncExecutable;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

public class EcsUpgradeContainerStepV2 extends CdAsyncExecutable<EcsCommandResponse, EcsUpgradeContainerStep> {
    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.ECS_UPGRADE_CONTAINER_V2.getName())
            .setStepCategory(StepCategory.STEP)
            .build();
}