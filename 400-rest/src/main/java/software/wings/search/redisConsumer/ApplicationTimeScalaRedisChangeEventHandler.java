/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.search.redisConsumer;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.ff.FeatureFlagService;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.search.SQLOperationHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ApplicationTimeScalaRedisChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject private FeatureFlagService featureFlagService;
  private static final String tableName = "cg_applications";

  @SneakyThrows
  public Map<String, Object> getColumnValueMapping(String value, String id) {
    JsonNode node = objectMapper.readTree(value);
    if (node == null) {
      return null;
    }
    Map<String, Object> columnValueMapping = new HashMap<>();

    if (id != null) {
      columnValueMapping.put("id", id);
    }

    if (node.get("name") != null) {
      columnValueMapping.put("name", node.get("name").asText());
    }

    if (node.get("accountId") != null) {
      columnValueMapping.put("account_id", node.get("accountId").asText());
    }

    if (node.get("createdAt") != null) {
      columnValueMapping.put("created_at", node.get("createdAt").asText());
    }

    if (node.get("lastUpdatedAt") != null) {
      columnValueMapping.put("last_updated_at", node.get("lastUpdatedAt").asText());
    }

    if (node.get("createdBy") != null && node.get("createdBy").get("name") != null) {
      columnValueMapping.put("created_by", node.get("createdBy").get("name").asText());
    }

    if (node.get("lastUpdatedBy") != null && node.get("lastUpdatedBy").get("name") != null) {
      columnValueMapping.put("last_updated_by", node.get("lastUpdatedBy").get("name").asText());
    }

    return columnValueMapping;
  }
  @Override
  public boolean handleCreateEvent(String id, String value) {
    return dbOperation(
        SQLOperationHelper.insertSQL(tableName, getColumnValueMapping(value, id)) + "ON CONFLICT (id) DO NOTHING");
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    return dbOperation(SQLOperationHelper.deleteSQL(tableName, Collections.singletonMap("id", id)));
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    return dbOperation(SQLOperationHelper.updateSQL(
        tableName, getColumnValueMapping(value, id), Collections.singletonMap("id", id), getPrimaryKeys()));
  }

  public List<String> getPrimaryKeys() {
    return asList("id");
  }

  public boolean dbOperation(String query) {
    if (!featureFlagService.isGlobalEnabled(FeatureName.CDS_DEBEZIUM_ENABLED_CG)) {
      return true;
    }
    boolean successfulOperation = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulOperation && retryCount < 5) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(query)) {
          statement.execute();
          successfulOperation = true;
        } catch (SQLException e) {
          log.error("Failed to save/update/delete data Query = {},retryCount=[{}], Exception: ", query, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("TimeScale Down");
    }
    return successfulOperation;
  }
}
