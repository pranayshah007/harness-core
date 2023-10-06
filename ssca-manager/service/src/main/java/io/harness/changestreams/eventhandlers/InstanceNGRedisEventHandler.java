/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreams.eventhandlers;

import io.harness.entities.Instance;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.ssca.services.CdInstanceSummaryService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class InstanceNGRedisEventHandler extends DebeziumAbstractRedisEventHandler {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Inject MongoTemplate mongoTemplate;

  @Inject CdInstanceSummaryService cdInstanceSummaryService;

  private Instance createEntity(String value) {
    Document document = Document.parse(value);
    document.remove("instanceInfo");
    return mongoTemplate.getConverter().read(Instance.class, document);
  }

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Instance instance = createEntity(value);
    return cdInstanceSummaryService.upsertInstance(instance);
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    Instance instance = createEntity(value);
    if (instance.isDeleted()) {
      return cdInstanceSummaryService.removeInstance(instance);
    }
    return true;
  }
}
