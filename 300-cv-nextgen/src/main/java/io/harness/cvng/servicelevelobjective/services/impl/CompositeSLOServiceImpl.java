package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLOService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CompositeSLOServiceImpl implements CompositeSLOService {
  @Inject HPersistence hPersistence;

  @Override
  public boolean isReferencedInCompositeSLO(ProjectParams projectParams, String simpleServiceLevelObjectiveIdentifier) {
    return !getReferencedCompositeSLOs(projectParams, simpleServiceLevelObjectiveIdentifier).isEmpty();
  }

  @Override
  public List<CompositeServiceLevelObjective> getReferencedCompositeSLOs(
      ProjectParams projectParams, String simpleServiceLevelObjectiveIdentifier) {
    List<AbstractServiceLevelObjective> compositeServiceLevelObjectives =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.type, ServiceLevelObjectiveType.COMPOSITE)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.accountId,
                projectParams.getAccountIdentifier())
            .asList();
    Set<CompositeServiceLevelObjective> referencedCompositeSLOs = new HashSet<>();
    for (AbstractServiceLevelObjective serviceLevelObjective : compositeServiceLevelObjectives) {
      CompositeServiceLevelObjective compositeServiceLevelObjective =
          (CompositeServiceLevelObjective) serviceLevelObjective;
      for (CompositeServiceLevelObjective.ServiceLevelObjectivesDetail serviceLevelObjectivesDetail :
          compositeServiceLevelObjective.getServiceLevelObjectivesDetails()) {
        if (serviceLevelObjectivesDetail.getServiceLevelObjectiveRef().equals(simpleServiceLevelObjectiveIdentifier)
            && serviceLevelObjectivesDetail.getOrgIdentifier().equals(projectParams.getOrgIdentifier())
            && serviceLevelObjectivesDetail.getProjectIdentifier().equals(projectParams.getProjectIdentifier())) {
          referencedCompositeSLOs.add(compositeServiceLevelObjective);
        }
      }
    }
    return referencedCompositeSLOs.stream().collect(Collectors.toList());
  }
}
