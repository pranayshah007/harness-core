/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.util.List;
import java.util.Properties;

public interface DebeziumService {
  DebeziumEngine<ChangeEvent<String, String>> getEngine(Properties props, MongoCollectionChangeConsumer changeConsumer,
      String collection, DebeziumController debeziumController, List<Integer> listOfErrorCodesForOffsetReset);

  void closeEngine(DebeziumEngine debeziumEngine, String collection) throws Exception;
}
