/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.migrations.Migration;

import software.wings.dl.WingsPersistence;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;

import com.google.inject.Inject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Pranjal on 05/27/2019
 */
@Slf4j
public class DatadogCVServiceConfigurationMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    DBCollection collection = wingsPersistence.getCollection(CVConfiguration.class);
    UpdateOperations<CVConfiguration> operations = wingsPersistence.createUpdateOperations(CVConfiguration.class);

    DBCursor cvConfigurationRecords = collection.find();

    log.info("will go through " + cvConfigurationRecords.size() + " records");
    int updated = 0;
    try {
      while (cvConfigurationRecords.hasNext()) {
        DBObject next = cvConfigurationRecords.next();
        StateType stateType = StateType.valueOf(next.get("stateType").toString());

        if (stateType == StateType.DATA_DOG) {
          String uuId = (String) next.get("_id");
          String metrics = (String) next.get("metrics");
          String dockerMetricFilter = (String) next.get("applicationFilter");

          if (isEmpty(metrics)) {
            continue;
          }
          Map<String, String> dockerMetrics = new HashMap<>();
          // remove spaces in between metric names
          dockerMetrics.put(dockerMetricFilter, metrics.replaceAll("\\s+", ""));

          operations.set("dockerMetrics", dockerMetrics);

          update(uuId, operations);
          updated++;
        }
      }
    } catch (Exception e) {
      log.error("DatadogCVServiceConfigurationMigration failed", e);
    }
    log.info("Complete. Updated " + updated + " records.");
  }

  private void update(String uuId, UpdateOperations<CVConfiguration> operations) {
    Query<CVConfiguration> query = wingsPersistence.createQuery(CVConfiguration.class).filter(ID_KEY, uuId);
    wingsPersistence.update(query, operations);
  }
}
