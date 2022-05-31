/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.beans.params.ServiceEnvironmentParams.builderWithProjectParams;
import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.COOL_OFF_DURATION;
import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.getNotificationTemplateData;
import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.getNotificationTemplateId;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.HealthMonitoringFlagResponse;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.monitoredService.AnomaliesSummaryDTO;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.CountServiceDTO;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.monitoredService.HealthScoreDTO;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.HistoricalTrend;
import io.harness.cvng.core.beans.monitoredService.MetricDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceChangeDetailSLO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.Sources;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceListItemDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceListItemDTO.MonitoredServiceListItemDTOBuilder;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceListItemDTO.SloHealthIndicatorDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceWithHealthSources;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceWithHealthSources.HealthSourceSummary;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceYamlDTO;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricHealthSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.filterParams.LiveMonitoringLogAnalysisFilter;
import io.harness.cvng.core.beans.params.filterParams.TimeSeriesAnalysisFilter;
import io.harness.cvng.core.beans.params.logsFilterParams.LiveMonitoringLogsFilter;
import io.harness.cvng.core.beans.template.TemplateDTO;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.core.handler.monitoredService.BaseMonitoredServiceHandler;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.SetupUsageEventService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.cvng.core.utils.template.MonitoredServiceYamlExpressionEvaluator;
import io.harness.cvng.core.utils.template.TemplateFacade;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.dashboard.services.api.LogDashboardService;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannel;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceHealthScoreCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class MonitoredServiceServiceImpl implements MonitoredServiceService {
  private static final Map<MonitoredServiceType, String> MONITORED_SERVICE_YAML_TEMPLATE = new HashMap<>();
  static {
    try {
      MONITORED_SERVICE_YAML_TEMPLATE.put(MonitoredServiceType.APPLICATION,
          Resources.toString(MonitoredServiceServiceImpl.class.getResource("monitored-service-template.yaml"),
              StandardCharsets.UTF_8));
      MONITORED_SERVICE_YAML_TEMPLATE.put(MonitoredServiceType.INFRASTRUCTURE,
          Resources.toString(MonitoredServiceServiceImpl.class.getResource("monitored-service-infra-template.yaml"),
              StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Inject private HealthSourceService healthSourceService;
  @Inject private HPersistence hPersistence;
  @Inject private HeatMapService heatMapService;
  @Inject private NextGenService nextGenService;
  @Inject private ServiceDependencyService serviceDependencyService;
  @Inject private SetupUsageEventService setupUsageEventService;
  @Inject private ChangeSourceService changeSourceService;
  @Inject private Clock clock;
  @Inject private TimeSeriesDashboardService timeSeriesDashboardService;
  @Inject private LogDashboardService logDashboardService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject private Set<BaseMonitoredServiceHandler> monitoredServiceHandlers;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVNGLogService cvngLogService;
  @Inject private NotificationRuleService notificationRuleService;
  @Inject private TemplateFacade templateFacade;
  @Inject private SLODashboardService sloDashboardService;
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject private NotificationClient notificationClient;
  @Inject private ActivityService activityService;

  private static final String templateIdentifierName = "monitoredServiceName";

  @Override
  public MonitoredServiceResponse create(String accountId, MonitoredServiceDTO monitoredServiceDTO) {
    ServiceEnvironmentParams environmentParams = ServiceEnvironmentParams.builder()
                                                     .accountIdentifier(accountId)
                                                     .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
                                                     .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
                                                     .serviceIdentifier(monitoredServiceDTO.getServiceRef())
                                                     .environmentIdentifier(monitoredServiceDTO.getEnvironmentRef())
                                                     .build();

    validate(monitoredServiceDTO);
    checkIfAlreadyPresent(accountId, environmentParams, monitoredServiceDTO.getIdentifier(),
        monitoredServiceDTO.getSources(), monitoredServiceDTO.getType());

    if (monitoredServiceDTO.getSources() != null && isNotEmpty(monitoredServiceDTO.getSources().getHealthSources())) {
      healthSourceService.create(accountId, monitoredServiceDTO.getOrgIdentifier(),
          monitoredServiceDTO.getProjectIdentifier(), monitoredServiceDTO.getEnvironmentRef(),
          monitoredServiceDTO.getServiceRef(), monitoredServiceDTO.getIdentifier(), monitoredServiceDTO.getIdentifier(),
          monitoredServiceDTO.getSources().getHealthSources(), getMonitoredServiceEnableStatus());
    }
    if (isNotEmpty(monitoredServiceDTO.getDependencies())) {
      validateDependencyMetadata(ProjectParams.builder()
                                     .accountIdentifier(accountId)
                                     .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
                                     .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
                                     .build(),
          monitoredServiceDTO.getDependencies());
      serviceDependencyService.updateDependencies(
          environmentParams, monitoredServiceDTO.getIdentifier(), monitoredServiceDTO.getDependencies());
    }
    if (monitoredServiceDTO.getSources() != null && isNotEmpty(monitoredServiceDTO.getSources().getChangeSources())) {
      changeSourceService.create(MonitoredServiceParams.builderWithServiceEnvParams(environmentParams)
                                     .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                                     .build(),
          monitoredServiceDTO.getSources().getChangeSources());
    }
    saveMonitoredServiceEntity(environmentParams, monitoredServiceDTO);
    log.info(
        "Saved monitored service with identifier {} for account {}", monitoredServiceDTO.getIdentifier(), accountId);
    setupUsageEventService.sendCreateEventsForMonitoredService(environmentParams, monitoredServiceDTO);
    return get(environmentParams, monitoredServiceDTO.getIdentifier());
  }

  @Override
  public MonitoredServiceResponse createFromYaml(ProjectParams projectParams, String yaml) {
    MonitoredServiceDTO monitoredServiceDTO = getExpandedMonitoredServiceFromYaml(projectParams, yaml);
    return create(projectParams.getAccountIdentifier(), monitoredServiceDTO);
  }

  @Override
  public MonitoredServiceResponse updateFromYaml(ProjectParams projectParams, String identifier, String yaml) {
    MonitoredServiceDTO monitoredServiceDTO = getExpandedMonitoredServiceFromYaml(projectParams, yaml);
    monitoredServiceDTO.setIdentifier(identifier);
    return update(projectParams.getAccountIdentifier(), monitoredServiceDTO);
  }

  @SneakyThrows
  private MonitoredServiceDTO getExpandedMonitoredServiceFromYaml(ProjectParams projectParams, String yaml) {
    String templateResolvedYaml = templateFacade.resolveYaml(projectParams, yaml);
    MonitoredServiceYamlExpressionEvaluator yamlExpressionEvaluator =
        new MonitoredServiceYamlExpressionEvaluator(templateResolvedYaml);
    MonitoredServiceDTO monitoredServiceDTO =
        YamlUtils.read(templateResolvedYaml, MonitoredServiceYamlDTO.class).getMonitoredServiceDTO();
    monitoredServiceDTO = (MonitoredServiceDTO) yamlExpressionEvaluator.resolve(monitoredServiceDTO, false);
    monitoredServiceDTO.setProjectIdentifier(projectParams.getProjectIdentifier());
    monitoredServiceDTO.setOrgIdentifier(projectParams.getOrgIdentifier());
    return monitoredServiceDTO;
  }

  private void validateDependencyMetadata(ProjectParams projectParams, Set<ServiceDependencyDTO> dependencyDTOs) {
    if (dependencyDTOs == null) {
      return;
    }
    dependencyDTOs.forEach(dependencyDTO -> {
      if (dependencyDTO.getDependencyMetadata() == null) {
        return;
      }
      MonitoredServiceDTO monitoredServiceDTO =
          get(projectParams, dependencyDTO.getMonitoredServiceIdentifier()).getMonitoredServiceDTO();
      Preconditions.checkNotNull(monitoredServiceDTO.getSources());
      Preconditions.checkNotNull(monitoredServiceDTO.getSources().getChangeSources());
      Set<ChangeSourceType> changeSourceTypes = monitoredServiceDTO.getSources()
                                                    .getChangeSources()
                                                    .stream()
                                                    .map(ChangeSourceDTO::getType)
                                                    .collect(Collectors.toSet());
      Set<ChangeSourceType> supportedChangeSources =
          dependencyDTO.getDependencyMetadata().getSupportedChangeSourceTypes();
      boolean isValid = false;
      for (ChangeSourceType changeSourceType : supportedChangeSources) {
        if (changeSourceTypes.contains(changeSourceType)) {
          isValid = true;
          break;
        }
      }
      if (!isValid) {
        throw new InvalidRequestException(
            "Invalid dependency setup for monitoredSource " + dependencyDTO.getMonitoredServiceIdentifier());
      }
    });
  }

  private boolean getMonitoredServiceEnableStatus() {
    return false; // TODO: Need to implement this logic later based on licensing.
  }

  @Override
  public MonitoredServiceResponse update(String accountId, MonitoredServiceDTO monitoredServiceDTO) {
    ServiceEnvironmentParams environmentParams = ServiceEnvironmentParams.builder()
                                                     .accountIdentifier(accountId)
                                                     .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
                                                     .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
                                                     .serviceIdentifier(monitoredServiceDTO.getServiceRef())
                                                     .environmentIdentifier(monitoredServiceDTO.getEnvironmentRef())
                                                     .build();
    MonitoredService monitoredService = getMonitoredService(environmentParams, monitoredServiceDTO.getIdentifier());
    if (monitoredService == null) {
      throw new InvalidRequestException(String.format(
          "Monitored Source Entity  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          monitoredServiceDTO.getIdentifier(), accountId, monitoredServiceDTO.getOrgIdentifier(),
          monitoredServiceDTO.getProjectIdentifier()));
    }
    Preconditions.checkArgument(monitoredService.getServiceIdentifier().equals(monitoredServiceDTO.getServiceRef()),
        "serviceRef update is not allowed");
    if (monitoredService.getType() == MonitoredServiceType.APPLICATION) {
      Preconditions.checkArgument(
          monitoredService.getEnvironmentIdentifierList().equals(monitoredServiceDTO.getEnvironmentRefList()),
          "environmentRef update is not allowed");
    }

    MonitoredServiceDTO existingMonitoredServiceDTO =
        createMonitoredServiceDTOFromEntity(monitoredService, environmentParams).getMonitoredServiceDTO();

    monitoredServiceHandlers.forEach(baseMonitoredServiceHandler
        -> baseMonitoredServiceHandler.beforeUpdate(
            environmentParams, existingMonitoredServiceDTO, monitoredServiceDTO));
    validate(monitoredServiceDTO);

    updateHealthSources(monitoredService, monitoredServiceDTO);
    changeSourceService.update(MonitoredServiceParams.builderWithProjectParams(environmentParams)
                                   .monitoredServiceIdentifier(monitoredService.getIdentifier())
                                   .build(),
        monitoredServiceDTO.getSources().getChangeSources());
    updateMonitoredService(monitoredService, monitoredServiceDTO);
    setupUsageEventService.sendCreateEventsForMonitoredService(environmentParams, monitoredServiceDTO);
    return get(environmentParams, monitoredServiceDTO.getIdentifier());
  }

  private void updateMonitoredService(MonitoredService monitoredService, MonitoredServiceDTO monitoredServiceDTO) {
    UpdateOperations<MonitoredService> updateOperations = hPersistence.createUpdateOperations(MonitoredService.class);
    updateOperations.set(MonitoredServiceKeys.name, monitoredServiceDTO.getName());
    if (monitoredServiceDTO.getDescription() != null) {
      updateOperations.set(MonitoredServiceKeys.desc, monitoredServiceDTO.getDescription());
    }
    if (monitoredServiceDTO.getTags() != null) {
      updateOperations.set(MonitoredServiceKeys.tags, TagMapper.convertToList(monitoredServiceDTO.getTags()));
    }
    if (monitoredServiceDTO.getSources() != null) {
      List<String> updatedHealthSourceIdentifiers = monitoredServiceDTO.getSources()
                                                        .getHealthSources()
                                                        .stream()
                                                        .map(healthSource -> healthSource.getIdentifier())
                                                        .collect(Collectors.toList());
      updateOperations.set(MonitoredServiceKeys.healthSourceIdentifiers, updatedHealthSourceIdentifiers);
      List<String> updatedChangeSourceIdentifiers = monitoredServiceDTO.getSources()
                                                        .getChangeSources()
                                                        .stream()
                                                        .map(changeSource -> changeSource.getIdentifier())
                                                        .collect(Collectors.toList());
      updateOperations.set(MonitoredServiceKeys.changeSourceIdentifiers, updatedChangeSourceIdentifiers);
    }
    if (monitoredServiceDTO.getTemplate() != null) {
      updateOperations.set(MonitoredServiceKeys.templateIdentifier, monitoredServiceDTO.getTemplate().getTemplateRef());
      updateOperations.set(
          MonitoredServiceKeys.templateVersionLabel, monitoredServiceDTO.getTemplate().getVersionLabel());
    }
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(monitoredService.getAccountId())
                                      .orgIdentifier(monitoredService.getOrgIdentifier())
                                      .projectIdentifier(monitoredService.getProjectIdentifier())
                                      .build();
    updateOperations.set(MonitoredServiceKeys.notificationRuleRefs,
        notificationRuleService.getNotificationRuleRefs(monitoredServiceDTO.getNotificationRuleRefs()));
    validateDependencyMetadata(projectParams, monitoredServiceDTO.getDependencies());
    serviceDependencyService.updateDependencies(
        projectParams, monitoredService.getIdentifier(), monitoredServiceDTO.getDependencies());

    hPersistence.update(monitoredService, updateOperations);
  }

  private void updateHealthSources(MonitoredService monitoredService, MonitoredServiceDTO monitoredServiceDTO) {
    Map<String, HealthSource> currentHealthSourcesMap = new HashMap<>();
    if (monitoredServiceDTO.getSources() != null) {
      monitoredServiceDTO.getSources().getHealthSources().forEach(
          healthSource -> currentHealthSourcesMap.put(healthSource.getIdentifier(), healthSource));
    }
    List<String> existingHealthIdentifiers = monitoredService.getHealthSourceIdentifiers();
    List<String> toBeDeletedIdentifiers = existingHealthIdentifiers.stream()
                                              .filter(identifier -> !currentHealthSourcesMap.containsKey(identifier))
                                              .collect(Collectors.toList());
    healthSourceService.delete(monitoredService.getAccountId(), monitoredServiceDTO.getOrgIdentifier(),
        monitoredServiceDTO.getProjectIdentifier(), monitoredServiceDTO.getIdentifier(), toBeDeletedIdentifiers);

    Set<HealthSource> toBeCreatedHealthSources = new HashSet<>();
    Set<HealthSource> toBeUpdatedHealthSources = new HashSet<>();

    currentHealthSourcesMap.forEach((identifier, healthSource) -> {
      if (existingHealthIdentifiers.contains(identifier)) {
        toBeUpdatedHealthSources.add(healthSource);
      } else {
        toBeCreatedHealthSources.add(healthSource);
      }
    });
    healthSourceService.create(monitoredService.getAccountId(), monitoredServiceDTO.getOrgIdentifier(),
        monitoredServiceDTO.getProjectIdentifier(), monitoredService.getEnvironmentIdentifier(),
        monitoredService.getServiceIdentifier(), monitoredService.getIdentifier(), monitoredServiceDTO.getIdentifier(),
        toBeCreatedHealthSources, monitoredService.isEnabled());
    healthSourceService.update(monitoredService.getAccountId(), monitoredServiceDTO.getOrgIdentifier(),
        monitoredServiceDTO.getProjectIdentifier(), monitoredService.getEnvironmentIdentifier(),
        monitoredService.getServiceIdentifier(), monitoredService.getIdentifier(), monitoredServiceDTO.getIdentifier(),
        toBeUpdatedHealthSources, monitoredService.isEnabled());
  }

  @Override
  public boolean delete(ProjectParams projectParams, String identifier) {
    MonitoredService monitoredService = getMonitoredService(projectParams, identifier);
    if (monitoredService == null) {
      throw new InvalidRequestException(
          String.format("Monitored Source Entity  with identifier %s and accountId %s is not present", identifier,
              projectParams.getAccountIdentifier()));
    }
    ServiceEnvironmentParams environmentParams = builderWithProjectParams(projectParams)
                                                     .serviceIdentifier(monitoredService.getServiceIdentifier())
                                                     .environmentIdentifier(monitoredService.getEnvironmentIdentifier())
                                                     .build();
    monitoredServiceHandlers.forEach(baseMonitoredServiceHandler
        -> baseMonitoredServiceHandler.beforeDelete(environmentParams,
            createMonitoredServiceDTOFromEntity(monitoredService, environmentParams).getMonitoredServiceDTO()));
    healthSourceService.delete(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
        projectParams.getProjectIdentifier(), monitoredService.getIdentifier(),
        monitoredService.getHealthSourceIdentifiers());
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                        .monitoredServiceIdentifier(monitoredService.getIdentifier())
                                                        .build();
    serviceDependencyService.deleteDependenciesForService(monitoredServiceParams, monitoredService.getIdentifier());
    changeSourceService.delete(monitoredServiceParams, monitoredService.getChangeSourceIdentifiers());
    notificationRuleService.delete(environmentParams,
        monitoredService.getNotificationRuleRefs()
            .stream()
            .map(ref -> ref.getNotificationRuleRef())
            .collect(Collectors.toList()));
    boolean deleted = hPersistence.delete(monitoredService);
    if (deleted) {
      setupUsageEventService.sendDeleteEventsForMonitoredService(projectParams, identifier);
    }
    return deleted;
  }

  @Override
  public List<MonitoredServiceResponse> get(ProjectParams projectParams, Set<String> identifierSet) {
    List<MonitoredService> monitoredServices =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .field(MonitoredServiceKeys.identifier)
            .in(identifierSet)
            .asList();
    List<MonitoredServiceResponse> monitoredServiceResponseList = new ArrayList<>();
    monitoredServices.forEach(monitoredService -> {
      ServiceEnvironmentParams environmentParams =
          builderWithProjectParams(projectParams)
              .serviceIdentifier(monitoredService.getServiceIdentifier())
              .environmentIdentifier(monitoredService.getEnvironmentIdentifier())
              .build();
      monitoredServiceResponseList.add(createMonitoredServiceDTOFromEntity(monitoredService, environmentParams));
    });
    return monitoredServiceResponseList;
  }

  @Override
  public MonitoredServiceResponse get(ProjectParams projectParams, String identifier) {
    MonitoredService monitoredServiceEntity = getMonitoredService(projectParams, identifier);
    if (monitoredServiceEntity == null) {
      throw new InvalidRequestException(
          String.format("Monitored Source Entity with identifier %s is not present", identifier));
    }
    ServiceEnvironmentParams environmentParams =
        builderWithProjectParams(projectParams)
            .serviceIdentifier(monitoredServiceEntity.getServiceIdentifier())
            .environmentIdentifier(monitoredServiceEntity.getEnvironmentIdentifier())
            .build();

    return createMonitoredServiceDTOFromEntity(monitoredServiceEntity, environmentParams);
  }

  private MonitoredServiceResponse createMonitoredServiceDTOFromEntity(
      MonitoredService monitoredServiceEntity, ProjectParams projectParams) {
    MonitoredServiceDTO monitoredServiceDTO =
        MonitoredServiceDTO.builder()
            .name(monitoredServiceEntity.getName())
            .identifier(monitoredServiceEntity.getIdentifier())
            .orgIdentifier(monitoredServiceEntity.getOrgIdentifier())
            .projectIdentifier(monitoredServiceEntity.getProjectIdentifier())
            .environmentRef(monitoredServiceEntity.getEnvironmentIdentifier())
            .environmentRefList(monitoredServiceEntity.getEnvironmentIdentifierList())
            .serviceRef(monitoredServiceEntity.getServiceIdentifier())
            .type(monitoredServiceEntity.getType())
            .description(monitoredServiceEntity.getDesc())
            .tags(TagMapper.convertToMap(monitoredServiceEntity.getTags()))
            .template(StringUtils.isNotEmpty(monitoredServiceEntity.getTemplateIdentifier())
                    ? TemplateDTO.builder()
                          .templateRef(monitoredServiceEntity.getTemplateIdentifier())
                          .versionLabel(monitoredServiceEntity.getTemplateVersionLabel())
                          .build()
                    : null)
            .sources(
                Sources.builder()
                    .healthSources(healthSourceService.get(monitoredServiceEntity.getAccountId(),
                        monitoredServiceEntity.getOrgIdentifier(), monitoredServiceEntity.getProjectIdentifier(),
                        monitoredServiceEntity.getIdentifier(), monitoredServiceEntity.getHealthSourceIdentifiers()))
                    // TODO: Update this call to get by monitoredServiceIdentifier in the next PR
                    .changeSources(
                        changeSourceService.get(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                    .monitoredServiceIdentifier(monitoredServiceEntity.getIdentifier())
                                                    .build(),
                            monitoredServiceEntity.getChangeSourceIdentifiers()))
                    .build())
            // TODO: Figure out dependencies by refList instead of one env
            .dependencies(serviceDependencyService.getDependentServicesForMonitoredService(
                ProjectParams.builder()
                    .accountIdentifier(projectParams.getAccountIdentifier())
                    .orgIdentifier(projectParams.getOrgIdentifier())
                    .projectIdentifier(projectParams.getProjectIdentifier())
                    .build(),
                monitoredServiceEntity.getIdentifier()))
            .notificationRuleRefs(
                notificationRuleService.getNotificationRuleRefDTOs(monitoredServiceEntity.getNotificationRuleRefs()))
            .build();
    return MonitoredServiceResponse.builder()
        .monitoredService(monitoredServiceDTO)
        .createdAt(monitoredServiceEntity.getCreatedAt())
        .lastModifiedAt(monitoredServiceEntity.getLastUpdatedAt())
        .build();
  }

  private MonitoredServiceResponse getApplicationMonitoredServiceResponse(
      ServiceEnvironmentParams serviceEnvironmentParams) {
    Optional<MonitoredService> monitoredService = getApplicationMonitoredService(serviceEnvironmentParams);
    if (monitoredService.isPresent()) {
      return get(serviceEnvironmentParams, monitoredService.get().getIdentifier());
    } else {
      return null;
    }
  }

  @Override
  public MonitoredServiceResponse get(ServiceEnvironmentParams serviceEnvironmentParams) {
    MonitoredService monitoredService = getMonitoredService(serviceEnvironmentParams);
    if (monitoredService == null) {
      return null;
    }
    return get(serviceEnvironmentParams, monitoredService.getIdentifier());
  }

  @Override
  public PageResponse<MonitoredServiceResponse> getList(ProjectParams projectParams,
      List<String> environmentIdentifiers, Integer offset, Integer pageSize, String filter) {
    List<MonitoredService> monitoredServiceEntities =
        getMonitoredServicesByEnvIds(projectParams, environmentIdentifiers);
    if (isEmpty(monitoredServiceEntities)) {
      throw new InvalidRequestException(
          String.format("There are no Monitored Services for the environments: %s", environmentIdentifiers));
    }

    PageResponse<MonitoredService> monitoredServiceEntitiesPageResponse =
        PageUtils.offsetAndLimit(monitoredServiceEntities, offset, pageSize);

    List<MonitoredServiceResponse> monitoredServiceResponses =
        monitoredServiceEntitiesPageResponse.getContent()
            .stream()
            .map(monitoredServiceEntity -> {
              ServiceEnvironmentParams environmentParams =
                  builderWithProjectParams(projectParams)
                      .serviceIdentifier(monitoredServiceEntity.getServiceIdentifier())
                      .environmentIdentifier(monitoredServiceEntity.getEnvironmentIdentifier())
                      .build();

              MonitoredServiceDTO monitoredServiceDTO =
                  MonitoredServiceDTO.builder()
                      .name(monitoredServiceEntity.getName())
                      .identifier(monitoredServiceEntity.getIdentifier())
                      .orgIdentifier(monitoredServiceEntity.getOrgIdentifier())
                      .projectIdentifier(monitoredServiceEntity.getProjectIdentifier())
                      .environmentRef(monitoredServiceEntity.getEnvironmentIdentifier())
                      .environmentRefList(monitoredServiceEntity.getEnvironmentIdentifierList())
                      .serviceRef(monitoredServiceEntity.getServiceIdentifier())
                      .type(monitoredServiceEntity.getType())
                      .description(monitoredServiceEntity.getDesc())
                      .tags(TagMapper.convertToMap(monitoredServiceEntity.getTags()))
                      .sources(
                          Sources.builder()
                              .healthSources(healthSourceService.get(monitoredServiceEntity.getAccountId(),
                                  monitoredServiceEntity.getOrgIdentifier(),
                                  monitoredServiceEntity.getProjectIdentifier(), monitoredServiceEntity.getIdentifier(),
                                  monitoredServiceEntity.getHealthSourceIdentifiers()))
                              .changeSources(changeSourceService.get(
                                  MonitoredServiceParams.builderWithProjectParams(environmentParams)
                                      .monitoredServiceIdentifier(monitoredServiceEntity.getIdentifier())
                                      .build(),
                                  monitoredServiceEntity.getChangeSourceIdentifiers()))
                              .build())
                      .dependencies(serviceDependencyService.getDependentServicesForMonitoredService(
                          projectParams, monitoredServiceEntity.getIdentifier()))
                      .build();
              return MonitoredServiceResponse.builder()
                  .monitoredService(monitoredServiceDTO)
                  .createdAt(monitoredServiceEntity.getCreatedAt())
                  .lastModifiedAt(monitoredServiceEntity.getLastUpdatedAt())
                  .build();
            })
            .collect(Collectors.toList());

    return PageResponse.<MonitoredServiceResponse>builder()
        .pageSize(pageSize)
        .pageIndex(offset)
        .totalPages(monitoredServiceEntitiesPageResponse.getTotalPages())
        .totalItems(monitoredServiceEntitiesPageResponse.getTotalItems())
        .pageItemCount(monitoredServiceEntitiesPageResponse.getPageItemCount())
        .content(monitoredServiceResponses)
        .build();
  }

  @Override
  public List<MonitoredServiceWithHealthSources> getAllWithTimeSeriesHealthSources(ProjectParams projectParams) {
    List<MonitoredService> monitoredServiceEntities = getMonitoredServices(projectParams);
    if (isEmpty(monitoredServiceEntities)) {
      return Collections.emptyList();
    }
    Map<String, Set<HealthSource>> healthSourceMap = healthSourceService.getHealthSource(monitoredServiceEntities);
    return monitoredServiceEntities.stream()
        .map(monitoredServiceEntity -> {
          Set<HealthSource> healthSource = healthSourceMap.get(monitoredServiceEntity.getIdentifier());
          return MonitoredServiceWithHealthSources.builder()
              .name(monitoredServiceEntity.getName())
              .identifier(monitoredServiceEntity.getIdentifier())
              .healthSources(Objects.nonNull(healthSource) ? healthSource.stream()
                                                                 .map(x
                                                                     -> HealthSourceSummary.builder()
                                                                            .name(x.getName())
                                                                            .identifier(x.getIdentifier())
                                                                            .build())
                                                                 .collect(Collectors.toSet())
                                                           : Collections.emptySet())
              .build();
        })
        .collect(Collectors.toList());
  }

  @Override
  public MonitoredServiceDTO getApplicationMonitoredServiceDTO(ServiceEnvironmentParams serviceEnvironmentParams) {
    MonitoredServiceResponse monitoredServiceResponse =
        getApplicationMonitoredServiceResponse(serviceEnvironmentParams);
    if (monitoredServiceResponse == null) {
      return null;
    } else {
      return monitoredServiceResponse.getMonitoredServiceDTO();
    }
  }
  @Override
  public MonitoredServiceDTO getMonitoredServiceDTO(MonitoredServiceParams monitoredServiceParams) {
    MonitoredServiceResponse monitoredServiceResponse =
        createMonitoredServiceDTOFromEntity(getMonitoredService(monitoredServiceParams), monitoredServiceParams);
    if (monitoredServiceResponse == null) {
      return null;
    } else {
      return monitoredServiceResponse.getMonitoredServiceDTO();
    }
  }
  @Override
  public MonitoredService getMonitoredService(ProjectParams projectParams, String identifier) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
        .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(MonitoredServiceKeys.identifier, identifier)
        .get();
  }
  @Override
  public Optional<MonitoredService> getApplicationMonitoredService(ServiceEnvironmentParams serviceEnvironmentParams) {
    return Optional.ofNullable(
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, serviceEnvironmentParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, serviceEnvironmentParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, serviceEnvironmentParams.getProjectIdentifier())
            .filter(MonitoredServiceKeys.serviceIdentifier, serviceEnvironmentParams.getServiceIdentifier())
            .field(MonitoredServiceKeys.environmentIdentifierList)
            .hasThisOne(serviceEnvironmentParams.getEnvironmentIdentifier())
            .filter(MonitoredServiceKeys.type, MonitoredServiceType.APPLICATION)
            .get());
  }
  @Deprecated
  private MonitoredService getMonitoredService(ServiceEnvironmentParams serviceEnvironmentParams) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, serviceEnvironmentParams.getAccountIdentifier())
        .filter(MonitoredServiceKeys.orgIdentifier, serviceEnvironmentParams.getOrgIdentifier())
        .filter(MonitoredServiceKeys.projectIdentifier, serviceEnvironmentParams.getProjectIdentifier())
        .filter(MonitoredServiceKeys.serviceIdentifier, serviceEnvironmentParams.getServiceIdentifier())
        .field(MonitoredServiceKeys.environmentIdentifierList)
        .hasThisOne(serviceEnvironmentParams.getEnvironmentIdentifier())
        .get();
  }
  @Override
  public MonitoredService getMonitoredService(MonitoredServiceParams monitoredServiceParams) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, monitoredServiceParams.getAccountIdentifier())
        .filter(MonitoredServiceKeys.orgIdentifier, monitoredServiceParams.getOrgIdentifier())
        .filter(MonitoredServiceKeys.projectIdentifier, monitoredServiceParams.getProjectIdentifier())
        .filter(MonitoredServiceKeys.identifier, monitoredServiceParams.getMonitoredServiceIdentifier())
        .get();
  }

  private List<MonitoredService> getMonitoredServicesByEnvIds(
      ProjectParams projectParams, List<String> environmentIdentifiers) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
        .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(MonitoredServiceKeys.environmentIdentifierList)
        .hasAnyOf(environmentIdentifiers)
        .asList();
  }
  private List<MonitoredService> getMonitoredServices(
      ProjectParams projectParams, List<String> monitoredServiceIdentifiers) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
        .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(MonitoredServiceKeys.identifier)
        .in(monitoredServiceIdentifiers)
        .asList();
  }

  private List<MonitoredService> getMonitoredServices(ProjectParams projectParams) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
        .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .asList();
  }

  private List<MonitoredService> getMonitoredServices(
      ProjectParams projectParams, String environmentIdentifier, String filter) {
    Query<MonitoredService> monitoredServicesQuery =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .order(Sort.descending(MonitoredServiceKeys.lastUpdatedAt));
    if (isNotEmpty(environmentIdentifier)) {
      monitoredServicesQuery.field(MonitoredServiceKeys.environmentIdentifierList).hasThisOne(environmentIdentifier);
    }
    List<MonitoredService> monitoredServices = monitoredServicesQuery.asList();
    if (filter != null) {
      monitoredServices =
          monitoredServices.stream()
              .filter(monitoredService
                  -> isEmpty(filter) || monitoredService.getName().toLowerCase().contains(filter.trim().toLowerCase()))
              .collect(Collectors.toList());
    }
    return monitoredServices;
  }

  private void checkIfAlreadyPresent(String accountId, ServiceEnvironmentParams serviceEnvironmentParams,
      String identifier, Sources sources, MonitoredServiceType monitoredServiceType) {
    MonitoredService monitoredServiceEntity = getMonitoredService(serviceEnvironmentParams, identifier);
    if (monitoredServiceEntity != null) {
      throw new DuplicateFieldException(String.format(
          "Monitored Source Entity  with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
          identifier, serviceEnvironmentParams.getOrgIdentifier(), serviceEnvironmentParams.getProjectIdentifier()));
    }
    if (monitoredServiceType == MonitoredServiceType.APPLICATION) {
      monitoredServiceEntity =
          hPersistence.createQuery(MonitoredService.class)
              .filter(MonitoredServiceKeys.accountId, accountId)
              .filter(MonitoredServiceKeys.orgIdentifier, serviceEnvironmentParams.getOrgIdentifier())
              .filter(MonitoredServiceKeys.projectIdentifier, serviceEnvironmentParams.getProjectIdentifier())
              .filter(MonitoredServiceKeys.serviceIdentifier, serviceEnvironmentParams.getServiceIdentifier())
              .filter(MonitoredServiceKeys.environmentIdentifier, serviceEnvironmentParams.getEnvironmentIdentifier())
              .get();
      if (monitoredServiceEntity != null) {
        throw new DuplicateFieldException(String.format(
            "Monitored Source Entity  with duplicate service ref %s, environmentRef %s having identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
            serviceEnvironmentParams.getServiceIdentifier(), serviceEnvironmentParams.getEnvironmentIdentifier(),
            identifier, serviceEnvironmentParams.getOrgIdentifier(), serviceEnvironmentParams.getProjectIdentifier()));
      }
    }
    if (sources != null) {
      healthSourceService.checkIfAlreadyPresent(accountId, serviceEnvironmentParams.getOrgIdentifier(),
          serviceEnvironmentParams.getProjectIdentifier(), identifier, sources.getHealthSources());
    }
  }

  private void saveMonitoredServiceEntity(ProjectParams projectParams, MonitoredServiceDTO monitoredServiceDTO) {
    MonitoredService monitoredServiceEntity =
        MonitoredService.builder()
            .name(monitoredServiceDTO.getName())
            .desc(monitoredServiceDTO.getDescription())
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .environmentIdentifier(monitoredServiceDTO.getEnvironmentRef())
            .environmentIdentifierList(monitoredServiceDTO.getEnvironmentRefList())
            .serviceIdentifier(monitoredServiceDTO.getServiceRef())
            .identifier(monitoredServiceDTO.getIdentifier())
            .type(monitoredServiceDTO.getType())
            .enabled(getMonitoredServiceEnableStatus())
            .tags(TagMapper.convertToList(monitoredServiceDTO.getTags()))
            .notificationRuleRefs(
                notificationRuleService.getNotificationRuleRefs(monitoredServiceDTO.getNotificationRuleRefs()))
            .build();
    if (monitoredServiceDTO.getTemplate() != null) {
      monitoredServiceEntity.setTemplateIdentifier(monitoredServiceDTO.getTemplate().getTemplateRef());
      monitoredServiceEntity.setTemplateVersionLabel(monitoredServiceDTO.getTemplate().getVersionLabel());
    }
    if (monitoredServiceDTO.getSources() != null) {
      monitoredServiceEntity.setHealthSourceIdentifiers(monitoredServiceDTO.getSources()
                                                            .getHealthSources()
                                                            .stream()
                                                            .map(healthSourceInfo -> healthSourceInfo.getIdentifier())
                                                            .collect(Collectors.toList()));
    }
    monitoredServiceEntity.setChangeSourceIdentifiers(monitoredServiceDTO.getSources()
                                                          .getChangeSources()
                                                          .stream()
                                                          .map(changeSourceDTO -> changeSourceDTO.getIdentifier())
                                                          .collect(Collectors.toList()));
    hPersistence.save(monitoredServiceEntity);
  }

  private HistoricalTrend getMonitoredServiceHistorialTrend(
      MonitoredService monitoredService, ProjectParams projectParams, DurationDTO duration, Instant endTime) {
    Preconditions.checkNotNull(monitoredService,
        "Monitored service for provided serviceIdentifier and envIdentifier or monitoredServiceIdentifier does not exist.");
    return heatMapService.getOverAllHealthScore(projectParams, monitoredService.getIdentifier(), duration, endTime);
  }

  @Override
  public List<MonitoredService> list(@NonNull ProjectParams projectParams, @Nullable String serviceIdentifier,
      @Nullable String environmentIdentifier) {
    Query<MonitoredService> query =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier());
    if (environmentIdentifier != null) {
      query.filter(MonitoredServiceKeys.environmentIdentifier, environmentIdentifier);
    }
    if (serviceIdentifier != null) {
      query.filter(MonitoredServiceKeys.serviceIdentifier, serviceIdentifier);
    }
    return query.asList();
  }

  @Override
  public List<MonitoredService> list(@NonNull ProjectParams projectParams, List<String> identifiers) {
    Query<MonitoredService> query =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier());
    if (isNotEmpty(identifiers)) {
      query.field(MonitoredServiceKeys.identifier).in(identifiers);
    }

    return query.asList();
  }

  private List<RiskData> getSortedDependentServiceRiskScoreList(
      ProjectParams projectParams, List<String> dependentServices) {
    List<RiskData> dependentServiceRiskScores = new ArrayList<>();
    List<MonitoredService> dependentMonitoredServices = getMonitoredServices(projectParams, dependentServices);
    Map<String, RiskData> latestRiskScoreByServiceMap =
        getLatestRiskScoreByServiceMap(projectParams, dependentMonitoredServices);
    dependentMonitoredServices.forEach(dependentMonitoredService
        -> dependentServiceRiskScores.add(latestRiskScoreByServiceMap.get(dependentMonitoredService.getIdentifier())));
    List<RiskData> sortedDependentServiceWithPositiveRiskScores =
        dependentServiceRiskScores.stream()
            .filter(x -> x.getHealthScore() != null && x.getHealthScore() >= 0)
            .collect(Collectors.toList());
    sortedDependentServiceWithPositiveRiskScores.sort(Comparator.comparing(RiskData::getHealthScore));
    List<RiskData> dependentServiceWithNegativeRiskScores =
        dependentServiceRiskScores.stream()
            .filter(x -> x.getHealthScore() == null || x.getHealthScore() < 0)
            .collect(Collectors.toList());
    sortedDependentServiceWithPositiveRiskScores.addAll(dependentServiceWithNegativeRiskScores);
    return sortedDependentServiceWithPositiveRiskScores;
  }

  private Map<String, RiskData> getLatestRiskScoreByServiceMap(
      ProjectParams projectParams, List<MonitoredService> monitoredServices) {
    return heatMapService.getLatestRiskScoreByMonitoredService(projectParams,
        monitoredServices.stream()
            .map(monitoredService -> monitoredService.getIdentifier())
            .collect(Collectors.toList()));
  }

  private List<MonitoredService> getMonitoredServicesAtRisk(List<MonitoredService> monitoredServices,
      Map<String, RiskData> latestRiskScoreByServiceMap, boolean servicesAtRiskFilter) {
    if (servicesAtRiskFilter) {
      monitoredServices = monitoredServices.stream()
                              .filter(x -> {
                                RiskData riskData = latestRiskScoreByServiceMap.get(x.getIdentifier());
                                return riskData.getHealthScore() != null && riskData.getHealthScore() <= 25;
                              })
                              .collect(Collectors.toList());
    }
    return monitoredServices;
  }

  @Override
  public PageResponse<MonitoredServiceListItemDTO> list(ProjectParams projectParams, String environmentIdentifier,
      Integer offset, Integer pageSize, String filter, boolean servicesAtRiskFilter) {
    List<MonitoredService> monitoredServices = getMonitoredServices(projectParams, environmentIdentifier, filter);
    Map<String, MonitoredService> idToMonitoredServiceMap =
        monitoredServices.stream().collect(Collectors.toMap(MonitoredService::getIdentifier, Function.identity()));
    Map<String, RiskData> latestRiskScoreByServiceMap =
        getLatestRiskScoreByServiceMap(projectParams, monitoredServices);
    List<MonitoredServiceListItemDTOBuilder> monitoredServiceListItemDTOS =
        getMonitoredServicesAtRisk(monitoredServices, latestRiskScoreByServiceMap, servicesAtRiskFilter)
            .stream()
            .map(monitoredService -> toMonitorServiceListDTO(monitoredService))
            .collect(Collectors.toList());

    PageResponse<MonitoredServiceListItemDTOBuilder> monitoredServiceListDTOBuilderPageResponse =
        PageUtils.offsetAndLimit(monitoredServiceListItemDTOS, offset, pageSize);

    List<String> serviceIdentifiers = new ArrayList<>();
    List<String> environmentIdentifierList = new ArrayList<>();
    List<String> monitoredServiceIdentifiers = new ArrayList<>();
    for (MonitoredServiceListItemDTOBuilder monitoredServiceListDTOBuilder :
        monitoredServiceListDTOBuilderPageResponse.getContent()) {
      serviceIdentifiers.add(monitoredServiceListDTOBuilder.getServiceRef());
      environmentIdentifierList.add(monitoredServiceListDTOBuilder.getEnvironmentRef());
      monitoredServiceIdentifiers.add(monitoredServiceListDTOBuilder.getIdentifier());
    }

    Map<String, String> serviceIdNameMap =
        nextGenService.getServiceIdNameMap(projectParams, new ArrayList<>(serviceIdentifiers));
    Map<String, String> environmentIdNameMap =
        nextGenService.getEnvironmentIdNameMap(projectParams, new ArrayList<>(environmentIdentifierList));
    List<HistoricalTrend> historicalTrendList = heatMapService.getHistoricalTrend(projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), monitoredServiceIdentifiers, 24);
    Map<String, List<String>> monitoredServiceToDependentServicesMap =
        serviceDependencyService.getMonitoredServiceToDependentServicesMap(projectParams, monitoredServiceIdentifiers);

    List<MonitoredServiceListItemDTO> monitoredServiceListDTOS = new ArrayList<>();
    int index = 0;
    Map<String, List<SloHealthIndicatorDTO>> sloHealthIndicatorDTOMap =
        getSloHealthIndicators(projectParams, monitoredServiceIdentifiers);
    for (MonitoredServiceListItemDTOBuilder monitoredServiceListDTOBuilder :
        monitoredServiceListDTOBuilderPageResponse.getContent()) {
      String serviceName = serviceIdNameMap.get(monitoredServiceListDTOBuilder.getServiceRef());
      String environmentName = environmentIdNameMap.get(monitoredServiceListDTOBuilder.getEnvironmentRef());
      HistoricalTrend historicalTrend = historicalTrendList.get(index);
      RiskData monitoredServiceRiskScore =
          latestRiskScoreByServiceMap.get(monitoredServiceListDTOBuilder.getIdentifier());
      List<RiskData> dependentServiceRiskScoreList = getSortedDependentServiceRiskScoreList(
          projectParams, monitoredServiceToDependentServicesMap.get(monitoredServiceListDTOBuilder.getIdentifier()));
      index++;
      MonitoredServiceParams monitoredServiceParams =
          MonitoredServiceParams.builder()
              .accountIdentifier(projectParams.getAccountIdentifier())
              .orgIdentifier(projectParams.getOrgIdentifier())
              .projectIdentifier(projectParams.getProjectIdentifier())
              .monitoredServiceIdentifier(monitoredServiceListDTOBuilder.getIdentifier())
              .build();

      ChangeSummaryDTO changeSummary = changeSourceService.getChangeSummary(monitoredServiceParams,
          idToMonitoredServiceMap.get(monitoredServiceListDTOBuilder.getIdentifier()).getChangeSourceIdentifiers(),
          clock.instant().minus(Duration.ofDays(1)), clock.instant());
      monitoredServiceListDTOS.add(
          monitoredServiceListDTOBuilder.historicalTrend(historicalTrend)
              .currentHealthScore(monitoredServiceRiskScore)
              .dependentHealthScore(dependentServiceRiskScoreList)
              .serviceName(serviceName)
              .environmentName(environmentName)
              .changeSummary(changeSummary)
              .sloHealthIndicators(sloHealthIndicatorDTOMap.get(monitoredServiceListDTOBuilder.getIdentifier()))
              .build());
    }
    return PageResponse.<MonitoredServiceListItemDTO>builder()
        .pageSize(pageSize)
        .pageIndex(offset)
        .totalPages(monitoredServiceListDTOBuilderPageResponse.getTotalPages())
        .totalItems(monitoredServiceListDTOBuilderPageResponse.getTotalItems())
        .pageItemCount(monitoredServiceListDTOBuilderPageResponse.getPageItemCount())
        .content(monitoredServiceListDTOS)
        .build();
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<MonitoredService> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    List<MonitoredService> monitoredServices = hPersistence.createQuery(MonitoredService.class)
                                                   .filter(MonitoredServiceKeys.accountId, accountId)
                                                   .filter(MonitoredServiceKeys.orgIdentifier, orgIdentifier)
                                                   .filter(MonitoredServiceKeys.projectIdentifier, projectIdentifier)
                                                   .asList();
    monitoredServices.forEach(monitoredService
        -> delete(ProjectParams.builder()
                      .accountIdentifier(monitoredService.getAccountId())
                      .orgIdentifier(monitoredService.getOrgIdentifier())
                      .projectIdentifier(monitoredService.getProjectIdentifier())
                      .build(),
            monitoredService.getIdentifier()));
  }

  @Override
  public void deleteByOrgIdentifier(Class<MonitoredService> clazz, String accountId, String orgIdentifier) {
    List<MonitoredService> monitoredServices = hPersistence.createQuery(MonitoredService.class)
                                                   .filter(MonitoredServiceKeys.accountId, accountId)
                                                   .filter(MonitoredServiceKeys.orgIdentifier, orgIdentifier)
                                                   .asList();
    monitoredServices.forEach(monitoredService
        -> delete(ProjectParams.builder()
                      .accountIdentifier(monitoredService.getAccountId())
                      .orgIdentifier(monitoredService.getOrgIdentifier())
                      .projectIdentifier(monitoredService.getProjectIdentifier())
                      .build(),
            monitoredService.getIdentifier()));
  }

  @Override
  public void deleteByAccountIdentifier(Class<MonitoredService> clazz, String accountId) {
    List<MonitoredService> monitoredServices =
        hPersistence.createQuery(MonitoredService.class).filter(MonitoredServiceKeys.accountId, accountId).asList();
    monitoredServices.forEach(monitoredService
        -> delete(ProjectParams.builder()
                      .accountIdentifier(monitoredService.getAccountId())
                      .orgIdentifier(monitoredService.getOrgIdentifier())
                      .projectIdentifier(monitoredService.getProjectIdentifier())
                      .build(),
            monitoredService.getIdentifier()));
  }

  private void validate(MonitoredServiceDTO monitoredServiceDTO) {
    if (monitoredServiceDTO.getType().equals(MonitoredServiceType.APPLICATION)) {
      Preconditions.checkState(monitoredServiceDTO.getEnvironmentRefList().size() == 1,
          "Application monitored service cannot be attached to more than one environment");
    }
    if (monitoredServiceDTO.getSources() != null) {
      Set<String> identifiers = new HashSet<>();
      monitoredServiceDTO.getSources().getHealthSources().forEach(healthSource -> {
        if (!identifiers.add(healthSource.getIdentifier())) {
          throw new InvalidRequestException(String.format(
              "Multiple Health Sources exists with the same identifier %s", healthSource.getIdentifier()));
        }
        healthSource.getSpec().validate();
      });
    }
  }

  @Override
  public List<EnvironmentResponse> listEnvironments(String accountId, String orgIdentifier, String projectIdentifier) {
    List<String> environmentIdentifiers = hPersistence.createQuery(MonitoredService.class)
                                              .filter(MonitoredServiceKeys.accountId, accountId)
                                              .filter(MonitoredServiceKeys.orgIdentifier, orgIdentifier)
                                              .filter(MonitoredServiceKeys.projectIdentifier, projectIdentifier)
                                              .asList()
                                              .stream()
                                              .map(monitoredService -> monitoredService.getEnvironmentIdentifier())
                                              .collect(Collectors.toList());

    return nextGenService.listEnvironment(accountId, orgIdentifier, projectIdentifier, environmentIdentifiers)
        .stream()
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public MonitoredServiceResponse createDefault(
      ProjectParams projectParams, String serviceIdentifier, String environmentIdentifier) {
    String identifier = serviceIdentifier + "_" + environmentIdentifier;
    identifier = identifier.substring(0, Math.min(identifier.length(), 64));
    MonitoredServiceDTO monitoredServiceDTO = MonitoredServiceDTO.builder()
                                                  .name(identifier)
                                                  .identifier(identifier)
                                                  .orgIdentifier(projectParams.getOrgIdentifier())
                                                  .projectIdentifier(projectParams.getProjectIdentifier())
                                                  .serviceRef(serviceIdentifier)
                                                  .environmentRef(environmentIdentifier)
                                                  .type(MonitoredServiceType.APPLICATION)
                                                  .description("Default Monitored Service")
                                                  .sources(Sources.builder().build())
                                                  .dependencies(new HashSet<>())
                                                  .build();
    try {
      create(projectParams.getAccountIdentifier(), monitoredServiceDTO);
    } catch (DuplicateKeyException e) {
      identifier = identifier.substring(0, Math.min(identifier.length(), 57));
      monitoredServiceDTO.setIdentifier(identifier + "_" + RandomStringUtils.randomAlphanumeric(7));
      create(projectParams.getAccountIdentifier(), monitoredServiceDTO);
    }
    return get(projectParams, monitoredServiceDTO.getIdentifier());
  }

  private MonitoredServiceListItemDTOBuilder toMonitorServiceListDTO(MonitoredService monitoredService) {
    return MonitoredServiceListItemDTO.builder()
        .name(monitoredService.getName())
        .identifier(monitoredService.getIdentifier())
        .serviceRef(monitoredService.getServiceIdentifier())
        .environmentRef(monitoredService.getEnvironmentIdentifier())
        .environmentRefList(monitoredService.getEnvironmentIdentifierList())
        .healthMonitoringEnabled(monitoredService.isEnabled())
        .tags(TagMapper.convertToMap(monitoredService.getTags()))
        .type(monitoredService.getType());
  }

  @Override
  public HealthMonitoringFlagResponse setHealthMonitoringFlag(
      ProjectParams projectParams, String identifier, boolean enable) {
    MonitoredService monitoredService = getMonitoredService(projectParams, identifier);
    Preconditions.checkNotNull(monitoredService, "Monitored service with identifier %s does not exists", identifier);
    healthSourceService.setHealthMonitoringFlag(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
        projectParams.getProjectIdentifier(), monitoredService.getIdentifier(),
        monitoredService.getHealthSourceIdentifiers(), enable);
    hPersistence.update(
        hPersistence.createQuery(MonitoredService.class).filter(MonitoredServiceKeys.uuid, monitoredService.getUuid()),
        hPersistence.createUpdateOperations(MonitoredService.class).set(MonitoredServiceKeys.enabled, enable));
    // TODO: handle race condition on same version update. Probably by using version annotation and throwing exception
    return HealthMonitoringFlagResponse.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(identifier)
        .healthMonitoringEnabled(enable)
        .build();
  }

  @Override
  public HistoricalTrend getOverAllHealthScore(
      ProjectParams projectParams, String identifier, DurationDTO duration, Instant endTime) {
    MonitoredService monitoredService = getMonitoredService(projectParams, identifier);
    return getMonitoredServiceHistorialTrend(monitoredService, projectParams, duration, endTime);
  }

  @Override
  public HealthScoreDTO getCurrentAndDependentServicesScore(MonitoredServiceParams monitoredServiceParams) {
    MonitoredService monitoredService = getMonitoredService(monitoredServiceParams);
    return getCurrentAndDependentServicesScore(monitoredServiceParams, monitoredService);
  }

  public HealthScoreDTO getCurrentAndDependentServicesScore(
      ProjectParams projectParams, MonitoredService monitoredService) {
    List<String> monitoredServiceIdentifiers = new ArrayList<>(Arrays.asList(monitoredService.getIdentifier()));
    Set<ServiceDependencyDTO> dependentServiceDTOS = serviceDependencyService.getDependentServicesForMonitoredService(
        projectParams, monitoredService.getIdentifier());
    dependentServiceDTOS.forEach(dependentServiceDTO -> {
      MonitoredService dependentMonitoredService =
          getMonitoredService(projectParams, dependentServiceDTO.getMonitoredServiceIdentifier());
      monitoredServiceIdentifiers.add(dependentMonitoredService.getIdentifier());
    });
    List<RiskData> allServiceRiskScoreList =
        heatMapService.getLatestRiskScoreForAllServicesList(projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), monitoredServiceIdentifiers);
    List<RiskData> dependentRiskScoreList = allServiceRiskScoreList.subList(1, allServiceRiskScoreList.size());
    RiskData minDependentRiskScore = null;
    if (!dependentRiskScoreList.isEmpty()) {
      List<RiskData> filteredRiskScoreList = dependentRiskScoreList.stream()
                                                 .filter(r -> r.getHealthScore() != null && r.getHealthScore() >= 0)
                                                 .collect(Collectors.toList());
      if (filteredRiskScoreList.isEmpty()) {
        minDependentRiskScore = dependentRiskScoreList.contains(RiskData.builder().riskStatus(Risk.NO_ANALYSIS).build())
            ? RiskData.builder().riskStatus(Risk.NO_ANALYSIS).build()
            : RiskData.builder().riskStatus(Risk.NO_DATA).build();
      } else {
        minDependentRiskScore = Collections.min(filteredRiskScoreList, Comparator.comparing(RiskData::getHealthScore));
      }
    }

    return HealthScoreDTO.builder()
        .currentHealthScore(allServiceRiskScoreList.get(0))
        .dependentHealthScore(minDependentRiskScore)
        .build();
  }

  public String getYamlTemplate(ProjectParams projectParams, MonitoredServiceType type) {
    // returning default yaml template, account/org/project specific templates can be generated later.
    String defaultTemplate = type == null ? MONITORED_SERVICE_YAML_TEMPLATE.get(MonitoredServiceType.APPLICATION)
                                          : MONITORED_SERVICE_YAML_TEMPLATE.get(type);
    return StringUtils.replaceEach(defaultTemplate, new String[] {"$projectIdentifier", "$orgIdentifier"},
        new String[] {projectParams.getProjectIdentifier(), projectParams.getOrgIdentifier()});
  }

  @Override
  public List<HealthSourceDTO> getHealthSources(ServiceEnvironmentParams serviceEnvironmentParams) {
    MonitoredService monitoredServiceEntity = getMonitoredService(serviceEnvironmentParams);
    return getHealthSources(monitoredServiceEntity);
  }

  @Override
  public List<HealthSourceDTO> getHealthSources(ProjectParams projectParams, String monitoredServiceIdentifier) {
    MonitoredService monitoredServiceEntity = getMonitoredService(projectParams, monitoredServiceIdentifier);
    return getHealthSources(monitoredServiceEntity);
  }

  private List<HealthSourceDTO> getHealthSources(MonitoredService monitoredServiceEntity) {
    Set<HealthSource> healthSources = healthSourceService.get(monitoredServiceEntity.getAccountId(),
        monitoredServiceEntity.getOrgIdentifier(), monitoredServiceEntity.getProjectIdentifier(),
        monitoredServiceEntity.getIdentifier(), monitoredServiceEntity.getHealthSourceIdentifiers());
    return healthSources.stream()
        .peek(healthSource
            -> healthSource.setIdentifier(HealthSourceService.getNameSpacedIdentifier(
                monitoredServiceEntity.getIdentifier(), healthSource.getIdentifier())))
        .map(healthSource -> HealthSourceDTO.toHealthSourceDTO(healthSource))
        .collect(Collectors.toList());
  }

  @Override
  public AnomaliesSummaryDTO getAnomaliesSummary(
      ProjectParams projectParams, String monitoredServiceIdentifier, TimeRangeParams timeRangeParams) {
    MonitoredService monitoredService = getMonitoredService(projectParams, monitoredServiceIdentifier);
    if (monitoredService == null) {
      throw new InvalidRequestException(
          String.format("Monitored Service not found for identifier %s", monitoredServiceIdentifier));
    }
    ServiceEnvironmentParams serviceEnvironmentParams =
        ServiceEnvironmentParams.builderWithProjectParams(projectParams)
            .serviceIdentifier(monitoredService.getServiceIdentifier())
            .environmentIdentifier(monitoredService.getEnvironmentIdentifier())
            .build();
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builderWithServiceEnvParams(serviceEnvironmentParams)
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .clusterTypes(Arrays.asList(LogAnalysisTag.UNKNOWN, LogAnalysisTag.UNEXPECTED))
            .build();
    long logAnomalousCount = logDashboardService
                                 .getAllLogsData(MonitoredServiceParams.builder()
                                                     .accountIdentifier(projectParams.getAccountIdentifier())
                                                     .projectIdentifier(projectParams.getProjectIdentifier())
                                                     .orgIdentifier(projectParams.getOrgIdentifier())
                                                     .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                     .build(),
                                     timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams)
                                 .getTotalItems();
    TimeSeriesAnalysisFilter timeSeriesAnalysisFilter =
        TimeSeriesAnalysisFilter.builder().anomalousMetricsOnly(true).build();
    long timeSeriesAnomalousCount =
        timeSeriesDashboardService
            .getTimeSeriesMetricData(monitoredServiceParams, timeRangeParams, timeSeriesAnalysisFilter, pageParams)
            .getTotalItems();
    return AnomaliesSummaryDTO.builder()
        .logsAnomalies(logAnomalousCount)
        .timeSeriesAnomalies(timeSeriesAnomalousCount)
        .build();
  }

  @Override
  public CountServiceDTO getCountOfServices(ProjectParams projectParams, String environmentIdentifier, String filter) {
    List<MonitoredService> allMonitoredServices = getMonitoredServices(projectParams, environmentIdentifier, filter);
    Map<String, RiskData> latestRiskScoreByServiceMap =
        getLatestRiskScoreByServiceMap(projectParams, allMonitoredServices);
    List<MonitoredService> monitoredServicesAtRisk =
        getMonitoredServicesAtRisk(allMonitoredServices, latestRiskScoreByServiceMap, true);

    return CountServiceDTO.builder()
        .allServicesCount(allMonitoredServices.size())
        .servicesAtRiskCount(monitoredServicesAtRisk.size())
        .build();
  }

  @Override
  public List<MetricDTO> getSloMetrics(
      ProjectParams projectParams, String monitoredServiceIdentifier, String healthSourceIdentifier) {
    Set<HealthSource> healthSources =
        healthSourceService.get(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), monitoredServiceIdentifier, Arrays.asList(healthSourceIdentifier));
    return healthSources.stream()
        .map(HealthSource::getSpec)
        .filter(spec -> spec instanceof MetricHealthSourceSpec)
        .map(spec -> (MetricHealthSourceSpec) spec)
        .filter(spec -> isNotEmpty(spec.getMetricDefinitions()))
        .flatMap(spec -> spec.getMetricDefinitions().stream())
        .filter(metricDefinition -> metricDefinition.getSli().getEnabled())
        .map(metricDefinition
            -> MetricDTO.builder()
                   .metricName(metricDefinition.getMetricName())
                   .identifier(metricDefinition.getIdentifier())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public MonitoredServiceListItemDTO getMonitoredServiceDetails(MonitoredServiceParams monitoredServiceParams) {
    MonitoredService monitoredService = getMonitoredService(monitoredServiceParams);
    return getMonitoredServiceDetails(monitoredServiceParams, monitoredService);
  }
  public MonitoredServiceListItemDTO getMonitoredServiceDetails(
      ProjectParams projectParams, MonitoredService monitoredService) {
    Preconditions.checkNotNull(monitoredService, "Monitored service does not exists");
    MonitoredServiceListItemDTOBuilder monitoredServiceListItemDTOBuilder = toMonitorServiceListDTO(monitoredService);
    Set<ServiceDependencyDTO> dependentServiceDTOs = serviceDependencyService.getDependentServicesForMonitoredService(
        projectParams, monitoredService.getIdentifier());
    List<String> dependentServices = new ArrayList<>();
    dependentServiceDTOs.forEach(x -> dependentServices.add(x.getMonitoredServiceIdentifier()));
    List<MonitoredService> allMonitoredServices = getMonitoredServices(projectParams);
    Map<String, RiskData> latestRiskScoreByServiceMap =
        getLatestRiskScoreByServiceMap(projectParams, allMonitoredServices);

    String serviceName =
        nextGenService
            .listService(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                projectParams.getProjectIdentifier(), Arrays.asList(monitoredService.getServiceIdentifier()))
            .get(0)
            .getService()
            .getName();
    String environmentName =
        nextGenService
            .listEnvironment(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                projectParams.getProjectIdentifier(),
                Arrays.asList(monitoredService.getEnvironmentIdentifier())) // TODO: check if this is needed.
            .get(0)
            .getEnvironment()
            .getName();
    List<HistoricalTrend> historicalTrendList =
        heatMapService.getHistoricalTrend(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), Arrays.asList(monitoredService.getIdentifier()), 24);
    RiskData monitoredServiceRiskScore = latestRiskScoreByServiceMap.get(monitoredService.getIdentifier());
    List<RiskData> dependentServiceRiskScoreList =
        getSortedDependentServiceRiskScoreList(projectParams, dependentServices);
    ChangeSummaryDTO changeSummary =
        changeSourceService.getChangeSummary(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                 .monitoredServiceIdentifier(monitoredService.getIdentifier())
                                                 .build(),
            monitoredService.getChangeSourceIdentifiers(), clock.instant().minus(Duration.ofDays(1)), clock.instant());
    Map<String, List<SloHealthIndicatorDTO>> sloHealthIndicatorDTOMap =
        getSloHealthIndicators(projectParams, Collections.singletonList(monitoredService.getIdentifier()));
    return monitoredServiceListItemDTOBuilder.historicalTrend(historicalTrendList.get(0))
        .currentHealthScore(monitoredServiceRiskScore)
        .dependentHealthScore(dependentServiceRiskScoreList)
        .sloHealthIndicators(sloHealthIndicatorDTOMap.get(monitoredService.getIdentifier()))
        .serviceName(serviceName)
        .environmentName(environmentName)
        .changeSummary(changeSummary)
        .build();
  }
  @Override
  public MonitoredServiceListItemDTO getMonitoredServiceDetails(ServiceEnvironmentParams serviceEnvironmentParams) {
    MonitoredService monitoredService = getMonitoredService(serviceEnvironmentParams);
    return getMonitoredServiceDetails(serviceEnvironmentParams, monitoredService);
  }

  @Override
  public List<String> getMonitoredServiceIdentifiers(
      ProjectParams projectParams, List<String> services, List<String> environments) {
    Query<MonitoredService> query =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier());
    if (isNotEmpty(services)) {
      query = query.field(MonitoredServiceKeys.serviceIdentifier).in(services);
    }
    if (isNotEmpty(environments)) {
      query = query.field(MonitoredServiceKeys.environmentIdentifierList).hasAnyOf(environments);
    }
    return query.asList().stream().map(MonitoredService::getIdentifier).collect(Collectors.toList());
  }

  private Map<String, List<SloHealthIndicatorDTO>> getSloHealthIndicators(
      ProjectParams projectParams, List<String> monitoredServiceIdentifiers) {
    Map<String, List<SloHealthIndicatorDTO>> sloHealthIndicatorDTOMap = new HashMap<>();
    if (isEmpty(monitoredServiceIdentifiers)) {
      return sloHealthIndicatorDTOMap;
    }
    List<SLOHealthIndicator> sloHealthIndicatorList =
        sloHealthIndicatorService.getByMonitoredServiceIdentifiers(projectParams, monitoredServiceIdentifiers);
    for (SLOHealthIndicator sloHealthIndicator : sloHealthIndicatorList) {
      List<SloHealthIndicatorDTO> sloHealthIndicatorDTOList =
          sloHealthIndicatorDTOMap.getOrDefault(sloHealthIndicator.getMonitoredServiceIdentifier(), new ArrayList<>());
      sloHealthIndicatorDTOList.add(
          SloHealthIndicatorDTO.builder()
              .errorBudgetRemainingPercentage(sloHealthIndicator.getErrorBudgetRemainingPercentage())
              .errorBudgetRisk(sloHealthIndicator.getErrorBudgetRisk())
              .serviceLevelObjectiveIdentifier(sloHealthIndicator.getServiceLevelObjectiveIdentifier())
              .build());
      sloHealthIndicatorDTOMap.put(sloHealthIndicator.getMonitoredServiceIdentifier(), sloHealthIndicatorDTOList);
    }
    return sloHealthIndicatorDTOMap;
  }

  @Override
  public PageResponse<CVNGLogDTO> getCVNGLogs(MonitoredServiceParams monitoredServiceParams,
      LiveMonitoringLogsFilter liveMonitoringLogsFilter, PageParams pageParams) {
    MonitoredService monitoredService = getMonitoredService(monitoredServiceParams);
    if (Objects.isNull(monitoredService)) {
      throw new NotFoundException("Monitored Service with identifier "
          + monitoredServiceParams.getMonitoredServiceIdentifier() + " not found.");
    }
    List<CVConfig> cvConfigs;
    if (liveMonitoringLogsFilter.filterByHealthSourceIdentifiers()) {
      cvConfigs = cvConfigService.list(monitoredServiceParams, liveMonitoringLogsFilter.getHealthSourceIdentifiers());
    } else {
      cvConfigs = cvConfigService.list(monitoredServiceParams);
    }
    List<String> cvConfigIds = cvConfigs.stream().map(CVConfig::getUuid).collect(Collectors.toList());
    List<String> verificationTaskIds = verificationTaskService.getServiceGuardVerificationTaskIds(
        monitoredServiceParams.getAccountIdentifier(), cvConfigIds);
    return cvngLogService.getCVNGLogs(
        monitoredServiceParams.getAccountIdentifier(), verificationTaskIds, liveMonitoringLogsFilter, pageParams);
  }

  @Override
  public List<MonitoredServiceChangeDetailSLO> getMonitoredServiceChangeDetails(
      ProjectParams projectParams, String monitoredServiceIdentifier, Long startTime, Long endTime) {
    List<ServiceLevelObjective> serviceLevelObjectiveList =
        serviceLevelObjectiveService.getByMonitoredServiceIdentifier(projectParams, monitoredServiceIdentifier);

    List<MonitoredServiceChangeDetailSLO> monitoredServiceChangeDetailSLOS = new ArrayList<>();

    for (ServiceLevelObjective serviceLevelObjective : serviceLevelObjectiveList) {
      LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
      ServiceLevelObjective.TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
      Boolean outOfRange = false;
      if (!Objects.isNull(startTime) && !Objects.isNull(endTime)) {
        if ((startTime > timePeriod.getEndTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
            || endTime < timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()).toEpochMilli()) {
          outOfRange = true;
        }
      }

      monitoredServiceChangeDetailSLOS.add(MonitoredServiceChangeDetailSLO.builder()
                                               .identifier(serviceLevelObjective.getIdentifier())
                                               .name(serviceLevelObjective.getName())
                                               .outOfRange(outOfRange)
                                               .build());
    }

    return monitoredServiceChangeDetailSLOS;
  }

  @VisibleForTesting
  List<NotificationRule> getNotificationRules(MonitoredService monitoredService) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(monitoredService.getAccountId())
                                      .orgIdentifier(monitoredService.getOrgIdentifier())
                                      .projectIdentifier(monitoredService.getProjectIdentifier())
                                      .build();
    List<String> notificationRuleRefs = monitoredService.getNotificationRuleRefs()
                                            .stream()
                                            .filter(ref -> ref.isEligible(clock.instant(), COOL_OFF_DURATION))
                                            .map(NotificationRuleRef::getNotificationRuleRef)
                                            .collect(Collectors.toList());
    return notificationRuleService.getEntities(projectParams, notificationRuleRefs);
  }

  @Override
  public void sendNotification(MonitoredService monitoredService) {
    List<NotificationRule> notificationRules = getNotificationRules(monitoredService);
    Map<String, String> templateData =
        getNotificationTemplateData(ProjectParams.builder()
                                        .accountIdentifier(monitoredService.getAccountId())
                                        .orgIdentifier(monitoredService.getOrgIdentifier())
                                        .projectIdentifier(monitoredService.getProjectIdentifier())
                                        .build(),
            templateIdentifierName, monitoredService.getName());
    Set<String> notificationRuleRefsWithChange = new HashSet<>();

    for (NotificationRule notificationRule : notificationRules) {
      List<MonitoredServiceNotificationRuleCondition> conditions =
          ((MonitoredServiceNotificationRule) notificationRule).getConditions();
      for (MonitoredServiceNotificationRuleCondition condition : conditions) {
        if (shouldSendNotification(monitoredService, condition)) {
          CVNGNotificationChannel notificationChannel = notificationRule.getNotificationMethod();
          String templateId = getNotificationTemplateId(notificationRule.getType(), notificationChannel.getType());
          NotificationResult notificationResult =
              notificationClient.sendNotificationAsync(notificationChannel.getSpec().toNotificationChannel(
                  monitoredService.getAccountId(), monitoredService.getOrgIdentifier(),
                  monitoredService.getProjectIdentifier(), templateId, templateData));
          log.info("Notification with Notification ID {} sent", notificationResult.getNotificationId());
          notificationRuleRefsWithChange.add(notificationRule.getIdentifier());
        }
      }
    }
    updateNotificationRuleRefInMonitoredService(monitoredService, new ArrayList<>(notificationRuleRefsWithChange));
  }

  @Override
  public PageResponse<NotificationRuleResponse> getNotificationRules(
      ProjectParams projectParams, String monitoredServiceIdentifier, PageParams pageParams) {
    MonitoredService monitoredService =
        getMonitoredService(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                .build());
    List<NotificationRuleRef> notificationRuleRefList = monitoredService.getNotificationRuleRefs();
    List<NotificationRuleResponse> notificationRuleResponseList =
        notificationRuleService.getNotificationRuleResponse(projectParams, notificationRuleRefList);
    PageResponse<NotificationRuleResponse> notificationRulePageResponse =
        PageUtils.offsetAndLimit(notificationRuleResponseList, pageParams.getPage(), pageParams.getSize());

    return PageResponse.<NotificationRuleResponse>builder()
        .pageSize(pageParams.getSize())
        .pageIndex(pageParams.getPage())
        .totalPages(notificationRulePageResponse.getTotalPages())
        .totalItems(notificationRulePageResponse.getTotalItems())
        .pageItemCount(notificationRulePageResponse.getPageItemCount())
        .content(notificationRulePageResponse.getContent())
        .build();
  }

  private void updateNotificationRuleRefInMonitoredService(
      MonitoredService monitoredService, List<String> notificationRuleRefs) {
    List<NotificationRuleRef> allNotificationRuleRefs = new ArrayList<>();
    List<NotificationRuleRef> notificationRuleRefsWithoutChange =
        monitoredService.getNotificationRuleRefs()
            .stream()
            .filter(notificationRuleRef -> !notificationRuleRefs.contains(notificationRuleRef.getNotificationRuleRef()))
            .collect(Collectors.toList());
    List<NotificationRuleRefDTO> notificationRuleRefDTOs =
        notificationRuleRefs.stream()
            .map(notificationRuleRef
                -> NotificationRuleRefDTO.builder().notificationRuleRef(notificationRuleRef).enabled(true).build())
            .collect(Collectors.toList());
    List<NotificationRuleRef> notificationRuleRefsWithChange =
        notificationRuleService.getNotificationRuleRefs(notificationRuleRefDTOs);
    allNotificationRuleRefs.addAll(notificationRuleRefsWithChange);
    allNotificationRuleRefs.addAll(notificationRuleRefsWithoutChange);
    UpdateOperations<MonitoredService> updateOperations = hPersistence.createUpdateOperations(MonitoredService.class);
    updateOperations.set(MonitoredServiceKeys.notificationRuleRefs, allNotificationRuleRefs);

    hPersistence.update(monitoredService, updateOperations);
  }

  @VisibleForTesting
  boolean shouldSendNotification(
      MonitoredService monitoredService, MonitoredServiceNotificationRuleCondition condition) {
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builder()
                                                        .accountIdentifier(monitoredService.getAccountId())
                                                        .orgIdentifier(monitoredService.getOrgIdentifier())
                                                        .projectIdentifier(monitoredService.getProjectIdentifier())
                                                        .monitoredServiceIdentifier(monitoredService.getIdentifier())
                                                        .build();
    switch (condition.getType()) {
      case HEALTH_SCORE:
        MonitoredServiceHealthScoreCondition healthScoreCondition = (MonitoredServiceHealthScoreCondition) condition;
        return heatMapService.isEveryHeatMapBelowThresholdForRiskTimeBuffer(monitoredServiceParams,
            monitoredService.getIdentifier(), healthScoreCondition.getThreshold(), healthScoreCondition.getPeriod());
      case CHANGE_OBSERVED:
        MonitoredServiceChangeObservedCondition changeObservedCondition =
            (MonitoredServiceChangeObservedCondition) condition;
        List<ActivityType> changeObservedActivityTypes = new ArrayList<>();
        changeObservedCondition.getChangeEventTypes().forEach(
            changeEventType -> changeObservedActivityTypes.addAll(changeEventType.getActivityTypes()));
        return activityService
            .getAnyEventFromListOfActivityTypes(monitoredServiceParams, changeObservedActivityTypes,
                clock.instant().minus(10, ChronoUnit.MINUTES), clock.instant())
            .isPresent();
      case CHANGE_IMPACT:
        MonitoredServiceChangeImpactCondition changeImpactCondition = (MonitoredServiceChangeImpactCondition) condition;
        List<ActivityType> changeImpactActivityTypes = new ArrayList<>();
        changeImpactCondition.getChangeEventTypes().forEach(
            changeEventType -> changeImpactActivityTypes.addAll(changeEventType.getActivityTypes()));
        Optional<Activity> optionalActivity =
            activityService.getAnyEventFromListOfActivityTypes(monitoredServiceParams, changeImpactActivityTypes,
                clock.instant().minus(changeImpactCondition.getPeriod(), ChronoUnit.MILLIS), clock.instant());
        if (optionalActivity.isPresent()) {
          Instant activityStartTime = optionalActivity.get().getActivityStartTime();
          long riskTimeBufferMins = Duration.between(activityStartTime, clock.instant()).toMinutes();
          return heatMapService.isEveryHeatMapBelowThresholdForRiskTimeBuffer(monitoredServiceParams,
              monitoredService.getIdentifier(), changeImpactCondition.getThreshold(), riskTimeBufferMins);
        } else {
          return false;
        }
      default:
        return false;
    }
  }
}
