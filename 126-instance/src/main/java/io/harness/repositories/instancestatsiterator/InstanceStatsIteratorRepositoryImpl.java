/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instancestatsiterator;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.models.InstanceStatsIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class InstanceStatsIteratorRepositoryImpl implements InstanceStatsIteratorRepository {
  private TimeScaleDBService timeScaleDBService;
  private CDFeatureFlagHelper cdFeatureFlagHelper;
  private static final int MAX_RETRY_COUNT = 3;
  private static final int BASE_DELAY_MILLIS = 1000;

  public InstanceStatsIterator getLatestRecord(String accountId, String orgId, String projectId, String serviceId)
      throws Exception {
    int totalTries = 0;
    while (totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = getScopedStatement(dbConnection, accountId, orgId, projectId, serviceId)) {
        resultSet = statement.executeQuery();
        return parseInstanceStatsIteratorRecord(resultSet);
      } catch (SQLException ex) {
        if (totalTries == MAX_RETRY_COUNT) {
          log.error("Error while fetching latest instance stats iterator record after all retries");
          throw ex;
        }
        log.warn(
            "Could not fetch latest instance stats iterator record. Retrying again. Retry number: {}", totalTries, ex);
        totalTries++;
      } catch (Exception ex) {
        log.error("Error while fetching latest instance stats iterator record");
        throw ex;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  public void updateTimestampForIterator(
      String accountId, String orgId, String projectId, String serviceId, long timestamp) {
    if (cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_STORE_INSTANCE_STATS_ITERATOR_RUN_TIME)) {
      boolean successfulOperation = false;
      int totalTries = 0;
      while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement =
                 getScopedStatementForUpdate(dbConnection, accountId, orgId, projectId, serviceId, timestamp)) {
          statement.execute();
          successfulOperation = true;
        } catch (SQLException ex) {
          if (totalTries == MAX_RETRY_COUNT) {
            log.error("Error while updating timestamp for instance stats iterator after all retries");
          }
          log.warn("Could not update timestamp for instance stats iterator. Retrying again. Retry number: {}",
              totalTries, ex);
          sleep(totalTries);
          totalTries++;
        } catch (Exception ex) {
          log.error("Error while updating timestamp for instance stats iterator", ex);
          sleep(totalTries);
          totalTries++;
        }
      }
    }
  }

  // ------------------------------- PRIVATE METHODS ------------------------------
  private void sleep(int totalTries) {
    int delayMillis = (int) (BASE_DELAY_MILLIS * Math.pow(2, totalTries));
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private InstanceStatsIterator parseInstanceStatsIteratorRecord(ResultSet resultSet) throws SQLException {
    if (resultSet == null || !resultSet.next()) {
      return null;
    }
    return InstanceStatsIterator.builder()
        .accountId(resultSet.getString(InstanceStatsIteratorFields.ACCOUNTID.fieldName()))
        .serviceId(resultSet.getString(InstanceStatsIteratorFields.SERVICEID.fieldName()))
        .reportedAt(resultSet.getTimestamp(InstanceStatsIteratorFields.REPORTEDAT.fieldName()))
        .build();
  }

  /**
   *
   * @param dbConnection connection session with a db
   * @param accountId account of service
   * @param orgId organisation of service
   * @param projectId project of service
   * @param serviceId identifier of service
   * @return statement to get instance stats by using service ref and scoped query
   * @throws SQLException exception
   */
  private PreparedStatement getScopedStatement(
      Connection dbConnection, String accountId, String orgId, String projectId, String serviceId) throws SQLException {
    IdentifierRef serviceIdentifierRef =
        IdentifierRefHelper.getIdentifierRefWithScope(accountId, orgId, projectId, serviceId);
    PreparedStatement statement = null;
    switch (serviceIdentifierRef.getScope()) {
      case ACCOUNT:
        statement = dbConnection.prepareStatement(InstanceStatsIteratorQuery.FETCH_LATEST_RECORD_ACCOUNT_LEVEL.query());
        statement.setString(1, accountId);
        statement.setString(2, serviceIdentifierRef.buildScopedIdentifier());
        break;
      case ORG:
        statement = dbConnection.prepareStatement(InstanceStatsIteratorQuery.FETCH_LATEST_RECORD_ORG_LEVEL.query());
        statement.setString(1, accountId);
        statement.setString(2, orgId);
        statement.setString(3, serviceIdentifierRef.buildScopedIdentifier());
        break;
      case PROJECT:
        statement = dbConnection.prepareStatement(InstanceStatsIteratorQuery.FETCH_LATEST_RECORD_PROJECT_LEVEL.query());
        statement.setString(1, accountId);
        statement.setString(2, orgId);
        statement.setString(3, projectId);
        statement.setString(4, serviceIdentifierRef.buildScopedIdentifier());
        break;
      default:
        throw new InvalidRequestException(
            format("Unknown scope : %s encountered while preparing statement for instance stats",
                serviceIdentifierRef.getScope().toString()));
    }

    return statement;
  }

  /**
   *
   * @param dbConnection connection session with a db
   * @param accountId account of service
   * @param orgId organisation of service
   * @param projectId project of service
   * @param serviceId identifier of service
   * @param timestamp timestamp
   * @return statement to get instance stats by using service ref and scoped query
   */
  @VisibleForTesting
  private PreparedStatement getScopedStatementForUpdate(Connection dbConnection, String accountId, String orgId,
      String projectId, String serviceId, long timestamp) throws SQLException {
    IdentifierRef serviceIdentifierRef =
        IdentifierRefHelper.getIdentifierRefWithScope(accountId, orgId, projectId, serviceId);
    PreparedStatement statement = dbConnection.prepareStatement(InstanceStatsIteratorQuery.UPDATE_RECORD.query());
    statement.setTimestamp(1, new Timestamp(timestamp), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
    statement.setString(2, accountId);
    statement.setString(5, serviceIdentifierRef.buildScopedIdentifier());
    statement.setTimestamp(6, new Timestamp(timestamp), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
    switch (serviceIdentifierRef.getScope()) {
      case ACCOUNT:
        statement.setString(3, "null");
        statement.setString(4, "null");
        break;
      case ORG:
        statement.setString(3, orgId);
        statement.setString(4, "null");
        break;
      case PROJECT:
        statement.setString(3, orgId);
        statement.setString(4, projectId);
        break;
      default:
        throw new InvalidRequestException(
            String.format("Unknown scope : %s encountered while preparing statement for instance stats iterator",
                serviceIdentifierRef.getScope().toString()));
    }

    return statement;
  }
}
