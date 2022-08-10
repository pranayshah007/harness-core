/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;

@Slf4j
@Singleton
public class MigrateEnvironmentsToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String insert_statement =
      "INSERT INTO CG_ENVIRONMENTS (ID,NAME,ACCOUNT_ID,ENV_TYPE,APP_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE CG_ENVIRONMENTS SET NAME=?, ACCOUNT_ID=?, ENV_TYPE=?, APP_ID=?, CREATED_AT=?, LAST_UPDATED_AT=?, CREATED_BY=?, LAST_UPDATED_BY=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM CG_ENVIRONMENTS WHERE ID=?";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_environments = new FindOptions();
      findOptions_environments.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<Environment> iterator =
               new HIterator<>(wingsPersistence.createQuery(Environment.class, excludeAuthority)
                                   .field(EnvironmentKeys.accountId)
                                   .equal(accountId)
                                   .fetch(findOptions_environments))) {
        while (iterator.hasNext()) {
          Environment environment = iterator.next();
          saveToTimeScale(environment);
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

  public void saveToTimeScale(Environment environment) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, environment.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("Environment found in the timescaleDB:[{}],updating it", environment.getUuid());
          updateDataInTimeScaleDB(environment, connection, updateStatement);
        } else {
          log.info("Environment not found in the timescaleDB:[{}],inserting it", environment.getUuid());
          insertDataInTimeScaleDB(environment, connection, insertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save environment,[{}]", environment.getUuid(), e);
        } else {
          log.info("Failed to save environment,[{}],retryCount=[{}]", environment.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save environment,[{}]", environment.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info(
            "Total time =[{}] for environment:[{}]", System.currentTimeMillis() - startTime, environment.getUuid());
      }
    }
  }

  private void insertDataInTimeScaleDB(
      Environment environment, Connection connection, PreparedStatement insertPreparedStatement) throws SQLException {
    insertPreparedStatement.setString(1, environment.getUuid());
    insertPreparedStatement.setString(2, environment.getName());
    insertPreparedStatement.setString(3, environment.getAccountId());
    insertPreparedStatement.setString(4, environment.getEnvironmentType().toString());
    insertPreparedStatement.setString(5, environment.getAppId());

    insertPreparedStatement.setLong(6, environment.getCreatedAt());
    insertPreparedStatement.setLong(7, environment.getLastUpdatedAt());

    String created_by = null;
    if (environment.getCreatedBy() != null) {
      created_by = environment.getCreatedBy().getName();
    }
    insertPreparedStatement.setString(8, created_by);

    String last_updated_by = null;
    if (environment.getLastUpdatedBy() != null) {
      last_updated_by = environment.getLastUpdatedBy().getName();
    }
    insertPreparedStatement.setString(9, last_updated_by);

    insertPreparedStatement.execute();
  }

  private void updateDataInTimeScaleDB(
      Environment environment, Connection connection, PreparedStatement updateStatement) throws SQLException {
    updateStatement.setString(1, environment.getName());
    updateStatement.setString(2, environment.getAccountId());
    updateStatement.setString(3, environment.getEnvironmentType().toString());
    updateStatement.setString(4, environment.getAppId());

    updateStatement.setLong(5, environment.getCreatedAt());
    updateStatement.setLong(6, environment.getLastUpdatedAt());

    String created_by = null;
    if (environment.getCreatedBy() != null) {
      created_by = environment.getCreatedBy().getName();
    }
    updateStatement.setString(7, created_by);

    String last_updated_by = null;
    if (environment.getLastUpdatedBy() != null) {
      last_updated_by = environment.getLastUpdatedBy().getName();
    }
    updateStatement.setString(8, last_updated_by);

    updateStatement.setString(9, environment.getUuid());

    updateStatement.execute();
  }
}
