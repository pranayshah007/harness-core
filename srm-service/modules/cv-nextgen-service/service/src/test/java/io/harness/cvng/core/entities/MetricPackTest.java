/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.MetricPackDTO.MetricDefinitionDTO;
import io.harness.cvng.core.entities.MetricPack.MetricDefinition;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MetricPackTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetDTO_metricPack() {
    MetricPack metricPack = MetricPack.builder()
                                .identifier("id")
                                .accountId("accountId")
                                .dataCollectionDsl("dsl")
                                .dataSourceType(DataSourceType.APP_DYNAMICS)
                                .metrics(Sets.newHashSet(MetricDefinition.builder().build()))
                                .build();
    MetricPackDTO metricPackDTO = metricPack.toDTO();
    assertThat(metricPackDTO.getMetrics())
        .isEqualTo(metricPack.getMetrics().stream().map(MetricDefinition::toDTO).collect(Collectors.toSet()));
    assertThat(metricPackDTO.getAccountId()).isEqualTo(metricPack.getAccountId());
    assertThat(metricPackDTO.getIdentifier()).isEqualTo(metricPack.getIdentifier());
    assertThat(metricPackDTO.getDataSourceType()).isEqualTo(metricPack.getDataSourceType());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetDTO_metricPackDefinition() {
    MetricDefinition metricDefinition =
        MetricDefinition.builder().name("name").path("path").validationPath("validationPath").build();
    MetricDefinitionDTO metricDefinitionDTO = metricDefinition.toDTO();

    assertThat(metricDefinitionDTO.getPath()).isEqualTo(metricDefinition.getPath());
    assertThat(metricDefinitionDTO.getName()).isEqualTo(metricDefinition.getName());
    assertThat(metricDefinitionDTO.getValidationPath()).isEqualTo(metricDefinition.getValidationPath());
    assertThat(metricDefinitionDTO.getType()).isEqualTo(metricDefinition.getType());
  }
}
