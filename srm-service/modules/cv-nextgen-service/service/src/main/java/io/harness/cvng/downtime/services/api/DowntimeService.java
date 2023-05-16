/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.services.api;

import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeDashboardFilter;
import io.harness.cvng.downtime.beans.DowntimeHistoryView;
import io.harness.cvng.downtime.beans.DowntimeListView;
import io.harness.cvng.downtime.beans.DowntimeResponse;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.servicelevelobjective.beans.MSDropdownResponse;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.ng.beans.PageResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DowntimeService extends DeleteEntityByHandler<Downtime> {
  DowntimeResponse create(ProjectParams projectParams, DowntimeDTO downtimeDTO);

  DowntimeResponse get(ProjectParams projectParams, String identifier);

  List<MonitoredServiceDetail> getAssociatedMonitoredServices(ProjectParams projectParams, String identifier);

  PageResponse<MSDropdownResponse> getDowntimeAssociatedMonitoredServices(
      ProjectParams projectParams, PageParams pageParams);

  Map<String, EntityUnavailabilityStatusesDTO> getMonitoredServicesAssociatedUnavailabilityInstanceMap(
      ProjectParams projectParams, Set<String> msIdentifiers);

  Downtime getEntity(ProjectParams projectParams, String identifier);
  DowntimeResponse update(ProjectParams projectParams, String identifier, DowntimeDTO downtimeDTO);

  DowntimeResponse enableOrDisable(ProjectParams projectParams, String identifier, boolean enable);
  boolean delete(ProjectParams projectParams, String identifier);

  PageResponse<DowntimeListView> list(
      ProjectParams projectParams, PageParams pageParams, DowntimeDashboardFilter filter);

  PageResponse<DowntimeHistoryView> history(
      ProjectParams projectParams, PageParams pageParams, DowntimeDashboardFilter filter);

  List<EntityUnavailabilityStatusesDTO> filterDowntimeInstancesOnMonitoredService(ProjectParams projectParams,
      List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS, String monitoredServiceIdentifier);

  List<EntityUnavailabilityStatusesDTO> filterDowntimeInstancesOnMonitoredServices(ProjectParams projectParams,
      List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS, Set<String> monitoredServiceIdentifier);

  List<EntityUnavailabilityStatuses> filterDowntimeInstancesOnMSs(ProjectParams projectParams,
      List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses, Set<String> monitoredServiceIdentifier);
}
