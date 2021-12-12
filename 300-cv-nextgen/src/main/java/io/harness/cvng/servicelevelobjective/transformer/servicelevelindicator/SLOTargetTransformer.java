package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.servicelevelobjective.beans.slotargetspec.SLOTargetSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;

public interface SLOTargetTransformer<E extends ServiceLevelObjective.SLOTarget, T extends SLOTargetSpec> {
  E getSLOTarget(T spec);
  T getSLOTargetSpec(E entity);
}
