/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.services.CVNextGenConstants.ERRORS_PACK_IDENTIFIER;
import static io.harness.cvng.core.services.CVNextGenConstants.INFRASTRUCTURE_PACK_IDENTIFIER;
import static io.harness.cvng.core.services.CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.rule.OwnerRule.VUK;
import static io.harness.rule.TestUserProvider.testUserProvider;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.TimeSeriesCustomThresholdActions;
import io.harness.cvng.beans.TimeSeriesThresholdActionType;
import io.harness.cvng.beans.TimeSeriesThresholdComparisonType;
import io.harness.cvng.beans.TimeSeriesThresholdCriteria;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.MetricPack.MetricDefinition;
import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.TimeSeriesThresholdService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MetricPackServiceImplTest extends CvNextGenTestBase {
  @Inject private MetricPackService metricPackService;
  @Inject private TimeSeriesThresholdService timeSeriesThresholdService;
  @Inject private HPersistence hPersistence;
  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;

  @Before
  public void setup() {
    accountId = generateUuid();
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    testUserProvider.setActiveUser(EmbeddedUser.builder().name("user1").build());
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testMetricPackFilesAdded() {
    final String metricPackUrl = getSourceResourceFile(MetricPackService.class, "/appdynamics/metric-packs");
    final Collection<File> metricPackYamls = FileUtils.listFiles(new File(metricPackUrl), null, false);
    assertThat(metricPackYamls.size()).isEqualTo(MetricPackServiceImpl.APPDYNAMICS_METRICPACK_FILES.size() + 1);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetMetricPacks() {
    final Collection<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);
    assertThat(metricPacks.size()).isGreaterThan(0);
    metricPacks.forEach(metricPack -> {
      assertThat(metricPack.getUuid()).isNotEmpty();
      assertThat(metricPack.getAccountId()).isEqualTo(accountId);
      assertThat(metricPack.getProjectIdentifier()).isEqualTo(projectIdentifier);
      assertThat(metricPack.getIdentifier()).isNotEmpty();
      assertThat(metricPack.getDataSourceType()).isEqualTo(DataSourceType.APP_DYNAMICS);
      assertThat(metricPack.getMetrics().size()).isGreaterThan(0);
      assertThat(metricPack.getCategory()).isNotNull();
      metricPack.getMetrics().forEach(metricDefinition -> {
        assertThat(metricDefinition.getName()).isNotEmpty();
        assertThat(metricDefinition.getPath()).isNotEmpty();
      });
    });
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetMetricPacksDTO() {
    final List<MetricPackDTO> metricPacks =
        metricPackService.getMetricPacks(DataSourceType.APP_DYNAMICS, accountId, orgIdentifier, projectIdentifier);
    assertThat(metricPacks.size()).isGreaterThan(0);
    metricPacks.forEach(metricPack -> {
      assertThat(metricPack.getUuid()).isNotEmpty();
      assertThat(metricPack.getAccountId()).isEqualTo(accountId);
      assertThat(metricPack.getProjectIdentifier()).isEqualTo(projectIdentifier);
      assertThat(metricPack.getIdentifier()).isNotEmpty();
      assertThat(metricPack.getDataSourceType()).isEqualTo(DataSourceType.APP_DYNAMICS);
      assertThat(metricPack.getMetrics().size()).isGreaterThan(0);
      assertThat(metricPack.getCategory()).isNotNull();
      metricPack.getMetrics().forEach(metricDefinition -> {
        assertThat(metricDefinition.getName()).isNotEmpty();
        assertThat(metricDefinition.getPath()).isNotEmpty();
      });
    });
    // Validate APPD Custom metric pack is hidden
    assertThat(metricPacks.stream()
                   .filter(metricPack -> metricPack.getIdentifier().equals(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER))
                   .filter(metricPack -> metricPack.getDataSourceType().equals(DataSourceType.APP_DYNAMICS)))
        .hasSize(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetMetricPack_withExistingCategory() {
    MetricPack metricPack = metricPackService.getMetricPack(
        accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS, ERRORS_PACK_IDENTIFIER);
    assertThat(metricPack.getAccountId()).isEqualTo(accountId);
    assertThat(metricPack.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(metricPack.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(metricPack.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
    assertThat(metricPack.getMetrics().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveMetricPacks() {
    Collection<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);
    List<MetricPack> performancePacks =
        metricPacks.stream()
            .filter(metricPack -> metricPack.getIdentifier().equals(PERFORMANCE_PACK_IDENTIFIER))
            .collect(Collectors.toList());
    assertThat(performancePacks.size()).isEqualTo(1);
    MetricPack performancePack = performancePacks.get(0);

    int performancePackSize = performancePack.getMetrics().size();
    performancePack.getMetrics().forEach(metric -> {
      metric.setIncluded(true);
      metric.setPath(null);
      metric.setValidationPath(null);
    });

    final boolean saved = metricPackService.saveMetricPacks(
        accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS, Lists.newArrayList(performancePack));
    assertThat(saved).isTrue();

    metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);
    assertThat(metricPacks.size()).isGreaterThan(1);

    performancePacks = metricPacks.stream()
                           .filter(metricPack -> metricPack.getIdentifier().equals(PERFORMANCE_PACK_IDENTIFIER))
                           .collect(Collectors.toList());
    assertThat(performancePacks.size()).isEqualTo(1);
    performancePack = performancePacks.get(0);

    assertThat(performancePack.getMetrics().size()).isEqualTo(performancePackSize);
    assertThat(performancePack.getMetrics()).contains(MetricDefinition.builder().name("Number of Slow Calls").build());
    performancePack.getMetrics().forEach(metricDefinition -> assertThat(metricDefinition.isIncluded()).isTrue());

    performancePack.getMetrics().forEach(metric -> assertThat(metric.getPath()).isNotEmpty());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testPopulateValidationPaths() {
    Collection<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);
    MetricPack qualityPack = metricPacks.stream()
                                 .filter(metricPack -> metricPack.getIdentifier().equals(ERRORS_PACK_IDENTIFIER))
                                 .findFirst()
                                 .orElseThrow(() -> new IllegalArgumentException("invalid pack name"));

    qualityPack.getMetrics().forEach(metricDefinition -> {
      assertThat(metricDefinition.getValidationPath()).isNotEmpty();
      metricDefinition.setValidationPath(null);
    });

    assertThat(qualityPack.getDataCollectionDsl()).isNull();
    metricPackService.populatePaths(
        accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS, qualityPack);
    qualityPack.getMetrics().forEach(metricDefinition -> assertThat(metricDefinition.getValidationPath()).isNotEmpty());
    assertThat(qualityPack.getDataCollectionDsl()).isEqualTo(MetricPackServiceImpl.APPDYNAMICS_QUALITY_PACK_DSL);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetMetricPackThresholds() {
    final List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);
    metricPacks.forEach(metricPack -> {
      List<TimeSeriesThreshold> metricPackThresholds = metricPackService.getMetricPackThresholds(
          accountId, orgIdentifier, projectIdentifier, metricPack.getIdentifier(), DataSourceType.APP_DYNAMICS);
      assertThat(metricPackThresholds).isNotEmpty();
      metricPack.getMetrics().forEach(metricDefinition -> {
        final List<TimeSeriesThreshold> thresholds =
            metricPackThresholds.stream()
                .filter(timeSeriesThreshold -> timeSeriesThreshold.getMetricName().equals(metricDefinition.getName()))
                .collect(Collectors.toList());
        assertThat(thresholds).isNotEmpty();
      });
    });
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveMetricPackThresholds() {
    final List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);
    final MetricPack metricPack = metricPacks.get(0);
    List<TimeSeriesThreshold> timeSeriesThresholds = Lists.newArrayList(
        TimeSeriesThreshold.builder()
            .metricPackIdentifier(metricPack.getIdentifier())
            .metricName("metric1")
            .action(TimeSeriesThresholdActionType.FAIL)
            .criteria(TimeSeriesThresholdCriteria.builder()
                          .criteria("  > 20.0")
                          .type(TimeSeriesThresholdComparisonType.DELTA)
                          .occurrenceCount(3)
                          .action(TimeSeriesCustomThresholdActions.FAIL_AFTER_CONSECUTIVE_OCCURRENCES)
                          .build())
            .build(),
        TimeSeriesThreshold.builder()
            .metricPackIdentifier(metricPack.getIdentifier())
            .metricName("metric1")
            .action(TimeSeriesThresholdActionType.IGNORE)
            .criteria(TimeSeriesThresholdCriteria.builder()
                          .criteria("  < 0.5")
                          .type(TimeSeriesThresholdComparisonType.RATIO)
                          .build())
            .build());
    timeSeriesThresholdService.saveMetricPackThreshold(
        accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS, timeSeriesThresholds);

    List<TimeSeriesThreshold> metricPackThresholds = metricPackService.getMetricPackThresholds(
        accountId, orgIdentifier, projectIdentifier, metricPack.getIdentifier(), DataSourceType.APP_DYNAMICS);
    int defaultThresholdsSize = 2;
    assertThat(metricPackThresholds.size()).isEqualTo(timeSeriesThresholds.size() + defaultThresholdsSize);
    TimeSeriesThreshold timeSeriesThreshold = metricPackThresholds.get(2);
    assertThat(timeSeriesThreshold.getAccountId()).isEqualTo(accountId);
    assertThat(timeSeriesThreshold.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(timeSeriesThreshold.getMetricPackIdentifier()).isEqualTo(metricPack.getIdentifier());
    assertThat(timeSeriesThreshold.getDataSourceType()).isEqualTo(DataSourceType.APP_DYNAMICS);
    assertThat(timeSeriesThreshold.getAction()).isEqualTo(TimeSeriesThresholdActionType.FAIL);
    assertThat(timeSeriesThreshold.getCriteria().getType()).isEqualTo(TimeSeriesThresholdComparisonType.DELTA);
    assertThat(timeSeriesThreshold.getCriteria().getAction())
        .isEqualTo(TimeSeriesCustomThresholdActions.FAIL_AFTER_CONSECUTIVE_OCCURRENCES);
    assertThat(timeSeriesThreshold.getCriteria().getOccurrenceCount()).isEqualTo(3);
    assertThat(timeSeriesThreshold.getCriteria().getThresholdType()).isEqualTo(TimeSeriesThresholdType.ACT_WHEN_HIGHER);

    timeSeriesThreshold = metricPackThresholds.get(3);
    assertThat(timeSeriesThreshold.getAccountId()).isEqualTo(accountId);
    assertThat(timeSeriesThreshold.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(timeSeriesThreshold.getMetricPackIdentifier()).isEqualTo(metricPack.getIdentifier());
    assertThat(timeSeriesThreshold.getDataSourceType()).isEqualTo(DataSourceType.APP_DYNAMICS);
    assertThat(timeSeriesThreshold.getAction()).isEqualTo(TimeSeriesThresholdActionType.IGNORE);
    assertThat(timeSeriesThreshold.getCriteria().getType()).isEqualTo(TimeSeriesThresholdComparisonType.RATIO);
    assertThat(timeSeriesThreshold.getCriteria().getAction()).isNull();
    assertThat(timeSeriesThreshold.getCriteria().getOccurrenceCount()).isNull();
    assertThat(timeSeriesThreshold.getCriteria().getThresholdType()).isEqualTo(TimeSeriesThresholdType.ACT_WHEN_LOWER);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testCreateDefaultMetricPackAndThresholds() {
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);
    assertThat(metricPacks).isNotEmpty();

    List<MetricPack> performancePacks =
        metricPacks.stream()
            .filter(metricPack -> metricPack.getIdentifier().equals(PERFORMANCE_PACK_IDENTIFIER))
            .collect(Collectors.toList());
    assertThat(performancePacks.size()).isEqualTo(1);

    List<TimeSeriesThreshold> metricPackThresholds = metricPackService.getMetricPackThresholds(
        accountId, orgIdentifier, projectIdentifier, metricPacks.get(0).getIdentifier(), DataSourceType.APP_DYNAMICS);

    assertThat(metricPackThresholds).isNotEmpty();
    assertThat(metricPackThresholds).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateDefaultMetricPackAndThresholds_Stackdriver() {
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.STACKDRIVER);
    assertThat(metricPacks).isNotEmpty();

    List<MetricPack> infraPacks =
        metricPacks.stream()
            .filter(metricPack -> metricPack.getIdentifier().equals(INFRASTRUCTURE_PACK_IDENTIFIER))
            .collect(Collectors.toList());
    assertThat(infraPacks.size()).isEqualTo(1);

    List<TimeSeriesThreshold> metricPackThresholds = metricPackService.getMetricPackThresholds(
        accountId, orgIdentifier, projectIdentifier, infraPacks.get(0).getIdentifier(), DataSourceType.STACKDRIVER);

    assertThat(metricPackThresholds).isNotEmpty();
    assertThat(metricPackThresholds).size().isEqualTo(2);

    List<MetricPack> performancePacks =
        metricPacks.stream()
            .filter(metricPack -> metricPack.getIdentifier().equals(PERFORMANCE_PACK_IDENTIFIER))
            .collect(Collectors.toList());
    assertThat(performancePacks.size()).isEqualTo(1);

    metricPackThresholds = metricPackService.getMetricPackThresholds(accountId, orgIdentifier, projectIdentifier,
        performancePacks.get(0).getIdentifier(), DataSourceType.STACKDRIVER);

    assertThat(metricPackThresholds).isNotEmpty();
    assertThat(metricPackThresholds).size().isEqualTo(4);

    List<MetricPack> errorPacks = metricPacks.stream()
                                      .filter(metricPack -> metricPack.getIdentifier().equals(ERRORS_PACK_IDENTIFIER))
                                      .collect(Collectors.toList());
    assertThat(errorPacks.size()).isEqualTo(1);

    metricPackThresholds = metricPackService.getMetricPackThresholds(
        accountId, orgIdentifier, projectIdentifier, errorPacks.get(0).getIdentifier(), DataSourceType.STACKDRIVER);

    assertThat(metricPackThresholds).isNotEmpty();
    assertThat(metricPackThresholds).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateDefaultMetricPackAndThresholds_NewRelic() {
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.NEW_RELIC);
    assertThat(metricPacks).isNotEmpty();

    List<MetricPack> performancePacks =
        metricPacks.stream()
            .filter(metricPack -> metricPack.getIdentifier().equals(PERFORMANCE_PACK_IDENTIFIER))
            .collect(Collectors.toList());
    assertThat(performancePacks.size()).isEqualTo(1);

    List<TimeSeriesThreshold> metricPackThresholds = metricPackService.getMetricPackThresholds(
        accountId, orgIdentifier, projectIdentifier, performancePacks.get(0).getIdentifier(), DataSourceType.NEW_RELIC);

    assertThat(metricPackThresholds).isNotEmpty();
    assertThat(metricPackThresholds).size().isEqualTo(6);
  }
}
