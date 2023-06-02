/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import static io.harness.rule.OwnerRule.ARPITJ;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.ChangeSourceDTODeserializer;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChangeSourceDTODeserializerTest extends CvNextGenTestBase {
  private ChangeSourceDTODeserializer changeSourceDTODeserializer;
  Field[] excludedFields;
  String jsonString;

  @Before
  public void setup() throws IllegalAccessException {
    changeSourceDTODeserializer = new ChangeSourceDTODeserializer();
    excludedFields = new Field[] {};
    jsonString = "{"
        + "                \"name\": \"Custom Deploy\","
        + "                \"identifier\": \"custom_deploy\","
        + "                \"type\": \"CustomDeploy\","
        + "                \"enabled\": true,"
        + "                \"spec\": {},\n"
        + "                \"category\": \"Deployment\""
        + "            }";
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testDeserialize() throws IllegalAccessException, IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonFactory jsonFactory = objectMapper.getFactory();
    JsonParser jsonParser = jsonFactory.createParser(jsonString);
    ChangeSourceDTO changeSourceDTO = changeSourceDTODeserializer.deserialize(jsonParser, null);

    List<Field> fieldList = Arrays.stream(changeSourceDTO.getClass().getDeclaredFields())
                                .filter(field -> Arrays.stream(excludedFields).noneMatch(field::equals))
                                .collect(Collectors.toList());

    for (Field f : fieldList) {
      Class t = f.getType();
      f.setAccessible(true);
      Object v = f.get(changeSourceDTO);
      if (t == boolean.class && Boolean.FALSE.equals(v)) {
        Assert.fail("Deserialization Failed for field " + f
            + ". Update custom deserializer and add field in json string with non default value.");
      } else if (t != boolean.class && t.isPrimitive() && ((Number) v).doubleValue() == 0) {
        Assert.fail("Deserialization Failed for field " + f
            + ". Update custom deserializer and add field in json string with non default value.");
      } else if (!t.isPrimitive() && v == null) {
        Assert.fail("Deserialization Failed for field " + f
            + ". Update custom deserializer and add field in json string with non default value.");
      }
    }
  }
}
