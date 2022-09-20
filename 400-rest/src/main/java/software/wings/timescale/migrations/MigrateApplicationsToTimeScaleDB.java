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
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Application;
import software.wings.dl.WingsPersistence;

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
public class MigrateApplicationsToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String upsert_statement =
      "INSERT INTO CG_APPLICATIONS (ID,NAME,ACCOUNT_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET NAME = excluded.NAME,ACCOUNT_ID = excluded.ACCOUNT_ID,CREATED_AT = excluded.CREATED_AT,LAST_UPDATED_AT = excluded.LAST_UPDATED_AT,CREATED_BY = excluded.CREATED_BY,LAST_UPDATED_BY = excluded.LAST_UPDATED_BY;";

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
          prepareTimeScaleQueries(application);
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

  private void prepareTimeScaleQueries(Application application) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataInTimeScaleDB(application, upsertStatement);
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
        log.info(
            "Total time =[{}] for application:[{}]", System.currentTimeMillis() - startTime, application.getAppId());
      }
    }
  }

  private void upsertDataInTimeScaleDB(Application application, PreparedStatement upsertPreparedStatement)
      throws SQLException {
    upsertPreparedStatement.setString(1, application.getAppId());
    upsertPreparedStatement.setString(2, application.getName());
    upsertPreparedStatement.setString(3, application.getAccountId());
    upsertPreparedStatement.setLong(4, application.getCreatedAt());
    upsertPreparedStatement.setLong(5, application.getLastUpdatedAt());

    String created_by = null;
    if (application.getCreatedBy() != null) {
      created_by = application.getCreatedBy().getName();
    }
    upsertPreparedStatement.setString(6, created_by);
    upsertPreparedStatement.setString(
        7, application.getLastUpdatedBy() != null ? application.getLastUpdatedBy().getName() : null);

    upsertPreparedStatement.execute();
  }
}
