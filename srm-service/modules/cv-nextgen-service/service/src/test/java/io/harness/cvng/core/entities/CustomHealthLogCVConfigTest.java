/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthLogCVConfigTest extends CategoryTest {
  CustomHealthLogCVConfig customHealthCVConfig;
  String accountId = "1234_accountId";
  String projectId = "1234_projectId";
  String orgId = "1234_orgId";
  String connectorId = "1234_connectorId";

  @Before
  public void setup() {
    customHealthCVConfig = CustomHealthLogCVConfig.builder()
                               .category(CVMonitoringCategory.PERFORMANCE)
                               .accountId(accountId)
                               .projectIdentifier(projectId)
                               .orgIdentifier(orgId)
                               .connectorIdentifier(connectorId)
                               .query("error*")
                               .queryName("Error Query")
                               .timestampJsonPath("$.[0].timestamp")
                               .logMessageJsonPath("$.[0].metricValue")
                               .serviceInstanceJsonPath("$.[0].serviceInstanceJSON")
                               .requestDefinition(CustomHealthRequestDefinition.builder()
                                                      .method(CustomHealthMethod.GET)
                                                      .endTimeInfo(TimestampInfo.builder().build())
                                                      .startTimeInfo(TimestampInfo.builder().build())
                                                      .urlPath("https://url.com")
                                                      .build())
                               .build();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testValidateParams_whenQueryDefinitionIsNull() {
    customHealthCVConfig.setRequestDefinition(null);
    assertThatThrownBy(customHealthCVConfig::validateParams)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("requestDefinition should not be null");
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testValidateParams_whenTimestampJSONPathIsNull() {
    customHealthCVConfig.setTimestampJsonPath(null);
    assertThatThrownBy(customHealthCVConfig::validateParams)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("timestampJsonPath should not be null");
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testValidateParams_whenURLIsNull() {
    customHealthCVConfig.getRequestDefinition().setUrlPath(null);
    assertThatThrownBy(customHealthCVConfig::validateParams)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("urlPath should not be null");
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testValidateParams_whenEndTimeInfoIsNull() {
    customHealthCVConfig.getRequestDefinition().setEndTimeInfo(null);
    assertThatThrownBy(customHealthCVConfig::validateParams)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("endTimeInfo should not be null");
  }
}
