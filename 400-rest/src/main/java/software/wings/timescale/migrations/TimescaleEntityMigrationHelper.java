/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.timescale.migrations;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.timescaledb.TimeScaleDBService;

import io.fabric8.utils.Lists;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
public class TimescaleEntityMigrationHelper {
  private static final String UPDATE_IS_DELETED = "UPDATE %s SET IS_DELETED = true WHERE ID=?";

  public static void insertArrayData(
      int index, Connection dbConnection, PreparedStatement preparedStatement, List<String> data) throws SQLException {
    if (!Lists.isNullOrEmpty(data)) {
      Array array = dbConnection.createArrayOf("text", data.toArray());
      preparedStatement.setArray(index, array);
    } else {
      preparedStatement.setArray(index, null);
    }
  }

  public static void deleteFromTimescaleDB(
      String id, TimeScaleDBService timeScaleDBService, int maxTry, String tableName) {
    String query = String.format(UPDATE_IS_DELETED, tableName);
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < maxTry) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement deleteStatement = connection.prepareStatement(query)) {
        deleteStatement.setString(1, id);
        deleteStatement.executeQuery();
        successful = true;
      } catch (SQLException e) {
        if (retryCount > maxTry) {
          log.error("Failed to delete entity,[{}]", id, e);
        } else {
          log.info("Failed to delete entity,[{}],retryCount=[{}]", id, retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to delete entity,[{}]", id, e);
        retryCount = maxTry + 1;
      } finally {
        log.info("Total time =[{}] for entity:[{}]", System.currentTimeMillis() - startTime, id);
      }
    }
  }
}
