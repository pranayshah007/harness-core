/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.cvng.core.services.CVNextGenConstants.CV_ANALYSIS_WINDOW_MINUTES;
import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.cvng.core.utils.DateTimeUtils.roundDownToMinBoundary;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.FIVE_MIN;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.THIRTY_MINUTES;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.monitoredService.HistoricalTrend;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapKeys;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class HeatMapServiceImplTest extends CvNextGenTestBase {
  @Inject private HeatMapService heatMapService;

  private String projectIdentifier;

  private String monitoredServiceIdentifier;
  private String accountId;
  private String orgIdentifier;
  private CVConfig cvConfig;
  @Inject private HPersistence hPersistence;
  private Clock clock;
  private BuilderFactory builderFactory;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    monitoredServiceIdentifier =
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier();
    cvConfig = builderFactory.appDynamicsCVConfigBuilder().build();
    clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(heatMapService, "clock", clock, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpsertAddsAllFields() {
    Instant instant = Instant.now();
    heatMapService.updateRiskScore(
        accountId, orgIdentifier, projectIdentifier, cvConfig, CVMonitoringCategory.PERFORMANCE, instant, 0.6, 10, 0);
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class, excludeAuthority).asList();
    Set<String> nullableFields = Sets.newHashSet(HeatMapKeys.monitoredServiceIdentifier);
    heatMaps.forEach(heatMap -> {
      List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(HeatMap.class);
      fields.stream().filter(field -> !nullableFields.contains(field.getName())).forEach(field -> {
        try {
          field.setAccessible(true);
          assertThat(field.get(heatMap)).withFailMessage("field %s is null", field.getName()).isNotNull();
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      });
    });
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpsertAndUpdate() {
    Instant instant = Instant.now();
    heatMapService.updateRiskScore(
        accountId, orgIdentifier, projectIdentifier, cvConfig, CVMonitoringCategory.PERFORMANCE, instant, 0.6, 10, 9);
    verifyUpdates(instant, 0.6, 10, 9);

    // update and test
    heatMapService.updateRiskScore(
        accountId, orgIdentifier, projectIdentifier, cvConfig, CVMonitoringCategory.PERFORMANCE, instant, 0.7, 5, 8);
    verifyUpdates(instant, 0.7, 15, 17);

    // updating with lower risk score shouldn't change anything
    heatMapService.updateRiskScore(
        accountId, orgIdentifier, projectIdentifier, cvConfig, CVMonitoringCategory.PERFORMANCE, instant, 0.5, 5, 5);
    verifyUpdates(instant, 0.7, 20, 22);
  }

  private void verifyUpdates(Instant instant, double riskScore, long anomalousMetricsCount, long anomalousLogsCount) {
    verifyHeatMaps(instant, riskScore,
        hPersistence.createQuery(HeatMap.class, excludeAuthority)
            .filter(HeatMapKeys.projectIdentifier, projectIdentifier)
            .filter(HeatMapKeys.monitoredServiceIdentifier, cvConfig.getMonitoredServiceIdentifier())
            .asList(),
        anomalousMetricsCount, anomalousLogsCount);
  }

  private void verifyHeatMaps(
      Instant instant, double riskScore, List<HeatMap> heatMaps, long anomalousMetricsCount, long anomalousLogsCount) {
    assertThat(heatMaps.size()).isEqualTo(HeatMapResolution.values().length);
    for (int i = 0; i < HeatMapResolution.values().length; i++) {
      HeatMapResolution heatMapResolution = HeatMapResolution.values()[i];
      HeatMap heatMap = heatMaps.get(i);
      assertThat(heatMap.getAccountId()).isEqualTo(accountId);
      assertThat(heatMap.getProjectIdentifier()).isEqualTo(projectIdentifier);
      assertThat(heatMap.getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
      assertThat(heatMap.getMonitoredServiceIdentifier()).isEqualTo(cvConfig.getMonitoredServiceIdentifier());
      assertThat(heatMap.getHeatMapResolution()).isEqualTo(heatMapResolution);
      assertThat(heatMap.getHeatMapBucketStartTime())
          .isEqualTo(Instant.ofEpochMilli(instant.toEpochMilli()
              - Math.floorMod(instant.toEpochMilli(), heatMapResolution.getBucketSize().toMillis())));
      assertThat(heatMap.getHeatMapBucketEndTime())
          .isEqualTo(heatMap.getHeatMapBucketStartTime().plusMillis(heatMapResolution.getBucketSize().toMillis()));
      Set<HeatMapRisk> heatMapRisks = heatMap.getHeatMapRisks();
      assertThat(heatMapRisks.size()).isEqualTo(1);
      HeatMapRisk heatMapRisk = heatMapRisks.iterator().next();
      assertThat(heatMapRisk.getStartTime())
          .isEqualTo(Instant.ofEpochMilli(instant.toEpochMilli()
              - Math.floorMod(instant.toEpochMilli(), heatMapResolution.getResolution().toMillis())));
      assertThat(heatMapRisk.getEndTime())
          .isEqualTo(heatMapRisk.getStartTime().plusMillis(heatMapResolution.getResolution().toMillis()));
      assertThat(heatMapRisk.getRiskScore()).isEqualTo(riskScore, offset(0.001));
      assertThat(heatMapRisk.getAnomalousMetricsCount()).isEqualTo(anomalousMetricsCount);
      assertThat(heatMapRisk.getAnomalousLogsCount()).isEqualTo(anomalousLogsCount);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpsert_whenMultipleBoundaries() {
    double numOfUnits = 1500;
    for (int minuteBoundry = 0; minuteBoundry < numOfUnits * CV_ANALYSIS_WINDOW_MINUTES;
         minuteBoundry += CV_ANALYSIS_WINDOW_MINUTES) {
      heatMapService.updateRiskScore(accountId, orgIdentifier, projectIdentifier, cvConfig,
          CVMonitoringCategory.PERFORMANCE, Instant.ofEpochMilli(TimeUnit.MINUTES.toMillis(minuteBoundry)), 0.6, 0, 0);
    }
    for (int i = 0; i < HeatMapResolution.values().length; i++) {
      HeatMapResolution heatMapResolution = HeatMapResolution.values()[i];
      List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class, excludeAuthority)
                                   .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                   .filter(HeatMapKeys.orgIdentifier, orgIdentifier)
                                   .filter(HeatMapKeys.projectIdentifier, projectIdentifier)
                                   .filter(HeatMapKeys.monitoredServiceIdentifier, monitoredServiceIdentifier)
                                   .asList();
      assertThat(heatMaps.size())
          .isEqualTo((int) Math.ceil(numOfUnits * TimeUnit.MINUTES.toMillis(CV_ANALYSIS_WINDOW_MINUTES)
              / heatMapResolution.getBucketSize().toMillis()));
      for (int j = 0; j < heatMaps.size(); j++) {
        HeatMap heatMap = heatMaps.get(j);
        assertThat(heatMap.getHeatMapResolution()).isEqualTo(heatMapResolution);
        assertThat(heatMap.getHeatMapBucketStartTime())
            .isEqualTo(Instant.ofEpochMilli(j * heatMapResolution.getBucketSize().toMillis()));
        SortedSet<HeatMapRisk> heatMapRisks = new TreeSet<>(heatMap.getHeatMapRisks());
        AtomicLong timeStamp = new AtomicLong(j * heatMapResolution.getBucketSize().toMillis());
        heatMapRisks.forEach(heatMapRisk -> {
          assertThat(heatMapRisk.getStartTime()).isEqualTo(Instant.ofEpochMilli(timeStamp.get()));
          timeStamp.addAndGet(heatMapResolution.getResolution().toMillis());
          assertThat(heatMapRisk.getRiskScore()).isEqualTo(0.6, offset(0.001));
        });
      }
    }

    // update a riskscore
    Instant updateInstant = Instant.ofEpochMilli(TimeUnit.MINUTES.toMillis(680));
    heatMapService.updateRiskScore(accountId, orgIdentifier, projectIdentifier, cvConfig,
        CVMonitoringCategory.PERFORMANCE, updateInstant, 0.7, 0, 0);

    for (int i = 0; i < HeatMapResolution.values().length; i++) {
      HeatMapResolution heatMapResolution = HeatMapResolution.values()[i];
      Instant bucketBoundary = Instant.ofEpochMilli(updateInstant.toEpochMilli()
          - Math.floorMod(updateInstant.toEpochMilli(), heatMapResolution.getBucketSize().toMillis()));
      List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class, excludeAuthority)
                                   .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                   .filter(HeatMapKeys.heatMapBucketStartTime, bucketBoundary)
                                   .filter(HeatMapKeys.projectIdentifier, projectIdentifier)
                                   .filter(HeatMapKeys.monitoredServiceIdentifier, monitoredServiceIdentifier)
                                   .asList();
      assertThat(heatMaps.size()).isEqualTo(1);
      HeatMap heatMap = heatMaps.get(0);
      Instant heatMapTimeStamp = Instant.ofEpochMilli(updateInstant.toEpochMilli()
          - Math.floorMod(updateInstant.toEpochMilli(), heatMapResolution.getResolution().toMillis()));

      AtomicBoolean verified = new AtomicBoolean(false);
      heatMap.getHeatMapRisks().forEach(heatMapRisk -> {
        if (heatMapRisk.getStartTime().equals(heatMapTimeStamp)) {
          verified.set(true);
          assertThat(heatMapRisk.getRiskScore()).isEqualTo(0.7, offset(0.0001));
        } else {
          assertThat(heatMapRisk.getRiskScore()).isEqualTo(0.6, offset(0.0001));
        }
      });
      assertThat(verified.get()).isTrue();
    }
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetHistoricalData_PreConditionServiceEnvironmentListLimit() {
    List<String> monitoredServiceIdentifiers = Collections.nCopies(11, monitoredServiceIdentifier);
    assertThatThrownBy(()
                           -> heatMapService.getHistoricalTrend(
                               accountId, orgIdentifier, projectIdentifier, monitoredServiceIdentifiers, 24))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            String.format("Based on page size, the health score calculation should be done for less than 10 services"));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetHistoricalData_EmptyServiceEnvironmentList() {
    List<HistoricalTrend> historicalTrendList =
        heatMapService.getHistoricalTrend(accountId, orgIdentifier, projectIdentifier, Collections.emptyList(), 24);
    assertThat(historicalTrendList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetHistoricalData_OneServiceEnvironmentOneCategoryForOneHour() {
    HeatMap heatMap = builderFactory.heatMapBuilder().build();
    heatMap.getHeatMapRisks().forEach(heatMapRisk -> heatMapRisk.setRiskScore(0.10));
    hPersistence.save(heatMap);
    HeatMap heatMapPrevious = builderFactory.heatMapBuilder().build();
    setStartTimeEndTimeAndRiskScoreWith30MinBucket(heatMapPrevious, heatMap.getHeatMapBucketStartTime(), 0.50);
    hPersistence.save(heatMapPrevious);

    List<HistoricalTrend> historicalTrendList = heatMapService.getHistoricalTrend(
        accountId, orgIdentifier, projectIdentifier, Arrays.asList(monitoredServiceIdentifier), 1);
    assertThat(historicalTrendList.size()).isEqualTo(1);
    assertThat(historicalTrendList.get(0).getHealthScores().size()).isEqualTo(2);
    historicalTrendList.get(0).getHealthScores().forEach(score -> {
      assertThat(score.getHealthScore()).isEqualTo(90);
      assertThat(score.getRiskStatus()).isEqualTo(Risk.HEALTHY);
    });
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetHistoricalData_OneServiceEnvironmentMultipleCategoryForOneHour() {
    HeatMap heatMapErrorCategory = builderFactory.heatMapBuilder().build();
    heatMapErrorCategory.getHeatMapRisks().forEach(heatMapRisk -> heatMapRisk.setRiskScore(0.10));
    hPersistence.save(heatMapErrorCategory);
    HeatMap heatMapPerformanceCategory =
        builderFactory.heatMapBuilder().category(CVMonitoringCategory.PERFORMANCE).build();
    Instant analysisTime = roundDownToMinBoundary(clock.instant(), 30);
    heatMapPerformanceCategory.getHeatMapRisks().forEach(heatMapRisk -> {
      if (ChronoUnit.MINUTES.between(heatMapRisk.getEndTime(), analysisTime) < 30) {
        heatMapRisk.setRiskScore(.50);
      }
    });
    hPersistence.save(heatMapPerformanceCategory);

    List<HistoricalTrend> historicalTrendList = heatMapService.getHistoricalTrend(
        accountId, orgIdentifier, projectIdentifier, Arrays.asList(monitoredServiceIdentifier), 1);

    assertThat(historicalTrendList.size()).isEqualTo(1);
    assertThat(historicalTrendList.get(0).getHealthScores().size()).isEqualTo(2);

    assertThat(historicalTrendList.get(0).getHealthScores().get(0).getHealthScore()).isEqualTo(90);
    assertThat(historicalTrendList.get(0).getHealthScores().get(0).getRiskStatus()).isEqualTo(Risk.HEALTHY);

    assertThat(historicalTrendList.get(0).getHealthScores().get(1).getHealthScore()).isEqualTo(50);
    assertThat(historicalTrendList.get(0).getHealthScores().get(1).getRiskStatus()).isEqualTo(Risk.OBSERVE);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetHistoricalData_OneServiceEnvironmentOneCategoryFor24Hour() {
    Instant analysisTime = clock.instant();
    analysisTime = roundDownToMinBoundary(analysisTime, 30);
    HeatMap heatMap = builderFactory.heatMapBuilder().build();
    setStartTimeEndTimeAndRiskScoreWith30MinBucket(heatMap, analysisTime.plus(12, ChronoUnit.HOURS), 0.10);
    hPersistence.save(heatMap);

    HeatMap heatMapPrevious = builderFactory.heatMapBuilder().build();
    setStartTimeEndTimeAndRiskScoreWith30MinBucket(heatMapPrevious, heatMap.getHeatMapBucketStartTime(), 0.50);
    hPersistence.save(heatMapPrevious);

    List<HistoricalTrend> historicalTrendList = heatMapService.getHistoricalTrend(
        accountId, orgIdentifier, projectIdentifier, Arrays.asList(monitoredServiceIdentifier), 24);

    assertThat(historicalTrendList.size()).isEqualTo(1);
    assertThat(historicalTrendList.get(0).getHealthScores().size()).isEqualTo(48);

    for (int i = 0; i < 24; i++) {
      assertThat(historicalTrendList.get(0).getHealthScores().get(i).getHealthScore()).isEqualTo(50);
      assertThat(historicalTrendList.get(0).getHealthScores().get(i).getRiskStatus()).isEqualTo(Risk.OBSERVE);
    }
    for (int i = 24; i < 48; i++) {
      assertThat(historicalTrendList.get(0).getHealthScores().get(i).getHealthScore()).isEqualTo(90);
      assertThat(historicalTrendList.get(0).getHealthScores().get(i).getRiskStatus()).isEqualTo(Risk.HEALTHY);
    }
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetHistoricalData_MultipleServiceEnvironmentOneCategoryForOneHour() {
    String secondMonitoredService = "secondService";
    HeatMap heatMap = builderFactory.heatMapBuilder().build();
    heatMap.getHeatMapRisks().forEach(heatMapRisk -> heatMapRisk.setRiskScore(0.10));
    hPersistence.save(heatMap);

    HeatMap heatMapPrevious = builderFactory.heatMapBuilder().build();
    setStartTimeEndTimeAndRiskScoreWith30MinBucket(heatMapPrevious, heatMap.getHeatMapBucketStartTime(), 0.50);
    hPersistence.save(heatMapPrevious);

    HeatMap anotherServiceHeatMap =
        builderFactory.heatMapBuilder().monitoredServiceIdentifier(secondMonitoredService).build();
    anotherServiceHeatMap.getHeatMapRisks().forEach(heatMapRisk -> heatMapRisk.setRiskScore(0.55));
    hPersistence.save(anotherServiceHeatMap);

    List<String> serviceEnvIds = Arrays.asList(monitoredServiceIdentifier, secondMonitoredService);

    List<HistoricalTrend> historicalTrendList =
        heatMapService.getHistoricalTrend(accountId, orgIdentifier, projectIdentifier, serviceEnvIds, 1);
    assertThat(historicalTrendList.size()).isEqualTo(2);

    assertThat(historicalTrendList.get(0).getHealthScores().size()).isEqualTo(2);
    assertThat(historicalTrendList.get(1).getHealthScores().size()).isEqualTo(2);

    assertThat(historicalTrendList.get(0).getHealthScores().get(0).getHealthScore()).isEqualTo(90);
    assertThat(historicalTrendList.get(0).getHealthScores().get(0).getRiskStatus()).isEqualTo(Risk.HEALTHY);
    assertThat(historicalTrendList.get(0).getHealthScores().get(1).getHealthScore()).isEqualTo(90);
    assertThat(historicalTrendList.get(0).getHealthScores().get(1).getRiskStatus()).isEqualTo(Risk.HEALTHY);

    assertThat(historicalTrendList.get(1).getHealthScores().get(0).getHealthScore()).isEqualTo(45);
    assertThat(historicalTrendList.get(1).getHealthScores().get(0).getRiskStatus()).isEqualTo(Risk.NEED_ATTENTION);
    assertThat(historicalTrendList.get(1).getHealthScores().get(1).getHealthScore()).isEqualTo(45);
    assertThat(historicalTrendList.get(1).getHealthScores().get(1).getRiskStatus()).isEqualTo(Risk.NEED_ATTENTION);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetHistoricalData_MultipleServiceEnvironmentOneCategoryFor48Hour() {
    Instant analysisTime = clock.instant();
    analysisTime = roundDownToMinBoundary(analysisTime, 30);
    HeatMap heatMap = builderFactory.heatMapBuilder().build();
    setStartTimeEndTimeAndRiskScoreWith30MinBucket(heatMap, analysisTime.plus(12, ChronoUnit.HOURS), 0.10);
    hPersistence.save(heatMap);

    HeatMap heatMapPrevious = builderFactory.heatMapBuilder().build();
    setStartTimeEndTimeAndRiskScoreWith30MinBucket(heatMapPrevious, heatMap.getHeatMapBucketStartTime(), 0.40);
    hPersistence.save(heatMapPrevious);

    String secondMonitoredServiceIdentifier = "secondService";
    HeatMap anotherServiceHeatMap =
        builderFactory.heatMapBuilder().monitoredServiceIdentifier(secondMonitoredServiceIdentifier).build();
    anotherServiceHeatMap.getHeatMapRisks().forEach(heatMapRisk -> heatMapRisk.setRiskScore(0.55));
    hPersistence.save(anotherServiceHeatMap);

    List<String> serviceEnvIds = Arrays.asList(monitoredServiceIdentifier, secondMonitoredServiceIdentifier);
    List<HistoricalTrend> historicalTrendList =
        heatMapService.getHistoricalTrend(accountId, orgIdentifier, projectIdentifier, serviceEnvIds, 24);

    assertThat(historicalTrendList.size()).isEqualTo(2);

    assertThat(historicalTrendList.get(0).getHealthScores().size()).isEqualTo(48);
    assertThat(historicalTrendList.get(1).getHealthScores().size()).isEqualTo(48);

    for (int i = 0; i < 24; i++) {
      assertThat(historicalTrendList.get(0).getHealthScores().get(i).getHealthScore().intValue()).isEqualTo(60);
      assertThat(historicalTrendList.get(0).getHealthScores().get(i).getRiskStatus()).isEqualTo(Risk.OBSERVE);
    }
    for (int i = 24; i < 48; i++) {
      assertThat(historicalTrendList.get(0).getHealthScores().get(i).getHealthScore().intValue()).isEqualTo(90);
      assertThat(historicalTrendList.get(0).getHealthScores().get(i).getRiskStatus()).isEqualTo(Risk.HEALTHY);
    }

    for (int i = 0; i < 48; i++) {
      assertThat(historicalTrendList.get(1).getHealthScores().get(i).getHealthScore().intValue()).isEqualTo(45);
      assertThat(historicalTrendList.get(1).getHealthScores().get(i).getRiskStatus()).isEqualTo(Risk.NEED_ATTENTION);
    }
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLatestRiskScoreOneServiceEnvironmentOneCategoryWithLatestBucketPresent() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    HeatMap previousHeatmap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(previousHeatmap, heatMap.getHeatMapBucketStartTime(), 0.5, 0.5);
    hPersistence.save(previousHeatmap);

    Map<String, RiskData> riskData = heatMapService.getLatestRiskScoreByMonitoredService(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier));

    assertThat(riskData.size()).isEqualTo(1);
    Map.Entry<String, RiskData> entry = riskData.entrySet().iterator().next();
    assertThat(entry.getKey()).isEqualTo(monitoredServiceIdentifier);
    assertThat(entry.getValue().getRiskStatus()).isEqualTo(Risk.HEALTHY);
    assertThat(entry.getValue().getHealthScore()).isEqualTo(75);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void
  testGetLatestRiskScoreOneServiceEnvironmentOneCategoryWithLatestBucketPresentAndLast10MinDataNotPresent() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    SortedSet<HeatMapRisk> risks = new TreeSet<>(heatMap.getHeatMapRisks());
    risks.remove(risks.last());
    risks.remove(risks.last());
    heatMap.setHeatMapRisks(risks.stream().collect(Collectors.toList()));
    hPersistence.save(heatMap);

    Map<String, RiskData> riskData = heatMapService.getLatestRiskScoreByMonitoredService(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier));

    assertThat(riskData.size()).isEqualTo(1);
    Map.Entry<String, RiskData> entry = riskData.entrySet().iterator().next();
    assertThat(entry.getKey()).isEqualTo(monitoredServiceIdentifier);
    assertThat(entry.getValue().getRiskStatus()).isEqualTo(Risk.HEALTHY);
    assertThat(entry.getValue().getHealthScore()).isEqualTo(75);

    risks.remove(risks.last());
    heatMap.setHeatMapRisks(risks.stream().collect(Collectors.toList()));
    hPersistence.save(heatMap);

    riskData = heatMapService.getLatestRiskScoreByMonitoredService(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier));
    assertThat(riskData.size()).isEqualTo(1);
    entry = riskData.entrySet().iterator().next();
    assertThat(entry.getKey()).isEqualTo(monitoredServiceIdentifier);
    assertThat(entry.getValue().getRiskStatus()).isEqualTo(Risk.NO_DATA);
    assertThat(entry.getValue().getHealthScore()).isEqualTo(null);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLatestRiskScoreOneServiceEnvironmentOneCategoryWithOnlyPreviousBucketPresent() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());

    HeatMap previousHeatmap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(previousHeatmap, endTime.minus(4, ChronoUnit.HOURS), 0.50, 0.10);
    hPersistence.save(previousHeatmap);

    Map<String, RiskData> riskData = heatMapService.getLatestRiskScoreByMonitoredService(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier));

    assertThat(riskData.size()).isEqualTo(1);
    Map.Entry<String, RiskData> entry = riskData.entrySet().iterator().next();
    assertThat(entry.getKey()).isEqualTo(monitoredServiceIdentifier);
    assertThat(entry.getValue().getRiskStatus()).isEqualTo(Risk.NO_DATA);
    assertThat(entry.getValue().getHealthScore()).isEqualTo(null);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLatestRiskScoreOneServiceEnvironmentOneCategoryWithNoHeatMapRiskPresent() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    Instant startTime = endTime.minus(4, ChronoUnit.HOURS);
    HeatMap previousHeatmap =
        builderFactory.heatMapBuilder()
            .heatMapResolution(FIVE_MIN)
            .heatMapBucketStartTime(startTime)
            .heatMapBucketEndTime(endTime)
            .heatMapRisks(Arrays.asList(
                HeatMapRisk.builder().startTime(startTime).endTime(startTime.plus(5, ChronoUnit.MINUTES)).build()))
            .build();
    hPersistence.save(previousHeatmap);

    Map<String, RiskData> riskData = heatMapService.getLatestRiskScoreByMonitoredService(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier));

    assertThat(riskData.size()).isEqualTo(1);
    Map.Entry<String, RiskData> entry = riskData.entrySet().iterator().next();
    assertThat(entry.getKey()).isEqualTo(monitoredServiceIdentifier);
    assertThat(entry.getValue().getRiskStatus()).isEqualTo(Risk.NO_DATA);
    assertThat(entry.getValue().getHealthScore()).isEqualTo(null);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLatestRiskScoreOneServiceEnvironmentMultipleCategory() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    HeatMap heatMapPerformanceCategory =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMapPerformanceCategory, endTime, 0.35, 0.65);
    hPersistence.save(heatMapPerformanceCategory);

    HeatMap previousHeatmap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(previousHeatmap, heatMap.getHeatMapBucketStartTime(), 0.5, 0.5);
    hPersistence.save(previousHeatmap);

    Map<String, RiskData> riskData = heatMapService.getLatestRiskScoreByMonitoredService(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier));

    assertThat(riskData.size()).isEqualTo(1);
    Map.Entry<String, RiskData> entry = riskData.entrySet().iterator().next();
    assertThat(entry.getKey()).isEqualTo(monitoredServiceIdentifier);
    assertThat(entry.getValue().getRiskStatus()).isEqualTo(Risk.NEED_ATTENTION);
    assertThat(entry.getValue().getHealthScore()).isEqualTo(35);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLatestRiskScoreMultipleServiceEnvironmentOneCategory() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    String newMonitoredServiceIdentifier = "newService";
    HeatMap anotherServiceEnvHeatMap = builderFactory.heatMapBuilder()
                                           .heatMapResolution(FIVE_MIN)
                                           .monitoredServiceIdentifier(newMonitoredServiceIdentifier)
                                           .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(anotherServiceEnvHeatMap, endTime, 0.11, 0.37);
    hPersistence.save(anotherServiceEnvHeatMap);

    Map<String, RiskData> riskData = heatMapService.getLatestRiskScoreByMonitoredService(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier, newMonitoredServiceIdentifier));

    assertThat(riskData.size()).isEqualTo(2);

    assertThat(riskData.containsKey(monitoredServiceIdentifier)).isEqualTo(true);
    assertThat(riskData.get(monitoredServiceIdentifier).getRiskStatus()).isEqualTo(Risk.HEALTHY);
    assertThat(riskData.get(monitoredServiceIdentifier).getHealthScore()).isEqualTo(75);

    assertThat(riskData.containsKey(newMonitoredServiceIdentifier)).isEqualTo(true);
    assertThat(riskData.get(newMonitoredServiceIdentifier).getRiskStatus()).isEqualTo(Risk.OBSERVE);
    assertThat(riskData.get(newMonitoredServiceIdentifier).getHealthScore()).isEqualTo(63);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLatestRiskScoreMultipleServiceEnvironmentMultipleCategory() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    HeatMap previousHeatmap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(previousHeatmap, heatMap.getHeatMapBucketStartTime(), 0.5, 0.5);
    hPersistence.save(previousHeatmap);

    HeatMap heatMapPerformanceCategory =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMapPerformanceCategory, endTime, 0.35, 0.65);
    hPersistence.save(heatMapPerformanceCategory);

    String newMonitoredServiceIdentifier = "newService";
    HeatMap anotherServiceEnvHeatMap = builderFactory.heatMapBuilder()
                                           .heatMapResolution(FIVE_MIN)
                                           .monitoredServiceIdentifier(newMonitoredServiceIdentifier)
                                           .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(anotherServiceEnvHeatMap, endTime, 0.11, 0.37);
    hPersistence.save(anotherServiceEnvHeatMap);

    Map<String, RiskData> riskData = heatMapService.getLatestRiskScoreByMonitoredService(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier, newMonitoredServiceIdentifier));

    assertThat(riskData.size()).isEqualTo(2);

    assertThat(riskData.containsKey(monitoredServiceIdentifier)).isEqualTo(true);
    assertThat(riskData.get(monitoredServiceIdentifier).getRiskStatus()).isEqualTo(Risk.NEED_ATTENTION);
    assertThat(riskData.get(monitoredServiceIdentifier).getHealthScore()).isEqualTo(35);

    assertThat(riskData.containsKey(newMonitoredServiceIdentifier)).isEqualTo(true);
    assertThat(riskData.get(newMonitoredServiceIdentifier).getRiskStatus()).isEqualTo(Risk.OBSERVE);
    assertThat(riskData.get(newMonitoredServiceIdentifier).getHealthScore()).isEqualTo(63);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetOverAllHealthScoreOneCategory4hrsDuration() {
    Instant endTime = clock.instant();
    createHeatMaps(endTime, FIVE_MIN, CVMonitoringCategory.ERRORS, 2);
    HistoricalTrend historicalTrend =
        heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
            cvConfig.getMonitoredServiceIdentifier(), DurationDTO.FOUR_HOURS, endTime);

    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    Instant time =
        getBoundaryOfResolution(endTime, FIVE_MIN.getResolution()).plusMillis(FIVE_MIN.getResolution().toMillis());
    for (int i = 47; i >= 0; i--) {
      RiskData riskData = historicalTrend.getHealthScores().get(i);
      assertThat(riskData.getEndTime()).isEqualTo(time.toEpochMilli());
      assertThat(riskData.getStartTime()).isEqualTo(time.minus(5, ChronoUnit.MINUTES).toEpochMilli());
      time = time.minus(5, ChronoUnit.MINUTES);
    }

    assertOverallHealthScoreWithInTwoHeatMap(historicalTrend);

    // Test for boundary condition
    endTime = getBoundaryOfResolution(endTime, FIVE_MIN.getBucketSize()).minus(1, ChronoUnit.MINUTES);
    historicalTrend = heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
        cvConfig.getMonitoredServiceIdentifier(), DurationDTO.FOUR_HOURS, endTime);
    assertOverallHealthScoreWithInOneHeatMap(historicalTrend);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetOverAllHealthScoreOneCategory4hrsDurationMultipleCategory() {
    Instant endTime = clock.instant();
    createHeatMaps(endTime, FIVE_MIN, CVMonitoringCategory.ERRORS, 2);
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    heatMaps.forEach(heatMap -> heatMap.getHeatMapRisks().forEach(heatMapRisk -> heatMapRisk.setRiskScore(0.0)));
    hPersistence.save(heatMaps);
    Instant timeToCheckFor = getBoundaryOfResolution(endTime, FIVE_MIN.getBucketSize()).minus(1, ChronoUnit.MINUTES);
    HistoricalTrend historicalTrend =
        heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
            cvConfig.getMonitoredServiceIdentifier(), DurationDTO.FOUR_HOURS, timeToCheckFor);

    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    for (int i = 47; i >= 23; i--) {
      assertThat(historicalTrend.getHealthScores().get(i).getHealthScore()).isEqualTo(100);
      assertThat(historicalTrend.getHealthScores().get(i).getRiskStatus()).isEqualTo(getHealthScoreRiskStatus(100));
    }

    createHeatMaps(endTime, FIVE_MIN, CVMonitoringCategory.PERFORMANCE, 2);
    historicalTrend = heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
        cvConfig.getMonitoredServiceIdentifier(), DurationDTO.FOUR_HOURS, timeToCheckFor);
    assertOverallHealthScoreWithInOneHeatMap(historicalTrend);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetOverAllHealthScoreOneCategory24hrsDuration() {
    Instant endTime = clock.instant();
    createHeatMaps(endTime, THIRTY_MINUTES, CVMonitoringCategory.ERRORS, 2);
    Instant timeToCheckFor = endTime.plus(2, ChronoUnit.HOURS);
    HistoricalTrend historicalTrend =
        heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
            cvConfig.getMonitoredServiceIdentifier(), DurationDTO.TWENTY_FOUR_HOURS, timeToCheckFor);
    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    Instant time = getBoundaryOfResolution(timeToCheckFor, THIRTY_MINUTES.getResolution())
                       .plusMillis(THIRTY_MINUTES.getResolution().toMillis());
    for (int i = 47; i >= 0; i--) {
      RiskData riskData = historicalTrend.getHealthScores().get(i);
      assertThat(riskData.getEndTime()).isEqualTo(time.toEpochMilli());
      assertThat(riskData.getStartTime()).isEqualTo(time.minus(30, ChronoUnit.MINUTES).toEpochMilli());
      time = time.minus(30, ChronoUnit.MINUTES);
    }
    assertOverallHealthScoreWithInTwoHeatMap(historicalTrend);

    // Test for boundary condition
    timeToCheckFor = getBoundaryOfResolution(endTime, THIRTY_MINUTES.getBucketSize()).minus(1, ChronoUnit.MINUTES);
    historicalTrend = heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
        cvConfig.getMonitoredServiceIdentifier(), DurationDTO.TWENTY_FOUR_HOURS, timeToCheckFor);
    assertOverallHealthScoreWithInOneHeatMap(historicalTrend);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetOverAllHealthScoreOneCategory3DaysDuration() {
    Instant endTime = clock.instant();
    createHeatMaps(endTime, THIRTY_MINUTES, CVMonitoringCategory.ERRORS, 4);
    Instant timeToCheckFor = endTime;
    HistoricalTrend historicalTrend =
        heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
            cvConfig.getMonitoredServiceIdentifier(), DurationDTO.THREE_DAYS, timeToCheckFor);

    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    Instant time = getBoundaryOfResolution(timeToCheckFor, THIRTY_MINUTES.getResolution())
                       .plusMillis(THIRTY_MINUTES.getResolution().toMillis());
    int healthScore = 79;
    for (int i = 47; i >= 0; i--) {
      RiskData riskData = historicalTrend.getHealthScores().get(i);
      assertThat(riskData.getEndTime()).isEqualTo(time.toEpochMilli());
      assertThat(riskData.getStartTime()).isEqualTo(time.minus(90, ChronoUnit.MINUTES).toEpochMilli());
      assertHealthScore(riskData, healthScore);
      healthScore += 3;
      if (healthScore == 100) {
        healthScore = 52;
      }
      time = time.minus(90, ChronoUnit.MINUTES);
    }

    // Test for boundary condition
    timeToCheckFor = getBoundaryOfResolution(endTime, THIRTY_MINUTES.getBucketSize()).minus(1, ChronoUnit.MINUTES);
    historicalTrend = heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
        cvConfig.getMonitoredServiceIdentifier(), DurationDTO.THREE_DAYS, timeToCheckFor);
    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);

    time = getBoundaryOfResolution(timeToCheckFor, THIRTY_MINUTES.getResolution())
               .plusMillis(THIRTY_MINUTES.getResolution().toMillis());
    healthScore = 52;
    for (int i = 47; i >= 0; i--) {
      RiskData riskData = historicalTrend.getHealthScores().get(i);
      assertThat(riskData.getEndTime()).isEqualTo(time.toEpochMilli());
      assertThat(riskData.getStartTime()).isEqualTo(time.minus(90, ChronoUnit.MINUTES).toEpochMilli());
      assertHealthScore(riskData, healthScore);
      healthScore += 3;
      if (healthScore == 100) {
        healthScore = 52;
      }
      time = time.minus(90, ChronoUnit.MINUTES);
    }
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetOverAllHealthScoreOneCategory7DaysDuration() {
    Instant endTime = clock.instant();
    createHeatMaps(endTime, THIRTY_MINUTES, CVMonitoringCategory.ERRORS, 8);
    Instant timeToCheckFor = endTime;
    HistoricalTrend historicalTrend =
        heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
            cvConfig.getMonitoredServiceIdentifier(), DurationDTO.SEVEN_DAYS, timeToCheckFor);

    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    Instant time = getBoundaryOfResolution(timeToCheckFor, THIRTY_MINUTES.getResolution())
                       .plusMillis(THIRTY_MINUTES.getResolution().toMillis());

    for (int i = 47; i >= 0; i--) {
      RiskData riskData = historicalTrend.getHealthScores().get(i);
      assertThat(riskData.getEndTime()).isEqualTo(time.toEpochMilli());
      assertThat(riskData.getStartTime()).isEqualTo(time.minus(210, ChronoUnit.MINUTES).toEpochMilli());
      time = time.minus(210, ChronoUnit.MINUTES);
    }
    assertHealthScore(historicalTrend.getHealthScores().get(0), 72);
    assertHealthScore(historicalTrend.getHealthScores().get(47), 79);
    assertHealthScore(historicalTrend.getHealthScores().get(39), 87);
    assertHealthScore(historicalTrend.getHealthScores().get(38), 52);
    assertHealthScore(historicalTrend.getHealthScores().get(40), 80);

    // Test for boundary condition
    timeToCheckFor = getBoundaryOfResolution(endTime, THIRTY_MINUTES.getBucketSize()).minus(1, ChronoUnit.MINUTES);
    historicalTrend = heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
        cvConfig.getMonitoredServiceIdentifier(), DurationDTO.SEVEN_DAYS, timeToCheckFor);
    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    time = getBoundaryOfResolution(timeToCheckFor, THIRTY_MINUTES.getResolution())
               .plusMillis(THIRTY_MINUTES.getResolution().toMillis());
    for (int i = 47; i >= 0; i--) {
      RiskData riskData = historicalTrend.getHealthScores().get(i);
      assertThat(riskData.getEndTime()).isEqualTo(time.toEpochMilli());
      assertThat(riskData.getStartTime()).isEqualTo(time.minus(210, ChronoUnit.MINUTES).toEpochMilli());
      time = time.minus(210, ChronoUnit.MINUTES);
    }
    assertHealthScore(historicalTrend.getHealthScores().get(0), 93);
    assertHealthScore(historicalTrend.getHealthScores().get(47), 52);
    assertHealthScore(historicalTrend.getHealthScores().get(42), 87);
    assertHealthScore(historicalTrend.getHealthScores().get(41), 52);
    assertHealthScore(historicalTrend.getHealthScores().get(43), 80);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetOverAllHealthScoreOneCategory30DaysDuration() {
    Instant endTime = clock.instant();
    createHeatMaps(endTime, THIRTY_MINUTES, CVMonitoringCategory.ERRORS, 31);
    Instant timeToCheckFor = endTime;
    HistoricalTrend historicalTrend =
        heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
            cvConfig.getMonitoredServiceIdentifier(), DurationDTO.THIRTY_DAYS, timeToCheckFor);
    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    Instant time = getBoundaryOfResolution(timeToCheckFor, THIRTY_MINUTES.getResolution())
                       .plusMillis(THIRTY_MINUTES.getResolution().toMillis());
    for (int i = 47; i >= 0; i--) {
      RiskData riskData = historicalTrend.getHealthScores().get(i);
      assertThat(riskData.getEndTime()).isEqualTo(time.toEpochMilli());
      assertThat(riskData.getStartTime()).isEqualTo(time.minus(15, ChronoUnit.HOURS).toEpochMilli());
      time = time.minus(15, ChronoUnit.HOURS);
    }
    assertHealthScore(historicalTrend.getHealthScores().get(0), 52);
    assertHealthScore(historicalTrend.getHealthScores().get(47), 52);
    assertHealthScore(historicalTrend.getHealthScores().get(35), 55);

    // Test for boundary condition
    historicalTrend = heatMapService.getOverAllHealthScore(builderFactory.getContext().getProjectParams(),
        cvConfig.getMonitoredServiceIdentifier(), DurationDTO.THIRTY_DAYS, timeToCheckFor);
    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    time = getBoundaryOfResolution(timeToCheckFor, THIRTY_MINUTES.getResolution())
               .plusMillis(THIRTY_MINUTES.getResolution().toMillis());
    for (int i = 47; i >= 0; i--) {
      RiskData riskData = historicalTrend.getHealthScores().get(i);
      assertThat(riskData.getEndTime()).isEqualTo(time.toEpochMilli());
      assertThat(riskData.getStartTime()).isEqualTo(time.minus(15, ChronoUnit.HOURS).toEpochMilli());
      time = time.minus(15, ChronoUnit.HOURS);
    }
    assertHealthScore(historicalTrend.getHealthScores().get(0), 52);
    assertHealthScore(historicalTrend.getHealthScores().get(47), 52);
    assertHealthScore(historicalTrend.getHealthScores().get(46), 61);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLatestRiskScoreByServiceMap() {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.80, 0.75);
    hPersistence.save(heatMap);
    Map<String, RiskData> latestRiskScoreByServiceMap =
        heatMapService.getLatestRiskScoreByMonitoredService(projectParams, Arrays.asList(monitoredServiceIdentifier));
    assertThat(latestRiskScoreByServiceMap.keySet().size()).isEqualTo(1);
    assertThat(latestRiskScoreByServiceMap.get(monitoredServiceIdentifier).getHealthScore()).isEqualTo(25);
    assertThat(latestRiskScoreByServiceMap.get(monitoredServiceIdentifier).getRiskStatus())
        .isEqualTo(Risk.NEED_ATTENTION);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLatestHealthScoreWithHeatMapHavingNoHeatMapRiskAttached() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    Instant startTime = endTime.minus(4, ChronoUnit.HOURS);
    HeatMap heatMap = builderFactory.heatMapBuilder()
                          .heatMapResolution(FIVE_MIN)
                          .heatMapBucketStartTime(startTime)
                          .heatMapBucketEndTime(endTime)
                          .heatMapRisks(Arrays.asList(HeatMapRisk.builder()
                                                          .riskScore(-1)
                                                          .startTime(endTime.minus(5, ChronoUnit.MINUTES))
                                                          .endTime(endTime)
                                                          .build()))
                          .build();
    hPersistence.save(heatMap);

    Map<String, RiskData> riskDataMap = heatMapService.getLatestHealthScore(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier));
    assertThat(riskDataMap.isEmpty()).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLatestHealthScoreWithMonitoredServiceIdentifiers_WithHeatMapHavingNoHeatMapRiskAttached() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    Instant startTime = endTime.minus(4, ChronoUnit.HOURS);
    HeatMap heatMap = builderFactory.heatMapBuilder()
                          .heatMapResolution(FIVE_MIN)
                          .heatMapBucketStartTime(startTime)
                          .heatMapBucketEndTime(endTime)
                          .heatMapRisks(Arrays.asList(HeatMapRisk.builder()
                                                          .riskScore(-1)
                                                          .startTime(endTime.minus(5, ChronoUnit.MINUTES))
                                                          .endTime(endTime)
                                                          .build()))
                          .build();
    hPersistence.save(heatMap);

    Map<String, RiskData> riskDataMap = heatMapService.getLatestHealthScore(
        builderFactory.getProjectParams(), Arrays.asList(monitoredServiceIdentifier));
    assertThat(riskDataMap.isEmpty()).isEqualTo(true);
  }

  private void assertHealthScore(RiskData riskData, int val) {
    assertThat(riskData.getHealthScore()).isEqualTo(val);
    assertThat(getHealthScoreRiskStatus(riskData.getHealthScore())).isEqualTo(riskData.getRiskStatus());
  }

  private void assertOverallHealthScoreWithInTwoHeatMap(HistoricalTrend historicalTrend) {
    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    int healthScore = 75;
    for (int i = 47; i >= 23; i--) {
      assertThat(historicalTrend.getHealthScores().get(i).getHealthScore()).isEqualTo(healthScore);
      assertThat(historicalTrend.getHealthScores().get(i).getRiskStatus())
          .isEqualTo(getHealthScoreRiskStatus(healthScore));
      healthScore++;
    }
    healthScore = 52;
    for (int i = 22; i >= 0; i--) {
      assertThat(historicalTrend.getHealthScores().get(i).getHealthScore()).isEqualTo(healthScore);
      assertThat(historicalTrend.getHealthScores().get(i).getRiskStatus())
          .isEqualTo(getHealthScoreRiskStatus(healthScore));
      healthScore++;
    }
  }

  private void assertOverallHealthScoreWithInOneHeatMap(HistoricalTrend historicalTrend) {
    assertThat(historicalTrend.getHealthScores().size()).isEqualTo(48);
    int healthScore = 52;
    for (int i = 47; i >= 0; i--) {
      assertThat(historicalTrend.getHealthScores().get(i).getHealthScore()).isEqualTo(healthScore);
      assertThat(historicalTrend.getHealthScores().get(i).getRiskStatus())
          .isEqualTo(getHealthScoreRiskStatus(healthScore));
      healthScore++;
    }
  }

  private Risk getHealthScoreRiskStatus(int healthScore) {
    if (healthScore >= 75) {
      return Risk.HEALTHY;
    } else if (healthScore >= 50) {
      return Risk.OBSERVE;
    } else if (healthScore >= 25) {
      return Risk.NEED_ATTENTION;
    } else {
      return Risk.UNHEALTHY;
    }
  }

  private void createHeatMaps(
      Instant endTime, HeatMapResolution heatMapResolution, CVMonitoringCategory category, int numberOfHeatMaps) {
    for (int i = 0; i < numberOfHeatMaps; i++) {
      endTime = getBoundaryOfResolution(endTime, heatMapResolution.getBucketSize())
                    .plusMillis(heatMapResolution.getBucketSize().toMillis());
      Instant startTime = endTime.minus(heatMapResolution.getBucketSize());
      HeatMap heatMap = builderFactory.heatMapBuilder()
                            .heatMapResolution(heatMapResolution)
                            .category(category)
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
      endTime = startTime.minus(1, ChronoUnit.MINUTES);
    }
  }

  private void setStartTimeEndTimeAndRiskScoreWith30MinBucket(HeatMap heatMap, Instant endTime, double riskScore) {
    Instant startTime = endTime.minus(24, ChronoUnit.HOURS);
    heatMap.setHeatMapBucketStartTime(startTime);
    heatMap.setHeatMapBucketEndTime(endTime);
    List<HeatMapRisk> heatMapRisks = new ArrayList<>();

    for (Instant time = startTime; time.isBefore(endTime); time = time.plus(30, ChronoUnit.MINUTES)) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore(riskScore)
                           .startTime(time)
                           .endTime(time.plus(30, ChronoUnit.MINUTES))
                           .build());
    }
    heatMap.setHeatMapRisks(heatMapRisks);
  }

  private void setStartTimeEndTimeAndRiskScoreWith5MinBucket(
      HeatMap heatMap, Instant endTime, double firstHalfRiskScore, double secondHalfRiskScore) {
    Instant startTime = endTime.minus(4, ChronoUnit.HOURS);
    heatMap.setHeatMapBucketStartTime(startTime);
    heatMap.setHeatMapBucketEndTime(endTime);
    List<HeatMapRisk> heatMapRisks = new ArrayList<>();

    for (Instant time = startTime; time.isBefore(startTime.plus(2, ChronoUnit.HOURS));
         time = time.plus(5, ChronoUnit.MINUTES)) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore(firstHalfRiskScore)
                           .startTime(time)
                           .endTime(time.plus(5, ChronoUnit.MINUTES))
                           .build());
    }
    for (Instant time = startTime.plus(2, ChronoUnit.HOURS); time.isBefore(endTime);
         time = time.plus(5, ChronoUnit.MINUTES)) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore(secondHalfRiskScore)
                           .startTime(time)
                           .endTime(time.plus(5, ChronoUnit.MINUTES))
                           .build());
    }
    heatMap.setHeatMapRisks(heatMapRisks);
  }

  private Instant getBoundaryOfResolution(Instant input, Duration resolution) {
    long timeStamp = input.toEpochMilli();
    return Instant.ofEpochMilli(timeStamp - (timeStamp % resolution.toMillis()));
  }
}
