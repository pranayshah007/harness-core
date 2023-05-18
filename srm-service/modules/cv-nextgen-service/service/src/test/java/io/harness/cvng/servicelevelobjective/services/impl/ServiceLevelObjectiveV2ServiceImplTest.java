/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.CVNGTestConstants.FIXED_TIME_FOR_TESTS;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.NO_DATA;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.client.FakeNotificationClient;
import io.harness.cvng.core.beans.TimeGraphResponse;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.logsFilterParams.SLILogsFilter;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveCreateEvent;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveDeleteEvent;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveUpdateEvent;
import io.harness.cvng.notification.beans.ErrorBudgetRemainingMinutesConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.entities.SLONotificationRule;
import io.harness.cvng.notification.entities.SLONotificationRule.SLONotificationRuleCondition;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.CompositeSLOFormulaType;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RollingSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SLOTarget;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.transformer.ServiceLevelObjectiveDetailsTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.notification.notificationclient.NotificationResultWithoutStatus;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceLevelObjectiveV2ServiceImplTest extends CvNextGenTestBase {
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject CVNGLogService cvngLogService;
  @Inject NotificationRuleService notificationRuleService;
  @Inject HPersistence hPersistence;
  @Mock CompositeSLOServiceImpl compositeSLOService;
  @Mock SideKickService sideKickService;

  @Inject private GraphDataService graphDataService;

  @Mock FakeNotificationClient notificationClient;

  @Inject private OutboxService outboxService;
  @Inject private SLIRecordService sliRecordService;

  @Inject private Map<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMap;

  @Inject private ServiceLevelObjectiveDetailsTransformer serviceLevelObjectiveDetailsTransformer;

  private BuilderFactory builderFactory;
  ProjectParams projectParams;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  ServiceLevelObjectiveV2DTO compositeSLODTO;
  CompositeServiceLevelObjective compositeServiceLevelObjective;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2;
  SimpleServiceLevelObjective simpleServiceLevelObjective1;
  SimpleServiceLevelObjective simpleServiceLevelObjective2;
  SLOTargetDTO calendarSloTarget;
  SLOTargetDTO updatedSloTarget;
  Clock clock;
  ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse;

  MonitoredServiceDTO monitoredServiceDTO;

  @Before
  public void setup() throws IllegalAccessException, ParseException {
    MockitoAnnotations.initMocks(this);
    clock = FIXED_TIME_FOR_TESTS;
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "compositeSLOService", compositeSLOService, true);
    FieldUtils.writeField(serviceLevelIndicatorService, "compositeSLOService", compositeSLOService, true);
    FieldUtils.writeField(compositeSLOService, "hPersistence", hPersistence, true);
    FieldUtils.writeField(compositeSLOService, "sideKickService", sideKickService, true);
    FieldUtils.writeField(compositeSLOService, "clock", clock, true);
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "sideKickService", sideKickService, true);
    when(compositeSLOService.isReferencedInCompositeSLO(any(), any())).thenCallRealMethod();
    when(compositeSLOService.getReferencedCompositeSLOs(any(), any())).thenCallRealMethod();
    when(compositeSLOService.shouldReset(any(), any())).thenCallRealMethod();
    when(compositeSLOService.shouldRecalculate(any(), any())).thenCallRealMethod();
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setProjectIdentifier("project");
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    projectParams = ProjectParams.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build();

    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                              .identifier("service1_env1")
                              .name("monitored service 1")
                              .sources(MonitoredServiceDTO.Sources.builder().build())
                              .serviceRef("service1")
                              .environmentRef("env1")
                              .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("simpleSLOIdentifier").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO1);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, simpleServiceLevelObjectiveDTO1.getIdentifier());

    simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("simpleSLOIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveSpec2.setServiceLevelIndicators(
        Collections.singletonList(builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build()));
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO2);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, simpleServiceLevelObjectiveDTO2.getIdentifier());

    compositeSLODTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                          .spec(CompositeServiceLevelObjectiveSpec.builder()
                                    .serviceLevelObjectivesDetails(Arrays.asList(
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                            .weightagePercentage(25.0)
                                            .accountId(simpleServiceLevelObjective2.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                            .build()))
                                    .build())
                          .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, compositeSLODTO);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, compositeSLODTO.getIdentifier());

    calendarSloTarget =
        SLOTargetDTO.builder()
            .type(SLOTargetType.CALENDER)
            .sloTargetPercentage(80.0)
            .spec(CalenderSLOTargetSpec.builder()
                      .type(SLOCalenderType.WEEKLY)
                      .spec(CalenderSLOTargetSpec.WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.MONDAY).build())

                      .build())
            .build();

    updatedSloTarget = SLOTargetDTO.builder()
                           .type(SLOTargetType.ROLLING)
                           .sloTargetPercentage(80.0)
                           .spec(RollingSLOTargetSpec.builder().periodLength("60d").build())
                           .build();
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "clock", clock, true);
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "sliRecordService", sliRecordService, true);
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "notificationClient", notificationClient, true);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreate_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    SLOTarget sloTarget = sloTargetTypeSLOTargetTransformerMap.get(sloDTO.getSloTarget().getType())
                              .getSLOTarget(sloDTO.getSloTarget().getSpec());
    assertThat(serviceLevelObjective.getTarget()).isEqualTo(sloTarget);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    SimpleServiceLevelObjective simpleServiceLevelObjective =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjective.getSliEvaluationType())
        .isEqualTo(serviceLevelIndicatorService
                       .getServiceLevelIndicator(projectParams,
                           ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
                               .getServiceLevelIndicators()
                               .get(0)
                               .getIdentifier())
                       .getSLIEvaluationType());
    sloDTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                 .identifier("compositeSloIdentifier1")
                 .spec(CompositeServiceLevelObjectiveSpec.builder()
                           .evaluationType(SLIEvaluationType.WINDOW)
                           .serviceLevelObjectivesDetails(
                               Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                                 .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                                 .weightagePercentage(50.0)
                                                 .accountId(simpleServiceLevelObjective1.getAccountId())
                                                 .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                                 .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                                 .build(),
                                   ServiceLevelObjectiveDetailsDTO.builder()
                                       .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                       .weightagePercentage(25.0)
                                       .accountId(simpleServiceLevelObjective2.getAccountId())
                                       .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                       .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                       .build(),
                                   ServiceLevelObjectiveDetailsDTO.builder()
                                       .serviceLevelObjectiveRef(simpleServiceLevelObjective.getIdentifier())
                                       .weightagePercentage(25.0)
                                       .accountId(simpleServiceLevelObjective.getAccountId())
                                       .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
                                       .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
                                       .build()))
                           .build())
                 .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    assertThat(verificationTaskService.getCompositeSLOVerificationTaskId(
                   builderFactory.getContext().getAccountId(), compositeServiceLevelObjective.getUuid()))
        .isEqualTo(compositeServiceLevelObjective.getUuid());
    serviceLevelObjective = serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    sloTarget = sloTargetTypeSLOTargetTransformerMap.get(sloDTO.getSloTarget().getType())
                    .getSLOTarget(sloDTO.getSloTarget().getSpec());
    assertThat(serviceLevelObjective.getTarget()).isEqualTo(sloTarget);
    assertThat(serviceLevelObjective.getSliEvaluationType())
        .isEqualTo(((CompositeServiceLevelObjectiveSpec) sloDTO.getSpec()).getEvaluationType());
    assertThat(((CompositeServiceLevelObjective) serviceLevelObjective).getCompositeSLOFormulaType())
        .isEqualTo(CompositeSLOFormulaType.WEIGHTED_AVERAGE);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateLeastPerformanceCompositeSLO_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    SLOTarget sloTarget = sloTargetTypeSLOTargetTransformerMap.get(sloDTO.getSloTarget().getType())
                              .getSLOTarget(sloDTO.getSloTarget().getSpec());
    assertThat(serviceLevelObjective.getTarget()).isEqualTo(sloTarget);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    SimpleServiceLevelObjective simpleServiceLevelObjective =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjective.getSliEvaluationType())
        .isEqualTo(serviceLevelIndicatorService
                       .getServiceLevelIndicator(projectParams,
                           ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
                               .getServiceLevelIndicators()
                               .get(0)
                               .getIdentifier())
                       .getSLIEvaluationType());
    sloDTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                 .identifier("compositeSloIdentifier1")
                 .spec(CompositeServiceLevelObjectiveSpec.builder()
                           .evaluationType(SLIEvaluationType.WINDOW)
                           .sloFormulaType(CompositeSLOFormulaType.LEAST_PERFORMANCE)
                           .serviceLevelObjectivesDetails(
                               Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                                 .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                                 .weightagePercentage(30.0)
                                                 .accountId(simpleServiceLevelObjective1.getAccountId())
                                                 .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                                 .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                                 .build(),
                                   ServiceLevelObjectiveDetailsDTO.builder()
                                       .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                       .weightagePercentage(100.0)
                                       .accountId(simpleServiceLevelObjective2.getAccountId())
                                       .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                       .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                       .build(),
                                   ServiceLevelObjectiveDetailsDTO.builder()
                                       .serviceLevelObjectiveRef(simpleServiceLevelObjective.getIdentifier())
                                       .weightagePercentage(25.0)
                                       .accountId(simpleServiceLevelObjective.getAccountId())
                                       .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
                                       .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
                                       .build()))
                           .build())
                 .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    assertThat(verificationTaskService.getCompositeSLOVerificationTaskId(
                   builderFactory.getContext().getAccountId(), compositeServiceLevelObjective.getUuid()))
        .isEqualTo(compositeServiceLevelObjective.getUuid());
    serviceLevelObjective = serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    sloTarget = sloTargetTypeSLOTargetTransformerMap.get(sloDTO.getSloTarget().getType())
                    .getSLOTarget(sloDTO.getSloTarget().getSpec());
    assertThat(serviceLevelObjective.getTarget()).isEqualTo(sloTarget);
    assertThat(serviceLevelObjective.getSliEvaluationType())
        .isEqualTo(((CompositeServiceLevelObjectiveSpec) sloDTO.getSpec()).getEvaluationType());
    assertThat(((CompositeServiceLevelObjective) serviceLevelObjective).getCompositeSLOFormulaType())
        .isEqualTo(CompositeSLOFormulaType.LEAST_PERFORMANCE);
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetEvaluationType() {
    // setting up monitored service for different project in same acc
    ProjectParams projectParamsTest = ProjectParams.builder()
                                          .accountIdentifier(projectParams.getAccountIdentifier())
                                          .orgIdentifier(generateUuid())
                                          .projectIdentifier(generateUuid())
                                          .build();
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .orgIdentifier(projectParamsTest.getOrgIdentifier())
                                                  .projectIdentifier(projectParamsTest.getProjectIdentifier())
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(projectParamsTest.getAccountIdentifier(), monitoredServiceDTO);

    // creating simple SLO in the different project with same sli and slo identifier
    simpleServiceLevelObjectiveDTO2 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                          .projectIdentifier(projectParamsTest.getProjectIdentifier())
                                          .orgIdentifier(projectParamsTest.getOrgIdentifier())
                                          .identifier("simpleSLOIdentifier")
                                          .build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(projectParamsTest, simpleServiceLevelObjectiveDTO2);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParamsTest, simpleServiceLevelObjectiveDTO2.getIdentifier());

    // validating that sli identifier is same for both
    assertThat(simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
        .isEqualTo(simpleServiceLevelObjective2.getServiceLevelIndicators().get(0));

    // creating composite slo using these two slos
    compositeSLODTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                          .identifier("new_composite_slo")
                          .orgIdentifier(null)
                          .projectIdentifier(null)
                          .spec(CompositeServiceLevelObjectiveSpec.builder()
                                    .serviceLevelObjectivesDetails(Arrays.asList(
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                            .weightagePercentage(25.0)
                                            .accountId(simpleServiceLevelObjective2.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                            .build()))
                                    .build())
                          .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, compositeSLODTO);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, compositeSLODTO.getIdentifier());

    // validating that the evaluation type is calculated correct.
    assertThat(compositeServiceLevelObjective.getSliEvaluationType()).isEqualTo(SLIEvaluationType.WINDOW);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateSimpleSLOWithConsecutiveMinutes_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    WindowBasedServiceLevelIndicatorSpec sliSpec =
        (WindowBasedServiceLevelIndicatorSpec) spec.getServiceLevelIndicators().get(0).getSpec();
    RatioSLIMetricSpec sliMetricSpec = (RatioSLIMetricSpec) sliSpec.getSpec();
    sliMetricSpec.setConsiderConsecutiveMinutes(5);
    sliMetricSpec.setConsiderAllConsecutiveMinutesFromStartAsBad(false);
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    SLOTarget sloTarget = sloTargetTypeSLOTargetTransformerMap.get(sloDTO.getSloTarget().getType())
                              .getSLOTarget(sloDTO.getSloTarget().getSpec());
    assertThat(serviceLevelObjective.getTarget()).isEqualTo(sloTarget);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    assertThat((RatioSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) ((SimpleServiceLevelObjectiveSpec)
                                                                                 serviceLevelObjectiveResponse
                                                                                     .getServiceLevelObjectiveV2DTO()
                                                                                     .getSpec())
                                         .getServiceLevelIndicators()
                                         .get(0)
                                         .getSpec())
                   .getSpec())
        .isEqualTo(sliMetricSpec);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_Calendar_Success_UpdateFailure() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .identifier("service2_env2")
                                                  .name("monitored service 2")
                                                  .sources(MonitoredServiceDTO.Sources.builder().build())
                                                  .serviceRef("service2")
                                                  .environmentRef("env2")
                                                  .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("new_simple_slo_1").build();
    simpleServiceLevelObjectiveDTO3.setSloTarget(calendarSloTarget);
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec3 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO3.getSpec();
    simpleServiceLevelObjectiveSpec3.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec3.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO3.setSpec(simpleServiceLevelObjectiveSpec3);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO3);
    SimpleServiceLevelObjective simpleServiceLevelObjective3 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            projectParams, simpleServiceLevelObjectiveDTO3.getIdentifier());

    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO4 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("new_simple_slo_2").build();
    simpleServiceLevelObjectiveDTO4.setSloTarget(calendarSloTarget);
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec4 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO4.getSpec();
    simpleServiceLevelObjectiveSpec4.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec4.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO4.setSpec(simpleServiceLevelObjectiveSpec4);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO4);
    SimpleServiceLevelObjective simpleServiceLevelObjective4 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            projectParams, simpleServiceLevelObjectiveDTO4.getIdentifier());

    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("compositeSloIdentifier1")
            .sloTarget(calendarSloTarget)
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective3.getIdentifier())
                                            .weightagePercentage(50.0)
                                            .accountId(simpleServiceLevelObjective3.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective3.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective3.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective4.getIdentifier())
                                  .weightagePercentage(50.0)
                                  .accountId(simpleServiceLevelObjective4.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective4.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective4.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    assertThat(verificationTaskService.getCompositeSLOVerificationTaskId(
                   builderFactory.getContext().getAccountId(), compositeServiceLevelObjective.getUuid()))
        .isEqualTo(compositeServiceLevelObjective.getUuid());

    calendarSloTarget.setSpec(
        CalenderSLOTargetSpec.builder()
            .type(SLOCalenderType.WEEKLY)
            .spec(CalenderSLOTargetSpec.WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.TUESDAY).build())
            .build());
    sloDTO.setSloTarget(calendarSloTarget);
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Composite SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s can not be created/updated as the compliance time period of the SLO and the associated SLOs is different.",
            "compositeSloIdentifier1", projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_Rolling_Failure() {
    compositeSLODTO.setSloTarget(updatedSloTarget);
    assertThatThrownBy(
        () -> serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO.getIdentifier(), compositeSLODTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Composite SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s can not be created/updated as the compliance time period of the SLO and the associated SLOs is different.",
            "compositeSloIdentifier", projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));

    compositeSLODTO.setSloTarget(calendarSloTarget);
    assertThatThrownBy(
        () -> serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO.getIdentifier(), compositeSLODTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Composite SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s can not be created/updated as the compliance time period of the SLO and the associated SLOs is different.",
            "compositeSloIdentifier", projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_Rolling_WithNoti_Success() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleDTO.setIdentifier("rule1");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule3");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("compositeSloIdentifier1")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(50.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                  .weightagePercentage(50.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .notificationRuleRefs(
                Arrays.asList(NotificationRuleRefDTO.builder().notificationRuleRef("rule1").enabled(true).build(),
                    NotificationRuleRefDTO.builder().notificationRuleRef("rule2").enabled(true).build(),
                    NotificationRuleRefDTO.builder().notificationRuleRef("rule3").enabled(true).build()))
            .build();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    assertThat(verificationTaskService.getCompositeSLOVerificationTaskId(
                   builderFactory.getContext().getAccountId(), compositeServiceLevelObjective.getUuid()))
        .isEqualTo(compositeServiceLevelObjective.getUuid());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_Calendar_WithNoti_Success() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .identifier("service2_env2")
                                                  .name("monitored service 2")
                                                  .sources(MonitoredServiceDTO.Sources.builder().build())
                                                  .serviceRef("service2")
                                                  .environmentRef("env2")
                                                  .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("new_simple_slo_1").build();
    simpleServiceLevelObjectiveDTO3.setSloTarget(calendarSloTarget);
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec3 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO3.getSpec();
    simpleServiceLevelObjectiveSpec3.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec3.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO3.setSpec(simpleServiceLevelObjectiveSpec3);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO3);
    SimpleServiceLevelObjective simpleServiceLevelObjective3 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            projectParams, simpleServiceLevelObjectiveDTO3.getIdentifier());

    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO4 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("new_simple_slo_2").build();
    simpleServiceLevelObjectiveDTO4.setSloTarget(calendarSloTarget);
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec4 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO4.getSpec();
    simpleServiceLevelObjectiveSpec4.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec4.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO4.setSpec(simpleServiceLevelObjectiveSpec4);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO4);
    SimpleServiceLevelObjective simpleServiceLevelObjective4 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            projectParams, simpleServiceLevelObjectiveDTO4.getIdentifier());

    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleDTO.setIdentifier("rule1");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule3");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("compositeSloIdentifier1")
            .sloTarget(calendarSloTarget)
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective3.getIdentifier())
                                            .weightagePercentage(50.0)
                                            .accountId(simpleServiceLevelObjective3.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective3.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective3.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective4.getIdentifier())
                                  .weightagePercentage(50.0)
                                  .accountId(simpleServiceLevelObjective4.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective4.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective4.getProjectIdentifier())
                                  .build()))
                      .build())
            .notificationRuleRefs(
                Arrays.asList(NotificationRuleRefDTO.builder().notificationRuleRef("rule1").enabled(true).build(),
                    NotificationRuleRefDTO.builder().notificationRuleRef("rule2").enabled(true).build(),
                    NotificationRuleRefDTO.builder().notificationRuleRef("rule3").enabled(true).build()))
            .build();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    assertThat(verificationTaskService.getCompositeSLOVerificationTaskId(
                   builderFactory.getContext().getAccountId(), compositeServiceLevelObjective.getUuid()))
        .isEqualTo(compositeServiceLevelObjective.getUuid());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreate_validationFailedForIncorrectSimpleSLO() {
    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("compositeSloIdentifier1")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(50.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("simpleslo1")
                                  .weightagePercentage(50.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.create(projectParams, sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
            "simpleslo1", projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreate_validationFailedForWeightagePercentage() {
    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("compositeSloIdentifier1")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(50.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                  .weightagePercentage(25.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.create(projectParams, sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "The weightage percentage of all the SLOs constituting the Composite SLO with identifier %s is 75.0. It should sum up to 100.",
            sloDTO.getIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_WithoutTagsSuccess() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setTags(new HashMap<>());
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreate_validateSLOHealthIndicatorCreationTest() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    SLOHealthIndicator sloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(100.0);
    SLOHealthIndicator compositeSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, compositeSLODTO.getIdentifier());
    assertThat(compositeSLOHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(100.0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_CalendarSLOTargetSuccess() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setSloTarget(calendarSloTarget);
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_withoutMonitoredServiceFailedValidation() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.create(projectParams, sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Monitored Source Entity with identifier %s is not present",
            simpleServiceLevelObjectiveSpec.getMonitoredServiceRef()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDelete_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    boolean isDeleted = serviceLevelObjectiveV2Service.delete(projectParams, sloDTO.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testForceDelete_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveV2Response =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    boolean isDeleted = serviceLevelObjectiveV2Service.forceDelete(projectParams, sloDTO.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(projectParams,
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2Response.getServiceLevelObjectiveV2DTO().getSpec())
            .getServiceLevelIndicators()
            .get(0)
            .getIdentifier());
    assertThat(serviceLevelIndicator).isNull();
    SLOHealthIndicator sloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    assertThat(sloHealthIndicator).isNull();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDelete_SimpleSLO_AssociatedWith_CompositeSLO_Failure() {
    List<String> referencedCompositeSLOIdentifiers =
        compositeSLOService.getReferencedCompositeSLOs(projectParams, simpleServiceLevelObjective1.getIdentifier())
            .stream()
            .map(CompositeServiceLevelObjective::getIdentifier)
            .collect(Collectors.toList());
    assertThatThrownBy(
        () -> serviceLevelObjectiveV2Service.delete(projectParams, simpleServiceLevelObjective1.getIdentifier()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Can't delete the SLO with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s. This is associated with Composite SLO with identifier%s %s.",
            simpleServiceLevelObjective1.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(),
            referencedCompositeSLOIdentifiers.size() > 1 ? "s" : "",
            String.join(",", referencedCompositeSLOIdentifiers)));
    boolean isDeleted = serviceLevelObjectiveV2Service.delete(projectParams, compositeSLODTO.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
    isDeleted = serviceLevelObjectiveV2Service.delete(projectParams, simpleServiceLevelObjective1.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testDelete_CompositeSLOSuccess() {
    boolean isDeleted = serviceLevelObjectiveV2Service.delete(projectParams, compositeSLODTO.getIdentifier());
    verify(sideKickService, times(1)).schedule(any(), any());
    assertThat(isDeleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDelete_validationFailedForIncorrectSLO() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    sloDTO.setIdentifier("incorrectSLOIdentifier");
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.delete(projectParams, sloDTO.getIdentifier()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
            sloDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testDelete_deleteSLOHealthIndicator() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    boolean isDeleted = serviceLevelObjectiveV2Service.delete(projectParams, sloDTO.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
    SLOHealthIndicator sloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    assertThat(sloHealthIndicator).isNull();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testSetMonitoredServiceSLOEnabled_success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjective.isEnabled()).isEqualTo(false);
    serviceLevelObjectiveV2Service.setMonitoredServiceSLOsEnableFlag(
        projectParams, simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(), true);
    serviceLevelObjective = serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjective.isEnabled()).isEqualTo(true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    sloDTO.setDescription("newDescription");
    sloDTO.setName("newName");
    sloDTO.setTags(new HashMap<String, String>() {
      {
        put("newtag1", "newvalue1");
        put("newtag2", "");
      }
    });
    sloDTO.setUserJourneyRefs(Collections.singletonList("newuserJourney"));
    sloDTO.setSloTarget(SLOTargetDTO.builder()
                            .type(SLOTargetType.ROLLING)
                            .sloTargetPercentage(90.0)
                            .spec(RollingSLOTargetSpec.builder().periodLength("30d").build())
                            .build());
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleDTO.setIdentifier("rule1");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule3");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder().notificationRuleRef("rule1").enabled(true).build(),
            NotificationRuleRefDTO.builder().notificationRuleRef("rule2").enabled(true).build(),
            NotificationRuleRefDTO.builder().notificationRuleRef("rule3").enabled(true).build()));
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicatorType(ServiceLevelIndicatorType.LATENCY);
    sloDTO.setSpec(simpleServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_SimpleSLO_AssociatedWith_CompositeSLO_Failure() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    SimpleServiceLevelObjective simpleServiceLevelObjective =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    ServiceLevelObjectiveV2DTO compositeSLODTO1 = compositeSLODTO;
    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        (CompositeServiceLevelObjectiveSpec) compositeSLODTO1.getSpec();
    compositeSLODTO1.setIdentifier("newCompositeSLO1");
    compositeServiceLevelObjectiveSpec.setServiceLevelObjectivesDetails(
        Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                          .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                          .weightagePercentage(50.0)
                          .accountId(simpleServiceLevelObjective1.getAccountId())
                          .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                          .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                          .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                .weightagePercentage(25.0)
                .accountId(simpleServiceLevelObjective2.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective.getIdentifier())
                .weightagePercentage(25.0)
                .accountId(simpleServiceLevelObjective.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
                .build()));
    compositeSLODTO1.setSpec(compositeServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response newCompositeSLO =
        serviceLevelObjectiveV2Service.create(projectParams, compositeSLODTO1);
    assertThat(newCompositeSLO.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO1);
    simpleServiceLevelObjectiveDTO1.setSloTarget(calendarSloTarget);
    List<String> referencedCompositeSLOIdentifiers =
        compositeSLOService.getReferencedCompositeSLOs(projectParams, simpleServiceLevelObjective1.getIdentifier())
            .stream()
            .map(CompositeServiceLevelObjective::getIdentifier)
            .collect(Collectors.toList());
    assertThatThrownBy(()
                           -> serviceLevelObjectiveV2Service.update(projectParams,
                               simpleServiceLevelObjective1.getIdentifier(), simpleServiceLevelObjectiveDTO1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Can't update the compliance time period for SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s as it is associated with Composite SLO with identifier%s %s.",
            simpleServiceLevelObjective1.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(),
            referencedCompositeSLOIdentifiers.size() > 1 ? "s" : "",
            String.join(", ", referencedCompositeSLOIdentifiers)));
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdate_simpleSLO_associatedWithCompositeSLO_failureSLIEvaluationType() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    SimpleServiceLevelObjective simpleServiceLevelObjective =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    ServiceLevelObjectiveV2DTO compositeSLODTO1 = compositeSLODTO;
    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        (CompositeServiceLevelObjectiveSpec) compositeSLODTO1.getSpec();
    compositeSLODTO1.setIdentifier("newCompositeSLO1");
    compositeServiceLevelObjectiveSpec.setServiceLevelObjectivesDetails(
        Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                          .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                          .weightagePercentage(50.0)
                          .accountId(simpleServiceLevelObjective1.getAccountId())
                          .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                          .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                          .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                .weightagePercentage(25.0)
                .accountId(simpleServiceLevelObjective2.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective.getIdentifier())
                .weightagePercentage(25.0)
                .accountId(simpleServiceLevelObjective.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
                .build()));
    compositeSLODTO1.setSpec(compositeServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response newCompositeSLO =
        serviceLevelObjectiveV2Service.create(projectParams, compositeSLODTO1);
    assertThat(newCompositeSLO.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO1);
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicators(
        Collections.singletonList(builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build()));
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec);
    List<String> referencedCompositeSLOIdentifiers =
        compositeSLOService.getReferencedCompositeSLOs(projectParams, simpleServiceLevelObjective1.getIdentifier())
            .stream()
            .map(CompositeServiceLevelObjective::getIdentifier)
            .collect(Collectors.toList());
    assertThatThrownBy(()
                           -> serviceLevelObjectiveV2Service.update(projectParams,
                               simpleServiceLevelObjective1.getIdentifier(), simpleServiceLevelObjectiveDTO1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Can't update the SLI evaluation type for SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s as it is associated with Composite SLO with identifier%s %s.",
            simpleServiceLevelObjective1.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(),
            referencedCompositeSLOIdentifiers.size() > 1 ? "s" : "",
            String.join(", ", referencedCompositeSLOIdentifiers)));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_SimpleSLO_AssociatedWith_CompositeSLO_Failure_BecauseOf_Duplicates() {
    ServiceLevelObjectiveV2DTO compositeSLODTO1 = compositeSLODTO;
    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        (CompositeServiceLevelObjectiveSpec) compositeSLODTO1.getSpec();
    compositeSLODTO1.setIdentifier("newCompositeSLO1");
    compositeSLODTO1.setName("newCompositeSLO1");
    compositeServiceLevelObjectiveSpec.setServiceLevelObjectivesDetails(
        Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                          .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                          .weightagePercentage(50.0)
                          .accountId(simpleServiceLevelObjective1.getAccountId())
                          .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                          .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                          .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                .weightagePercentage(50.0)
                .accountId(simpleServiceLevelObjective1.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                .build()));
    compositeSLODTO1.setSpec(compositeServiceLevelObjectiveSpec);
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.create(projectParams, compositeSLODTO1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("An SLO can't be referenced more than once"));
  }
  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_AddCompositeSLOSuccess() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    SimpleServiceLevelObjective simpleServiceLevelObjective =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    ServiceLevelObjectiveV2DTO compositeSLODTO1 = compositeSLODTO;
    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        (CompositeServiceLevelObjectiveSpec) compositeSLODTO1.getSpec();
    compositeSLODTO1.setDescription("newDescription");
    compositeSLODTO1.setName("newName");
    compositeSLODTO1.setTags(new HashMap<String, String>() {
      {
        put("newtag1", "newvalue1");
        put("newtag2", "");
      }
    });
    compositeSLODTO1.setUserJourneyRefs(Collections.singletonList("newuserJourney"));
    compositeServiceLevelObjectiveSpec.setServiceLevelObjectivesDetails(
        Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                          .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                          .weightagePercentage(50.0)
                          .accountId(simpleServiceLevelObjective1.getAccountId())
                          .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                          .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                          .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                .weightagePercentage(25.0)
                .accountId(simpleServiceLevelObjective2.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective.getIdentifier())
                .weightagePercentage(25.0)
                .accountId(simpleServiceLevelObjective.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
                .build()));
    compositeSLODTO1.setSpec(compositeServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO1.getIdentifier(), compositeSLODTO1);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO1);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());

    // Delete should trigger recalculate
    compositeServiceLevelObjectiveSpec.setServiceLevelObjectivesDetails(
        Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                          .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                          .weightagePercentage(50.0)
                          .accountId(simpleServiceLevelObjective1.getAccountId())
                          .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                          .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                          .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                .weightagePercentage(50.0)
                .accountId(simpleServiceLevelObjective2.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                .build()));
    compositeSLODTO1.setSpec(compositeServiceLevelObjectiveSpec);
    updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO1.getIdentifier(), compositeSLODTO1);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO1);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(1)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_CompositeSLO_WithNotificationRule_Success() {
    ServiceLevelObjectiveV2DTO compositeSLODTO1 = compositeSLODTO;
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleDTO.setIdentifier("rule1");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule3");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    compositeSLODTO1.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder().notificationRuleRef("rule1").enabled(true).build(),
            NotificationRuleRefDTO.builder().notificationRuleRef("rule2").enabled(true).build(),
            NotificationRuleRefDTO.builder().notificationRuleRef("rule3").enabled(true).build()));
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO1.getIdentifier(), compositeSLODTO1);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO1);
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());

    // deleting notification rule.
    compositeSLODTO1.setNotificationRuleRefs(new ArrayList<>());
    updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO1.getIdentifier(), compositeSLODTO1);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO1);
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_UpdateWeightageCompositeSLOSuccess() {
    doCallRealMethod().when(compositeSLOService).recalculate(any());
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    ServiceLevelObjectiveV2DTO compositeSLODTO1 = compositeSLODTO;
    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        (CompositeServiceLevelObjectiveSpec) compositeSLODTO1.getSpec();
    compositeSLODTO1.setDescription("newDescription");
    compositeSLODTO1.setName("newName");
    compositeSLODTO1.setTags(new HashMap<String, String>() {
      {
        put("newtag1", "newvalue1");
        put("newtag2", "");
      }
    });
    compositeSLODTO1.setUserJourneyRefs(Collections.singletonList("newuserJourney"));
    compositeServiceLevelObjectiveSpec.setServiceLevelObjectivesDetails(
        Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                          .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                          .weightagePercentage(50.0)
                          .accountId(simpleServiceLevelObjective1.getAccountId())
                          .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                          .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                          .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                .weightagePercentage(50.0)
                .accountId(simpleServiceLevelObjective2.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                .build()));
    compositeSLODTO1.setSpec(compositeServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO1.getIdentifier(), compositeSLODTO1);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO1);
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(1)).recalculate(any());
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, compositeServiceLevelObjective.getIdentifier());
    assertThat(compositeServiceLevelObjective.getVersion()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_AddAndDeleteCompositeSLOSuccess() {
    doCallRealMethod().when(compositeSLOService).reset(any());
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    SimpleServiceLevelObjective simpleServiceLevelObjective =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    ServiceLevelObjectiveV2DTO compositeSLODTO1 = compositeSLODTO;
    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        (CompositeServiceLevelObjectiveSpec) compositeSLODTO1.getSpec();
    compositeSLODTO1.setDescription("newDescription");
    compositeSLODTO1.setName("newName");
    compositeSLODTO1.setTags(new HashMap<String, String>() {
      {
        put("newtag1", "newvalue1");
        put("newtag2", "");
      }
    });
    compositeSLODTO1.setUserJourneyRefs(Collections.singletonList("newuserJourney"));
    compositeServiceLevelObjectiveSpec.setServiceLevelObjectivesDetails(
        Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                          .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                          .weightagePercentage(50.0)
                          .accountId(simpleServiceLevelObjective1.getAccountId())
                          .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                          .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                          .build(),
            ServiceLevelObjectiveDetailsDTO.builder()
                .serviceLevelObjectiveRef(simpleServiceLevelObjective.getIdentifier())
                .weightagePercentage(50.0)
                .accountId(simpleServiceLevelObjective.getAccountId())
                .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
                .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
                .build()));
    compositeSLODTO1.setSpec(compositeServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO1.getIdentifier(), compositeSLODTO1);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO1);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, compositeServiceLevelObjective.getIdentifier());
    assertThat(compositeServiceLevelObjective.getVersion()).isEqualTo(1);
    assertThat(compositeServiceLevelObjective.getStartedAt())
        .isGreaterThan(compositeServiceLevelObjective.getCreatedAt());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_validateSLOHealthIndicatorUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    SLOHealthIndicator existingSloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    sloDTO.setDescription("newDescription");
    serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    SLOHealthIndicator updatedSloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, sloDTO.getIdentifier());
    assertThat(updatedSloHealthIndicator.getLastUpdatedAt())
        .isGreaterThan(existingSloHealthIndicator.getLastUpdatedAt());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_SLIUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO1;
    ServiceLevelIndicatorDTO responseSLIDTO =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec =
        (RatioSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).getSpec();
    ratioSLIMetricSpec.setThresholdType(ThresholdType.LESS_THAN);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(ratioSLIMetricSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(1)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_SLIMissingDataTypeUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO1;
    ServiceLevelIndicatorDTO responseSLIDTO =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), responseSLIDTO.getIdentifier());
    String sliIndicator = serviceLevelIndicator.getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    WindowBasedServiceLevelIndicatorSpec serviceLevelIndicatorSpec =
        (WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec();
    serviceLevelIndicatorSpec.setSliMissingDataType(SLIMissingDataType.BAD);
    serviceLevelIndicatorDTO1.setSpec(serviceLevelIndicatorSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    ServiceLevelIndicator updatedServiceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            ((SimpleServiceLevelObjectiveSpec) updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                    .getSpec())
                .getServiceLevelIndicators()
                .get(0)
                .getIdentifier());
    String updatedSliIndicator = updatedServiceLevelIndicator.getUuid();
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    assertThat(serviceLevelIndicator.getSliMissingDataType())
        .isNotEqualTo(updatedServiceLevelIndicator.getSliMissingDataType());
    serviceLevelIndicator.setSliMissingDataType(SLIMissingDataType.BAD);
    assertThat(serviceLevelIndicator.getSliMissingDataType())
        .isEqualTo(updatedServiceLevelIndicator.getSliMissingDataType());
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(1)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_SLIMetricValueForRatioUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO1;
    ServiceLevelIndicatorDTO responseSLIDTO =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec =
        (RatioSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).getSpec();
    ratioSLIMetricSpec.setThresholdValue(578.02);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(ratioSLIMetricSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(1)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_SLIMetricValueForThresholdUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO2;
    ServiceLevelIndicatorDTO responseSLIDTO =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    ThresholdSLIMetricSpec thresholdSLIMetricSpec =
        (ThresholdSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).getSpec();
    thresholdSLIMetricSpec.setThresholdValue(578.02);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(thresholdSLIMetricSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(1)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_SLIOperatorForThresholdUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO2;
    ServiceLevelIndicatorDTO responseSLIDTO =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    ThresholdSLIMetricSpec thresholdSLIMetricSpec =
        (ThresholdSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).getSpec();
    thresholdSLIMetricSpec.setThresholdType(ThresholdType.LESS_THAN);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(thresholdSLIMetricSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    verify(compositeSLOService, times(0)).reset(any());
    verify(compositeSLOService, times(1)).recalculate(any());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_SLIUpdateWithSLOTarget() throws ParseException {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getSpec())
            .getServiceLevelIndicators()
            .get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec =
        (RatioSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).getSpec();
    ratioSLIMetricSpec.setThresholdType(ThresholdType.LESS_THAN);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(ratioSLIMetricSpec);
    sloDTO.setSloTarget(calendarSloTarget);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();

    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_UpdateSLOTarget() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getSpec())
            .getServiceLevelIndicators()
            .get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    sloDTO.setSloTarget(updatedSloTarget);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getSloTarget())
        .isEqualTo(updatedSloTarget);
    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_UpdateWithSLOTargetWithSLI() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getSpec())
            .getServiceLevelIndicators()
            .get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec =
        (RatioSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).getSpec();
    ratioSLIMetricSpec.setThresholdType(ThresholdType.LESS_THAN);
    serviceLevelIndicatorDTO1.setIdentifier(responseSLIDTO.getIdentifier());
    serviceLevelIndicatorDTO1.setName(responseSLIDTO.getName());
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(ratioSLIMetricSpec);
    sloDTO.setSloTarget(calendarSloTarget);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();

    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_UpdateWithSLOTarget() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpecFromResponse =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getSpec();
    ServiceLevelIndicatorDTO responseSLIDTO =
        simpleServiceLevelObjectiveSpecFromResponse.getServiceLevelIndicators().get(0);
    ServiceLevelIndicator serviceLevelIndicatorOld = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), responseSLIDTO.getIdentifier());
    String sliIndicator = serviceLevelIndicatorOld.getUuid();
    sloDTO.setSloTarget(calendarSloTarget);
    ServiceLevelObjectiveV2Response updatedServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updatedServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();

    assertThat(sliIndicator).isEqualTo(updatedSliIndicator);
    ServiceLevelIndicator serviceLevelIndicatorNew = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), responseSLIDTO.getIdentifier());
    assertThat(serviceLevelIndicatorOld.getVersion()).isEqualTo(serviceLevelIndicatorNew.getVersion());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_UpdateNewSLIRatio() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO1;
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpecFromResponse =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    ServiceLevelIndicatorDTO responseSLIDTO =
        simpleServiceLevelObjectiveSpecFromResponse.getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec ratioSLIMetricSpec =
        (RatioSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).getSpec();
    ratioSLIMetricSpec.setMetric1("metric7");
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(ratioSLIMetricSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updatedServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updatedServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(sliIndicator).isNotEqualTo(updatedSliIndicator);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_UpdateNewSLIThreshold() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO2;
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpecFromResponse =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    ServiceLevelIndicatorDTO responseSLIDTO =
        simpleServiceLevelObjectiveSpecFromResponse.getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    ThresholdSLIMetricSpec thresholdSLIMetricSpec =
        (ThresholdSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).getSpec();
    thresholdSLIMetricSpec.setMetric1("metric7");
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(thresholdSLIMetricSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updatedServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updatedServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(sliIndicator).isNotEqualTo(updatedSliIndicator);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_UpdateNewSLIRatioToThreshold() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO1;
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpecFromResponse =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    ServiceLevelIndicatorDTO responseSLIDTO =
        simpleServiceLevelObjectiveSpecFromResponse.getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    ThresholdSLIMetricSpec thresholdSLIMetricSpec = ThresholdSLIMetricSpec.builder()
                                                        .metric1("metric1")
                                                        .thresholdType(ThresholdType.GREATER_THAN)
                                                        .thresholdValue(200.0)
                                                        .build();
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(thresholdSLIMetricSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updatedServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updatedServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(sliIndicator).isNotEqualTo(updatedSliIndicator);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_UpdateNewSLIThresholdtoRatio() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO2;
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpecFromResponse =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    ServiceLevelIndicatorDTO responseSLIDTO =
        simpleServiceLevelObjectiveSpecFromResponse.getServiceLevelIndicators().get(0);
    String sliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), responseSLIDTO.getIdentifier())
            .getUuid();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 =
        ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec()).getServiceLevelIndicators().get(0);
    RatioSLIMetricSpec thresholdSLIMetricSpec = RatioSLIMetricSpec.builder()
                                                    .metric1("metric1")
                                                    .metric2("metric2")
                                                    .thresholdType(ThresholdType.GREATER_THAN)
                                                    .thresholdValue(200.0)
                                                    .build();
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(thresholdSLIMetricSpec);
    ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO1.getSpec()).setSpec(thresholdSLIMetricSpec);
    ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
        .setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO1));
    ServiceLevelObjectiveV2Response updatedServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    String updatedSliIndicator =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjectiveSpec) updatedServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
                        .getSpec())
                    .getServiceLevelIndicators()
                    .get(0)
                    .getIdentifier())
            .getUuid();
    assertThat(sliIndicator).isNotEqualTo(updatedSliIndicator);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_HealthSourceUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO1;
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    simpleServiceLevelObjectiveSpec.setHealthSourceRef("newHealthSourceRef");
    sloDTO.setSpec(simpleServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_MonitoredServiceUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = simpleServiceLevelObjectiveDTO1;
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setServiceRef("service12");
    monitoredServiceDTO.setEnvironmentRef("env12");
    monitoredServiceDTO.setIdentifier("service12_env12");
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef("service12_env12");
    sloDTO.setSpec(simpleServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    verify(compositeSLOService, times(1)).reset(any());
    verify(compositeSLOService, times(0)).recalculate(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_FailedWithEntityNotPresent() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    sloDTO.setIdentifier("newIdentifier");
    sloDTO.setDescription("newDescription");
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
            sloDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGet_IdentifierBasedQuery() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    ServiceLevelObjectiveV2Response serviceLevelObjectiveV2Response =
        serviceLevelObjectiveV2Service.get(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjectiveV2Response.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGet_IdentifierList() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    ProjectParams accountProjectParams =
        ProjectParams.builder().accountIdentifier(projectParams.getAccountIdentifier()).build();
    List<AbstractServiceLevelObjective> serviceLevelObjectiveList =
        serviceLevelObjectiveV2Service.getSimpleSLOWithChildResource(
            accountProjectParams, Collections.singletonList(sloDTO.getIdentifier()));
    assertThat(serviceLevelObjectiveList.size()).isEqualTo(1);
    assertThat(serviceLevelObjectiveList.get(0).getIdentifier()).isEqualTo(sloDTO.getIdentifier());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGet_OnUserJourneyFilter() {
    ServiceLevelObjectiveV2DTO sloDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id1").build();
    createMonitoredService();
    sloDTO1.setUserJourneyRefs(Arrays.asList("Uid1", "Uid2"));
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO1);

    ServiceLevelObjectiveV2DTO sloDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id2").build();
    sloDTO2.setUserJourneyRefs(Arrays.asList("Uid4", "Uid3"));
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO2);

    ServiceLevelObjectiveV2DTO sloDTO3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id3").build();
    sloDTO3.setUserJourneyRefs(Arrays.asList("Uid4", "Uid2"));
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO3);

    List<ServiceLevelObjectiveV2Response> serviceLevelObjectiveV2ResponseList =
        serviceLevelObjectiveV2Service
            .get(projectParams, 0, 2,
                ServiceLevelObjectiveFilter.builder().userJourneys(Arrays.asList("Uid1", "Uid3")).build())
            .getContent();
    assertThat(serviceLevelObjectiveV2ResponseList).hasSize(2);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetRiskCount_Success() {
    riskCountSetup();
    SLORiskCountResponse sloRiskCountResponse =
        serviceLevelObjectiveV2Service.getRiskCount(projectParams, SLODashboardApiFilter.builder().build());

    assertThat(sloRiskCountResponse.getTotalCount()).isEqualTo(6);
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
    assertThat(sloRiskCountResponse.getRiskCounts()
                   .stream()
                   .filter(rc -> rc.getErrorBudgetRisk().equals(ErrorBudgetRisk.UNHEALTHY))
                   .findAny()
                   .get()
                   .getCount())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetRiskCount_withSearchFilter() {
    riskCountSetup();
    SLORiskCountResponse sloRiskCountResponse = serviceLevelObjectiveV2Service.getRiskCount(
        projectParams, SLODashboardApiFilter.builder().searchFilter("id").build());

    assertThat(sloRiskCountResponse.getTotalCount()).isEqualTo(2);
    assertThat(sloRiskCountResponse.getRiskCounts()).hasSize(5);
    assertThat(sloRiskCountResponse.getRiskCounts()
                   .stream()
                   .filter(rc -> rc.getErrorBudgetRisk().equals(ErrorBudgetRisk.EXHAUSTED))
                   .findAny()
                   .get()
                   .getCount())
        .isEqualTo(0);
    assertThat(sloRiskCountResponse.getRiskCounts()
                   .stream()
                   .filter(rc -> rc.getErrorBudgetRisk().equals(ErrorBudgetRisk.UNHEALTHY))
                   .findAny()
                   .get()
                   .getCount())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetRiskCount_withChildResourceFilter() {
    riskCountSetup();
    BuilderFactory builderFactory1 = BuilderFactory.builder().build();
    builderFactory1.getContext().setAccountId(builderFactory.getContext().getAccountId());
    builderFactory1.getContext().setOrgIdentifier("orgIdentifier1");
    builderFactory1.getContext().setProjectIdentifier("project3");
    createMonitoredService(builderFactory1);
    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory1.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id1").name("id1").build();
    serviceLevelObjectiveV2Service.create(builderFactory1.getProjectParams(), sloDTO);
    SLORiskCountResponse sloRiskCountResponse = serviceLevelObjectiveV2Service.getRiskCount(
        projectParams, SLODashboardApiFilter.builder().childResource(true).build());

    assertThat(sloRiskCountResponse.getTotalCount()).isEqualTo(7);
    assertThat(sloRiskCountResponse.getRiskCounts()).hasSize(5);
    assertThat(sloRiskCountResponse.getRiskCounts()
                   .stream()
                   .filter(rc -> rc.getErrorBudgetRisk().equals(ErrorBudgetRisk.HEALTHY))
                   .findAny()
                   .get()
                   .getCount())
        .isEqualTo(7);
  }

  private void riskCountSetup() {
    createMonitoredService(builderFactory);
    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id1").name("id1").build();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    SLOHealthIndicator sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                                                .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                                                .errorBudgetRemainingPercentage(10)
                                                .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                                                .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                 .identifier("id2")
                 .name("can not be searched")
                 .build();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(-10)
                             .errorBudgetRisk(ErrorBudgetRisk.EXHAUSTED)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id3").name("id3").build();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetRiskCount_WithFilters() {
    createMonitoredService();
    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                            .identifier("id1")
                                            .userJourneyRefs(Collections.singletonList("uj1"))
                                            .build();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    SLOHealthIndicator sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                                                .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                                                .errorBudgetRemainingPercentage(10)
                                                .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                                                .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                 .identifier("id5")
                 .userJourneyRefs(Collections.singletonList("uj2"))
                 .build();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(10)
                             .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
            .identifier("id2")
            .userJourneyRefs(Collections.singletonList("uj1"))
            .sloTarget(
                SLOTargetDTO.builder()
                    .type(SLOTargetType.CALENDER)
                    .sloTargetPercentage(80.0)
                    .spec(
                        CalenderSLOTargetSpec.builder()
                            .type(SLOCalenderType.WEEKLY)
                            .spec(
                                CalenderSLOTargetSpec.WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.MONDAY).build())
                            .build())
                    .build())
            .build();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(-10)
                             .errorBudgetRisk(ErrorBudgetRisk.EXHAUSTED)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                 .identifier("id3")
                 .userJourneyRefs(Collections.singletonList("uj3"))
                 .build();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);

    SLORiskCountResponse sloRiskCountResponse = serviceLevelObjectiveV2Service.getRiskCount(projectParams,
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
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetCVNGLogs() {
    Instant startTime = FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = FIXED_TIME_FOR_TESTS.instant();
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    List<String> serviceLevelIndicators = simpleServiceLevelObjectiveSpec.getServiceLevelIndicators()
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
        Arrays.asList(builderFactory.executionLogDTOBuilder().traceableId(verificationTaskIds.get(0)).build());
    cvngLogService.save(cvngLogDTOs);

    SLILogsFilter sliLogsFilter = SLILogsFilter.builder()
                                      .logType(CVNGLogType.EXECUTION_LOG)
                                      .startTime(startTime.toEpochMilli())
                                      .endTime(endTime.toEpochMilli())
                                      .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = serviceLevelObjectiveV2Service.getCVNGLogs(
        projectParams, sloDTO.getIdentifier(), sliLogsFilter, PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    ExecutionLogDTO executionLogDTOS = (ExecutionLogDTO) cvngLogDTOResponse.getContent().get(0);
    assertThat(executionLogDTOS.getAccountId()).isEqualTo(accountId);
    assertThat(executionLogDTOS.getTraceableId()).isEqualTo(verificationTaskIds.get(0));
    assertThat(executionLogDTOS.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    assertThat(executionLogDTOS.getType()).isEqualTo(CVNGLogType.EXECUTION_LOG);
    assertThat(executionLogDTOS.getLogLevel()).isEqualTo(ExecutionLogDTO.LogLevel.INFO);
    assertThat(executionLogDTOS.getLog()).isEqualTo("Data Collection successfully completed.");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_ForCompositeSLO() {
    Instant startTime = FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = FIXED_TIME_FOR_TESTS.instant();
    ServiceLevelObjectiveV2DTO sloDTO = compositeSLODTO;
    String verificationTaskId = verificationTaskService.getCompositeSLOVerificationTaskId(
        projectParams.getAccountIdentifier(), compositeServiceLevelObjective.getUuid());
    List<CVNGLogDTO> cvngLogDTOs =
        Arrays.asList(builderFactory.executionLogDTOBuilder().traceableId(verificationTaskId).build());
    cvngLogService.save(cvngLogDTOs);

    SLILogsFilter sliLogsFilter = SLILogsFilter.builder()
                                      .logType(CVNGLogType.EXECUTION_LOG)
                                      .startTime(startTime.toEpochMilli())
                                      .endTime(endTime.toEpochMilli())
                                      .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = serviceLevelObjectiveV2Service.getCVNGLogs(
        projectParams, sloDTO.getIdentifier(), sliLogsFilter, PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    ExecutionLogDTO executionLogDTOS = (ExecutionLogDTO) cvngLogDTOResponse.getContent().get(0);
    assertThat(executionLogDTOS.getAccountId()).isEqualTo(accountId);
    assertThat(executionLogDTOS.getTraceableId()).isEqualTo(verificationTaskId);
    assertThat(executionLogDTOS.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    assertThat(executionLogDTOS.getType()).isEqualTo(CVNGLogType.EXECUTION_LOG);
    assertThat(executionLogDTOS.getLogLevel()).isEqualTo(ExecutionLogDTO.LogLevel.INFO);
    assertThat(executionLogDTOS.getLog()).isEqualTo("Data Collection successfully completed.");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetNotificationRules() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    PageResponse<NotificationRuleResponse> notificationRuleResponsePageResponse =
        serviceLevelObjectiveV2Service.getNotificationRules(
            projectParams, sloDTO.getIdentifier(), PageParams.builder().page(0).size(10).build());
    assertThat(notificationRuleResponsePageResponse.getTotalPages()).isEqualTo(1);
    assertThat(notificationRuleResponsePageResponse.getTotalItems()).isEqualTo(1);
    assertThat(notificationRuleResponsePageResponse.getContent().get(0).isEnabled()).isTrue();
    assertThat(notificationRuleResponsePageResponse.getContent().get(0).getNotificationRule().getIdentifier())
        .isEqualTo(notificationRuleDTO.getIdentifier());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testBeforeNotificationRuleDelete() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleDTO.setIdentifier("rule1");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule3");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder().notificationRuleRef("rule1").enabled(true).build(),
            NotificationRuleRefDTO.builder().notificationRuleRef("rule2").enabled(true).build(),
            NotificationRuleRefDTO.builder().notificationRuleRef("rule3").enabled(true).build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThatThrownBy(()
                           -> serviceLevelObjectiveV2Service.beforeNotificationRuleDelete(
                               builderFactory.getContext().getProjectParams(), "rule1"))
        .hasMessage(
            "Deleting notification rule is used in SLOs, Please delete the notification rule inside SLOs before deleting notification rule. SLOs : sloName");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withIncorrectNotificationRule() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.create(projectParams, sloDTO))
        .hasMessage("NotificationRule with identifier rule is of type MONITORED_SERVICE and cannot be added into SLO");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreate_withIncorrectIdentifierNotificationRule() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder().notificationRuleRef("wrongIdentifier").enabled(true).build()));
    createMonitoredService();
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.create(projectParams, sloDTO))
        .hasMessage("NotificationRule does not exist for identifier: wrongIdentifier");
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_withNotificationRuleDelete() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    sloDTO.setNotificationRuleRefs(null);
    serviceLevelObjectiveV2Service.update(
        builderFactory.getContext().getProjectParams(), sloDTO.getIdentifier(), sloDTO);
    NotificationRule notificationRule = notificationRuleService.getEntity(
        builderFactory.getContext().getProjectParams(), notificationRuleDTO.getIdentifier());

    assertThat(notificationRule).isNull();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByProjectIdentifier_Success() {
    ProjectParams projectParamsTest = ProjectParams.builder()
                                          .accountIdentifier(builderFactory.getProjectParams().getAccountIdentifier())
                                          .orgIdentifier("orgIdentifier")
                                          .projectIdentifier("project3")
                                          .build();
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .orgIdentifier(projectParamsTest.getOrgIdentifier())
                                                  .projectIdentifier(projectParamsTest.getProjectIdentifier())
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(projectParamsTest.getAccountIdentifier(), monitoredServiceDTO);
    ServiceLevelObjectiveV2Service mockServiceLevelObjectiveService = spy(serviceLevelObjectiveV2Service);
    mockServiceLevelObjectiveService.create(projectParamsTest, sloDTO);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) mockServiceLevelObjectiveService.getEntity(
        projectParamsTest, sloDTO.getIdentifier());
    sloDTO = createSLOBuilder();
    sloDTO.setIdentifier("secondSLO");
    mockServiceLevelObjectiveService.create(projectParamsTest, sloDTO);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) mockServiceLevelObjectiveService.getEntity(
        projectParamsTest, sloDTO.getIdentifier());
    compositeSLODTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                          .spec(CompositeServiceLevelObjectiveSpec.builder()
                                    .serviceLevelObjectivesDetails(Arrays.asList(
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                            .weightagePercentage(25.0)
                                            .accountId(simpleServiceLevelObjective2.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                            .build()))
                                    .build())
                          .build();
    compositeSLODTO.setIdentifier("compositeSLO2");
    compositeSLODTO.setOrgIdentifier(projectParamsTest.getOrgIdentifier());
    compositeSLODTO.setProjectIdentifier(projectParamsTest.getProjectIdentifier());
    mockServiceLevelObjectiveService.create(projectParamsTest, compositeSLODTO);

    compositeSLODTO.setIdentifier("compositeSLO3");
    compositeSLODTO.setOrgIdentifier(builderFactory.getProjectParams().getOrgIdentifier());
    compositeSLODTO.setProjectIdentifier(builderFactory.getProjectParams().getOrgIdentifier());
    mockServiceLevelObjectiveService.create(builderFactory.getProjectParams(), compositeSLODTO);
    mockServiceLevelObjectiveService.deleteByProjectIdentifier(AbstractServiceLevelObjective.class,
        projectParamsTest.getAccountIdentifier(), projectParamsTest.getOrgIdentifier(),
        projectParamsTest.getProjectIdentifier());
    verify(mockServiceLevelObjectiveService, times(3)).delete(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByOrgIdentifier_Success() {
    ProjectParams projectParamsTest = ProjectParams.builder()
                                          .accountIdentifier(generateUuid())
                                          .orgIdentifier("orgIdentifier1")
                                          .projectIdentifier("project")
                                          .build();
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .orgIdentifier(projectParamsTest.getOrgIdentifier())
                                                  .projectIdentifier(projectParamsTest.getProjectIdentifier())
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(projectParamsTest.getAccountIdentifier(), monitoredServiceDTO);
    ServiceLevelObjectiveV2Service mockServiceLevelObjectiveService = spy(serviceLevelObjectiveV2Service);
    mockServiceLevelObjectiveService.create(projectParamsTest, sloDTO);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) mockServiceLevelObjectiveService.getEntity(
        projectParamsTest, sloDTO.getIdentifier());
    sloDTO = createSLOBuilder();
    sloDTO.setIdentifier("secondSLO");
    mockServiceLevelObjectiveService.create(projectParamsTest, sloDTO);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) mockServiceLevelObjectiveService.getEntity(
        projectParamsTest, sloDTO.getIdentifier());
    compositeSLODTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                          .spec(CompositeServiceLevelObjectiveSpec.builder()
                                    .serviceLevelObjectivesDetails(Arrays.asList(
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                            .weightagePercentage(25.0)
                                            .accountId(simpleServiceLevelObjective2.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                            .build()))
                                    .build())
                          .build();
    compositeSLODTO.setIdentifier("compositeSLO2");
    compositeSLODTO.setOrgIdentifier(projectParamsTest.getOrgIdentifier());
    compositeSLODTO.setProjectIdentifier(projectParamsTest.getProjectIdentifier());
    mockServiceLevelObjectiveService.create(projectParamsTest, compositeSLODTO);
    mockServiceLevelObjectiveService.deleteByOrgIdentifier(AbstractServiceLevelObjective.class,
        projectParamsTest.getAccountIdentifier(), projectParamsTest.getOrgIdentifier());
    verify(mockServiceLevelObjectiveService, times(3)).delete(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByAccountIdentifier_Success() {
    ProjectParams projectParamsTest = ProjectParams.builder()
                                          .accountIdentifier(generateUuid())
                                          .orgIdentifier("orgIdentifier")
                                          .projectIdentifier("project")
                                          .build();
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .orgIdentifier(projectParamsTest.getOrgIdentifier())
                                                  .projectIdentifier(projectParamsTest.getProjectIdentifier())
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(projectParamsTest.getAccountIdentifier(), monitoredServiceDTO);
    ServiceLevelObjectiveV2Service mockServiceLevelObjectiveService = spy(serviceLevelObjectiveV2Service);
    mockServiceLevelObjectiveService.create(projectParamsTest, sloDTO);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) mockServiceLevelObjectiveService.getEntity(
        projectParamsTest, sloDTO.getIdentifier());
    sloDTO = createSLOBuilder();
    sloDTO.setIdentifier("secondSLO");
    mockServiceLevelObjectiveService.create(projectParamsTest, sloDTO);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) mockServiceLevelObjectiveService.getEntity(
        projectParamsTest, sloDTO.getIdentifier());
    compositeSLODTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                          .spec(CompositeServiceLevelObjectiveSpec.builder()
                                    .serviceLevelObjectivesDetails(Arrays.asList(
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                            .weightagePercentage(25.0)
                                            .accountId(simpleServiceLevelObjective2.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                            .build()))
                                    .build())
                          .build();
    compositeSLODTO.setIdentifier("compositeSLO2");
    compositeSLODTO.setOrgIdentifier(projectParamsTest.getOrgIdentifier());
    compositeSLODTO.setProjectIdentifier(projectParamsTest.getProjectIdentifier());
    mockServiceLevelObjectiveService.create(projectParamsTest, compositeSLODTO);

    mockServiceLevelObjectiveService.deleteByAccountIdentifier(
        AbstractServiceLevelObjective.class, projectParamsTest.getAccountIdentifier());
    verify(mockServiceLevelObjectiveService, times(3)).delete(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSImpleSLOsByMonitoredServiceIdentifier() {
    createMonitoredService();
    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id3").build();
    sloDTO.setUserJourneyRefs(Arrays.asList("Uid4", "Uid2"));
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveV2Service.getByMonitoredServiceIdentifiers(
                   projectParams, Collections.singleton(monitoredServiceDTO.getIdentifier())))
        .hasSize(2);
    assertThat(serviceLevelObjectiveV2Service.getByMonitoredServiceIdentifiers(
                   projectParams, Collections.singleton(builderFactory.getContext().getMonitoredServiceIdentifier())))
        .hasSize(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_ServiceLevelObjectiveV2CreateAuditEvent() throws JsonProcessingException {
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);
    ServiceLevelObjectiveCreateEvent serviceLevelObjectiveCreateEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
            outboxEvent.getEventData(), ServiceLevelObjectiveCreateEvent.class);
    assertThat(outboxEvent.getEventType()).isEqualTo(ServiceLevelObjectiveCreateEvent.builder().build().getEventType());
    assertThat(serviceLevelObjectiveCreateEvent.getNewServiceLevelObjectiveDTO()).isEqualTo(compositeSLODTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_ServiceLevelObjectiveV2UpdateAuditEvent() throws JsonProcessingException {
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(compositeSLODTO);
    compositeSLODTO.setDescription("newDescription");
    serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO.getIdentifier(), compositeSLODTO);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);
    ServiceLevelObjectiveUpdateEvent serviceLevelObjectiveUpdateEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
            outboxEvent.getEventData(), ServiceLevelObjectiveUpdateEvent.class);
    assertThat(outboxEvent.getEventType()).isEqualTo(ServiceLevelObjectiveUpdateEvent.builder().build().getEventType());
    assertThat(serviceLevelObjectiveUpdateEvent.getOldServiceLevelObjectiveDTO())
        .isEqualTo(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO());
    assertThat(serviceLevelObjectiveUpdateEvent.getNewServiceLevelObjectiveDTO()).isEqualTo(compositeSLODTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testDelete_ServiceLevelObjectiveV2DeleteAuditEvent() throws JsonProcessingException {
    boolean isDeleted = serviceLevelObjectiveV2Service.delete(projectParams, compositeSLODTO.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);
    ServiceLevelObjectiveDeleteEvent serviceLevelObjectiveDeleteEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
            outboxEvent.getEventData(), ServiceLevelObjectiveDeleteEvent.class);
    assertThat(outboxEvent.getEventType()).isEqualTo(ServiceLevelObjectiveDeleteEvent.builder().build().getEventType());
    assertThat(serviceLevelObjectiveDeleteEvent.getOldServiceLevelObjectiveDTO()).isEqualTo(compositeSLODTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOnboardingGraph() throws IllegalAccessException {
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "clock", clock, true);

    createMonitoredService();

    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        CompositeServiceLevelObjectiveSpec.builder()
            .serviceLevelObjectivesDetails(
                Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                  .weightagePercentage(75.0)
                                  .accountId(simpleServiceLevelObjective1.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                  .build(),
                    ServiceLevelObjectiveDetailsDTO.builder()
                        .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                        .weightagePercentage(25.0)
                        .accountId(simpleServiceLevelObjective2.getAccountId())
                        .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                        .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                        .build()))
            .build();
    TimeGraphResponse timeGraphResponse =
        serviceLevelObjectiveV2Service.getOnboardingGraph(compositeServiceLevelObjectiveSpec);
    assert timeGraphResponse != null;
    assertThat(timeGraphResponse.getDataPoints()).isNotNull();
    assertThat(timeGraphResponse.getDataPoints()).isEmpty();

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1);
    createSLIRecords(sliId2);

    timeGraphResponse = serviceLevelObjectiveV2Service.getOnboardingGraph(compositeServiceLevelObjectiveSpec);
    assert timeGraphResponse != null;
    assertThat(timeGraphResponse.getDataPoints()).isNotNull();
    assertThat(timeGraphResponse.getDataPoints()).isNotEmpty();
    assertThat(timeGraphResponse.getDataPoints().size()).isEqualTo(10);
    assertThat(timeGraphResponse.getDataPoints().get(0).getValue()).isEqualTo(0.0);
    assertThat(timeGraphResponse.getDataPoints().get(1).getValue()).isEqualTo(50.0);
    assertThat(timeGraphResponse.getDataPoints().get(2).getValue()).isEqualTo(66.66666666666667);
    assertThat(timeGraphResponse.getDataPoints().get(3).getValue()).isEqualTo(75.0);
    assertThat(timeGraphResponse.getDataPoints().get(4).getValue()).isEqualTo(80.0);
    assertThat(timeGraphResponse.getDataPoints().get(5).getValue()).isEqualTo(83.33333333333333);
    assertThat(timeGraphResponse.getDataPoints().get(6).getValue()).isEqualTo(71.42857142857143);
    assertThat(timeGraphResponse.getDataPoints().get(7).getValue()).isEqualTo(62.5);
    assertThat(timeGraphResponse.getDataPoints().get(8).getValue()).isEqualTo(55.55555555555556);
    assertThat(timeGraphResponse.getDataPoints().get(9).getValue()).isEqualTo(50.0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testSendNotification() throws IllegalAccessException, IOException {
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
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
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
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());

    clock = Clock.fixed(clock.instant().plus(1, ChronoUnit.HOURS), ZoneOffset.UTC);
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "clock", clock, true);
    when(notificationClient.sendNotificationAsync(any()))
        .thenReturn(NotificationResultWithoutStatus.builder().notificationId("notificationId").build());

    serviceLevelObjectiveV2Service.handleNotification(serviceLevelObjective);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testSendNotification_compositeSLO() throws IllegalAccessException {
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

    compositeSLODTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build(),
            NotificationRuleRefDTO.builder()
                .notificationRuleRef(notificationRuleResponseTwo.getNotificationRule().getIdentifier())
                .enabled(true)
                .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO.getIdentifier(), compositeSLODTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, compositeSLODTO.getIdentifier());

    clock = Clock.fixed(clock.instant().plus(1, ChronoUnit.HOURS), ZoneOffset.UTC);
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "clock", clock, true);
    when(notificationClient.sendNotificationAsync(any()))
        .thenReturn(NotificationResultWithoutStatus.builder().notificationId("notificationId").build());

    serviceLevelObjectiveV2Service.handleNotification(serviceLevelObjective);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testNotification_requestCompositeSLO_errorBudgetRemaining() {
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

    simpleServiceLevelObjectiveDTO1 = builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder()
                                          .identifier("requestSimpleSLOIdentifier")
                                          .build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO1);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, simpleServiceLevelObjectiveDTO1.getIdentifier());

    simpleServiceLevelObjectiveDTO2 = builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder()
                                          .identifier("requestSimpleSLOIdentifier2")
                                          .build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO2);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, simpleServiceLevelObjectiveDTO2.getIdentifier());

    compositeSLODTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                          .identifier("requestCompositeSLO")
                          .name("requestCompositeSLO")
                          .spec(CompositeServiceLevelObjectiveSpec.builder()
                                    .evaluationType(SLIEvaluationType.REQUEST)
                                    .serviceLevelObjectivesDetails(Arrays.asList(
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                            .weightagePercentage(25.0)
                                            .accountId(simpleServiceLevelObjective2.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                            .build()))
                                    .build())
                          .build();

    compositeSLODTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build(),
            NotificationRuleRefDTO.builder()
                .notificationRuleRef(notificationRuleResponseTwo.getNotificationRule().getIdentifier())
                .enabled(true)
                .build()));
    createMonitoredService();
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.create(projectParams, compositeSLODTO))
        .hasMessage(
            "Invalid notification with identifier: rule2. Request based composite SLOs can't have notifications for condition [Error Budget Remaining].");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withErrorBudgetRemainingPercentage() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());

    SLONotificationRuleCondition condition =
        SLONotificationRule.SLOErrorBudgetRemainingPercentageCondition.builder().threshold(10.0).build();

    assertThat(((ServiceLevelObjectiveV2ServiceImpl) serviceLevelObjectiveV2Service)
                   .getNotificationData(serviceLevelObjective, condition)
                   .shouldSendNotification())
        .isFalse();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withErrorBudgetBurnRate() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    String sliId = serviceLevelIndicatorService
                       .getServiceLevelIndicator(projectParams,
                           ((SimpleServiceLevelObjective) serviceLevelObjective).getServiceLevelIndicators().get(0))
                       .getUuid();
    createSLIRecords(sliId);
    SLONotificationRule.SLOErrorBudgetBurnRateCondition condition =
        SLONotificationRule.SLOErrorBudgetBurnRateCondition.builder()
            .threshold(0.04)
            .lookBackDuration(Duration.ofMinutes(10).toMillis())
            .build();

    assertThat(((ServiceLevelObjectiveV2ServiceImpl) serviceLevelObjectiveV2Service)
                   .getNotificationData(serviceLevelObjective, condition)
                   .shouldSendNotification())
        .isTrue();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testShouldSendNotification_withErrorBudgetBurnRateForRequestBasedSLO() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("new_request_based_slo").build();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    String sliId = serviceLevelIndicatorService
                       .getServiceLevelIndicator(projectParams,
                           ((SimpleServiceLevelObjective) serviceLevelObjective).getServiceLevelIndicators().get(0))
                       .getUuid();
    List<Long> goodCounts = Arrays.asList(50l, 60l, 70l, 20l, 40l, 50l, 25l, 75l);
    List<Long> badCounts = Arrays.asList(10l, 10l, 20l, 10l, 20l, 10l, 20l, 10l);
    createDataForRequestSLI(clock.instant().minus(8, ChronoUnit.MINUTES), goodCounts, badCounts, sliId);
    SLONotificationRule.SLOErrorBudgetBurnRateCondition condition =
        SLONotificationRule.SLOErrorBudgetBurnRateCondition.builder()
            .threshold(25.0)
            .lookBackDuration(Duration.ofMinutes(8).toMillis())
            .build();

    assertThat(((ServiceLevelObjectiveV2ServiceImpl) serviceLevelObjectiveV2Service)
                   .getNotificationData(serviceLevelObjective, condition)
                   .shouldSendNotification())
        .isTrue();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testShouldSendNotificationForCompositeSLO_withErrorBudgetBurnRate() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponseOne =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    compositeSLODTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponseOne.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.update(projectParams, compositeSLODTO.getIdentifier(), compositeSLODTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, compositeSLODTO.getIdentifier());
    Instant startTime = clock.instant().minus(Duration.ofMinutes(10));
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecords(startTime.plus(5, ChronoUnit.MINUTES), startTime.plus(10, ChronoUnit.MINUTES), runningGoodCount,
        runningBadCount);
    SLONotificationRule.SLOErrorBudgetBurnRateCondition condition =
        SLONotificationRule.SLOErrorBudgetBurnRateCondition.builder()
            .threshold(0.02)
            .lookBackDuration(Duration.ofMinutes(5).toMillis())
            .build();

    assertThat(((ServiceLevelObjectiveV2ServiceImpl) serviceLevelObjectiveV2Service)
                   .getNotificationData(serviceLevelObjective, condition)
                   .shouldSendNotification())
        .isTrue();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetNotificationRules_withCoolOffLogic() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());

    assertThat(((ServiceLevelObjectiveV2ServiceImpl) serviceLevelObjectiveV2Service)
                   .getNotificationRules(serviceLevelObjective)
                   .size())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testNotGetNotificationRules_withDisabled() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(false)
                          .build()));
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());

    assertThat(((ServiceLevelObjectiveV2ServiceImpl) serviceLevelObjectiveV2Service)
                   .getNotificationRules(serviceLevelObjective)
                   .size())
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetEvaluationType_accountCompositeSLO() {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setProjectIdentifier("project1");
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    projectParams = ProjectParams.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build();

    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                              .identifier("service2_env2")
                              .name("monitored service 2")
                              .sources(MonitoredServiceDTO.Sources.builder().build())
                              .serviceRef("service2")
                              .environmentRef("env2")
                              .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("simpleSLOIdentifier").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO1);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, simpleServiceLevelObjectiveDTO1.getIdentifier());

    simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("compositeSloIdentifier").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveSpec2.setServiceLevelIndicators(
        Collections.singletonList(builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build()));
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO2);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, simpleServiceLevelObjectiveDTO2.getIdentifier());

    ProjectParams projectParams2 = ProjectParams.builder().accountIdentifier(accountId).build();

    compositeSLODTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                          .projectIdentifier(null)
                          .orgIdentifier(null)
                          .spec(CompositeServiceLevelObjectiveSpec.builder()
                                    .evaluationType(SLIEvaluationType.REQUEST)
                                    .serviceLevelObjectivesDetails(Arrays.asList(
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                            .weightagePercentage(25.0)
                                            .accountId(simpleServiceLevelObjective2.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                            .build(),
                                        ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build()))
                                    .build())
                          .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams2, compositeSLODTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams2, compositeSLODTO.getIdentifier());
    assertThat(serviceLevelObjective.getSliEvaluationType()).isEqualTo(SLIEvaluationType.REQUEST);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllReferredSLOs() {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setProjectIdentifier("project1");
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    projectParams = ProjectParams.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build();

    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .identifier("service2_env2")
                              .name("monitored service 2")
                              .sources(MonitoredServiceDTO.Sources.builder().build())
                              .serviceRef("service2")
                              .environmentRef("env2")
                              .build();
    monitoredServiceService.create(accountId, monitoredServiceDTO);

    simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("simpleSLOIdentifier").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO1);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, simpleServiceLevelObjectiveDTO1.getIdentifier());

    simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("simpleSLOIdentifier3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveSpec2.setServiceLevelIndicators(
        Collections.singletonList(builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build()));
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(projectParams, simpleServiceLevelObjectiveDTO2);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        projectParams, simpleServiceLevelObjectiveDTO2.getIdentifier());

    ProjectParams projectParams2 = ProjectParams.builder().accountIdentifier(accountId).build();

    compositeSLODTO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                          .projectIdentifier(null)
                          .orgIdentifier(null)
                          .spec(CompositeServiceLevelObjectiveSpec.builder()
                                    .serviceLevelObjectivesDetails(
                                        Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                                          .serviceLevelObjectiveRef("simpleSLOIdentifier2")
                                                          .weightagePercentage(25.0)
                                                          .accountId(simpleServiceLevelObjective2.getAccountId())
                                                          .orgIdentifier("orgIdentifier")
                                                          .projectIdentifier("project")
                                                          .build(),
                                            ServiceLevelObjectiveDetailsDTO.builder()
                                                .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                                .weightagePercentage(75.0)
                                                .accountId(simpleServiceLevelObjective1.getAccountId())
                                                .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                                .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                                .build()))
                                    .build())
                          .build();
    List<AbstractServiceLevelObjective> serviceLevelObjectiveList =
        serviceLevelObjectiveV2Service.getAllReferredSLOs(projectParams2,
            ((CompositeServiceLevelObjectiveSpec) compositeSLODTO.getSpec())
                .getServiceLevelObjectivesDetails()
                .stream()
                .map(serviceLevelObjectiveDetailsDTO
                    -> serviceLevelObjectiveDetailsTransformer.getServiceLevelObjectiveDetails(
                        serviceLevelObjectiveDetailsDTO))
                .collect(Collectors.toSet()));
    assertThat(serviceLevelObjectiveList.size()).isEqualTo(2);
    assertThat(serviceLevelObjectiveList.get(0).getIdentifier()).isEqualTo("simpleSLOIdentifier");
    assertThat(serviceLevelObjectiveList.get(0).getProjectIdentifier()).isEqualTo("project1");
    assertThat(serviceLevelObjectiveList.get(1).getIdentifier()).isEqualTo("simpleSLOIdentifier2");
    assertThat(serviceLevelObjectiveList.get(1).getProjectIdentifier()).isEqualTo("project");
  }

  private ServiceLevelObjectiveV2DTO createSimpleWindowSLOBuilder(
      String orgIdentifier, String projectIdentifier, String identifier) {
    return builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .name(identifier)
        .identifier(identifier)
        .build();
  }

  private ServiceLevelObjectiveV2DTO createSimpleRequestSLOBuilder(
      String orgIdentifier, String projectIdentifier, String identifier) {
    return builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder()
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .name(identifier)
        .identifier(identifier)
        .build();
  }

  private ServiceLevelObjectiveV2DTO createSLOBuilder() {
    return builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
  }

  private ServiceLevelObjectiveV2DTO createCompositeSLOBuilder() {
    return builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder().build();
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  private void createMonitoredService(BuilderFactory builderFactory) {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  private void createSLIRecords(String sliId) {
    Instant startTime = clock.instant().minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, "verificationTaskId", 0);
  }

  private void createDataForRequestSLI(Instant startTime, List<Long> goodCounts, List<Long> badCounts, String sliId) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < goodCounts.size(); i++) {
      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(GOOD)
                              .timeStamp(startTime.plus(Duration.ofMinutes(i)))
                              .badEventCount(badCounts.get(i))
                              .goodEventCount(goodCounts.get(i))
                              .build());
    }
    sliRecordService.create(sliRecordParams, sliId, sliId, 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      long goodCount = 0;
      long badCount = 0;
      if (sliState == GOOD) {
        goodCount++;
      } else if (sliState == BAD) {
        badCount++;
      }
      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(sliState)
                              .timeStamp(startTime.plus(Duration.ofMinutes(i)))
                              .goodEventCount(goodCount)
                              .badEventCount(badCount)
                              .build());
    }
    return sliRecordParams;
  }

  private List<CompositeSLORecord> createSLORecords(
      Instant start, Instant end, List<Double> runningGoodCount, List<Double> runningBadCount) {
    int index = 0;
    List<CompositeSLORecord> sloRecords = new ArrayList<>();
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                         .verificationTaskId(compositeServiceLevelObjective.getUuid())
                                         .sloId(compositeServiceLevelObjective.getUuid())
                                         .version(0)
                                         .runningBadCount(runningBadCount.get(index))
                                         .runningGoodCount(runningGoodCount.get(index))
                                         .sloVersion(0)
                                         .timestamp(instant)
                                         .build();
      sloRecords.add(sloRecord);
      index++;
    }
    hPersistence.save(sloRecords);
    return sloRecords;
  }
}
