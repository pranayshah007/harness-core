/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redisHandler;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.debezium.DebeziumChangeEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class RedisAbstractHandler {
  public final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  @SneakyThrows
  String getId(String key) {
    JsonNode node = objectMapper.readTree(key);
    return node.get("id").asText();
  }
  @SneakyThrows
  public boolean handleEvent(DebeziumChangeEvent event) {
    String optype = event.getOptype();
    String id = getId(event.getKey());
    String value = event.getValue();
    switch (optype) {
      case "SNAPSHOT":
      case "CREATE":
        return handleCreateEvent(id, value);
      case "UPDATE":
        return handleUpdateEvent(id, value);
      case "DELETE":
        return handleDeleteEvent(id);
      default:
        break;
    }
    log.error("Unknown optype found: {}", optype);
    return true;
  }

  public abstract boolean handleCreateEvent(String id, String value) throws JsonProcessingException;
  public abstract boolean handleDeleteEvent(String id);
  public abstract boolean handleUpdateEvent(String id, String value);
}
