/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.timescaledb.data;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.TimeScaleDBDataMigration;
import io.harness.persistence.HIterator;
import io.harness.serializer.JsonUtils;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Account;
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
public class AddAccountNameInAccountTable implements TimeScaleDBDataMigration {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String update_statement = "UPDATE ACCOUNTS SET NAME=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM ACCOUNTS WHERE ID=?";

  @Override
  public boolean migrate() {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating deployment data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions = new FindOptions();
      findOptions.readPreference(ReadPreference.secondaryPreferred());
      try (HIterator<Account> iterator =
               new HIterator<>(wingsPersistence.createQuery(Account.class, excludeAuthority).fetch(findOptions))) {
        while (iterator.hasNext()) {
          Account account = iterator.next();
          updateAccount(account);
          count++;
          if (count % 100 == 0) {
            log.info("Completed migrating workflow execution [{}] records", count);
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to complete rollback duration migration", e);
      return false;
    } finally {
      log.info("Completed updating [{}] records", count);
    }
    return true;
  }

  private void updateAccount(Account account) {
    log.info(JsonUtils.asJson(account));
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;

    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement)) {
        queryStatement.setString(1, account.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("Account found:[{}],updating it", account.getUuid());
          updateDataInTimescaleDB(account, updateStatement);
        }

        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to update Account,[{}]", account.getUuid(), e);
        } else {
          log.info("Failed to update Account,[{}],retryCount=[{}]", account.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to update Account,[{}]", account.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total update time =[{}] for Account:[{}]", System.currentTimeMillis() - startTime, account.getUuid());
      }
    }
  }

  private void updateDataInTimescaleDB(Account account, PreparedStatement updateStatement) throws SQLException {
    updateStatement.setString(1, account.getAccountName());
    updateStatement.setString(2, account.getUuid());
    updateStatement.execute();
  }
}
