/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
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
  @Inject ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject MonitoredServiceService monitoredServiceService;

  BuilderFactory builderFactory;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    FieldUtils.writeField(sloErrorBudgetResetService, "clock", builderFactory.getClock(), true);
    FieldUtils.writeField(serviceLevelObjectiveService, "clock", builderFactory.getClock(), true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testResetErrorBudget() {
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(
        builderFactory.getProjectParams(), builderFactory.getServiceLevelObjectiveDTOBuilder().build());
    String sloIdentifier = serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getIdentifier();
    SLOErrorBudgetResetDTO sloErrorBudgetResetDTO =
        sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
            builderFactory.getSLOErrorBudgetResetDTOBuilder().serviceLevelObjectiveIdentifier(sloIdentifier).build());
    SLOErrorBudgetResetDTO saved =
        sloErrorBudgetResetService.getErrorBudgetResets(builderFactory.getProjectParams(), sloIdentifier).get(0);
    assertThat(saved.getValidUntil().longValue()).isEqualTo(1587549726000L);
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
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(
        builderFactory.getProjectParams(), builderFactory.getServiceLevelObjectiveDTOBuilder().build());
    String sloIdentifier = serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getIdentifier();
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
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(),
        builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("slo1").build());
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(),
        builderFactory.getServiceLevelObjectiveDTOBuilder().identifier("slo2").build());
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
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(
        builderFactory.getProjectParams(), builderFactory.getServiceLevelObjectiveDTOBuilder().build());
    String sloIdentifier = serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getIdentifier();
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
