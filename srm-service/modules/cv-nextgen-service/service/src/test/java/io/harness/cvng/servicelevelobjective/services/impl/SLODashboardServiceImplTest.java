/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.NO_DATA;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;
import static io.harness.rule.TestUserProvider.testUserProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import io.harness.CvNextGenTestBase;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceListItemDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.EntityDetails;
import io.harness.cvng.downtime.beans.EntityIdentifiersRule;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.AnnotationInstanceDetails;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLOConsumptionBreakdown;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.Point;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetFilterDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.UnavailabilityInstancesResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryEvents.SecondaryEventDetailsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryEvents.SecondaryEventsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryEvents.SecondaryEventsType;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RollingSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.Annotation;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.AnnotationService;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLODashboardServiceImplTest extends CvNextGenTestBase {
  @Inject private SLODashboardService sloDashboardService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private MetricPackService metricPackService;
  @Inject private SLIRecordService sliRecordService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private SLOErrorBudgetResetService sloErrorBudgetResetService;
  @Inject private CompositeSLORecordService sloRecordService;
  @Inject private GraphDataService graphDataService;

  @Inject private DowntimeService downtimeService;
  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;
  @Inject private AnnotationService annotationService;
  private Instant startTime;
  private Instant endTime;
  private String verificationTaskId;
  private CompositeServiceLevelObjective compositeServiceLevelObjective;

  @Inject private Clock clock;
  @Inject private HPersistence hPersistence;
  private BuilderFactory builderFactory;
  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setProjectIdentifier("project");
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());

    startTime = TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
    testUserProvider.setActiveUser(EmbeddedUser.builder().name("user1").email("user1@harness.io").build());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_withNoData() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    SLODashboardWidget sloDashboardWidget =
        sloDashboardService
            .getSloDashboardDetail(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier(), null, null)
            .getSloDashboardWidget();

    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().size()).isEqualTo(1);
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().get(0).getMonitoredServiceIdentifier())
        .isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjective.getTags());
    assertThat(sloDashboardWidget.getType()).isEqualTo(simpleServiceLevelObjectiveSpec.getServiceLevelIndicatorType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(serviceLevelObjective.getSloTarget().getType());
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
    assertThat(sloDashboardWidget.isCalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(0);
    assertThat(sloDashboardWidget.getServiceIdentifier()).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(sloDashboardWidget.getEnvironmentIdentifier()).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(sloDashboardWidget.getServiceName()).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.getEnvironmentName()).isEqualTo("Mocked env name");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_withSLOQuarter() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(simpleServiceLevelObjectiveSpec);
    SLOTargetDTO calendarSloTarget = SLOTargetDTO.builder()
                                         .type(SLOTargetType.CALENDER)
                                         .sloTargetPercentage(80.0)
                                         .spec(CalenderSLOTargetSpec.builder()
                                                   .type(SLOCalenderType.QUARTERLY)
                                                   .spec(CalenderSLOTargetSpec.QuarterlyCalenderSpec.builder().build())
                                                   .build())
                                         .build();
    serviceLevelObjective.setSloTarget(calendarSloTarget);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    SLODashboardWidget sloDashboardWidget =
        sloDashboardService
            .getSloDashboardDetail(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier(), null, null)
            .getSloDashboardWidget();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(66);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  @Ignore("resetErrorBudget function is not present")
  public void testGetSloDashboardDetail_withSLOErrorBudgetReset() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(simpleServiceLevelObjectiveSpec);

    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);
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
    SLODashboardWidget sloDashboardWidget =
        sloDashboardService
            .getSloDashboardDetail(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier(), null, null)
            .getSloDashboardWidget();

    assertThat(sloDashboardWidget.getErrorBudgetRemaining())
        .isEqualTo(8790); // 30 days - 30*24*60 - 20% -> 8640 -> 8640 + 100 -> 8740  -> 8740 + 50-> 8790
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(100, offset(0.0001));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_SimpleSLO_withSLIDatas() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(10)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        serviceLevelIndicator.getUuid());
    SLODashboardWidget sloDashboardWidget =
        sloDashboardService
            .getSloDashboardDetail(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier(), null, null)
            .getSloDashboardWidget();

    assertSLIGraphData(clock.instant().minus(Duration.ofMinutes(10)), sloDashboardWidget.getSloPerformanceTrend(),
        sloDashboardWidget.getErrorBudgetBurndown(), Lists.newArrayList(100.0, 50.0, 33.33, 50.0),
        Lists.newArrayList(100.0, 99.9884, 99.9768, 99.9768));
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().size()).isEqualTo(1);
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().get(0).getMonitoredServiceIdentifier())
        .isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjective.getTags());
    assertThat(sloDashboardWidget.getType()).isEqualTo(simpleServiceLevelObjectiveSpec.getServiceLevelIndicatorType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(serviceLevelObjective.getSloTarget().getType());
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
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_CompositeSLO_withSLIDatas() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    SimpleServiceLevelObjective simpleServiceLevelObjective1 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1.getIdentifier());

    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(healthSource.getIdentifier());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2);
    SimpleServiceLevelObjective simpleServiceLevelObjective2 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2.getIdentifier());

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
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
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    verificationTaskId = compositeServiceLevelObjective.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25);
    createSLORecords(startTime, endTime.minusSeconds(120), runningGoodCount, runningBadCount);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(1.75);
    assertThat(sloRecords.get(0).getSloVersion()).isEqualTo(0);

    SLODashboardWidget sloDashboardWidget =
        sloDashboardService
            .getSloDashboardDetail(builderFactory.getProjectParams(), compositeServiceLevelObjective.getIdentifier(),
                startTime.toEpochMilli(), endTime.toEpochMilli())
            .getSloDashboardWidget();
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(compositeServiceLevelObjective.getIdentifier());
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjectiveV2DTO.getTags());
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().size()).isEqualTo(2);
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().get(0).getMonitoredServiceIdentifier())
        .isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().get(1).getMonitoredServiceIdentifier())
        .isEqualTo(monitoredServiceDTO2.getIdentifier());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(compositeServiceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(compositeServiceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getCurrentPeriodLengthDays()).isEqualTo(30);
    assertThat(sloDashboardWidget.getCurrentPeriodStartTime())
        .isEqualTo(Instant.parse("2020-06-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getCurrentPeriodEndTime())
        .isEqualTo(Instant.parse("2020-07-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getErrorBudgetRemaining()).isEqualTo(8639); // 8640 - (1.25 bad mins)
    assertThat(sloDashboardWidget.getSloTargetPercentage()).isCloseTo(80, offset(.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(99.9855, offset(0.001));
    assertThat(sloDashboardWidget.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(sloDashboardWidget.isRecalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.isCalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(0);
    assertCompositeSLOGraphData(clock.instant().minus(Duration.ofMinutes(10)),
        sloDashboardWidget.getSloPerformanceTrend(), sloDashboardWidget.getErrorBudgetBurndown(), runningGoodCount,
        runningBadCount, 8640);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_CompositeSLO_withMonitoredServiceDetails() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    SimpleServiceLevelObjective simpleServiceLevelObjective1 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1.getIdentifier());

    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(healthSource.getIdentifier());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2);
    SimpleServiceLevelObjective simpleServiceLevelObjective2 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2.getIdentifier());

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
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
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    SLODashboardWidget sloDashboardWidget =
        sloDashboardService
            .getSloDashboardDetail(builderFactory.getProjectParams(), compositeServiceLevelObjective.getIdentifier(),
                startTime.toEpochMilli(), endTime.toEpochMilli())
            .getSloDashboardWidget();
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(compositeServiceLevelObjective.getIdentifier());
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjectiveV2DTO.getTags());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(compositeServiceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(compositeServiceLevelObjective.getTarget().getType());
    List<MonitoredServiceDetail> monitoredServiceDetails = sloDashboardWidget.getMonitoredServiceDetails();
    assertThat(monitoredServiceDetails.size()).isEqualTo(2);
    MonitoredServiceDetail monitoredServiceDetail = monitoredServiceDetails.get(0);
    assertThat(monitoredServiceDetail.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(monitoredServiceDetail.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(monitoredServiceDetail.getServiceIdentifier()).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(monitoredServiceDetail.getEnvironmentIdentifier()).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(monitoredServiceDetail.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(monitoredServiceDetail.getProjectParams()).isEqualTo(builderFactory.getProjectParams());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_emptyResponse() {
    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(0);
    assertThat(pageResponse.getTotalItems()).isEqualTo(0);
    assertThat(pageResponse.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_withNoData() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(simpleServiceLevelObjectiveSpec);

    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);
    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjective.getTags());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(serviceLevelObjective.getSloTarget().getType());
    assertThat(sloDashboardWidget.getErrorBudgetRemaining()).isEqualTo(8640); // 30 days - 30*24*60 - 20% -> 8640
    assertThat(sloDashboardWidget.getSloTargetPercentage()).isCloseTo(80, offset(.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(100, offset(0.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(sloDashboardWidget.getServiceIdentifier()).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(sloDashboardWidget.getEnvironmentIdentifier()).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(sloDashboardWidget.getNoOfActiveAlerts())
        .isEqualTo(serviceLevelObjective.getNotificationRuleRefs().size());
    assertThat(sloDashboardWidget.getServiceName()).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.getEnvironmentName()).isEqualTo("Mocked env name");
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListViewSearchFunctionality() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    ServiceLevelObjectiveV2DTO serviceLevelObjective1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective1);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective3.setName("new three");
    serviceLevelObjective3.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().searchFilter("ew").build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(2);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getName()).isEqualTo(serviceLevelObjective3.getName());
    sloDashboardWidget = sloDashboardWidgets.get(1);
    assertThat(sloDashboardWidget.getName()).isEqualTo(serviceLevelObjective2.getName());

    //    with special character
    pageResponse = sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
        SLODashboardApiFilter.builder().searchFilter("*").build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(0);
    assertThat(pageResponse.getTotalItems()).isEqualTo(0);
    sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(0);

    //    with no such SLO
    pageResponse = sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
        SLODashboardApiFilter.builder().searchFilter("random").build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(0);
    assertThat(pageResponse.getTotalItems()).isEqualTo(0);
    sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(0);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListViewWithFiltersApplied() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective1);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective3.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new three");
    serviceLevelObjective3.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO serviceLevelObjective4 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective4.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new four");
    serviceLevelObjective3.setIdentifier("new_four");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO compositeSLO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef("new_two")
                                            .weightagePercentage(75.0)
                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("new_four")
                                  .weightagePercentage(25.0)
                                  .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                  .accountId(builderFactory.getContext().getAccountId())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);

    PageResponse<MonitoredServiceListItemDTO> msPageResponse =
        monitoredServiceService.list(builderFactory.getProjectParams(), null, 0, 10, null, false);
    assertThat(msPageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(msPageResponse.getTotalItems()).isEqualTo(2);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder()
                .monitoredServiceIdentifier(monitoredServiceIdentifier + '1')
                .type(ServiceLevelObjectiveType.SIMPLE)
                .build(),
            PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(2);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getName()).isEqualTo(serviceLevelObjective3.getName());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_EvaluationTypeFilter_Request() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective1);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective3.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new three");
    serviceLevelObjective3.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO serviceLevelObjective4 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective4.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new four");
    serviceLevelObjective3.setIdentifier("new_four");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO serviceLevelObjective5 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective5.setName("new five");
    serviceLevelObjective5.setIdentifier("new_five");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective5);

    ServiceLevelObjectiveV2DTO serviceLevelObjective6 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective6.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective6.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective6.setName("new six");
    serviceLevelObjective6.setIdentifier("new_six");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective6);

    ServiceLevelObjectiveV2DTO compositeSLO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef("new_two")
                                            .weightagePercentage(75.0)
                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("new_four")
                                  .weightagePercentage(25.0)
                                  .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                  .accountId(builderFactory.getContext().getAccountId())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);

    compositeSLO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                       .name("requestComposite")
                       .identifier("requestComposite")
                       .spec(CompositeServiceLevelObjectiveSpec.builder()
                                 .serviceLevelObjectivesDetails(Arrays.asList(
                                     ServiceLevelObjectiveDetailsDTO.builder()
                                         .serviceLevelObjectiveRef("new_five")
                                         .weightagePercentage(75.0)
                                         .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                         .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                         .accountId(builderFactory.getContext().getAccountId())
                                         .build(),
                                     ServiceLevelObjectiveDetailsDTO.builder()
                                         .serviceLevelObjectiveRef("new_six")
                                         .weightagePercentage(25.0)
                                         .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                         .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                         .accountId(builderFactory.getContext().getAccountId())
                                         .build()))
                                 .build())
                       .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);

    PageResponse<MonitoredServiceListItemDTO> msPageResponse =
        monitoredServiceService.list(builderFactory.getProjectParams(), null, 0, 10, null, false);
    assertThat(msPageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(msPageResponse.getTotalItems()).isEqualTo(2);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().evaluationType(SLIEvaluationType.REQUEST).build(),
            PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(3);
    assertThat(pageResponse.getTotalItems()).isEqualTo(3);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(3);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getName()).isEqualTo(compositeSLO.getName());
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.REQUEST);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_EvaluationTypeFilter_Window() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective1);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective3.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new three");
    serviceLevelObjective3.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO serviceLevelObjective4 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective4.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new four");
    serviceLevelObjective3.setIdentifier("new_four");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO serviceLevelObjective5 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective5.setName("new five");
    serviceLevelObjective5.setIdentifier("new_five");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective5);

    ServiceLevelObjectiveV2DTO serviceLevelObjective6 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective6.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective6.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective6.setName("new six");
    serviceLevelObjective6.setIdentifier("new_six");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective6);

    ServiceLevelObjectiveV2DTO compositeSLO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef("new_two")
                                            .weightagePercentage(75.0)
                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("new_four")
                                  .weightagePercentage(25.0)
                                  .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                  .accountId(builderFactory.getContext().getAccountId())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);

    compositeSLO = builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
                       .name("requestComposite")
                       .identifier("requestComposite")
                       .spec(CompositeServiceLevelObjectiveSpec.builder()
                                 .serviceLevelObjectivesDetails(Arrays.asList(
                                     ServiceLevelObjectiveDetailsDTO.builder()
                                         .serviceLevelObjectiveRef("new_five")
                                         .weightagePercentage(75.0)
                                         .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                         .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                         .accountId(builderFactory.getContext().getAccountId())
                                         .build(),
                                     ServiceLevelObjectiveDetailsDTO.builder()
                                         .serviceLevelObjectiveRef("new_six")
                                         .weightagePercentage(25.0)
                                         .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                         .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                         .accountId(builderFactory.getContext().getAccountId())
                                         .build()))
                                 .build())
                       .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);

    PageResponse<MonitoredServiceListItemDTO> msPageResponse =
        monitoredServiceService.list(builderFactory.getProjectParams(), null, 0, 10, null, false);
    assertThat(msPageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(msPageResponse.getTotalItems()).isEqualTo(2);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().evaluationType(SLIEvaluationType.WINDOW).build(),
            PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(5);
    assertThat(pageResponse.getTotalItems()).isEqualTo(5);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(5);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.WINDOW);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_withSLOQuarter() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(simpleServiceLevelObjectiveSpec);

    SLOTargetDTO calendarSloTarget = SLOTargetDTO.builder()
                                         .type(SLOTargetType.CALENDER)
                                         .sloTargetPercentage(80.0)
                                         .spec(CalenderSLOTargetSpec.builder()
                                                   .type(SLOCalenderType.QUARTERLY)
                                                   .spec(CalenderSLOTargetSpec.QuarterlyCalenderSpec.builder().build())
                                                   .build())
                                         .build();
    serviceLevelObjective.setSloTarget(calendarSloTarget);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);
    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_AccountScoped() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";

    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    ProjectParams projectParam1 = builderFactory.getProjectParams();
    projectParam1.setProjectIdentifier("project1");
    metricPackService.createDefaultMetricPackAndThresholds(
        projectParam1.getAccountIdentifier(), projectParam1.getOrgIdentifier(), projectParam1.getProjectIdentifier());
    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .identifier(monitoredServiceIdentifier + '1')
                                                   .projectIdentifier("project1")
                                                   .build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective1);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().projectIdentifier("project3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective3.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new three");
    serviceLevelObjective3.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(projectParam1, serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO serviceLevelObjective4 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective4.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new four");
    serviceLevelObjective3.setIdentifier("new_four");
    serviceLevelObjectiveV2Service.create(projectParam1, serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO compositeSLO =
        ServiceLevelObjectiveV2DTO.builder()
            .type(ServiceLevelObjectiveType.COMPOSITE)
            .identifier("compositeSloIdentifier")
            .name("sloName")
            .tags(new HashMap<String, String>() {
              {
                put("tag1", "value1");
                put("tag2", "");
              }
            })
            .description("slo description")
            .sloTarget(SLOTargetDTO.builder()
                           .type(SLOTargetType.ROLLING)
                           .sloTargetPercentage(80.0)
                           .spec(RollingSLOTargetSpec.builder().periodLength("30d").build())
                           .build())
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef("new_two")
                                            .weightagePercentage(75.0)
                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("new_three")
                                  .weightagePercentage(25.0)
                                  .projectIdentifier(projectParam1.getProjectIdentifier())
                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                  .accountId(builderFactory.getContext().getAccountId())
                                  .build()))
                      .build())
            .userJourneyRefs(Collections.singletonList("userJourney"))
            .build();
    serviceLevelObjectiveV2Service.create(
        ProjectParams.builder().accountIdentifier(builderFactory.getProjectParams().getAccountIdentifier()).build(),
        compositeSLO);
    // SLO Health List view page.
    PageResponse<SLOHealthListView> pageResponse = sloDashboardService.getSloHealthListView(
        ProjectParams.builder().accountIdentifier(builderFactory.getProjectParams().getAccountIdentifier()).build(),
        SLODashboardApiFilter.builder()
            .type(ServiceLevelObjectiveType.COMPOSITE)
            .evaluationType(SLIEvaluationType.WINDOW)
            .build(),
        PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getName()).isEqualTo(compositeSLO.getName());

    // SLO Health List view page to add simple slo's.
    pageResponse = sloDashboardService.getSloHealthListView(
        ProjectParams.builder().accountIdentifier(builderFactory.getProjectParams().getAccountIdentifier()).build(),
        SLODashboardApiFilter.builder().type(ServiceLevelObjectiveType.SIMPLE).childResource(true).build(),
        PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(4);
    assertThat(pageResponse.getTotalItems()).isEqualTo(4);
    sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(4);

    // SLO health list view page for getting simple slo's in a composite slo
    pageResponse = sloDashboardService.getSloHealthListView(
        ProjectParams.builder().accountIdentifier(builderFactory.getProjectParams().getAccountIdentifier()).build(),
        SLODashboardApiFilter.builder()
            .type(ServiceLevelObjectiveType.SIMPLE)
            .compositeSLOIdentifier(compositeSLO.getIdentifier())
            .childResource(true)
            .build(),
        PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(2);
  }
  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_withSLOTargetFilterApplied() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    SLOTargetDTO sloTargetDTO = SLOTargetDTO.builder()
                                    .type(SLOTargetType.CALENDER)
                                    .sloTargetPercentage(80.0)
                                    .spec(CalenderSLOTargetSpec.builder()
                                              .type(SLOCalenderType.QUARTERLY)
                                              .spec(CalenderSLOTargetSpec.QuarterlyCalenderSpec.builder().build())
                                              .build())
                                    .build();

    ServiceLevelObjectiveV2DTO serviceLevelObjective1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().sloTarget(sloTargetDTO).build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective1);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().sloTarget(sloTargetDTO).build();
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective3.setName("new three");
    serviceLevelObjective3.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder()
                .type(ServiceLevelObjectiveType.SIMPLE)
                .sloTargetFilterDTO(
                    SLOTargetFilterDTO.builder().type(sloTargetDTO.getType()).spec(sloTargetDTO.getSpec()).build())
                .build(),
            PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(2);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSLOConsumptionBreakdownView() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2DTO1.setName("new two");
    serviceLevelObjectiveV2DTO1.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO1);
    AbstractServiceLevelObjective serviceLevelObjective1 = serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO1.getIdentifier());

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO2.getSpec();
    WindowBasedServiceLevelIndicatorSpec serviceLevelIndicatorSpec =
        (WindowBasedServiceLevelIndicatorSpec) simpleServiceLevelObjectiveSpec.getServiceLevelIndicators()
            .get(0)
            .getSpec();
    serviceLevelIndicatorSpec.setSliMissingDataType(SLIMissingDataType.BAD);
    simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).setSpec(serviceLevelIndicatorSpec);
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjectiveV2DTO2.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2DTO2.setName("new three");
    serviceLevelObjectiveV2DTO2.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO2);
    AbstractServiceLevelObjective serviceLevelObjective2 = serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO2.getIdentifier());

    ServiceLevelObjectiveV2DTO compositeSLO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef("new_two")
                                            .weightagePercentage(75.0)
                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("new_three")
                                  .weightagePercentage(25.0)
                                  .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                  .accountId(builderFactory.getContext().getAccountId())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);
    AbstractServiceLevelObjective compositeServiceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(builderFactory.getProjectParams(), compositeSLO.getIdentifier());
    serviceLevelObjectiveV2Service.delete(builderFactory.getProjectParams(), compositeSLO.getIdentifier());
    compositeServiceLevelObjective.setCreatedAt(startTime.toEpochMilli());
    compositeServiceLevelObjective.setStartedAt(startTime.toEpochMilli());
    hPersistence.save(compositeServiceLevelObjective);
    ServiceLevelIndicator serviceLevelIndicator1 =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO1.getSpec())
                .getServiceLevelIndicators()
                .get(0)
                .getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(12)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        serviceLevelIndicator1.getUuid());
    SLODashboardWidget.SLOGraphData sloGraphData1 =
        graphDataService.getGraphData(serviceLevelObjective1, clock.instant().minus(Duration.ofDays(1)),
            clock.instant(), 8640, TimeRangeParams.builder().startTime(startTime).endTime(endTime).build());

    ServiceLevelIndicator serviceLevelIndicator2 =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO2.getSpec())
                .getServiceLevelIndicators()
                .get(0)
                .getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(12)), Arrays.asList(NO_DATA, BAD, BAD, NO_DATA),
        serviceLevelIndicator2.getUuid());
    SLODashboardWidget.SLOGraphData sloGraphData2 =
        graphDataService.getGraphData(serviceLevelObjective2, clock.instant().minus(Duration.ofDays(1)),
            clock.instant(), 8640, TimeRangeParams.builder().startTime(startTime).endTime(endTime).build());

    PageResponse<SLOConsumptionBreakdown> pageResponse = sloDashboardService.getSLOConsumptionBreakdownView(
        builderFactory.getProjectParams(), compositeSLO.getIdentifier(),
        startTime.minus(2, ChronoUnit.MINUTES).toEpochMilli(), endTime.toEpochMilli());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    List<SLOConsumptionBreakdown> sloConsumptionBreakdownList = pageResponse.getContent();
    assertThat(sloConsumptionBreakdownList).hasSize(2);

    SLOConsumptionBreakdown sloBreakdown = sloConsumptionBreakdownList.get(0);
    assertThat(sloBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO1.getIdentifier());
    assertThat(sloBreakdown.getSloName()).isEqualTo(serviceLevelObjectiveV2DTO1.getName());
    assertThat(sloBreakdown.getSliType())
        .isEqualTo(
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO1.getSpec()).getServiceLevelIndicatorType());
    assertThat(sloBreakdown.getSloTargetPercentage())
        .isEqualTo(serviceLevelObjectiveV2DTO1.getSloTarget().getSloTargetPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(sloGraphData1.getErrorBudgetBurned());
    assertThat(sloBreakdown.getSliStatusPercentage()).isEqualTo(sloGraphData1.getSliStatusPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(1);

    sloBreakdown = sloConsumptionBreakdownList.get(1);
    assertThat(sloBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO2.getIdentifier());
    assertThat(sloBreakdown.getSloName()).isEqualTo(serviceLevelObjectiveV2DTO2.getName());
    assertThat(sloBreakdown.getSliType())
        .isEqualTo(
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO2.getSpec()).getServiceLevelIndicatorType());
    assertThat(sloBreakdown.getSloTargetPercentage())
        .isEqualTo(serviceLevelObjectiveV2DTO2.getSloTarget().getSloTargetPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(sloGraphData2.getErrorBudgetBurned());
    assertThat(sloBreakdown.getSliStatusPercentage()).isEqualTo(sloGraphData2.getSliStatusPercentage());
    assertThat(sloBreakdown.getProjectParams()).isEqualTo(builderFactory.getProjectParams());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSLOConsumptionBreakdownView_ForAccountScoped() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";

    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    ProjectParams projectParam1 = builderFactory.getProjectParams();
    projectParam1.setProjectIdentifier("project1");
    metricPackService.createDefaultMetricPackAndThresholds(
        projectParam1.getAccountIdentifier(), projectParam1.getOrgIdentifier(), projectParam1.getProjectIdentifier());
    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .identifier(monitoredServiceIdentifier + '1')
                                                   .projectIdentifier("project1")
                                                   .build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2DTO1.setName("new one");
    serviceLevelObjectiveV2DTO1.setIdentifier("new_one");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO1);
    AbstractServiceLevelObjective serviceLevelObjective1 = serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO1.getIdentifier());

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().projectIdentifier("project3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO2.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjectiveV2DTO2.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2DTO2.setName("new two");
    serviceLevelObjectiveV2DTO2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(projectParam1, serviceLevelObjectiveV2DTO2);
    AbstractServiceLevelObjective serviceLevelObjective2 =
        serviceLevelObjectiveV2Service.getEntity(projectParam1, serviceLevelObjectiveV2DTO2.getIdentifier());

    ServiceLevelObjectiveV2DTO compositeSLO =
        ServiceLevelObjectiveV2DTO.builder()
            .type(ServiceLevelObjectiveType.COMPOSITE)
            .identifier("compositeSloIdentifier")
            .name("sloName")
            .tags(new HashMap<String, String>() {
              {
                put("tag1", "value1");
                put("tag2", "");
              }
            })
            .description("slo description")
            .sloTarget(SLOTargetDTO.builder()
                           .type(SLOTargetType.ROLLING)
                           .sloTargetPercentage(80.0)
                           .spec(RollingSLOTargetSpec.builder().periodLength("30d").build())
                           .build())
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef("new_one")
                                            .weightagePercentage(75.0)
                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("new_two")
                                  .weightagePercentage(25.0)
                                  .projectIdentifier(projectParam1.getProjectIdentifier())
                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                  .accountId(builderFactory.getContext().getAccountId())
                                  .build()))
                      .build())
            .userJourneyRefs(Collections.singletonList("userJourney"))
            .build();
    serviceLevelObjectiveV2Service.create(
        ProjectParams.builder().accountIdentifier(builderFactory.getProjectParams().getAccountIdentifier()).build(),
        compositeSLO);
    hPersistence.update(hPersistence.createQuery(AbstractServiceLevelObjective.class),
        hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class)
            .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.startedAt, startTime.toEpochMilli())
            .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.createdAt, startTime.toEpochMilli())
            .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.lastUpdatedAt, startTime.toEpochMilli()));
    ServiceLevelIndicator serviceLevelIndicator1 =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO1.getSpec())
                .getServiceLevelIndicators()
                .get(0)
                .getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(10)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        serviceLevelIndicator1.getUuid());
    SLODashboardWidget.SLOGraphData sloGraphData1 =
        graphDataService.getGraphData(serviceLevelObjective1, clock.instant().minus(Duration.ofDays(1)),
            clock.instant(), 8640, TimeRangeParams.builder().startTime(startTime).endTime(endTime).build());

    ServiceLevelIndicator serviceLevelIndicator2 = serviceLevelIndicatorService.getServiceLevelIndicator(projectParam1,
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO2.getSpec())
            .getServiceLevelIndicators()
            .get(0)
            .getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(10)), Arrays.asList(BAD, BAD, BAD, BAD),
        serviceLevelIndicator2.getUuid());
    SLODashboardWidget.SLOGraphData sloGraphData2 =
        graphDataService.getGraphData(serviceLevelObjective2, clock.instant().minus(Duration.ofDays(1)),
            clock.instant(), 8640, TimeRangeParams.builder().startTime(startTime).endTime(endTime).build());

    PageResponse<SLOConsumptionBreakdown> pageResponse = sloDashboardService.getSLOConsumptionBreakdownView(
        ProjectParams.builder().accountIdentifier(builderFactory.getProjectParams().getAccountIdentifier()).build(),
        compositeSLO.getIdentifier(), startTime.toEpochMilli(), endTime.toEpochMilli());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    List<SLOConsumptionBreakdown> sloConsumptionBreakdownList = pageResponse.getContent();
    assertThat(sloConsumptionBreakdownList).hasSize(2);

    SLOConsumptionBreakdown sloBreakdown = sloConsumptionBreakdownList.get(0);
    assertThat(sloBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO1.getIdentifier());
    assertThat(sloBreakdown.getSloName()).isEqualTo(serviceLevelObjectiveV2DTO1.getName());
    assertThat(sloBreakdown.getSliType())
        .isEqualTo(
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO1.getSpec()).getServiceLevelIndicatorType());
    assertThat(sloBreakdown.getSloTargetPercentage())
        .isEqualTo(serviceLevelObjectiveV2DTO1.getSloTarget().getSloTargetPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(sloGraphData1.getErrorBudgetBurned());
    assertThat(sloBreakdown.getSliStatusPercentage()).isEqualTo(sloGraphData1.getSliStatusPercentage());
    assertThat(sloBreakdown.getProjectParams()).isEqualTo(builderFactory.getProjectParams());

    sloBreakdown = sloConsumptionBreakdownList.get(1);
    assertThat(sloBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO2.getIdentifier());
    assertThat(sloBreakdown.getSloName()).isEqualTo(serviceLevelObjectiveV2DTO2.getName());
    assertThat(sloBreakdown.getSliType())
        .isEqualTo(
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO2.getSpec()).getServiceLevelIndicatorType());
    assertThat(sloBreakdown.getSloTargetPercentage())
        .isEqualTo(serviceLevelObjectiveV2DTO2.getSloTarget().getSloTargetPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(sloGraphData2.getErrorBudgetBurned());
    assertThat(sloBreakdown.getSliStatusPercentage()).isEqualTo(sloGraphData2.getSliStatusPercentage());
    assertThat(sloBreakdown.getProjectParams()).isEqualTo(projectParam1);
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
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceIdentifier);
    spec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    SLODashboardDetail sloDashboardDetail = sloDashboardService.getSloDashboardDetail(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier(), null, null);
    assertThat(sloDashboardDetail.getDescription()).isEqualTo("slo description");
    assertThat(sloDashboardDetail.getSloDashboardWidget().getSloIdentifier())
        .isEqualTo(serviceLevelObjective.getIdentifier());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetUnavailabilityInstancesForSimpleSLO() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceIdentifier);
    spec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    long endTime = startTime + Duration.ofDays(365).toSeconds();

    DowntimeDTO downtimeDTO = builderFactory.getRecurringDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().entityRef(monitoredServiceIdentifier).enabled(true).build()))
            .build());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    List<UnavailabilityInstancesResponse> unavailabilityInstancesResponses =
        sloDashboardService.getUnavailabilityInstances(
            builderFactory.getProjectParams(), startTime * 1000, endTime * 1000, serviceLevelObjective.getIdentifier());
    assertThat(unavailabilityInstancesResponses.size()).isEqualTo(53);
    assertThat(unavailabilityInstancesResponses.get(0).getEntityIdentifier()).isEqualTo(downtimeDTO.getIdentifier());
    assertThat(unavailabilityInstancesResponses.get(0).getEntityType()).isEqualTo(EntityType.MAINTENANCE_WINDOW);

    unavailabilityInstancesResponses = sloDashboardService.getUnavailabilityInstances(builderFactory.getProjectParams(),
        startTime * 1000, startTime * 1000 + Duration.ofDays(6).toMillis(), serviceLevelObjective.getIdentifier());
    assertThat(unavailabilityInstancesResponses.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetUnavailabilityInstancesForCompositeSLO() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO1 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2DTO1.setName("new two");
    serviceLevelObjectiveV2DTO1.setIdentifier("new_two");
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceIdentifier);
    serviceLevelObjectiveV2DTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO1);

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO2.getSpec();
    WindowBasedServiceLevelIndicatorSpec serviceLevelIndicatorSpec =
        (WindowBasedServiceLevelIndicatorSpec) simpleServiceLevelObjectiveSpec.getServiceLevelIndicators()
            .get(0)
            .getSpec();
    serviceLevelIndicatorSpec.setSliMissingDataType(SLIMissingDataType.BAD);
    simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).setSpec(serviceLevelIndicatorSpec);
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjectiveV2DTO2.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2DTO2.setName("new three");
    serviceLevelObjectiveV2DTO2.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO2);

    ServiceLevelObjectiveV2DTO compositeSLO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef("new_two")
                                            .weightagePercentage(75.0)
                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("new_three")
                                  .weightagePercentage(25.0)
                                  .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                  .accountId(builderFactory.getContext().getAccountId())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);

    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    long endTime = startTime + Duration.ofDays(365).toSeconds();

    DowntimeDTO downtimeDTO = builderFactory.getRecurringDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().entityRef(monitoredServiceIdentifier).enabled(true).build()))
            .build());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    downtimeDTO = builderFactory.getOnetimeDurationBasedDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().entityRef(monitoredServiceIdentifier).enabled(true).build()))
            .build());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    List<UnavailabilityInstancesResponse> unavailabilityInstancesResponses =
        sloDashboardService.getUnavailabilityInstances(
            builderFactory.getProjectParams(), startTime * 1000, endTime * 1000, compositeSLO.getIdentifier());
    assertThat(unavailabilityInstancesResponses.size()).isEqualTo(54);
    assertThat(unavailabilityInstancesResponses.get(0).getEntityType()).isEqualTo(EntityType.MAINTENANCE_WINDOW);

    unavailabilityInstancesResponses = sloDashboardService.getUnavailabilityInstances(builderFactory.getProjectParams(),
        startTime * 1000, startTime * 1000 + Duration.ofDays(6).toMillis(), compositeSLO.getIdentifier());
    assertThat(unavailabilityInstancesResponses.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSecondaryEventsForSimpleSLO_Success() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceIdentifier);
    spec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    long endTime = startTime + Duration.ofDays(365).toSeconds();

    DowntimeDTO downtimeDTO = builderFactory.getRecurringDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().entityRef(monitoredServiceIdentifier).enabled(true).build()))
            .build());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);

    List<Annotation> annotations =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());

    List<SecondaryEventsResponse> secondaryEvents = sloDashboardService.getSecondaryEvents(
        builderFactory.getProjectParams(), startTime * 1000, endTime * 1000, serviceLevelObjective.getIdentifier());
    assertThat(secondaryEvents.size()).isEqualTo(54);
    assertThat(secondaryEvents.get(0).getType()).isEqualTo(SecondaryEventsType.DOWNTIME);
    assertThat(secondaryEvents.get(0).getStartTime()).isEqualTo(startTime);

    assertThat(secondaryEvents.get(1).getType()).isEqualTo(SecondaryEventsType.ANNOTATION);
    assertThat(secondaryEvents.get(1).getIdentifiers().get(0)).isEqualTo(annotations.get(0).getUuid());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSecondaryEventsForCompositeSLO_Success() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO1 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2DTO1.setName("new two");
    serviceLevelObjectiveV2DTO1.setIdentifier("new_two");
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceIdentifier);
    serviceLevelObjectiveV2DTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO1);

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO2.getSpec();
    WindowBasedServiceLevelIndicatorSpec serviceLevelIndicatorSpec =
        (WindowBasedServiceLevelIndicatorSpec) simpleServiceLevelObjectiveSpec.getServiceLevelIndicators()
            .get(0)
            .getSpec();
    serviceLevelIndicatorSpec.setSliMissingDataType(SLIMissingDataType.BAD);
    simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).setSpec(serviceLevelIndicatorSpec);
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjectiveV2DTO2.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2DTO2.setName("new three");
    serviceLevelObjectiveV2DTO2.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO2);

    ServiceLevelObjectiveV2DTO compositeSLO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef("new_two")
                                            .weightagePercentage(75.0)
                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef("new_three")
                                  .weightagePercentage(25.0)
                                  .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                  .accountId(builderFactory.getContext().getAccountId())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);

    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    long endTime = startTime + Duration.ofDays(365).toSeconds();

    DowntimeDTO downtimeDTO = builderFactory.getRecurringDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().entityRef(monitoredServiceIdentifier).enabled(true).build()))
            .build());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    downtimeDTO = builderFactory.getOnetimeDurationBasedDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().entityRef(monitoredServiceIdentifier).enabled(true).build()))
            .build());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationDTO.setSloIdentifier(compositeSLO.getIdentifier());
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);

    List<Annotation> annotations =
        annotationService.get(builderFactory.getProjectParams(), compositeSLO.getIdentifier());

    List<SecondaryEventsResponse> secondaryEvents = sloDashboardService.getSecondaryEvents(
        builderFactory.getProjectParams(), startTime * 1000, endTime * 1000, compositeSLO.getIdentifier());
    assertThat(secondaryEvents.size()).isEqualTo(55);
    assertThat(secondaryEvents.get(0).getType()).isEqualTo(SecondaryEventsType.DOWNTIME);
    assertThat(secondaryEvents.get(0).getStartTime()).isEqualTo(startTime);

    assertThat(secondaryEvents.get(2).getType()).isEqualTo(SecondaryEventsType.ANNOTATION);
    assertThat(secondaryEvents.get(2).getIdentifiers().get(0)).isEqualTo(annotations.get(0).getUuid());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSecondaryEventDetails_Success() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceIdentifier);
    spec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    long startTime =
        CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond() + Duration.ofMinutes(1).toSeconds();
    long endTime = startTime + Duration.ofMinutes(30).toSeconds();

    DowntimeDTO downtimeDTO = builderFactory.getOnetimeDurationBasedDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().entityRef(monitoredServiceIdentifier).enabled(true).build()))
            .build());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    List<EntityUnavailabilityStatuses> instances = entityUnavailabilityStatusesService.getAllUnavailabilityInstances(
        builderFactory.getProjectParams(), startTime, endTime);

    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);
    annotationDTO.setMessage("new one");
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);

    List<Annotation> annotations =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    List<String> annotationIds = annotations.stream().map(Annotation::getUuid).collect(Collectors.toList());

    SecondaryEventDetailsResponse response = sloDashboardService.getSecondaryEventDetails(
        SecondaryEventsType.DOWNTIME, Collections.singletonList(instances.get(0).getUuid()));

    assertThat(response.getType()).isEqualTo(SecondaryEventsType.DOWNTIME);
    assertThat(response.getStartTime()).isEqualTo(CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond());

    response = sloDashboardService.getSecondaryEventDetails(SecondaryEventsType.ANNOTATION, annotationIds);

    assertThat(response.getStartTime()).isEqualTo(startTime);
    assertThat(response.getEndTime()).isEqualTo(endTime);
    assertThat(response.getType()).isEqualTo(SecondaryEventsType.ANNOTATION);

    AnnotationInstanceDetails instanceDetails = (AnnotationInstanceDetails) response.getDetails();
    assertThat(instanceDetails.getAnnotations().size()).isEqualTo(annotationIds.size());
    assertThat(instanceDetails.getAnnotations().get(0).getUuid()).isEqualTo(annotations.get(0).getUuid());
    assertThat(instanceDetails.getAnnotations().get(0).getMessage()).isEqualTo(annotations.get(0).getMessage());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSecondaryEventDetails_WithDifferentThreadMessageError() {
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjective =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceIdentifier);
    spec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    long endTime = startTime + Duration.ofMinutes(30).toSeconds();

    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);
    annotationDTO.setStartTime(startTime + Duration.ofMinutes(5).toSeconds());
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);

    List<Annotation> annotations =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    List<String> annotationIds = annotations.stream().map(Annotation::getUuid).collect(Collectors.toList());

    assertThatThrownBy(
        () -> sloDashboardService.getSecondaryEventDetails(SecondaryEventsType.ANNOTATION, annotationIds))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("All the messages should be of the same thread");
  }

  private void createData(Instant startTime, List<SLIRecord.SLIState> sliStates, String sliId) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, sliId, 0);
  }

  private List<SLIRecord> createSLIRecords(String sliId, List<SLIRecord.SLIState> states) {
    int index = 0;
    List<SLIRecord> sliRecords = new ArrayList<>();
    int runningBadCount = 0, runningGoodCount = 0;
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      if (states.get(index) == BAD) {
        runningBadCount++;
      }
      if (states.get(index) == GOOD) {
        runningGoodCount++;
      }
      SLIRecord sliRecord = SLIRecord.builder()
                                .verificationTaskId(verificationTaskId)
                                .sliId(sliId)
                                .version(0)
                                .sliState(states.get(index))
                                .runningBadCount(runningBadCount)
                                .runningGoodCount(runningGoodCount)
                                .sliVersion(0)
                                .timestamp(instant)
                                .build();
      sliRecords.add(sliRecord);
      index++;
    }
    return sliRecords;
  }

  private List<CompositeSLORecord> createSLORecords(
      Instant start, Instant end, List<Double> runningGoodCount, List<Double> runningBadCount) {
    int index = 0;
    List<CompositeSLORecord> sloRecords = new ArrayList<>();
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                         .verificationTaskId(verificationTaskId)
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

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIRecord.SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIRecord.SLIState sliState = sliStates.get(i);
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

  private void assertCompositeSLOGraphData(Instant startTime, List<Point> sloPerformanceTrend,
      List<Point> errorBudgetBurndown, List<Double> runningGoodCount, List<Double> runningBadCount,
      int totalErrorBudgetMinutes) {
    for (int i = 0; i < sloPerformanceTrend.size(); i++) {
      assertThat(sloPerformanceTrend.get(i).getTimestamp())
          .isEqualTo(startTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(sloPerformanceTrend.get(i).getValue())
          .isCloseTo((runningGoodCount.get(i) * 100.0) / (i + 1), offset(0.01));
      assertThat(errorBudgetBurndown.get(i).getTimestamp())
          .isEqualTo(startTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(errorBudgetBurndown.get(i).getValue())
          .isCloseTo(
              ((totalErrorBudgetMinutes - runningBadCount.get(i)) * 100.0) / totalErrorBudgetMinutes, offset(0.01));
    }
  }
}
