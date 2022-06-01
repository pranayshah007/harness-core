/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.NO_DATA;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.client.FakeNotificationClient;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.logsFilterParams.SLILogsFilter;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.ErrorBudgetRemainingMinutesConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetBurnRateCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetRemainingPercentageCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLONotificationRuleCondition;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOTarget;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec.WeeklyCalendarSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RollingSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator.SLOHealthIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.ServiceLevelObjectiveKeys;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator.AnalysisOrchestratorKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.notification.notificationclient.NotificationResultWithoutStatus;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceLevelObjectiveServiceImplTest extends CvNextGenTestBase {
  @Inject ServiceLevelObjectiveService serviceLevelObjectiveService;

  @Inject MonitoredServiceService monitoredServiceService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject SLOErrorBudgetResetService sloErrorBudgetResetService;
  @Inject CVNGLogService cvngLogService;
  @Inject HPersistence hPersistence;
  @Inject NotificationRuleService notificationRuleService;
  @Mock FakeNotificationClient notificationClient;
  @Inject private SLIRecordServiceImpl sliRecordService;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String identifier;
  String name;
  List<ServiceLevelIndicatorDTO> serviceLevelIndicators;
  SLOTarget sloTarget;
  SLOTarget calendarSloTarget;
  SLOTarget updatedSloTarget;
  String userJourneyIdentifier;
  String description;
  String monitoredServiceIdentifier;
  String healthSourceIdentifiers;
  ProjectParams projectParams;
  Map<String, String> tags;
  private BuilderFactory builderFactory;
  Clock clock;

  @Before
  public void setup() throws IllegalAccessException, ParseException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    identifier = "sloIdentifier";
    name = "sloName";
    monitoredServiceIdentifier = "monitoredServiceIdentifier";
    healthSourceIdentifiers = "healthSourceIdentifier";
    description = "description";
    tags = new HashMap<>();
    tags.put("tag1", "value1");
    tags.put("tag2", "value2");

    serviceLevelIndicators = Collections.singletonList(ServiceLevelIndicatorDTO.builder()
                                                           .identifier("sliIndicator")
                                                           .name("sliName")
                                                           .type(ServiceLevelIndicatorType.LATENCY)
                                                           .spec(ServiceLevelIndicatorSpec.builder()
                                                                     .type(SLIMetricType.RATIO)
                                                                     .spec(RatioSLIMetricSpec.builder()
                                                                               .eventType(RatioSLIMetricEventType.GOOD)
                                                                               .metric1("metric1")
                                                                               .metric2("metric2")
                                                                               .build())
                                                                     .build())
                                                           .build());

    sloTarget = SLOTarget.builder()
                    .type(SLOTargetType.ROLLING)
                    .sloTargetPercentage(80.0)
                    .spec(RollingSLOTargetSpec.builder().periodLength("30d").build())
                    .build();

    calendarSloTarget = SLOTarget.builder()
                            .type(SLOTargetType.CALENDER)
                            .sloTargetPercentage(80.0)
                            .spec(CalenderSLOTargetSpec.builder()
                                      .type(SLOCalenderType.WEEKLY)
                                      .spec(WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.MONDAY).build())

                                      .build())
                            .build();
    updatedSloTarget = SLOTarget.builder()
                           .type(SLOTargetType.ROLLING)
                           .sloTargetPercentage(80.0)
                           .spec(RollingSLOTargetSpec.builder().periodLength("60d").build())
                           .build();
    userJourneyIdentifier = "userJourney";

    projectParams = ProjectParams.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build();
    clock = Clock.fixed(Instant.parse("2020-08-21T10:02:06Z"), ZoneOffset.UTC);
    FieldUtils.writeField(notificationRuleService, "clock", clock, true);
    FieldUtils.writeField(sliRecordService, "clock", clock, true);
    FieldUtils.writeField(serviceLevelObjectiveService, "notificationRuleService", notificationRuleService, true);
    FieldUtils.writeField(serviceLevelObjectiveService, "sliRecordService", sliRecordService, true);
    FieldUtils.writeField(serviceLevelObjectiveService, "notificationClient", notificationClient, true);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_Success() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testCreate_validateSLOHealthIndicatorCreationTest() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    SLOHealthIndicator sloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(100.0);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_WithoutTagsSuccess() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setTags(new HashMap<>());
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_CalendarSLOTargetSuccess() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setTarget(calendarSloTarget);
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_withoutMonitoredServiceFailedValidation() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    assertThatThrownBy(() -> serviceLevelObjectiveService.create(projectParams, sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Monitored Source Entity with identifier %s is not present", sloDTO.getMonitoredServiceRef()));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testDelete_Success() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    boolean isDeleted = serviceLevelObjectiveService.delete(projectParams, sloDTO.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testDelete_validationFailedForIncorrectSLO() {
    ServiceLevelObjectiveDTO serviceLevelObjectiveDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, serviceLevelObjectiveDTO);
    serviceLevelObjectiveDTO.setIdentifier("incorrectSLOIdentifier");
    assertThatThrownBy(
        () -> serviceLevelObjectiveService.delete(projectParams, serviceLevelObjectiveDTO.getIdentifier()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            serviceLevelObjectiveDTO.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testDelete_deleteSLOHealthIndicator() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    boolean isDeleted = serviceLevelObjectiveService.delete(projectParams, sloDTO.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
    SLOHealthIndicator sloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    assertThat(sloHealthIndicator).isNull();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_Success() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    sloDTO.setDescription("newDescription");
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpdate_SuccessClearErrorBudgetReset() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    sloDTO.setDescription("newDescription");
    sloErrorBudgetResetService.resetErrorBudget(projectParams,
        builderFactory.getSLOErrorBudgetResetDTOBuilder()
            .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
            .build());
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    assertThat(
        sloErrorBudgetResetService.getErrorBudgetResets(builderFactory.getProjectParams(), sloDTO.getIdentifier()))
        .isNullOrEmpty();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpdate_validateSLOHealthIndicatorUpdate() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    SLOHealthIndicator existingSloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    sloDTO.setDescription("newDescription");
    ServiceLevelObjectiveResponse updatedServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    SLOHealthIndicator updatedSloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    assertThat(updatedSloHealthIndicator.getLastUpdatedAt())
        .isGreaterThan(existingSloHealthIndicator.getLastUpdatedAt());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_SLIUpdateSuccess() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 = sloDTO.getServiceLevelIndicators().get(0);
    serviceLevelIndicatorDTO1.setType(ServiceLevelIndicatorType.AVAILABILITY);
    RatioSLIMetricSpec ratioSLIMetricSpec = (RatioSLIMetricSpec) serviceLevelIndicatorDTO1.getSpec().getSpec();
    ratioSLIMetricSpec.setMetric1("newMetric");
    serviceLevelIndicatorDTO1.getSpec().setSpec(ratioSLIMetricSpec);
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO2 = builderFactory.getServiceLevelIndicatorDTOBuilder();
    serviceLevelIndicatorDTO2.setSliMissingDataType(SLIMissingDataType.GOOD);
    serviceLevelIndicatorDTO2.setSpec(ServiceLevelIndicatorSpec.builder()
                                          .type(SLIMetricType.RATIO)
                                          .spec(RatioSLIMetricSpec.builder()
                                                    .thresholdValue(20.0)
                                                    .thresholdType(ThresholdType.GREATER_THAN)
                                                    .eventType(RatioSLIMetricEventType.BAD)
                                                    .metric1("metric4")
                                                    .metric2("metric5")
                                                    .build())
                                          .build());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList = new ArrayList<>();
    serviceLevelIndicatorDTOList.add(serviceLevelIndicatorDTO1);
    serviceLevelIndicatorDTOList.add(serviceLevelIndicatorDTO2);
    sloDTO.setServiceLevelIndicators(serviceLevelIndicatorDTOList);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators())
        .isEqualTo(serviceLevelIndicatorDTOList);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_SLIUpdate() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 = sloDTO.getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec = (RatioSLIMetricSpec) serviceLevelIndicatorDTO1.getSpec().getSpec();
    ratioSLIMetricSpec.setThresholdType(ThresholdType.LESS_THAN);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    serviceLevelIndicatorDTO1.getSpec().setSpec(ratioSLIMetricSpec);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator = serviceLevelIndicatorService
                                     .getServiceLevelIndicator(builderFactory.getProjectParams(),
                                         updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()
                                             .getServiceLevelIndicators()
                                             .get(0)
                                             .getIdentifier())
                                     .getUuid();
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_SLIUpdateWithSLOTarget() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 = sloDTO.getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec = (RatioSLIMetricSpec) serviceLevelIndicatorDTO1.getSpec().getSpec();
    ratioSLIMetricSpec.setThresholdType(ThresholdType.LESS_THAN);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    serviceLevelIndicatorDTO1.getSpec().setSpec(ratioSLIMetricSpec);
    sloDTO.setTarget(calendarSloTarget);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator = serviceLevelIndicatorService
                                     .getServiceLevelIndicator(builderFactory.getProjectParams(),
                                         updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()
                                             .getServiceLevelIndicators()
                                             .get(0)
                                             .getIdentifier())
                                     .getUuid();

    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    String verificationTaskId =
        verificationTaskService.createSLIVerificationTask(builderFactory.getContext().getAccountId(), sliIndicator);
    AnalysisOrchestrator analysisOrchestrator =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
            .get();
    assertThat(analysisOrchestrator.getAnalysisStateMachineQueue().size()).isEqualTo(14);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_UpdateSLOTarget() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    sloDTO.setTarget(updatedSloTarget);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator = serviceLevelIndicatorService
                                     .getServiceLevelIndicator(builderFactory.getProjectParams(),
                                         updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()
                                             .getServiceLevelIndicators()
                                             .get(0)
                                             .getIdentifier())
                                     .getUuid();
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getTarget())
        .isEqualTo(updatedSloTarget);
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    String verificationTaskId =
        verificationTaskService.createSLIVerificationTask(builderFactory.getContext().getAccountId(), sliIndicator);
    AnalysisOrchestrator analysisOrchestrator =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
            .get();
    assertThat(analysisOrchestrator.getAnalysisStateMachineQueue().size()).isEqualTo(121);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_UpdateSLOTargetAfterCurrentStartTime() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    sloDTO.setTarget(calendarSloTarget);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator = serviceLevelIndicatorService
                                     .getServiceLevelIndicator(builderFactory.getProjectParams(),
                                         updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()
                                             .getServiceLevelIndicators()
                                             .get(0)
                                             .getIdentifier())
                                     .getUuid();
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getTarget())
        .isEqualTo(calendarSloTarget);
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    String verificationTaskId =
        verificationTaskService.createSLIVerificationTask(builderFactory.getContext().getAccountId(), sliIndicator);
    AnalysisOrchestrator analysisOrchestrator =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
            .get();
    assertThat(analysisOrchestrator).isNull();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_UpdateWithSLOTargetWithSLI() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 = sloDTO.getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec = (RatioSLIMetricSpec) serviceLevelIndicatorDTO1.getSpec().getSpec();
    ratioSLIMetricSpec.setThresholdType(ThresholdType.LESS_THAN);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    serviceLevelIndicatorDTO1.getSpec().setSpec(ratioSLIMetricSpec);
    sloDTO.setTarget(calendarSloTarget);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator = serviceLevelIndicatorService
                                     .getServiceLevelIndicator(builderFactory.getProjectParams(),
                                         updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()
                                             .getServiceLevelIndicators()
                                             .get(0)
                                             .getIdentifier())
                                     .getUuid();

    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    String verificationTaskId =
        verificationTaskService.createSLIVerificationTask(builderFactory.getContext().getAccountId(), sliIndicator);
    AnalysisOrchestrator analysisOrchestrator =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
            .get();
    ServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveService.getEntity(projectParams, sloDTO.getIdentifier());
    assertThat(analysisOrchestrator.getAnalysisStateMachineQueue().size()).isEqualTo(14);
    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
    ServiceLevelObjective.TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
    Instant startTime = timePeriod.getStartTime(serviceLevelObjective.getZoneOffset());
    assertThat(analysisOrchestrator.getAnalysisStateMachineQueue().get(0).getAnalysisStartTime()).isBefore(startTime);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_UpdateWithSLOTarget() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    ServiceLevelIndicator serviceLevelIndicatorOld = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), responseSLIDTO.getIdentifier());
    String sliIndicator = serviceLevelIndicatorOld.getUuid();
    sloDTO.setTarget(calendarSloTarget);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator = serviceLevelIndicatorService
                                     .getServiceLevelIndicator(builderFactory.getProjectParams(),
                                         updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()
                                             .getServiceLevelIndicators()
                                             .get(0)
                                             .getIdentifier())
                                     .getUuid();

    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    String verificationTaskId =
        verificationTaskService.createSLIVerificationTask(builderFactory.getContext().getAccountId(), sliIndicator);
    AnalysisOrchestrator analysisOrchestrator =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
            .get();
    assertThat(analysisOrchestrator).isNull();
    ServiceLevelIndicator serviceLevelIndicatorNew = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), responseSLIDTO.getIdentifier());
    assertThat(serviceLevelIndicatorOld.getVersion()).isEqualTo(serviceLevelIndicatorNew.getVersion());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_UpdateNewSLI() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 = sloDTO.getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec = (RatioSLIMetricSpec) serviceLevelIndicatorDTO1.getSpec().getSpec();
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    ratioSLIMetricSpec.setMetric1("metric7");
    serviceLevelIndicatorDTO1.getSpec().setSpec(ratioSLIMetricSpec);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator = serviceLevelIndicatorService
                                     .getServiceLevelIndicator(builderFactory.getProjectParams(),
                                         updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()
                                             .getServiceLevelIndicators()
                                             .get(0)
                                             .getIdentifier())
                                     .getUuid();
    assertThat(sliIndicator).isNotEqualTo(updatedSliIndicator);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_FailedWithEntityNotPresent() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    sloDTO.setIdentifier("newIdentifier");
    sloDTO.setDescription("newDescription");
    assertThatThrownBy(() -> serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            sloDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGet_IdentifierBasedQuery() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.get(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGet_errorBudgetRiskBasedQuery() {
    createMonitoredService();

    ServiceLevelObjectiveDTO sloDTO1 = builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("id1").build();
    serviceLevelObjectiveService.create(projectParams, sloDTO1);
    hPersistence.update(sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO1.getIdentifier()),
        hPersistence.createUpdateOperations(SLOHealthIndicator.class)
            .set(SLOHealthIndicatorKeys.errorBudgetRemainingPercentage, 10)
            .set(SLOHealthIndicatorKeys.errorBudgetRisk, ErrorBudgetRisk.getFromPercentage(10)));

    ServiceLevelObjectiveDTO sloDTO2 = builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("id2").build();
    serviceLevelObjectiveService.create(projectParams, sloDTO2);
    hPersistence.update(sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO2.getIdentifier()),
        hPersistence.createUpdateOperations(SLOHealthIndicator.class)
            .set(SLOHealthIndicatorKeys.errorBudgetRemainingPercentage, -10)
            .set(SLOHealthIndicatorKeys.errorBudgetRisk, ErrorBudgetRisk.getFromPercentage(-10)));

    PageResponse<ServiceLevelObjectiveResponse> serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.get(projectParams, 0, 1,
            ServiceLevelObjectiveFilter.builder().errorBudgetRisks(Arrays.asList(ErrorBudgetRisk.UNHEALTHY)).build());
    assertThat(serviceLevelObjectiveResponse.getContent().get(0).getServiceLevelObjectiveDTO()).isEqualTo(sloDTO1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetSLOForDashboard_errorBudgetRiskBasedQuery() {
    createMonitoredService();
    ServiceLevelObjectiveDTO sloDTO1 = builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("id1").build();
    serviceLevelObjectiveService.create(projectParams, sloDTO1);
    hPersistence.update(sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO1.getIdentifier()),
        hPersistence.createUpdateOperations(SLOHealthIndicator.class)
            .set(SLOHealthIndicatorKeys.errorBudgetRemainingPercentage, 10)
            .set(SLOHealthIndicatorKeys.errorBudgetRisk, ErrorBudgetRisk.getFromPercentage(10)));

    ServiceLevelObjectiveDTO sloDTO2 = builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("id2").build();
    serviceLevelObjectiveService.create(projectParams, sloDTO2);
    hPersistence.update(sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO2.getIdentifier()),
        hPersistence.createUpdateOperations(SLOHealthIndicator.class)
            .set(SLOHealthIndicatorKeys.errorBudgetRemainingPercentage, -10)
            .set(SLOHealthIndicatorKeys.errorBudgetRisk, ErrorBudgetRisk.getFromPercentage(-10)));

    PageResponse<ServiceLevelObjectiveResponse> serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.getSLOForDashboard(projectParams,
            SLODashboardApiFilter.builder().errorBudgetRisks(Arrays.asList(ErrorBudgetRisk.UNHEALTHY)).build(),
            PageParams.builder().page(0).size(10).build());
    assertThat(serviceLevelObjectiveResponse.getContent().get(0).getServiceLevelObjectiveDTO()).isEqualTo(sloDTO1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetRiskCount_Success() {
    createMonitoredService();
    ServiceLevelObjectiveDTO sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("id1").build();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    SLOHealthIndicator sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                                                .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                                                .errorBudgetRemainingPercentage(10)
                                                .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                                                .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("id2").build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(projectParams, sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(-10)
                             .errorBudgetRisk(ErrorBudgetRisk.EXHAUSTED)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("id3").build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(projectParams, sloDTO);

    SLORiskCountResponse sloRiskCountResponse =
        serviceLevelObjectiveService.getRiskCount(projectParams, SLODashboardApiFilter.builder().build());

    assertThat(sloRiskCountResponse.getTotalCount()).isEqualTo(3);
    assertThat(sloRiskCountResponse.getRiskCounts()).hasSize(5);
    assertThat(sloRiskCountResponse.getRiskCounts()
                   .stream()
                   .filter(rc -> rc.getErrorBudgetRisk().equals(ErrorBudgetRisk.EXHAUSTED))
                   .findAny()
                   .get()
                   .getDisplayName())
        .isEqualTo(ErrorBudgetRisk.EXHAUSTED.getDisplayName());
    assertThat(sloRiskCountResponse.getRiskCounts()
                   .stream()
                   .filter(rc -> rc.getErrorBudgetRisk().equals(ErrorBudgetRisk.EXHAUSTED))
                   .findAny()
                   .get()
                   .getCount())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetRiskCount_WithFilters() {
    createMonitoredService();
    ServiceLevelObjectiveDTO sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                          .identifier("id1")
                                          .userJourneyRef("uj1")
                                          .type(ServiceLevelIndicatorType.AVAILABILITY)
                                          .build();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    SLOHealthIndicator sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                                                .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                                                .errorBudgetRemainingPercentage(10)
                                                .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                                                .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                 .identifier("id5")
                 .userJourneyRef("uj2")
                 .type(ServiceLevelIndicatorType.AVAILABILITY)
                 .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(projectParams, sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(10)
                             .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                 .identifier("id2")
                 .userJourneyRef("uj1")
                 .type(ServiceLevelIndicatorType.AVAILABILITY)
                 .target(SLOTarget.builder()
                             .type(SLOTargetType.CALENDER)
                             .sloTargetPercentage(80.0)
                             .spec(CalenderSLOTargetSpec.builder()
                                       .type(SLOCalenderType.WEEKLY)
                                       .spec(WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.MONDAY).build())
                                       .build())
                             .build())
                 .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(projectParams, sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(-10)
                             .errorBudgetRisk(ErrorBudgetRisk.EXHAUSTED)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                 .identifier("id3")
                 .type(ServiceLevelIndicatorType.AVAILABILITY)
                 .userJourneyRef("uj3")
                 .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(projectParams, sloDTO);

    SLORiskCountResponse sloRiskCountResponse = serviceLevelObjectiveService.getRiskCount(projectParams,
        SLODashboardApiFilter.builder()
            .userJourneyIdentifiers(Arrays.asList("uj1"))
            .sliTypes(Arrays.asList(ServiceLevelIndicatorType.AVAILABILITY))
            .targetTypes(Arrays.asList(SLOTargetType.ROLLING))
            .build());

    assertThat(sloRiskCountResponse.getTotalCount()).isEqualTo(1);
    assertThat(sloRiskCountResponse.getRiskCounts()).hasSize(5);
    assertThat(sloRiskCountResponse.getRiskCounts()
                   .stream()
                   .filter(rc -> rc.getErrorBudgetRisk().equals(ErrorBudgetRisk.UNHEALTHY))
                   .findAny()
                   .get()
                   .getDisplayName())
        .isEqualTo(ErrorBudgetRisk.UNHEALTHY.getDisplayName());
    assertThat(sloRiskCountResponse.getRiskCounts()
                   .stream()
                   .filter(rc -> rc.getErrorBudgetRisk().equals(ErrorBudgetRisk.UNHEALTHY))
                   .findAny()
                   .get()
                   .getCount())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs() {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    List<String> serviceLevelIndicators = sloDTO.getServiceLevelIndicators()
                                              .stream()
                                              .map(ServiceLevelIndicatorDTO::getIdentifier)
                                              .collect(Collectors.toList());
    List<String> sliIds = serviceLevelIndicatorService.getEntities(projectParams, serviceLevelIndicators)
                              .stream()
                              .map(ServiceLevelIndicator::getUuid)
                              .collect(Collectors.toList());
    List<String> verificationTaskIds =
        verificationTaskService.getSLIVerificationTaskIds(projectParams.getAccountIdentifier(), sliIds);
    List<CVNGLogDTO> cvngLogDTOs =
        IntStream.range(0, 3)
            .mapToObj(index -> builderFactory.executionLogDTOBuilder().traceableId(verificationTaskIds.get(0)).build())
            .collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);

    SLILogsFilter sliLogsFilter = SLILogsFilter.builder()
                                      .logType(CVNGLogType.EXECUTION_LOG)
                                      .startTime(startTime.toEpochMilli())
                                      .endTime(endTime.toEpochMilli())
                                      .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = serviceLevelObjectiveService.getCVNGLogs(
        projectParams, sloDTO.getIdentifier(), sliLogsFilter, PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
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
  public void testGetNotificationRules_withCoolOffLogic() throws IllegalAccessException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    ServiceLevelObjective serviceLevelObjective = getServiceLevelObjective(sloDTO.getIdentifier());

    clock = Clock.fixed(clock.instant().plus(10, ChronoUnit.MINUTES), ZoneOffset.UTC);
    FieldUtils.writeField(serviceLevelObjectiveService, "clock", clock, true);
    assertThat(((ServiceLevelObjectiveServiceImpl) serviceLevelObjectiveService)
                   .getNotificationRules(serviceLevelObjective)
                   .size())
        .isEqualTo(0);

    clock = Clock.fixed(clock.instant().plus(50, ChronoUnit.MINUTES), ZoneOffset.UTC);
    FieldUtils.writeField(serviceLevelObjectiveService, "clock", clock, true);
    assertThat(((ServiceLevelObjectiveServiceImpl) serviceLevelObjectiveService)
                   .getNotificationRules(serviceLevelObjective)
                   .size())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSendNotification() throws IllegalAccessException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setName("rule2");
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleDTO.setConditions(
        Arrays.asList(NotificationRuleCondition.builder()
                          .type(NotificationRuleConditionType.ERROR_BUDGET_REMAINING_MINUTES)
                          .spec(ErrorBudgetRemainingMinutesConditionSpec.builder().threshold(9000.0).build())
                          .build()));
    NotificationRuleResponse notificationRuleResponseTwo =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build(),
            NotificationRuleRefDTO.builder()
                .notificationRuleRef(notificationRuleResponseTwo.getNotificationRule().getIdentifier())
                .enabled(true)
                .build()));
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    ServiceLevelObjective serviceLevelObjective = getServiceLevelObjective(sloDTO.getIdentifier());

    clock = Clock.fixed(clock.instant().plus(1, ChronoUnit.HOURS), ZoneOffset.UTC);
    FieldUtils.writeField(serviceLevelObjectiveService, "clock", clock, true);
    when(notificationClient.sendNotificationAsync(any()))
        .thenReturn(NotificationResultWithoutStatus.builder().notificationId("notificationId").build());

    serviceLevelObjectiveService.sendNotification(serviceLevelObjective);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withErrorBudgetRemainingPercentage() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    ServiceLevelObjective serviceLevelObjective = getServiceLevelObjective(sloDTO.getIdentifier());

    SLONotificationRuleCondition condition =
        SLOErrorBudgetRemainingPercentageCondition.builder().threshold(10.0).build();

    assertThat(((ServiceLevelObjectiveServiceImpl) serviceLevelObjectiveService)
                   .shouldSendNotification(serviceLevelObjective, condition))
        .isFalse();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withErrorBudgetBurnRate() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    ServiceLevelObjective serviceLevelObjective = getServiceLevelObjective(sloDTO.getIdentifier());
    createSLIRecords(serviceLevelObjective.getServiceLevelIndicators().get(0));

    SLOErrorBudgetBurnRateCondition condition = SLOErrorBudgetBurnRateCondition.builder()
                                                    .threshold(0.04)
                                                    .lookBackDuration(Duration.ofMinutes(10).toMillis())
                                                    .build();

    assertThat(((ServiceLevelObjectiveServiceImpl) serviceLevelObjectiveService)
                   .shouldSendNotification(serviceLevelObjective, condition))
        .isTrue();

    condition.setThreshold(0.05);
    assertThat(((ServiceLevelObjectiveServiceImpl) serviceLevelObjectiveService)
                   .shouldSendNotification(serviceLevelObjective, condition))
        .isFalse();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetNotificationRules() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    PageResponse<NotificationRuleResponse> notificationRuleResponsePageResponse =
        serviceLevelObjectiveService.getNotificationRules(
            projectParams, sloDTO.getIdentifier(), PageParams.builder().page(0).size(10).build());
    assertThat(notificationRuleResponsePageResponse.getTotalPages()).isEqualTo(1);
    assertThat(notificationRuleResponsePageResponse.getTotalItems()).isEqualTo(1);
    assertThat(notificationRuleResponsePageResponse.getContent().get(0).isEnabled()).isTrue();
    assertThat(notificationRuleResponsePageResponse.getContent().get(0).getNotificationRule().getIdentifier())
        .isEqualTo(notificationRuleDTO.getIdentifier());
  }

  private void createSLIRecords(String sliId) {
    Instant startTime = clock.instant().minus(Duration.ofMinutes(10));
    List<SLIRecord.SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, "verificationTaskId", 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      sliRecordParams.add(
          SLIRecordParam.builder().sliState(sliState).timeStamp(startTime.plus(Duration.ofMinutes(i))).build());
    }
    return sliRecordParams;
  }

  private ServiceLevelObjective getServiceLevelObjective(String identifier) {
    return hPersistence.createQuery(ServiceLevelObjective.class)
        .filter(ServiceLevelObjectiveKeys.accountId, accountId)
        .filter(ServiceLevelObjectiveKeys.orgIdentifier, orgIdentifier)
        .filter(ServiceLevelObjectiveKeys.projectIdentifier, projectIdentifier)
        .filter(ServiceLevelObjectiveKeys.identifier, identifier)
        .get();
  }

  private ServiceLevelObjectiveDTO createSLOBuilder() {
    return builderFactory.getServiceLevelObjectiveDTOBuilder().build();
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }
}
