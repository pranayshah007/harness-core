/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.yaml.snakeyaml.introspector.FieldProperty;
import org.yaml.snakeyaml.nodes.NodeTuple;

@OwnedBy(CDP)
public class YamlRepresenterTest extends CategoryTest {
  YamlRepresenter representerRemoveEmpty = new YamlRepresenter(true);

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void representJavaBeanPropertyKeepEmptyAsIs() throws NoSuchFieldException {
    TestClass target = TestClass.builder().name("ABC").dontHideMeIfEmpty("").hideMeIfEmpty("").valueType(null).build();

    NodeTuple tuple;
    tuple = representerRemoveEmpty.representJavaBeanProperty(
        target, new FieldProperty(TestClass.class.getField("dontHideMeIfEmpty")), target.getDontHideMeIfEmpty(), null);
    assertThat(tuple).isNotNull();

    tuple = representerRemoveEmpty.representJavaBeanProperty(
        target, new FieldProperty(TestClass.class.getField("hideMeIfEmpty")), target.getHideMeIfEmpty(), null);
    assertThat(tuple).isNull();

    tuple = representerRemoveEmpty.representJavaBeanProperty(
        target, new FieldProperty(TestClass.class.getField("valueType")), target.getValueType(), null);
    assertThat(tuple).isNull();
  }

  @Value
  @Builder
  static class TestClass {
    public String name;
    @YamlKeepEmptyAsIs public String dontHideMeIfEmpty;
    public String hideMeIfEmpty;
    @YamlKeepEmptyAsIs public String valueType;
  }
}
