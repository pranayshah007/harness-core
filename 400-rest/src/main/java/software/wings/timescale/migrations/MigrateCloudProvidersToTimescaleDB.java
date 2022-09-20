/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
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
public class MigrateCloudProvidersToTimescaleDB {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String upsert_statement =
      "INSERT INTO CG_CLOUD_PROVIDERS (ID,NAME,ACCOUNT_ID,APP_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET NAME = excluded.NAME,ACCOUNT_ID = excluded.ACCOUNT_ID,APP_ID = excluded.APP_ID,CREATED_AT = excluded.CREATED_AT,LAST_UPDATED_AT = excluded.LAST_UPDATED_AT,CREATED_BY = excluded.CREATED_BY,LAST_UPDATED_BY = excluded.LAST_UPDATED_BY;";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_cloud_providers = new FindOptions();
      findOptions_cloud_providers.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<SettingAttribute> iterator =
               new HIterator<>(wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority)
                                   .filter(SettingAttributeKeys.category, CLOUD_PROVIDER)
                                   .field(SettingAttributeKeys.accountId)
                                   .equal(accountId)
                                   .fetch(findOptions_cloud_providers))) {
        while (iterator.hasNext()) {
          SettingAttribute settingAttribute = iterator.next();
          prepareTimeScaleQueries(settingAttribute);
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

  private void prepareTimeScaleQueries(SettingAttribute settingAttribute) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        if (settingAttribute.getCategory().equals(CLOUD_PROVIDER)) {
          upsertDataInTimeScaleDB(settingAttribute, upsertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save CloudProvider,[{}]", settingAttribute.getUuid(), e);
        } else {
          log.info("Failed to save CloudProvider,[{}],retryCount=[{}]", settingAttribute.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save CloudProvider,[{}]", settingAttribute.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        log.info("Total time =[{}] for CloudProvider:[{}]", System.currentTimeMillis() - startTime,
            settingAttribute.getUuid());
      }
    }
  }

  private void upsertDataInTimeScaleDB(SettingAttribute settingAttribute, PreparedStatement upsertPreparedStatement)
      throws SQLException {
    upsertPreparedStatement.setString(1, settingAttribute.getUuid());
    upsertPreparedStatement.setString(2, settingAttribute.getName());
    upsertPreparedStatement.setString(3, settingAttribute.getAccountId());
    upsertPreparedStatement.setString(4, settingAttribute.getAppId());

    upsertPreparedStatement.setLong(5, settingAttribute.getCreatedAt());
    upsertPreparedStatement.setLong(6, settingAttribute.getLastUpdatedAt());

    String created_by = null;
    if (settingAttribute.getCreatedBy() != null) {
      created_by = settingAttribute.getCreatedBy().getName();
    }
    upsertPreparedStatement.setString(7, created_by);

    String last_updated_by = null;
    if (settingAttribute.getLastUpdatedBy() != null) {
      last_updated_by = settingAttribute.getLastUpdatedBy().getName();
    }
    upsertPreparedStatement.setString(8, last_updated_by);

    upsertPreparedStatement.execute();
  }
}
