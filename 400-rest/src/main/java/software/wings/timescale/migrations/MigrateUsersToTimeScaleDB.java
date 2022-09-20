/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.timescale.migrations.TimescaleEntityMigrationHelper.insertArrayData;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;

@Slf4j
@Singleton
public class MigrateUsersToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject WingsPersistence wingsPersistence;
  @Inject AccountService accountService;

  private static final int MAX_RETRY = 5;

  private static final String upsert_statement =
      "INSERT INTO CG_USERS (ID,NAME,ACCOUNT_IDS,EMAIL,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET NAME = excluded.NAME,ACCOUNT_IDS = excluded.ACCOUNT_IDS,EMAIL = excluded.EMAIL,CREATED_AT = excluded.CREATED_AT,LAST_UPDATED_AT = excluded.LAST_UPDATED_AT,CREATED_BY = excluded.CREATED_BY,LAST_UPDATED_BY = excluded.LAST_UPDATED_BY;";

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
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataInTimeScaleDB(user, connection, upsertStatement);
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
        log.info("Total time =[{}] for user:[{}]", System.currentTimeMillis() - startTime, user.getUuid());
      }
    }
  }

  private void upsertDataInTimeScaleDB(User user, Connection connection, PreparedStatement upsertPreparedStatement)
      throws SQLException {
    upsertPreparedStatement.setString(1, user.getUuid());
    upsertPreparedStatement.setString(2, user.getName());
    insertArrayData(3, connection, upsertPreparedStatement, user.getAccountIds());
    upsertPreparedStatement.setString(4, user.getEmail());

    upsertPreparedStatement.setLong(5, user.getCreatedAt());
    upsertPreparedStatement.setLong(6, user.getLastUpdatedAt());

    String created_by = null;
    if (user.getCreatedBy() != null) {
      created_by = user.getCreatedBy().getName();
    }
    upsertPreparedStatement.setString(7, created_by);

    String last_updated_by = null;
    if (user.getLastUpdatedBy() != null) {
      last_updated_by = user.getLastUpdatedBy().getName();
    }
    upsertPreparedStatement.setString(8, last_updated_by);

    upsertPreparedStatement.execute();
  }
}
