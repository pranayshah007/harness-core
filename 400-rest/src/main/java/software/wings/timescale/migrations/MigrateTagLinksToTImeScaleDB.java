/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.HarnessTagLink;
import software.wings.beans.HarnessTagLink.HarnessTagLinkKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;

@Slf4j
@Singleton
public class MigrateTagLinksToTImeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String insert_statement =
      "INSERT INTO CG_TAGS (ID,ACCOUNT_ID,APP_ID,TAG_KEY,TAG_VALUE,ENTITY_TYPE,ENTITY_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE CG_TAGS SET NAME=?, ACCOUNT_ID=?, APP_ID=?, TAG_KEY=?, TAG_VALUE=?, ENTITY_TYPE=?, ENTITY_ID=?, CREATED_AT=?, LAST_UPDATED_AT=?, CREATED_BY=?, LAST_UPDATED_BY=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM CG_TAGS WHERE ID=?";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptionsTagLinks = new FindOptions();
      findOptionsTagLinks.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<HarnessTagLink> iterator =
               new HIterator<>(wingsPersistence.createQuery(HarnessTagLink.class, excludeAuthority)
                                   .field(HarnessTagLinkKeys.accountId)
                                   .equal(accountId)
                                   .fetch(findOptionsTagLinks))) {
        while (iterator.hasNext()) {
          HarnessTagLink HarnessTagLink = iterator.next();
          prepareTimeScaleQueries(HarnessTagLink);
          count++;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to complete migration", e);
      return false;
    } finally {
      log.info("Completed migrating [{}] records", count);
    }
    return true;
  }

  private void prepareTimeScaleQueries(HarnessTagLink harnessTagLink) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, harnessTagLink.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("Application found in the timescaleDB:[{}],updating it", harnessTagLink.getUuid());
          updateDataInTimeScaleDB(harnessTagLink, connection, updateStatement);
        } else {
          log.info("Application not found in the timescaleDB:[{}],inserting it", harnessTagLink.getUuid());
          insertDataInTimeScaleDB(harnessTagLink, connection, insertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save application,[{}]", harnessTagLink.getUuid(), e);
        } else {
          log.info("Failed to save application,[{}],retryCount=[{}]", harnessTagLink.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save application,[{}]", harnessTagLink.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info(
            "Total time =[{}] for application:[{}]", System.currentTimeMillis() - startTime, harnessTagLink.getUuid());
      }
    }
  }

  private void insertDataInTimeScaleDB(HarnessTagLink harnessTagLink, Connection connection,
      PreparedStatement insertPreparedStatement) throws SQLException {
    insertPreparedStatement.setString(1, harnessTagLink.getUuid());
    insertPreparedStatement.setString(2, harnessTagLink.getAccountId());
    insertPreparedStatement.setString(3, harnessTagLink.getAppId());
    insertPreparedStatement.setString(4, harnessTagLink.getKey());
    insertPreparedStatement.setString(5, harnessTagLink.getValue());
    insertPreparedStatement.setString(6, harnessTagLink.getEntityType().name());
    insertPreparedStatement.setString(7, harnessTagLink.getEntityId());
    insertPreparedStatement.setLong(8, harnessTagLink.getCreatedAt());
    insertPreparedStatement.setLong(9, harnessTagLink.getLastUpdatedAt());
    insertPreparedStatement.setString(
        10, harnessTagLink.getCreatedBy() != null ? harnessTagLink.getCreatedBy().getName() : null);
    insertPreparedStatement.setString(
        11, harnessTagLink.getLastUpdatedBy() != null ? harnessTagLink.getLastUpdatedBy().getName() : null);

    insertPreparedStatement.execute();
  }

  private void updateDataInTimeScaleDB(
      HarnessTagLink harnessTagLink, Connection connection, PreparedStatement updateStatement) throws SQLException {
    updateStatement.setString(1, harnessTagLink.getAccountId());
    updateStatement.setString(2, harnessTagLink.getAppId());
    updateStatement.setString(3, harnessTagLink.getKey());
    updateStatement.setString(4, harnessTagLink.getValue());
    updateStatement.setString(5, harnessTagLink.getEntityType().name());
    updateStatement.setString(6, harnessTagLink.getEntityId());
    updateStatement.setLong(7, harnessTagLink.getCreatedAt());
    updateStatement.setLong(8, harnessTagLink.getLastUpdatedAt());
    updateStatement.setString(
        9, harnessTagLink.getCreatedBy() != null ? harnessTagLink.getCreatedBy().getName() : null);
    updateStatement.setString(
        10, harnessTagLink.getLastUpdatedBy() != null ? harnessTagLink.getLastUpdatedBy().getName() : null);

    updateStatement.setString(11, harnessTagLink.getUuid());
    updateStatement.execute();
  }

  private void insertArrayData(
      int index, Connection dbConnection, PreparedStatement preparedStatement, List<String> data) throws SQLException {
    if (!isEmpty(data)) {
      Array array = dbConnection.createArrayOf("text", data.toArray());
      preparedStatement.setArray(index, array);
    } else {
      preparedStatement.setArray(index, null);
    }
  }
}
