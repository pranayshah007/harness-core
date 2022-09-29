/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.HarnessTagLink;
import software.wings.beans.HarnessTagLink.HarnessTagLinkKeys;
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
public class MigrateTagLinksToTImeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String upsert_statement =
      "INSERT INTO CG_TAGS (ID,ACCOUNT_ID,APP_ID,TAG_KEY,TAG_VALUE,ENTITY_TYPE,ENTITY_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET ACCOUNT_ID = excluded.ACCOUNT_ID,APP_ID = excluded.APP_ID,TAG_KEY = excluded.TAG_KEY,TAG_VALUE = excluded.TAG_VALUE,ENTITY_TYPE = excluded.ENTITY_TYPE,ENTITY_ID = excluded.ENTITY_ID,CREATED_AT = excluded.CREATED_AT,LAST_UPDATED_AT = excluded.LAST_UPDATED_AT,CREATED_BY = excluded.CREATED_BY,LAST_UPDATED_BY = excluded.LAST_UPDATED_BY;";

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
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataInTimeScaleDB(harnessTagLink, upsertStatement);
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
        log.info(
            "Total time =[{}] for application:[{}]", System.currentTimeMillis() - startTime, harnessTagLink.getUuid());
      }
    }
  }

  private void upsertDataInTimeScaleDB(HarnessTagLink harnessTagLink, PreparedStatement upsertPreparedStatement)
      throws SQLException {
    upsertPreparedStatement.setString(1, harnessTagLink.getUuid());
    upsertPreparedStatement.setString(2, harnessTagLink.getAccountId());
    upsertPreparedStatement.setString(3, harnessTagLink.getAppId());
    upsertPreparedStatement.setString(4, harnessTagLink.getKey());
    upsertPreparedStatement.setString(5, harnessTagLink.getValue());
    upsertPreparedStatement.setString(6, harnessTagLink.getEntityType().name());
    upsertPreparedStatement.setString(7, harnessTagLink.getEntityId());
    upsertPreparedStatement.setLong(8, harnessTagLink.getCreatedAt());
    upsertPreparedStatement.setLong(9, harnessTagLink.getLastUpdatedAt());
    upsertPreparedStatement.setString(
        10, harnessTagLink.getCreatedBy() != null ? harnessTagLink.getCreatedBy().getName() : null);
    upsertPreparedStatement.setString(
        11, harnessTagLink.getLastUpdatedBy() != null ? harnessTagLink.getLastUpdatedBy().getName() : null);

    upsertPreparedStatement.execute();
  }
}
