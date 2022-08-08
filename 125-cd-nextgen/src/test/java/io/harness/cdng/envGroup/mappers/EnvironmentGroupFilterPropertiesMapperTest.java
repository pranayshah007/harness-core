/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.mappers;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterProperties;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class EnvironmentGroupFilterPropertiesMapperTest extends CategoryTest {
  String envGroupName = "envGroupName";
  String description = "description";
  List<String> envIdentifier = Arrays.asList("env");
  @InjectMocks EnvironmentGroupFilterPropertiesMapper environmentGroupFilterPropertiesMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testWriteDto() {
    FilterProperties filterProperties = EnvironmentGroupFilterProperties.builder()
                                            .envGroupName(envGroupName)
                                            .description(description)
                                            .envIdentifiers(envIdentifier)
                                            .build();
    EnvironmentGroupFilterPropertiesDTO filterPropertiesDTO =
        (EnvironmentGroupFilterPropertiesDTO) environmentGroupFilterPropertiesMapper.writeDTO(filterProperties);
    assertThat(filterPropertiesDTO.getFilterType()).isEqualTo(FilterType.ENVIRONMENTGROUP);
    assertThat(filterPropertiesDTO.getEnvGroupName()).isEqualTo(envGroupName);
    assertThat(filterPropertiesDTO.getDescription()).isEqualTo(description);
    assertThat(filterPropertiesDTO.getEnvIdentifiers()).isEqualTo(envIdentifier);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testToEntity() {
    FilterPropertiesDTO filterPropertiesDTO = EnvironmentGroupFilterPropertiesDTO.builder()
                                                  .envGroupName(envGroupName)
                                                  .description(description)
                                                  .envIdentifiers(envIdentifier)
                                                  .build();
    EnvironmentGroupFilterProperties filterProperties =
        (EnvironmentGroupFilterProperties) environmentGroupFilterPropertiesMapper.toEntity(filterPropertiesDTO);
    assertThat(filterProperties.getEnvGroupName()).isEqualTo(envGroupName);
    assertThat(filterProperties.getDescription()).isEqualTo(description);
    assertThat(filterProperties.getEnvIdentifiers()).isEqualTo(envIdentifier);
    assertThat(filterProperties.getType()).isEqualTo(FilterType.ENVIRONMENTGROUP);
  }
}
