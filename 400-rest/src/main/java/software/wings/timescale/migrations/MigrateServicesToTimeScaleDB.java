/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Service.ServiceKeys;
import static software.wings.timescale.migrations.TimescaleEntityMigrationHelper.insertArrayData;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Service;
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
public class MigrateServicesToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String upsert_statement =
      "INSERT INTO CG_SERVICES (ID,NAME,ARTIFACT_TYPE,VERSION,ACCOUNT_ID,APP_ID,ARTIFACT_STREAM_IDS,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY,DEPLOYMENT_TYPE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON  CONFLICT(ID) DO UPDATE SET NAME = excluded.NAME, ARTIFACT_TYPE = excluded.ARTIFACT_TYPE, VERSION = excluded.VERSION, ACCOUNT_ID = excluded.ACCOUNT_ID, APP_ID = excluded.APP_ID, ARTIFACT_STREAM_IDS = excluded.ARTIFACT_STREAM_IDS, CREATED_AT = excluded.CREATED_AT, LAST_UPDATED_AT = excluded.LAST_UPDATED_AT, CREATED_BY = excluded.CREATED_BY, LAST_UPDATED_BY = excluded.LAST_UPDATED_BY, DEPLOYMENT_TYPE = excluded.DEPLOYMENT_TYPE;";

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
          prepareTimeScaleQueries(service);
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

  private void prepareTimeScaleQueries(Service service) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataInTimeScaleDB(service, connection, upsertStatement);
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
        log.info("Total time =[{}] for service:[{}]", System.currentTimeMillis() - startTime, service.getUuid());
      }
    }
  }

  private void upsertDataInTimeScaleDB(
      Service service, Connection connection, PreparedStatement upsertPreparedStatement) throws SQLException {
    upsertPreparedStatement.setString(1, service.getUuid());
    upsertPreparedStatement.setString(2, service.getName());
    upsertPreparedStatement.setString(3, service.getArtifactType().toString());
    upsertPreparedStatement.setLong(4, service.getVersion());
    upsertPreparedStatement.setString(5, service.getAccountId());
    upsertPreparedStatement.setString(6, service.getAppId());
    insertArrayData(7, connection, upsertPreparedStatement, service.getArtifactStreamIds());
    upsertPreparedStatement.setLong(8, service.getCreatedAt());
    upsertPreparedStatement.setLong(9, service.getLastUpdatedAt());

    String created_by = null;
    if (service.getCreatedBy() != null) {
      created_by = service.getCreatedBy().getName();
    }
    upsertPreparedStatement.setString(10, created_by);

    String last_updated_by = null;
    if (service.getLastUpdatedBy() != null) {
      last_updated_by = service.getLastUpdatedBy().getName();
    }
    upsertPreparedStatement.setString(11, last_updated_by);
    upsertPreparedStatement.setString(
        12, service.getDeploymentType() != null ? service.getDeploymentType().getDisplayName() : null);

    upsertPreparedStatement.execute();
  }
}
