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
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.sidekick.VerificationTaskCleanupSideKickData;
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs;
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs.MetricGraph;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorTransformer;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.persistence.HPersistence;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class ServiceLevelIndicatorServiceImpl implements ServiceLevelIndicatorService {
  private static final int INTERVAL_HOURS = 12;
  @Inject private HPersistence hPersistence;
  @Inject private Map<SLIMetricType, ServiceLevelIndicatorUpdatableEntity> serviceLevelIndicatorMapBinder;
  @Inject private ServiceLevelIndicatorEntityAndDTOTransformer serviceLevelIndicatorEntityAndDTOTransformer;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private HealthSourceService healthSourceService;
  @Inject private OnboardingService onboardingService;
  @Inject private MetricPackService metricPackService;
  @Inject private Map<SLIMetricType, ServiceLevelIndicatorTransformer> serviceLevelIndicatorTransformerMap;
  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;
  @Inject private SLIDataProcessorService sliDataProcessorService;
  @Inject private Clock clock;
  @Inject private OrchestrationService orchestrationService;
  @Inject private SideKickService sideKickService;

  @Override
  public SLIOnboardingGraphs getOnboardingGraphs(ProjectParams projectParams, String monitoredServiceIdentifier,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String tracingId) {
    List<CVConfig> cvConfigs = healthSourceService
                                   .getCVConfigs(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                                       projectParams.getProjectIdentifier(), monitoredServiceIdentifier,
                                       serviceLevelIndicatorDTO.getHealthSourceRef())
                                   .stream()
                                   .filter(cvConfig -> cvConfig instanceof MetricCVConfig)
                                   .map(cvConfig -> (MetricCVConfig) cvConfig)
                                   .peek(metricCVConfig
                                       -> metricPackService.populateDataCollectionDsl(
                                           metricCVConfig.getType(), metricCVConfig.getMetricPack()))
                                   .collect(Collectors.toList());

    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorTransformerMap.get(serviceLevelIndicatorDTO.getSpec().getType())
            .getEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIdentifier,
                serviceLevelIndicatorDTO.getHealthSourceRef());
    Preconditions.checkArgument(isNotEmpty(cvConfigs), "Health source not present");
    CVConfig baseCVConfig = cvConfigs.get(0);
    DataCollectionInfo dataCollectionInfo = dataSourceTypeDataCollectionInfoMapperMap.get(baseCVConfig.getType())
                                                .toDataCollectionInfo(cvConfigs, serviceLevelIndicator);

    Instant endTime = clock.instant().truncatedTo(ChronoUnit.MINUTES);
    Instant startTime = endTime.minus(Duration.ofDays(1));

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
    final Gson gson = new Gson();
    Type type = new TypeToken<List<TimeSeriesRecord>>() {}.getType();
    List<TimeSeriesRecord> timeSeriesRecords = gson.fromJson(JsonUtils.asJson(response.getResult()), type);

    Map<String, List<SLIAnalyseRequest>> sliAnalyseRequest =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(timeSeriesRecord
            -> timeSeriesRecord.getMetricIdentifier(),
            Collectors.mapping(timeSeriesRecord
                -> SLIAnalyseRequest.builder()
                       .metricValue(timeSeriesRecord.getMetricValue())
                       .timeStamp(Instant.ofEpochMilli(timeSeriesRecord.getTimestamp()))
                       .build(),
                Collectors.toList())));
    List<SLIAnalyseResponse> sliAnalyseResponses = sliDataProcessorService.process(
        sliAnalyseRequest, serviceLevelIndicatorDTO.getSpec().getSpec(), startTime, endTime);
    sliAnalyseResponses.sort(Comparator.comparing(SLIAnalyseResponse::getTimeStamp));
    final SLIAnalyseResponse initialSLIResponse = sliAnalyseResponses.get(0);

    TimeGraphResponse sliGraph =
        TimeGraphResponse.builder()
            .startTime(startTime.toEpochMilli())
            .endTime(endTime.toEpochMilli())
            .dataPoints(sliAnalyseResponses.stream()
                            .map(sliAnalyseResponse
                                -> DataPoints.builder()
                                       .timeStamp(sliAnalyseResponse.getTimeStamp().toEpochMilli())
                                       .value(serviceLevelIndicatorDTO.getSliMissingDataType()
                                                  .calculateSLIValue(sliAnalyseResponse.getRunningGoodCount(),
                                                      sliAnalyseResponse.getRunningBadCount(),
                                                      Duration.between(initialSLIResponse.getTimeStamp(),
                                                                  sliAnalyseResponse.getTimeStamp())
                                                              .toMinutes()
                                                          + 1)
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
  public List<String> create(ProjectParams projectParams, List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList,
      String serviceLevelObjectiveIdentifier, String monitoredServiceIndicator, String healthSourceIndicator) {
    List<String> serviceLevelIndicatorIdentifiers = new ArrayList<>();
    for (ServiceLevelIndicatorDTO serviceLevelIndicatorDTO : serviceLevelIndicatorDTOList) {
      if (Objects.isNull(serviceLevelIndicatorDTO.getName())
          && Objects.isNull(serviceLevelIndicatorDTO.getIdentifier())) {
        generateNameAndIdentifier(serviceLevelObjectiveIdentifier, serviceLevelIndicatorDTO);
      }
      saveServiceLevelIndicatorEntity(
          projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
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
    serviceLevelIndicatorDTO.setName(
        serviceLevelObjectiveIdentifier + "_" + serviceLevelIndicatorDTO.getSpec().getSpec().getMetricName());
    serviceLevelIndicatorDTO.setIdentifier(
        serviceLevelObjectiveIdentifier + "_" + serviceLevelIndicatorDTO.getSpec().getSpec().getMetricName());
  }

  @Override
  public List<ServiceLevelIndicatorDTO> get(ProjectParams projectParams, List<String> serviceLevelIndicators) {
    List<ServiceLevelIndicator> serviceLevelIndicatorList = getEntities(projectParams, serviceLevelIndicators);
    return serviceLevelIndicatorList.stream().map(this::sliEntityToDTO).collect(Collectors.toList());
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
    for (ServiceLevelIndicatorDTO serviceLevelIndicatorDTO : serviceLevelIndicatorDTOList) {
      if (Objects.isNull(serviceLevelIndicatorDTO.getName())
          && Objects.isNull(serviceLevelIndicatorDTO.getIdentifier())) {
        generateNameAndIdentifier(serviceLevelObjectiveIdentifier, serviceLevelIndicatorDTO);
      }
      ServiceLevelIndicator serviceLevelIndicator =
          getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());
      ServiceLevelIndicator newServiceLevelIndicator =
          convertDTOToEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
      if (Objects.isNull(serviceLevelIndicator)) {
        saveServiceLevelIndicatorEntity(newServiceLevelIndicator);
      } else if (!serviceLevelIndicator.isUpdatable(convertDTOToEntity(
                     projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator))) {
        deleteAndCreate(projectParams, newServiceLevelIndicator);
      } else {
        updateServiceLevelIndicatorEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator,
            healthSourceIndicator, timePeriod, currentTimePeriod);
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
  public void deleteByIdentifier(ProjectParams projectParams, List<String> serviceLevelIndicatorIdentifier) {
    if (isNotEmpty(serviceLevelIndicatorIdentifier)) {
      Query<ServiceLevelIndicator> serviceLevelIndicatorQuery =
          hPersistence.createQuery(ServiceLevelIndicator.class, excludeAuthority)
              .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
              .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
              .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
              .field(ServiceLevelIndicatorKeys.identifier)
              .in(serviceLevelIndicatorIdentifier);
      List<ServiceLevelIndicator> serviceLevelIndicatorList = serviceLevelIndicatorQuery.asList();
      hPersistence.delete(serviceLevelIndicatorQuery);
      serviceLevelIndicatorList.forEach(sli -> {
        String verificationTaskId = verificationTaskService.getSLIVerificationTaskId(sli.getAccountId(), sli.getUuid());
        if (StringUtils.isNotBlank(verificationTaskId)) {
          sideKickService.schedule(
              VerificationTaskCleanupSideKickData.builder().verificationTaskId(verificationTaskId).build(),
              clock.instant().plus(Duration.ofMinutes(15)));
        }
      });
    }
  }

  private String getDSL(DataSourceType dataSourceType, MetricPack metricPack) {
    metricPackService.populateDataCollectionDsl(dataSourceType, metricPack);
    return metricPack.getDataCollectionDsl();
  }

  private void updateServiceLevelIndicatorEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator, String healthSourceIndicator,
      TimePeriod timePeriod, TimePeriod currentTimePeriod) {
    UpdatableEntity<ServiceLevelIndicator, ServiceLevelIndicator> updatableEntity =
        serviceLevelIndicatorMapBinder.get(serviceLevelIndicatorDTO.getSpec().getType());
    ServiceLevelIndicator serviceLevelIndicator =
        getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());
    UpdateOperations<ServiceLevelIndicator> updateOperations =
        hPersistence.createUpdateOperations(ServiceLevelIndicator.class);
    ServiceLevelIndicator updatableServiceLevelIndicator =
        convertDTOToEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
    if (shouldReAnalysis(serviceLevelIndicator, updatableServiceLevelIndicator, timePeriod, currentTimePeriod)) {
      updatableEntity.setUpdateOperations(updateOperations, updatableServiceLevelIndicator);
      Instant startTime = timePeriod.getStartTime(ZoneOffset.UTC).minus(INTERVAL_HOURS, ChronoUnit.HOURS);
      Instant endTime = DateTimeUtils.roundDownTo5MinBoundary(clock.instant());
      for (Instant intervalStartTime = startTime; intervalStartTime.isBefore(endTime);) {
        Instant intervalEndTime = intervalStartTime.plus(INTERVAL_HOURS, ChronoUnit.HOURS);
        if (intervalEndTime.isAfter(endTime)) {
          intervalEndTime = endTime;
        }
        orchestrationService.queueAnalysis(verificationTaskService.getSLIVerificationTaskId(
                                               serviceLevelIndicator.getAccountId(), serviceLevelIndicator.getUuid()),
            intervalStartTime, intervalEndTime);
        intervalStartTime = intervalEndTime;
      }
      hPersistence.update(serviceLevelIndicator, updateOperations);
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
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator,
      String healthSourceIndicator) {
    ServiceLevelIndicator serviceLevelIndicator =
        convertDTOToEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
    saveServiceLevelIndicatorEntity(serviceLevelIndicator);
  }

  private void saveServiceLevelIndicatorEntity(ServiceLevelIndicator serviceLevelIndicator) {
    hPersistence.save(serviceLevelIndicator);
    verificationTaskService.createSLIVerificationTask(serviceLevelIndicator.getAccountId(),
        serviceLevelIndicator.getUuid(), serviceLevelIndicator.getVerificationTaskTags());
  }

  private ServiceLevelIndicator convertDTOToEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator,
      String healthSourceIndicator) {
    return serviceLevelIndicatorEntityAndDTOTransformer.getEntity(
        projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
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

  private ServiceLevelIndicatorDTO sliEntityToDTO(ServiceLevelIndicator serviceLevelIndicator) {
    return serviceLevelIndicatorEntityAndDTOTransformer.getDto(serviceLevelIndicator);
  }

  private Map<String, MetricGraph> getMetricGraphs(
      List<TimeSeriesRecord> timeSeriesRecords, List<String> metricIdentifiers, Instant startTime, Instant endTime) {
    Map<String, List<DataPoints>> metricToDataPoints =
        timeSeriesRecords.stream()
            .filter(timeSeriesRecord -> metricIdentifiers.contains(timeSeriesRecord.getMetricIdentifier()))
            .collect(Collectors.groupingBy(timeSeriesRecord
                -> timeSeriesRecord.getMetricIdentifier(),
                Collectors.mapping(timeSeriesRecord
                    -> DataPoints.builder()
                           .value(timeSeriesRecord.getMetricValue())
                           .timeStamp(timeSeriesRecord.getTimestamp())
                           .build(),
                    Collectors.toList())));

    Map<String, String> metricIdentifierToNameMap = timeSeriesRecords.stream().collect(
        Collectors.toMap(TimeSeriesRecord::getMetricIdentifier, TimeSeriesRecord::getMetricName, (a, b) -> a));

    return metricToDataPoints.entrySet().stream().collect(Collectors.toMap(entry
        -> entry.getKey(),
        entry
        -> (MetricGraph) MetricGraph.builder()
               .metricName(metricIdentifierToNameMap.get(entry.getKey()))
               .metricIdentifier(entry.getKey())
               .startTime(startTime.toEpochMilli())
               .endTime(endTime.toEpochMilli())
               .dataPoints(entry.getValue())
               .build()));
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
}
