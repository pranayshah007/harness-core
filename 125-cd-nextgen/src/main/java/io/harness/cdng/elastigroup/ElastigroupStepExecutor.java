/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
public interface ElastigroupStepExecutor {
  TaskChainResponse executeElastigroupTask(Ambiance ambiance, StepElementParameters stepParameters,
                                           ElastigroupExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
                                           EcsStepExecutorParams ecsStepExecutorParams);
  TaskChainResponse executeEcsPrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters,
      EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData, UnitProgressData unitProgressData);
}
