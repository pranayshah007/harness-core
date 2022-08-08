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

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

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
public class MigrateUsersToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject WingsPersistence wingsPersistence;
  @Inject AccountService accountService;

  private static final int MAX_RETRY = 5;

  private static final String insert_statement =
      "INSERT INTO CG_USERS (ID,NAME,ACCOUNT_IDS,EMAIL,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE CG_USERS SET NAME=?, ACCOUNT_ID=?, EMAIL=?, CREATED_AT=?, LAST_UPDATED_AT=?, CREATED_BY=?, LAST_UPDATED_BY=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM CG_USERS WHERE ID=?";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_users = new FindOptions();
      findOptions_users.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<User> iterator = new HIterator<>(wingsPersistence.createQuery(User.class, excludeAuthority)
                                                          .field(UserKeys.accounts)
                                                          .contains(accountId)
                                                          .fetch(findOptions_users))) {
        while (iterator.hasNext()) {
          User user = iterator.next();
          prepareTimeScaleQueries(user);
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

  private void prepareTimeScaleQueries(User user) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, user.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("User found in the timescaleDB:[{}],updating it", user.getUuid());
          updateDataInTimeScaleDB(user, connection, updateStatement);
        } else {
          log.info("User not found in the timescaleDB:[{}],inserting it", user.getUuid());
          insertDataInTimeScaleDB(user, connection, insertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save user,[{}]", user.getUuid(), e);
        } else {
          log.info("Failed to save user,[{}],retryCount=[{}]", user.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save user,[{}]", user.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total time =[{}] for user:[{}]", System.currentTimeMillis() - startTime, user.getUuid());
      }
    }
  }

  private void insertDataInTimeScaleDB(User user, Connection connection, PreparedStatement insertPreparedStatement)
      throws SQLException {
    insertPreparedStatement.setString(1, user.getUuid());
    insertPreparedStatement.setString(2, user.getName());
    insertArrayData(3, connection, insertPreparedStatement, user.getAccountIds());
    insertPreparedStatement.setString(4, user.getEmail());

    insertPreparedStatement.setLong(5, user.getCreatedAt());
    insertPreparedStatement.setLong(6, user.getLastUpdatedAt());

    String created_by = null;
    if (user.getCreatedBy() != null) {
      created_by = user.getCreatedBy().getName();
    }
    insertPreparedStatement.setString(7, created_by);

    String last_updated_by = null;
    if (user.getLastUpdatedBy() != null) {
      last_updated_by = user.getLastUpdatedBy().getName();
    }
    insertPreparedStatement.setString(8, last_updated_by);

    insertPreparedStatement.execute();
  }

  private void updateDataInTimeScaleDB(User user, Connection connection, PreparedStatement updateStatement)
      throws SQLException {
    updateStatement.setString(1, user.getName());
    insertArrayData(2, connection, updateStatement, user.getAccountIds());
    updateStatement.setString(3, user.getEmail());

    updateStatement.setLong(4, user.getCreatedAt());
    updateStatement.setLong(5, user.getLastUpdatedAt());

    String created_by = null;
    if (user.getCreatedBy() != null) {
      created_by = user.getCreatedBy().getName();
    }
    updateStatement.setString(7, created_by);

    String last_updated_by = null;
    if (user.getLastUpdatedBy() != null) {
      last_updated_by = user.getLastUpdatedBy().getName();
    }
    updateStatement.setString(8, last_updated_by);

    updateStatement.setString(9, user.getUuid());

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
