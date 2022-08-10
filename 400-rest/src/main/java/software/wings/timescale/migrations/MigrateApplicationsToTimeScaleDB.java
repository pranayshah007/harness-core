/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Application.ApplicationKeys;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Application;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;

@Slf4j
@Singleton
public class MigrateApplicationsToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String insert_statement =
      "INSERT INTO CG_APPLICATIONS (ID,NAME,ACCOUNT_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE CG_APPLICATIONS SET NAME=?, ACCOUNT_ID=?, CREATED_AT=?, LAST_UPDATED_AT=?, CREATED_BY=?, LAST_UPDATED_BY=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM CG_APPLICATIONS WHERE ID=?";

  private static final String delete_statement = "DELETE FROM CG_APPLICATIONS WHERE ID=?";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_applications = new FindOptions();
      findOptions_applications.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<Application> iterator =
               new HIterator<>(wingsPersistence.createQuery(Application.class, excludeAuthority)
                                   .field(ApplicationKeys.accountId)
                                   .equal(accountId)
                                   .fetch(findOptions_applications))) {
        while (iterator.hasNext()) {
          Application application = iterator.next();
          saveToTimeScale(application);
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

  public void saveToTimeScale(Application application) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, application.getAppId());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("Application found in the timescaleDB:[{}],updating it", application.getAppId());
          updateDataInTimeScaleDB(application, connection, updateStatement);
        } else {
          log.info("Application not found in the timescaleDB:[{}],inserting it", application.getAppId());
          insertDataInTimeScaleDB(application, connection, insertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save application,[{}]", application.getAppId(), e);
        } else {
          log.info("Failed to save application,[{}],retryCount=[{}]", application.getAppId(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save application,[{}]", application.getAppId(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info(
            "Total time =[{}] for application:[{}]", System.currentTimeMillis() - startTime, application.getAppId());
      }
    }
  }

  private void insertDataInTimeScaleDB(
      Application application, Connection connection, PreparedStatement insertPreparedStatement) throws SQLException {
    insertPreparedStatement.setString(1, application.getAppId());
    insertPreparedStatement.setString(2, application.getName());
    insertPreparedStatement.setString(3, application.getAccountId());
    insertPreparedStatement.setLong(4, application.getCreatedAt());
    insertPreparedStatement.setLong(5, application.getLastUpdatedAt());

    String created_by = null;
    if (application.getCreatedBy() != null) {
      created_by = application.getCreatedBy().getName();
    }
    insertPreparedStatement.setString(6, created_by);
    insertPreparedStatement.setString(
        7, application.getLastUpdatedBy() != null ? application.getLastUpdatedBy().getName() : null);

    insertPreparedStatement.execute();
  }

  private void updateDataInTimeScaleDB(
      Application application, Connection connection, PreparedStatement updateStatement) throws SQLException {
    updateStatement.setString(1, application.getName());
    updateStatement.setString(2, application.getAccountId());
    updateStatement.setLong(3, application.getCreatedAt());
    updateStatement.setLong(4, application.getLastUpdatedAt());

    String created_by = null;
    if (application.getCreatedBy() != null) {
      created_by = application.getCreatedBy().getName();
    }
    updateStatement.setString(4, created_by);
    updateStatement.setString(
        6, application.getLastUpdatedBy() != null ? application.getLastUpdatedBy().getName() : null);
    updateStatement.setString(7, application.getAppId());

    updateStatement.execute();
  }

  public void deleteFromTimescale(String id) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement deleteStatement = connection.prepareStatement(delete_statement)) {
        deleteStatement.setString(1, id);
        queryResult = deleteStatement.executeQuery();
        successful = true;
      } catch (SQLException e) {
        if (retryCount > MAX_RETRY) {
          log.error("Failed to save application,[{}]", id, e);
        } else {
          log.info("Failed to save application,[{}],retryCount=[{}]", id, retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save application,[{}]", id, e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total time =[{}] for application:[{}]", System.currentTimeMillis() - startTime, id);
      }
    }
  }
}
