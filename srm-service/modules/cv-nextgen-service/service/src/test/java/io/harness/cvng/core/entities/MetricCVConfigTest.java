/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.NEMANJA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.models.VerificationType;
import io.harness.rule.Owner;

import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MetricCVConfigTest extends CategoryTest {
  private String accountId;
  private String connectorId;
  private String productName;
  private String groupId;

  @Before
  public void setup() {
    this.accountId = generateUuid();
    this.connectorId = generateUuid();
    this.productName = generateUuid();
    this.groupId = generateUuid();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testValidateParams_whenMetricPackIsUndefined() {
    MetricCVConfig metricCVConfig = createCVConfig();
    assertThatThrownBy(() -> metricCVConfig.validate())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("metricPack should not be null");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetFirstTimeDataCollectionTimeRange_basedOnCreatedAt() {
    MetricCVConfig metricCVConfig = createCVConfig();
    metricCVConfig.setCreatedAt(Instant.parse("2020-04-22T10:02:06Z").toEpochMilli());
    assertThat(metricCVConfig.getFirstTimeDataCollectionTimeRange())
        .isEqualTo(TimeRange.builder()
                       .endTime(Instant.parse("2020-04-22T10:00:00Z"))
                       .startTime(Instant.parse("2020-04-22T07:45:00Z"))
                       .build());
  }

  private MetricCVConfig createCVConfig() {
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    fillCommon(cvConfig);
    cvConfig.setApplicationName("Application Name");
    cvConfig.setTierName("Tier Name");
    return cvConfig;
  }

  private void fillCommon(MetricCVConfig cvConfig) {
    cvConfig.setVerificationType(VerificationType.LOG);
    cvConfig.setAccountId(accountId);
    cvConfig.setConnectorIdentifier(connectorId);
    cvConfig.setMonitoredServiceIdentifier(generateUuid());
    cvConfig.setProjectIdentifier(generateUuid());
    cvConfig.setIdentifier(groupId);
    cvConfig.setMonitoringSourceName(generateUuid());
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    cvConfig.setProductName(productName);
  }
}
