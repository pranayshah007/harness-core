/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.beans.params.ServiceEnvironmentParams.builderWithProjectParams;
import static io.harness.cvng.core.constant.MonitoredServiceConstants.REGULAR_EXPRESSION;
import static io.harness.cvng.core.utils.FeatureFlagNames.SRM_CODE_ERROR_NOTIFICATIONS;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.ENVIRONMENT_NAME;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.NOTIFICATION_NAME;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.NOTIFICATION_URL;
import static io.harness.cvng.notification.utils.ErrorTrackingNotificationRuleUtils.buildMonitoredServiceConfigurationTabUrl;
import static io.harness.cvng.notification.utils.ErrorTrackingNotificationRuleUtils.getCodeErrorTemplateData;
import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.getDurationInSeconds;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CHANGE_EVENT_TYPE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.COOL_OFF_DURATION;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_HEALTH_SCORE;
import static io.harness.data.structure.CollectionUtils.distinctByKey;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ModuleType;
import io.harness.beans.FeatureName;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.client.ErrorTrackingService;
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
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceWithHealthSources;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceWithHealthSources.HealthSourceSummary;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceYamlDTO;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.monitoredService.SloHealthIndicatorDTO;
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
import io.harness.cvng.core.entities.EntityDisableTime;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.core.handler.monitoredService.BaseMonitoredServiceHandler;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.SetupUsageEventService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.cvng.core.utils.FeatureFlagNames;
import io.harness.cvng.core.utils.template.MonitoredServiceValidator;
import io.harness.cvng.core.utils.template.MonitoredServiceYamlExpressionEvaluator;
import io.harness.cvng.core.utils.template.TemplateFacade;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.dashboard.services.api.LogDashboardService;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.cvng.events.monitoredservice.MonitoredServiceCreateEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceDeleteEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceToggleEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceUpdateEvent;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceHealthScoreCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.entities.NotificationRule.CVNGNotificationChannel;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator.NotificationData;
import io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.usage.impl.ActiveServiceMonitoredDTO;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.PageUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import io.fabric8.utils.Lists;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;

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
  @Inject private ErrorTrackingService errorTrackingService;
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
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private NotificationClient notificationClient;
  @Inject private ActivityService activityService;
  @Inject private OutboxService outboxService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private EnforcementClientService enforcementClientService;
  @Inject private FeatureFlagService featureFlagService;

  @Inject NgLicenseHttpClient ngLicenseHttpClient;
  @Inject
  private Map<NotificationRuleConditionType, NotificationRuleTemplateDataGenerator>
      notificationRuleConditionTypeTemplateDataGeneratorMap;

  @Inject private EntityDisabledTimeService entityDisabledTimeService;

  @Override
  public MonitoredServiceResponse create(String accountId, MonitoredServiceDTO monitoredServiceDTO) {
    ServiceEnvironmentParams environmentParams = ServiceEnvironmentParams.builder()
                                                     .accountIdentifier(accountId)
                                                     .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
                                                     .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
                                                     .serviceIdentifier(monitoredServiceDTO.getServiceRef())
                                                     .environmentIdentifier(monitoredServiceDTO.getEnvironmentRef())
                                                     .build();

    validate(monitoredServiceDTO, accountId);
    filterOutHarnessCDChangeSource(monitoredServiceDTO);
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
    outboxService.save(MonitoredServiceCreateEvent.builder()
                           .resourceName(monitoredServiceDTO.getName())
                           .newMonitoredServiceYamlDTO(
                               MonitoredServiceYamlDTO.builder().monitoredServiceDTO(monitoredServiceDTO).build())
                           .accountIdentifier(accountId)
                           .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                           .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
                           .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
                           .build());
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
  @Override
  public MonitoredServiceDTO getExpandedMonitoredServiceFromYaml(ProjectParams projectParams, String yaml) {
    String templateResolvedYaml = templateFacade.resolveYaml(projectParams, yaml);
    MonitoredServiceYamlExpressionEvaluator yamlExpressionEvaluator =
        new MonitoredServiceYamlExpressionEvaluator(templateResolvedYaml);
    templateResolvedYaml = sanitizeTemplateYaml(templateResolvedYaml);
    MonitoredServiceDTO monitoredServiceDTO =
        YamlUtils.read(templateResolvedYaml, MonitoredServiceYamlDTO.class).getMonitoredServiceDTO();
    monitoredServiceDTO = (MonitoredServiceDTO) yamlExpressionEvaluator.resolve(monitoredServiceDTO, false);
    monitoredServiceDTO.setProjectIdentifier(projectParams.getProjectIdentifier());
    monitoredServiceDTO.setOrgIdentifier(projectParams.getOrgIdentifier());
    MonitoredServiceValidator.validateMSDTO(monitoredServiceDTO);
    return monitoredServiceDTO;
  }

  private String sanitizeTemplateYaml(String templateResolvedYaml) throws IOException {
    YamlField rootYamlNode = YamlUtils.readTree(templateResolvedYaml);
    JsonNode rootNode = rootYamlNode.getNode().getCurrJsonNode();
    ObjectNode monitoredService = (ObjectNode) rootNode.get("monitoredService");
    monitoredService.put("identifier", REGULAR_EXPRESSION);
    monitoredService.put("name", REGULAR_EXPRESSION);
    return YamlUtils.writeYamlString(rootYamlNode);
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
    validate(monitoredServiceDTO, accountId);
    filterOutHarnessCDChangeSource(monitoredServiceDTO);

    updateHealthSources(monitoredService, monitoredServiceDTO);
    changeSourceService.update(MonitoredServiceParams.builderWithProjectParams(environmentParams)
                                   .monitoredServiceIdentifier(monitoredService.getIdentifier())
                                   .build(),
        monitoredServiceDTO.getSources().getChangeSources());
    updateMonitoredService(monitoredService, monitoredServiceDTO);
    outboxService.save(
        MonitoredServiceUpdateEvent.builder()
            .resourceName(monitoredServiceDTO.getName())
            .oldMonitoredServiceYamlDTO(
                MonitoredServiceYamlDTO.builder().monitoredServiceDTO(existingMonitoredServiceDTO).build())
            .newMonitoredServiceYamlDTO(
                MonitoredServiceYamlDTO.builder().monitoredServiceDTO(monitoredServiceDTO).build())
            .accountIdentifier(accountId)
            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
            .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
            .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
            .build());
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
        getNotificationRuleRefs(projectParams, monitoredService, monitoredServiceDTO));
    updateOperations.set(MonitoredServiceKeys.lastUpdatedAt, clock.millis());
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
    MonitoredServiceDTO monitoredServiceDTO =
        createMonitoredServiceDTOFromEntity(monitoredService, environmentParams).getMonitoredServiceDTO();
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
      outboxService.save(MonitoredServiceDeleteEvent.builder()
                             .resourceName(monitoredService.getName())
                             .oldMonitoredServiceYamlDTO(
                                 MonitoredServiceYamlDTO.builder().monitoredServiceDTO(monitoredServiceDTO).build())
                             .monitoredServiceIdentifier(monitoredService.getIdentifier())
                             .accountIdentifier(monitoredService.getAccountId())
                             .orgIdentifier(monitoredService.getOrgIdentifier())
                             .projectIdentifier(monitoredService.getProjectIdentifier())
                             .build());
      setupUsageEventService.sendDeleteEventsForMonitoredService(projectParams, monitoredService);
      activityService.deleteByMonitoredServiceIdentifier(monitoredServiceParams);
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
  public List<MonitoredServiceDetail> getMonitoredServiceDetails(ProjectParams projectParams, Set<String> identifier) {
    List<MonitoredService> monitoredServices =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .field(MonitoredServiceKeys.identifier)
            .in(identifier)
            .asList();
    return getMonitoredServiceDetails(projectParams, monitoredServices);
  }

  @Override
  public List<MonitoredServiceDetail> getAllMonitoredServiceDetails(ProjectParams projectParams) {
    List<MonitoredService> monitoredServices =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .asList();
    return getMonitoredServiceDetails(projectParams, monitoredServices);
  }

  private List<MonitoredServiceDetail> getMonitoredServiceDetails(
      ProjectParams projectParams, List<MonitoredService> monitoredServices) {
    List<MonitoredServiceDetail> monitoredServiceDetails = new ArrayList<>();
    Set<String> environmentIdentifiers = new HashSet<>();
    Set<String> serviceIdentifiers = new HashSet<>();
    monitoredServices.forEach(monitoredService -> {
      environmentIdentifiers.add(monitoredService.getEnvironmentIdentifier());
      serviceIdentifiers.add(monitoredService.getServiceIdentifier());
    });
    Map<String, String> environmentIdNameMap =
        nextGenService.getEnvironmentIdNameMap(projectParams, new ArrayList<>(environmentIdentifiers));
    Map<String, String> serviceIdNameMap =
        nextGenService.getServiceIdNameMap(projectParams, new ArrayList<>(serviceIdentifiers));
    monitoredServices.forEach(monitoredService -> {
      monitoredServiceDetails.add(
          MonitoredServiceDetail.builder()
              .monitoredServiceIdentifier(monitoredService.getIdentifier())
              .monitoredServiceName(monitoredService.getName())
              .serviceName(serviceIdNameMap.get(monitoredService.getServiceIdentifier()))
              .serviceIdentifier(monitoredService.getServiceIdentifier())
              .environmentName(environmentIdNameMap.get(monitoredService.getEnvironmentIdentifier()))
              .environmentIdentifier(monitoredService.getEnvironmentIdentifier())
              .projectParams(projectParams)
              .build());
    });
    return monitoredServiceDetails;
  }
  @Override
  public List<MonitoredServiceResponse> get(String accountId, Set<String> identifierSet) {
    List<MonitoredService> monitoredServices = hPersistence.createQuery(MonitoredService.class)
                                                   .filter(MonitoredServiceKeys.accountId, accountId)
                                                   .field(MonitoredServiceKeys.identifier)
                                                   .in(identifierSet)
                                                   .asList();
    List<MonitoredServiceResponse> monitoredServiceResponseList = new ArrayList<>();
    monitoredServices.forEach(monitoredService -> {
      ServiceEnvironmentParams environmentParams =
          builderWithProjectParams(ProjectParams.builder()
                                       .accountIdentifier(monitoredService.getAccountId())
                                       .orgIdentifier(monitoredService.getOrgIdentifier())
                                       .projectIdentifier(monitoredService.getProjectIdentifier())
                                       .build())
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
            .enabled(monitoredServiceEntity.isEnabled())
            .build();
    return MonitoredServiceResponse.builder()
        .monitoredService(monitoredServiceDTO)
        .createdAt(monitoredServiceEntity.getCreatedAt())
        .lastModifiedAt(monitoredServiceEntity.getLastUpdatedAt())
        .build();
  }
  @Override
  public MonitoredServiceResponse getApplicationMonitoredServiceResponse(
      ServiceEnvironmentParams serviceEnvironmentParams) {
    Optional<MonitoredService> monitoredService = getApplicationMonitoredService(serviceEnvironmentParams);
    if (monitoredService.isPresent()) {
      return get(serviceEnvironmentParams, monitoredService.get().getIdentifier());
    } else {
      return null;
    }
  }

  private MonitoredServiceResponse getMonitoredServiceResponse(MonitoredServiceParams monitoredServiceParams) {
    MonitoredService monitoredService = getMonitoredService(monitoredServiceParams);
    if (Objects.nonNull(monitoredService)) {
      return get(monitoredServiceParams, monitoredService.getIdentifier());
    } else {
      return null;
    }
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
                      .enabled(monitoredServiceEntity.isEnabled())
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
    MonitoredServiceResponse monitoredServiceResponse = getMonitoredServiceResponse(monitoredServiceParams);
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
    Query<MonitoredService> query =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier());
    if (!Lists.isNullOrEmpty(environmentIdentifiers)) {
      query = query.field(MonitoredServiceKeys.environmentIdentifierList).hasAnyOf(environmentIdentifiers);
    }
    return query.asList();
  }

  private List<MonitoredService> getMonitoredServicesByEnvIds(
      ProjectParams projectParams, String environmentIdentifier) {
    Query<MonitoredService> monitoredServicesQuery =
        hPersistence.createQuery(MonitoredService.class)
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .order(Sort.descending(MonitoredServiceKeys.lastUpdatedAt));

    if (isNotEmpty(environmentIdentifier)) {
      monitoredServicesQuery.field(MonitoredServiceKeys.environmentIdentifierList).hasThisOne(environmentIdentifier);
    }
    return monitoredServicesQuery.asList();
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

  private List<MonitoredService> filterMonitoredService(
      List<MonitoredService> monitoredServices, Map<String, String> serviceIdNameMap, String filter) {
    return monitoredServices.stream()
        .filter(monitoredService
            -> isEmpty(filter)
                || serviceIdNameMap.get(monitoredService.getServiceIdentifier())
                       .toLowerCase()
                       .contains(filter.trim().toLowerCase())
                || monitoredService.getEnvironmentIdentifierList().stream().anyMatch(
                    env -> env.toLowerCase().contains(filter.trim().toLowerCase())))
        .collect(Collectors.toList());
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
              .field(MonitoredServiceKeys.environmentIdentifierList)
              .hasThisOne(serviceEnvironmentParams.getEnvironmentIdentifier())
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
    long currentTime = System.currentTimeMillis();
    MonitoredService monitoredServiceEntity =
        MonitoredService.builder()
            .name(monitoredServiceDTO.getName())
            .desc(monitoredServiceDTO.getDescription())
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .environmentIdentifierList(monitoredServiceDTO.getEnvironmentRefList())
            .serviceIdentifier(monitoredServiceDTO.getServiceRef())
            .identifier(monitoredServiceDTO.getIdentifier())
            .type(monitoredServiceDTO.getType())
            // SRM-10798: enabled should come from monitoredServiceDTO
            .enabled(monitoredServiceDTO.isEnabled())
            .lastDisabledAt(clock.millis())
            .tags(TagMapper.convertToList(monitoredServiceDTO.getTags()))
            .notificationRuleRefs(notificationRuleService.getNotificationRuleRefs(projectParams,
                monitoredServiceDTO.getNotificationRuleRefs(), NotificationRuleType.MONITORED_SERVICE,
                Instant.ofEpochSecond(0)))
            .createdAt(currentTime)
            .lastUpdatedAt(currentTime)
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
      query = query.field(MonitoredServiceKeys.environmentIdentifierList).hasThisOne(environmentIdentifier);
    }
    if (serviceIdentifier != null) {
      query = query.filter(MonitoredServiceKeys.serviceIdentifier, serviceIdentifier);
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

  @Override
  public List<MonitoredService> listWithFilter(
      @NonNull ProjectParams projectParams, List<String> identifiers, String filter) {
    List<MonitoredService> monitoredServices = list(projectParams, identifiers);
    if (Objects.nonNull(filter)) {
      List<String> serviceIdentifiers = new ArrayList<>();
      for (MonitoredService monitoredService : monitoredServices) {
        serviceIdentifiers.add(monitoredService.getServiceIdentifier());
      }
      Map<String, String> serviceIdNameMap =
          nextGenService.getServiceIdNameMap(projectParams, new ArrayList<>(serviceIdentifiers));
      monitoredServices = filterMonitoredService(monitoredServices, serviceIdNameMap, filter);
    }
    return monitoredServices;
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
  public PageResponse<MonitoredServiceListItemDTO> list(ProjectParams projectParams,
      List<String> environmentIdentifiers, Integer offset, Integer pageSize, String filter,
      boolean servicesAtRiskFilter) {
    List<MonitoredService> monitoredServices = getMonitoredServicesByEnvIds(projectParams, environmentIdentifiers);
    List<String> serviceIdentifiers = new ArrayList<>();
    for (MonitoredService monitoredService : monitoredServices) {
      serviceIdentifiers.add(monitoredService.getServiceIdentifier());
    }
    Map<String, String> serviceIdNameMap =
        nextGenService.getServiceIdNameMap(projectParams, new ArrayList<>(serviceIdentifiers));
    if (Objects.nonNull(filter)) {
      monitoredServices = filterMonitoredService(monitoredServices, serviceIdNameMap, filter);
    }
    Map<String, MonitoredService> idToMonitoredServiceMap =
        monitoredServices.stream().collect(Collectors.toMap(MonitoredService::getIdentifier, Function.identity()));
    Map<String, RiskData> latestRiskScoreByServiceMap =
        getLatestRiskScoreByServiceMap(projectParams, monitoredServices);
    List<MonitoredServiceListItemDTOBuilder> monitoredServiceListItemDTOS =
        getMonitoredServicesAtRisk(monitoredServices, latestRiskScoreByServiceMap, servicesAtRiskFilter)
            .stream()
            .map(monitoredService -> toMonitorServiceListDTO(monitoredService))
            .collect(Collectors.toList());
    Set<String> enabledServices = getEnabledMonitoredServices(projectParams)
                                      .stream()
                                      .filter(distinctByKey(x -> x.getServiceIdentifier()))
                                      .map(MonitoredService::getServiceIdentifier)
                                      .collect(Collectors.toSet());

    PageResponse<MonitoredServiceListItemDTOBuilder> monitoredServiceListDTOBuilderPageResponse =
        PageUtils.offsetAndLimit(monitoredServiceListItemDTOS, offset, pageSize);

    environmentIdentifiers = new ArrayList<>();
    List<String> monitoredServiceIdentifiers = new ArrayList<>();
    for (MonitoredServiceListItemDTOBuilder monitoredServiceListDTOBuilder :
        monitoredServiceListDTOBuilderPageResponse.getContent()) {
      environmentIdentifiers.add(monitoredServiceListDTOBuilder.getEnvironmentRef());
      monitoredServiceIdentifiers.add(monitoredServiceListDTOBuilder.getIdentifier());
    }

    Map<String, String> environmentIdNameMap =
        nextGenService.getEnvironmentIdNameMap(projectParams, new ArrayList<>(environmentIdentifiers));
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
      boolean serviceLicenseEnabled = enabledServices.contains(monitoredServiceListDTOBuilder.getServiceRef());

      monitoredServiceListDTOS.add(
          monitoredServiceListDTOBuilder.historicalTrend(historicalTrend)
              .currentHealthScore(monitoredServiceRiskScore)
              .dependentHealthScore(dependentServiceRiskScoreList)
              .serviceName(serviceName)
              .environmentName(environmentName)
              .changeSummary(changeSummary)
              .sloHealthIndicators(sloHealthIndicatorDTOMap.get(monitoredServiceListDTOBuilder.getIdentifier()))
              .serviceMonitoringEnabled(serviceLicenseEnabled)
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

  private List<MonitoredService> getEnabledMonitoredServices(ProjectParams projectParams) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
        .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(MonitoredServiceKeys.enabled, true)
        .asList();
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

  private void validate(MonitoredServiceDTO monitoredServiceDTO, String accountId) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
                                      .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
                                      .build();
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
    notificationRuleService.validateNotification(monitoredServiceDTO.getNotificationRuleRefs(), projectParams);
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
    checkLicenseForMonitoredServiceToggle(projectParams, monitoredService, enable);

    Preconditions.checkNotNull(monitoredService, "Monitored service with identifier %s does not exists", identifier);

    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builder()
                                                        .accountIdentifier(projectParams.getAccountIdentifier())
                                                        .orgIdentifier(projectParams.getOrgIdentifier())
                                                        .projectIdentifier(projectParams.getProjectIdentifier())
                                                        .monitoredServiceIdentifier(identifier)
                                                        .build();
    MonitoredServiceDTO currentMonitoredServiceDTO = getMonitoredServiceDTO(monitoredServiceParams);
    currentMonitoredServiceDTO.setEnabled(monitoredService.isEnabled());
    MonitoredServiceYamlDTO oldMonitoredServiceYamlDTO =
        MonitoredServiceYamlDTO.builder().monitoredServiceDTO(currentMonitoredServiceDTO).build();

    healthSourceService.setHealthMonitoringFlag(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
        projectParams.getProjectIdentifier(), monitoredService.getIdentifier(),
        monitoredService.getHealthSourceIdentifiers(), enable);
    serviceLevelObjectiveV2Service.setMonitoredServiceSLOsEnableFlag(
        projectParams, monitoredService.getIdentifier(), enable);
    serviceLevelIndicatorService.setMonitoredServiceSLIsEnableFlag(
        projectParams, monitoredService.getIdentifier(), enable);
    if (enable
        && featureFlagService.isFeatureFlagEnabled(
            projectParams.getAccountIdentifier(), FeatureFlagNames.SRM_SLO_TOGGLE)) {
      entityDisabledTimeService.save(EntityDisableTime.builder()
                                         .entityUUID(monitoredService.getUuid())
                                         .accountId(monitoredService.getAccountId())
                                         .startTime(monitoredService.getLastDisabledAt())
                                         .endTime(clock.millis())
                                         .build());
    }
    hPersistence.update(
        hPersistence.createQuery(MonitoredService.class).filter(MonitoredServiceKeys.uuid, monitoredService.getUuid()),
        hPersistence.createUpdateOperations(MonitoredService.class)
            .set(MonitoredServiceKeys.enabled, enable)
            .set(MonitoredServiceKeys.lastDisabledAt, clock.millis())
            .set(MonitoredServiceKeys.lastUpdatedAt, clock.millis()));

    MonitoredServiceDTO newMonitoredServiceDTO = getMonitoredServiceDTO(monitoredServiceParams);
    MonitoredService newMonitoredService = getMonitoredService(projectParams, identifier);
    newMonitoredServiceDTO.setEnabled(newMonitoredService.isEnabled());
    MonitoredServiceYamlDTO newMonitoredServiceYamlDTO =
        MonitoredServiceYamlDTO.builder().monitoredServiceDTO(newMonitoredServiceDTO).build();

    outboxService.save(MonitoredServiceToggleEvent.builder()
                           .resourceName(monitoredService.getName())
                           .accountIdentifier(monitoredService.getAccountId())
                           .oldMonitoredServiceYamlDTO(oldMonitoredServiceYamlDTO)
                           .newMonitoredServiceYamlDTO(newMonitoredServiceYamlDTO)
                           .monitoredServiceIdentifier(monitoredService.getIdentifier())
                           .orgIdentifier(monitoredService.getOrgIdentifier())
                           .projectIdentifier(monitoredService.getProjectIdentifier())
                           .build());
    // TODO: handle race condition on same version update. Probably by using version annotation and throwing exception
    return HealthMonitoringFlagResponse.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(identifier)
        .healthMonitoringEnabled(enable)
        .build();
  }

  private void checkLicenseForMonitoredServiceToggle(
      ProjectParams projectParams, MonitoredService monitoredService, boolean enable) {
    if (enable == true
        && featureFlagService.isFeatureFlagEnabled(
            projectParams.getAccountIdentifier(), FeatureFlagNames.CVNG_LICENSE_ENFORCEMENT)) {
      AccountLicenseDTO accountLicenseDTO = null;
      try {
        Call<ResponseDTO<AccountLicenseDTO>> accountLicensesCall =
            ngLicenseHttpClient.getAccountLicensesDTO(projectParams.getAccountIdentifier());
        accountLicenseDTO = NGRestUtils.getResponse(accountLicensesCall);
      } catch (Exception e) {
        log.error("Failed to fetch License data");
        throw e;
      }

      if ((!accountLicenseDTO.getAllModuleLicenses().get(ModuleType.SRM).isEmpty())
          && accountLicenseDTO.getAllModuleLicenses()
                 .get(ModuleType.SRM)
                 .get(0)
                 .getStatus()
                 .equals(LicenseStatus.ACTIVE)) {
        long increment = 0;
        if (!isUniqueService(projectParams, monitoredService)) {
          increment = 1;
        }
        enforcementClientService.checkAvailabilityWithIncrement(
            FeatureRestrictionName.SRM_SERVICES, projectParams.getAccountIdentifier(), increment);
      } else if (!featureFlagService.isFeatureFlagEnabled(
                     projectParams.getAccountIdentifier(), FeatureName.CVNG_ENABLED.name())) {
        throw new RuntimeException("Invalid License, Please Contact Harness Support");
      }
    }
  }

  private boolean isUniqueService(ProjectParams projectParams, MonitoredService monitoredService) {
    List<MonitoredService> enabledMonitoredServices = getEnabledMonitoredServicesWithScopedQuery(projectParams);

    return getServiceParamsSet(enabledMonitoredServices)
        .contains(ServiceParams.builder()
                      .serviceIdentifier(monitoredService.getServiceIdentifier())
                      .orgIdentifier(monitoredService.getOrgIdentifier())
                      .projectIdentifier(monitoredService.getProjectIdentifier())
                      .accountIdentifier(monitoredService.getAccountId())
                      .build());
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

  @SneakyThrows
  public String getYamlTemplate(ProjectParams projectParams, MonitoredServiceType type) {
    // returning default yaml template, account/org/project specific templates can be generated later.
    String defaultTemplate = type == null ? MONITORED_SERVICE_YAML_TEMPLATE.get(MonitoredServiceType.APPLICATION)
                                          : MONITORED_SERVICE_YAML_TEMPLATE.get(type);

    if (projectParams.getProjectIdentifier() == null) {
      defaultTemplate = StringUtils.remove(defaultTemplate, "  projectIdentifier: $projectIdentifier\n");
    } else {
      defaultTemplate =
          StringUtils.replace(defaultTemplate, "$projectIdentifier", projectParams.getProjectIdentifier());
    }

    if (projectParams.getOrgIdentifier() == null) {
      defaultTemplate = StringUtils.remove(defaultTemplate, "  orgIdentifier: $orgIdentifier\n");
    } else {
      defaultTemplate = StringUtils.replace(defaultTemplate, "$orgIdentifier", projectParams.getOrgIdentifier());
    }

    return defaultTemplate;
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
    List<MonitoredService> allMonitoredServices = getMonitoredServicesByEnvIds(projectParams, environmentIdentifier);
    if (Objects.nonNull(filter)) {
      List<String> serviceIdentifiers = new ArrayList<>();
      for (MonitoredService monitoredService : allMonitoredServices) {
        serviceIdentifiers.add(monitoredService.getServiceIdentifier());
      }
      Map<String, String> serviceIdNameMap =
          nextGenService.getServiceIdNameMap(projectParams, new ArrayList<>(serviceIdentifiers));
      allMonitoredServices = filterMonitoredService(allMonitoredServices, serviceIdNameMap, filter);
    }
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
    List<AbstractServiceLevelObjective> serviceLevelObjectiveList =
        serviceLevelObjectiveV2Service.getByMonitoredServiceIdentifier(projectParams, monitoredServiceIdentifier);

    List<MonitoredServiceChangeDetailSLO> monitoredServiceChangeDetailSLOS = new ArrayList<>();

    for (AbstractServiceLevelObjective serviceLevelObjective : serviceLevelObjectiveList) {
      LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
      TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
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
                                            .filter(ref -> ref.isEnabled())
                                            .map(NotificationRuleRef::getNotificationRuleRef)
                                            .collect(Collectors.toList());
    return notificationRuleService.getEntities(projectParams, notificationRuleRefs);
  }

  @Override
  public void handleNotification(MonitoredService monitoredService) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(monitoredService.getAccountId())
                                      .orgIdentifier(monitoredService.getOrgIdentifier())
                                      .projectIdentifier(monitoredService.getProjectIdentifier())
                                      .build();
    List<NotificationRule> notificationRules = getNotificationRules(monitoredService);
    Set<String> notificationRuleRefsWithChange = new HashSet<>();

    for (NotificationRule notificationRule : notificationRules) {
      List<MonitoredServiceNotificationRuleCondition> conditions =
          ((MonitoredServiceNotificationRule) notificationRule).getConditions();
      for (MonitoredServiceNotificationRuleCondition condition : conditions) {
        NotificationData notificationData;
        switch (condition.getType()) {
          case HEALTH_SCORE:
            notificationData =
                getHealthScoreNotificationData(monitoredService, (MonitoredServiceHealthScoreCondition) condition);
            break;
          case CHANGE_OBSERVED:
            notificationData = getChangeObservedNotificationData(
                monitoredService, (MonitoredServiceChangeObservedCondition) condition);
            break;
          case CHANGE_IMPACT:
            notificationData =
                getChangeImpactNotificationData(monitoredService, (MonitoredServiceChangeImpactCondition) condition);
            break;
          case CODE_ERRORS:
            notificationData = getCodeErrorsNotificationData(
                monitoredService, (MonitoredServiceCodeErrorCondition) condition, notificationRule);
            break;
          default:
            notificationData = NotificationData.builder().shouldSendNotification(false).build();
            break;
        }
        if (notificationData.shouldSendNotification()) {
          CVNGNotificationChannel notificationChannel = notificationRule.getNotificationMethod();
          final NotificationRuleTemplateDataGenerator notificationRuleTemplateDataGenerator =
              notificationRuleConditionTypeTemplateDataGeneratorMap.get(condition.getType());
          Map<String, String> templateData = notificationRuleTemplateDataGenerator.getTemplateData(projectParams,
              monitoredService.getName(), monitoredService.getIdentifier(), monitoredService.getServiceIdentifier(),
              monitoredService.getIdentifier(), condition, notificationData.getTemplateDataMap());
          String templateId = notificationRuleTemplateDataGenerator.getTemplateId(
              notificationRule.getType(), notificationChannel.getType());
          try {
            NotificationResult notificationResult =
                notificationClient.sendNotificationAsync(notificationChannel.toNotificationChannel(
                    monitoredService.getAccountId(), monitoredService.getOrgIdentifier(),
                    monitoredService.getProjectIdentifier(), templateId, templateData));
            log.info(
                "Notification with Notification ID {}, Notification Rule {}, Condition {} for Monitored Service {} sent",
                notificationResult.getNotificationId(), notificationRule.getName(),
                condition.getType().getDisplayName(), monitoredService.getName());
          } catch (Exception ex) {
            log.error("Unable to send notification because of following exception", ex);
          }
          notificationRuleRefsWithChange.add(notificationRule.getIdentifier());
        }
      }
    }
    updateNotificationRuleRefInMonitoredService(
        projectParams, monitoredService, new ArrayList<>(notificationRuleRefsWithChange));
  }

  @Override
  public PageResponse<NotificationRuleResponse> getNotificationRules(
      ProjectParams projectParams, String monitoredServiceIdentifier, PageParams pageParams) {
    MonitoredService monitoredService =
        getMonitoredService(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                .build());
    if (monitoredService == null) {
      throw new InvalidRequestException(String.format(
          "Monitored Service  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          monitoredServiceIdentifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
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

  @Override
  public void beforeNotificationRuleDelete(ProjectParams projectParams, String notificationRuleRef) {
    List<MonitoredService> monitoredServices =
        get(projectParams, Filter.builder().notificationRuleRef(notificationRuleRef).build());
    Preconditions.checkArgument(isEmpty(monitoredServices),
        "Deleting notification rule is used in Monitored Services, "
            + "Please delete the notification rule inside Monitored Services before deleting notification rule. Monitored Services : "
            + String.join(", ",
                monitoredServices.stream()
                    .map(monitoredService -> monitoredService.getName())
                    .collect(Collectors.toList())));
  }

  @Override
  public long countUniqueEnabledServices(String accountId) {
    if (!featureFlagService.isFeatureFlagEnabled(accountId, FeatureFlagNames.CVNG_LICENSE_ENFORCEMENT)) {
      return 0;
    }
    List<MonitoredService> enabledMonitoredServices =
        getEnabledMonitoredServicesWithScopedQuery(ProjectParams.builder().accountIdentifier(accountId).build());
    return getServiceParamsSet(enabledMonitoredServices).size();
  }

  @Override
  public List<ActiveServiceMonitoredDTO> listActiveServiceMonitored(ProjectParams projectParams) {
    Map<ServiceParams, Long> serviceParamsCountMap = new HashMap<>();
    Map<ProjectParams, List<String>> projectParamsServiceIdentifiersMap = new HashMap<>();
    Map<ProjectParams, Map<String, String>> projectParamsToServiceIdNameMap = new HashMap<>();
    long currentTimeInMS = clock.millis();
    getEnableMonitoredServiceParamsSet(projectParams).stream().forEach(serviceParams -> {
      long count = serviceParamsCountMap.getOrDefault(serviceParams, 0L);
      serviceParamsCountMap.put(serviceParams, count + 1);
      List<String> serviceIdentifiers =
          projectParamsServiceIdentifiersMap.getOrDefault(serviceParams.getProjectParams(), new ArrayList<>());
      serviceIdentifiers.add(serviceParams.getServiceIdentifier());
      projectParamsServiceIdentifiersMap.put(serviceParams.getProjectParams(), serviceIdentifiers);
    });

    for (ProjectParams projectParam : projectParamsServiceIdentifiersMap.keySet()) {
      Map<String, String> serviceIdNameMap =
          nextGenService.getServiceIdNameMap(projectParam, projectParamsServiceIdentifiersMap.get(projectParam));
      projectParamsToServiceIdNameMap.put(projectParam, serviceIdNameMap);
    }

    List<ActiveServiceMonitoredDTO> activeServiceMonitoredDTOList = new ArrayList<>();

    for (ServiceParams serviceParams : serviceParamsCountMap.keySet()) {
      ActiveServiceMonitoredDTO activeServiceMonitoredDTO =
          ActiveServiceMonitoredDTO.builder()
              .accountIdentifier(serviceParams.getAccountIdentifier())
              .timestamp(currentTimeInMS)
              .module(ModuleType.SRM.getDisplayName())
              .identifier(serviceParams.getServiceIdentifier())
              .orgIdentifier(serviceParams.getOrgIdentifier())
              .projectIdentifier(serviceParams.getProjectIdentifier())
              .name(projectParamsToServiceIdNameMap.get(serviceParams.getProjectParams())
                        .get(serviceParams.getServiceIdentifier()))
              .monitoredServiceCount(serviceParamsCountMap.get(serviceParams))
              .build();

      activeServiceMonitoredDTOList.add(activeServiceMonitoredDTO);
    }

    return activeServiceMonitoredDTOList;
  }

  private List<ServiceParams> getEnableMonitoredServiceParamsSet(ProjectParams projectParams) {
    List<MonitoredService> enabledMonitoredServices = getEnabledMonitoredServicesWithScopedQuery(projectParams);
    return enabledMonitoredServices.stream()
        .map(monitoredService
            -> ServiceParams.builder()
                   .serviceIdentifier(monitoredService.getServiceIdentifier())
                   .orgIdentifier(monitoredService.getOrgIdentifier())
                   .projectIdentifier(monitoredService.getProjectIdentifier())
                   .accountIdentifier(monitoredService.getAccountId())
                   .build())
        .collect(Collectors.toList());
  }

  private Set<ServiceParams> getServiceParamsSet(List<MonitoredService> monitoredServices) {
    return monitoredServices.stream()
        .map(monitoredService
            -> ServiceParams.builder()
                   .serviceIdentifier(monitoredService.getServiceIdentifier())
                   .orgIdentifier(monitoredService.getOrgIdentifier())
                   .projectIdentifier(monitoredService.getProjectIdentifier())
                   .accountIdentifier(monitoredService.getAccountId())
                   .build())
        .collect(Collectors.toSet());
  }

  private List<MonitoredService> getEnabledMonitoredServicesWithScopedQuery(ProjectParams projectParams) {
    Query<MonitoredService> query = hPersistence.createQuery(MonitoredService.class)
                                        .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
                                        .filter(MonitoredServiceKeys.enabled, true);

    if (projectParams.getOrgIdentifier() != null) {
      query = query.filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier());
    }
    if (projectParams.getProjectIdentifier() != null) {
      query = query.filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier());
    }

    return query.asList();
  }

  private List<MonitoredService> get(ProjectParams projectParams, Filter filter) {
    List<MonitoredService> monitoredServiceList =
        hPersistence.createQuery(MonitoredService.class)
            .disableValidation()
            .filter(MonitoredServiceKeys.accountId, projectParams.getAccountIdentifier())
            .filter(MonitoredServiceKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(MonitoredServiceKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .asList();
    if (filter.getNotificationRuleRef() != null) {
      monitoredServiceList =
          monitoredServiceList.stream()
              .filter(monitoredService
                  -> monitoredService.getNotificationRuleRefs()
                          .stream()
                          .filter(ref -> ref.getNotificationRuleRef().equals(filter.getNotificationRuleRef()))
                          .collect(Collectors.toList())
                          .size()
                      > 0)
              .collect(Collectors.toList());
    }
    return monitoredServiceList;
  }

  private void updateNotificationRuleRefInMonitoredService(
      ProjectParams projectParams, MonitoredService monitoredService, List<String> notificationRuleRefs) {
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
    List<NotificationRuleRef> notificationRuleRefsWithChange = notificationRuleService.getNotificationRuleRefs(
        projectParams, notificationRuleRefDTOs, NotificationRuleType.MONITORED_SERVICE, clock.instant());
    allNotificationRuleRefs.addAll(notificationRuleRefsWithChange);
    allNotificationRuleRefs.addAll(notificationRuleRefsWithoutChange);
    UpdateOperations<MonitoredService> updateOperations = hPersistence.createUpdateOperations(MonitoredService.class);
    updateOperations.set(MonitoredServiceKeys.notificationRuleRefs, allNotificationRuleRefs);

    hPersistence.update(monitoredService, updateOperations);
  }

  private static MonitoredServiceParams buildMonitoredServiceParams(MonitoredService monitoredService) {
    return MonitoredServiceParams.builder()
        .accountIdentifier(monitoredService.getAccountId())
        .orgIdentifier(monitoredService.getOrgIdentifier())
        .projectIdentifier(monitoredService.getProjectIdentifier())
        .monitoredServiceIdentifier(monitoredService.getIdentifier())
        .build();
  }

  @VisibleForTesting
  NotificationData getHealthScoreNotificationData(
      MonitoredService monitoredService, MonitoredServiceHealthScoreCondition healthScoreCondition) {
    MonitoredServiceParams monitoredServiceParams = buildMonitoredServiceParams(monitoredService);
    Map<String, String> templateDataMap = new HashMap<>();
    boolean isEveryHeatMapBelowThreshold = false;
    long riskTimeBufferMins = 0;

    riskTimeBufferMins = getDurationInSeconds(healthScoreCondition.getPeriod());
    isEveryHeatMapBelowThreshold = heatMapService.isEveryHeatMapBelowThresholdForRiskTimeBuffer(monitoredServiceParams,
        monitoredService.getIdentifier(), healthScoreCondition.getThreshold(), riskTimeBufferMins);
    if (isEveryHeatMapBelowThreshold) {
      List<RiskData> allServiceRiskScoreList =
          heatMapService.getLatestRiskScoreForAllServicesList(monitoredServiceParams.getAccountIdentifier(),
              monitoredServiceParams.getOrgIdentifier(), monitoredServiceParams.getProjectIdentifier(),
              Collections.singletonList(monitoredServiceParams.getMonitoredServiceIdentifier()));
      templateDataMap.put(CURRENT_HEALTH_SCORE, allServiceRiskScoreList.get(0).getHealthScore().toString());
    }
    return NotificationData.builder()
        .shouldSendNotification(isEveryHeatMapBelowThreshold)
        .templateDataMap(templateDataMap)
        .build();
  }

  @VisibleForTesting
  NotificationData getChangeObservedNotificationData(
      MonitoredService monitoredService, MonitoredServiceChangeObservedCondition changeObservedCondition) {
    MonitoredServiceParams monitoredServiceParams = buildMonitoredServiceParams(monitoredService);
    Map<String, String> templateDataMap = new HashMap<>();

    List<ActivityType> changeObservedActivityTypes = new ArrayList<>();
    changeObservedCondition.getChangeCategories().forEach(changeCategory
        -> changeObservedActivityTypes.addAll(ChangeSourceType.getForCategory(changeCategory)
                                                  .stream()
                                                  .map(ChangeSourceType::getActivityType)
                                                  .collect(Collectors.toList())));
    Optional<Activity> activity = activityService.getAnyEventFromListOfActivityTypes(monitoredServiceParams,
        changeObservedActivityTypes, clock.instant().minus(5, ChronoUnit.MINUTES), clock.instant());
    activity.ifPresent(value
        -> templateDataMap.put(
            CHANGE_EVENT_TYPE, ChangeSourceType.ofActivityType(value.getType()).getChangeCategory().getDisplayName()));
    return NotificationData.builder()
        .shouldSendNotification(activity.isPresent())
        .templateDataMap(templateDataMap)
        .build();
  }

  @VisibleForTesting
  NotificationData getChangeImpactNotificationData(
      MonitoredService monitoredService, MonitoredServiceChangeImpactCondition changeImpactCondition) {
    MonitoredServiceParams monitoredServiceParams = buildMonitoredServiceParams(monitoredService);
    Map<String, String> templateDataMap = new HashMap<>();
    boolean isEveryHeatMapBelowThreshold = false;
    long riskTimeBufferMins = 0;

    List<ActivityType> changeImpactActivityTypes = new ArrayList<>();
    changeImpactCondition.getChangeCategories().forEach(changeCategory
        -> changeImpactActivityTypes.addAll(ChangeSourceType.getForCategory(changeCategory)
                                                .stream()
                                                .map(ChangeSourceType::getActivityType)
                                                .collect(Collectors.toList())));
    Optional<Activity> optionalActivity =
        activityService.getAnyEventFromListOfActivityTypes(monitoredServiceParams, changeImpactActivityTypes,
            clock.instant().minus(changeImpactCondition.getPeriod(), ChronoUnit.MILLIS), clock.instant());
    if (optionalActivity.isPresent()) {
      templateDataMap.put(CHANGE_EVENT_TYPE,
          ChangeSourceType.ofActivityType(optionalActivity.get().getType()).getChangeCategory().getDisplayName());
      Instant activityStartTime = optionalActivity.get().getActivityStartTime();
      riskTimeBufferMins = Duration.between(activityStartTime, clock.instant()).toMinutes();
      isEveryHeatMapBelowThreshold =
          heatMapService.isEveryHeatMapBelowThresholdForRiskTimeBuffer(monitoredServiceParams,
              monitoredService.getIdentifier(), changeImpactCondition.getThreshold(), riskTimeBufferMins);
      if (isEveryHeatMapBelowThreshold) {
        List<RiskData> allServiceRiskScoreList =
            heatMapService.getLatestRiskScoreForAllServicesList(monitoredServiceParams.getAccountIdentifier(),
                monitoredServiceParams.getOrgIdentifier(), monitoredServiceParams.getProjectIdentifier(),
                Collections.singletonList(monitoredServiceParams.getMonitoredServiceIdentifier()));
        templateDataMap.put(CURRENT_HEALTH_SCORE, allServiceRiskScoreList.get(0).getHealthScore().toString());
      }
      return NotificationData.builder()
          .shouldSendNotification(isEveryHeatMapBelowThreshold)
          .templateDataMap(templateDataMap)
          .build();
    } else {
      return NotificationData.builder().shouldSendNotification(false).build();
    }
  }

  private NotificationData getCodeErrorsNotificationData(MonitoredService monitoredService,
      MonitoredServiceCodeErrorCondition codeErrorCondition, NotificationRule notificationRule) {
    MonitoredServiceParams monitoredServiceParams = buildMonitoredServiceParams(monitoredService);
    Map<String, String> templateDataMap = new HashMap<>();
    boolean featureFlagEnabled =
        featureFlagService.isFeatureFlagEnabled(monitoredService.getAccountId(), SRM_CODE_ERROR_NOTIFICATIONS);
    final List<String> environmentIdentifierList = monitoredService.getEnvironmentIdentifierList();
    boolean oneEnvironmentId = environmentIdentifierList != null && environmentIdentifierList.size() == 1;

    if (featureFlagEnabled && oneEnvironmentId) {
      String environmentId = environmentIdentifierList.get(0);
      ErrorTrackingNotificationData notificationData = null;
      try {
        notificationData = errorTrackingService.getNotificationData(monitoredService.getOrgIdentifier(),
            monitoredService.getAccountId(), monitoredService.getProjectIdentifier(),
            monitoredService.getServiceIdentifier(), environmentId, codeErrorCondition.getErrorTrackingEventStatus(),
            codeErrorCondition.getErrorTrackingEventTypes(), notificationRule.getUuid());
      } catch (Exception e) {
        log.error("Error connecting to the ErrorTracking Event Summary API.", e);
      }
      if (notificationData != null && !notificationData.getScorecards().isEmpty()) {
        final String baseLinkUrl =
            ((ErrorTrackingTemplateDataGenerator) notificationRuleConditionTypeTemplateDataGeneratorMap.get(
                 NotificationRuleConditionType.CODE_ERRORS))
                .getBaseLinkUrl(monitoredService.getAccountId());
        templateDataMap.putAll(
            getCodeErrorTemplateData(codeErrorCondition.getErrorTrackingEventStatus(), notificationData, baseLinkUrl));
        templateDataMap.put(
            NOTIFICATION_URL, buildMonitoredServiceConfigurationTabUrl(baseLinkUrl, monitoredServiceParams));
        templateDataMap.put(NOTIFICATION_NAME, notificationRule.getName());
        templateDataMap.put(ENVIRONMENT_NAME, environmentId);
        return NotificationData.builder().shouldSendNotification(true).templateDataMap(templateDataMap).build();
      }
    }
    return NotificationData.builder().shouldSendNotification(false).build();
  }

  private List<NotificationRuleRef> getNotificationRuleRefs(
      ProjectParams projectParams, MonitoredService monitoredService, MonitoredServiceDTO monitoredServiceDTO) {
    List<NotificationRuleRef> notificationRuleRefs =
        notificationRuleService.getNotificationRuleRefs(projectParams, monitoredServiceDTO.getNotificationRuleRefs(),
            NotificationRuleType.MONITORED_SERVICE, Instant.ofEpochSecond(0));
    deleteNotificationRuleRefs(projectParams, monitoredService, notificationRuleRefs);
    return notificationRuleRefs;
  }

  private void deleteNotificationRuleRefs(
      ProjectParams projectParams, MonitoredService monitoredService, List<NotificationRuleRef> notificationRuleRefs) {
    List<String> existingNotificationRuleRefs = monitoredService.getNotificationRuleRefs()
                                                    .stream()
                                                    .map(NotificationRuleRef::getNotificationRuleRef)
                                                    .collect(Collectors.toList());
    List<String> updatedNotificationRuleRefs =
        notificationRuleRefs.stream().map(NotificationRuleRef::getNotificationRuleRef).collect(Collectors.toList());
    notificationRuleService.deleteNotificationRuleRefs(
        projectParams, existingNotificationRuleRefs, updatedNotificationRuleRefs);
  }

  private void filterOutHarnessCDChangeSource(MonitoredServiceDTO monitoredServiceDTO) {
    Sources sources = monitoredServiceDTO.getSources();
    Set<ChangeSourceDTO> changeSourceDTOList = monitoredServiceDTO.getSources().getChangeSources();
    sources.setChangeSources(changeSourceDTOList.stream()
                                 .filter(changeSourceDTO -> changeSourceDTO.getType() != ChangeSourceType.HARNESS_CD)
                                 .collect(Collectors.toSet()));
    monitoredServiceDTO.setSources(sources);
  }

  @Value
  @Builder
  private static class Filter {
    String notificationRuleRef;
  }

  @FieldDefaults(level = AccessLevel.PRIVATE)
  @Data
  @SuperBuilder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class ServiceParams extends ProjectParams {
    String serviceIdentifier;

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      List<String> firstServiceParams = new ArrayList<>();
      firstServiceParams.add(this.serviceIdentifier);
      firstServiceParams.add(this.getProjectIdentifier());
      firstServiceParams.add(this.getOrgIdentifier());
      firstServiceParams.add(this.getAccountIdentifier());

      List<String> secondServiceParams = new ArrayList<>();
      secondServiceParams.add(((ServiceParams) obj).serviceIdentifier);
      secondServiceParams.add(((ServiceParams) obj).getProjectIdentifier());
      secondServiceParams.add(((ServiceParams) obj).getOrgIdentifier());
      secondServiceParams.add(((ServiceParams) obj).getAccountIdentifier());
      return firstServiceParams.equals(secondServiceParams);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      List<String> serviceParams = new ArrayList<>();
      serviceParams.add(this.serviceIdentifier);
      serviceParams.add(this.getProjectIdentifier());
      serviceParams.add(this.getOrgIdentifier());
      serviceParams.add(this.getAccountIdentifier());
      for (String s : serviceParams) {
        result = result * prime + s.hashCode();
      }
      return result;
    }

    public ProjectParams getProjectParams() {
      return ProjectParams.builder()
          .projectIdentifier(this.getProjectIdentifier())
          .orgIdentifier(this.getOrgIdentifier())
          .accountIdentifier(this.getAccountIdentifier())
          .build();
    }
  }
}
