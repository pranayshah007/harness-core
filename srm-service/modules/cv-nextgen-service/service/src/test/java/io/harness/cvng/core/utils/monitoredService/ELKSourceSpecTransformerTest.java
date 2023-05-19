/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.ELKHealthSourceSpec;
import io.harness.cvng.core.entities.ELKCVConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ELKSourceSpecTransformerTest extends CvNextGenTestBase {
  private BuilderFactory builderFactory;

  private static final String MOCKED_CONNECTOR_IDENTIFIER = "mockedConnectorIdentifier";
  private static final String MOCKED_PRODUCT_NAME = "mockedProductName";
  private static final int QUERY_COUNT = 5;
  private static final String MOCKED_GROUP_IDENTIFIER = "mockedGroupIdentifier";

  private List<ELKHealthSourceSpec.ELKHealthSourceQueryDTO> queries;

  @Inject ELKHealthSourceSpecTransformer classUnderTest;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    queries = IntStream.range(1, QUERY_COUNT)
                  .mapToObj(index
                      -> ELKHealthSourceSpec.ELKHealthSourceQueryDTO.builder()
                             .name(randomAlphabetic(10))
                             .query(randomAlphabetic(10))
                             .build())
                  .collect(Collectors.toList());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void transformToHealthSourceConfigPreconditionEmptyCVConfigs() {
    assertThatThrownBy(() -> classUnderTest.transform(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("List of cvConfigs can not empty.");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void transformToHealthSourceConfigPreconditionDifferentIdentifier() {
    List<ELKCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setIdentifier("different-identifier");
    assertThatThrownBy(() -> classUnderTest.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group ID should be same for List of all configs.");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfigPreconditionForConnectorRef() {
    List<ELKCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setConnectorIdentifier("different-connector-ref");
    assertThatThrownBy(() -> classUnderTest.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ConnectorRef should be same for List of all configs.");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfigPreconditionForFeatureName() {
    List<ELKCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setProductName("different-product-name");
    assertThatThrownBy(() -> classUnderTest.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application feature name should be same for List of all configs.");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    List<ELKCVConfig> cvConfigs = createCVConfigs();
    ELKHealthSourceSpec elkHealthSourceSpec = classUnderTest.transform(cvConfigs);

    assertThat(elkHealthSourceSpec.getConnectorRef()).isEqualTo(MOCKED_CONNECTOR_IDENTIFIER);
    assertThat(elkHealthSourceSpec.getFeature()).isEqualTo(MOCKED_PRODUCT_NAME);
    assertThat(elkHealthSourceSpec.getQueries().size()).isEqualTo(queries.size());
    elkHealthSourceSpec.getQueries().forEach(query -> assertThat(query.getIndex()).isNotNull());
  }

  private List<ELKCVConfig> createCVConfigs() {
    return queries.stream()
        .map(query -> {
          ELKCVConfig elkCVConfig = (ELKCVConfig) builderFactory.elkCVConfigBuilder()
                                        .queryName(query.getName())
                                        .query(query.getQuery())
                                        .connectorIdentifier(MOCKED_CONNECTOR_IDENTIFIER)
                                        .productName(MOCKED_PRODUCT_NAME)
                                        .identifier(MOCKED_GROUP_IDENTIFIER)
                                        .build();
          return elkCVConfig;
        })
        .collect(Collectors.toList());
  }
}
