package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;

public interface CompositeSLOResetService {
  void reset(CompositeServiceLevelObjective compositeServiceLevelObjective);
}
