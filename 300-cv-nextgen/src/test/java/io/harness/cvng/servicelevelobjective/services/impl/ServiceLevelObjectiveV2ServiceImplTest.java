/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceLevelObjectiveV2ServiceImplTest extends CvNextGenTestBase {
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;

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
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
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
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
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
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_withoutMonitoredServiceFailedValidation() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.create(projectParams, sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Monitored Source Entity with identifier %s is not present", sloDTO.getMonitoredServiceRef()));
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
  @Ignore("will re-enable after we modify the read operation to happen from V2 entity")
  public void testDelete_validationFailedForIncorrectSLO() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    sloDTO.setIdentifier("incorrectSLOIdentifier");
    assertThatThrownBy(() -> serviceLevelObjectiveV2Service.delete(projectParams, sloDTO.getIdentifier()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            sloDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testSetMonitoredServiceSLOEnabled_success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
        sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjective.isEnabled()).isEqualTo(false);
    serviceLevelObjectiveV2Service.setMonitoredServiceSLOsEnableFlag(
        projectParams, sloDTO.getMonitoredServiceRef(), true);
    serviceLevelObjective = serviceLevelObjectiveV2Service.getEntity(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjective.isEnabled()).isEqualTo(true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_Success() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    List<String> serviceLevelIndicators =
        serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
            sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
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
    sloDTO.setServiceLevelIndicatorType(ServiceLevelIndicatorType.LATENCY);
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_UpdateWithSLOTarget() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    List<String> serviceLevelIndicators =
        serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
            sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO responseSLIDTO =
        serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getServiceLevelIndicators().get(0);
    ServiceLevelIndicator serviceLevelIndicatorOld = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), responseSLIDTO.getIdentifier());
    String sliIndicator = serviceLevelIndicatorOld.getUuid();
    sloDTO.setSloTarget(calendarSloTarget);
    ServiceLevelObjectiveV2Response updatedServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators);
    String updatedSliIndicator = serviceLevelIndicatorService
                                     .getServiceLevelIndicator(builderFactory.getProjectParams(),
                                         updatedServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()
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
    createMonitoredService();
    List<String> serviceLevelIndicators =
        serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
            sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    sloDTO.setHealthSourceRef("newHealthSourceRef");
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_MonitoredServiceUpdate() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    List<String> serviceLevelIndicators =
        serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
            sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setServiceRef("service1");
    monitoredServiceDTO.setEnvironmentRef("env1");
    monitoredServiceDTO.setIdentifier("service1_env1");
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    sloDTO.setMonitoredServiceRef("service1_env1");
    ServiceLevelObjectiveV2Response updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.update(projectParams, sloDTO.getIdentifier(), sloDTO, serviceLevelIndicators);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  @Ignore("will re-enable after we modify the read operation to happen from V2 entity")
  public void testUpdate_FailedWithEntityNotPresent() {
    ServiceLevelObjectiveV2DTO sloDTO = createSLOBuilder();
    createMonitoredService();
    List<String> serviceLevelIndicators =
        serviceLevelIndicatorService.create(projectParams, sloDTO.getServiceLevelIndicators(), sloDTO.getIdentifier(),
            sloDTO.getMonitoredServiceRef(), sloDTO.getHealthSourceRef());
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
            "SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            sloDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
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
