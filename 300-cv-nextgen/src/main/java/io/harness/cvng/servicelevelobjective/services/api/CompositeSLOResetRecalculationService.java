package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;

public interface CompositeSLOResetRecalculationService {
  void reset(CompositeServiceLevelObjective compositeServiceLevelObjective);
  void recalculate(CompositeServiceLevelObjective compositeServiceLevelObjective);
}
