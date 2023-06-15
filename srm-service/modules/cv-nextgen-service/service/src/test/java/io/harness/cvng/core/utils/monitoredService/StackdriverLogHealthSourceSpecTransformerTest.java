/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.StackdriverLogHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.StackdriverLogHealthSourceSpec.StackdriverLogHealthSourceQueryDTO;
import io.harness.cvng.core.entities.StackdriverLogCVConfig;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class StackdriverLogHealthSourceSpecTransformerTest extends CvNextGenTestBase {
  List<StackdriverLogHealthSourceQueryDTO> queryDTOS;
  String envIdentifier;
  String connectorIdentifier;
  String productName;
  String projectIdentifier;
  String accountId;
  String identifier;
  String monitoringSourceName;
  String serviceIdentifier;
  private BuilderFactory builderFactory;

  @Inject StackdriverLogHealthSourceSpecTransformer stackdriverLogHealthSourceSpecTransformer;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    queryDTOS = Lists.newArrayList(StackdriverLogHealthSourceQueryDTO.builder()
                                       .name(randomAlphabetic(10))
                                       .query(randomAlphabetic(10))
                                       .messageIdentifier(randomAlphabetic(10))
                                       .serviceInstanceIdentifier(randomAlphabetic(10))
                                       .build(),
        StackdriverLogHealthSourceQueryDTO.builder()
            .name(randomAlphabetic(10))
            .query(randomAlphabetic(10))
            .messageIdentifier(randomAlphabetic(10))
            .serviceInstanceIdentifier(randomAlphabetic(10))
            .build());
    envIdentifier = "env";
    connectorIdentifier = "connectorId";
    productName = "Application Monitoring";
    projectIdentifier = "projectId";
    accountId = generateUuid();
    identifier = "healthSourceIdentifier";
    monitoringSourceName = "StackdriverLog";
    serviceIdentifier = "serviceId";
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void transformToDSConfig_preconditionEmptyCVConfigs() {
    assertThatThrownBy(() -> stackdriverLogHealthSourceSpecTransformer.transform(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("List of cvConfigs can not empty.");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void transformToDSConfig_preconditionDifferentIdentifier() {
    List<StackdriverLogCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setIdentifier("different-identifier");
    assertThatThrownBy(() -> stackdriverLogHealthSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group ID should be same for List of all configs.");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_preconditionForConnectorRef() {
    List<StackdriverLogCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setConnectorIdentifier("different-connector-ref");
    assertThatThrownBy(() -> stackdriverLogHealthSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ConnectorRef should be same for List of all configs.");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_preconditionForFeatureName() {
    List<StackdriverLogCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setProductName("different-product-name");
    assertThatThrownBy(() -> stackdriverLogHealthSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application feature name should be same for List of all configs.");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    List<StackdriverLogCVConfig> cvConfigs = createCVConfigs();
    StackdriverLogHealthSourceSpec stackdriverLogHealthSourceSpec =
        stackdriverLogHealthSourceSpecTransformer.transform(cvConfigs);

    assertThat(stackdriverLogHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(stackdriverLogHealthSourceSpec.getFeature()).isEqualTo(productName);
    assertThat(stackdriverLogHealthSourceSpec.getQueries().size()).isEqualTo(2);
  }

  private List<StackdriverLogCVConfig> createCVConfigs() {
    List<StackdriverLogCVConfig> stackdriverLogCVConfigs = new ArrayList<>();
    queryDTOS.forEach(query
        -> stackdriverLogCVConfigs.add((StackdriverLogCVConfig) builderFactory.stackdriverLogCVConfigBuilder()
                                           .messageIdentifier(query.getMessageIdentifier())
                                           .serviceInstanceIdentifier(query.getServiceInstanceIdentifier())
                                           .queryName(query.getName())
                                           .query(query.getQuery())
                                           .connectorIdentifier(connectorIdentifier)
                                           .productName(productName)
                                           .projectIdentifier(projectIdentifier)
                                           .accountId(accountId)
                                           .identifier(identifier)
                                           .monitoringSourceName(monitoringSourceName)
                                           .build()));
    return stackdriverLogCVConfigs;
  }
}
