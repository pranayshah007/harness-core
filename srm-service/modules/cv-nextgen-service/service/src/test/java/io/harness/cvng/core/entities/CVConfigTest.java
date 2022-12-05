/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NEMANJA;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.models.VerificationType;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVConfigTest extends CategoryTest {
  private String accountId;
  private String connectorIdentifier;
  private String productName;
  private String groupId;
  private String serviceInstanceIdentifier;

  @Before
  public void setup() {
    this.accountId = generateUuid();
    this.connectorIdentifier = generateUuid();
    this.productName = generateUuid();
    this.groupId = generateUuid();
    this.serviceInstanceIdentifier = generateUuid();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testValidate_whenAccountIdIsUndefined() {
    CVConfig cvConfig = createCVConfig();
    cvConfig.setAccountId(null);
    assertThatThrownBy(() -> cvConfig.validate())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("accountId should not be null");
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testValidate_whenConnectorIdIsUndefined() {
    CVConfig cvConfig = createCVConfig();
    cvConfig.setConnectorIdentifier(null);
    assertThatThrownBy(() -> cvConfig.validate())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("connectorIdentifier should not be null");
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testValidate_whenMonitoredServiceIdentifierIsUndefined() {
    CVConfig cvConfig = createCVConfig();
    cvConfig.setMonitoredServiceIdentifier(null);
    assertThatThrownBy(() -> cvConfig.validate())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("monitoredServiceIdentifier should not be null");
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testValidate_whenProjectIdentifierIsUndefined() {
    CVConfig cvConfig = createCVConfig();
    cvConfig.setProjectIdentifier(null);
    assertThatThrownBy(() -> cvConfig.validate())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("projectIdentifier should not be null");
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testValidate_whenGroupIdIsUndefined() {
    CVConfig cvConfig = createCVConfig();
    cvConfig.setIdentifier(null);
    assertThatThrownBy(() -> cvConfig.validate())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("identifier should not be null");
  }

  private CVConfig createCVConfig() {
    SplunkCVConfig cvConfig = new SplunkCVConfig();
    fillCommon(cvConfig);
    cvConfig.setQuery("exception");
    cvConfig.setServiceInstanceIdentifier(serviceInstanceIdentifier);
    return cvConfig;
  }

  private void fillCommon(CVConfig cvConfig) {
    cvConfig.setVerificationType(VerificationType.LOG);
    cvConfig.setAccountId(accountId);
    cvConfig.setConnectorIdentifier(connectorIdentifier);
    cvConfig.setProjectIdentifier(generateUuid());
    cvConfig.setIdentifier(groupId);
    cvConfig.setMonitoredServiceIdentifier(generateUuid());
    cvConfig.setMonitoringSourceName(generateUuid());
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    cvConfig.setProductName(productName);
  }
}
