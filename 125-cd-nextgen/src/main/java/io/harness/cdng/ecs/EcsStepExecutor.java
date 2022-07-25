package io.harness.cdng.ecs;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
public interface EcsStepExecutor {
  TaskChainResponse executeEcsTask(Ambiance ambiance,
                                   StepElementParameters stepParameters,
                                   EcsExecutionPassThroughData executionPassThroughData,
                                   UnitProgressData unitProgressData, EcsStepExecutorParams ecsStepExecutorParams);
}
