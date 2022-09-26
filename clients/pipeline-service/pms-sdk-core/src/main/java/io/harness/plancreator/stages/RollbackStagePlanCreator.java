package io.harness.plancreator.stages;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class RollbackStagePlanCreator {
  public PlanCreationResponse createPlanForRollbackStage(YamlField stageYamlField) {
    // todo: implement properly
    return PlanCreationResponse.builder().build();
  }
}
