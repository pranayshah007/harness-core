/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdlicense.exception.CgLicenseUsageException;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class CdLicenseUsageUtils {
  @Inject TimeScaleDBService timeScaleDBService;

  public static final double INSTANCE_COUNT_PERCENTILE_DISC = 0.95;
  public static final int MAX_RETRY = 3;
  public static final String QUERY_FETCH_SERVICE_INSTANCE_USAGE = ""
      + "select percentile_disc(?) within group (order by instanceCountsPerDay.totalInstancesPerDay) as percentileInstanceCount\n"
      + "from (\n"
      + "    select time_bucket('1 day', instancesPerDay.collectionTime) as collectionDay,\n"
      + "        sum(instancesPerDay.totalPerDay) as totalInstancesPerDay\n"
      + "    from (\n"
      + "        select time_bucket('1 day', instancesPerReportedAt.reportedat) as collectionTime,\n"
      + "            percentile_disc(0.95) within group (order by instancesPerReportedAt.instancecount) AS totalPerDay,\n"
      + "            instancesPerReportedAt.orgid, instancesPerReportedAt.projectid, instancesPerReportedAt.serviceid\n"
      + "        from (\n"
      + "            select  date_trunc('minute', reportedat) as reportedat, orgid, projectid, serviceid, sum(instancecount) as instancecount\n"
      + "            from \n"
      + "                ng_instance_stats \n"
      + "            where accountid = ?\n"
      + "                and reportedat > now() - INTERVAL '30 day' \n"
      + "            group by orgid, projectid, serviceid, date_trunc('minute', reportedat)\n"
      + "        ) as instancesPerReportedAt\n"
      + "        group by collectionTime, instancesPerReportedAt.orgid, instancesPerReportedAt.projectid, instancesPerReportedAt.serviceid\n"
      + "    ) as instancesPerDay\n"
      + "    group by collectionDay\n"
      + ") as instanceCountsPerDay";
  public static final String QUERY_FETCH_INSTANCES_PER_SERVICE = ""
      + "select percentile_disc(?) within group (order by instancesPerServicePerDay.totalPerDay) as instanceCount,\n"
      + "    orgid, projectid, serviceid\n"
      + "from (\n"
      + "    select time_bucket('1 day', instancesPerReportedAt.reportedat) as collectionTime,\n"
      + "        percentile_disc(0.95) within group (order by instancesPerReportedAt.instancecount) AS totalPerDay,\n"
      + "        instancesPerReportedAt.orgid, instancesPerReportedAt.projectid, instancesPerReportedAt.serviceid\n"
      + "    from (\n"
      + "        select  date_trunc('minute', reportedat) as reportedat, orgid, projectid, serviceid, sum(instancecount) as instancecount\n"
      + "        from \n"
      + "            ng_instance_stats \n"
      + "        where accountid = ?\n"
      + "            and reportedat > now() - INTERVAL '30 day' \n"
      + "        group by orgid, projectid, serviceid, date_trunc('minute', reportedat)\n"
      + "    ) as instancesPerReportedAt\n"
      + "    group by collectionTime, instancesPerReportedAt.orgid, instancesPerReportedAt.projectid, instancesPerReportedAt.serviceid\n"
      + ") as instancesPerServicePerDay\n"
      + "group by orgid, projectid, serviceid";

  public long fetchServiceInstancesOver30Days(String accountId) {
    if (StringUtils.isEmpty(accountId)) {
      log.error("AccountId: [{}] cannot be empty for finding service instances", accountId);
      return 0L;
    }

    int retry = 0;
    boolean successfulOperation = false;
    long percentileInstanceCount = 0L;
    while (!successfulOperation && retry <= MAX_RETRY) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchStatement = dbConnection.prepareStatement(QUERY_FETCH_SERVICE_INSTANCE_USAGE)) {
        fetchStatement.setDouble(1, INSTANCE_COUNT_PERCENTILE_DISC);
        fetchStatement.setString(2, accountId);

        ResultSet resultSet = fetchStatement.executeQuery();
        if (Objects.nonNull(resultSet) && resultSet.next()) {
          percentileInstanceCount = resultSet.getLong(1);
        } else {
          log.warn("No service instances count found for accountId: [{}]", accountId);
        }

        successfulOperation = true;
      } catch (SQLException exception) {
        if (retry >= MAX_RETRY) {
          String errorLog = "MAX RETRY FAILURE: Failed to fetch service instance usage after " + MAX_RETRY + " retries";
          throw new CgLicenseUsageException(errorLog, exception);
        }
        log.error(
            "Failed to fetch service instance usage for accountId : [{}] , retry : [{}]", accountId, retry, exception);
        retry++;
      }
    }

    return percentileInstanceCount;
  }

  public List<AggregateServiceUsageInfo> fetchInstancesPerServiceOver30Days(String accountId) {
    if (StringUtils.isEmpty(accountId)) {
      log.error("AccountId: [{}] cannot be empty for finding service instances", accountId);
      return Collections.emptyList();
    }

    int retry = 0;
    boolean successfulOperation = false;
    List<AggregateServiceUsageInfo> instanceCountPerService = new ArrayList<>();
    while (!successfulOperation && retry <= MAX_RETRY) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchStatement = dbConnection.prepareStatement(QUERY_FETCH_INSTANCES_PER_SERVICE)) {
        fetchStatement.setDouble(1, INSTANCE_COUNT_PERCENTILE_DISC);
        fetchStatement.setString(2, accountId);

        ResultSet resultSet = fetchStatement.executeQuery();
        instanceCountPerService = processResultSet(resultSet);
        successfulOperation = true;
      } catch (SQLException exception) {
        if (retry >= MAX_RETRY) {
          String errorLog = "MAX RETRY FAILURE: Failed to fetch service instance usage after " + MAX_RETRY + " retries";
          throw new CgLicenseUsageException(errorLog, exception);
        }
        log.error(
            "Failed to fetch service instance usage for accountId : [{}] , retry : [{}]", accountId, retry, exception);
        retry++;
      }
    }

    return instanceCountPerService;
  }

  private List<AggregateServiceUsageInfo> processResultSet(ResultSet resultSet) throws SQLException {
    if (Objects.isNull(resultSet)) {
      return new ArrayList<>();
    }

    List<AggregateServiceUsageInfo> instanceCountPerService = new ArrayList<>();
    while (resultSet.next()) {
      long instanceCount = resultSet.getLong(1);
      String orgId = resultSet.getString(2);
      String projectId = resultSet.getString(3);
      String serviceId = resultSet.getString(4);
      instanceCountPerService.add(new AggregateServiceUsageInfo(orgId, projectId, serviceId, instanceCount));
    }

    return instanceCountPerService;
  }
}
