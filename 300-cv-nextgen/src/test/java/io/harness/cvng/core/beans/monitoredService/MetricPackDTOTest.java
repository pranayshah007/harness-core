/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class MetricPackDTOTest extends CvNextGenTestBase {
  TimeSeriesMetricPackDTO metricPackDTO;
  @Inject MetricPackService metricPackService;
  String orgIdentifier;
  String projectIdentifier;
  String accountId;

  @Before
  public void setup() {
    orgIdentifier = "org";
    projectIdentifier = "project";
    accountId = generateUuid();
    metricPackDTO = TimeSeriesMetricPackDTO.builder().identifier(CVNextGenConstants.ERRORS_PACK_IDENTIFIER).build();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testToMetricPack() {
    MetricPack metricPack = metricPackDTO.toMetricPack(
        accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS, metricPackService);
    assertThat(metricPack.getAccountId()).isEqualTo(accountId);
    assertThat(metricPack.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(metricPack.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(metricPack.getIdentifier()).isEqualTo(CVMonitoringCategory.ERRORS.getDisplayName());
    assertThat(metricPack.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
    assertThat(metricPack.getMetrics().size()).isEqualTo(1);
    assertThat(metricPack.getDataSourceType()).isEqualTo(DataSourceType.APP_DYNAMICS);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testToMetricPackDTO() {
    MetricPack metricPack = createMetricPack();
    TimeSeriesMetricPackDTO metricPackDTO = TimeSeriesMetricPackDTO.toMetricPackDTO(metricPack);
    assertThat(metricPackDTO.getIdentifier()).isEqualTo(metricPack.getIdentifier());
  }

  private MetricPack createMetricPack() {
    return MetricPack.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(CVMonitoringCategory.ERRORS.getDisplayName())
        .category(CVMonitoringCategory.ERRORS)
        .dataSourceType(DataSourceType.APP_DYNAMICS)
        .build();
  }
}
