/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.GOOD;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.Point;
import io.harness.cvng.servicelevelobjective.beans.SLOTarget;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLODashboardServiceImplTest extends CvNextGenTestBase {
  @Inject private SLODashboardService sloDashboardService;
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private MetricPackService metricPackService;
  @Inject private SLIRecordService sliRecordService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private SLOErrorBudgetResetService sloErrorBudgetResetService;

  @Inject private Clock clock;
  private BuilderFactory builderFactory;
  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardWidgets_emptyResponse() {
    PageResponse<SLODashboardWidget> pageResponse =
        sloDashboardService.getSloDashboardWidgets(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(4).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(0);
    assertThat(pageResponse.getTotalItems()).isEqualTo(0);
    assertThat(pageResponse.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardWidgets_withSLOs() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveDTO serviceLevelObjective = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                                         .monitoredServiceRef(monitoredServiceIdentifier)
                                                         .healthSourceRef(healthSource.getIdentifier())
                                                         .build();

    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), serviceLevelObjective);
    PageResponse<SLODashboardWidget> pageResponse =
        sloDashboardService.getSloDashboardWidgets(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(4).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLODashboardWidget> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLODashboardWidget sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjective.getTags());
    assertThat(sloDashboardWidget.getType())
        .isEqualTo(serviceLevelObjective.getServiceLevelIndicators().get(0).getType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(serviceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getCurrentPeriodLengthDays()).isEqualTo(30);
    assertThat(sloDashboardWidget.getCurrentPeriodStartTime())
        .isEqualTo(Instant.parse("2020-06-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getCurrentPeriodEndTime())
        .isEqualTo(Instant.parse("2020-07-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getErrorBudgetRemaining()).isEqualTo(8640); // 30 days - 30*24*60 - 20% -> 8640
    assertThat(sloDashboardWidget.getSloTargetPercentage()).isCloseTo(80, offset(.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(100, offset(0.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(sloDashboardWidget.isRecalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(0);
    assertThat(sloDashboardWidget.getServiceIdentifier()).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(sloDashboardWidget.getEnvironmentIdentifier()).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(sloDashboardWidget.getServiceName()).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.getEnvironmentName()).isEqualTo("Mocked env name");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardWidgets_withSLOQuarter() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveDTO serviceLevelObjective = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                                         .monitoredServiceRef(monitoredServiceIdentifier)
                                                         .healthSourceRef(healthSource.getIdentifier())
                                                         .build();

    SLOTarget calendarSloTarget = SLOTarget.builder()
                                      .type(SLOTargetType.CALENDER)
                                      .sloTargetPercentage(80.0)
                                      .spec(CalenderSLOTargetSpec.builder()
                                                .type(SLOCalenderType.QUARTERLY)
                                                .spec(CalenderSLOTargetSpec.QuarterlyCalenderSpec.builder().build())
                                                .build())
                                      .build();
    serviceLevelObjective.setTarget(calendarSloTarget);
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), serviceLevelObjective);
    PageResponse<SLODashboardWidget> pageResponse =
        sloDashboardService.getSloDashboardWidgets(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(4).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLODashboardWidget> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLODashboardWidget sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(66);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetSloDashboardWidgets_withSLOErrorBudgetReset() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveDTO serviceLevelObjective = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                                         .monitoredServiceRef(monitoredServiceIdentifier)
                                                         .healthSourceRef(healthSource.getIdentifier())
                                                         .build();

    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), serviceLevelObjective);
    sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
        builderFactory.getSLOErrorBudgetResetDTOBuilder()
            .serviceLevelObjectiveIdentifier(serviceLevelObjective.getIdentifier())
            .errorBudgetIncrementMinutes(100)
            .build());
    sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
        builderFactory.getSLOErrorBudgetResetDTOBuilder()
            .serviceLevelObjectiveIdentifier(serviceLevelObjective.getIdentifier())
            .errorBudgetIncrementMinutes(50)
            .build());
    PageResponse<SLODashboardWidget> pageResponse =
        sloDashboardService.getSloDashboardWidgets(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(4).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLODashboardWidget> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLODashboardWidget sloDashboardWidget = sloDashboardWidgets.get(0);

    assertThat(sloDashboardWidget.getErrorBudgetRemaining())
        .isEqualTo(8790); // 30 days - 30*24*60 - 20% -> 8640 -> 8640 + 100 -> 8740  -> 8740 + 50-> 8790
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(100, offset(0.0001));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardWidgets_withSLIDatas() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveDTO serviceLevelObjective = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                                         .monitoredServiceRef(monitoredServiceIdentifier)
                                                         .healthSourceRef(healthSource.getIdentifier())
                                                         .build();

    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), serviceLevelObjective);
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), serviceLevelObjective.getServiceLevelIndicators().get(0).getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(10)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        serviceLevelIndicator.getUuid());
    PageResponse<SLODashboardWidget> pageResponse =
        sloDashboardService.getSloDashboardWidgets(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(4).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLODashboardWidget> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLODashboardWidget sloDashboardWidget = sloDashboardWidgets.get(0);
    assertSLIGraphData(clock.instant().minus(Duration.ofMinutes(10)), sloDashboardWidget.getSloPerformanceTrend(),
        sloDashboardWidget.getErrorBudgetBurndown(), Lists.newArrayList(100.0, 50.0, 33.33, 50.0),
        Lists.newArrayList(100.0, 99.9884, 99.9768, 99.9768));
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjective.getTags());
    assertThat(sloDashboardWidget.getType())
        .isEqualTo(serviceLevelObjective.getServiceLevelIndicators().get(0).getType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(serviceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getCurrentPeriodLengthDays()).isEqualTo(30);
    assertThat(sloDashboardWidget.getCurrentPeriodStartTime())
        .isEqualTo(Instant.parse("2020-06-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getCurrentPeriodEndTime())
        .isEqualTo(Instant.parse("2020-07-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getErrorBudgetRemaining())
        .isEqualTo(8638); // 30 days - 30*24*60 - 20% -> 8640 - (2 bad mins)
    assertThat(sloDashboardWidget.getSloTargetPercentage()).isCloseTo(80, offset(.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(99.9768, offset(0.001));
    assertThat(sloDashboardWidget.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(sloDashboardWidget.isRecalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(0);
    assertThat(sloDashboardWidget.getServiceIdentifier()).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(sloDashboardWidget.getEnvironmentIdentifier()).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(sloDashboardWidget.getServiceName()).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.getEnvironmentName()).isEqualTo("Mocked env name");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveDTO serviceLevelObjective = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                                         .monitoredServiceRef(monitoredServiceIdentifier)
                                                         .healthSourceRef(healthSource.getIdentifier())
                                                         .build();

    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), serviceLevelObjective);

    SLODashboardDetail sloDashboardDetail = sloDashboardService.getSloDashboardDetail(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier(), null, null);
    assertThat(sloDashboardDetail.getDescription()).isEqualTo("slo description");
    assertThat(sloDashboardDetail.getSloDashboardWidget().getSloIdentifier())
        .isEqualTo(serviceLevelObjective.getIdentifier());
  }

  private void createData(Instant startTime, List<SLIRecord.SLIState> sliStates, String sliId) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, sliId, 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIRecord.SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIRecord.SLIState sliState = sliStates.get(i);
      sliRecordParams.add(
          SLIRecordParam.builder().sliState(sliState).timeStamp(startTime.plus(Duration.ofMinutes(i))).build());
    }
    return sliRecordParams;
  }

  private void assertSLIGraphData(Instant startTime, List<Point> sloPerformanceTrend, List<Point> errorBudgetBurndown,
      List<Double> expectedSLITrend, List<Double> expectedBurndown) {
    for (int i = 0; i < expectedSLITrend.size(); i++) {
      assertThat(sloPerformanceTrend.get(i).getTimestamp())
          .isEqualTo(startTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(sloPerformanceTrend.get(i).getValue()).isCloseTo(expectedSLITrend.get(i), offset(0.01));
      assertThat(errorBudgetBurndown.get(i).getTimestamp())
          .isEqualTo(startTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(errorBudgetBurndown.get(i).getValue()).isCloseTo(expectedBurndown.get(i), offset(0.01));
    }
  }
}
