/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.distribution.barrier.Barrier;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.service.BarrierService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierInitializer implements OrchestrationStartObserver {
  @Inject private BarrierService barrierService;

  @Override
  public void onStart(OrchestrationStartInfo orchestrationStartInfo) {
    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            orchestrationStartInfo.getAmbiance(), FeatureName.CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES.name())) {
      return;
    }
    String version = AmbianceUtils.getPipelineVersion(orchestrationStartInfo.getAmbiance());
    String planExecutionId = orchestrationStartInfo.getPlanExecutionId();
    PlanExecutionMetadata planExecutionMetadata = orchestrationStartInfo.getPlanExecutionMetadata();
    try {
      switch (version) {
        case HarnessYamlVersion.V1:
          // TODO: Barrier support
          break;
        case HarnessYamlVersion.V0:
          Map<String, BarrierSetupInfo> barrierIdentifierSetupInfoMap =
              barrierService.getBarrierSetupInfoList(planExecutionMetadata.getProcessedYaml())
                  .stream()
                  .collect(Collectors.toMap(BarrierSetupInfo::getIdentifier, Function.identity()));

          Map<String, List<BarrierPositionInfo.BarrierPosition>> barrierPositionInfoMap =
              barrierService.getBarrierPositionInfoList(planExecutionMetadata.getProcessedYaml());

          List<BarrierExecutionInstance> barriers =
              barrierPositionInfoMap.entrySet()
                  .stream()
                  .filter(entry -> !entry.getValue().isEmpty())
                  .map(entry
                      -> BarrierExecutionInstance.builder()
                             .uuid(generateUuid())
                             .setupInfo(barrierIdentifierSetupInfoMap.get(entry.getKey()))
                             .positionInfo(BarrierPositionInfo.builder()
                                               .planExecutionId(planExecutionId)
                                               .barrierPositionList(entry.getValue())
                                               .build())
                             .name(barrierIdentifierSetupInfoMap.get(entry.getKey()).getName())
                             .barrierState(Barrier.State.STANDING)
                             .identifier(entry.getKey())
                             .planExecutionId(planExecutionId)
                             .build())
                  .collect(Collectors.toList());

          barrierService.saveAll(barriers);
          break;
        default:
          throw new IllegalStateException("version not supported");
      }
    } catch (Exception e) {
      log.error("Barrier initialization failed for planExecutionId: [{}]", planExecutionId);
      throw e;
    }
  }
}
