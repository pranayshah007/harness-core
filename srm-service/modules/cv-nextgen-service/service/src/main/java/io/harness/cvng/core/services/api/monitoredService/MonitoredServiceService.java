/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api.monitoredService;

import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.HealthMonitoringFlagResponse;
import io.harness.cvng.core.beans.monitoredService.AnomaliesSummaryDTO;
import io.harness.cvng.core.beans.monitoredService.CountServiceDTO;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.monitoredService.HealthScoreDTO;
import io.harness.cvng.core.beans.monitoredService.HistoricalTrend;
import io.harness.cvng.core.beans.monitoredService.MetricDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceChangeDetailSLO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceListItemDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceWithHealthSources;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.logsFilterParams.LiveMonitoringLogsFilter;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.cvng.usage.impl.ActiveServiceMonitoredDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.NonNull;

public interface MonitoredServiceService extends DeleteEntityByHandler<MonitoredService> {
  MonitoredServiceResponse create(String accountId, MonitoredServiceDTO monitoredServiceDTO);
  MonitoredServiceResponse createFromYaml(ProjectParams projectParams, String yaml);
  MonitoredServiceResponse updateFromYaml(ProjectParams projectParams, String identifier, String yaml);
  MonitoredServiceResponse update(String accountId, MonitoredServiceDTO monitoredServiceDTO);
  boolean delete(ProjectParams projectParams, String identifier);
  List<MonitoredServiceResponse> get(ProjectParams projectParams, Set<String> identifier);

  List<MonitoredServiceDetail> getMonitoredServiceDetails(ProjectParams projectParams, Set<String> identifier);

  List<MonitoredServiceDetail> getAllMonitoredServiceDetails(ProjectParams projectParams);
  List<MonitoredServiceResponse> get(String accountId, Set<String> identifier);
  MonitoredServiceResponse get(ProjectParams projectParams, String identifier);
  MonitoredServiceResponse getApplicationMonitoredServiceResponse(ServiceEnvironmentParams serviceEnvironmentParams);
  PageResponse<MonitoredServiceResponse> getList(ProjectParams projectParams, List<String> environmentIdentifiers,
      Integer offset, Integer pageSize, String filter);
  List<MonitoredServiceWithHealthSources> getAllWithTimeSeriesHealthSources(ProjectParams projectParams);
  MonitoredServiceDTO getApplicationMonitoredServiceDTO(ServiceEnvironmentParams serviceEnvironmentParams);
  MonitoredServiceDTO getMonitoredServiceDTO(MonitoredServiceParams monitoredServiceParams);
  // use with MonitoredServiceParams instead
  @Deprecated MonitoredService getMonitoredService(ProjectParams projectParams, String identifier);
  MonitoredService getMonitoredService(MonitoredServiceParams monitoredServiceParams);
  MonitoredServiceDTO getExpandedMonitoredServiceFromYaml(ProjectParams projectParams, String yaml);
  Optional<MonitoredService> getApplicationMonitoredService(ServiceEnvironmentParams serviceEnvironmentParams);

  List<MonitoredService> list(
      @NonNull ProjectParams projectParams, @Nullable String serviceIdentifier, @Nullable String environmentIdentifier);

  List<MonitoredService> list(@NonNull ProjectParams projectParams, List<String> identifiers);

  List<MonitoredService> listWithFilter(@NonNull ProjectParams projectParams, List<String> identifiers, String filter);

  PageResponse<MonitoredServiceListItemDTO> list(ProjectParams projectParams, List<String> environmentIdentifiers,
      Integer offset, Integer pageSize, String filter, boolean servicesAtRiskFilter);

  List<String> listConnectorRefs(MonitoredServiceDTO monitoredServiceDTO);

  List<EnvironmentResponse> listEnvironments(String accountId, String orgIdentifier, String projectIdentifier);
  MonitoredServiceResponse createDefault(
      ProjectParams projectParams, String serviceIdentifier, String environmentIdentifier);
  HealthMonitoringFlagResponse setHealthMonitoringFlag(ProjectParams projectParams, String identifier, boolean enable);

  HistoricalTrend getOverAllHealthScore(
      ProjectParams projectParams, String identifier, DurationDTO duration, Instant endTime);

  HealthScoreDTO getCurrentAndDependentServicesScore(MonitoredServiceParams monitoredServiceParams);

  String getYamlTemplate(ProjectParams projectParams, MonitoredServiceType type);

  List<HealthSourceDTO> getHealthSources(ProjectParams projectParams, String monitoredServiceIdentifier);
  /**
   * use #getHealthSources with monitored service identifier instead
   */
  @Deprecated List<HealthSourceDTO> getHealthSources(ServiceEnvironmentParams serviceEnvironmentParams);

  AnomaliesSummaryDTO getAnomaliesSummary(
      ProjectParams projectParams, String monitoredServiceIdentifier, TimeRangeParams timeRangeParams);
  CountServiceDTO getCountOfServices(ProjectParams projectParams, String environmentIdentifier, String filter);

  List<MetricDTO> getSloMetrics(
      ProjectParams projectParams, String monitoredServiceIdentifier, String healthSourceIdentifier);

  MonitoredServiceListItemDTO getMonitoredServiceDetails(MonitoredServiceParams monitoredServiceParams);

  MonitoredServiceListItemDTO getMonitoredServiceDetails(ServiceEnvironmentParams serviceEnvironmentParams);

  List<String> getMonitoredServiceIdentifiers(
      ProjectParams projectParams, List<String> services, List<String> environments);
  PageResponse<CVNGLogDTO> getCVNGLogs(MonitoredServiceParams monitoredServiceParams,
      LiveMonitoringLogsFilter liveMonitoringLogsFilter, PageParams pageParams);

  List<MonitoredServiceChangeDetailSLO> getMonitoredServiceChangeDetails(
      ProjectParams projectParams, String monitoredServiceIdentifier, Long startTime, Long endTime);
  void handleNotification(MonitoredService monitoredService);
  PageResponse<NotificationRuleResponse> getNotificationRules(
      ProjectParams projectParams, String monitoredServiceIdentifier, PageParams pageParams);
  void beforeNotificationRuleDelete(ProjectParams projectParams, String notificationRuleRef);
  long countUniqueEnabledServices(String accountId);

  List<ActiveServiceMonitoredDTO> listActiveServiceMonitored(ProjectParams projectParams);
}
