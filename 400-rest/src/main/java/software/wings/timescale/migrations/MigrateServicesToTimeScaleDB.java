/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Service.ServiceKeys;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Service;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import io.fabric8.utils.Lists;
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
public class MigrateServicesToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String insert_statement =
      "INSERT INTO CG_SERVICES (ID,NAME,ARTIFACT_TYPE,VERSION,ACCOUNT_ID,APP_ID,ARTIFACT_STREAM_IDS,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY,DEPLOYMENT_TYPE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE CG_SERVICES SET NAME=?, ARTIFACT_TYPE=?, VERSION=?, ACCOUNT_ID=?, APP_ID=?, ARTIFACT_STREAM_IDS=?, CREATED_AT=?, LAST_UPDATED_AT=?, CREATED_BY=?, LAST_UPDATED_BY=?, DEPLOYMENT_TYPE=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM CG_SERVICES WHERE ID=?";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_services = new FindOptions();
      findOptions_services.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<Service> iterator = new HIterator<>(wingsPersistence.createQuery(Service.class, excludeAuthority)
                                                             .field(ServiceKeys.accountId)
                                                             .equal(accountId)
                                                             .fetch(findOptions_services))) {
        while (iterator.hasNext()) {
          Service service = iterator.next();
          saveToTimeScale(service);
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

  public void saveToTimeScale(Service service) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, service.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("Service found in the timescaleDB:[{}],updating it", service.getUuid());
          updateDataInTimeScaleDB(service, connection, updateStatement);
        } else {
          log.info("Service not found in the timescaleDB:[{}],inserting it", service.getUuid());
          insertDataInTimeScaleDB(service, connection, insertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save service,[{}]", service.getUuid(), e);
        } else {
          log.info("Failed to save service,[{}],retryCount=[{}]", service.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save service,[{}]", service.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total time =[{}] for service:[{}]", System.currentTimeMillis() - startTime, service.getUuid());
      }
    }
  }

  private void insertDataInTimeScaleDB(
      Service service, Connection connection, PreparedStatement insertPreparedStatement) throws SQLException {
    insertPreparedStatement.setString(1, service.getUuid());
    insertPreparedStatement.setString(2, service.getName());
    insertPreparedStatement.setString(3, service.getArtifactType().toString());
    insertPreparedStatement.setLong(4, service.getVersion());
    insertPreparedStatement.setString(5, service.getAccountId());
    insertPreparedStatement.setString(6, service.getAppId());
    insertArrayData(7, connection, insertPreparedStatement, service.getArtifactStreamIds());
    insertPreparedStatement.setLong(8, service.getCreatedAt());
    insertPreparedStatement.setLong(9, service.getLastUpdatedAt());

    String created_by = null;
    if (service.getCreatedBy() != null) {
      created_by = service.getCreatedBy().getName();
    }
    insertPreparedStatement.setString(10, created_by);

    String last_updated_by = null;
    if (service.getLastUpdatedBy() != null) {
      last_updated_by = service.getLastUpdatedBy().getName();
    }
    insertPreparedStatement.setString(11, last_updated_by);
    insertPreparedStatement.setString(12, service.getDeploymentType().getDisplayName());

    insertPreparedStatement.execute();
  }

  private void updateDataInTimeScaleDB(Service service, Connection connection, PreparedStatement updateStatement)
      throws SQLException {
    updateStatement.setString(1, service.getName());
    updateStatement.setString(2, service.getArtifactType().toString());
    updateStatement.setLong(3, service.getVersion());
    updateStatement.setString(4, service.getAccountId());
    updateStatement.setString(5, service.getAppId());
    insertArrayData(6, connection, updateStatement, service.getArtifactStreamIds());
    updateStatement.setLong(7, service.getCreatedAt());
    updateStatement.setLong(8, service.getLastUpdatedAt());

    String created_by = null;
    if (service.getCreatedBy() != null) {
      created_by = service.getCreatedBy().getName();
    }
    updateStatement.setString(9, created_by);

    String last_updated_by = null;
    if (service.getLastUpdatedBy() != null) {
      last_updated_by = service.getLastUpdatedBy().getName();
    }
    updateStatement.setString(10, last_updated_by);
    updateStatement.setString(11, service.getDeploymentType().getDisplayName());
    updateStatement.setString(12, service.getUuid());

    updateStatement.execute();
  }

  private void insertArrayData(
      int index, Connection dbConnection, PreparedStatement preparedStatement, List<String> data) throws SQLException {
    if (!Lists.isNullOrEmpty(data)) {
      Array array = dbConnection.createArrayOf("text", data.toArray());
      preparedStatement.setArray(index, array);
    } else {
      preparedStatement.setArray(index, null);
    }
  }
}
