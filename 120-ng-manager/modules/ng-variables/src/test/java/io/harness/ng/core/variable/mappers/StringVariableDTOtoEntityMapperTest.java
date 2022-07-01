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
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.entity.StringVariable;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StringVariableDTOtoEntityMapperTest extends CategoryTest {
  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testToVariableEntity() {
    String fixedValue = randomAlphabetic(10);
    String defaultValue = randomAlphabetic(10);
    String[] allowedValues = {randomAlphabetic(10), randomAlphabetic(5)};
    Set<String> allowedValuesSet = new HashSet<>(Arrays.asList(allowedValues));
    String regex = randomAlphabetic(6);
    StringVariableConfigDTO variableConfigDTO = StringVariableConfigDTO.builder()
                                                    .fixedValue(fixedValue)
                                                    .defaultValue(defaultValue)
                                                    .allowedValues(allowedValuesSet)
                                                    .regex(regex)
                                                    .build();
    StringVariable variable = StringVariableDTOtoEntityMapper.toVariableEntity(variableConfigDTO);
    assertThat(variable.getFixedValue()).isEqualTo(fixedValue);
    assertThat(variable.getDefaultValue()).isEqualTo(defaultValue);
    assertThat(variable.getAllowedValues()).isEqualTo(allowedValuesSet);
    assertThat(variable.getRegex()).isEqualTo(regex);
  }
}
