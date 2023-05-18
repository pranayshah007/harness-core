/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.cvng.downtime.utils.DateTimeUtils.dtf;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.NO_DATA;
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
import io.harness.cvng.downtime.beans.AllEntitiesRule;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeStatus;
import io.harness.cvng.downtime.beans.EntityDetails;
import io.harness.cvng.downtime.beans.EntityIdentifiersRule;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.AnnotationInstanceDetails;
import io.harness.cvng.servicelevelobjective.beans.CompositeSLOFormulaType;
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
import io.harness.cvng.servicelevelobjective.beans.SLOError;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetInstanceDetails;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetFilterDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventDetailsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType;
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
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset.SLOErrorBudgetResetKeys;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.AnnotationService;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLODashboardServiceImplTest extends CvNextGenTestBase {
  @Inject private SLODashboardService sloDashboardService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
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

  private ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO;
  private AbstractServiceLevelObjective serviceLevelObjective;
  private MonitoredServiceDTO monitoredServiceDTO;
  private SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec;

  private ServiceLevelObjectiveV2DTO serviceLevelObjectiveRequestBasedV2DTO;

  private AbstractServiceLevelObjective serviceLevelObjectiveRequestBased;

  private SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpecRequestBased;
  private HealthSource healthSource;
  private SLOTargetDTO calendarSloTarget;

  private final String monitoredServiceIdentifier = "monitoredServiceIdentifier";
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

    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    serviceLevelObjectiveV2DTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjectiveV2DTO.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    serviceLevelObjective = serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    serviceLevelObjectiveRequestBasedV2DTO = builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpecRequestBased =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveRequestBasedV2DTO.getSpec();
    simpleServiceLevelObjectiveSpecRequestBased.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpecRequestBased.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjectiveRequestBasedV2DTO.setSpec(simpleServiceLevelObjectiveSpecRequestBased);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveRequestBasedV2DTO);
    serviceLevelObjectiveRequestBased = serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveRequestBasedV2DTO.getIdentifier());

    calendarSloTarget = SLOTargetDTO.builder()
                            .type(SLOTargetType.CALENDER)
                            .sloTargetPercentage(80.0)
                            .spec(CalenderSLOTargetSpec.builder()
                                      .type(SLOCalenderType.QUARTERLY)
                                      .spec(CalenderSLOTargetSpec.QuarterlyCalenderSpec.builder().build())
                                      .build())
                            .build();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_withNoData() {
    SLODashboardWidget sloDashboardWidget = sloDashboardService
                                                .getSloDashboardDetail(builderFactory.getProjectParams(),
                                                    serviceLevelObjectiveV2DTO.getIdentifier(), null, null)
                                                .getSloDashboardWidget();

    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().size()).isEqualTo(1);
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().get(0).getMonitoredServiceIdentifier())
        .isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.WINDOW);
    assertThat(sloDashboardWidget.isTotalErrorBudgetApplicable()).isEqualTo(true);
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjectiveV2DTO.getTags());
    assertThat(sloDashboardWidget.getType()).isEqualTo(simpleServiceLevelObjectiveSpec.getServiceLevelIndicatorType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(serviceLevelObjectiveV2DTO.getSloTarget().getType());
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
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.WINDOW);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_withSLOQuarter() {
    serviceLevelObjectiveV2DTO.setIdentifier("newSloIdentifier");
    serviceLevelObjectiveV2DTO.setSloTarget(calendarSloTarget);
    simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).setIdentifier("sli_identifier");
    serviceLevelObjectiveV2DTO.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);

    SLODashboardWidget sloDashboardWidget = sloDashboardService
                                                .getSloDashboardDetail(builderFactory.getProjectParams(),
                                                    serviceLevelObjectiveV2DTO.getIdentifier(), null, null)
                                                .getSloDashboardWidget();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(66);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  @Ignore("resetErrorBudget function is not present")
  public void testGetSloDashboardDetail_withSLOErrorBudgetReset() {
    sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
        builderFactory.getSLOErrorBudgetResetDTOBuilder()
            .serviceLevelObjectiveIdentifier(serviceLevelObjectiveV2DTO.getIdentifier())
            .errorBudgetIncrementMinutes(100)
            .build());
    sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(),
        builderFactory.getSLOErrorBudgetResetDTOBuilder()
            .serviceLevelObjectiveIdentifier(serviceLevelObjectiveV2DTO.getIdentifier())
            .errorBudgetIncrementMinutes(50)
            .build());
    SLODashboardWidget sloDashboardWidget = sloDashboardService
                                                .getSloDashboardDetail(builderFactory.getProjectParams(),
                                                    serviceLevelObjectiveV2DTO.getIdentifier(), null, null)
                                                .getSloDashboardWidget();

    assertThat(sloDashboardWidget.getErrorBudgetRemaining())
        .isEqualTo(8790); // 30 days - 30*24*60 - 20% -> 8640 -> 8640 + 100 -> 8740  -> 8740 + 50-> 8790
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(100, offset(0.0001));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_SimpleSLO_withSLIDatas() {
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(10)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        serviceLevelIndicator.getUuid());
    SLODashboardWidget sloDashboardWidget = sloDashboardService
                                                .getSloDashboardDetail(builderFactory.getProjectParams(),
                                                    serviceLevelObjectiveV2DTO.getIdentifier(), null, null)
                                                .getSloDashboardWidget();

    assertSLIGraphData(clock.instant().minus(Duration.ofMinutes(10)), sloDashboardWidget.getSloPerformanceTrend(),
        sloDashboardWidget.getErrorBudgetBurndown(), Lists.newArrayList(100.0, 50.0, 33.33, 50.0),
        Lists.newArrayList(100.0, 99.9884, 99.9768, 99.9768));
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().size()).isEqualTo(1);
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().get(0).getMonitoredServiceIdentifier())
        .isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.WINDOW);
    assertThat(sloDashboardWidget.isTotalErrorBudgetApplicable()).isEqualTo(true);
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjectiveV2DTO.getTags());
    assertThat(sloDashboardWidget.getType()).isEqualTo(simpleServiceLevelObjectiveSpec.getServiceLevelIndicatorType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(serviceLevelObjectiveV2DTO.getSloTarget().getType());
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
    assertThat(sloDashboardWidget.getTotalErrorBudget()).isEqualTo(8640);
    assertThat(sloDashboardWidget.getServiceName()).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.WINDOW);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_SimpleRequestSLO_withSLIDatas() {
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            simpleServiceLevelObjectiveSpecRequestBased.getServiceLevelIndicators().get(0).getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(10)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        Arrays.asList(100L, 95L, 80L, 100L), Arrays.asList(0L, 5L, 20L, 100L), serviceLevelIndicator.getUuid());
    SLODashboardWidget sloDashboardWidget = sloDashboardService
                                                .getSloDashboardDetail(builderFactory.getProjectParams(),
                                                    serviceLevelObjectiveRequestBasedV2DTO.getIdentifier(), null, null)
                                                .getSloDashboardWidget();

    assertSLIGraphData(clock.instant().minus(Duration.ofMinutes(10)), sloDashboardWidget.getSloPerformanceTrend(),
        sloDashboardWidget.getErrorBudgetBurndown(), Lists.newArrayList(100.0, 97.5, 91.66, 75.0),
        Lists.newArrayList(100.0, 87.5, 58.33, -25.0));
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjectiveRequestBased.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().size()).isEqualTo(1);
    assertThat(sloDashboardWidget.getMonitoredServiceDetails().get(0).getMonitoredServiceIdentifier())
        .isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.REQUEST);
    assertThat(sloDashboardWidget.isTotalErrorBudgetApplicable()).isEqualTo(true);
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjectiveRequestBasedV2DTO.getTags());
    assertThat(sloDashboardWidget.getSloTargetType())
        .isEqualTo(serviceLevelObjectiveRequestBased.getTarget().getType());
    assertThat(sloDashboardWidget.getCurrentPeriodLengthDays()).isEqualTo(30);
    assertThat(sloDashboardWidget.getCurrentPeriodStartTime())
        .isEqualTo(Instant.parse("2020-06-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getCurrentPeriodEndTime())
        .isEqualTo(Instant.parse("2020-07-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getErrorBudgetRemaining()).isEqualTo(-25);
    assertThat(sloDashboardWidget.getSloTargetPercentage()).isCloseTo(80, offset(.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(-25, offset(0.001));
    assertThat(sloDashboardWidget.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.EXHAUSTED);
    assertThat(sloDashboardWidget.isRecalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(0);
    assertThat(sloDashboardWidget.getServiceIdentifier()).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(sloDashboardWidget.getEnvironmentIdentifier()).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(sloDashboardWidget.getTotalErrorBudget()).isEqualTo(100);
    assertThat(sloDashboardWidget.getServiceName()).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.REQUEST);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_CompositeSLO_withSLIDatas() {
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
                                            .serviceLevelObjectiveRef(serviceLevelObjective.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(serviceLevelObjective.getAccountId())
                                            .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                            .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
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
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.WINDOW);
    assertThat(sloDashboardWidget.isTotalErrorBudgetApplicable()).isEqualTo(true);
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(compositeServiceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(compositeServiceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getCurrentPeriodLengthDays()).isEqualTo(30);
    assertThat(sloDashboardWidget.getCurrentPeriodStartTime())
        .isEqualTo(Instant.parse("2020-06-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getCurrentPeriodEndTime())
        .isEqualTo(Instant.parse("2020-07-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getErrorBudgetRemaining()).isEqualTo(8639); // 8640 - (1.25 bad mins)
    assertThat(sloDashboardWidget.getSloTargetPercentage()).isCloseTo(80, offset(.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(99.9884, offset(0.001));
    assertThat(sloDashboardWidget.getTotalErrorBudget()).isEqualTo(8640);
    assertThat(sloDashboardWidget.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(sloDashboardWidget.isRecalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.isCalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(0);
    assertCompositeSLOGraphData(clock.instant().minus(Duration.ofMinutes(10)),
        sloDashboardWidget.getSloPerformanceTrend(), sloDashboardWidget.getErrorBudgetBurndown(), runningGoodCount,
        runningBadCount, 8640);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_RequestCompositeSLO_withSLIDatas() {
    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
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
                      .evaluationType(SLIEvaluationType.REQUEST)
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(serviceLevelObjectiveRequestBased.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(serviceLevelObjectiveRequestBased.getAccountId())
                                            .orgIdentifier(serviceLevelObjectiveRequestBased.getOrgIdentifier())
                                            .projectIdentifier(serviceLevelObjectiveRequestBased.getProjectIdentifier())
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

    String sliId1 =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjective) serviceLevelObjectiveRequestBased).getServiceLevelIndicators().get(0))
            .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    List<SLIState> sliStateList1 = Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100L, 200l, 0L);
    List<Long> badCounts1 = Arrays.asList(10L, 20L, 0L);
    List<SLIState> sliStateList2 = Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts2 = Arrays.asList(100L, 200L, 300L);
    List<Long> badCounts2 = Arrays.asList(0L, 0L, 10L);

    List<SLIRecord> sliRecordList1 =
        createSLIRecords(startTime, endTime.minusSeconds(120), sliId1, sliStateList1, goodCounts1, badCounts1);
    List<SLIRecord> sliRecordList2 =
        createSLIRecords(startTime, endTime.minusSeconds(120), sliId2, sliStateList2, goodCounts2, badCounts2);

    List<List<SLIRecord>> objectiveDetailToSLIRecordList = new ArrayList<>();
    objectiveDetailToSLIRecordList.add(sliRecordList1);
    objectiveDetailToSLIRecordList.add(sliRecordList2);

    createSLORecords(startTime, endTime.minusSeconds(120), objectiveDetailToSLIRecordList);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(0);
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
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.REQUEST);
    assertThat(sloDashboardWidget.isTotalErrorBudgetApplicable()).isEqualTo(false);
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(compositeServiceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(compositeServiceLevelObjective.getTarget().getType());
    assertThat(sloDashboardWidget.getCurrentPeriodLengthDays()).isEqualTo(30);
    assertThat(sloDashboardWidget.getCurrentPeriodStartTime())
        .isEqualTo(Instant.parse("2020-06-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getCurrentPeriodEndTime())
        .isEqualTo(Instant.parse("2020-07-27T10:50:00Z").toEpochMilli());
    assertThat(sloDashboardWidget.getErrorBudgetRemaining()).isEqualTo(0);
    assertThat(sloDashboardWidget.getSloTargetPercentage()).isCloseTo(80, offset(.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(63.4581, offset(0.001));
    assertThat(sloDashboardWidget.getTotalErrorBudget()).isEqualTo(0);
    assertThat(sloDashboardWidget.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.OBSERVE);
    assertThat(sloDashboardWidget.isRecalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.isCalculatingSLI()).isFalse();
    assertThat(sloDashboardWidget.getTimeRemainingDays()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloDashboardDetail_CompositeSLO_withMonitoredServiceDetails() {
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
                                            .serviceLevelObjectiveRef(serviceLevelObjective.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(serviceLevelObjective.getAccountId())
                                            .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                            .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
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
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.WINDOW);
    assertThat(sloDashboardWidget.isTotalErrorBudgetApplicable()).isEqualTo(true);
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
  public void testGetSloHealthListView_withNoData() {
    DowntimeDTO downtimeDTO = builderFactory.getOnetimeDurationBasedDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().enabled(true).entityRef(monitoredServiceIdentifier).build()))
            .build());
    OnetimeDowntimeSpec onetimeDowntimeSpec = (OnetimeDowntimeSpec) downtimeDTO.getSpec().getSpec();
    onetimeDowntimeSpec.setStartDateTime(dtf.format(LocalDateTime.now(clock).minusMinutes(5)));
    downtimeDTO.getSpec().setSpec(onetimeDowntimeSpec);
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(2);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(1);
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(sloDashboardWidget.getHealthSourceName()).isEqualTo(healthSource.getName());
    assertThat(sloDashboardWidget.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(sloDashboardWidget.getMonitoredServiceName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.getTags()).isEqualTo(serviceLevelObjectiveV2DTO.getTags());
    assertThat(sloDashboardWidget.getSloTargetType()).isEqualTo(serviceLevelObjectiveV2DTO.getSloTarget().getType());
    assertThat(sloDashboardWidget.getErrorBudgetRemaining()).isEqualTo(8640); // 30 days - 30*24*60 - 20% -> 8640
    assertThat(sloDashboardWidget.getSloTargetPercentage()).isCloseTo(80, offset(.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRemainingPercentage()).isCloseTo(100, offset(0.0001));
    assertThat(sloDashboardWidget.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(sloDashboardWidget.getServiceIdentifier()).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(sloDashboardWidget.getEnvironmentIdentifier()).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(sloDashboardWidget.getDowntimeStatusDetails().getStatus()).isEqualTo(DowntimeStatus.ACTIVE);
    assertThat(sloDashboardWidget.getDowntimeStatusDetails().getEndTime())
        .isEqualTo(clock.instant().plus(Duration.ofMinutes(25)).getEpochSecond());
    assertThat(sloDashboardWidget.getNoOfActiveAlerts())
        .isEqualTo(serviceLevelObjectiveV2DTO.getNotificationRuleRefs().size());
    assertThat(sloDashboardWidget.getServiceName()).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(sloDashboardWidget.getSloError().isFailedState()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_ForSimpleSLOWIthFailedState() {
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOEntity(serviceLevelObjectiveRequestBased);
    sloHealthIndicator.setFailedState(true);
    hPersistence.save(sloHealthIndicator);

    PageResponse<SLOHealthListView> pageResponse = sloDashboardService.getSloHealthListView(
        builderFactory.getProjectParams(), SLODashboardApiFilter.builder().searchFilter("request").build(),
        PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getSloError())
        .isEqualTo(SLOError.getErrorForDataCollectionFailureInSimpleSLOInListView());

    SLODashboardDetail sloDashboardDetail = sloDashboardService.getSloDashboardDetail(builderFactory.getProjectParams(),
        serviceLevelObjectiveRequestBased.getIdentifier(), clock.instant().toEpochMilli(),
        clock.instant().toEpochMilli());
    assertThat(sloDashboardDetail.getSloDashboardWidget().getSloError())
        .isEqualTo(SLOError.getErrorForDataCollectionFailureInSimpleSLOWidgetDetailsView());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetListViewConfigurationAndConsumption_ForAssociatedCompositeSLOWIthFailedState() {
    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
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
                      .evaluationType(SLIEvaluationType.REQUEST)
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(serviceLevelObjectiveRequestBased.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(serviceLevelObjectiveRequestBased.getAccountId())
                                            .orgIdentifier(serviceLevelObjectiveRequestBased.getOrgIdentifier())
                                            .projectIdentifier(serviceLevelObjectiveRequestBased.getProjectIdentifier())
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
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOEntity(serviceLevelObjectiveRequestBased);
    sloHealthIndicator.setFailedState(true);
    hPersistence.save(sloHealthIndicator);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().type(ServiceLevelObjectiveType.COMPOSITE).build(),
            PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getSloError())
        .isEqualTo(SLOError.getErrorForDataCollectionFailureInCompositeSLOInListView());

    pageResponse = sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
        SLODashboardApiFilter.builder().compositeSLOIdentifier(serviceLevelObjectiveV2DTO.getIdentifier()).build(),
        PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(2);
    sloDashboardWidget = sloDashboardWidgets.get(1);
    assertThat(sloDashboardWidget.getSloError()).isEqualTo(SLOError.getNoError());
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(simpleServiceLevelObjectiveDTO2.getIdentifier());
    assertThat(sloDashboardWidget.getProjectParams()).isEqualTo(builderFactory.getProjectParams());
    sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getSloError())
        .isEqualTo(SLOError.getErrorForDataCollectionFailureInSimpleSLOInListView());
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjectiveRequestBased.getIdentifier());
    assertThat(sloDashboardWidget.getProjectParams()).isEqualTo(builderFactory.getProjectParams());

    PageResponse<SLOConsumptionBreakdown> sloConsumptionBreakdownView =
        sloDashboardService.getSLOConsumptionBreakdownView(builderFactory.getProjectParams(),
            serviceLevelObjectiveV2DTO.getIdentifier(), startTime.toEpochMilli(), endTime.toEpochMilli());
    assertThat(sloConsumptionBreakdownView.getPageItemCount()).isEqualTo(2);
    assertThat(sloConsumptionBreakdownView.getTotalItems()).isEqualTo(2);
    List<SLOConsumptionBreakdown> sloConsumptionBreakdowns = sloConsumptionBreakdownView.getContent();
    assertThat(sloConsumptionBreakdowns).hasSize(2);
    SLOConsumptionBreakdown sloConsumptionBreakdown = sloConsumptionBreakdowns.get(1);
    assertThat(sloConsumptionBreakdown.getSloError()).isEqualTo(SLOError.getNoError());
    assertThat(sloConsumptionBreakdown.getSloIdentifier()).isEqualTo(simpleServiceLevelObjectiveDTO2.getIdentifier());
    assertThat(sloConsumptionBreakdown.getProjectParams()).isEqualTo(builderFactory.getProjectParams());
    sloConsumptionBreakdown = sloConsumptionBreakdowns.get(0);
    assertThat(sloConsumptionBreakdown.getSloError())
        .isEqualTo(SLOError.getErrorForDataCollectionFailureInSimpleSLOInListView());
    assertThat(sloConsumptionBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveRequestBased.getIdentifier());
    assertThat(sloConsumptionBreakdown.getProjectParams()).isEqualTo(builderFactory.getProjectParams());

    SLODashboardDetail sloDashboardDetail = sloDashboardService.getSloDashboardDetail(builderFactory.getProjectParams(),
        serviceLevelObjectiveV2DTO.getIdentifier(), clock.instant().toEpochMilli(), clock.instant().toEpochMilli());
    assertThat(sloDashboardDetail.getSloDashboardWidget().getSloError())
        .isEqualTo(SLOError.getErrorForDataCollectionFailureInCompositeSLOWidgetDetailsView());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetListViewConfigurationAndConsumption_ForDeletionOfSimpleSLOInACompositeSLO() {
    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
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
                      .evaluationType(SLIEvaluationType.REQUEST)
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(serviceLevelObjectiveRequestBased.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(serviceLevelObjectiveRequestBased.getAccountId())
                                            .orgIdentifier(serviceLevelObjectiveRequestBased.getOrgIdentifier())
                                            .projectIdentifier(serviceLevelObjectiveRequestBased.getProjectIdentifier())
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
    String sliId1 =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(),
                ((SimpleServiceLevelObjective) serviceLevelObjectiveRequestBased).getServiceLevelIndicators().get(0))
            .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    List<SLIState> sliStateList1 = Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100L, 200l, 0L);
    List<Long> badCounts1 = Arrays.asList(10L, 20L, 0L);
    List<SLIState> sliStateList2 = Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts2 = Arrays.asList(100L, 200L, 300L);
    List<Long> badCounts2 = Arrays.asList(0L, 0L, 10L);

    List<SLIRecord> sliRecordList1 =
        createSLIRecords(startTime, endTime.minusSeconds(120), sliId1, sliStateList1, goodCounts1, badCounts1);
    List<SLIRecord> sliRecordList2 =
        createSLIRecords(startTime, endTime.minusSeconds(120), sliId2, sliStateList2, goodCounts2, badCounts2);
    List<List<SLIRecord>> objectiveDetailToSLIRecordList = new ArrayList<>();
    objectiveDetailToSLIRecordList.add(sliRecordList1);
    objectiveDetailToSLIRecordList.add(sliRecordList2);
    createSLORecords(startTime, endTime.minusSeconds(120), objectiveDetailToSLIRecordList);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOEntity(simpleServiceLevelObjective2);
    hPersistence.delete(simpleServiceLevelObjective2);
    hPersistence.delete(sloHealthIndicator);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().type(ServiceLevelObjectiveType.COMPOSITE).build(),
            PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(pageResponse.getTotalItems()).isEqualTo(1);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(1);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getSloError()).isEqualTo(SLOError.getErrorForDeletionOfSimpleSLOInListView());

    pageResponse = sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
        SLODashboardApiFilter.builder().compositeSLOIdentifier(serviceLevelObjectiveV2DTO.getIdentifier()).build(),
        PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(2);
    sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getSloError()).isEqualTo(SLOError.getNoError());
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(serviceLevelObjectiveRequestBased.getIdentifier());
    assertThat(sloDashboardWidget.getProjectParams()).isEqualTo(builderFactory.getProjectParams());
    sloDashboardWidget = sloDashboardWidgets.get(1);
    assertThat(sloDashboardWidget.getSloError())
        .isEqualTo(SLOError.getErrorForDeletionOfSimpleSLOInConfigurationListView());
    assertThat(sloDashboardWidget.getSloIdentifier()).isEqualTo(simpleServiceLevelObjective2.getIdentifier());
    assertThat(sloDashboardWidget.getProjectParams()).isEqualTo(builderFactory.getProjectParams());

    PageResponse<SLOConsumptionBreakdown> sloConsumptionBreakdownView =
        sloDashboardService.getSLOConsumptionBreakdownView(builderFactory.getProjectParams(),
            serviceLevelObjectiveV2DTO.getIdentifier(), startTime.toEpochMilli(), endTime.toEpochMilli());
    assertThat(sloConsumptionBreakdownView.getPageItemCount()).isEqualTo(2);
    assertThat(sloConsumptionBreakdownView.getTotalItems()).isEqualTo(2);
    List<SLOConsumptionBreakdown> sloConsumptionBreakdowns = sloConsumptionBreakdownView.getContent();
    assertThat(sloConsumptionBreakdowns).hasSize(2);
    SLOConsumptionBreakdown sloConsumptionBreakdown = sloConsumptionBreakdowns.get(0);
    assertThat(sloConsumptionBreakdown.getSloError()).isEqualTo(SLOError.getNoError());
    assertThat(sloConsumptionBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveRequestBased.getIdentifier());
    assertThat(sloConsumptionBreakdown.getProjectParams()).isEqualTo(builderFactory.getProjectParams());
    sloConsumptionBreakdown = sloConsumptionBreakdowns.get(1);
    assertThat(sloConsumptionBreakdown.getSloError())
        .isEqualTo(SLOError.getErrorForDeletionOfSimpleSLOInConsumptionView());
    assertThat(sloConsumptionBreakdown.getSloIdentifier()).isEqualTo(simpleServiceLevelObjective2.getIdentifier());
    assertThat(sloConsumptionBreakdown.getProjectParams()).isEqualTo(builderFactory.getProjectParams());

    SLODashboardDetail sloDashboardDetail = sloDashboardService.getSloDashboardDetail(builderFactory.getProjectParams(),
        serviceLevelObjectiveV2DTO.getIdentifier(), startTime.toEpochMilli(), endTime.toEpochMilli());
    assertThat(sloDashboardDetail.getSloDashboardWidget().getSloError())
        .isEqualTo(SLOError.getErrorForDeletionOfSimpleSLOInWidgetDetailsView());
  }
  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListViewSearchFunctionality() {
    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective2.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    serviceLevelObjective2.setSpec(spec);
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective3.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    serviceLevelObjective3.setSpec(spec);
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
    assertThat(sloDashboardWidget.getSloError().isFailedState()).isEqualTo(false);

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
    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective2.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    serviceLevelObjective2.setSpec(spec);
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
    assertThat(pageResponse.getPageItemCount()).isEqualTo(3);
    assertThat(pageResponse.getTotalItems()).isEqualTo(3);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(3);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getName()).isEqualTo(serviceLevelObjective3.getName());
    assertThat(sloDashboardWidget.getSloError().isFailedState()).isEqualTo(false);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_DowntimeStatusDetailsWithAllEntitiesRule() {
    DowntimeDTO downtimeDTO = builderFactory.getOnetimeDurationBasedDowntimeDTO();
    downtimeDTO.setEntitiesRule(AllEntitiesRule.builder().build());
    OnetimeDowntimeSpec onetimeDowntimeSpec = (OnetimeDowntimeSpec) downtimeDTO.getSpec().getSpec();
    onetimeDowntimeSpec.setStartDateTime(dtf.format(LocalDateTime.now(clock).minusMinutes(5)));
    downtimeDTO.getSpec().setSpec(onetimeDowntimeSpec);
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(pageResponse.getTotalItems()).isEqualTo(2);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(2);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getDowntimeStatusDetails().getStatus()).isEqualTo(DowntimeStatus.ACTIVE);
    assertThat(sloDashboardWidget.getDowntimeStatusDetails().getEndTime())
        .isEqualTo(clock.instant().plus(Duration.ofMinutes(25)).getEpochSecond());
    assertThat(sloDashboardWidget.getSloError().isFailedState()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_EvaluationTypeFilter_Request() {
    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective2.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    serviceLevelObjective2.setSpec(spec);
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
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective5.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective5.setSpec(simpleServiceLevelObjectiveSpec);
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
                                 .evaluationType(SLIEvaluationType.REQUEST)
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
    assertThat(pageResponse.getPageItemCount()).isEqualTo(4);
    assertThat(pageResponse.getTotalItems()).isEqualTo(4);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(4);
    SLOHealthListView sloDashboardWidget = sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.getName()).isEqualTo(compositeSLO.getName());
    assertThat(sloDashboardWidget.getEvaluationType()).isEqualTo(SLIEvaluationType.REQUEST);
    assertThat(sloDashboardWidget.getSloError().isFailedState()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_EvaluationTypeFilter_Window() {
    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective2.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    serviceLevelObjective2.setSpec(spec);
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
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective5.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier + '1');
    serviceLevelObjective5.setSpec(simpleServiceLevelObjectiveSpec);
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
                                 .evaluationType(SLIEvaluationType.REQUEST)
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
    assertThat(sloDashboardWidget.getSloError().isFailedState()).isEqualTo(false);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_withSLOQuarter() {
    serviceLevelObjectiveV2DTO.setIdentifier("newSLOIdentifier");
    serviceLevelObjectiveV2DTO.setSloTarget(calendarSloTarget);
    simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).setIdentifier("sli_identifier");
    serviceLevelObjectiveV2DTO.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(3);
    assertThat(pageResponse.getTotalItems()).isEqualTo(3);
    List<SLOHealthListView> sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(3);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSloHealthListView_AccountScoped() {
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

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective2.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    serviceLevelObjective2.setSpec(spec);
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().projectIdentifier("project3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjective3.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    serviceLevelObjective3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjective3.setName("new three");
    serviceLevelObjective3.setIdentifier("new_three");
    serviceLevelObjectiveV2Service.create(projectParam1, serviceLevelObjective3);

    ServiceLevelObjectiveV2DTO serviceLevelObjective4 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective4.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
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
    assertThat(sloDashboardWidget.getSloError().isFailedState()).isEqualTo(false);

    // SLO Health List view page to add simple slo's.
    pageResponse = sloDashboardService.getSloHealthListView(
        ProjectParams.builder().accountIdentifier(builderFactory.getProjectParams().getAccountIdentifier()).build(),
        SLODashboardApiFilter.builder().type(ServiceLevelObjectiveType.SIMPLE).childResource(true).build(),
        PageParams.builder().page(0).size(10).build());
    assertThat(pageResponse.getPageItemCount()).isEqualTo(5);
    assertThat(pageResponse.getTotalItems()).isEqualTo(5);
    sloDashboardWidgets = pageResponse.getContent();
    assertThat(sloDashboardWidgets).hasSize(5);

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
    serviceLevelObjectiveV2DTO.setIdentifier("newSLOIdentifier");
    serviceLevelObjectiveV2DTO.setSloTarget(calendarSloTarget);
    simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).setIdentifier("sli_identifier");
    serviceLevelObjectiveV2DTO.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);

    ServiceLevelObjectiveV2DTO serviceLevelObjective2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().sloTarget(calendarSloTarget).build();
    serviceLevelObjective2.setName("new two");
    serviceLevelObjective2.setIdentifier("new_two");
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective2.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    serviceLevelObjective2.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective2);

    ServiceLevelObjectiveV2DTO serviceLevelObjective3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjective3.setName("new three");
    serviceLevelObjective3.setIdentifier("new_three");
    spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective3.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    serviceLevelObjective3.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective3);

    PageResponse<SLOHealthListView> pageResponse =
        sloDashboardService.getSloHealthListView(builderFactory.getProjectParams(),
            SLODashboardApiFilter.builder()
                .type(ServiceLevelObjectiveType.SIMPLE)
                .sloTargetFilterDTO(SLOTargetFilterDTO.builder()
                                        .type(calendarSloTarget.getType())
                                        .spec(calendarSloTarget.getSpec())
                                        .build())
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
    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

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
                                            .serviceLevelObjectiveRef(serviceLevelObjectiveV2DTO.getIdentifier())
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
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec())
                .getServiceLevelIndicators()
                .get(0)
                .getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(12)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        serviceLevelIndicator1.getUuid());
    SLODashboardWidget.SLOGraphData sloGraphData1 =
        graphDataService.getGraphData(serviceLevelObjective, clock.instant().minus(Duration.ofDays(1)), clock.instant(),
            8640, TimeRangeParams.builder().startTime(startTime).endTime(endTime).build());

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
    assertThat(sloBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(sloBreakdown.getSloName()).isEqualTo(serviceLevelObjectiveV2DTO.getName());
    assertThat(sloBreakdown.getSliType())
        .isEqualTo(
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec()).getServiceLevelIndicatorType());
    assertThat(sloBreakdown.getSloTargetPercentage())
        .isEqualTo(serviceLevelObjectiveV2DTO.getSloTarget().getSloTargetPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(sloGraphData1.getErrorBudgetBurned());
    assertThat(sloBreakdown.getSliStatusPercentage()).isEqualTo(sloGraphData1.getSliStatusPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(1);
    assertThat(sloBreakdown.getSloError().isFailedState()).isEqualTo(false);

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
    assertThat(sloBreakdown.getSloError().isFailedState()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSLOConsumptionBreakdownViewForLeastPerformantSLO() {
    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

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
                      .sloFormulaType(CompositeSLOFormulaType.LEAST_PERFORMANCE)
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(serviceLevelObjectiveV2DTO.getIdentifier())
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
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec())
                .getServiceLevelIndicators()
                .get(0)
                .getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(12)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        serviceLevelIndicator1.getUuid());
    SLODashboardWidget.SLOGraphData sloGraphData1 =
        graphDataService.getGraphData(serviceLevelObjective, clock.instant().minus(Duration.ofDays(1)), clock.instant(),
            8640, TimeRangeParams.builder().startTime(startTime).endTime(endTime).build());

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
    assertThat(sloBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(sloBreakdown.getSloName()).isEqualTo(serviceLevelObjectiveV2DTO.getName());
    assertThat(sloBreakdown.getSliType())
        .isEqualTo(
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec()).getServiceLevelIndicatorType());
    assertThat(sloBreakdown.getSloTargetPercentage())
        .isEqualTo(serviceLevelObjectiveV2DTO.getSloTarget().getSloTargetPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(sloGraphData1.getErrorBudgetBurned());
    assertThat(sloBreakdown.getSliStatusPercentage()).isEqualTo(sloGraphData1.getSliStatusPercentage());
    assertThat(sloBreakdown.getErrorBudgetBurned()).isEqualTo(1);
    assertThat(sloBreakdown.getSloError().isFailedState()).isEqualTo(false);
    assertThat(sloBreakdown.getContributedErrorBudgetBurned()).isEqualTo(null);

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
    assertThat(sloBreakdown.getSloError().isFailedState()).isEqualTo(false);
    assertThat(sloBreakdown.getContributedErrorBudgetBurned()).isEqualTo(null);
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSLOConsumptionBreakdownView_ForAccountScoped() {
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
                                            .serviceLevelObjectiveRef(serviceLevelObjective.getIdentifier())
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
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec())
                .getServiceLevelIndicators()
                .get(0)
                .getIdentifier());
    createData(clock.instant().minus(Duration.ofMinutes(10)), Arrays.asList(GOOD, BAD, BAD, GOOD),
        serviceLevelIndicator1.getUuid());
    SLODashboardWidget.SLOGraphData sloGraphData1 =
        graphDataService.getGraphData(serviceLevelObjective, clock.instant().minus(Duration.ofDays(1)), clock.instant(),
            8640, TimeRangeParams.builder().startTime(startTime).endTime(endTime).build());

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
    assertThat(sloBreakdown.getSloIdentifier()).isEqualTo(serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(sloBreakdown.getSloName()).isEqualTo(serviceLevelObjectiveV2DTO.getName());
    assertThat(sloBreakdown.getSliType())
        .isEqualTo(
            ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec()).getServiceLevelIndicatorType());
    assertThat(sloBreakdown.getSloTargetPercentage())
        .isEqualTo(serviceLevelObjectiveV2DTO.getSloTarget().getSloTargetPercentage());
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
    SLODashboardDetail sloDashboardDetail = sloDashboardService.getSloDashboardDetail(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier(), null, null);
    assertThat(sloDashboardDetail.getDescription()).isEqualTo("slo description");
    assertThat(sloDashboardDetail.getSloDashboardWidget().getSloIdentifier())
        .isEqualTo(serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(sloDashboardDetail.getSloDashboardWidget().getSloError()).isEqualTo(SLOError.getNoError());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSecondaryEventsForSimpleSLO_Success() {
    serviceLevelObjectiveV2DTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                     .identifier("newSLOIdentifier")
                                     .sloTarget(calendarSloTarget)
                                     .build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjectiveV2DTO.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).getIdentifier());

    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();

    DowntimeDTO downtimeDTO = builderFactory.getRecurringDowntimeDTO();
    downtimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(Collections.singletonList(
                EntityDetails.builder().entityRef(monitoredServiceIdentifier).enabled(true).build()))
            .build());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);

    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationDTO.setSloIdentifier(serviceLevelObjectiveV2DTO.getIdentifier());
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);

    SLOErrorBudgetResetDTO sloErrorBudgetResetDTO = builderFactory.getSLOErrorBudgetResetDTOBuilder().build();
    sloErrorBudgetResetDTO.setServiceLevelObjectiveIdentifier(serviceLevelObjectiveV2DTO.getIdentifier());
    sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(), sloErrorBudgetResetDTO);
    SLOErrorBudgetReset sloErrorBudgetReset = ((SLOErrorBudgetResetServiceImpl) sloErrorBudgetResetService)
                                                  .getSLOErrorBudgetResetEntities(builderFactory.getProjectParams(),
                                                      serviceLevelObjectiveV2DTO.getIdentifier())
                                                  .get(0);
    UpdateOperations<SLOErrorBudgetReset> updateOperations =
        hPersistence.createUpdateOperations(SLOErrorBudgetReset.class);
    updateOperations.set(SLOErrorBudgetResetKeys.createdAt, (startTime + Duration.ofMinutes(2).toSeconds()) * 1000);
    hPersistence.update(sloErrorBudgetReset, updateOperations);

    List<Annotation> annotations =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    entityUnavailabilityStatusesService.create(builderFactory.getProjectParams(),
        Collections.singletonList(EntityUnavailabilityStatusesDTO.builder()
                                      .orgIdentifier(builderFactory.getProjectParams().getOrgIdentifier())
                                      .projectIdentifier(builderFactory.getProjectParams().getProjectIdentifier())
                                      .entityType(EntityType.SLO)
                                      .entityId(serviceLevelIndicator.getUuid())
                                      .status(EntityUnavailabilityStatus.DATA_COLLECTION_FAILED)
                                      .startTime(startTime + Duration.ofMinutes(3).toSeconds())
                                      .endTime(startTime + Duration.ofMinutes(8).toSeconds())
                                      .build()));

    List<SecondaryEventsResponse> secondaryEvents =
        sloDashboardService.getSecondaryEvents(builderFactory.getProjectParams(), startTime * 1000,
            (startTime + Duration.ofMinutes(10).toSeconds()) * 1000, serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(secondaryEvents.size()).isEqualTo(4);
    assertThat(secondaryEvents.get(0).getType()).isEqualTo(SecondaryEventsType.DOWNTIME);
    assertThat(secondaryEvents.get(0).getStartTime()).isEqualTo(startTime);

    assertThat(secondaryEvents.get(1).getType()).isEqualTo(SecondaryEventsType.ANNOTATION);
    assertThat(secondaryEvents.get(1).getIdentifiers().get(0)).isEqualTo(annotations.get(0).getUuid());

    assertThat(secondaryEvents.get(2).getType()).isEqualTo(SecondaryEventsType.ERROR_BUDGET_RESET);
    assertThat(secondaryEvents.get(2).getStartTime()).isEqualTo(startTime + Duration.ofMinutes(2).toSeconds());
    assertThat(secondaryEvents.get(2).getIdentifiers().get(0)).isEqualTo(sloErrorBudgetReset.getUuid());

    assertThat(secondaryEvents.get(3).getType()).isEqualTo(SecondaryEventsType.DATA_COLLECTION_FAILURE);
    assertThat(secondaryEvents.get(3).getStartTime()).isEqualTo(startTime + Duration.ofMinutes(3).toSeconds());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSecondaryEventsForCompositeSLO_Success() {
    MonitoredServiceDTO monitoredServiceDTO2 =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier + '1').build();
    monitoredServiceDTO2.setServiceRef("new");
    monitoredServiceDTO2.setEnvironmentRef("one");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);

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
                                            .serviceLevelObjectiveRef(serviceLevelObjective.getIdentifier())
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

    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationDTO.setSloIdentifier(compositeSLO.getIdentifier());
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);

    List<Annotation> annotations =
        annotationService.get(builderFactory.getProjectParams(), compositeSLO.getIdentifier());

    List<SecondaryEventsResponse> secondaryEvents =
        sloDashboardService.getSecondaryEvents(builderFactory.getProjectParams(), startTime * 1000,
            (startTime + Duration.ofMinutes(10).toSeconds()) * 1000, compositeSLO.getIdentifier());
    assertThat(secondaryEvents.size()).isEqualTo(1);

    assertThat(secondaryEvents.get(0).getType()).isEqualTo(SecondaryEventsType.ANNOTATION);
    assertThat(secondaryEvents.get(0).getIdentifiers().get(0)).isEqualTo(annotations.get(0).getUuid());
    assertThat(secondaryEvents.get(0).getStartTime()).isEqualTo(annotations.get(0).getStartTime());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSecondaryEventDetails_Success() {
    serviceLevelObjectiveV2DTO =
        builderFactory.getSimpleCalendarServiceLevelObjectiveV2DTOBuilder().identifier("slo").build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec();
    simpleServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceIdentifier);
    simpleServiceLevelObjectiveSpec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjectiveV2DTO.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    serviceLevelObjective = serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
            simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).getIdentifier());

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

    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationDTO.setSloIdentifier("slo");
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);
    annotationDTO.setMessage("new one");
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);

    List<Annotation> annotations =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());
    List<String> annotationIds = annotations.stream().map(Annotation::getUuid).collect(Collectors.toList());

    entityUnavailabilityStatusesService.create(builderFactory.getProjectParams(),
        Collections.singletonList(EntityUnavailabilityStatusesDTO.builder()
                                      .orgIdentifier(builderFactory.getProjectParams().getOrgIdentifier())
                                      .projectIdentifier(builderFactory.getProjectParams().getProjectIdentifier())
                                      .entityType(EntityType.SLO)
                                      .entityId(serviceLevelIndicator.getUuid())
                                      .status(EntityUnavailabilityStatus.DATA_COLLECTION_FAILED)
                                      .startTime(startTime)
                                      .endTime(endTime)
                                      .build()));
    List<EntityUnavailabilityStatuses> instances = entityUnavailabilityStatusesService.getAllUnavailabilityInstances(
        builderFactory.getProjectParams(), startTime, endTime);
    SLOErrorBudgetResetDTO sloErrorBudgetResetDTO =
        builderFactory.getSLOErrorBudgetResetDTOBuilder()
            .createdAt((startTime + Duration.ofMinutes(2).toSeconds()) * 1000)
            .build();
    sloErrorBudgetResetDTO.setServiceLevelObjectiveIdentifier(serviceLevelObjectiveV2DTO.getIdentifier());
    sloErrorBudgetResetService.resetErrorBudget(builderFactory.getProjectParams(), sloErrorBudgetResetDTO);
    SLOErrorBudgetReset sloErrorBudgetReset = ((SLOErrorBudgetResetServiceImpl) sloErrorBudgetResetService)
                                                  .getSLOErrorBudgetResetEntities(builderFactory.getProjectParams(),
                                                      serviceLevelObjectiveV2DTO.getIdentifier())
                                                  .get(0);
    UpdateOperations<SLOErrorBudgetReset> updateOperations =
        hPersistence.createUpdateOperations(SLOErrorBudgetReset.class);
    updateOperations.set(SLOErrorBudgetResetKeys.createdAt, (startTime + Duration.ofMinutes(2).toSeconds()) * 1000);
    hPersistence.update(sloErrorBudgetReset, updateOperations);

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

    response = sloDashboardService.getSecondaryEventDetails(
        SecondaryEventsType.ERROR_BUDGET_RESET, Collections.singletonList(sloErrorBudgetReset.getUuid()));
    assertThat(response.getStartTime()).isEqualTo(startTime + Duration.ofMinutes(2).toSeconds());
    assertThat(response.getType()).isEqualTo(SecondaryEventsType.ERROR_BUDGET_RESET);
    assertThat(((SLOErrorBudgetResetInstanceDetails) response.getDetails()).getErrorBudgetIncrementMinutes())
        .isEqualTo(sloErrorBudgetReset.getErrorBudgetIncrementMinutes());

    response = sloDashboardService.getSecondaryEventDetails(
        SecondaryEventsType.DATA_COLLECTION_FAILURE, Collections.singletonList(instances.get(1).getUuid()));
    assertThat(response.getStartTime()).isEqualTo(startTime);
    assertThat(response.getEndTime()).isEqualTo(endTime);
    assertThat(response.getType()).isEqualTo(SecondaryEventsType.DATA_COLLECTION_FAILURE);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSecondaryEventDetails_WithDifferentThreadMessageError() {
    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
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

  private void createData(Instant startTime, List<SLIState> sliStates, String sliId) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, sliId, 0);
  }

  private void createData(
      Instant startTime, List<SLIState> sliStates, List<Long> goodCounts, List<Long> badCounts, String sliId) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates, goodCounts, badCounts);
    sliRecordService.create(sliRecordParams, sliId, sliId, 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(
      Instant startTime, List<SLIState> sliStates, List<Long> goodCounts, List<Long> badCounts) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      long goodCount = goodCounts.get(i);
      long badCount = badCounts.get(i);
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

  private List<CompositeSLORecord> createSLORecords(
      Instant start, Instant end, List<List<SLIRecord>> objectiveDetailToSLIRecordList) {
    int index = 0;
    int numberOfReferredSLOs = compositeServiceLevelObjective.getServiceLevelObjectivesDetails().size();
    List<CompositeSLORecord> sloRecords = new ArrayList<>();
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      Map<String, SLIRecord> scopedIdentifierToSLIRecordMap = new HashMap<>();
      for (int i = 0; i < numberOfReferredSLOs; i++) {
        scopedIdentifierToSLIRecordMap.put(
            serviceLevelObjectiveV2Service.getScopedIdentifier(
                compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(i)),
            objectiveDetailToSLIRecordList.get(i).get(index));
      }
      CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                         .verificationTaskId(verificationTaskId)
                                         .sloId(compositeServiceLevelObjective.getUuid())
                                         .version(0)
                                         .runningBadCount(0)
                                         .runningGoodCount(0)
                                         .sloVersion(0)
                                         .timestamp(instant)
                                         .scopedIdentifierSLIRecordMap(scopedIdentifierToSLIRecordMap)
                                         .build();
      sloRecords.add(sloRecord);
      index++;
    }
    hPersistence.save(sloRecords);
    return sloRecords;
  }

  private List<SLIRecord> createSLIRecords(
      Instant start, Instant end, String sliId, List<SLIState> states, List<Long> goodCounts, List<Long> badCounts) {
    int index = 0;
    List<SLIRecord> sliRecords = new ArrayList<>();
    long runningGoodCount = 0;
    long runningBadCount = 0;
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      runningGoodCount += goodCounts.get(index);
      runningBadCount += badCounts.get(index);
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
      double total =
          runningGoodCount.get(i) + runningBadCount.get(i) - runningBadCount.get(0) - runningGoodCount.get(0);
      double percentageTrend;
      if (total == 0) {
        percentageTrend = 100;
      } else {
        percentageTrend = ((runningGoodCount.get(i) - runningGoodCount.get(0)) * 100) / total;
      }
      assertThat(sloPerformanceTrend.get(i).getValue()).isCloseTo(percentageTrend, offset(0.01));
      assertThat(errorBudgetBurndown.get(i).getTimestamp())
          .isEqualTo(startTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(errorBudgetBurndown.get(i).getValue())
          .isCloseTo(((totalErrorBudgetMinutes - (runningBadCount.get(i) - runningBadCount.get(0))) * 100.0)
                  / totalErrorBudgetMinutes,
              offset(0.01));
    }
  }
}
