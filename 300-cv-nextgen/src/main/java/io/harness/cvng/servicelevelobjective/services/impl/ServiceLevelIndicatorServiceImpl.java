package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorTransformer;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class ServiceLevelIndicatorServiceImpl implements ServiceLevelIndicatorService {
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
  @Inject Clock clock;

  @Override
  public TimeGraphResponse getOnboardingGraph(ProjectParams projectParams, String monitoredServiceIdentifier,
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
            -> timeSeriesRecord.getMetricName(),
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

    return TimeGraphResponse.builder()
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .dataPoints(
            sliAnalyseResponses.stream()
                .map(sliAnalyseResponse
                    -> DataPoints.builder()
                           .timeStamp(sliAnalyseResponse.getTimeStamp().toEpochMilli())
                           .value(serviceLevelIndicatorDTO.getSliMissingDataType().calculateSLIValue(
                               sliAnalyseResponse.getRunningGoodCount(), sliAnalyseResponse.getRunningBadCount(),
                               Duration.between(initialSLIResponse.getTimeStamp(), sliAnalyseResponse.getTimeStamp())
                                       .toMinutes()
                                   + 1))
                           .build())
                .collect(Collectors.toList()))
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
    List<ServiceLevelIndicator> serviceLevelIndicatorList =
        hPersistence.createQuery(ServiceLevelIndicator.class)
            .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .field(ServiceLevelIndicatorKeys.identifier)
            .in(serviceLevelIndicators)
            .asList();
    return serviceLevelIndicatorList.stream().map(this::sliEntityToDTO).collect(Collectors.toList());
  }

  @Override
  public List<String> update(ProjectParams projectParams, List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList,
      String serviceLevelObjectiveIdentifier, List<String> serviceLevelIndicatorsList, String monitoredServiceIndicator,
      String healthSourceIndicator) {
    List<String> serviceLevelIndicatorIdentifiers = new ArrayList<>();
    for (ServiceLevelIndicatorDTO serviceLevelIndicatorDTO : serviceLevelIndicatorDTOList) {
      if (Objects.isNull(serviceLevelIndicatorDTO.getName())
          && Objects.isNull(serviceLevelIndicatorDTO.getIdentifier())) {
        generateNameAndIdentifier(serviceLevelObjectiveIdentifier, serviceLevelIndicatorDTO);
      }
      ServiceLevelIndicator serviceLevelIndicator =
          getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());
      if (Objects.isNull(serviceLevelIndicator)) {
        saveServiceLevelIndicatorEntity(
            projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
      } else {
        updateServiceLevelIndicatorEntity(
            projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
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

  @Override
  public void deleteByIdentifier(ProjectParams projectParams, List<String> serviceLevelIndicatorIdentifier) {
    if (isNotEmpty(serviceLevelIndicatorIdentifier)) {
      hPersistence.delete(hPersistence.createQuery(ServiceLevelIndicator.class)
                              .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
                              .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
                              .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
                              .field(ServiceLevelIndicatorKeys.identifier)
                              .in(serviceLevelIndicatorIdentifier));
    }
  }

  private String getDSL(DataSourceType dataSourceType, MetricPack metricPack) {
    metricPackService.populateDataCollectionDsl(dataSourceType, metricPack);
    return metricPack.getDataCollectionDsl();
  }

  private void updateServiceLevelIndicatorEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator,
      String healthSourceIndicator) {
    UpdatableEntity<ServiceLevelIndicator, ServiceLevelIndicator> updatableEntity =
        serviceLevelIndicatorMapBinder.get(serviceLevelIndicatorDTO.getSpec().getType());
    ServiceLevelIndicator serviceLevelIndicator =
        getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());
    UpdateOperations<ServiceLevelIndicator> updateOperations =
        hPersistence.createUpdateOperations(ServiceLevelIndicator.class);
    ServiceLevelIndicator updatableServiceLevelIndicator =
        convertDTOToEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
    updatableEntity.setUpdateOperations(updateOperations, updatableServiceLevelIndicator);
    hPersistence.update(serviceLevelIndicator, updateOperations);
  }

  private void saveServiceLevelIndicatorEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator,
      String healthSourceIndicator) {
    ServiceLevelIndicator serviceLevelIndicator =
        convertDTOToEntity(projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator);
    hPersistence.save(serviceLevelIndicator);
    verificationTaskService.createSLIVerificationTask(
        serviceLevelIndicator.getAccountId(), serviceLevelIndicator.getUuid());
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

  private ServiceLevelIndicatorDTO sliEntityToDTO(ServiceLevelIndicator serviceLevelIndicator) {
    return serviceLevelIndicatorEntityAndDTOTransformer.getDto(serviceLevelIndicator);
  }

  @Override
  public List<CVConfig> fetchCVConfigForSLI(ServiceLevelIndicator serviceLevelIndicator) {
    return healthSourceService.getCVConfigs(serviceLevelIndicator.getAccountId(),
        serviceLevelIndicator.getOrgIdentifier(), serviceLevelIndicator.getProjectIdentifier(),
        serviceLevelIndicator.getMonitoredServiceIdentifier(), serviceLevelIndicator.getHealthSourceIdentifier());
  }
}
