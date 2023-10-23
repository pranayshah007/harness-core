/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.cvng.beans.DataSourceType.SUMOLOGIC_LOG;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NextGenLogHealthSourceSpecTransformerTest extends CvNextGenTestBase {
  private static final String MOCKED_CONNECTOR_IDENTIFIER = "mockedConnectorIdentifier";
  private static final int QUERY_COUNT = 5;
  private static final String MOCKED_GROUP_IDENTIFIER = "mockedGroupIdentifier";
  private static final String GROUP_NAME = "g1";
  BuilderFactory builderFactory;
  List<QueryDefinition> queries;
  String query;
  @Inject NextGenLogHealthSourceSpecTransformer classUnderTest;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    String queryDefinitionName = randomAlphabetic(10);
    String queryDefinitionIdentifier = randomAlphabetic(10);
    query = randomAlphabetic(10);
    queries = IntStream.range(1, QUERY_COUNT)
                  .mapToObj(index
                      -> QueryDefinition.builder()
                             .name(queryDefinitionName)
                             .identifier(queryDefinitionIdentifier)
                             .groupName(GROUP_NAME)
                             .query(query)
                             .build())
                  .collect(Collectors.toList());
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void transformToHealthSourceConfigPreconditionEmptyCVConfigs() {
    assertThatThrownBy(() -> classUnderTest.transform(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("List of cvConfigs can not empty.");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void transformToHealthSourceConfigPreconditionDifferentIdentifier() {
    List<NextGenLogCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setIdentifier("different-identifier");
    assertThatThrownBy(() -> classUnderTest.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group ID should be same for List of all configs.");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfigPreconditionForConnectorRef() {
    List<NextGenLogCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setConnectorIdentifier("different-ConnectorDisconnectHandler-ref");
    assertThatThrownBy(() -> classUnderTest.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ConnectorRef should be same for List of all configs.");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    List<NextGenLogCVConfig> cvConfigs = createCVConfigs();
    NextGenHealthSourceSpec nextGenHealthSourceSpec = classUnderTest.transform(cvConfigs);
    assertThat(nextGenHealthSourceSpec.getConnectorRef()).isEqualTo(MOCKED_CONNECTOR_IDENTIFIER);
    assertThat(nextGenHealthSourceSpec.getQueryDefinitions().size()).isEqualTo(queries.size());
    assertThat(nextGenHealthSourceSpec.getDataSourceType()).isEqualTo(SUMOLOGIC_LOG);
    nextGenHealthSourceSpec.getQueryDefinitions().forEach(queryDefinition -> {
      assertThat(queryDefinition.getIdentifier()).isNotNull();
      assertThat(queryDefinition.getGroupName()).isEqualTo(GROUP_NAME);
      assertThat(queryDefinition.getQuery()).isEqualTo(query);
    });
  }

  private List<NextGenLogCVConfig> createCVConfigs() {
    return queries.stream()
        .map((QueryDefinition query)
                 -> (NextGenLogCVConfig) builderFactory
                        .nextGenLogCVConfigBuilder(SUMOLOGIC_LOG, query.getGroupName(), query.getIdentifier())
                        .queryName(query.getName())
                        .query(query.getQuery())
                        .connectorIdentifier(MOCKED_CONNECTOR_IDENTIFIER)
                        .identifier(MOCKED_GROUP_IDENTIFIER)
                        .build())
        .collect(Collectors.toList());
  }
}
