/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.logsFilterParams.SLILogsFilter;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceLevelObjectiveV2ServiceImplTest extends CvNextGenTestBase {
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject CVNGLogService cvngLogService;
  @Inject NotificationRuleService notificationRuleService;
  @Inject HPersistence hPersistence;

  private BuilderFactory builderFactory;
  ProjectParams projectParams;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  SLOTargetDTO calendarSloTarget;

  @Before
  public void setup() throws IllegalAccessException, ParseException {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    projectParams = ProjectParams.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build();
    calendarSloTarget =
        SLOTargetDTO.builder()
            .type(SLOTargetType.CALENDER)
            .sloTargetPercentage(80.0)
            .spec(CalenderSLOTargetSpec.builder()
                      .type(SLOCalenderType.WEEKLY)
                      .spec(CalenderSLOTargetSpec.WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.MONDAY).build())

                      .build())
            .build();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(),
        sloDTO.getIdentifier(), simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_WithoutTagsSuccess() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setTags(new HashMap<>());
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(),
        sloDTO.getIdentifier(), simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_CalendarSLOTargetSuccess() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    sloDTO.setSloTarget(calendarSloTarget);
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(),
        sloDTO.getIdentifier(), simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec.getHealthSourceRef());
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
            "[SLOV2 Not Found] SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            sloDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testSetMonitoredServiceSLOEnabled_success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(),
        sloDTO.getIdentifier(), simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec.getHealthSourceRef());
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
    List<String> serviceLevelIndicators = serviceLevelIndicatorService.create(projectParams,
        simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(), simpleServiceLevelObjectiveSpec.getHealthSourceRef());
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
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicatorType(ServiceLevelIndicatorType.LATENCY);
    sloDTO.setSpec(simpleServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_UpdateWithSLOTarget() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    List<String> serviceLevelIndicators = serviceLevelIndicatorService.create(projectParams,
        simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(), simpleServiceLevelObjectiveSpec.getHealthSourceRef());
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
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators);
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
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_HealthSourceUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    List<String> serviceLevelIndicators = serviceLevelIndicatorService.create(projectParams,
        simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(), simpleServiceLevelObjectiveSpec.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef("newHealthSourceRef");
    sloDTO.setSpec(simpleServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_MonitoredServiceUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    List<String> serviceLevelIndicators = serviceLevelIndicatorService.create(projectParams,
        simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(), simpleServiceLevelObjectiveSpec.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setServiceRef("service1");
    monitoredServiceDTO.setEnvironmentRef("env1");
    monitoredServiceDTO.setIdentifier("service1_env1");
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef("service1_env1");
    sloDTO.setSpec(simpleServiceLevelObjectiveSpec);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_FailedWithEntityNotPresent() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    List<String> serviceLevelIndicators = serviceLevelIndicatorService.create(projectParams,
        simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(), simpleServiceLevelObjectiveSpec.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    sloDTO.setIdentifier("newIdentifier");
    sloDTO.setDescription("newDescription");
    assertThatThrownBy(()
                           -> serviceLevelObjectiveV2Service.update(
                               projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "[SLOV2 Not Found] SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            sloDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGet_IdentifierBasedQuery() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(),
        sloDTO.getIdentifier(), simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec.getHealthSourceRef());
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    ServiceLevelObjectiveV2Response serviceLevelObjectiveV2Response =
        serviceLevelObjectiveV2Service.get(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjectiveV2Response.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGet_OnUserJourneyFilter() {
    ServiceLevelObjectiveV2DTO sloDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id1").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) sloDTO1.getSpec();
    createMonitoredService();
    sloDTO1.setUserJourneyRefs(Arrays.asList("Uid1", "Uid2"));
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec1.getServiceLevelIndicators(),
        sloDTO1.getIdentifier(), simpleServiceLevelObjectiveSpec1.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec1.getHealthSourceRef());
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO1);

    ServiceLevelObjectiveV2DTO sloDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) sloDTO2.getSpec();
    sloDTO2.setUserJourneyRefs(Arrays.asList("Uid4", "Uid3"));
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec2.getServiceLevelIndicators(),
        sloDTO2.getIdentifier(), simpleServiceLevelObjectiveSpec2.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec2.getHealthSourceRef());
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO2);

    ServiceLevelObjectiveV2DTO sloDTO3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec3 =
        (SimpleServiceLevelObjectiveSpec) sloDTO3.getSpec();
    sloDTO3.setUserJourneyRefs(Arrays.asList("Uid4", "Uid2"));
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec3.getServiceLevelIndicators(),
        sloDTO3.getIdentifier(), simpleServiceLevelObjectiveSpec3.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec3.getHealthSourceRef());
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
    createMonitoredService();
    ServiceLevelObjectiveV2DTO sloDTO =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id1").build();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    SLOHealthIndicator sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                                                .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                                                .errorBudgetRemainingPercentage(10)
                                                .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                                                .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id2").build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(-10)
                             .errorBudgetRisk(ErrorBudgetRisk.EXHAUSTED)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id3").build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, sloDTO);

    SLORiskCountResponse sloRiskCountResponse =
        serviceLevelObjectiveV2Service.getRiskCount(projectParams, SLODashboardApiFilter.builder().build());

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
  public void testGetRiskCount_WithFilters() {
    createMonitoredService();
    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                            .identifier("id1")
                                            .userJourneyRefs(Collections.singletonList("uj1"))
                                            .build();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
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
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
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
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
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
    serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(projectParams, sloDTO);

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
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO.getSpec();
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(),
        sloDTO.getIdentifier(), simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec.getHealthSourceRef());
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

  private AbstractServiceLevelObjective getServiceLevelObjective(String identifier) {
    return hPersistence.createQuery(AbstractServiceLevelObjective.class)
        .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.accountId, accountId)
        .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.orgIdentifier, orgIdentifier)
        .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.projectIdentifier, projectIdentifier)
        .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.identifier, identifier)
        .get();
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
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByProjectIdentifier_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Service mockServiceLevelObjectiveService = spy(serviceLevelObjectiveV2Service);
    mockServiceLevelObjectiveService.create(projectParams, sloDTO);
    sloDTO.setIdentifier("secondSLO");
    mockServiceLevelObjectiveService.create(projectParams, sloDTO);
    mockServiceLevelObjectiveService.deleteByProjectIdentifier(AbstractServiceLevelObjective.class,
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    verify(mockServiceLevelObjectiveService, times(2)).delete(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByOrgIdentifier_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Service mockServiceLevelObjectiveService = spy(serviceLevelObjectiveV2Service);
    mockServiceLevelObjectiveService.create(projectParams, sloDTO);
    sloDTO.setIdentifier("secondSLO");
    mockServiceLevelObjectiveService.create(projectParams, sloDTO);
    mockServiceLevelObjectiveService.deleteByOrgIdentifier(
        AbstractServiceLevelObjective.class, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier());
    verify(mockServiceLevelObjectiveService, times(2)).delete(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByAccountIdentifier_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveV2Service mockServiceLevelObjectiveService = spy(serviceLevelObjectiveV2Service);
    mockServiceLevelObjectiveService.create(projectParams, sloDTO);
    sloDTO.setIdentifier("secondSLO");
    mockServiceLevelObjectiveService.create(projectParams, sloDTO);
    mockServiceLevelObjectiveService.deleteByAccountIdentifier(
        AbstractServiceLevelObjective.class, projectParams.getAccountIdentifier());
    verify(mockServiceLevelObjectiveService, times(2)).delete(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetAllSLOs() {
    ServiceLevelObjectiveV2DTO sloDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id1").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) sloDTO1.getSpec();
    createMonitoredService();
    sloDTO1.setUserJourneyRefs(Arrays.asList("Uid1", "Uid2"));
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec1.getServiceLevelIndicators(),
        sloDTO1.getIdentifier(), simpleServiceLevelObjectiveSpec1.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec1.getHealthSourceRef());
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO1);

    ServiceLevelObjectiveV2DTO sloDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) sloDTO2.getSpec();
    sloDTO2.setUserJourneyRefs(Arrays.asList("Uid4", "Uid3"));
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec2.getServiceLevelIndicators(),
        sloDTO2.getIdentifier(), simpleServiceLevelObjectiveSpec2.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec2.getHealthSourceRef());
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO2);

    ServiceLevelObjectiveV2DTO sloDTO3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec3 =
        (SimpleServiceLevelObjectiveSpec) sloDTO3.getSpec();
    sloDTO3.setUserJourneyRefs(Arrays.asList("Uid4", "Uid2"));
    serviceLevelIndicatorService.create(projectParams, simpleServiceLevelObjectiveSpec3.getServiceLevelIndicators(),
        sloDTO3.getIdentifier(), simpleServiceLevelObjectiveSpec3.getMonitoredServiceRef(),
        simpleServiceLevelObjectiveSpec3.getHealthSourceRef());
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO3);
    assertThat(serviceLevelObjectiveV2Service.getAllSLOs(projectParams)).hasSize(3);
  }

  private ServiceLevelObjectiveV2DTO createSLOBuilder() {
    return builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  private void createDisabledMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setEnabled(false);
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }
}
