/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans;

import software.wings.beans.TaskTypeV2;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateTaskResponseV2Deserializer extends StdDeserializer<DelegateTaskResponseV2> {
  public DelegateTaskResponseV2Deserializer() {
    super(DelegateTaskResponseV2.class);
  }

  protected DelegateTaskResponseV2Deserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public DelegateTaskResponseV2 deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {
    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
    TaskTypeV2 taskType = TaskTypeV2.valueOf(node.get("type").asText());
    Class<? extends DelegateResponseData> responseClass = taskType.getResponseClass();
    ObjectMapper objectMapper = new ObjectMapper();
    String id = node.get("id").asText();
    DelegateTaskResponse.ResponseCode code = DelegateTaskResponse.ResponseCode.valueOf(node.get("code").asText());
    DelegateResponseData responseData = objectMapper.treeToValue(node.get("data"), responseClass);
    return DelegateTaskResponseV2.builder().delegateResponseData(responseData).taskType(taskType).id(id).responseCode(code).build();
  }
}
