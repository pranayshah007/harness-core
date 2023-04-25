/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;

import java.util.List;

public interface SLOHealthIndicatorService {
  List<SLOHealthIndicator> getByMonitoredServiceIdentifiers(
      ProjectParams projectParams, List<String> monitoredServiceIdentifiers);
  SLOHealthIndicator getBySLOIdentifier(ProjectParams projectParams, String serviceLevelObjectiveIdentifier);
  SLOHealthIndicator getBySLOEntity(AbstractServiceLevelObjective serviceLevelObjective);

  List<SLOHealthIndicator> getBySLOIdentifiers(
      ProjectParams projectParams, List<String> serviceLevelObjectiveIdentifiers);
  List<SLOHealthIndicator> getBySLOIdentifiers(String accountId, List<String> serviceLevelObjectiveIdentifiers);
  void upsert(AbstractServiceLevelObjective serviceLevelObjective);
  void delete(ProjectParams projectParams, String serviceLevelObjectiveIdentifier);
  SLOGraphData getGraphData(ProjectParams projectParams, AbstractServiceLevelObjective serviceLevelObjective);

  boolean getFailedState(ProjectParams projectParams, AbstractServiceLevelObjective serviceLevelObjective);
}
