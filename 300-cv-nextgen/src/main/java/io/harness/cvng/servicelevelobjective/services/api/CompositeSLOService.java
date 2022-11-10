package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;

import java.util.List;

public interface CompositeSLOService {
  boolean isReferencedInCompositeSLO(ProjectParams projectParams, String simpleServiceLevelObjectiveIdentifier);
  List<CompositeServiceLevelObjective> getReferencedCompositeSLOs(
      ProjectParams projectParams, String simpleServiceLevelObjectiveIdentifier);
}
