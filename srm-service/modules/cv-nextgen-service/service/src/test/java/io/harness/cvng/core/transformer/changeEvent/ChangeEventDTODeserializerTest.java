/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import static io.harness.rule.OwnerRule.ARPITJ;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeEventDTODeserializer;
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

public class ChangeEventDTODeserializerTest extends CvNextGenTestBase {
  private ChangeEventDTODeserializer changeEventDTODeserializer;
  Field[] excludedFields;
  String jsonString;

  @Before
  public void setup() throws IllegalAccessException {
    changeEventDTODeserializer = new ChangeEventDTODeserializer();
    excludedFields = new Field[] {};
    jsonString = "{"
        + "    \"id\": \"-k53qRQAQ1O7DBLb9ACnjQ\","
        + "    \"accountId\": \"-k53qRQAQ1O7DBLb9ACnjQ\","
        + "    \"orgIdentifier\": \"default\","
        + "    \"projectIdentifier\": \"projectId\","
        + "    \"serviceIdentifier\": \"serviceId\","
        + "    \"serviceName\": \"serviceName\","
        + "    \"envIdentifier\": \"envId\","
        + "    \"environmentName\": \"envName\","
        + "    \"name\": \"name\","
        + "    \"changeSourceIdentifier\": \"changeSourceId\","
        + "    \"monitoredServiceIdentifier\": \"monitoredServiceId\","
        + "    \"type\": \"HarnessCDNextGen\","
        + "    \"eventTime\": 1630992973462,"
        + "    \"metadata\": {"
        + "        \"deploymentStartTime\": 1630992973462,"
        + "        \"deploymentEndTime\": 1630992998599,"
        + "        \"planExecutionId\": \"testex1\","
        + "        \"pipelineId\": \"test\","
        + "        \"stageStepId\": \"EOFUmKvJR1CEKvRmVvpzkw\","
        + "        \"stageId\": \"test\","
        + "        \"artifactType\": \"DockerRegistry\","
        + "        \"artifactTag\": \"praveen-cv-test\","
        + "        \"status\": \"ABORTED\""
        + "    }"
        + "}";
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testDeserialize() throws IllegalAccessException, IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonFactory jsonFactory = objectMapper.getFactory();
    JsonParser jsonParser = jsonFactory.createParser(jsonString);
    ChangeEventDTO changeEventDTO = changeEventDTODeserializer.deserialize(jsonParser, null);

    List<Field> fieldList = Arrays.stream(changeEventDTO.getClass().getDeclaredFields())
                                .filter(field -> Arrays.stream(excludedFields).noneMatch(field::equals))
                                .collect(Collectors.toList());

    for (Field f : fieldList) {
      Class t = f.getType();
      f.setAccessible(true);
      Object v = f.get(changeEventDTO);
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
