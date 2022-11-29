/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.timescaledb.data;

import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.migrations.TimeScaleDBDataMigration;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AddNewAndRemoveUnusedIndexesFromDeployment implements TimeScaleDBDataMigration {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject FeatureFlagService featureFlagService;

  private static final int MAX_RETRY = 10;
  private static final String REMOVAL_TIME_LOG = "Total removal time =[{}]";
  private static final String DROP_INDEX_1 = "DROP INDEX CONCURRENTLY IF EXISTS deployment_artifacts_gin_index";
  private static final String DROP_INDEX_2 = "DROP INDEX CONCURRENTLY IF EXISTS deployment_stagename_index";
  private static final String DROP_INDEX_3 = "DROP INDEX CONCURRENTLY IF EXISTS deployment_tags_gin_index";
  private static final String DROP_INDEX_4 = "DROP INDEX CONCURRENTLY IF EXISTS deployment_trigger_id_index";
  private static final String DROP_INDEX_5 = "DROP INDEX CONCURRENTLY IF EXISTS deployment_workflows_gin_index";
  private static final String DROP_INDEX_6 = "DROP INDEX CONCURRENTLY IF EXISTS deployment_env_type_index";
  private static final String CREATE_INDEX_1 =
      "CREATE INDEX IF NOT EXISTS deployment_accountid_idx ON deployment(accountid) WITH (timescaledb.transaction_per_chunk)";
  private static final String CREATE_INDEX_2 =
      "CREATE INDEX IF NOT EXISTS deployment_starttime_idx ON deployment(starttime DESC) WITH (timescaledb.transaction_per_chunk)";

  @Override
  public boolean migrate() {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating deployment data to TimeScaleDB");
      return false;
    }
    try {
      List<String> accountIds =
          featureFlagService.getAccountIds(FeatureName.TIME_SCALE_CG_SYNC).stream().collect(Collectors.toList());
      for (String accountId : accountIds) {
        log.info("Migration for removal of indexes began for account " + accountId);
        dropIndex1();
        dropIndex2();
        dropIndex3();
        dropIndex4();
        dropIndex5();
        dropIndex6();
        createIndex1();
        createIndex2();
      }
    } catch (Exception e) {
      log.warn("Exception caught while migration", e);
      return false;
    }
    return true;
  }

  private void dropIndex1() {
    boolean successful = false;
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    log.info("Beginning removal of index 1");

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement updateStatement = connection.prepareStatement(DROP_INDEX_1)) {
        updateStatement.execute();
        successful = true;
      } catch (Exception e) {
        log.info("Failed to drop index 1, retryCount=[{}]", retryCount, e);
        retryCount++;
      } finally {
        log.info(REMOVAL_TIME_LOG, System.currentTimeMillis() - startTime);
      }
    }
  }

  private void dropIndex2() {
    boolean successful = false;
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    log.info("Beginning removal of index 2");

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement updateStatement = connection.prepareStatement(DROP_INDEX_2)) {
        updateStatement.execute();
        successful = true;
      } catch (Exception e) {
        log.info("Failed to drop index 2, retryCount=[{}]", retryCount, e);
        retryCount++;
      } finally {
        log.info(REMOVAL_TIME_LOG, System.currentTimeMillis() - startTime);
      }
    }
  }

  private void dropIndex3() {
    boolean successful = false;
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    log.info("Beginning removal of index 3");

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement updateStatement = connection.prepareStatement(DROP_INDEX_3)) {
        updateStatement.execute();
        successful = true;
      } catch (Exception e) {
        log.info("Failed to drop index 3, retryCount=[{}]", retryCount, e);
        retryCount++;
      } finally {
        log.info(REMOVAL_TIME_LOG, System.currentTimeMillis() - startTime);
      }
    }
  }

  private void dropIndex4() {
    boolean successful = false;
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    log.info("Beginning removal of index 4");

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement updateStatement = connection.prepareStatement(DROP_INDEX_4)) {
        updateStatement.execute();
        successful = true;
      } catch (Exception e) {
        log.info("Failed to drop index 4, retryCount=[{}]", retryCount, e);
        retryCount++;
      } finally {
        log.info(REMOVAL_TIME_LOG, System.currentTimeMillis() - startTime);
      }
    }
  }

  private void dropIndex5() {
    boolean successful = false;
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    log.info("Beginning removal of index 5");

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement updateStatement = connection.prepareStatement(DROP_INDEX_5)) {
        updateStatement.execute();
        successful = true;
      } catch (Exception e) {
        log.info("Failed to drop index 5, retryCount=[{}]", retryCount, e);
        retryCount++;
      } finally {
        log.info(REMOVAL_TIME_LOG, System.currentTimeMillis() - startTime);
      }
    }
  }

  private void dropIndex6() {
    boolean successful = false;
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    log.info("Beginning removal of index 6");

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement updateStatement = connection.prepareStatement(DROP_INDEX_6)) {
        updateStatement.execute();
        successful = true;
      } catch (Exception e) {
        log.info("Failed to drop index 6, retryCount=[{}]", retryCount, e);
        retryCount++;
      } finally {
        log.info(REMOVAL_TIME_LOG, System.currentTimeMillis() - startTime);
      }
    }
  }

  private void createIndex1() {
    boolean successful = false;
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    log.info("Beginning addition of index 1");

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement updateStatement = connection.prepareStatement(CREATE_INDEX_1)) {
        updateStatement.execute();
        successful = true;
      } catch (Exception e) {
        log.info("Failed to add index 1, retryCount=[{}]", retryCount, e);
        retryCount++;
      } finally {
        log.info("Total addition time =[{}]", System.currentTimeMillis() - startTime);
      }
    }
  }

  private void createIndex2() {
    boolean successful = false;
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    log.info("Beginning addition of index 2");

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement updateStatement = connection.prepareStatement(CREATE_INDEX_2)) {
        updateStatement.execute();
        successful = true;
      } catch (Exception e) {
        log.info("Failed to add index 2, retryCount=[{}]", retryCount, e);
        retryCount++;
      } finally {
        log.info("Total addition time =[{}]", System.currentTimeMillis() - startTime);
      }
    }
  }
}
