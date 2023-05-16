/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.instancestats;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.exception.InvalidRequestException;
import io.harness.models.InstanceStats;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class InstanceStatsRepositoryImpl implements InstanceStatsRepository {
  private TimeScaleDBService timeScaleDBService;
  private static final int MAX_RETRY_COUNT = 3;

  public InstanceStats getLatestRecord(String accountId, String orgId, String projectId, String serviceId)
      throws Exception {
    int totalTries = 0;
    while (totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = getScopedStatement(dbConnection, accountId, orgId, projectId, serviceId)) {
        resultSet = statement.executeQuery();
        return parseInstanceStatsRecord(resultSet);
      } catch (SQLException ex) {
        if (totalTries == MAX_RETRY_COUNT) {
          log.error("Error while fetching latest instance stats record after all retries");
          throw ex;
        }
        log.warn("Could not fetch latest instance stats record. Retrying again. Retry number: {}", totalTries, ex);
        totalTries++;
      } catch (Exception ex) {
        log.error("Error while fetching latest instance stats record");
        throw ex;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  // ------------------------------- PRIVATE METHODS ------------------------------
  private InstanceStats parseInstanceStatsRecord(ResultSet resultSet) throws SQLException {
    while (resultSet != null && resultSet.next()) {
      return InstanceStats.builder()
          .accountId(
              resultSet.getString(io.harness.repositories.instancestats.InstanceStatsFields.ACCOUNTID.fieldName()))
          .envId(resultSet.getString(io.harness.repositories.instancestats.InstanceStatsFields.ENVID.fieldName()))
          .serviceId(
              resultSet.getString(io.harness.repositories.instancestats.InstanceStatsFields.SERVICEID.fieldName()))
          .reportedAt(resultSet.getTimestamp(InstanceStatsFields.REPORTEDAT.fieldName()))
          .build();
    }
    return null;
  }

  /**
   *
   * @param dbConnection connection session with a db
   * @param accountId account of service
   * @param orgId organisation of service
   * @param projectId project of service
   * @param serviceId identifier of service
   * @return statement to get instance stats by using service ref and scoped query
   * @throws SQLException
   */
  private PreparedStatement getScopedStatement(
      Connection dbConnection, String accountId, String orgId, String projectId, String serviceId) throws SQLException {
    IdentifierRef serviceIdentifierRef =
        IdentifierRefHelper.getIdentifierRefWithScope(accountId, orgId, projectId, serviceId);
    PreparedStatement statement = null;
    switch (serviceIdentifierRef.getScope()) {
      case ACCOUNT:
        statement = dbConnection.prepareStatement(InstanceStatsQuery.FETCH_LATEST_RECORD_ACCOUNT_LEVEL.query());
        statement.setString(1, accountId);
        statement.setString(2, serviceIdentifierRef.buildScopedIdentifier());
        break;
      case ORG:
        statement = dbConnection.prepareStatement(InstanceStatsQuery.FETCH_LATEST_RECORD_ORG_LEVEL.query());
        statement.setString(1, accountId);
        statement.setString(2, orgId);
        statement.setString(3, serviceIdentifierRef.buildScopedIdentifier());
        break;
      case PROJECT:
        statement = dbConnection.prepareStatement(InstanceStatsQuery.FETCH_LATEST_RECORD_PROJECT_LEVEL.query());
        statement.setString(1, accountId);
        statement.setString(2, orgId);
        statement.setString(3, projectId);
        statement.setString(4, serviceIdentifierRef.buildScopedIdentifier());
        break;
      default:
        throw new InvalidRequestException(
            String.format("Unknown scope : %s encountered while preparing statement for instance stats",
                serviceIdentifierRef.getScope().toString()));
    }

    return statement;
  }
}
