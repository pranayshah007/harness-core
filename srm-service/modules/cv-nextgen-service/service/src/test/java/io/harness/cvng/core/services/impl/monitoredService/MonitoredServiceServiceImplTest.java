/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.FIVE_MIN;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ANJAN;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.NAVEEN;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.ModuleType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.ChangeSummaryDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.beans.cvnglog.CVNGLogTag.TagType;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.client.FakeNotificationClient;
import io.harness.cvng.core.beans.HealthMonitoringFlagResponse;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.CountServiceDTO;
import io.harness.cvng.core.beans.monitoredService.HealthScoreDTO;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MetricDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceChangeDetailSLO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.MonitoredServiceDTOBuilder;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.Sources;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceListItemDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceWithHealthSources;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.monitoredService.SloHealthIndicatorDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec.AppDMetricDefinitions;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.params.logsFilterParams.LiveMonitoringLogsFilter;
import io.harness.cvng.core.entities.AnalysisInfo.SLI;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig.MetricInfo;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.SetupUsageEventService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.WebhookConfigService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.cvng.core.services.impl.ChangeSourceUpdateHandler;
import io.harness.cvng.core.services.impl.PagerdutyChangeSourceUpdateHandler;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.events.monitoredservice.MonitoredServiceCreateEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceDeleteEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceToggleEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceUpdateEvent;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.notification.beans.ChangeObservedConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceHealthScoreCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SRMModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.lock.PersistentLocker;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.notification.notificationclient.NotificationResultWithoutStatus;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonitoredServiceServiceImplTest extends CvNextGenTestBase {
  @Inject MetricPackService metricPackService;
  @Inject CVConfigService cvConfigService;
  @Inject ChangeSourceService changeSourceService;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject HeatMapService heatMapService;
  @Inject HPersistence hPersistence;
  @Inject ServiceDependencyService serviceDependencyService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject CVNGLogService cvngLogService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject NotificationRuleService notificationRuleService;
  @Inject private ActivityService activityService;
  @Inject private OutboxService outboxService;

  @Mock SetupUsageEventService setupUsageEventService;
  @Mock ChangeSourceService changeSourceServiceMock;
  @Mock FakeNotificationClient notificationClient;
  @Mock private PersistentLocker mockedPersistentLocker;
  @Mock private EnforcementClientService enforcementClientService;
  @Mock private FeatureFlagService featureFlagService;

  @Mock private WebhookConfigService webhookConfigService;

  @Mock private NgLicenseHttpClient ngLicenseHttpClient;

  private BuilderFactory builderFactory;
  String healthSourceName;
  String healthSourceIdentifier;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String environmentIdentifier;
  String serviceIdentifier;
  String feature;
  String connectorIdentifier;
  String appTierName;
  String applicationName;
  String monitoredServiceName;
  String monitoredServiceIdentifier;
  String changeSourceIdentifier;
  Clock clock;
  ProjectParams projectParams;
  ServiceEnvironmentParams environmentParams;
  Map<String, String> tags;
  PagerdutyChangeSourceUpdateHandler pagerdutyChangeSourceUpdateHandler;
  Map<ChangeSourceType, ChangeSourceUpdateHandler> changeSourceUpdateHandlerMap;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setProjectIdentifier("project");
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    healthSourceName = "health source name";
    healthSourceIdentifier = "healthSourceIdentifier";
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    environmentIdentifier = builderFactory.getContext().getEnvIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    feature = "Application Monitoring";
    connectorIdentifier = BuilderFactory.CONNECTOR_IDENTIFIER;
    applicationName = "appApplicationName";
    appTierName = "tier";
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceName = "monitoredServiceName";
    monitoredServiceIdentifier =
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier();
    clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    changeSourceIdentifier = "changeSourceIdentifier";
    tags = new HashMap<String, String>() {
      {
        put("tag1", "value1");
        put("tag2", "");
      }
    };
    changeSourceUpdateHandlerMap = new HashMap<>();
    pagerdutyChangeSourceUpdateHandler = Mockito.mock(PagerdutyChangeSourceUpdateHandler.class);
    changeSourceUpdateHandlerMap.put(ChangeSourceType.PAGER_DUTY, pagerdutyChangeSourceUpdateHandler);

    projectParams = ProjectParams.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build();
    environmentParams = builderFactory.getContext().getServiceEnvironmentParams();

    when(webhookConfigService.getWebhookApiBaseUrl()).thenReturn("http://localhost:6457/cv/api/");

    FieldUtils.writeField(monitoredServiceService, "setupUsageEventService", setupUsageEventService, true);
    FieldUtils.writeField(changeSourceService, "changeSourceUpdateHandlerMap", changeSourceUpdateHandlerMap, true);
    FieldUtils.writeField(monitoredServiceService, "changeSourceService", changeSourceService, true);
    FieldUtils.writeField(heatMapService, "clock", clock, true);
    FieldUtils.writeField(monitoredServiceService, "heatMapService", heatMapService, true);
    FieldUtils.writeField(monitoredServiceService, "notificationClient", notificationClient, true);
    FieldUtils.writeField(monitoredServiceService, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(monitoredServiceService, "ngLicenseHttpClient", ngLicenseHttpClient, true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCountUniqueEnabledServices_allUnique() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "evn1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms2", "service2", "evn1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms3", "service3", "evn1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);

    doReturn(true).when(featureFlagService).isFeatureFlagEnabled(any(), any());

    long count = monitoredServiceService.countUniqueEnabledServices(builderFactory.getContext().getAccountId());
    assertThat(count).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCountUniqueEnabledServices_someCommonServiceIdentifier() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms2", "service2", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms3", "service3", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms4", "service1", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms4", true);

    doReturn(true).when(featureFlagService).isFeatureFlagEnabled(any(), any());

    long count = monitoredServiceService.countUniqueEnabledServices(builderFactory.getContext().getAccountId());
    assertThat(count).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceChangeDetails_inRange() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    serviceLevelObjectiveV2Service.create(
        builderFactory.getProjectParams(), builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build());
    List<MonitoredServiceChangeDetailSLO> monitoredServiceChangeDetailSLOS =
        monitoredServiceService.getMonitoredServiceChangeDetails(
            builderFactory.getProjectParams(), monitoredServiceDTO.getIdentifier(), null, null);
    assertThat(monitoredServiceChangeDetailSLOS.size()).isEqualTo(1);
    assertThat(monitoredServiceChangeDetailSLOS.get(0).getIdentifier()).isEqualTo("sloIdentifier");
    assertThat(monitoredServiceChangeDetailSLOS.get(0).getName()).isEqualTo("sloName");
    assertThat(monitoredServiceChangeDetailSLOS.get(0).isOutOfRange()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceChangeDetails_notInRange() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    serviceLevelObjectiveV2Service.create(
        builderFactory.getProjectParams(), builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build());
    List<MonitoredServiceChangeDetailSLO> monitoredServiceChangeDetailSLOS =
        monitoredServiceService.getMonitoredServiceChangeDetails(
            builderFactory.getProjectParams(), monitoredServiceDTO.getIdentifier(), 1640058000000l, 1641058000000l);
    assertThat(monitoredServiceChangeDetailSLOS.size()).isEqualTo(1);
    assertThat(monitoredServiceChangeDetailSLOS.get(0).getIdentifier()).isEqualTo("sloIdentifier");
    assertThat(monitoredServiceChangeDetailSLOS.get(0).getName()).isEqualTo("sloName");
    assertThat(monitoredServiceChangeDetailSLOS.get(0).isOutOfRange()).isEqualTo(true);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreate_withFailedValidation() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    HealthSource healthSource = builderFactory.createHealthSource(CVMonitoringCategory.ERRORS);
    healthSource.setName("some-health_source-name");
    monitoredServiceDTO.getSources().getHealthSources().add(healthSource);
    assertThatThrownBy(
        () -> monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Multiple Health Sources exists with the same identifier %s", healthSourceIdentifier));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testCreateFromYaml() {
    String yaml = "monitoredService:\n"
        + "  template:\n"
        + "   templateRef: templateRef123\n"
        + "   versionLabel: versionLabel123\n"
        + "  type: Application\n"
        + "  description: description\n"
        + "  identifier: <+monitoredService.serviceRef>\n"
        + "  name: <+monitoredService.identifier>\n"
        + "  serviceRef: service1\n"
        + "  environmentRef: <+monitoredService.variables.environmentIdentifier>\n"
        + "  sources:\n"
        + "      healthSources:\n"
        + "      changeSources: \n"
        + "  tags: {}\n"
        + "  variables:\n"
        + "    -   name: environmentIdentifier\n"
        + "        type: String\n"
        + "        value: env3";
    MonitoredServiceResponse monitoredServiceResponse =
        monitoredServiceService.createFromYaml(builderFactory.getProjectParams(), yaml);
    MonitoredServiceResponse monitoredServiceResponseFromDb =
        monitoredServiceService.get(builderFactory.getProjectParams(), "service1_env3");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO()).isNotNull();
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getName()).isEqualTo("service1_env3");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getEnvironmentRef()).isEqualTo("env3");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getTemplate().getTemplateRef())
        .isEqualTo("templateRef123");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getTemplate().getVersionLabel())
        .isEqualTo("versionLabel123");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateFromYaml_WithValidJsonMetricDefinitionString() {
    String yaml = "monitoredService:\n"
        + "  template:\n"
        + "   templateRef: templateRef123\n"
        + "   versionLabel: versionLabel123\n"
        + "  type: Application\n"
        + "  description: description\n"
        + "  identifier: testms\n"
        + "  name: test\n"
        + "  serviceRef: service1\n"
        + "  environmentRef: test\n"
        + "  sources:\n"
        + "      changeSources: \n"
        + "      healthSources:\n"
        + "        - type: Stackdriver\n"
        + "          identifier: gcp\n"
        + "          name: gcp\n"
        + "          spec:\n"
        + "            connectorRef: account.Google_Cloud_Provider\n"
        + "            feature: Cloud Metrics\n"
        + "            metricDefinitions:\n"
        + "              - dashboardName: \"\"\n"
        + "                dashboardPath: \"\"\n"
        + "                metricName: m1\n"
        + "                metricTags:\n"
        + "                  - m\n"
        + "                identifier: m1\n"
        + "                isManualQuery: true\n"
        + "                jsonMetricDefinitionString: \"{   \\\"dataSets\\\": [     {       \\\"timeSeriesQuery\\\": {         \\\"unitOverride\\\": \\\"1\\\",         \\\"timeSeriesFilter\\\": {           \\\"filter\\\": \\\"metric.type=\\\\\\\"custom.googleapis.com/user/x_mongo_prod_instance_count\\\\\\\" resource.type=\\\\\\\"global\\\\\\\"\\\",           \\\"aggregation\\\": {             \\\"perSeriesAligner\\\": \\\"ALIGN_MEAN\\\",             \\\"alignmentPeriod\\\": \\\"60s\\\"           }         }       }     }   ] }\"\n"
        + "                riskProfile:\n"
        + "                  metricType: RESP_TIME\n"
        + "                  category: Performance\n"
        + "                  thresholdTypes:\n"
        + "                    - ACT_WHEN_HIGHER\n"
        + "                sli:\n"
        + "                  enabled: true\n"
        + "                serviceInstanceField: svc\n"
        + "                analysis:\n"
        + "                  riskProfile:\n"
        + "                    category: Performance\n"
        + "                    metricType: RESP_TIME\n"
        + "                    thresholdTypes:\n"
        + "                      - ACT_WHEN_HIGHER\n"
        + "                  liveMonitoring:\n"
        + "                    enabled: true\n"
        + "                  deploymentVerification:\n"
        + "                    enabled: true";
    MonitoredServiceResponse monitoredServiceResponse =
        monitoredServiceService.createFromYaml(builderFactory.getProjectParams(), yaml);
    MonitoredServiceResponse monitoredServiceResponseFromDb =
        monitoredServiceService.get(builderFactory.getProjectParams(), "service1_test");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO()).isNotNull();
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getName()).isEqualTo("service1_test");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getEnvironmentRef()).isEqualTo("test");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getTemplate().getTemplateRef())
        .isEqualTo("templateRef123");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getTemplate().getVersionLabel())
        .isEqualTo("versionLabel123");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateFromYaml_WithValidJsonMetricDefinitionStringAndValidJsonMetricDefinition() {
    String yaml = "monitoredService:\n"
        + "  template:\n"
        + "   templateRef: templateRef123\n"
        + "   versionLabel: versionLabel123\n"
        + "  type: Application\n"
        + "  description: description\n"
        + "  identifier: testms\n"
        + "  name: test\n"
        + "  serviceRef: service1\n"
        + "  environmentRef: test\n"
        + "  sources:\n"
        + "      changeSources: \n"
        + "      healthSources:\n"
        + "        - type: Stackdriver\n"
        + "          identifier: gcp\n"
        + "          name: gcp\n"
        + "          spec:\n"
        + "            connectorRef: account.Google_Cloud_Provider\n"
        + "            feature: Cloud Metrics\n"
        + "            metricDefinitions:\n"
        + "              - dashboardName: \"\"\n"
        + "                dashboardPath: \"\"\n"
        + "                metricName: m1\n"
        + "                metricTags:\n"
        + "                  - m\n"
        + "                identifier: m1\n"
        + "                isManualQuery: true\n"
        + "                jsonMetricDefinition:\n"
        + "                  datasets:\n"
        + "                  - timeSeriesQuery:\n"
        + "                      unitOverride: \"1\"\n"
        + "                      timeSeriesFilter:\n"
        + "                        filter: test\n"
        + "                        aggregation:\n"
        + "                          perSeriesAligner: test\n"
        + "                          alignmentPeriod: 60s\n"
        + "                jsonMetricDefinitionString: \"{   \\\"dataSets\\\": [     {       \\\"timeSeriesQuery\\\": {         \\\"unitOverride\\\": \\\"1\\\",         \\\"timeSeriesFilter\\\": {           \\\"filter\\\": \\\"metric.type=\\\\\\\"custom.googleapis.com/user/x_mongo_prod_instance_count\\\\\\\" resource.type=\\\\\\\"global\\\\\\\"\\\",           \\\"aggregation\\\": {             \\\"perSeriesAligner\\\": \\\"ALIGN_MEAN\\\",             \\\"alignmentPeriod\\\": \\\"60s\\\"           }         }       }     }   ] }\"\n"
        + "                riskProfile:\n"
        + "                  metricType: RESP_TIME\n"
        + "                  category: Performance\n"
        + "                  thresholdTypes:\n"
        + "                    - ACT_WHEN_HIGHER\n"
        + "                sli:\n"
        + "                  enabled: true\n"
        + "                serviceInstanceField: svc\n"
        + "                analysis:\n"
        + "                  riskProfile:\n"
        + "                    category: Performance\n"
        + "                    metricType: RESP_TIME\n"
        + "                    thresholdTypes:\n"
        + "                      - ACT_WHEN_HIGHER\n"
        + "                  liveMonitoring:\n"
        + "                    enabled: true\n"
        + "                  deploymentVerification:\n"
        + "                    enabled: true";
    MonitoredServiceResponse monitoredServiceResponse =
        monitoredServiceService.createFromYaml(builderFactory.getProjectParams(), yaml);
    MonitoredServiceResponse monitoredServiceResponseFromDb =
        monitoredServiceService.get(builderFactory.getProjectParams(), "service1_test");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO()).isNotNull();
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getName()).isEqualTo("service1_test");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getEnvironmentRef()).isEqualTo("test");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getTemplate().getTemplateRef())
        .isEqualTo("templateRef123");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getTemplate().getVersionLabel())
        .isEqualTo("versionLabel123");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateFromYaml_WithInvalidJsonMetricDefinitionString() {
    String yaml = "monitoredService:\n"
        + "  template:\n"
        + "   templateRef: templateRef123\n"
        + "   versionLabel: versionLabel123\n"
        + "  type: Application\n"
        + "  description: description\n"
        + "  identifier: testms\n"
        + "  name: test\n"
        + "  serviceRef: service1\n"
        + "  environmentRef: test\n"
        + "  sources:\n"
        + "      changeSources: \n"
        + "      healthSources:\n"
        + "        - type: Stackdriver\n"
        + "          identifier: gcp\n"
        + "          name: gcp\n"
        + "          spec:\n"
        + "            connectorRef: account.Google_Cloud_Provider\n"
        + "            feature: Cloud Metrics\n"
        + "            metricDefinitions:\n"
        + "              - dashboardName: \"\"\n"
        + "                dashboardPath: \"\"\n"
        + "                metricName: m1\n"
        + "                metricTags:\n"
        + "                  - m\n"
        + "                identifier: m1\n"
        + "                isManualQuery: true\n"
        + "                jsonMetricDefinitionString: \"{   \\\"dataSets\\\" [     {       \\\"timeSeriesQuery\\\": {         \\\"unitOverride\\\": \\\"1\\\",         \\\"timeSeriesFilter\\\": {           \\\"filter\\\": \\\"metric.type=\\\\\\\"custom.googleapis.com/user/x_mongo_prod_instance_count\\\\\\\" resource.type=\\\\\\\"global\\\\\\\"\\\",           \\\"aggregation\\\": {             \\\"perSeriesAligner\\\": \\\"ALIGN_MEAN\\\",             \\\"alignmentPeriod\\\": \\\"60s\\\"           }         }       }     }   ] }\"\n"
        + "                riskProfile:\n"
        + "                  metricType: RESP_TIME\n"
        + "                  category: Performance\n"
        + "                  thresholdTypes:\n"
        + "                    - ACT_WHEN_HIGHER\n"
        + "                sli:\n"
        + "                  enabled: true\n"
        + "                serviceInstanceField: svc\n"
        + "                analysis:\n"
        + "                  riskProfile:\n"
        + "                    category: Performance\n"
        + "                    metricType: RESP_TIME\n"
        + "                    thresholdTypes:\n"
        + "                      - ACT_WHEN_HIGHER\n"
        + "                  liveMonitoring:\n"
        + "                    enabled: true\n"
        + "                  deploymentVerification:\n"
        + "                    enabled: true";

    assertThatThrownBy(() -> monitoredServiceService.createFromYaml(builderFactory.getProjectParams(), yaml))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateFromYaml_withoutIdentifier() {
    String yaml = "monitoredService:\n"
        + "  template:\n"
        + "   templateRef: templateRef123\n"
        + "   versionLabel: versionLabel123\n"
        + "  type: Application\n"
        + "  description: description\n"
        + "  serviceRef: service1\n"
        + "  environmentRef: <+monitoredService.variables.environmentIdentifier>\n"
        + "  sources:\n"
        + "      healthSources:\n"
        + "      changeSources: \n"
        + "  tags: {}\n"
        + "  variables:\n"
        + "    -   name: environmentIdentifier\n"
        + "        type: String\n"
        + "        value: env3";
    MonitoredServiceResponse monitoredServiceResponse =
        monitoredServiceService.createFromYaml(builderFactory.getProjectParams(), yaml);
    MonitoredServiceResponse monitoredServiceResponseFromDb =
        monitoredServiceService.get(builderFactory.getProjectParams(), "service1_env3");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO()).isNotNull();
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getName()).isEqualTo("service1_env3");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getEnvironmentRef()).isEqualTo("env3");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getTemplate().getTemplateRef())
        .isEqualTo("templateRef123");
    assertThat(monitoredServiceResponse.getMonitoredServiceDTO().getTemplate().getVersionLabel())
        .isEqualTo("versionLabel123");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testCreateFromYaml_expressionEvaluvationError() {
    String yaml = "monitoredService:\n"
        + "  identifier: <+monitoredService.serviceRef>\n"
        + "  type: Application\n"
        + "  description: description\n"
        + "  name: <+monitoredService.name>\n"
        + "  serviceRef: <+monitoredService.serviceRef>\n"
        + "  environmentRef: env1\n"
        + "  tags: {}\n"
        + "  sources:\n"
        + "    healthSources:\n"
        + "    changeSources: \n";
    assertThatThrownBy(() -> monitoredServiceService.createFromYaml(builderFactory.getProjectParams(), yaml))
        .hasMessage("Infinite loop in variable interpretation");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetSloMetrics() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    String healthSourceIdentifier = "healthSourceIdentifier";
    PrometheusCVConfig cvConfig = (PrometheusCVConfig) builderFactory.prometheusCVConfigBuilder()
                                      .metricInfoList(Arrays.asList(MetricInfo.builder()
                                                                        .metricName("metricName1")
                                                                        .sli(SLI.builder().enabled(true).build())
                                                                        .identifier("identifier1")
                                                                        .build(),
                                          MetricInfo.builder()
                                              .metricName("metricName2")
                                              .sli(SLI.builder().enabled(false).build())
                                              .identifier("identifier2")
                                              .build(),
                                          MetricInfo.builder()
                                              .metricName("metricName3")
                                              .sli(SLI.builder().enabled(true).build())
                                              .identifier("identifier3")
                                              .build()))
                                      .metricPack(MetricPack.builder().category(CVMonitoringCategory.ERRORS).build())
                                      .identifier(monitoredServiceIdentifier + "/" + healthSourceIdentifier)
                                      .build();
    hPersistence.save(cvConfig);
    List<MetricDTO> metricDTOS = monitoredServiceService.getSloMetrics(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, healthSourceIdentifier);
    metricDTOS = metricDTOS.stream().sorted(Comparator.comparing(MetricDTO::getMetricName)).collect(toList());
    assertThat(metricDTOS.size()).isEqualTo(2);
    assertThat(metricDTOS.get(0).getMetricName()).isEqualTo("metricName1");
    assertThat(metricDTOS.get(1).getMetricName()).isEqualTo("metricName3");
    assertThat(metricDTOS.get(0).getIdentifier()).isEqualTo("identifier1");
    assertThat(metricDTOS.get(1).getIdentifier()).isEqualTo("identifier3");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreate_monitoredServiceAlreadyPresentWithSameIdentifier() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    assertThatThrownBy(
        () -> monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(String.format(
            "Monitored Source Entity  with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
            monitoredServiceDTO.getIdentifier(), monitoredServiceDTO.getOrgIdentifier(),
            monitoredServiceDTO.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreate_monitoredServiceAlreadyPresentWithServiceAndEnvironmentRef() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO.setIdentifier("some-other-identifier");
    assertThatThrownBy(
        () -> monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(String.format(
            "Monitored Source Entity  with duplicate service ref %s, environmentRef %s having identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
            monitoredServiceDTO.getServiceRef(), monitoredServiceDTO.getEnvironmentRef(),
            monitoredServiceDTO.getIdentifier(), monitoredServiceDTO.getOrgIdentifier(),
            monitoredServiceDTO.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreate_monitoredServiceHealthSourcesConfigAlreadyPresent() {
    CVConfig cvConfig =
        builderFactory.appDynamicsCVConfigBuilder()
            .identifier(HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier))
            .accountId(accountId)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .connectorIdentifier(connectorIdentifier)
            .monitoringSourceName(healthSourceName)
            .productName(feature)
            .category(CVMonitoringCategory.ERRORS)
            .build();
    cvConfigService.save(cvConfig);
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    assertThatThrownBy(
        () -> monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(String.format(
            "Already Existing configs for Monitored Service  with identifier %s and orgIdentifier %s and projectIdentifier %s",
            HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier),
            orgIdentifier, projectIdentifier));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreate_monitoredServiceWithEmptyHealthSources() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO).isEqualTo(monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreate_monitoredServiceNonEmptyHealthSources() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO).isEqualTo(monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    AppDynamicsCVConfig cvConfig = (AppDynamicsCVConfig) cvConfigs.get(0);
    assertCVConfig(cvConfig, CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testCreate_monitoredServiceNonEmptyChangeSource() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO).isEqualTo(monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    Set<ChangeSourceDTO> changeSources =
        changeSourceService.get(builderFactory.getContext().getMonitoredServiceParams(),
            savedMonitoredServiceDTO.getSources()
                .getChangeSources()
                .stream()
                .map(ChangeSourceDTO::getIdentifier)
                .collect(toList()));
    assertThat(changeSources.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpdate_ChangeSourceCreation() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder().build();
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();

    MonitoredServiceDTO toUpdateMonitoredServiceDTO =
        createMonitoredServiceDTOBuilder()
            .sources(Sources.builder()
                         .changeSources(new HashSet<>(
                             Arrays.asList(builderFactory.getHarnessCDCurrentGenChangeSourceDTOBuilder().build(),
                                 builderFactory.getPagerDutyChangeSourceDTOBuilder().build())))
                         .build())
            .build();
    MonitoredServiceDTO updatedMonitoredServiceDTO =
        monitoredServiceService.update(builderFactory.getContext().getAccountId(), toUpdateMonitoredServiceDTO)
            .getMonitoredServiceDTO();

    assertThat(updatedMonitoredServiceDTO).isEqualTo(toUpdateMonitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, toUpdateMonitoredServiceDTO);
    Set<ChangeSourceDTO> changeSources =
        changeSourceService.get(builderFactory.getContext().getMonitoredServiceParams(),
            updatedMonitoredServiceDTO.getSources()
                .getChangeSources()
                .stream()
                .map(ChangeSourceDTO::getIdentifier)
                .collect(toList()));
    assertThat(changeSources.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpdate_beforeUpdatehandlerInvoked() {
    MonitoredServiceDTO existingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .sources(
                Sources.builder()
                    .healthSources(new HashSet<>(Arrays.asList(
                        HealthSource.builder()
                            .identifier("healthSourceIdentifier")
                            .name("health source name")
                            .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
                            .spec(AppDynamicsHealthSourceSpec.builder()
                                      .applicationName("appApplicationName")
                                      .tierName("tier")
                                      .connectorRef("CONNECTOR_IDENTIFIER")
                                      .feature("Application Monitoring")
                                      .metricDefinitions(Arrays.asList(
                                          AppDMetricDefinitions.builder()
                                              .identifier("metric1")
                                              .metricName("metric2")
                                              .groupName("group1")
                                              .metricPath("path2")
                                              .baseFolder("baseFolder2")
                                              .sli(SLIDTO.builder().enabled(true).build())
                                              .analysis(
                                                  AnalysisDTO.builder()
                                                      .riskProfile(RiskProfile.builder()
                                                                       .category(CVMonitoringCategory.ERRORS)
                                                                       .metricType(TimeSeriesMetricType.INFRA)
                                                                       .build())
                                                      .deploymentVerification(
                                                          DeploymentVerificationDTO.builder()
                                                              .enabled(true)
                                                              .serviceInstanceMetricPath("Individual Nodes|*|path")
                                                              .build())
                                                      .liveMonitoring(LiveMonitoringDTO.builder().enabled(true).build())
                                                      .build())
                                              .build()))
                                      .build())
                            .build())))
                    .build())
            .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), existingMonitoredService);
    MonitoredServiceDTO updatingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .identifier(existingMonitoredService.getIdentifier())
            .sources(Sources.builder().healthSources(new HashSet<>()).build())
            .build();
    serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Arrays.asList(builderFactory.getServiceLevelIndicatorDTOBuilder()), "sloIdentifier",
        existingMonitoredService.getIdentifier(), "healthSourceIdentifier");
    assertThatThrownBy(
        () -> monitoredServiceService.update(builderFactory.getContext().getAccountId(), updatingMonitoredService))
        .hasMessage(
            "Deleting metrics are used in SLIs, Please delete the SLIs before deleting metrics. SLIs : sloIdentifier_metric1");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testDelete_beforeUpdatehandlerInvoked() {
    MonitoredServiceDTO existingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .sources(
                Sources.builder()
                    .healthSources(new HashSet<>(Arrays.asList(
                        HealthSource.builder()
                            .identifier("healthSourceIdentifier")
                            .name("health source name")
                            .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
                            .spec(AppDynamicsHealthSourceSpec.builder()
                                      .applicationName("appApplicationName")
                                      .tierName("tier")
                                      .connectorRef("CONNECTOR_IDENTIFIER")
                                      .feature("Application Monitoring")
                                      .metricDefinitions(Arrays.asList(
                                          AppDMetricDefinitions.builder()
                                              .identifier("metric1")
                                              .metricName("metric2")
                                              .groupName("group1")
                                              .metricPath("path2")
                                              .baseFolder("baseFolder2")
                                              .sli(SLIDTO.builder().enabled(true).build())
                                              .analysis(
                                                  AnalysisDTO.builder()
                                                      .riskProfile(RiskProfile.builder()
                                                                       .category(CVMonitoringCategory.ERRORS)
                                                                       .metricType(TimeSeriesMetricType.INFRA)
                                                                       .build())
                                                      .deploymentVerification(
                                                          DeploymentVerificationDTO.builder()
                                                              .enabled(true)
                                                              .serviceInstanceMetricPath("|Individual Nodes|*|path")
                                                              .build())
                                                      .liveMonitoring(LiveMonitoringDTO.builder().enabled(true).build())
                                                      .build())
                                              .build()))
                                      .build())
                            .build())))
                    .build())
            .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), existingMonitoredService);
    serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Arrays.asList(builderFactory.getServiceLevelIndicatorDTOBuilder()), "sloIdentifier",
        existingMonitoredService.getIdentifier(), "healthSourceIdentifier");
    assertThatThrownBy(()
                           -> monitoredServiceService.delete(
                               builderFactory.getProjectParams(), existingMonitoredService.getIdentifier()))
        .hasMessage(
            "Deleting metrics are used in SLIs, Please delete the SLIs before deleting metrics. SLIs : sloIdentifier_metric1");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreate_monitoredServiceNonEmptyDependencies() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithDependencies();
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO).isEqualTo(monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    Set<ServiceDependencyDTO> serviceDependencyDTOS = serviceDependencyService.getDependentServicesForMonitoredService(
        builderFactory.getContext().getProjectParams(), monitoredServiceDTO.getIdentifier());
    assertThat(serviceDependencyDTOS).isEqualTo(monitoredServiceDTO.getDependencies());
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGet_IdentifierNotPresent() {
    assertThatThrownBy(
        () -> monitoredServiceService.get(builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Monitored Source Entity with identifier %s is not present", monitoredServiceIdentifier));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGet_usingIdentifier() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO getMonitoredServiceDTO =
        monitoredServiceService.get(builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier)
            .getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO).isEqualTo(getMonitoredServiceDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGet_usingIdentifiers() {
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms2", "service1", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    List<MonitoredServiceDetail> monitoredServiceDetails = monitoredServiceService.getMonitoredServiceDetails(
        builderFactory.getContext().getProjectParams(), Set.of("ms1", "ms2"));
    assertThat(monitoredServiceDetails.size()).isEqualTo(2);
    assertThat(monitoredServiceDetails.get(0).getMonitoredServiceIdentifier()).isEqualTo("ms1");
    assertThat(monitoredServiceDetails.get(0).getServiceIdentifier()).isEqualTo("service1");
    assertThat(monitoredServiceDetails.get(0).getEnvironmentIdentifier()).isEqualTo("env1");
    assertThat(monitoredServiceDetails.get(0).getServiceName()).isEqualTo("Mocked service name");
    assertThat(monitoredServiceDetails.get(0).getEnvironmentName()).isEqualTo("Mocked env name");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetAll() {
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms2", "service1", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    List<MonitoredServiceDetail> monitoredServiceDetails =
        monitoredServiceService.getAllMonitoredServiceDetails(builderFactory.getContext().getProjectParams());
    assertThat(monitoredServiceDetails.size()).isEqualTo(2);
    assertThat(monitoredServiceDetails.get(0).getMonitoredServiceIdentifier()).isEqualTo("ms1");
    assertThat(monitoredServiceDetails.get(0).getServiceIdentifier()).isEqualTo("service1");
    assertThat(monitoredServiceDetails.get(0).getEnvironmentIdentifier()).isEqualTo("env1");
    assertThat(monitoredServiceDetails.get(0).getServiceName()).isEqualTo("Mocked service name");
    assertThat(monitoredServiceDetails.get(0).getEnvironmentName()).isEqualTo("Mocked env name");
  }
  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGet_usingServiceEnvironmentNotPresent() {
    assertThat(monitoredServiceService.getApplicationMonitoredServiceResponse(
                   builderFactory.getContext().getServiceEnvironmentParams()))
        .isNull();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGet_usingServiceEnvironment() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO getMonitoredServiceDTO =
        monitoredServiceService
            .getApplicationMonitoredServiceResponse(builderFactory.getContext().getServiceEnvironmentParams())
            .getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO).isEqualTo(getMonitoredServiceDTO);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testDelete() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    boolean isDeleted =
        monitoredServiceService.delete(builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier);
    assertThat(isDeleted).isEqualTo(true);
    monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertThat(monitoredService).isEqualTo(null);
    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(changeSourceService.get(
                   builderFactory.getContext().getMonitoredServiceParams(), Arrays.asList(changeSourceIdentifier)))
        .isEmpty();
    assertThat(cvConfigs.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testDelete_ActivityDeletion_Success() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    activityService.createActivity(builderFactory.getKubernetesClusterActivityBuilder().build());
    activityService.createActivity(builderFactory.getDeploymentActivityBuilder().build());
    activityService.createActivity(builderFactory.getPagerDutyActivityBuilder().build());
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                        .build();
    assertThat(activityService.getByMonitoredServiceIdentifier(monitoredServiceParams).size()).isEqualTo(3);
    boolean isDeleted =
        monitoredServiceService.delete(builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier);
    assertThat(isDeleted).isEqualTo(true);
    assertThat(activityService.getByMonitoredServiceIdentifier(monitoredServiceParams).size()).isEqualTo(0);
    monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertThat(monitoredService).isEqualTo(null);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testDelete_PagerDutyChangeSource_HandleDeleteException() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithPagerDutyChangeSource();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);

    doAnswer(invocation -> { throw new SocketTimeoutException(); })
        .when(pagerdutyChangeSourceUpdateHandler)
        .handleDelete(any(PagerDutyChangeSource.class));

    boolean isDeleted =
        monitoredServiceService.delete(builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier);
    assertThat(isDeleted).isEqualTo(true);

    MonitoredService monitoredService1 = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertThat(monitoredService1).isEqualTo(null);
    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(changeSourceService.get(
                   builderFactory.getContext().getMonitoredServiceParams(), Arrays.asList(changeSourceIdentifier)))
        .isEmpty();
    assertThat(cvConfigs.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testList_sortingOrder() throws IllegalAccessException {
    clock = Clock.systemDefaultZone();
    FieldUtils.writeField(monitoredServiceService, "clock", clock, true);
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_2_local", "service_2", Sets.newHashSet("service_1_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    environmentIdentifier = "new-environment";
    monitoredServiceIdentifier = "new-monitored-service-identifier";
    healthSourceIdentifier = "new-health-source-identifier";
    monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        monitoredServiceIdentifier, environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse =
        monitoredServiceService.list(projectParams, null, 0, 10, null, false);
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(3);
    List<MonitoredServiceListItemDTO> monitoredServiceListItemDTOS = monitoredServiceListDTOPageResponse.getContent();
    MonitoredServiceListItemDTO monitoredServiceListItemDTO = monitoredServiceListItemDTOS.get(0);
    assertThat(monitoredServiceListItemDTO.getName()).isEqualTo("service_2_local");
    assertThat(monitoredServiceListItemDTO.getIdentifier()).isEqualTo("service_2_local");
    assertThat(monitoredServiceListItemDTO.getServiceName()).isEqualTo("Mocked service name");
    assertThat(monitoredServiceListItemDTO.getServiceRef()).isEqualTo("service_2");
    assertThat(monitoredServiceListItemDTO.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(monitoredServiceListItemDTO.getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(monitoredServiceListItemDTO.isHealthMonitoringEnabled()).isFalse();
    assertThat(monitoredServiceListItemDTO.getTags()).isEqualTo(tags);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testList_withEnvironmentFilter() throws IllegalAccessException {
    useChangeSourceServiceMock();
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceTwoDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_2_local", "service_2", Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    environmentIdentifier = "new-environment";
    monitoredServiceIdentifier = "new-monitored-service-identifier";
    healthSourceIdentifier = "new-health-source-identifier";
    monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        monitoredServiceIdentifier, environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    ChangeSummaryDTO changeSummary = ChangeSummaryDTO.builder().build();
    when(changeSourceServiceMock.getChangeSummary(any(), any(), any(), any())).thenReturn(changeSummary);
    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse = monitoredServiceService.list(
        projectParams, Collections.singletonList(environmentIdentifier), 0, 10, null, false);
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(1);
    MonitoredServiceListItemDTO monitoredServiceListItemDTO = monitoredServiceListDTOPageResponse.getContent().get(0);
    assertThat(monitoredServiceListItemDTO.getName()).isEqualTo(monitoredServiceIdentifier);
    assertThat(monitoredServiceListItemDTO.getIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(monitoredServiceListItemDTO.getServiceRef()).isEqualTo(serviceIdentifier);
    assertThat(monitoredServiceListItemDTO.getEnvironmentRef()).isEqualTo(environmentIdentifier);
    assertThat(monitoredServiceListItemDTO.getServiceName()).isEqualTo("Mocked service name");
    assertThat(monitoredServiceListItemDTO.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(monitoredServiceListItemDTO.getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(monitoredServiceListItemDTO.getChangeSummary()).isEqualTo(changeSummary);
    assertThat(monitoredServiceListItemDTO.isHealthMonitoringEnabled()).isFalse();

    monitoredServiceListDTOPageResponse =
        monitoredServiceService.list(projectParams, Collections.emptyList(), 0, 10, null, false);
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testList_withEnvironmentFilterAccrossMultipleEnvs() throws IllegalAccessException {
    useChangeSourceServiceMock();
    MonitoredServiceDTO monitoredServiceOneDTO =
        builderFactory.monitoredServiceDTOBuilder().dependencies(Collections.emptySet()).build();
    MonitoredServiceDTO monitoredServiceTwoDTO =
        builderFactory.monitoredServiceDTOBuilder()
            .identifier("monitoredService2")
            .type(MonitoredServiceType.INFRASTRUCTURE)
            .environmentRefList(Arrays.asList(builderFactory.getContext().getEnvIdentifier(), "env2"))
            .dependencies(Collections.singleton(ServiceDependencyDTO.builder()
                                                    .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                                                    .build()))
            .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);

    ChangeSummaryDTO changeSummary = ChangeSummaryDTO.builder().build();
    when(changeSourceServiceMock.getChangeSummary(any(), any(), any(), any())).thenReturn(changeSummary);
    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse =
        monitoredServiceService.list(projectParams, Collections.singletonList("env2"), 0, 10, null, false);
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(1);
    MonitoredServiceListItemDTO monitoredServiceListItemDTO = monitoredServiceListDTOPageResponse.getContent().get(0);
    assertThat(monitoredServiceListItemDTO.getName()).isEqualTo("monitored service name");
    assertThat(monitoredServiceListItemDTO.getIdentifier()).isEqualTo("monitoredService2");
    assertThat(monitoredServiceListItemDTO.getType()).isEqualTo(MonitoredServiceType.INFRASTRUCTURE);
    assertThat(monitoredServiceListItemDTO.getDependentHealthScore()).hasSize(1);
    assertThat(monitoredServiceListItemDTO.getDependentHealthScore().get(0).getRiskStatus()).isEqualTo(Risk.NO_DATA);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testList_forAllEnvironment() {
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_2_local", "service_2", Sets.newHashSet("service_1_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    environmentIdentifier = "new-environment";
    monitoredServiceIdentifier = "new-monitored-service-identifier";
    healthSourceIdentifier = "new-health-source-identifier";
    monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        monitoredServiceIdentifier, environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse =
        monitoredServiceService.list(projectParams, null, 0, 10, null, false);
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(3);
    List<MonitoredServiceListItemDTO> monitoredServiceListItemDTOS =
        monitoredServiceListDTOPageResponse.getContent()
            .stream()
            .sorted(Comparator.comparing(MonitoredServiceListItemDTO::getIdentifier))
            .collect(toList());
    MonitoredServiceListItemDTO monitoredServiceListItemDTO = monitoredServiceListItemDTOS.get(0);
    assertThat(monitoredServiceListItemDTO.getName()).isEqualTo(monitoredServiceIdentifier);
    assertThat(monitoredServiceListItemDTO.getIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(monitoredServiceListItemDTO.getServiceName()).isEqualTo("Mocked service name");
    assertThat(monitoredServiceListItemDTO.getServiceRef()).isEqualTo(serviceIdentifier);
    assertThat(monitoredServiceListItemDTO.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(monitoredServiceListItemDTO.getEnvironmentRef()).isEqualTo(environmentIdentifier);
    assertThat(monitoredServiceListItemDTO.getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(monitoredServiceListItemDTO.isHealthMonitoringEnabled()).isFalse();
    assertThat(monitoredServiceListItemDTO.getTags()).isEqualTo(tags);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testList_allUniqueServices() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "evn1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms2", "service2", "evn1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms3", "service3", "evn1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);

    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse =
        monitoredServiceService.list(projectParams, null, 0, 10, null, false);
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(3);
    List<MonitoredServiceListItemDTO> monitoredServiceListItemDTOS = monitoredServiceListDTOPageResponse.getContent();
    monitoredServiceListItemDTOS = monitoredServiceListItemDTOS.stream()
                                       .sorted(Comparator.comparing(MonitoredServiceListItemDTO::getIdentifier))
                                       .collect(toList());
    assertThat(monitoredServiceListItemDTOS.get(0).isServiceMonitoringEnabled()).isEqualTo(true);
    assertThat(monitoredServiceListItemDTOS.get(1).isServiceMonitoringEnabled()).isEqualTo(true);
    assertThat(monitoredServiceListItemDTOS.get(2).isServiceMonitoringEnabled()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testList_someCommonServices() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms2", "service2", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms3", "service3", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms4", "service1", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);

    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse =
        monitoredServiceService.list(projectParams, null, 0, 10, null, false);
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(4);
    List<MonitoredServiceListItemDTO> monitoredServiceListItemDTOS = monitoredServiceListDTOPageResponse.getContent();
    monitoredServiceListItemDTOS = monitoredServiceListItemDTOS.stream()
                                       .sorted(Comparator.comparing(MonitoredServiceListItemDTO::getIdentifier))
                                       .collect(toList());
    assertThat(monitoredServiceListItemDTOS.get(0).isServiceMonitoringEnabled()).isEqualTo(true);
    assertThat(monitoredServiceListItemDTOS.get(1).isServiceMonitoringEnabled()).isEqualTo(true);
    assertThat(monitoredServiceListItemDTOS.get(2).isServiceMonitoringEnabled()).isEqualTo(false);
    assertThat(monitoredServiceListItemDTOS.get(3).isServiceMonitoringEnabled()).isEqualTo(true);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testDeleteByAccountId() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    monitoredServiceService.deleteByAccountIdentifier(MonitoredService.class, accountId);
    monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertThat(monitoredService).isEqualTo(null);
    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testDeleteByProjectIdentifier() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    monitoredServiceService.deleteByProjectIdentifier(
        MonitoredService.class, accountId, orgIdentifier, projectIdentifier);
    monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertThat(monitoredService).isEqualTo(null);
    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testDeleteByOrgIdentifier() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    monitoredServiceService.deleteByOrgIdentifier(MonitoredService.class, accountId, orgIdentifier);
    monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertThat(monitoredService).isEqualTo(null);
    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreateDefault() {
    MonitoredServiceDTO monitoredServiceDTO =
        monitoredServiceService
            .createDefault(builderFactory.getContext().getProjectParams(), serviceIdentifier, environmentIdentifier)
            .getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getName()).isEqualTo(serviceIdentifier + "_" + environmentIdentifier);
    assertThat(monitoredServiceDTO.getIdentifier()).isEqualTo(serviceIdentifier + "_" + environmentIdentifier);
    assertThat(monitoredServiceDTO.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(monitoredServiceDTO.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(monitoredServiceDTO.getServiceRef()).isEqualTo(serviceIdentifier);
    assertThat(monitoredServiceDTO.getEnvironmentRef()).isEqualTo(environmentIdentifier);
    assertThat(monitoredServiceDTO.getSources().getHealthSources().size()).isEqualTo(0);
    assertThat(monitoredServiceDTO.getDescription()).isEqualTo("Default Monitored Service");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_monitoredServiceDoesNotExists() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    assertThatThrownBy(
        () -> monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Monitored Source Entity  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            monitoredServiceIdentifier, accountId, orgIdentifier, projectIdentifier));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_serviceRefUpdateNotAllowed() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO.setServiceRef("new-service-ref");
    assertThatThrownBy(
        () -> monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("serviceRef update is not allowed");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_environmentRefUpdateNotAllowed() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO.setEnvironmentRef("new-environement-ref");
    assertThatThrownBy(
        () -> monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("environmentRef update is not allowed");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_monitoredServiceBasics() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO.setName("new-name");
    monitoredServiceDTO.setDescription("new-description");
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO).isEqualTo(monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceIdentifier);
    assertThat(monitoredService.getName()).isEqualTo("new-name");
    assertThat(monitoredService.getDesc()).isEqualTo("new-description");
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testSetHealthMonitoringFlag_monitoredServiceEnabled() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    HealthMonitoringFlagResponse healthMonitoringFlagResponse = monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getProjectParams(), monitoredServiceDTO.getIdentifier(), true);
    assertThat(healthMonitoringFlagResponse.isHealthMonitoringEnabled()).isTrue();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_deletingHealthSource() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());

    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    monitoredServiceDTO.getSources().setHealthSources(null);
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO).isEqualTo(monitoredServiceDTO);
    monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_addingHealthSource() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    AppDynamicsCVConfig alreadySavedCVConfig = (AppDynamicsCVConfig) cvConfigs.get(0);
    assertCVConfig(alreadySavedCVConfig, CVMonitoringCategory.ERRORS);

    HealthSource healthSource = builderFactory.createHealthSource(CVMonitoringCategory.PERFORMANCE);
    healthSource.setIdentifier("new-healthSource-identifier");
    monitoredServiceDTO.getSources().getHealthSources().add(healthSource);
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO).isEqualTo(monitoredServiceDTO);
    monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    assertThat(monitoredService.getHealthSourceIdentifiers().size()).isEqualTo(2);

    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    assertCVConfig((AppDynamicsCVConfig) cvConfigs.get(0), CVMonitoringCategory.ERRORS);

    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, "new-healthSource-identifier"));
    assertThat(cvConfigs.size()).isEqualTo(1);
    assertCVConfig((AppDynamicsCVConfig) cvConfigs.get(0), CVMonitoringCategory.PERFORMANCE);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_updatingHealthSource() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    AppDynamicsCVConfig alreadySavedCVConfig = (AppDynamicsCVConfig) cvConfigs.get(0);
    assertCVConfig(alreadySavedCVConfig, CVMonitoringCategory.ERRORS);

    HealthSourceSpec healthSourceSpec = builderFactory.createHealthSourceSpec(CVMonitoringCategory.PERFORMANCE);
    monitoredServiceDTO.getSources().getHealthSources().iterator().next().setSpec(healthSourceSpec);

    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO).isEqualTo(monitoredServiceDTO);
    monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    assertCommonMonitoredService(monitoredService, monitoredServiceDTO);
    assertThat(monitoredService.getHealthSourceIdentifiers().size()).isEqualTo(1);

    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(cvConfigs.size()).isEqualTo(1);
    assertCVConfig((AppDynamicsCVConfig) cvConfigs.get(0), CVMonitoringCategory.PERFORMANCE);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSetHealthMonitoringFlag() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, false);
    MonitoredService updatedMonitoredService = getMonitoredService(monitoredServiceIdentifier);
    getCVConfigs(updatedMonitoredService).forEach(cvConfig -> assertThat(cvConfig.isEnabled()).isFalse());
    assertThat(updatedMonitoredService.isEnabled()).isFalse();
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, true);
    updatedMonitoredService = getMonitoredService(monitoredServiceIdentifier);
    assertThat(updatedMonitoredService.isEnabled()).isTrue();
    getCVConfigs(updatedMonitoredService).forEach(cvConfig -> assertThat(cvConfig.isEnabled()).isTrue());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testSetHealthMonitoringFlag_withLicense() {
    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);
          Map<ModuleType, List<ModuleLicenseDTO>> testLicenses = new HashMap<>();
          List<ModuleLicenseDTO> srmModuleLicenseDTOS = new ArrayList<>();
          srmModuleLicenseDTOS.add(SRMModuleLicenseDTO.builder()
                                       .numberOfServices(5)
                                       .moduleType(ModuleType.SRM)
                                       .edition(Edition.FREE)
                                       .status(LicenseStatus.ACTIVE)
                                       .build());
          testLicenses.put(ModuleType.SRM, srmModuleLicenseDTOS);
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().allModuleLicenses(testLicenses).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });
    when(featureFlagService.isFeatureFlagEnabled(Mockito.any(), Mockito.any())).thenReturn(true);
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, false);
    MonitoredService updatedMonitoredService = getMonitoredService(monitoredServiceIdentifier);
    getCVConfigs(updatedMonitoredService).forEach(cvConfig -> assertThat(cvConfig.isEnabled()).isFalse());
    assertThat(updatedMonitoredService.isEnabled()).isFalse();
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, true);
    updatedMonitoredService = getMonitoredService(monitoredServiceIdentifier);
    assertThat(updatedMonitoredService.isEnabled()).isTrue();
    getCVConfigs(updatedMonitoredService).forEach(cvConfig -> assertThat(cvConfig.isEnabled()).isTrue());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testSetHealthMonitoringFlag_withOnlyFeatureFlag() {
    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);
          Map<ModuleType, List<ModuleLicenseDTO>> testLicenses = new HashMap<>();
          testLicenses.put(ModuleType.SRM, new ArrayList<>());
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().allModuleLicenses(testLicenses).build())));
          return call;
        });
    when(featureFlagService.isFeatureFlagEnabled(Mockito.any(), Mockito.any())).thenReturn(true);
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, false);
    MonitoredService updatedMonitoredService = getMonitoredService(monitoredServiceIdentifier);
    getCVConfigs(updatedMonitoredService).forEach(cvConfig -> assertThat(cvConfig.isEnabled()).isFalse());
    assertThat(updatedMonitoredService.isEnabled()).isFalse();
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, true);
    updatedMonitoredService = getMonitoredService(monitoredServiceIdentifier);
    assertThat(updatedMonitoredService.isEnabled()).isTrue();
    getCVConfigs(updatedMonitoredService).forEach(cvConfig -> assertThat(cvConfig.isEnabled()).isTrue());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testSetHealthMonitoringFlag_withInvalidSRMAccess() {
    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);
          Map<ModuleType, List<ModuleLicenseDTO>> testLicenses = new HashMap<>();
          testLicenses.put(ModuleType.SRM, new ArrayList<>());
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().allModuleLicenses(testLicenses).build())));
          return call;
        });
    when(featureFlagService.isFeatureFlagEnabled(Mockito.any(), Mockito.any())).thenReturn(true);
    when(featureFlagService.isFeatureFlagEnabled(Mockito.any(), eq(FeatureName.CVNG_ENABLED.name()))).thenReturn(false);
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, false);
    MonitoredService updatedMonitoredService = getMonitoredService(monitoredServiceIdentifier);
    getCVConfigs(updatedMonitoredService).forEach(cvConfig -> assertThat(cvConfig.isEnabled()).isFalse());
    assertThat(updatedMonitoredService.isEnabled()).isFalse();
    try {
      monitoredServiceService.setHealthMonitoringFlag(
          builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, true);
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo("Invalid License, Please Contact Harness Support");
    }
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testListEnvironments() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<EnvironmentResponse> environmentResponses =
        monitoredServiceService.listEnvironments(accountId, orgIdentifier, projectIdentifier);
    assertThat(environmentResponses.size()).isEqualTo(1);
    assertThat(environmentResponses.get(0).getEnvironment().getName()).isEqualTo("Mocked env name");
    assertThat(environmentResponses.get(0).getEnvironment().getIdentifier()).isEqualTo(environmentIdentifier);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testListMonitoredServices() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    List<MonitoredService> monitoredServices =
        monitoredServiceService.list(builderFactory.getContext().getProjectParams(),
            monitoredServiceDTO.getServiceRef(), monitoredServiceDTO.getEnvironmentRef());
    assertThat(monitoredServices.size()).isEqualTo(1);

    monitoredServices = monitoredServiceService.list(
        builderFactory.getContext().getProjectParams(), monitoredServiceDTO.getServiceRef(), null);
    assertThat(monitoredServices.size()).isEqualTo(1);

    monitoredServices = monitoredServiceService.list(
        builderFactory.getContext().getProjectParams(), null, monitoredServiceDTO.getEnvironmentRef());
    assertThat(monitoredServices.size()).isEqualTo(1);

    monitoredServices = monitoredServiceService.list(builderFactory.getContext().getProjectParams(), null, null);
    assertThat(monitoredServices.size()).isEqualTo(1);

    assertThatThrownBy(() -> monitoredServiceService.list(null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("projectParams is marked non-null but is null");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetHealthSources() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<HealthSourceDTO> healthSourceDTOS = monitoredServiceService.getHealthSources(environmentParams);
    assertThat(healthSourceDTOS.size()).isEqualTo(1);
    assertThat(healthSourceDTOS.get(0).getIdentifier())
        .isEqualTo(HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(healthSourceDTOS.get(0).getType()).isEqualTo(DataSourceType.APP_DYNAMICS);
    assertThat(healthSourceDTOS.get(0).getVerificationType()).isEqualTo(VerificationType.TIME_SERIES);
    assertThat(healthSourceDTOS.get(0).getName()).isEqualTo(healthSourceName);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetHealthSources_zeroHealthSources() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<HealthSourceDTO> healthSourceDTOS = monitoredServiceService.getHealthSources(environmentParams);
    assertThat(healthSourceDTOS).isEmpty();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetHealthSourcesWithMonitoredServiceIdentifier() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<HealthSourceDTO> healthSourceDTOS =
        monitoredServiceService.getHealthSources(projectParams, monitoredServiceIdentifier);
    assertThat(healthSourceDTOS.size()).isEqualTo(1);
    assertThat(healthSourceDTOS.get(0).getIdentifier())
        .isEqualTo(HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier));
    assertThat(healthSourceDTOS.get(0).getType()).isEqualTo(DataSourceType.APP_DYNAMICS);
    assertThat(healthSourceDTOS.get(0).getVerificationType()).isEqualTo(VerificationType.TIME_SERIES);
    assertThat(healthSourceDTOS.get(0).getName()).isEqualTo(healthSourceName);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetHealthSourcesWithMonitoredServiceIdentifier_zeroHealthSources() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .identifier(monitoredServiceIdentifier)
                                                  .sources(Sources.builder().build())
                                                  .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<HealthSourceDTO> healthSourceDTOS =
        monitoredServiceService.getHealthSources(projectParams, monitoredServiceIdentifier);
    assertThat(healthSourceDTOS).isEmpty();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetYamlTemplate() {
    assert (monitoredServiceService.getYamlTemplate(projectParams, MonitoredServiceType.APPLICATION))
        .equals("monitoredService:\n"
            + "  identifier:\n"
            + "  type: Application\n"
            + "  name:\n"
            + "  desc:\n"
            + "  projectIdentifier: " + projectParams.getProjectIdentifier() + "\n"
            + "  orgIdentifier: " + projectParams.getOrgIdentifier() + "\n"
            + "  serviceRef:\n"
            + "  environmentRef:\n"
            + "  sources:\n"
            + "    healthSources:\n"
            + "    changeSources:\n");

    assert (monitoredServiceService.getYamlTemplate(projectParams, MonitoredServiceType.INFRASTRUCTURE))
        .equals("monitoredService:\n"
            + "  identifier:\n"
            + "  type: Infrastructure\n"
            + "  name:\n"
            + "  desc:\n"
            + "  projectIdentifier: " + projectParams.getProjectIdentifier() + "\n"
            + "  orgIdentifier: " + projectParams.getOrgIdentifier() + "\n"
            + "  serviceRef:\n"
            + "  environmentRef:\n"
            + "  sources:\n"
            + "    healthSources:\n"
            + "    changeSources:\n");

    assert (monitoredServiceService.getYamlTemplate(projectParams, null))
        .equals("monitoredService:\n"
            + "  identifier:\n"
            + "  type: Application\n"
            + "  name:\n"
            + "  desc:\n"
            + "  projectIdentifier: " + projectParams.getProjectIdentifier() + "\n"
            + "  orgIdentifier: " + projectParams.getOrgIdentifier() + "\n"
            + "  serviceRef:\n"
            + "  environmentRef:\n"
            + "  sources:\n"
            + "    healthSources:\n"
            + "    changeSources:\n");
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetYamlTemplate_accountScope() {
    ProjectParams updatedProjectParams = projectParams;
    projectParams.setOrgIdentifier(null);
    projectParams.setProjectIdentifier(null);

    assert (monitoredServiceService.getYamlTemplate(updatedProjectParams, MonitoredServiceType.APPLICATION))
        .equals("monitoredService:\n"
            + "  identifier:\n"
            + "  type: Application\n"
            + "  name:\n"
            + "  desc:\n"
            + "  serviceRef:\n"
            + "  environmentRef:\n"
            + "  sources:\n"
            + "    healthSources:\n"
            + "    changeSources:\n");

    assert (monitoredServiceService.getYamlTemplate(updatedProjectParams, MonitoredServiceType.INFRASTRUCTURE))
        .equals("monitoredService:\n"
            + "  identifier:\n"
            + "  type: Infrastructure\n"
            + "  name:\n"
            + "  desc:\n"
            + "  serviceRef:\n"
            + "  environmentRef:\n"
            + "  sources:\n"
            + "    healthSources:\n"
            + "    changeSources:\n");
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testListOfMonitoredServices() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceDTO.setIdentifier("monitoredServiceIdentifier");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    String serviceRef1 = "delegate";
    String identifier1 = "monitoredServiceDTO1";
    monitoredServiceDTO.setServiceRef(serviceRef1);
    monitoredServiceDTO.setIdentifier(identifier1);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    String serviceRef2 = "nextgen-manager";
    String identifier2 = "monitoredServiceDTO2";
    monitoredServiceDTO.setServiceRef(serviceRef2);
    monitoredServiceDTO.setIdentifier(identifier2);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    String serviceRef3 = "ff";
    String identifier3 = "monitoredServiceDTO3";
    monitoredServiceDTO.setServiceRef(serviceRef3);
    monitoredServiceDTO.setEnvironmentRef("staging-env");
    monitoredServiceDTO.setIdentifier(identifier3);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    PageResponse pageResponse =
        monitoredServiceService.getList(projectParams, Collections.singletonList(environmentIdentifier), 0, 10, null);
    assertThat(pageResponse.getPageSize()).isEqualTo(10);
    assertThat(pageResponse.getPageItemCount()).isEqualTo(3);
    assertThat(pageResponse.getTotalItems()).isEqualTo(3);

    MonitoredServiceDTO dto1 = createMonitoredServiceDTO();
    dto1.setIdentifier("monitoredServiceIdentifier");

    MonitoredServiceDTO dto2 = createMonitoredServiceDTO();
    dto2.setServiceRef(serviceRef1);
    dto2.setIdentifier(identifier1);

    MonitoredServiceDTO dto3 = createMonitoredServiceDTO();
    dto3.setServiceRef(serviceRef2);
    dto3.setIdentifier(identifier2);

    List<MonitoredServiceResponse> responses = pageResponse.getContent();
    List<MonitoredServiceResponse> responseDTOs =
        responses.stream()
            .sorted(Comparator.comparing(a -> a.getMonitoredServiceDTO().getIdentifier()))
            .collect(Collectors.toList());

    assertThat(responseDTOs.get(0).getMonitoredServiceDTO().getIdentifier()).isEqualTo(dto2.getIdentifier());
    assertThat(responseDTOs.get(0).getMonitoredServiceDTO().getEnvironmentRef()).isEqualTo(dto2.getEnvironmentRef());
    assertThat(responseDTOs.get(0).getMonitoredServiceDTO().getServiceRef()).isEqualTo(dto2.getServiceRef());

    assertThat(responseDTOs.get(1).getMonitoredServiceDTO().getIdentifier()).isEqualTo(dto3.getIdentifier());
    assertThat(responseDTOs.get(1).getMonitoredServiceDTO().getEnvironmentRef()).isEqualTo(dto3.getEnvironmentRef());
    assertThat(responseDTOs.get(1).getMonitoredServiceDTO().getServiceRef()).isEqualTo(dto3.getServiceRef());

    assertThat(responseDTOs.get(2).getMonitoredServiceDTO().getIdentifier()).isEqualTo(dto1.getIdentifier());
    assertThat(responseDTOs.get(2).getMonitoredServiceDTO().getEnvironmentRef()).isEqualTo(dto1.getEnvironmentRef());
    assertThat(responseDTOs.get(2).getMonitoredServiceDTO().getServiceRef()).isEqualTo(dto1.getServiceRef());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetAll_WithIdentifierAndHealthSource() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    String serviceRef1 = "service1";
    String identifier1 = "monitoredService1";
    monitoredServiceDTO.setServiceRef(serviceRef1);
    monitoredServiceDTO.setIdentifier(identifier1);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    String serviceRef2 = "service2";
    String identifier2 = "monitoredService2";
    monitoredServiceDTO.setServiceRef(serviceRef2);
    monitoredServiceDTO.setIdentifier(identifier2);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    List<MonitoredServiceWithHealthSources> monitoredServiceWithHealthSourcesList =
        monitoredServiceService.getAllWithTimeSeriesHealthSources(projectParams);

    assertThat(monitoredServiceWithHealthSourcesList.size()).isEqualTo(2);
    assertThat(monitoredServiceWithHealthSourcesList.get(0).getIdentifier()).isEqualTo(identifier1);
    assertThat(monitoredServiceWithHealthSourcesList.get(0).getHealthSources().size()).isEqualTo(1);
    assertThat(
        monitoredServiceWithHealthSourcesList.get(0).getHealthSources().stream().findFirst().get().getIdentifier())
        .isEqualTo("healthSourceIdentifier");
    assertThat(monitoredServiceWithHealthSourcesList.get(1).getIdentifier()).isEqualTo(identifier2);
    assertThat(monitoredServiceWithHealthSourcesList.get(1).getHealthSources().size()).isEqualTo(1);
    assertThat(
        monitoredServiceWithHealthSourcesList.get(1).getHealthSources().stream().findFirst().get().getIdentifier())
        .isEqualTo("healthSourceIdentifier");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetAll_noHealthSources() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    String serviceRef1 = "service1";
    String identifier1 = "monitoredService1";
    monitoredServiceDTO.setServiceRef(serviceRef1);
    monitoredServiceDTO.setIdentifier(identifier1);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<MonitoredServiceWithHealthSources> monitoredServiceWithHealthSourcesList =
        monitoredServiceService.getAllWithTimeSeriesHealthSources(projectParams);
    assertThat(monitoredServiceWithHealthSourcesList.get(0).getHealthSources()).isEmpty();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetAll_noMonitoredService() {
    String serviceRef1 = "service1";
    String identifier1 = "monitoredService1";
    List<MonitoredServiceWithHealthSources> monitoredServiceWithHealthSourcesList =
        monitoredServiceService.getAllWithTimeSeriesHealthSources(projectParams);
    assertThat(monitoredServiceWithHealthSourcesList).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreate_withDependency() {
    String dependentService = generateUuid();
    ServiceDependencyDTO dependencyDTO =
        ServiceDependencyDTO.builder().monitoredServiceIdentifier(dependentService).build();
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceDTO.setDependencies(Sets.newHashSet(dependencyDTO));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceResponse response = monitoredServiceService.get(projectParams, monitoredServiceDTO.getIdentifier());
    assertThat(response.getMonitoredServiceDTO().getDependencies()).isNotNull();
    Set<ServiceDependencyDTO> dependencyDTOS = response.getMonitoredServiceDTO().getDependencies();
    assertThat(dependencyDTOS.size()).isEqualTo(1);
    assertThat(dependencyDTOS).containsExactly(dependencyDTO);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpdate_removeExistingDependency() {
    String dependentService = generateUuid();
    ServiceDependencyDTO dependencyDTO =
        ServiceDependencyDTO.builder().monitoredServiceIdentifier(dependentService).build();
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceDTO.setDependencies(Sets.newHashSet(dependencyDTO));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceResponse response = monitoredServiceService.get(projectParams, monitoredServiceDTO.getIdentifier());
    Set<ServiceDependencyDTO> dependencyDTOS = response.getMonitoredServiceDTO().getDependencies();
    assertThat(dependencyDTOS).containsExactly(dependencyDTO);

    monitoredServiceDTO.setDependencies(null);

    monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    response = monitoredServiceService.get(projectParams, monitoredServiceDTO.getIdentifier());
    assertThat(response.getMonitoredServiceDTO().getDependencies()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testList_withNoMonitoredService() {
    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse = monitoredServiceService.list(
        projectParams, Collections.singletonList(environmentIdentifier), 0, 10, null, false);
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(0);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(0);
    assertThat(monitoredServiceListDTOPageResponse.getPageItemCount()).isEqualTo(0);
    assertThat(monitoredServiceListDTOPageResponse.getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testList_withNoDependencies() {
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_2_local", "service_2", Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse = monitoredServiceService.list(
        projectParams, Collections.singletonList(environmentIdentifier), 0, 10, null, false);
    monitoredServiceListDTOPageResponse.getContent().sort(
        Comparator.comparing(MonitoredServiceListItemDTO::getIdentifier));
    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(2);
    assertThat(monitoredServiceListDTOPageResponse.getContent().get(0).getDependentHealthScore().size()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getContent().get(1).getDependentHealthScore().size()).isEqualTo(0);

    List<MonitoredServiceListItemDTO> monitoredServiceListItemDTOS = monitoredServiceListDTOPageResponse.getContent();
    assertThat(monitoredServiceListItemDTOS.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testList_withServicesAtRiskFilter() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_2_local", "service_2", Sets.newHashSet("service_1_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);

    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceIdentifier)
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);
    msOneHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);

    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.65, 0.55);
    hPersistence.save(msTwoHeatMap);
    msTwoHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.65, 0.55);
    hPersistence.save(msTwoHeatMap);

    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse = monitoredServiceService.list(
        projectParams, Collections.singletonList(environmentIdentifier), 0, 10, null, true);

    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(1);
    MonitoredServiceListItemDTO monitoredServiceListItemDTO = monitoredServiceListDTOPageResponse.getContent().get(0);
    assertThat(monitoredServiceListItemDTO.getName()).isEqualTo("service_1_local");
    assertThat(monitoredServiceListItemDTO.getIdentifier()).isEqualTo("service_1_local");
    assertThat(monitoredServiceListItemDTO.getServiceRef()).isEqualTo(serviceIdentifier);
    assertThat(monitoredServiceListItemDTO.getEnvironmentRef()).isEqualTo(environmentIdentifier);
    assertThat(monitoredServiceListItemDTO.getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(monitoredServiceListItemDTO.isHealthMonitoringEnabled()).isFalse();
    assertThat(monitoredServiceListItemDTO.getIdentifier()).isNotEqualTo("service_2_local");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testList_withSortedDependentHealthScores() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceOneDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_1_local", environmentParams.getServiceIdentifier(),
            Sets.newHashSet("service_2_local", "service_3_local", "service_4_local", "service_5_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_2_local", "service_2", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceThreeDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_3_local", "service_3", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceFourDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_4_local", "service_4", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceFiveDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_5_local", "service_5", Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceThreeDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceFourDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceFiveDTO);

    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceIdentifier)
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.15, 0.15);
    hPersistence.save(msOneHeatMap);

    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.85);
    hPersistence.save(msTwoHeatMap);

    HeatMap msThreeHeatMap = builderFactory.heatMapBuilder()
                                 .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                                 .heatMapResolution(FIVE_MIN)
                                 .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.50, 0.50);
    hPersistence.save(msThreeHeatMap);

    HeatMap msFourHeatMap = builderFactory.heatMapBuilder()
                                .monitoredServiceIdentifier(monitoredServiceFourDTO.getIdentifier())
                                .heatMapResolution(FIVE_MIN)
                                .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msFourHeatMap, endTime, -2.0, -2.0);
    hPersistence.save(msFourHeatMap);

    HeatMap msFiveHeatMap = builderFactory.heatMapBuilder()
                                .monitoredServiceIdentifier(monitoredServiceFiveDTO.getIdentifier())
                                .heatMapResolution(FIVE_MIN)
                                .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msFiveHeatMap, endTime, -0.5, -0.5);
    hPersistence.save(msFiveHeatMap);

    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListDTOPageResponse =
        monitoredServiceService.list(projectParams, null, 0, 10, null, false);

    assertThat(monitoredServiceListDTOPageResponse.getTotalPages()).isEqualTo(1);
    assertThat(monitoredServiceListDTOPageResponse.getTotalItems()).isEqualTo(5);

    List<MonitoredServiceListItemDTO> monitoredServiceListItemDTOs = monitoredServiceListDTOPageResponse.getContent();
    monitoredServiceListItemDTOs.sort(Comparator.comparing(MonitoredServiceListItemDTO::getIdentifier));
    MonitoredServiceListItemDTO monitoredServiceListItemDTO = monitoredServiceListItemDTOs.get(0);
    List<RiskData> dependentHealthScores = monitoredServiceListItemDTO.getDependentHealthScore();

    assertThat(monitoredServiceListItemDTO.getIdentifier()).isEqualTo("service_1_local");
    assertThat(monitoredServiceListItemDTO.getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(dependentHealthScores.get(0).getRiskStatus()).isEqualTo(Risk.UNHEALTHY);
    assertThat(dependentHealthScores.get(1).getRiskStatus()).isEqualTo(Risk.OBSERVE);
    assertThat(dependentHealthScores.get(2).getRiskStatus()).isEqualTo(Risk.NO_DATA);
    assertThat(dependentHealthScores.get(3).getRiskStatus()).isEqualTo(Risk.NO_ANALYSIS);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceFromDependencyGraph_withCorrectServiceIdentifier() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies("service_1_local",
        environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local", "service_3_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_2_local", "service_2", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceThreeDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_3_local", "service_3", Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceThreeDTO);

    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.15, 0.15);
    hPersistence.save(msOneHeatMap);

    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.85);
    hPersistence.save(msTwoHeatMap);

    HeatMap msThreeHeatMap = builderFactory.heatMapBuilder()
                                 .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                                 .heatMapResolution(FIVE_MIN)
                                 .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.50, 0.50);
    hPersistence.save(msThreeHeatMap);

    MonitoredServiceListItemDTO monitoredServiceListItemDTO = monitoredServiceService.getMonitoredServiceDetails(
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
            .build());
    assertThat(monitoredServiceListItemDTO.getIdentifier()).isEqualTo("service_1_local");
    assertThat(monitoredServiceListItemDTO.getName()).isEqualTo("service_1_local");
    assertThat(monitoredServiceListItemDTO.getServiceRef()).isEqualTo(environmentParams.getServiceIdentifier());
    assertThat(monitoredServiceListItemDTO.getEnvironmentRef()).isEqualTo(environmentParams.getEnvironmentIdentifier());
    assertThat(monitoredServiceListItemDTO.getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(monitoredServiceListItemDTO.isHealthMonitoringEnabled()).isFalse();
    assertThat(monitoredServiceListItemDTO.getServiceName()).isEqualTo("Mocked service name");
    assertThat(monitoredServiceListItemDTO.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(monitoredServiceListItemDTO.getCurrentHealthScore().getRiskStatus()).isEqualTo(Risk.HEALTHY);
    assertThat(monitoredServiceListItemDTO.getDependentHealthScore().size()).isEqualTo(2);
    assertThat(monitoredServiceListItemDTO.getDependentHealthScore().get(0).getRiskStatus()).isEqualTo(Risk.UNHEALTHY);
    assertThat(monitoredServiceListItemDTO.getDependentHealthScore().get(1).getRiskStatus()).isEqualTo(Risk.OBSERVE);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceFromDependencyGraph_withCorrectServiceIdentifierAndSLOHealthIndicator() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies("service_1_local",
        environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local", "service_3_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_2_local", "service_2", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceThreeDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_3_local", "service_3", Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceThreeDTO);

    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.15, 0.15);
    hPersistence.save(msOneHeatMap);

    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.85);
    hPersistence.save(msTwoHeatMap);

    HeatMap msThreeHeatMap = builderFactory.heatMapBuilder()
                                 .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                                 .heatMapResolution(FIVE_MIN)
                                 .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.50, 0.50);
    hPersistence.save(msThreeHeatMap);
    SLOHealthIndicator sloHealthIndicator = SLOHealthIndicator.builder()
                                                .accountId(builderFactory.getContext().getAccountId())
                                                .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                                .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                                .monitoredServiceIdentifier("service_1_local")
                                                .serviceLevelObjectiveIdentifier("sloIdentifier")
                                                .errorBudgetRemainingPercentage(70.0)
                                                .build();
    hPersistence.save(sloHealthIndicator);
    MonitoredServiceListItemDTO monitoredServiceListItemDTO =
        monitoredServiceService.getMonitoredServiceDetails(environmentParams);
    assertThat(monitoredServiceListItemDTO.getIdentifier()).isEqualTo("service_1_local");
    assertThat(monitoredServiceListItemDTO.getName()).isEqualTo("service_1_local");
    assertThat(monitoredServiceListItemDTO.getServiceRef()).isEqualTo(environmentParams.getServiceIdentifier());
    assertThat(monitoredServiceListItemDTO.getEnvironmentRef()).isEqualTo(environmentParams.getEnvironmentIdentifier());
    assertThat(monitoredServiceListItemDTO.getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(monitoredServiceListItemDTO.isHealthMonitoringEnabled()).isFalse();
    assertThat(monitoredServiceListItemDTO.getServiceName()).isEqualTo("Mocked service name");
    assertThat(monitoredServiceListItemDTO.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(monitoredServiceListItemDTO.getCurrentHealthScore().getRiskStatus()).isEqualTo(Risk.HEALTHY);
    assertThat(monitoredServiceListItemDTO.getDependentHealthScore().size()).isEqualTo(2);
    assertThat(monitoredServiceListItemDTO.getDependentHealthScore().get(0).getRiskStatus()).isEqualTo(Risk.UNHEALTHY);
    assertThat(monitoredServiceListItemDTO.getDependentHealthScore().get(1).getRiskStatus()).isEqualTo(Risk.OBSERVE);
    assertThat(monitoredServiceListItemDTO.getSloHealthIndicators().size()).isEqualTo(1);
    SloHealthIndicatorDTO sloHealthIndicatorResponse = monitoredServiceListItemDTO.getSloHealthIndicators().get(0);
    assertThat(sloHealthIndicatorResponse.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.OBSERVE);
    assertThat(sloHealthIndicatorResponse.getErrorBudgetRemainingPercentage()).isEqualTo(70.0);
    assertThat(sloHealthIndicatorResponse.getServiceLevelObjectiveIdentifier()).isEqualTo("sloIdentifier");
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetMonitoredService_withSLOHealthIndicator() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceDTO.setDependencies(Collections.emptySet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    SLOHealthIndicator sloHealthIndicator = SLOHealthIndicator.builder()
                                                .accountId(builderFactory.getContext().getAccountId())
                                                .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                                .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                                .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                .serviceLevelObjectiveIdentifier("sloIdentifier")
                                                .errorBudgetRemainingPercentage(70.0)
                                                .build();
    hPersistence.save(sloHealthIndicator);
    PageResponse<MonitoredServiceListItemDTO> monitoredServiceListItemDTOPageResponse = monitoredServiceService.list(
        projectParams, Collections.singletonList(environmentIdentifier), 0, 10, null, false);
    MonitoredServiceListItemDTO monitoredServiceListItemDTO =
        monitoredServiceListItemDTOPageResponse.getContent().get(0);
    assertThat(monitoredServiceListItemDTO.getSloHealthIndicators().size()).isEqualTo(1);
    SloHealthIndicatorDTO sloHealthIndicatorResponse = monitoredServiceListItemDTO.getSloHealthIndicators().get(0);
    assertThat(sloHealthIndicatorResponse.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.OBSERVE);
    assertThat(sloHealthIndicatorResponse.getErrorBudgetRemainingPercentage()).isEqualTo(70.0);
    assertThat(sloHealthIndicatorResponse.getServiceLevelObjectiveIdentifier()).isEqualTo("sloIdentifier");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceFromDependencyGraph_withWrongServiceIdentifier() {
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builder()
                                                        .accountIdentifier(environmentParams.getAccountIdentifier())
                                                        .orgIdentifier(environmentParams.getOrgIdentifier())
                                                        .projectIdentifier(environmentParams.getProjectIdentifier())
                                                        .monitoredServiceIdentifier("monitoredService")
                                                        .build();
    assertThatThrownBy(() -> monitoredServiceService.getMonitoredServiceDetails(monitoredServiceParams))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(String.format("Monitored service does not exists"));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCurrentAndDependentServicesScore_forNoData() {
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_2_local", "service_2", Sets.newHashSet("service_1_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    HealthScoreDTO healthScoreDTO = monitoredServiceService.getCurrentAndDependentServicesScore(
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
            .build());
    assertThat(healthScoreDTO.getCurrentHealthScore().getRiskStatus()).isEqualTo(Risk.NO_DATA);
    assertThat(healthScoreDTO.getDependentHealthScore().getRiskStatus()).isEqualTo(Risk.NO_DATA);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCurrentAndDependentServicesScore_withValidRisk() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies("service_1_local",
        environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local", "service_3_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_2_local", "service_2", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceThreeDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_3_local", "service_3", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceFourDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_4_local", "service_4", Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceThreeDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceFourDTO);
    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.15, 0.15);
    hPersistence.save(msOneHeatMap);
    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.70, 0.70);
    hPersistence.save(msTwoHeatMap);
    HeatMap msThreeHeatMap = builderFactory.heatMapBuilder()
                                 .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                                 .heatMapResolution(FIVE_MIN)
                                 .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.30, 0.30);
    hPersistence.save(msThreeHeatMap);
    HeatMap msFourHeatMap = builderFactory.heatMapBuilder()
                                .monitoredServiceIdentifier(monitoredServiceFourDTO.getIdentifier())
                                .heatMapResolution(FIVE_MIN)
                                .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msFourHeatMap, endTime, -0.5, -0.5);
    hPersistence.save(msFourHeatMap);

    HealthScoreDTO healthScoreDTO = monitoredServiceService.getCurrentAndDependentServicesScore(
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
            .build());
    assertThat(healthScoreDTO.getCurrentHealthScore().getRiskStatus()).isEqualTo(Risk.HEALTHY);
    assertThat(healthScoreDTO.getDependentHealthScore().getRiskStatus()).isEqualTo(Risk.NEED_ATTENTION);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCurrentAndDependentServicesScore_forNoAnalysis() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies("service_1_local",
        environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local", "service_3_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_2_local", "service_2", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceThreeDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_3_local", "service_3", Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceThreeDTO);
    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.15, 0.15);
    hPersistence.save(msOneHeatMap);
    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, -2.0, -2.0);
    hPersistence.save(msTwoHeatMap);
    HeatMap msThreeHeatMap = builderFactory.heatMapBuilder()
                                 .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                                 .heatMapResolution(FIVE_MIN)
                                 .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, -0.5, -0.5);
    hPersistence.save(msThreeHeatMap);

    HealthScoreDTO healthScoreDTO = monitoredServiceService.getCurrentAndDependentServicesScore(
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
            .build());
    assertThat(healthScoreDTO.getCurrentHealthScore().getRiskStatus()).isEqualTo(Risk.HEALTHY);
    assertThat(healthScoreDTO.getDependentHealthScore().getRiskStatus()).isEqualTo(Risk.NO_ANALYSIS);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCurrentAndDependentServicesScore_withNoDependencies() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);

    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .category(CVMonitoringCategory.PERFORMANCE)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);

    HealthScoreDTO healthScoreDTO = monitoredServiceService.getCurrentAndDependentServicesScore(
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
            .build());
    assertThat(healthScoreDTO.getCurrentHealthScore().getRiskStatus()).isEqualTo(Risk.NEED_ATTENTION);
    assertThat(healthScoreDTO.getDependentHealthScore()).isNull();
  }

  MonitoredServiceDTO createMonitoredServiceDTOWithCustomDependencies(
      String identifier, String serviceIdentifier, Set<String> dependentServiceIdentifiers) {
    return createMonitoredServiceDTOBuilder()
        .identifier(identifier)
        .name(identifier)
        .serviceRef(serviceIdentifier)
        .sources(MonitoredServiceDTO.Sources.builder()
                     .healthSources(Arrays.asList(builderFactory.createHealthSource(CVMonitoringCategory.ERRORS))
                                        .stream()
                                        .collect(Collectors.toSet()))
                     .build())
        .dependencies(
            Sets.newHashSet(dependentServiceIdentifiers.stream()
                                .map(id -> ServiceDependencyDTO.builder().monitoredServiceIdentifier(id).build())
                                .collect(Collectors.toSet())))
        .build();
  }

  MonitoredServiceDTO createMonitoredServiceDTO() {
    return createMonitoredServiceDTOBuilder()
        .sources(MonitoredServiceDTO.Sources.builder()
                     .healthSources(Arrays.asList(builderFactory.createHealthSource(CVMonitoringCategory.ERRORS))
                                        .stream()
                                        .collect(Collectors.toSet()))
                     .changeSources(Arrays.asList(builderFactory.getHarnessCDCurrentGenChangeSourceDTOBuilder().build())
                                        .stream()
                                        .collect(Collectors.toSet()))
                     .build())
        .build();
  }

  MonitoredServiceDTO createMonitoredServiceDTOWithPagerDutyChangeSource() {
    return createMonitoredServiceDTOBuilder()
        .sources(MonitoredServiceDTO.Sources.builder()
                     .healthSources(Arrays.asList(builderFactory.createHealthSource(CVMonitoringCategory.ERRORS))
                                        .stream()
                                        .collect(Collectors.toSet()))
                     .changeSources(Arrays.asList(builderFactory.getPagerDutyChangeSourceDTOBuilder().build())
                                        .stream()
                                        .collect(Collectors.toSet()))
                     .build())
        .build();
  }

  MonitoredServiceDTO createMonitoredServiceDTOWithDependencies() {
    return createMonitoredServiceDTOBuilder()
        .sources(MonitoredServiceDTO.Sources.builder()
                     .healthSources(Arrays.asList(builderFactory.createHealthSource(CVMonitoringCategory.ERRORS))
                                        .stream()
                                        .collect(Collectors.toSet()))
                     .build())
        .dependencies(
            Sets.newHashSet(ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build(),
                ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build()))
        .build();
  }

  private MonitoredServiceDTOBuilder createMonitoredServiceDTOBuilder(
      String monitoredServiceIdentifier, String serviceIdentifier, String environmentIdentifier) {
    return builderFactory.monitoredServiceDTOBuilder()
        .identifier(monitoredServiceIdentifier)
        .serviceRef(serviceIdentifier)
        .environmentRef(environmentIdentifier)
        .name(monitoredServiceName)
        .tags(tags);
  }

  private MonitoredServiceDTOBuilder createMonitoredServiceDTOBuilder() {
    return builderFactory.monitoredServiceDTOBuilder()
        .identifier(monitoredServiceIdentifier)
        .serviceRef(serviceIdentifier)
        .environmentRef(environmentIdentifier)
        .name(monitoredServiceName)
        .tags(tags);
  }

  private MonitoredServiceDTO createEnabledMonitoredService() {
    return createMonitoredServiceDTOBuilder().enabled(true).build();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCountOfServices_withNoMonitoredService() {
    CountServiceDTO countServiceDTO = monitoredServiceService.getCountOfServices(projectParams, null, null);
    assertThat(countServiceDTO.getAllServicesCount()).isEqualTo(0);
    assertThat(countServiceDTO.getServicesAtRiskCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCountOfServices_forNoData() {
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_2_local", "service_2", Sets.newHashSet("service_1_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    CountServiceDTO countServiceDTO = monitoredServiceService.getCountOfServices(projectParams, null, null);
    assertThat(countServiceDTO.getAllServicesCount()).isEqualTo(2);
    assertThat(countServiceDTO.getServicesAtRiskCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCountOfServices_withFilters() {
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_2_local", "service_2", Sets.newHashSet("service_1_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    CountServiceDTO countServiceDTO = monitoredServiceService.getCountOfServices(projectParams, null, "service");
    assertThat(countServiceDTO.getAllServicesCount()).isEqualTo(2);
    assertThat(countServiceDTO.getServicesAtRiskCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCountOfServices_withAllServicesAndServicesAtRisk() throws IllegalAccessException {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceOneDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_2_local", "service_2", Sets.newHashSet("service_1_local"));
    MonitoredServiceDTO monitoredServiceThreeDTO =
        createMonitoredServiceDTOWithCustomDependencies("service_3_local", "service_3", Sets.newHashSet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceThreeDTO);

    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);
    msOneHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceIdentifier)
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);

    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msTwoHeatMap);
    msTwoHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msTwoHeatMap);

    HeatMap msThreeHeatMap = builderFactory.heatMapBuilder()
                                 .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                                 .heatMapResolution(FIVE_MIN)
                                 .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.65, 0.55);
    hPersistence.save(msThreeHeatMap);
    msThreeHeatMap = builderFactory.heatMapBuilder()
                         .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                         .heatMapResolution(FIVE_MIN)
                         .category(CVMonitoringCategory.PERFORMANCE)
                         .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.65, 0.55);
    hPersistence.save(msThreeHeatMap);

    CountServiceDTO countServiceDTO = monitoredServiceService.getCountOfServices(projectParams, null, null);

    assertThat(countServiceDTO.getAllServicesCount()).isEqualTo(3);
    assertThat(countServiceDTO.getServicesAtRiskCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs() {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getContext().getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
            .build();
    List<String> cvConfigIds =
        cvConfigService.list(monitoredServiceParams).stream().map(CVConfig::getUuid).collect(Collectors.toList());
    List<String> verificationTaskIds = verificationTaskService.getServiceGuardVerificationTaskIds(
        builderFactory.getContext().getAccountId(), cvConfigIds);
    List<CVNGLogDTO> cvngLogDTOs =
        IntStream.range(0, 3)
            .mapToObj(index -> builderFactory.executionLogDTOBuilder().traceableId(verificationTaskIds.get(0)).build())
            .collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);

    LiveMonitoringLogsFilter liveMonitoringLogsFilter = LiveMonitoringLogsFilter.builder()
                                                            .logType(CVNGLogType.EXECUTION_LOG)
                                                            .startTime(startTime.toEpochMilli())
                                                            .endTime(endTime.toEpochMilli())
                                                            .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = monitoredServiceService.getCVNGLogs(
        monitoredServiceParams, liveMonitoringLogsFilter, PageParams.builder().page(0).size(10).build());

    // Since there is no unique check on CVNGLog we will have 3 log records
    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(3);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    ExecutionLogDTO executionLogDTOS = (ExecutionLogDTO) cvngLogDTOResponse.getContent().get(0);
    assertThat(executionLogDTOS.getAccountId()).isEqualTo(accountId);
    assertThat(executionLogDTOS.getTraceableId()).isEqualTo(verificationTaskIds.get(0));
    assertThat(executionLogDTOS.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    assertThat(executionLogDTOS.getType()).isEqualTo(CVNGLogType.EXECUTION_LOG);
    assertThat(executionLogDTOS.getLogLevel()).isEqualTo(LogLevel.INFO);
    assertThat(executionLogDTOS.getLog()).isEqualTo("Data Collection successfully completed.");
    assertThat(executionLogDTOS.getTags())
        .contains(CVNGLogTag.builder().key("startTime").value("1595846995000").type(TagType.TIMESTAMP).build(),
            CVNGLogTag.builder().key("endTime").value("1595847000000").type(TagType.TIMESTAMP).build(),
            CVNGLogTag.builder()
                .key("healthSourceIdentifier")
                .value("healthSourceIdentifier")
                .type(TagType.STRING)
                .build(),
            CVNGLogTag.builder().key("traceableId").value(verificationTaskIds.get(0)).type(TagType.DEBUG).build());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_withHealthSourceFilter() {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getContext().getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
            .build();
    List<CVConfig> cvConfigs = cvConfigService.list(monitoredServiceParams);
    List<String> cvConfigIds = cvConfigs.stream().map(CVConfig::getUuid).collect(Collectors.toList());
    List<String> verificationTaskIds = verificationTaskService.getServiceGuardVerificationTaskIds(
        builderFactory.getContext().getAccountId(), cvConfigIds);

    List<CVNGLogDTO> cvngLogDTOs =
        IntStream.range(0, 3)
            .mapToObj(index -> builderFactory.executionLogDTOBuilder().traceableId(verificationTaskIds.get(0)).build())
            .collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);

    LiveMonitoringLogsFilter liveMonitoringLogsFilter =
        LiveMonitoringLogsFilter.builder()
            .healthSourceIdentifiers(Arrays.asList(cvConfigs.get(0).getIdentifier()))
            .logType(CVNGLogType.EXECUTION_LOG)
            .startTime(startTime.toEpochMilli())
            .endTime(endTime.toEpochMilli())
            .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = monitoredServiceService.getCVNGLogs(
        monitoredServiceParams, liveMonitoringLogsFilter, PageParams.builder().page(0).size(10).build());
    // Since there is no unique check on CVNGLog we will have 3 log records
    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(3);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    ExecutionLogDTO executionLogDTOS = (ExecutionLogDTO) cvngLogDTOResponse.getContent().get(0);
    assertThat(executionLogDTOS.getAccountId()).isEqualTo(accountId);
    assertThat(executionLogDTOS.getTraceableId()).isEqualTo(verificationTaskIds.get(0));
    assertThat(executionLogDTOS.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    assertThat(executionLogDTOS.getType()).isEqualTo(CVNGLogType.EXECUTION_LOG);
    assertThat(executionLogDTOS.getLogLevel()).isEqualTo(LogLevel.INFO);
    assertThat(executionLogDTOS.getLog()).isEqualTo("Data Collection successfully completed.");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetNotificationRules_withCoolOffLogic() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());

    assertThat(((MonitoredServiceServiceImpl) monitoredServiceService).getNotificationRules(monitoredService).size())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetNotificationRules_withDisabled() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(false)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());

    assertThat(((MonitoredServiceServiceImpl) monitoredServiceService).getNotificationRules(monitoredService).size())
        .isEqualTo(0);
  }
  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSendNotification() throws IllegalAccessException, IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setName("rule2");
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleDTO.setConditions(
        Arrays.asList(NotificationRuleCondition.builder()
                          .type(NotificationRuleConditionType.CHANGE_OBSERVED)
                          .spec(ChangeObservedConditionSpec.builder()
                                    .changeCategories(Arrays.asList(ChangeCategory.DEPLOYMENT))
                                    .build())
                          .build()));
    NotificationRuleResponse notificationRuleResponseTwo =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build(),
            NotificationRuleRefDTO.builder()
                .notificationRuleRef(notificationRuleResponseTwo.getNotificationRule().getIdentifier())
                .enabled(true)
                .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    createHeatMaps(monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());

    clock = Clock.fixed(clock.instant().plus(1, ChronoUnit.HOURS), ZoneOffset.UTC);
    FieldUtils.writeField(monitoredServiceService, "clock", clock, true);
    when(notificationClient.sendNotificationAsync(any()))
        .thenReturn(NotificationResultWithoutStatus.builder().notificationId("notificationId").build());

    monitoredServiceService.handleNotification(monitoredService);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testSendNotification_incorrectIdentifier() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder().notificationRuleRef("wrongIdentifier").enabled(true).build()));
    assertThatThrownBy(
        () -> monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .hasMessage("NotificationRule does not exist for identifier: wrongIdentifier");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withHealthScoreCondition() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    createHeatMaps(monitoredServiceDTO);

    MonitoredServiceHealthScoreCondition condition =
        MonitoredServiceHealthScoreCondition.builder().threshold(20.0).period(600000).build();
    assertThat(((MonitoredServiceServiceImpl) monitoredServiceService)
                   .getHealthScoreNotificationData(monitoredService, condition)
                   .shouldSendNotification())
        .isTrue();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withHealthScoreCondition_withNoData() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());

    MonitoredServiceHealthScoreCondition condition =
        MonitoredServiceHealthScoreCondition.builder().threshold(20.0).period(600000).build();
    assertThat(((MonitoredServiceServiceImpl) monitoredServiceService)
                   .getHealthScoreNotificationData(monitoredService, condition)
                   .shouldSendNotification())
        .isFalse();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withChangeObservedCondition() throws IllegalAccessException {
    FieldUtils.writeField(monitoredServiceService, "clock", clock, true);
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    createActivity(monitoredServiceDTO);

    MonitoredServiceChangeObservedCondition condition = MonitoredServiceChangeObservedCondition.builder()
                                                            .changeCategories(Arrays.asList(ChangeCategory.DEPLOYMENT))
                                                            .build();

    assertThat(((MonitoredServiceServiceImpl) monitoredServiceService)
                   .getChangeObservedNotificationData(monitoredService, condition)
                   .shouldSendNotification())
        .isTrue();
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withChangeObservedCondition_forFFActivity() throws IllegalAccessException {
    FieldUtils.writeField(monitoredServiceService, "clock", clock, true);
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    createFFActivity(monitoredServiceDTO);

    MonitoredServiceChangeObservedCondition condition =
        MonitoredServiceChangeObservedCondition.builder()
            .changeCategories(Arrays.asList(ChangeCategory.FEATURE_FLAG))
            .build();

    assertThat(((MonitoredServiceServiceImpl) monitoredServiceService)
                   .getChangeObservedNotificationData(monitoredService, condition)
                   .shouldSendNotification())
        .isTrue();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withChangeImpactCondition() throws IllegalAccessException {
    FieldUtils.writeField(monitoredServiceService, "clock", clock, true);
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredService monitoredService = getMonitoredService(monitoredServiceDTO.getIdentifier());
    createHeatMaps(monitoredServiceDTO);
    createActivity(monitoredServiceDTO);

    MonitoredServiceChangeImpactCondition condition = MonitoredServiceChangeImpactCondition.builder()
                                                          .changeCategories(Arrays.asList(ChangeCategory.DEPLOYMENT))
                                                          .threshold(20.0)
                                                          .period(600000)
                                                          .build();
    assertThat(((MonitoredServiceServiceImpl) monitoredServiceService)
                   .getChangeImpactNotificationData(monitoredService, condition)
                   .shouldSendNotification())
        .isTrue();

    clock = Clock.fixed(clock.instant().plus(10, ChronoUnit.MINUTES), ZoneOffset.UTC);
    FieldUtils.writeField(monitoredServiceService, "clock", clock, true);
    assertThat(((MonitoredServiceServiceImpl) monitoredServiceService)
                   .getChangeImpactNotificationData(monitoredService, condition)
                   .shouldSendNotification())
        .isFalse();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetNotificationRules() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    PageResponse<NotificationRuleResponse> notificationRuleResponsePageResponse =
        monitoredServiceService.getNotificationRules(
            projectParams, monitoredServiceDTO.getIdentifier(), PageParams.builder().page(0).size(10).build());
    assertThat(notificationRuleResponsePageResponse.getTotalPages()).isEqualTo(1);
    assertThat(notificationRuleResponsePageResponse.getTotalItems()).isEqualTo(1);
    assertThat(notificationRuleResponsePageResponse.getContent().get(0).isEnabled()).isTrue();
    assertThat(notificationRuleResponsePageResponse.getContent().get(0).getNotificationRule().getIdentifier())
        .isEqualTo(notificationRuleDTO.getIdentifier());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testBeforeNotificationRuleDelete() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    notificationRuleDTO.setIdentifier("rule1");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule3");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder().notificationRuleRef("rule1").enabled(true).build(),
            NotificationRuleRefDTO.builder().notificationRuleRef("rule2").enabled(true).build(),
            NotificationRuleRefDTO.builder().notificationRuleRef("rule3").enabled(true).build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    assertThatThrownBy(()
                           -> monitoredServiceService.beforeNotificationRuleDelete(
                               builderFactory.getContext().getProjectParams(), "rule1"))
        .hasMessage("Deleting notification rule is used in Monitored Services, "
            + "Please delete the notification rule inside Monitored Services before deleting notification rule. Monitored Services : service_1_local");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withIncorrectNotificationRule() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    assertThatThrownBy(
        () -> monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO))
        .hasMessage("NotificationRule with identifier rule is of type SLO and cannot be added into MONITORED_SERVICE");
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testCreate_MonitoredServiceCreateAuditEvent() throws JsonProcessingException {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);
    assertThat(outboxEvent.getEventType()).isEqualTo(MonitoredServiceCreateEvent.builder().build().getEventType());
    MonitoredServiceCreateEvent monitoredServiceCreateEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(outboxEvent.getEventData(), MonitoredServiceCreateEvent.class);
    assertThat(monitoredServiceCreateEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(monitoredServiceCreateEvent.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(monitoredServiceCreateEvent.getProjectIdentifier()).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testCreate_withMonitoredServiceEnabled() {
    MonitoredServiceDTO monitoredServiceDTO = createEnabledMonitoredService();
    MonitoredServiceDTO savedMonitoredServiceDTO =
        monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO)
            .getMonitoredServiceDTO();
    assertThat(savedMonitoredServiceDTO.isEnabled()).isTrue();
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testUpdate_MonitoredServiceUpdateAuditEvent() throws JsonProcessingException {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO.setName("new-name");
    monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);
    assertThat(outboxEvent.getEventType()).isEqualTo(MonitoredServiceUpdateEvent.builder().build().getEventType());
    MonitoredServiceUpdateEvent monitoredServiceUpdateEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(outboxEvent.getEventData(), MonitoredServiceUpdateEvent.class);
    assertThat(monitoredServiceUpdateEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(monitoredServiceUpdateEvent.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(monitoredServiceUpdateEvent.getProjectIdentifier()).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testDelete_MonitoredServiceDeleteAuditEvent() throws JsonProcessingException {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceService.delete(builderFactory.getContext().getProjectParams(), monitoredServiceDTO.getIdentifier());
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);
    assertThat(outboxEvent.getEventType()).isEqualTo(MonitoredServiceDeleteEvent.builder().build().getEventType());
    MonitoredServiceDeleteEvent monitoredServiceDeleteEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(outboxEvent.getEventData(), MonitoredServiceDeleteEvent.class);
    assertThat(monitoredServiceDeleteEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(monitoredServiceDeleteEvent.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(monitoredServiceDeleteEvent.getProjectIdentifier()).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testSetHealthMonitoringFlag_MonitoredServiceToggleAuditEvent() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, false);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);
    assertThat(outboxEvent.getEventType()).isEqualTo(MonitoredServiceToggleEvent.builder().build().getEventType());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdate_withNotificationRuleDelete() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOWithCustomDependencies(
        "service_1_local", environmentParams.getServiceIdentifier(), Sets.newHashSet());
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceDTO.setNotificationRuleRefs(null);
    monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    NotificationRule notificationRule = notificationRuleService.getEntity(
        builderFactory.getContext().getProjectParams(), notificationRuleDTO.getIdentifier());

    assertThat(notificationRule).isNull();
  }

  private void createActivity(MonitoredServiceDTO monitoredServiceDTO) {
    useMockedPersistentLocker();
    Activity activity = builderFactory.getDeploymentActivityBuilder()
                            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                            .activityStartTime(clock.instant().minus(5, ChronoUnit.MINUTES))
                            .build();
    activityService.upsert(activity);
  }

  private void createFFActivity(MonitoredServiceDTO monitoredServiceDTO) {
    useMockedPersistentLocker();
    Activity activity = builderFactory.getInternalChangeActivity_FFBuilder()
                            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                            .eventTime(clock.instant().minus(5, ChronoUnit.MINUTES))
                            .activityStartTime(clock.instant().minus(5, ChronoUnit.MINUTES))
                            .build();
    activityService.upsert(activity);
  }

  private void createHeatMaps(MonitoredServiceDTO monitoredServiceDTO) {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceIdentifier)
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.85);
    hPersistence.save(msOneHeatMap);
    msOneHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.85);
    hPersistence.save(msOneHeatMap);
  }

  @SneakyThrows
  private void useMockedPersistentLocker() {
    FieldUtils.writeField(activityService, "persistentLocker", mockedPersistentLocker, true);
  }

  private void setStartTimeEndTimeAndRiskScoreWith5MinBucket(
      HeatMap heatMap, Instant endTime, double firstHalfRiskScore, double secondHalfRiskScore) {
    Instant startTime = endTime.minus(4, ChronoUnit.HOURS);
    heatMap.setHeatMapBucketStartTime(startTime);
    heatMap.setHeatMapBucketEndTime(endTime);
    List<HeatMapRisk> heatMapRisks = new ArrayList<>();

    for (Instant time = startTime; time.isBefore(startTime.plus(2, ChronoUnit.HOURS));
         time = time.plus(5, ChronoUnit.MINUTES)) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore(firstHalfRiskScore)
                           .startTime(time)
                           .endTime(time.plus(5, ChronoUnit.MINUTES))
                           .anomalousMetricsCount(1)
                           .anomalousLogsCount(2)
                           .build());
    }
    for (Instant time = startTime.plus(2, ChronoUnit.HOURS); time.isBefore(endTime);
         time = time.plus(5, ChronoUnit.MINUTES)) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore(secondHalfRiskScore)
                           .startTime(time)
                           .endTime(time.plus(5, ChronoUnit.MINUTES))
                           .anomalousMetricsCount(1)
                           .anomalousLogsCount(2)
                           .build());
    }
    heatMap.setHeatMapRisks(heatMapRisks);
  }

  void assertCommonMonitoredService(MonitoredService monitoredService, MonitoredServiceDTO monitoredServiceDTO) {
    assertThat(monitoredService.getName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(monitoredService.getIdentifier()).isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(monitoredService.getAccountId()).isEqualTo(accountId);
    assertThat(monitoredService.getDesc()).isEqualTo(monitoredServiceDTO.getDescription());
    assertThat(monitoredService.getServiceIdentifier()).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(monitoredService.getEnvironmentIdentifier()).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(monitoredService.getOrgIdentifier()).isEqualTo(monitoredServiceDTO.getOrgIdentifier());
    assertThat(monitoredService.getProjectIdentifier()).isEqualTo(monitoredServiceDTO.getProjectIdentifier());
    assertThat(monitoredService.getTags()).isEqualTo(TagMapper.convertToList(monitoredServiceDTO.getTags()));
    assertThat(monitoredService.getHealthSourceIdentifiers())
        .isEqualTo(monitoredServiceDTO.getSources()
                       .getHealthSources()
                       .stream()
                       .map(source -> source.getIdentifier())
                       .collect(toList()));
    assertThat(monitoredService.getProjectIdentifier()).isEqualTo(monitoredServiceDTO.getProjectIdentifier());
  }
  private void assertCVConfig(AppDynamicsCVConfig cvConfig, CVMonitoringCategory cvMonitoringCategory) {
    assertThat(cvConfig.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(cvConfig.getProductName()).isEqualTo(feature);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(healthSourceName);
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(cvConfig.getTierName()).isEqualTo(appTierName);
    assertThat(cvConfig.getApplicationName()).isEqualTo(applicationName);
    assertThat(cvConfig.getApplicationName()).isEqualTo(applicationName);
    assertThat(cvConfig.getCategory()).isEqualTo(cvMonitoringCategory);
  }

  private MonitoredService getMonitoredService(String identifier) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, accountId)
        .filter(MonitoredServiceKeys.orgIdentifier, orgIdentifier)
        .filter(MonitoredServiceKeys.projectIdentifier, projectIdentifier)
        .filter(MonitoredServiceKeys.identifier, identifier)
        .get();
  }

  private List<CVConfig> getCVConfigs(MonitoredService monitoredService) {
    return hPersistence.createQuery(CVConfig.class)
        .filter(CVConfigKeys.accountId, accountId)
        .filter(CVConfigKeys.orgIdentifier, orgIdentifier)
        .filter(CVConfigKeys.projectIdentifier, projectIdentifier)
        .field(CVConfigKeys.identifier)
        .in(monitoredService.getHealthSourceIdentifiers()
                .stream()
                .map(identifier
                    -> HealthSourceService.getNameSpacedIdentifier(monitoredService.getIdentifier(), identifier))
                .collect(toList()))
        .asList();
  }

  private void useChangeSourceServiceMock() throws IllegalAccessException {
    FieldUtils.writeField(monitoredServiceService, "changeSourceService", changeSourceServiceMock, true);
  }
}
