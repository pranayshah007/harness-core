/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.ChangeSourceDTODeserializer;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChangeSourceDTODeserializerTest extends CvNextGenTestBase {
  private BuilderFactory builderFactory;

  private ChangeSourceDTODeserializer changeSourceDTODeserializer;

  Field[] excludedFields;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    changeSourceDTODeserializer = new ChangeSourceDTODeserializer();
    excludedFields = new Field[] {};
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testDeserialize() throws IllegalAccessException {
    ChangeSourceDTO changeSourceDTO = changeSourceDTODeserializer.deserialize();

    List<Field> fieldList = Arrays.stream(changeSourceDTO.getClass().getDeclaredFields())
                                .filter(field -> Arrays.stream(excludedFields).noneMatch(field::equals))
                                .collect(Collectors.toList());

    for (Field f : fieldList) {
      Class t = f.getType();
      Object v = f.get(changeSourceDTO);
      if (t == boolean.class && Boolean.FALSE.equals(v)) {
        Assert.fail("Custom deserialization not set for field " + f);
      } else if (t.isPrimitive() && ((Number) v).doubleValue() == 0) {
        Assert.fail("Custom deserialization not set for field " + f);
      } else if (!t.isPrimitive() && v == null) {
        Assert.fail("Custom deserialization not set for field " + f);
      }
    }
  }
}
