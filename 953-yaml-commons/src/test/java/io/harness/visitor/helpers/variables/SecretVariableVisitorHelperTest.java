/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.visitor.helpers.variables;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretVariableVisitorHelperTest extends CategoryTest {
  SecretVariableVisitorHelper visitor = new SecretVariableVisitorHelper();

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void addReference() {
    assertThat(addReference(SecretNGVariable.builder().build())).isEmpty();
    assertThat(addReference(SecretNGVariable.builder().name("s").build())).isEmpty();
    assertThat(addReference(SecretNGVariable.builder().name("s").defaultValue(SecretRefData.builder().build()).build()))
        .isEmpty();
    assertThat(
        addReference(
            SecretNGVariable.builder().name("s").defaultValue(SecretRefData.builder().identifier("").build()).build()))
        .isEmpty();
    assertThat(addReference(SecretNGVariable.builder()
                                .name("s")
                                .value(ParameterField.createValueField(SecretRefData.builder().build()))
                                .build()))
        .isEmpty();
    assertThat(addReference(SecretNGVariable.builder()
                                .name("s")
                                .value(ParameterField.createValueField(SecretRefData.builder().identifier("").build()))
                                .build()))
        .isEmpty();

    assertThat(addReference(SecretNGVariable.builder()
                                .name("s")
                                .value(ParameterField.createValueField(SecretRefData.builder().identifier("").build()))
                                .defaultValue(SecretRefData.builder().build())
                                .build()))
        .isEmpty();
  }

  private Set<EntityDetailProtoDTO> addReference(SecretNGVariable ngVariable) {
    return visitor.addReference(ngVariable, "acc", "org", "project", Map.of());
  }
}
