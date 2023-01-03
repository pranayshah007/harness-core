/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DebeziumServiceImpl implements DebeziumService {
  @Override
  public DebeziumEngine<ChangeEvent<String, String>> getEngine(
      Properties props, MongoCollectionChangeConsumer changeConsumer, String collection) {
    return DebeziumEngine.create(Json.class)
        .using(props)
        .using(DebeziumUtils.getConnectorCallback(collection))
        .using(DebeziumUtils.getCompletionCallback(
            props.get(DebeziumConfiguration.OFFSET_STORAGE_FILE_FILENAME).toString(),
            props.get(DebeziumConfiguration.OFFSET_STORAGE_KEY).toString()))
        .notifying(changeConsumer)
        .build();
  }

  @Override
  public void closeEngine(DebeziumEngine debeziumEngine, String collection) throws Exception {
    if (debeziumEngine != null) {
      log.info("Closing Debezium engine for collection {}", collection);
      debeziumEngine.close();
      TimeUnit.SECONDS.sleep(10);
    }
  }
}
