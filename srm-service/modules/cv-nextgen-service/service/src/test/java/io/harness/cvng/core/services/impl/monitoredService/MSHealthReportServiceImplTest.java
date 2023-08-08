/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.CVNGTestConstants.FIXED_TIME_FOR_TESTS;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.FIVE_MIN;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANALYSIS_DURATION;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANALYSIS_ENDED_AT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANALYSIS_PIPELINE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANALYSIS_STARTED_AT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_IDENTIFIER;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MS_HEALTH_REPORT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PIPELINE_ID;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PLAN_EXECUTION_ID;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SERVICE_IDENTIFIER;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.STAGE_STEP_ID;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.GOOD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.client.FakeNotificationClient;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.MSHealthReport;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.monitoredService.MSHealthReportService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.FeatureFlagNames;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.FireHydrantReportNotificationCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceDeploymentImpactReportCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RollingSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.notification.notificationclient.NotificationResultWithoutStatus;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MSHealthReportServiceImplTest extends CvNextGenTestBase {
  @Inject ActivityService activityService;
  @Inject ChangeEventService changeEventService;
  @Inject HPersistence hPersistence;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject MSHealthReportService msHealthReportService;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject SLIRecordService sliRecordService;
  Clock clock;

  @Mock FakeNotificationClient notificationClient;

  BuilderFactory builderFactory;
  FeatureFlagService featureFlagService;

  MonitoredServiceResponse monitoredServiceResponse;

  @Before
  public void before() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    clock = FIXED_TIME_FOR_TESTS;
    monitoredServiceResponse = monitoredServiceService.createDefault(builderFactory.getProjectParams(),
        builderFactory.getContext().getServiceIdentifier(), builderFactory.getContext().getEnvIdentifier());
    MockitoAnnotations.initMocks(this);
    featureFlagService = mock(FeatureFlagService.class);
    when(featureFlagService.isFeatureFlagEnabled(
             eq(builderFactory.getContext().getAccountId()), eq(FeatureFlagNames.SRM_INTERNAL_CHANGE_SOURCE_CE)))
        .thenReturn(true);
    FieldUtils.writeField(changeEventService, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(msHealthReportService, "notificationClient", notificationClient, true);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetMSHealthReport() {
    Instant eventTime = clock.instant();
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(eventTime.minus(Duration.ofMinutes(60))).build(),
        builderFactory.getInternalChangeActivity_CEBuilder().eventTime(eventTime.minus(Duration.ofMinutes(45))).build(),
        builderFactory.getInternalChangeActivity_FFBuilder().eventTime(eventTime.minus(Duration.ofMinutes(45))).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(eventTime.minus(Duration.ofMinutes(15)))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO =
        changeEventService.getChangeSummary(builderFactory.getContext().getProjectParams(), null, null, null, null,
            eventTime.minus(Duration.ofMinutes(60)), eventTime);

    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    ((RollingSLOTargetSpec) simpleServiceLevelObjectiveDTO.getSloTarget().getSpec()).setPeriodLength("1d");
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO);
    SimpleServiceLevelObjective simpleServiceLevelObjective =
        (SimpleServiceLevelObjective) serviceLevelObjectiveService.getEntity(
            builderFactory.getProjectParams(), "sloIdentifier");
    String sliIdentifier = simpleServiceLevelObjective.getServiceLevelIndicators().get(0);
    List<SLIState> sliStates = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      sliStates.add(GOOD);
    }
    for (int i = 30; i < 60; i++) {
      sliStates.add(BAD);
    }
    String sliId =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(), sliIdentifier)
            .getUuid();
    createData(eventTime.minus(Duration.ofMinutes(60)), sliStates, sliId, simpleServiceLevelObjective.getUuid());

    createHeatMaps(eventTime.plus(Duration.ofMinutes(10)));

    MSHealthReport msHealthReport = msHealthReportService.getMSHealthReport(builderFactory.getProjectParams(),
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier(),
        clock.instant().minus(1, ChronoUnit.HOURS));
    assertThat(msHealthReport.getChangeSummary().getTotal()).isEqualTo(changeSummaryDTO.getTotal());
    MSHealthReport.AssociatedSLOsDetails associatedSLODetails = msHealthReport.getAssociatedSLOsDetails().get(0);
    assertThat(associatedSLODetails.getIdentifier()).isEqualTo(simpleServiceLevelObjective.getIdentifier());
    assertThat(associatedSLODetails.getCurrentSLOPerformance()).isEqualTo(50);
    assertThat(associatedSLODetails.getErrorBudgetBurned()).isEqualTo(30, offset(0.01));
    assertThat(msHealthReport.getCurrentHealthScore().getHealthScore()).isEqualTo(52);
    assertThat(msHealthReport.getCurrentHealthScore().getRiskStatus()).isEqualTo(Risk.OBSERVE);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetMSHealthReport_WithNoData() {
    MSHealthReport msHealthReport = msHealthReportService.getMSHealthReport(builderFactory.getProjectParams(),
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier(),
        clock.instant().minus(1, ChronoUnit.HOURS));
    assertThat(msHealthReport.getChangeSummary().getTotal().getCount()).isEqualTo(0);
    assertThat(msHealthReport.getAssociatedSLOsDetails().size()).isEqualTo(0);
    assertThat(msHealthReport.getCurrentHealthScore().getHealthScore()).isEqualTo(null);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testHandleNotification() {
    MSHealthReport msHealthReport = msHealthReportService.getMSHealthReport(builderFactory.getProjectParams(),
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier(),
        clock.instant().minus(1, ChronoUnit.HOURS));
    when(notificationClient.sendNotificationAsync(any()))
        .thenReturn(NotificationResultWithoutStatus.builder().notificationId("notificationId").build());
    Map<String, Object> entityDetails = Map.of(ENTITY_IDENTIFIER,
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier(), ENTITY_NAME,
        monitoredServiceResponse.getMonitoredServiceDTO().getName(), SERVICE_IDENTIFIER,
        monitoredServiceResponse.getMonitoredServiceDTO().getServiceRef(), MS_HEALTH_REPORT, msHealthReport);
    msHealthReportService.sendReportNotification(builderFactory.getProjectParams(), entityDetails,
        NotificationRuleType.FIRE_HYDRANT, FireHydrantReportNotificationCondition.builder().build(),
        new NotificationRule.CVNGSlackChannel(null, "webhookUrl"),
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNotificationForDeploymentImpact() {
    MSHealthReport msHealthReport = msHealthReportService.getMSHealthReport(builderFactory.getProjectParams(),
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier(),
        clock.instant().minus(2, ChronoUnit.DAYS));
    when(notificationClient.sendNotificationAsync(any()))
        .thenReturn(NotificationResultWithoutStatus.builder().notificationId("notificationId").build());
    Map<String, Object> entityDetails = new HashMap<>();
    entityDetails.put(
        ENTITY_IDENTIFIER, builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
    entityDetails.put(ENTITY_NAME, monitoredServiceResponse.getMonitoredServiceDTO().getName());
    entityDetails.put(SERVICE_IDENTIFIER, monitoredServiceResponse.getMonitoredServiceDTO().getServiceRef());
    entityDetails.put(MS_HEALTH_REPORT, msHealthReport);
    entityDetails.put(PIPELINE_ID, generateUuid());
    entityDetails.put(PLAN_EXECUTION_ID, generateUuid());
    entityDetails.put(STAGE_STEP_ID, generateUuid());
    entityDetails.put(ANALYSIS_ENDED_AT, "18 Jul at 2:10 AM GMT");
    entityDetails.put(ANALYSIS_STARTED_AT, "16 Jul at 2:10 AM GMT");
    entityDetails.put(ANALYSIS_DURATION, "2 days");
    entityDetails.put(ANALYSIS_PIPELINE_NAME, "pipeline_name");
    msHealthReportService.sendReportNotification(builderFactory.getProjectParams(), entityDetails,
        NotificationRuleType.MONITORED_SERVICE, MonitoredServiceDeploymentImpactReportCondition.builder().build(),
        new NotificationRule.CVNGSlackChannel(null, "webhookUrl"),
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  private void createData(Instant startTime, List<SLIState> sliStates, String sliId, String verificationTaskId) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
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

  private void createHeatMaps(Instant endTime) {
    HeatMapResolution heatMapResolution = FIVE_MIN;
    endTime = getBoundaryOfResolution(endTime, heatMapResolution.getBucketSize())
                  .plusMillis(heatMapResolution.getBucketSize().toMillis());
    Instant startTime = endTime.minus(heatMapResolution.getBucketSize());
    HeatMap heatMap = builderFactory.heatMapBuilder()
                          .heatMapResolution(heatMapResolution)
                          .category(CVMonitoringCategory.ERRORS)
                          .heatMapBucketStartTime(startTime)
                          .heatMapBucketEndTime(endTime)
                          .build();
    List<HeatMapRisk> heatMapRisks = new ArrayList<>();
    int risk = 1;
    for (Instant time = startTime; time.isBefore(endTime); time = time.plus(heatMapResolution.getResolution())) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore((double) risk / 100)
                           .startTime(time)
                           .endTime(time.plus(heatMapResolution.getResolution()))
                           .build());
      risk++;
    }
    heatMap.setHeatMapRisks(heatMapRisks);
    hPersistence.save(heatMap);
  }

  private Instant getBoundaryOfResolution(Instant input, Duration resolution) {
    long timeStamp = input.toEpochMilli();
    return Instant.ofEpochMilli(timeStamp - (timeStamp % resolution.toMillis()));
  }
}
