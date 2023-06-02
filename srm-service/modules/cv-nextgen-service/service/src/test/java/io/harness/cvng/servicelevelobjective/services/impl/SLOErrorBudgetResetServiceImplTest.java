/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RollingSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLOErrorBudgetResetServiceImplTest extends CvNextGenTestBase {
  @Inject SLOErrorBudgetResetService sloErrorBudgetResetService;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject MonitoredServiceService monitoredServiceService;

  BuilderFactory builderFactory;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    FieldUtils.writeField(sloErrorBudgetResetService, "clock", builderFactory.getClock(), true);
    FieldUtils.writeField(serviceLevelObjectiveV2Service, "clock", builderFactory.getClock(), true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testResetErrorBudget() {
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(
        builderFactory.getProjectParams(), builderFactory.getSimpleCalendarServiceLevelObjectiveV2DTOBuilder().build());
    String sloIdentifier = serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getIdentifier();
    SLOErrorBudgetResetDTO sloErrorBudgetResetDTO =
        sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
            builderFactory.getSLOErrorBudgetResetDTOBuilder().serviceLevelObjectiveIdentifier(sloIdentifier).build());
    SLOErrorBudgetResetDTO saved =
        sloErrorBudgetResetService.getErrorBudgetResets(builderFactory.getProjectParams(), sloIdentifier).get(0);
    assertThat(saved.getValidUntil().longValue()).isEqualTo(1588464000000L);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testResetErrorBudget_failForRolling() {
    createMonitoredService();
    RollingSLOTargetSpec spec = RollingSLOTargetSpec.builder().periodLength("7d").build();
    SLOTargetDTO sloTargetDTO =
        SLOTargetDTO.builder().sloTargetPercentage(99.0).type(SLOTargetType.ROLLING).spec(spec).build();
    ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().sloTarget(sloTargetDTO).build();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveDTO);
    String sloIdentifier = serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getIdentifier();

    assertThatThrownBy(()
                           -> sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
                               builderFactory.getSLOErrorBudgetResetDTOBuilder()
                                   .serviceLevelObjectiveIdentifier(sloIdentifier)
                                   .build()))
        .hasMessage("SLO Target should be of type Calender");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testResetErrorBudget_withoutSLO() {
    assertThatThrownBy(()
                           -> sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
                               builderFactory.getSLOErrorBudgetResetDTOBuilder().build()))
        .hasMessageContaining("SLO with identifier:slo not found");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetErrorBudgetResets() {
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(
        builderFactory.getProjectParams(), builderFactory.getSimpleCalendarServiceLevelObjectiveV2DTOBuilder().build());
    String sloIdentifier = serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getIdentifier();
    SLOErrorBudgetResetDTO sloErrorBudgetResetDTO =
        sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
            builderFactory.getSLOErrorBudgetResetDTOBuilder().serviceLevelObjectiveIdentifier(sloIdentifier).build());
    List<SLOErrorBudgetResetDTO> saved =
        sloErrorBudgetResetService.getErrorBudgetResets(builderFactory.getProjectParams(), sloIdentifier);
    assertThat(saved).hasSize(1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetErrorBudgetResetsMap() {
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(),
        builderFactory.getSimpleCalendarServiceLevelObjectiveV2DTOBuilder().identifier("slo1").build());
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(),
        builderFactory.getSimpleCalendarServiceLevelObjectiveV2DTOBuilder().identifier("slo2").build());
    sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
        builderFactory.getSLOErrorBudgetResetDTOBuilder().serviceLevelObjectiveIdentifier("slo1").build());
    sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
        builderFactory.getSLOErrorBudgetResetDTOBuilder()
            .serviceLevelObjectiveIdentifier("slo2")
            .errorBudgetIncrementMinutes(20)
            .build());
    Map<String, List<SLOErrorBudgetResetDTO>> savedMap = sloErrorBudgetResetService.getErrorBudgetResets(
        builderFactory.getProjectParams(), new HashSet<>(Arrays.asList("slo1", "slo2")));
    assertThat(savedMap).hasSize(2);
    assertThat(savedMap.get("slo1").get(0).getErrorBudgetIncrementMinutes()).isEqualTo(10);
    assertThat(savedMap.get("slo2").get(0).getErrorBudgetIncrementMinutes()).isEqualTo(20);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGlearErrorBudgetResets() {
    createMonitoredService();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse = serviceLevelObjectiveV2Service.create(
        builderFactory.getProjectParams(), builderFactory.getSimpleCalendarServiceLevelObjectiveV2DTOBuilder().build());
    String sloIdentifier = serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getIdentifier();
    SLOErrorBudgetResetDTO sloErrorBudgetResetDTO =
        sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
            builderFactory.getSLOErrorBudgetResetDTOBuilder().serviceLevelObjectiveIdentifier(sloIdentifier).build());
    sloErrorBudgetResetService.clearErrorBudgetResets(builderFactory.getProjectParams(), sloIdentifier);
    List<SLOErrorBudgetResetDTO> savedList =
        sloErrorBudgetResetService.getErrorBudgetResets(builderFactory.getProjectParams(), sloIdentifier);
    assertThat(savedList).isNullOrEmpty();
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }
}
