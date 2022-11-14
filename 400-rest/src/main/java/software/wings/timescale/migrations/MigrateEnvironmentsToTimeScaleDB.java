/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.timescale.migrations.TimescaleEntityMigrationHelper.deleteFromTimescaleDB;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
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
public class MigrateEnvironmentsToTimeScaleDB implements TimeScaleEntityMigrationInterface {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String upsert_statement =
      "INSERT INTO CG_ENVIRONMENTS (ID,NAME,ACCOUNT_ID,ENV_TYPE,APP_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET NAME = excluded.NAME,ACCOUNT_ID = excluded.ACCOUNT_ID,ENV_TYPE = excluded.ENV_TYPE,APP_ID = excluded.APP_ID,CREATED_AT = excluded.CREATED_AT,LAST_UPDATED_AT = excluded.LAST_UPDATED_AT,CREATED_BY = excluded.CREATED_BY,LAST_UPDATED_BY = excluded.LAST_UPDATED_BY;";

  private static final String TABLE_NAME = "CG_ENVIRONMENTS";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB for CG_ENVIRONMENTS");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_environments = new FindOptions();
      findOptions_environments.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<Environment> iterator =
               new HIterator<>(wingsPersistence.createAnalyticsQuery(Environment.class, excludeAuthority)
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
      log.warn("Failed to complete migration for CG_ENVIRONMENTS", e);
      return false;
    } finally {
      log.info("Completed migrating [{}] records for CG_ENVIRONMENTS", count);
    }
    return true;
  }

  public void saveToTimeScale(Environment environment) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataInTimeScaleDB(environment, upsertStatement);
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
        log.info(
            "Total time =[{}] for environment:[{}]", System.currentTimeMillis() - startTime, environment.getUuid());
      }
    }
  }

  private void upsertDataInTimeScaleDB(Environment environment, PreparedStatement upsertPreparedStatement)
      throws SQLException {
    upsertPreparedStatement.setString(1, environment.getUuid());
    upsertPreparedStatement.setString(2, environment.getName());
    upsertPreparedStatement.setString(3, environment.getAccountId());
    upsertPreparedStatement.setString(4, environment.getEnvironmentType().toString());
    upsertPreparedStatement.setString(5, environment.getAppId());

    upsertPreparedStatement.setLong(6, environment.getCreatedAt());
    upsertPreparedStatement.setLong(7, environment.getLastUpdatedAt());

    String created_by = null;
    if (environment.getCreatedBy() != null) {
      created_by = environment.getCreatedBy().getName();
    }
    upsertPreparedStatement.setString(8, created_by);

    String last_updated_by = null;
    if (environment.getLastUpdatedBy() != null) {
      last_updated_by = environment.getLastUpdatedBy().getName();
    }
    upsertPreparedStatement.setString(9, last_updated_by);

    upsertPreparedStatement.execute();
  }

  public void deleteFromTimescale(String id) {
    deleteFromTimescaleDB(id, timeScaleDBService, MAX_RETRY, TABLE_NAME);
  }

  public String getTimescaleDBClass() {
    return TABLE_NAME;
  }
}
