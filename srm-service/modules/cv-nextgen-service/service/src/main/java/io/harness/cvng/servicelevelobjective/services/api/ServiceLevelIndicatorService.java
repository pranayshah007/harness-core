/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.sli.MetricOnboardingGraph;
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;

import java.time.Instant;
import java.util.List;

public interface ServiceLevelIndicatorService {
  List<String> create(ProjectParams projectParams, List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList,
      String serviceLevelObjectiveIdentifier, String monitoredServiceIndicator, String healthSourceIndicator);

  SLIOnboardingGraphs getOnboardingGraphs(ProjectParams projectParams, String monitoredServiceIdentifier,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String tracingId);

  MetricOnboardingGraph getMetricGraphs(ProjectParams projectParams, String monitoredServiceIdentifier,
      String healthSourceRef, RatioSLIMetricEventType ratioSLIMetricEventType, List<String> metricIdentifiers,
      String tracingId);
  List<ServiceLevelIndicatorDTO> get(ProjectParams projectParams, List<String> serviceLevelIndicators);

  List<ServiceLevelIndicator> getEntities(ProjectParams projectParams, List<String> serviceLevelIndicators);

  List<String> update(ProjectParams projectParams, List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList,
      String serviceLevelObjectiveIdentifier, List<String> serviceLevelIndicatorsList, String monitoredServiceIndicator,
      String healthSourceIndicator, TimePeriod timePeriod, TimePeriod currentTimePeriod);

  void deleteByIdentifier(ProjectParams projectParams, List<String> serviceLevelIndicatorIdentifier);

  ServiceLevelIndicator get(String sliId);

  List<CVConfig> fetchCVConfigForSLI(ServiceLevelIndicator serviceLevelIndicator);

  List<CVConfig> fetchCVConfigForSLI(String sliId);

  ServiceLevelIndicator getServiceLevelIndicator(ProjectParams projectParams, String identifier);

  List<String> getSLIsWithMetrics(ProjectParams projectParams, String monitoredServiceIdentifier,
      String healthSourceIdentifier, List<String> metricIdentifiers);

  List<String> getSLIs(ProjectParams projectParams, String monitoredServiceIdentifier);

  void setMonitoredServiceSLIsEnableFlag(
      ProjectParams projectParams, String monitoredServiceIdentifier, boolean isEnabled);

  void enqueueDataCollectionFailureInstanceAndTriggerAnalysis(
      String verificationTaskId, Instant startTime, Instant endTime, ServiceLevelIndicator serviceLevelIndicator);

  String getScopedIdentifier(ServiceLevelIndicator serviceLevelIndicator);
}
