/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.mappers;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.variable.VariableType;
import io.harness.ng.core.variable.VariableValueType;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.entity.StringVariable;
import io.harness.ng.core.variable.entity.Variable;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class VariableMapperTest extends CategoryTest {
  @Spy VariableMapper variableMapper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testToVariable() {
    String identifier = randomAlphabetic(10);
    String name = randomAlphabetic(10);
    String description = randomAlphabetic(20);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    VariableType type = VariableType.STRING;
    VariableValueType valueType = VariableValueType.FIXED;
    VariableDTO variableDTO = VariableDTO.builder()
                                  .identifier(identifier)
                                  .name(name)
                                  .description(description)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .type(type)
                                  .variableConfig(StringVariableConfigDTO.builder().valueType(valueType).build())
                                  .build();
    Variable variable = variableMapper.toVariable(accountIdentifier, variableDTO);
    assertThat(variable).isNotNull();
    assertThat(variable.getIdentifier()).isEqualTo(variableDTO.getIdentifier());
    assertThat(variable.getName()).isEqualTo(variableDTO.getName());
    assertThat(variable.getDescription()).isEqualTo(variableDTO.getDescription());
    assertThat(variable.getOrgIdentifier()).isEqualTo(variableDTO.getOrgIdentifier());
    assertThat(variable.getProjectIdentifier()).isEqualTo(variableDTO.getProjectIdentifier());
    assertThat(variable.getType()).isEqualTo(variableDTO.getType());
    assertThat(variable.getValueType()).isEqualTo(variableDTO.getVariableConfig().getValueType());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testWriteDTO() {
    String id = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String name = randomAlphabetic(10);
    String description = randomAlphabetic(20);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    VariableType type = VariableType.STRING;
    VariableValueType valueType = VariableValueType.FIXED;
    Variable variable = StringVariable.builder().build();
    variable.setId(id);
    variable.setIdentifier(identifier);
    variable.setName(name);
    variable.setDescription(description);
    variable.setAccountIdentifier(accountIdentifier);
    variable.setOrgIdentifier(orgIdentifier);
    variable.setProjectIdentifier(projectIdentifier);
    variable.setType(type);
    variable.setValueType(valueType);
    VariableDTO variableDTO = variableMapper.writeDTO(variable);
    assertThat(variableDTO).isNotNull();
  }
}
