/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.cloudprovider;

import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;

import static java.util.Arrays.asList;

import io.harness.data.structure.EmptyPredicate;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.SettingAttribute;
import software.wings.search.SQLOperationHelper;
import software.wings.search.framework.ChangeHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CloudProviderTimescaleChangeHandler implements ChangeHandler {
  @Inject private TimeScaleDBService timeScaleDBService;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent) {
    String tableName = "cg_cloud_providers";

    switch (changeEvent.getChangeType()) {
      case INSERT:
        Map<String, Object> ColumnValueMapping = getColumnValueMapping(changeEvent);
        if (!EmptyPredicate.isEmpty(ColumnValueMapping)) {
          dbOperation(SQLOperationHelper.insertSQL(tableName, ColumnValueMapping));
        }
        break;
      case UPDATE:
        ColumnValueMapping = getColumnValueMapping(changeEvent);
        if (!EmptyPredicate.isEmpty(ColumnValueMapping)) {
          dbOperation(SQLOperationHelper.updateSQL(tableName, getColumnValueMapping(changeEvent),
              Collections.singletonMap("id", changeEvent.getUuid()), getPrimaryKeys()));
        }
        break;
      case DELETE:
        dbOperation(SQLOperationHelper.deleteSQL(tableName, Collections.singletonMap("id", changeEvent.getUuid())));
        break;
      default:
        return false;
    }
    return true;
  }

  public List<String> getPrimaryKeys() {
    return asList("id");
  }

  public boolean dbOperation(String query) {
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

  public Map<String, Object> getColumnValueMapping(ChangeEvent<?> changeEvent) {
    Map<String, Object> columnValueMapping = new HashMap<>();
    SettingAttribute settingAttribute = (SettingAttribute) changeEvent.getFullDocument();

    if (changeEvent == null) {
      return null;
    }

    if (settingAttribute == null) {
      return columnValueMapping;
    }

    if (!settingAttribute.getCategory().equals(CLOUD_PROVIDER)) {
      return null;
    }

    if (settingAttribute.getUuid() != null) {
      columnValueMapping.put("id", settingAttribute.getUuid());
    }

    // name
    if (settingAttribute.getName() != null) {
      columnValueMapping.put("name", settingAttribute.getName());
    }

    // account_id
    if (settingAttribute.getAccountId() != null) {
      columnValueMapping.put("account_id", settingAttribute.getAccountId());
    }

    // app_id
    if (settingAttribute.getAppId() != null) {
      columnValueMapping.put("app_id", settingAttribute.getAppId());
    }

    // created_at
    columnValueMapping.put("created_at", settingAttribute.getCreatedAt());

    // last_updated_at
    columnValueMapping.put("last_updated_at", settingAttribute.getLastUpdatedAt());

    // created_by
    if (settingAttribute.getCreatedBy() != null) {
      columnValueMapping.put("created_by", settingAttribute.getCreatedBy().getName());
    }

    // last_updated_by
    if (settingAttribute.getLastUpdatedBy() != null) {
      columnValueMapping.put("last_updated_by", settingAttribute.getLastUpdatedBy().getName());
    }
    return columnValueMapping;
  }
}
