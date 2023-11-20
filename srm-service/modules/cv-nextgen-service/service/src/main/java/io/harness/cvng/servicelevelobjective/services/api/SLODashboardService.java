/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.EnvironmentIdentifierResponse;
import io.harness.cvng.servicelevelobjective.beans.MSDropdownResponse;
import io.harness.cvng.servicelevelobjective.beans.SLOConsumptionBreakdown;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventDetailsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType;
import io.harness.ng.beans.PageResponse;
import io.harness.spec.server.cvng.v1.model.MetricGraph;

import java.util.List;
import java.util.Map;

public interface SLODashboardService {
  PageResponse<SLOHealthListView> getSloHealthListView(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams);
  PageResponse<SLOConsumptionBreakdown> getSLOConsumptionBreakdownView(
      ProjectParams projectParams, String compositeSLOIdentifier, Long startTime, Long endTime);

  SLODashboardDetail getSloDashboardDetail(
      ProjectParams projectParams, String sloIdentifier, Long startTime, Long endTime);

  Map<String, MetricGraph> getMetricGraphs(
      ProjectParams projectParams, String sloIdentifier, Long startTime, Long endTime);

  SLORiskCountResponse getRiskCount(ProjectParams projectParams, SLODashboardApiFilter serviceLevelObjectiveFilter);
  PageResponse<MSDropdownResponse> getSLOAssociatedMonitoredServices(
      ProjectParams projectParams, PageParams pageParams);

  PageResponse<EnvironmentIdentifierResponse> getSLOAssociatedEnvironmentIdentifiers(
      ProjectParams projectParams, PageParams pageParams);

  List<SecondaryEventsResponse> getSecondaryEvents(
      ProjectParams projectParams, long startTime, long endTime, String identifier);
  SecondaryEventDetailsResponse getSecondaryEventDetails(SecondaryEventsType eventType, List<String> uuids);
}
