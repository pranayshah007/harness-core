/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreams.eventhandlers;

import io.harness.entities.Instance;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ssca.services.CdInstanceSummaryService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InstanceNGRedisEventHandler extends DebeziumAbstractRedisEventHandler {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Inject CdInstanceSummaryService cdInstanceSummaryService;

  private Instance createEntity(String value) {
    try {
      return objectMapper.readValue(value, Instance.class);
    } catch (JsonProcessingException e) {
      log.error("Can not deserialize redis message into new instance.");
      throw new InvalidArgumentsException("Can not deserialize redis message into new instance.");
    }
  }

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Instance instance = createEntity(value);
    cdInstanceSummaryService.upsertInstance(instance);
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    Instance instance = createEntity(value);
    if (instance.isDeleted()) {
      cdInstanceSummaryService.removeInstance(instance);
    }
    return true;
  }
}
