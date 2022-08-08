/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANGELO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.ErrorTrackingHealthSourceSpec;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ErrorTrackingSourceSpecTransformerTest extends CvNextGenTestBase {
  private static String PRODUCT_NAME = "ErrorTracking";

  String envIdentifier;
  String connectorIdentifier;
  String productName;
  String projectIdentifier;
  String accountId;
  String identifier;
  String monitoringSourceName;
  String serviceIdentifier;
  String serviceInstanceIdentifier;
  private BuilderFactory builderFactory;

  @Inject ErrorTrackingHealthSourceSpecTransformer errorTrackingHealthSourceSpecTransformer;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    envIdentifier = "env";
    connectorIdentifier = "connectorId";
    productName = "Application Monitoring";
    projectIdentifier = "projectId";
    accountId = generateUuid();
    identifier = "healthSourceIdentifier";
    monitoringSourceName = "StackdriverLog";
    serviceIdentifier = "serviceId";
    serviceInstanceIdentifier = "serviceInstanceIdentifier";
  }

  @Test
  @Owner(developers = ANGELO)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    ErrorTrackingCVConfig cvConfig = createCVConfig();
    ArrayList<ErrorTrackingCVConfig> configs = new ArrayList<>();
    configs.add(cvConfig);
    ErrorTrackingHealthSourceSpec errorTrackingHealthSourceSpec =
        errorTrackingHealthSourceSpecTransformer.transformToHealthSourceConfig(configs);
    assertThat(errorTrackingHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(errorTrackingHealthSourceSpec.getType()).isEqualTo(DataSourceType.ERROR_TRACKING);
  }

  @Test
  @Owner(developers = ANGELO)
  @Category(UnitTests.class)
  public void transformToHealthSourceConfig_onDistinctProductName() {
    ErrorTrackingCVConfig cvConfig = createCVConfig();
    List<ErrorTrackingCVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(cvConfig);
    cvConfigs.add(cvConfig);
    Assertions
        .assertThatThrownBy(() -> errorTrackingHealthSourceSpecTransformer.transformToHealthSourceConfig(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Multiple configs not supported.");
  }

  private ErrorTrackingCVConfig createCVConfig() {
    return (ErrorTrackingCVConfig) builderFactory.errorTrackingCVConfigBuilder()
        .queryName(PRODUCT_NAME)
        .query(serviceIdentifier + ":" + envIdentifier)
        .connectorIdentifier(connectorIdentifier)
        .productName(PRODUCT_NAME)
        .projectIdentifier(projectIdentifier)
        .accountId(accountId)
        .identifier(identifier)
        .monitoringSourceName(monitoringSourceName)
        .build();
  }
}
