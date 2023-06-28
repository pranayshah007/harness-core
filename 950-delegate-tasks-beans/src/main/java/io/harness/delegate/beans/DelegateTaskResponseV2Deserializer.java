/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.tasks.ResponseData;

import software.wings.TaskTypeToRequestResponseMapper;
import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateTaskResponseV2Deserializer extends StdDeserializer<DelegateTaskResponseV2> {
  String CLASS_ANNOTATION = "@class";

  public DelegateTaskResponseV2Deserializer() {
    super(DelegateTaskResponseV2.class);
  }

  protected DelegateTaskResponseV2Deserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public DelegateTaskResponseV2 deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
    TaskType taskType = TaskType.valueOf(node.get("type").asText());
    Class<? extends ResponseData> responseClass =
        TaskTypeToRequestResponseMapper.getTaskResponseClass(taskType).orElse(null);
    ObjectMapper objectMapper = new ObjectMapper();
    String id = node.get("id").asText();
    DelegateTaskResponse.ResponseCode code = DelegateTaskResponse.ResponseCode.valueOf(node.get("code").asText());
    JsonNode data = node.get("data");
    // If the class annotation is not present, we add it since all the existing classes use it in serialization
    if (data.get(CLASS_ANNOTATION) == null) {
      ((ObjectNode) data).put(CLASS_ANNOTATION, responseClass.getName());
    }
    ResponseData responseData = objectMapper.treeToValue(data, responseClass);
    return DelegateTaskResponseV2.builder()
        .responseData(responseData)
        .taskType(taskType)
        .id(id)
        .responseCode(code)
        .build();
  }
}
