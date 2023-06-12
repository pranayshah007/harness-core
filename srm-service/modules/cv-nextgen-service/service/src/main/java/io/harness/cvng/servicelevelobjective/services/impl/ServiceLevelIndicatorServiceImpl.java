/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SyncDataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.TimeGraphResponse;
import io.harness.cvng.core.beans.TimeGraphResponse.DataPoints;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.sidekick.VerificationTaskCleanupSideKickData;
import io.harness.cvng.core.beans.sli.MetricOnboardingGraph;
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs;
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs.MetricGraph;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIValue;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLOService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.cvng.utils.ScopedInformation;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.exception.InvalidArgumentsException;
import io.harness.persistence.HPersistence;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import dev.morphia.query.Criteria;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ServiceLevelIndicatorServiceImpl implements ServiceLevelIndicatorService {
  private static final int INTERVAL_HOURS = 12;
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelIndicatorEntityAndDTOTransformer serviceLevelIndicatorEntityAndDTOTransformer;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private HealthSourceService healthSourceService;
  @Inject private OnboardingService onboardingService;
  @Inject private MetricPackService metricPackService;
  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;
  @Inject private SLIDataProcessorService sliDataProcessorService;
  @Inject private Clock clock;
  @Inject private OrchestrationService orchestrationService;
  @Inject private SideKickService sideKickService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private CompositeSLOService compositeSLOService;

  @Inject private SLIRecordService sliRecordService;

  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;
  @Override
  public SLIOnboardingGraphs getOnboardingGraphs(ProjectParams projectParams, String monitoredServiceIdentifier,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String tracingId) {
    List<CVConfig> cvConfigs =
        getCvConfigs(projectParams, monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef());
    CVConfig baseCVConfig = cvConfigs.get(0);

    MonitoredService monitoredService =
        monitoredServiceService.getMonitoredService(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                        .build());

    ServiceLevelIndicator serviceLevelIndicator = convertDTOToEntity(projectParams, serviceLevelIndicatorDTO,
        monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef(), monitoredService.isEnabled());

    DataCollectionInfo dataCollectionInfo = dataSourceTypeDataCollectionInfoMapperMap.get(baseCVConfig.getType())
                                                .toDataCollectionInfo(cvConfigs, serviceLevelIndicator);

    Instant endTime = clock.instant().truncatedTo(ChronoUnit.MINUTES);
    Instant startTime = endTime.minus(Duration.ofDays(1));

    List<TimeSeriesRecord> timeSeriesRecords =
        getTimeSeriesRecords(projectParams, baseCVConfig, startTime, endTime, dataCollectionInfo, tracingId);

    Map<String, List<SLIAnalyseRequest>> sliAnalyseRequest =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getMetricIdentifier,
            Collectors.mapping(timeSeriesRecord
                -> SLIAnalyseRequest.builder()
                       .metricValue(timeSeriesRecord.getMetricValue())
                       .timeStamp(Instant.ofEpochMilli(timeSeriesRecord.getTimestamp()))
                       .build(),
                Collectors.toList())));
    List<SLIAnalyseResponse> sliAnalyseResponses =
        sliDataProcessorService.process(sliAnalyseRequest, serviceLevelIndicatorDTO, startTime, endTime);
    sliAnalyseResponses.sort(Comparator.comparing(SLIAnalyseResponse::getTimeStamp));
    final SLIAnalyseResponse initialSLIResponse = sliAnalyseResponses.get(0);

    TimeGraphResponse sliGraph =
        TimeGraphResponse.builder()
            .startTime(startTime.toEpochMilli())
            .endTime(endTime.toEpochMilli())
            .dataPoints(
                sliAnalyseResponses.stream()
                    .map(sliAnalyseResponse
                        -> DataPoints.builder()
                               .timeStamp(sliAnalyseResponse.getTimeStamp().toEpochMilli())
                               .value(getSLIValue(serviceLevelIndicatorDTO, sliAnalyseResponse, initialSLIResponse)
                                          .sliPercentage())
                               .build())
                    .collect(Collectors.toList()))
            .build();

    return SLIOnboardingGraphs.builder()
        .metricGraphs(getMetricGraphs(timeSeriesRecords, serviceLevelIndicator.getMetricNames(), startTime, endTime))
        .sliGraph(sliGraph)
        .build();
  }

  @Override
  public MetricOnboardingGraph getMetricGraphs(ProjectParams projectParams, String monitoredServiceIdentifier,
      String healthSourceRef, RatioSLIMetricEventType ratioSLIMetricEventType, List<String> metricIdentifiers,
      String tracingId) {
    List<CVConfig> cvConfigs = getCvConfigs(projectParams, monitoredServiceIdentifier, healthSourceRef);
    CVConfig baseCVConfig = cvConfigs.get(0);
    DataCollectionInfo dataCollectionInfo = dataSourceTypeDataCollectionInfoMapperMap.get(baseCVConfig.getType())
                                                .toDataCollectionInfo(cvConfigs, metricIdentifiers);

    Instant endTime = clock.instant().truncatedTo(ChronoUnit.MINUTES);
    Instant startTime = endTime.minus(Duration.ofDays(1));

    List<TimeSeriesRecord> timeSeriesRecords =
        getTimeSeriesRecords(projectParams, baseCVConfig, startTime, endTime, dataCollectionInfo, tracingId);
    return MetricOnboardingGraph.builder()
        .metricGraphs(getMetricGraphs(timeSeriesRecords, metricIdentifiers, startTime, endTime))
        .metricPercentageGraph(
            getMetricPercentageGraph(timeSeriesRecords, metricIdentifiers, startTime, endTime, ratioSLIMetricEventType))
        .build();
  }

  private SLIValue getSLIValue(ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, SLIAnalyseResponse sliAnalyseResponse,
      SLIAnalyseResponse initialSLIResponse) {
    if (serviceLevelIndicatorDTO.getType() == SLIEvaluationType.WINDOW) {
      return serviceLevelIndicatorDTO.getSLIMissingDataType().calculateSLIValue(
          sliAnalyseResponse.getRunningGoodCount(), sliAnalyseResponse.getRunningBadCount(),
          Duration.between(initialSLIResponse.getTimeStamp(), sliAnalyseResponse.getTimeStamp()).toMinutes() + 1);
    } else if (serviceLevelIndicatorDTO.getType() == SLIEvaluationType.REQUEST) {
      return SLIValue.builder()
          .goodCount(sliAnalyseResponse.getRunningGoodCount())
          .badCount(sliAnalyseResponse.getRunningBadCount())
          .total(sliAnalyseResponse.getRunningGoodCount() + sliAnalyseResponse.getRunningBadCount())
          .build();
    } else {
      throw new InvalidArgumentsException(
          String.format("Invalid Service Level Indicator Type %s", serviceLevelIndicatorDTO.getType()));
    }
  }

  private List<CVConfig> getCvConfigs(
      ProjectParams projectParams, String monitoredServiceIdentifier, String healthSourceRef) {
    List<CVConfig> cvConfigs =
        healthSourceService
            .getCVConfigs(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                projectParams.getProjectIdentifier(), monitoredServiceIdentifier, healthSourceRef)
            .stream()
            .filter(MetricCVConfig.class ::isInstance)
            .map(MetricCVConfig.class ::cast)
            .peek(metricCVConfig
                -> metricPackService.populateDataCollectionDsl(
                    metricCVConfig.getType(), metricCVConfig.getMetricPack()))
            .collect(Collectors.toList());
    if (cvConfigs.isEmpty()) {
      throw new InvalidArgumentsException("Health source not present");
    }
    return cvConfigs;
  }

  private List<TimeSeriesRecord> getTimeSeriesRecords(ProjectParams projectParams, CVConfig baseCVConfig,
      Instant startTime, Instant endTime, DataCollectionInfo dataCollectionInfo, String tracingId) {
    DataCollectionRequest request = SyncDataCollectionRequest.builder()
                                        .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
                                        .dataCollectionInfo(dataCollectionInfo)
                                        .endTime(endTime)
                                        .startTime(startTime)
                                        .build();
    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(baseCVConfig.getConnectorIdentifier())
                                                    .accountId(projectParams.getAccountIdentifier())
                                                    .orgIdentifier(projectParams.getOrgIdentifier())
                                                    .projectIdentifier(projectParams.getProjectIdentifier())
                                                    .tracingId(tracingId)
                                                    .build();

    OnboardingResponseDTO response =
        onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
    return JsonUtils.asList(JsonUtils.asJson(response.getResult()), new TypeReference<>() {});
  }
  @Override
  public List<String> create(ProjectParams projectParams, List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList,
      String serviceLevelObjectiveIdentifier, String monitoredServiceIndicator, String healthSourceIndicator) {
    List<String> serviceLevelIndicatorIdentifiers = new ArrayList<>();
    for (ServiceLevelIndicatorDTO serviceLevelIndicatorDTO : serviceLevelIndicatorDTOList) {
      if (Objects.isNull(serviceLevelIndicatorDTO.getName())
          && Objects.isNull(serviceLevelIndicatorDTO.getIdentifier())) {
        generateNameAndIdentifier(serviceLevelObjectiveIdentifier, serviceLevelIndicatorDTO);
      }
      MonitoredService monitoredService =
          monitoredServiceService.getMonitoredService(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                          .monitoredServiceIdentifier(monitoredServiceIndicator)
                                                          .build());
      saveServiceLevelIndicatorEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator,
          healthSourceIndicator, monitoredService.isEnabled());
      serviceLevelIndicatorIdentifiers.add(serviceLevelIndicatorDTO.getIdentifier());
    }
    return serviceLevelIndicatorIdentifiers;
  }

  @Nullable
  @Override
  public ServiceLevelIndicator get(@NotNull String sliId) {
    return hPersistence.get(ServiceLevelIndicator.class, sliId);
  }

  private void generateNameAndIdentifier(
      String serviceLevelObjectiveIdentifier, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO) {
    serviceLevelIndicatorDTO.getSpec().generateNameAndIdentifier(
        serviceLevelObjectiveIdentifier, serviceLevelIndicatorDTO);
  }

  @Override
  public List<ServiceLevelIndicatorDTO> get(ProjectParams projectParams, List<String> serviceLevelIndicators) {
    List<ServiceLevelIndicator> serviceLevelIndicatorList = getEntities(projectParams, serviceLevelIndicators);
    return serviceLevelIndicatorList.stream().map(this::convertEntityToDTO).collect(Collectors.toList());
  }

  @Override
  public List<ServiceLevelIndicator> getEntities(ProjectParams projectParams, List<String> serviceLevelIndicators) {
    return hPersistence.createQuery(ServiceLevelIndicator.class)
        .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(ServiceLevelIndicatorKeys.identifier)
        .in(serviceLevelIndicators)
        .asList();
  }

  @Override
  public List<String> update(ProjectParams projectParams, List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList,
      String serviceLevelObjectiveIdentifier, List<String> serviceLevelIndicatorsList, String monitoredServiceIndicator,
      String healthSourceIndicator, TimePeriod timePeriod, TimePeriod currentTimePeriod) {
    List<String> serviceLevelIndicatorIdentifiers = new ArrayList<>();
    MonitoredService monitoredService =
        monitoredServiceService.getMonitoredService(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                        .monitoredServiceIdentifier(monitoredServiceIndicator)
                                                        .build());
    for (ServiceLevelIndicatorDTO serviceLevelIndicatorDTO : serviceLevelIndicatorDTOList) {
      if (Objects.isNull(serviceLevelIndicatorDTO.getName())
          && Objects.isNull(serviceLevelIndicatorDTO.getIdentifier())) {
        generateNameAndIdentifier(serviceLevelObjectiveIdentifier, serviceLevelIndicatorDTO);
      }
      ServiceLevelIndicator serviceLevelIndicator =
          getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());
      ServiceLevelIndicator newServiceLevelIndicator = convertDTOToEntity(projectParams, serviceLevelIndicatorDTO,
          monitoredServiceIndicator, healthSourceIndicator, monitoredService.isEnabled());
      if (Objects.isNull(serviceLevelIndicator)) {
        saveServiceLevelIndicatorEntity(newServiceLevelIndicator);
      } else if (!serviceLevelIndicator.isUpdatable(convertDTOToEntity(projectParams, serviceLevelIndicatorDTO,
                     monitoredServiceIndicator, healthSourceIndicator, serviceLevelIndicator.isEnabled()))) {
        deleteAndCreate(projectParams, newServiceLevelIndicator);
        List<CompositeServiceLevelObjective> referencedCompositeSLOs =
            compositeSLOService.getReferencedCompositeSLOs(projectParams, serviceLevelObjectiveIdentifier);
        for (CompositeServiceLevelObjective compositeServiceLevelObjective : referencedCompositeSLOs) {
          compositeSLOService.reset(compositeServiceLevelObjective);
        }
      } else {
        updateServiceLevelIndicatorEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator,
            healthSourceIndicator, timePeriod, currentTimePeriod, serviceLevelObjectiveIdentifier);
      }
      serviceLevelIndicatorIdentifiers.add(serviceLevelIndicatorDTO.getIdentifier());
    }
    List<String> toBeDeletedIdentifiers =
        serviceLevelIndicatorsList.stream()
            .filter(identifier -> !serviceLevelIndicatorIdentifiers.contains(identifier))
            .collect(Collectors.toList());
    deleteByIdentifier(projectParams, toBeDeletedIdentifiers);
    return serviceLevelIndicatorIdentifiers;
  }

  private void deleteAndCreate(ProjectParams projectParams, ServiceLevelIndicator serviceLevelIndicator) {
    deleteByIdentifier(projectParams, Collections.singletonList(serviceLevelIndicator.getIdentifier()));
    saveServiceLevelIndicatorEntity(serviceLevelIndicator);
  }

  @Override
  public boolean deleteByIdentifier(ProjectParams projectParams, List<String> serviceLevelIndicatorIdentifier) {
    boolean isDeleted = false;
    if (isNotEmpty(serviceLevelIndicatorIdentifier)) {
      Query<ServiceLevelIndicator> serviceLevelIndicatorQuery =
          hPersistence.createQuery(ServiceLevelIndicator.class, excludeAuthority)
              .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
              .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
              .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
              .field(ServiceLevelIndicatorKeys.identifier)
              .in(serviceLevelIndicatorIdentifier);
      List<ServiceLevelIndicator> serviceLevelIndicatorList = serviceLevelIndicatorQuery.asList();
      isDeleted = hPersistence.delete(serviceLevelIndicatorQuery);
      serviceLevelIndicatorList.forEach(sli -> {
        String verificationTaskId = verificationTaskService.getSLIVerificationTaskId(sli.getAccountId(), sli.getUuid());
        if (StringUtils.isNotBlank(verificationTaskId)) {
          sideKickService.schedule(
              VerificationTaskCleanupSideKickData.builder().verificationTaskId(verificationTaskId).build(),
              clock.instant().plus(Duration.ofMinutes(15)));
        }
      });
    }
    return isDeleted;
  }

  private void updateServiceLevelIndicatorEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator, String healthSourceIndicator,
      TimePeriod timePeriod, TimePeriod currentTimePeriod, String serviceLevelObjectiveIdentifier) {
    UpdatableEntity<ServiceLevelIndicator, ServiceLevelIndicator> updatableEntity =
        serviceLevelIndicatorEntityAndDTOTransformer.getUpdatableEntity(serviceLevelIndicatorDTO);
    ServiceLevelIndicator serviceLevelIndicator =
        getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());
    UpdateOperations<ServiceLevelIndicator> updateOperations =
        hPersistence.createUpdateOperations(ServiceLevelIndicator.class);
    ServiceLevelIndicator updatableServiceLevelIndicator = convertDTOToEntity(projectParams, serviceLevelIndicatorDTO,
        monitoredServiceIndicator, healthSourceIndicator, serviceLevelIndicator.isEnabled());
    updatableEntity.setUpdateOperations(updateOperations, updatableServiceLevelIndicator);
    if (shouldReAnalysis(serviceLevelIndicator, updatableServiceLevelIndicator, timePeriod, currentTimePeriod)) {
      // We are doing this inside if else because we want to update the SLI version as well in this case.
      // And we need to update SLI before queuing analysis so that queued analysis when executed takes the updated SLI.
      updateOperations.inc(ServiceLevelIndicatorKeys.version);
      hPersistence.update(serviceLevelIndicator, updateOperations);
      Instant startTime = timePeriod.getStartTime(ZoneOffset.UTC).minus(INTERVAL_HOURS, ChronoUnit.HOURS);
      SLIRecord firstSLIRecord = sliRecordService.getFirstSLIRecord(serviceLevelIndicator.getUuid(), startTime);
      Instant endTime = DateTimeUtils.roundDownTo5MinBoundary(clock.instant());
      if (firstSLIRecord != null) {
        startTime = startTime.isBefore(firstSLIRecord.getTimestamp()) ? firstSLIRecord.getTimestamp() : startTime;
      } else {
        startTime = endTime;
      }
      startTime = DateTimeUtils.roundDownTo5MinBoundary(startTime);
      for (Instant intervalStartTime = startTime; intervalStartTime.isBefore(endTime);) {
        Instant intervalEndTime = intervalStartTime.plus(INTERVAL_HOURS, ChronoUnit.HOURS);
        if (intervalEndTime.isAfter(endTime)) {
          intervalEndTime = endTime;
        }
        AnalysisInput analysisInput = AnalysisInput.builder()
                                          .verificationTaskId(verificationTaskService.getSLIVerificationTaskId(
                                              serviceLevelIndicator.getAccountId(), serviceLevelIndicator.getUuid()))
                                          .startTime(intervalStartTime)
                                          .endTime(intervalEndTime)
                                          .build();
        if (intervalStartTime.equals(startTime)) {
          orchestrationService.queueAnalysis(analysisInput);
        } else {
          orchestrationService.queueAnalysisWithoutEventPublish(serviceLevelIndicator.getAccountId(), analysisInput);
        }
        intervalStartTime = intervalEndTime;
      }
    } else {
      hPersistence.update(serviceLevelIndicator, updateOperations);
    }
    if (serviceLevelIndicator.shouldRecalculateReferencedCompositeSLOs(updatableServiceLevelIndicator)) {
      List<CompositeServiceLevelObjective> referencedCompositeSLOs =
          compositeSLOService.getReferencedCompositeSLOs(projectParams, serviceLevelObjectiveIdentifier);
      for (CompositeServiceLevelObjective compositeServiceLevelObjective : referencedCompositeSLOs) {
        compositeSLOService.recalculate(compositeServiceLevelObjective);
      }
    }
  }

  /**
   * In case of the changes in SLI and new slo target period is before the current slo target period
   * we need to recalculate the data again as the older is stale.
   * @param serviceLevelIndicator
   * @param updatableServiceLevelIndicator
   * @param timePeriod
   * @param currentTimePeriod
   * @return
   */
  private boolean shouldReAnalysis(ServiceLevelIndicator serviceLevelIndicator,
      ServiceLevelIndicator updatableServiceLevelIndicator, TimePeriod timePeriod, TimePeriod currentTimePeriod) {
    boolean shouldReAnalysisOnTimePeriod = timePeriod.getStartTime().isBefore(currentTimePeriod.getStartTime());
    return serviceLevelIndicator.shouldReAnalysis(updatableServiceLevelIndicator) || shouldReAnalysisOnTimePeriod;
  }

  private void saveServiceLevelIndicatorEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator, String healthSourceIndicator,
      boolean isEnabled) {
    ServiceLevelIndicator serviceLevelIndicator = convertDTOToEntity(
        projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator, isEnabled);
    saveServiceLevelIndicatorEntity(serviceLevelIndicator);
  }

  private void saveServiceLevelIndicatorEntity(ServiceLevelIndicator serviceLevelIndicator) {
    hPersistence.save(serviceLevelIndicator);
    verificationTaskService.createSLIVerificationTask(serviceLevelIndicator.getAccountId(),
        serviceLevelIndicator.getUuid(), serviceLevelIndicator.getVerificationTaskTags());
  }

  private ServiceLevelIndicator convertDTOToEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator, String healthSourceIndicator,
      boolean isEnabled) {
    return serviceLevelIndicatorEntityAndDTOTransformer.getEntity(
        projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator, isEnabled);
  }

  private ServiceLevelIndicatorDTO convertEntityToDTO(ServiceLevelIndicator serviceLevelIndicator) {
    return serviceLevelIndicatorEntityAndDTOTransformer.getDto(serviceLevelIndicator);
  }

  @Override
  public ServiceLevelIndicator getServiceLevelIndicator(ProjectParams projectParams, String identifier) {
    return hPersistence.createQuery(ServiceLevelIndicator.class)
        .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(ServiceLevelIndicatorKeys.identifier, identifier)
        .get();
  }

  @Override
  public List<String> getSLIsWithMetrics(ProjectParams projectParams, String monitoredServiceIdentifier,
      String healthSourceIdentifier, List<String> metricIdentifiers) {
    Query<ServiceLevelIndicator> query =
        hPersistence.createQuery(ServiceLevelIndicator.class)
            .disableValidation()
            .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(ServiceLevelIndicatorKeys.healthSourceIdentifier, healthSourceIdentifier)
            .filter(ServiceLevelIndicatorKeys.monitoredServiceIdentifier, monitoredServiceIdentifier)
            .project(ServiceLevelIndicatorKeys.identifier, true);
    query.or(Arrays.stream(SLIMetricType.values())
                 .flatMap(type -> type.getMetricDbFields().stream())
                 .map(metricFieldName -> query.criteria(metricFieldName).in(metricIdentifiers))
                 .toArray(Criteria[] ::new));
    return query.asList().stream().map(ServiceLevelIndicator::getIdentifier).collect(Collectors.toList());
  }

  @Override
  public List<String> getSLIs(ProjectParams projectParams, String monitoredServiceIdentifier) {
    Query<ServiceLevelIndicator> query =
        hPersistence.createQuery(ServiceLevelIndicator.class)
            .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(ServiceLevelIndicatorKeys.monitoredServiceIdentifier, monitoredServiceIdentifier)
            .project(ServiceLevelIndicatorKeys.identifier, true);
    return query.asList().stream().map(ServiceLevelIndicator::getIdentifier).collect(Collectors.toList());
  }

  @Override
  public List<ServiceLevelIndicator> getSLIs(ProjectParams projectParams) {
    return hPersistence.createQuery(ServiceLevelIndicator.class)
        .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .project(ServiceLevelIndicatorKeys.identifier, true)
        .asList();
  }

  private Map<String, MetricGraph> getMetricGraphs(
      List<TimeSeriesRecord> timeSeriesRecords, List<String> metricIdentifiers, Instant startTime, Instant endTime) {
    Map<String, List<DataPoints>> metricToDataPoints =
        timeSeriesRecords.stream()
            .filter(timeSeriesRecord -> metricIdentifiers.contains(timeSeriesRecord.getMetricIdentifier()))
            .collect(Collectors.groupingBy(TimeSeriesRecord::getMetricIdentifier,
                Collectors.mapping(timeSeriesRecord
                    -> DataPoints.builder()
                           .value(timeSeriesRecord.getMetricValue())
                           .timeStamp(timeSeriesRecord.getTimestamp())
                           .build(),
                    Collectors.toList())));

    Map<String, String> metricIdentifierToNameMap = timeSeriesRecords.stream().collect(
        Collectors.toMap(TimeSeriesRecord::getMetricIdentifier, TimeSeriesRecord::getMetricName, (a, b) -> a));

    return metricToDataPoints.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
        entry
        -> MetricGraph.builder()
               .metricName(metricIdentifierToNameMap.get(entry.getKey()))
               .metricIdentifier(entry.getKey())
               .startTime(startTime.toEpochMilli())
               .endTime(endTime.toEpochMilli())
               .dataPoints(
                   entry.getValue()
                       .stream()
                       .sorted(
                           (dataPoint1, dataPoint2) -> dataPoint1.getTimeStamp() < dataPoint2.getTimeStamp() ? -1 : 1)
                       .collect(Collectors.toList()))
               .build()));
  }

  private MetricOnboardingGraph.RatioMetricPercentageGraph getMetricPercentageGraph(
      List<TimeSeriesRecord> timeSeriesRecords, List<String> metricIdentifiers, Instant startTime, Instant endTime,
      RatioSLIMetricEventType eventType) {
    if (metricIdentifiers.size() != 2) {
      return null;
    }
    List<DataPoints> dataPoints = new ArrayList<>();
    Map<Long, Double> metric1ValuesMap = new HashMap<>();

    for (TimeSeriesRecord record : timeSeriesRecords) {
      if (record.getMetricIdentifier().equals(metricIdentifiers.get(0))) {
        metric1ValuesMap.put(record.getTimestamp(), record.getMetricValue());
      }
    }

    for (TimeSeriesRecord record : timeSeriesRecords) {
      if (record.getMetricIdentifier().equals(metricIdentifiers.get(1))) {
        long timestamp = record.getTimestamp();
        if (metric1ValuesMap.containsKey(timestamp)) {
          double metricValue1 = metric1ValuesMap.get(timestamp);
          double metricValue2 = record.getMetricValue();
          if (Objects.isNull(metricValue1) || Objects.isNull(metricValue2) || metricValue2 == 0) {
            continue;
          }
          double ratio = eventType.computeSLIMetricValue(metricValue1, metricValue2);
          dataPoints.add(DataPoints.builder().timeStamp(timestamp).value(ratio).build());
        }
      }
    }
    return MetricOnboardingGraph.RatioMetricPercentageGraph.builder()
        .metricIdentifier1(metricIdentifiers.get(0))
        .metricIdentifier2(metricIdentifiers.get(1))
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .dataPoints(dataPoints)
        .build();
  }

  @Override
  public List<CVConfig> fetchCVConfigForSLI(ServiceLevelIndicator serviceLevelIndicator) {
    return healthSourceService.getCVConfigs(serviceLevelIndicator.getAccountId(),
        serviceLevelIndicator.getOrgIdentifier(), serviceLevelIndicator.getProjectIdentifier(),
        serviceLevelIndicator.getMonitoredServiceIdentifier(), serviceLevelIndicator.getHealthSourceIdentifier());
  }

  @Override
  public List<CVConfig> fetchCVConfigForSLI(String sliId) {
    return fetchCVConfigForSLI(get(sliId));
  }

  @Override
  public void setMonitoredServiceSLIsEnableFlag(
      ProjectParams projectParams, String monitoredServiceIdentifier, boolean isEnabled) {
    hPersistence.update(hPersistence.createQuery(ServiceLevelIndicator.class)
                            .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
                            .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
                            .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
                            .filter(ServiceLevelIndicatorKeys.monitoredServiceIdentifier, monitoredServiceIdentifier),
        hPersistence.createUpdateOperations(ServiceLevelIndicator.class)
            .set(ServiceLevelIndicatorKeys.enabled, isEnabled));
  }

  @Override
  public void enqueueDataCollectionFailureInstanceAndTriggerAnalysis(
      String verificationTaskId, Instant startTime, Instant endTime, ServiceLevelIndicator serviceLevelIndicator) {
    entityUnavailabilityStatusesService.create(ProjectParams.builder()
                                                   .accountIdentifier(serviceLevelIndicator.getAccountId())
                                                   .orgIdentifier(serviceLevelIndicator.getOrgIdentifier())
                                                   .projectIdentifier(serviceLevelIndicator.getProjectIdentifier())
                                                   .build(),
        Collections.singletonList(EntityUnavailabilityStatusesDTO.builder()
                                      .orgIdentifier(serviceLevelIndicator.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelIndicator.getProjectIdentifier())
                                      .entityType(EntityType.SLO)
                                      .entityId(serviceLevelIndicator.getUuid())
                                      .status(EntityUnavailabilityStatus.DATA_COLLECTION_FAILED)
                                      .startTime(startTime.getEpochSecond())
                                      .endTime(endTime.getEpochSecond())
                                      .build()));
    for (Instant intervalStartTime = startTime; intervalStartTime.isBefore(endTime);) {
      Instant intervalEndTime = intervalStartTime.plus(INTERVAL_HOURS, ChronoUnit.HOURS);
      if (intervalEndTime.isAfter(endTime)) {
        intervalEndTime = endTime;
      }
      AnalysisInput analysisInput = AnalysisInput.builder()
                                        .verificationTaskId(verificationTaskId)
                                        .startTime(intervalStartTime)
                                        .endTime(intervalEndTime)
                                        .build();
      if (intervalStartTime.equals(startTime)) {
        orchestrationService.queueAnalysis(analysisInput);
      } else {
        orchestrationService.queueAnalysisWithoutEventPublish(serviceLevelIndicator.getAccountId(), analysisInput);
      }
      intervalStartTime = intervalEndTime;
    }
  }

  @Override
  public String getScopedIdentifier(ServiceLevelIndicator serviceLevelIndicator) {
    return ScopedInformation.getScopedInformation(serviceLevelIndicator.getAccountId(),
        serviceLevelIndicator.getOrgIdentifier(), serviceLevelIndicator.getProjectIdentifier(),
        serviceLevelIndicator.getIdentifier());
  }
}
