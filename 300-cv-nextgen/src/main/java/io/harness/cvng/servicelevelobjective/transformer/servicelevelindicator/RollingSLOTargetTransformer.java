package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RollingSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.RollingSLOTarget;

public class RollingSLOTargetTransformer implements SLOTargetTransformer<RollingSLOTarget, RollingSLOTargetSpec> {
  @Override
  public RollingSLOTarget getSLOTarget(RollingSLOTargetSpec spec) {
    String periodLength = spec.getPeriodLength(); // 28d
    return RollingSLOTarget.builder()
        .periodLengthDays(Integer.parseInt(periodLength.substring(0, periodLength.length() - 1)))
        .build();
  }

  @Override
  public RollingSLOTargetSpec getSLOTargetSpec(RollingSLOTarget entity) {
    return RollingSLOTargetSpec.builder().periodLength(entity.getPeriodLengthDays() + "d").build();
  }
}
